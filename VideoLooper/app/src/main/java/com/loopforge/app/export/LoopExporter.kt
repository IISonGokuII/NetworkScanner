package com.loopforge.app.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** What to render: an optional intro, the loop [loopStartUs, loopEndUs) [repeats] times, an optional outro. */
data class ExportPlan(
    val source: Uri,
    val sourceDurationUs: Long,
    val loopStartUs: Long,
    val loopEndUs: Long,
    val repeats: Int,
    val includeIntro: Boolean,
    val includeOutro: Boolean,
    val keepAudio: Boolean,
    /** Length of the soft transition at the seam, 0 = hard cut. */
    val crossfadeUs: Long = 0L,
    val frameIntervalUs: Long = 33_333L,
) {
    val loopUs: Long get() = loopEndUs - loopStartUs
    val introUs: Long get() = if (includeIntro && loopStartUs > MIN_SEGMENT_US) loopStartUs else 0L
    val outroUs: Long
        get() = (sourceDurationUs - loopEndUs).takeIf { includeOutro && it > MIN_SEGMENT_US } ?: 0L
    val totalUs: Long get() = introUs + repeats * loopUs + outroUs

    /** The transition that will actually be used, or null for a hard cut. */
    val crossfade: Crossfade?
        get() = if (crossfadeUs <= 0) null else Crossfade.plan(loopStartUs, loopEndUs, sourceDurationUs, crossfadeUs)

    companion object {
        const val MIN_SEGMENT_US = 50_000L
    }
}

data class ExportOutput(
    val uri: Uri,
    val durationUs: Long,
    val displayName: String,
    /** A cross-fade was requested but this device could not render it, so hard cuts were used. */
    val crossfadeSkipped: Boolean = false,
)

/**
 * Renders the extended video.
 *
 * Fast path: only the distinct pieces (intro, one loop pass, outro) are re-encoded with Media3
 * Transformer, then [Mp4Concat] copies the encoded loop as often as needed. A ten minute video
 * therefore costs about as much as encoding the loop once.
 *
 * With a cross-fade, the loop piece is a two-layer composition: the loop itself on top and,
 * underneath, the same footage whose last part is replaced by what comes just before the loop
 * start. Fading the top layer out at the end lets the picture (and the sound) arrive smoothly at
 * the loop start, so the jump back is invisible even if the two moments are not identical.
 *
 * Fallbacks: hard cuts on the fast path, then one composition containing every repetition.
 */
@OptIn(UnstableApi::class)
class LoopExporter(private val context: Context) {

    fun interface Progress {
        fun update(progress: Float, stage: String)
    }

    private data class Attempt(val hdrMode: Int, val videoMime: String?, val audioEffects: Boolean)

    /** A distinct part of the output that is encoded once and may be repeated. */
    private class Piece(val name: String, val stage: String, val lengthUs: Long, val build: (Attempt) -> Composition)

    private enum class Strategy { FAST_CROSSFADE, FAST, FULL }

