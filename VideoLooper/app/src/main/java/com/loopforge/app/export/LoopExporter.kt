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
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
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
) {
    val loopUs: Long get() = loopEndUs - loopStartUs
    val introUs: Long get() = if (includeIntro && loopStartUs > MIN_SEGMENT_US) loopStartUs else 0L
    val outroUs: Long
        get() = (sourceDurationUs - loopEndUs).takeIf { includeOutro && it > MIN_SEGMENT_US } ?: 0L
    val totalUs: Long get() = introUs + repeats * loopUs + outroUs

    companion object {
        const val MIN_SEGMENT_US = 50_000L
    }
}

data class ExportOutput(val uri: Uri, val durationUs: Long, val displayName: String)

/**
 * Renders the extended video.
 *
 * Fast path: only the distinct pieces (intro, one loop pass, outro) are re-encoded with Media3
 * Transformer, then [Mp4Concat] copies the encoded loop as often as needed. A ten minute video
 * therefore costs about as much as encoding the loop once.
 * Fallback: one Transformer composition containing every repetition.
 */
@OptIn(UnstableApi::class)
class LoopExporter(private val context: Context) {

    fun interface Progress {
        fun update(progress: Float, stage: String)
    }

    private data class Attempt(val hdrMode: Int, val videoMime: String?, val fades: Boolean)

    private class Segment(val name: String, val startUs: Long, val endUs: Long, val repeats: Int, val lengthUs: Long)

    suspend fun export(plan: ExportPlan, progress: Progress): ExportOutput {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val name = "LoopForge_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val segments = buildList {
            if (plan.introUs > 0) add(Segment("intro", 0L, plan.loopStartUs, 1, plan.introUs))
            add(Segment("loop", plan.loopStartUs, plan.loopEndUs, plan.repeats, plan.loopUs))
            if (plan.outroUs > 0) add(Segment("outro", plan.loopEndUs, C.TIME_END_OF_SOURCE, 1, plan.outroUs))
        }
        try {
            val uri = try {
                fastExport(plan, segments, dir, name, progress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Fast export failed, rendering everything", e)
                slowExport(plan, segments, dir, name, progress)
            }
            progress.update(1f, "Fertig")
            return ExportOutput(uri, measureDurationUs(uri) ?: plan.totalUs, name)
        } finally {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    private suspend fun fastExport(plan: ExportPlan, segments: List<Segment>, dir: File, name: String, progress: Progress): Uri {
        val encodeTotal = segments.sumOf { it.lengthUs }.coerceAtLeast(1L).toFloat()
        var encodedUs = 0L
        var attempt: Attempt? = null
        val files = segments.map { segment ->
            val out = File(dir, "${segment.name}.mp4")
            attempt = transcode(out, attempt, { a -> listOf(clip(plan, segment.startUs, segment.endUs, a.fades)) }) { p ->
                progress.update(FAST_ENCODE_SHARE * (encodedUs + p * segment.lengthUs) / encodeTotal, stageFor(segment))
            }
            encodedUs += segment.lengthUs
            out
        }
        val signatures = files.map(Mp4Concat::signature).distinct()
        check(signatures.size == 1) { "Segmente sind nicht kompatibel: $signatures" }

        val uri = Gallery.create(context, name)
        try {
            context.contentResolver.openFileDescriptor(uri, "rw")!!.use { pfd ->
                withContext(Dispatchers.IO) {
                    val job = coroutineContext
                    Mp4Concat.concat(
                        segments.zip(files) { s, f -> Mp4Concat.Source(f, s.repeats) },
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

    private suspend fun slowExport(plan: ExportPlan, segments: List<Segment>, dir: File, name: String, progress: Progress): Uri {
        val out = File(dir, "full.mp4")
        transcode(out, null, { a ->
            segments.flatMap { s -> List(s.repeats) { clip(plan, s.startUs, s.endUs, a.fades) } }
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

    private fun stageFor(segment: Segment) = when (segment.name) {
        "intro" -> "Intro wird gerendert…"
        "outro" -> "Outro wird gerendert…"
        else -> "Loop wird gerendert…"
    }

    /** Tries the export settings from most to least compatible-looking until one works. */
    private suspend fun transcode(
        out: File,
        preferred: Attempt?,
        items: (Attempt) -> List<EditedMediaItem>,
        onProgress: (Float) -> Unit,
    ): Attempt {
        val attempts = if (preferred != null) listOf(preferred) else ATTEMPTS
        var last: Exception? = null
        for (attempt in attempts) {
            try {
                val sequence = EditedMediaItemSequence.Builder(items(attempt)).build()
                val composition = Composition.Builder(sequence).setHdrMode(attempt.hdrMode).build()
                runTransformer(composition, out, attempt, onProgress)
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

    private fun clip(plan: ExportPlan, startUs: Long, endUs: Long, fades: Boolean): EditedMediaItem {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionUs(startUs)
            .setEndPositionUs(endUs)
            .build()
        val mediaItem = MediaItem.Builder().setUri(plan.source).setClippingConfiguration(clipping).build()
        val builder = EditedMediaItem.Builder(mediaItem).setRemoveAudio(!plan.keepAudio)
        if (plan.keepAudio && fades) {
            val durationUs = (if (endUs == C.TIME_END_OF_SOURCE) plan.sourceDurationUs else endUs) - startUs
            builder.setEffects(Effects(listOf(fadeProcessor(durationUs)), emptyList()))
        }
        return builder.build()
    }

    /** A few milliseconds of fade at both ends of every piece keep the audio seams click-free. */
    private fun fadeProcessor(durationUs: Long): AudioProcessor {
        val fade = min(FADE_US, durationUs / 4)
        val gain = DefaultGainProvider.Builder(1f)
        if (fade > 1 && durationUs > 2 * fade) {
            gain.addFadeAt(0L, fade, DefaultGainProvider.FADE_IN_EQUAL_POWER)
            gain.addFadeAt(max(0L, durationUs - fade), fade, DefaultGainProvider.FADE_OUT_EQUAL_POWER)
        }
        return GainProcessor(gain.build())
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

    companion object {
        private const val TAG = "LoopExporter"
        private const val FADE_US = 12_000L
        private const val FAST_ENCODE_SHARE = 0.85f

        private val ATTEMPTS = listOf(
            Attempt(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL, MimeTypes.VIDEO_H264, fades = true),
            Attempt(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC, MimeTypes.VIDEO_H264, fades = false),
            Attempt(Composition.HDR_MODE_KEEP_HDR, null, fades = false),
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