    suspend fun export(plan: ExportPlan, progress: Progress): ExportOutput {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val name = "LoopForge_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val crossfade = plan.crossfade
        val strategies = listOfNotNull(if (crossfade != null) Strategy.FAST_CROSSFADE else null, Strategy.FAST, Strategy.FULL)
        var last: Exception? = null
        try {
            for (strategy in strategies) {
                try {
                    val uri = when (strategy) {
                        Strategy.FAST_CROSSFADE -> fastExport(plan, crossfade, dir, name, progress)
                        Strategy.FAST -> fastExport(plan, null, dir, name, progress)
                        Strategy.FULL -> fullExport(plan, dir, name, progress)
                    }
                    progress.update(1f, "Fertig")
                    return ExportOutput(
                        uri = uri,
                        durationUs = measureDurationUs(uri) ?: plan.totalUs,
                        displayName = name,
                        crossfadeSkipped = crossfade != null && strategy != Strategy.FAST_CROSSFADE,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Export strategy $strategy failed", e)
                    last = e
                    dir.listFiles()?.forEach { it.delete() }
                }
            }
            throw last ?: IllegalStateException("Export fehlgeschlagen")
        } finally {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    private suspend fun fastExport(plan: ExportPlan, crossfade: Crossfade?, dir: File, name: String, progress: Progress): Uri {
        val layout = layout(plan, crossfade)
        val pieces = layout.map { it.first }.distinctBy { it.name }
        val encodeTotal = pieces.sumOf { it.lengthUs }.coerceAtLeast(1L).toFloat()
        var encodedUs = 0L
        var attempt: Attempt? = null
        val files = HashMap<String, File>()
        for (piece in pieces) {
            val out = File(dir, "${piece.name}.mp4")
            attempt = transcode(out, attempt, piece.build) { p ->
                progress.update(FAST_ENCODE_SHARE * (encodedUs + p * piece.lengthUs) / encodeTotal, piece.stage)
            }
            encodedUs += piece.lengthUs
            files[piece.name] = out
        }
        val signatures = files.values.map(Mp4Concat::signature).distinct()
        check(signatures.size == 1) { "Segmente sind nicht kompatibel: $signatures" }

        val uri = Gallery.create(context, name)
        try {
            context.contentResolver.openFileDescriptor(uri, "rw")!!.use { pfd ->
                withContext(Dispatchers.IO) {
                    val job = coroutineContext
                    Mp4Concat.concat(
                        layout.map { (piece, repeats) -> Mp4Concat.Source(files.getValue(piece.name), repeats) },
                        pfd.fileDescriptor,
                        onProgress = { p -> progress.update(FAST_ENCODE_SHARE + (1f - FAST_ENCODE_SHARE) * p, "Wiederholungen werden verkettet…") },
                        isCancelled = { !job.isActive },
                    )
                }
            }
            Gallery.publish(context, uri)
            return uri
        } catch (e: Throwable) {
            Gallery.delete(context, uri)
            throw e
        }
    }

    /**
     * Order of the pieces in the output. With a tail fade every pass ends on the frames before the
     * loop start, so the pass in front of an outro must be a plain one (and with a head fade, the
     * pass right after an intro).
     */
    private fun layout(plan: ExportPlan, crossfade: Crossfade?): List<Pair<Piece, Int>> {
        val plain = Piece("loop", "Loop wird gerendert…", plan.loopUs) { a ->
            single(a, clip(plan, plan.loopStartUs, plan.loopEndUs, a, edgeFades(plan.loopUs)))
        }
        val result = ArrayList<Pair<Piece, Int>>()
        if (plan.introUs > 0) {
            result += Piece("intro", "Intro wird gerendert…", plan.introUs) { a ->
                single(a, clip(plan, 0L, plan.loopStartUs, a, edgeFades(plan.introUs)))
            } to 1
        }
        if (crossfade == null) {
            result += plain to plan.repeats
        } else {
            val blended = Piece("loop_fade", "Loop mit Überblendung wird gerendert…", plan.loopUs) { a ->
                crossfadeComposition(plan, crossfade, a)
            }
            val firstPlain = crossfade.side == Crossfade.Side.HEAD && plan.introUs > 0
            val lastPlain = crossfade.side == Crossfade.Side.TAIL && plan.outroUs > 0
            if (plan.repeats == 1) {
                result += (if (firstPlain || lastPlain) plain else blended) to 1
            } else {
                if (firstPlain) result += plain to 1
                val middle = plan.repeats - (if (firstPlain) 1 else 0) - (if (lastPlain) 1 else 0)
                if (middle > 0) result += blended to middle
                if (lastPlain) result += plain to 1
            }
        }
        if (plan.outroUs > 0) {
            result += Piece("outro", "Outro wird gerendert…", plan.outroUs) { a ->
                single(a, clip(plan, plan.loopEndUs, C.TIME_END_OF_SOURCE, a, edgeFades(plan.outroUs)))
            } to 1
        }
        return result
    }

    private fun crossfadeComposition(plan: ExportPlan, crossfade: Crossfade, attempt: Attempt): Composition {
        val a = plan.loopStartUs
        val b = plan.loopEndUs
        val k = crossfade.fadeUs
        val l = plan.loopUs
        val top: EditedMediaItem
        val bottom: List<EditedMediaItem>
        // The bottom layer only matters while the top one fades; the rest of it repeats the loop
        // footage underneath an opaque top layer and stays silent.
        val audioBelow = attempt.audioEffects
        if (crossfade.side == Crossfade.Side.TAIL) {
            top = clip(plan, a, b, attempt, gains(l) { fadeIn(0, EDGE_FADE_US); fadeOut(l - k, k) })
            bottom = listOf(
                clip(plan, a, b - k, attempt, gains(b - k - a, silent = true), audio = audioBelow),
                clip(plan, a - k, a, attempt, gains(k) { fadeIn(0, k); fadeOut(k - EDGE_FADE_US, EDGE_FADE_US) }, audio = audioBelow),
            )
        } else {
            val afterEnd = if (b + k >= plan.sourceDurationUs) C.TIME_END_OF_SOURCE else b + k
            top = clip(plan, a, b, attempt, gains(l) { fadeIn(0, k); fadeOut(l - EDGE_FADE_US, EDGE_FADE_US) })
            bottom = listOf(
                clip(plan, b, afterEnd, attempt, gains(k) { fadeOut(0, k); fadeIn(0, EDGE_FADE_US) }, audio = audioBelow),
                clip(plan, a + k, b, attempt, gains(b - a - k, silent = true), audio = audioBelow),
            )
        }
        return Composition.Builder(
            EditedMediaItemSequence.Builder(top).build(),
            EditedMediaItemSequence.Builder(bottom).build(),
        )
            .setVideoCompositorSettings(CrossfadeCompositor(crossfade, l, plan.frameIntervalUs))
            .setHdrMode(attempt.hdrMode)
            .build()
    }

    private suspend fun fullExport(plan: ExportPlan, dir: File, name: String, progress: Progress): Uri {
        val out = File(dir, "full.mp4")
        transcode(out, null, { a ->
            val items = ArrayList<EditedMediaItem>()
            if (plan.introUs > 0) items += clip(plan, 0L, plan.loopStartUs, a, edgeFades(plan.introUs))
            repeat(plan.repeats) { items += clip(plan, plan.loopStartUs, plan.loopEndUs, a, edgeFades(plan.loopUs)) }
            if (plan.outroUs > 0) items += clip(plan, plan.loopEndUs, C.TIME_END_OF_SOURCE, a, edgeFades(plan.outroUs))
            Composition.Builder(EditedMediaItemSequence.Builder(items).build()).setHdrMode(a.hdrMode).build()
        }) { p -> progress.update(0.97f * p, "Video wird komplett gerendert…") }
        val uri = Gallery.create(context, name)
        try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "w")!!.use { output -> out.inputStream().use { it.copyTo(output, 1 shl 20) } }
            }
            Gallery.publish(context, uri)
            return uri
        } catch (e: Throwable) {
            Gallery.delete(context, uri)
            throw e
        }
    }

    /** Tries the export settings from most to least compatible-looking until one works. */
    private suspend fun transcode(
        out: File,
        preferred: Attempt?,
        build: (Attempt) -> Composition,
        onProgress: (Float) -> Unit,
    ): Attempt {
        val attempts = if (preferred != null) listOf(preferred) else ATTEMPTS
        var last: Exception? = null
        for (attempt in attempts) {
            try {
                runTransformer(build(attempt), out, attempt, onProgress)
                return attempt
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Export attempt $attempt failed", e)
                last = e
                out.delete()
            }
        }
        throw last ?: IllegalStateException("Export fehlgeschlagen")
    }

    private suspend fun runTransformer(composition: Composition, out: File, attempt: Attempt, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.Main) {
            val done = CompletableDeferred<Unit>()
            val builder = Transformer.Builder(context).setAudioMimeType(MimeTypes.AUDIO_AAC)
            attempt.videoMime?.let { builder.setVideoMimeType(it) }
            val transformer = builder.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    done.complete(Unit)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    done.completeExceptionally(exportException)
                }
            }).build()
            transformer.start(composition, out.absolutePath)
            val holder = ProgressHolder()
            val poller = launch {
                while (isActive) {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress / 100f)
                    }
                    delay(120)
                }
            }
            try {
                done.await()
                onProgress(1f)
            } catch (e: CancellationException) {
                transformer.cancel()
                throw e
            } finally {
                poller.cancel()
            }
        }

    private fun single(attempt: Attempt, item: EditedMediaItem): Composition =
        Composition.Builder(EditedMediaItemSequence.Builder(item).build()).setHdrMode(attempt.hdrMode).build()

    private fun clip(
        plan: ExportPlan,
        startUs: Long,
        endUs: Long,
        attempt: Attempt,
        gain: DefaultGainProvider,
        audio: Boolean = true,
    ): EditedMediaItem {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionUs(startUs)
            .setEndPositionUs(endUs)
            .build()
        val mediaItem = MediaItem.Builder().setUri(plan.source).setClippingConfiguration(clipping).build()
        val withAudio = plan.keepAudio && audio
        val builder = EditedMediaItem.Builder(mediaItem).setRemoveAudio(!withAudio)
        if (withAudio && attempt.audioEffects) {
            builder.setEffects(Effects(listOf(GainProcessor(gain)), emptyList()))
        }
        return builder.build()
    }

    /** A few milliseconds of fade at both ends of a piece keep the audio seams click-free. */
    private fun edgeFades(durationUs: Long) = gains(durationUs) {
        fadeIn(0, EDGE_FADE_US)
        fadeOut(durationUs - EDGE_FADE_US, EDGE_FADE_US)
    }

    private class GainScope(private val durationUs: Long, private val builder: DefaultGainProvider.Builder) {
        fun fadeIn(atUs: Long, lengthUs: Long) = add(atUs, lengthUs, DefaultGainProvider.FADE_IN_EQUAL_POWER)
        fun fadeOut(atUs: Long, lengthUs: Long) = add(atUs, lengthUs, DefaultGainProvider.FADE_OUT_EQUAL_POWER)

        private fun add(atUs: Long, lengthUs: Long, shape: DefaultGainProvider.FadeProvider) {
            val start = atUs.coerceIn(0L, durationUs)
            val length = min(lengthUs, durationUs - start)
            if (length > 2) builder.addFadeAt(start, length, shape)
        }
    }

    private fun gains(durationUs: Long, silent: Boolean = false, block: GainScope.() -> Unit = {}): DefaultGainProvider {
        val builder = DefaultGainProvider.Builder(if (silent) 0f else 1f)
        if (!silent) GainScope(max(1L, durationUs), builder).block()
        return builder.build()
    }

    private fun measureDurationUs(uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.times(1000)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /** Fades the top layer (the loop) in or out according to the [crossfade]. */
    private class CrossfadeCompositor(
        private val crossfade: Crossfade,
        private val loopUs: Long,
        private val frameUs: Long,
    ) : VideoCompositorSettings {
        @Volatile
        private var baseUs = C.TIME_UNSET

        override fun getOutputSize(inputSizes: List<Size>): Size = inputSizes[0]

        override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
            if (inputId != 0) return OPAQUE
            if (baseUs == C.TIME_UNSET) baseUs = presentationTimeUs
            val alpha = crossfade.loopAlpha(presentationTimeUs - baseUs, loopUs, frameUs)
            return object : OverlaySettings {
                override fun getAlphaScale(): Float = alpha
            }
        }

        companion object {
            private val OPAQUE = object : OverlaySettings {}
        }
    }

    companion object {
        private const val TAG = "LoopExporter"
        private const val EDGE_FADE_US = 12_000L
        private const val FAST_ENCODE_SHARE = 0.85f

        private val ATTEMPTS = listOf(
            Attempt(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL, MimeTypes.VIDEO_H264, audioEffects = true),
            Attempt(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC, MimeTypes.VIDEO_H264, audioEffects = true),
            Attempt(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL, MimeTypes.VIDEO_H264, audioEffects = false),
            Attempt(Composition.HDR_MODE_KEEP_HDR, null, audioEffects = false),
        )
    }
}

/** Saves videos to Movies/LoopForge so they show up in the gallery. */
object Gallery {
    private const val FOLDER = "LoopForge"

    fun create(context: Context, displayName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + FOLDER)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return context.contentResolver.insert(collection, values) ?: error("Galerie-Eintrag konnte nicht angelegt werden")
    }

    fun publish(context: Context, uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        context.contentResolver.update(uri, values, null, null)
    }

    fun delete(context: Context, uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
        }
    }
}
