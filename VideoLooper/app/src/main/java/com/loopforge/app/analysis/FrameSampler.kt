package com.loopforge.app.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.core.graphics.scale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decodes every frame of a video with the hardware decoder and turns it into a [FrameFeatures]
 * fingerprint, reading the YUV planes directly (no bitmaps, no GPU round-trip).
 */
class FrameSampler(private val context: Context) {

    /** [progress] is 0..1, [frames] the number of fingerprints taken so far. */
    fun interface Progress {
        fun update(progress: Float, frames: Int)
    }

    suspend fun sample(uri: Uri, progress: Progress): FrameFeatures = withContext(Dispatchers.Default) {
        try {
            decodeWithCodec(uri, progress)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "MediaCodec sampling failed, falling back to MediaMetadataRetriever", e)
            decodeWithRetriever(uri, progress)
        }
    }

    private suspend fun decodeWithCodec(uri: Uri, progress: Progress): FrameFeatures {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: error("Keine Videospur gefunden")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            val fps = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                format.getNumber(MediaFormat.KEY_FRAME_RATE)?.toDouble() ?: 30.0
            } else 30.0
            val expectedFrames = max(1.0, durationUs / 1e6 * fps)
            val keepEvery = max(1, ceil(expectedFrames / MAX_FRAMES).toInt())

            codec = createDecoder(mime, format)
            codec.start()

            val builder = FrameFeatureBuilder(min(MAX_FRAMES, expectedFrames.toInt() + 16))
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var decoded = 0
            var lastReport = 0L
            var idleSinceMs = 0L
            while (!outputDone) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                    // Some decoders never signal end of stream; stop once they go quiet.
                    val now = System.currentTimeMillis()
                    if (idleSinceMs == 0L) idleSinceMs = now
                    if (now - idleSinceMs > 3000) break
                } else {
                    idleSinceMs = 0L
                }
                if (outIndex >= 0) {
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    if (!eos || info.size > 0) {
                        if (decoded % keepEvery == 0) {
                            val image = codec.getOutputImage(outIndex) ?: error("Decoder liefert keine Bilder")
                            image.use { sampleImage(it, builder.next(info.presentationTimeUs)) }
                            builder.commit()
                        }
                        decoded++
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (eos) outputDone = true
                    val now = System.currentTimeMillis()
                    if (now - lastReport > 80 && durationUs > 0) {
                        lastReport = now
                        progress.update((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f), builder.count)
                    }
                }
            }
            if (builder.count < 2) error("Zu wenige Frames dekodiert")
            progress.update(1f, builder.count)
            return builder.build(durationUs)
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            codec?.release()
            extractor.release()
        }
    }

    private fun createDecoder(mime: String, trackFormat: MediaFormat): MediaCodec {
        val format = MediaFormat(trackFormat)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        val name = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format)
        } catch (_: Exception) {
            null
        }
        val codec = if (name != null) MediaCodec.createByCodecName(name) else MediaCodec.createDecoderByType(mime)
        try {
            codec.configure(format, null, null, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Flexible YUV not accepted, using decoder default", e)
            codec.reset()
            codec.configure(trackFormat, null, null, 0)
        }
        return codec
    }

    private fun sampleImage(image: Image, slot: FrameFeatureBuilder.Slot) {
        val planes = image.planes
        if (planes.size < 3) error("Unerwartetes Bildformat ${image.format}")
        // 10-bit HDR frames store each sample in 16 bits; the high byte is enough for us.
        val highByte = if (image.format == ImageFormat.YCBCR_P010) 1 else 0
        val crop = image.cropRect.takeIf { !it.isEmpty } ?: android.graphics.Rect(0, 0, image.width, image.height)
        samplePlane(planes[0], crop.left, crop.top, crop.width(), crop.height(), FrameFeatures.GRID, highByte, slot.array, slot.offset)
        val cl = crop.left / 2
        val ct = crop.top / 2
        val cw = max(1, crop.width() / 2)
        val ch = max(1, crop.height() / 2)
        samplePlane(planes[1], cl, ct, cw, ch, FrameFeatures.CHROMA_GRID, highByte, slot.array, slot.offset + FrameFeatures.LUMA)
        samplePlane(planes[2], cl, ct, cw, ch, FrameFeatures.CHROMA_GRID, highByte, slot.array, slot.offset + FrameFeatures.LUMA + FrameFeatures.CHROMA)
    }

    /** Averages 2x2 sample points per grid cell, which is plenty to suppress aliasing. */
    private fun samplePlane(
        plane: Image.Plane, left: Int, top: Int, width: Int, height: Int,
        grid: Int, highByte: Int, out: ByteArray, outOffset: Int,
    ) {
        val buffer: ByteBuffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val limit = buffer.limit()
        for (gy in 0 until grid) {
            val y0 = top + ((gy + 0.25f) * height / grid).toInt()
            val y1 = top + ((gy + 0.75f) * height / grid).toInt()
            for (gx in 0 until grid) {
                val x0 = left + ((gx + 0.25f) * width / grid).toInt()
                val x1 = left + ((gx + 0.75f) * width / grid).toInt()
                var sum = 0
                sum += read(buffer, y0 * rowStride + x0 * pixelStride + highByte, limit)
                sum += read(buffer, y0 * rowStride + x1 * pixelStride + highByte, limit)
                sum += read(buffer, y1 * rowStride + x0 * pixelStride + highByte, limit)
                sum += read(buffer, y1 * rowStride + x1 * pixelStride + highByte, limit)
                out[outOffset + gy * grid + gx] = (sum / 4).toByte()
            }
        }
    }

    private fun read(buffer: ByteBuffer, index: Int, limit: Int): Int =
        buffer.get(index.coerceIn(0, limit - 1)).toInt() and 0xFF

    /** Slower path for codecs that do not expose their output as [Image]. */
    private suspend fun decodeWithRetriever(uri: Uri, progress: Progress): FrameFeatures {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
            if (durationUs <= 0) error("Video hat keine Länge")
            val frameCount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull() ?: 0
            val builder = FrameFeatureBuilder()
            if (frameCount in 2..MAX_FALLBACK_SEQUENTIAL) {
                // Sequential decoding in small batches, every frame.
                val params = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.RGB_565 }
                var index = 0
                while (index < frameCount) {
                    currentCoroutineContext().ensureActive()
                    val batch = min(4, frameCount - index)
                    val frames = retriever.getFramesAtIndex(index, batch, params)
                    frames.forEachIndexed { k, bitmap ->
                        val t = (index + k).toLong() * durationUs / frameCount
                        sampleBitmap(bitmap, builder.next(t))
                        builder.commit()
                        bitmap.recycle()
                    }
                    index += batch
                    progress.update(index / frameCount.toFloat(), builder.count)
                }
            } else {
                val samples = min(MAX_FALLBACK_SAMPLES, max(2, (durationUs / 1e6 * 15).roundToInt()))
                for (i in 0 until samples) {
                    currentCoroutineContext().ensureActive()
                    val t = i.toLong() * durationUs / samples
                    val bitmap = retriever.getScaledFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST, 96, 96) ?: continue
                    sampleBitmap(bitmap, builder.next(t))
                    builder.commit()
                    bitmap.recycle()
                    progress.update((i + 1) / samples.toFloat(), builder.count)
                }
            }
            if (builder.count < 2) error("Video konnte nicht gelesen werden")
            return builder.build(durationUs)
        } finally {
            retriever.release()
        }
    }

    private fun sampleBitmap(bitmap: Bitmap, slot: FrameFeatureBuilder.Slot) {
        val g = FrameFeatures.GRID
        val small = bitmap.scale(g, g)
        val pixels = IntArray(g * g)
        small.getPixels(pixels, 0, g, 0, 0, g, g)
        if (small !== bitmap) small.recycle()
        val cg = FrameFeatures.CHROMA_GRID
        val block = g / cg
        val u = IntArray(cg * cg)
        val v = IntArray(cg * cg)
        for (y in 0 until g) for (x in 0 until g) {
            val p = pixels[y * g + x]
            val r = (p shr 16) and 0xFF
            val gr = (p shr 8) and 0xFF
            val b = p and 0xFF
            slot.array[slot.offset + y * g + x] = (0.299f * r + 0.587f * gr + 0.114f * b).roundToInt().coerceIn(0, 255).toByte()
            val c = (y / block) * cg + x / block
            u[c] += (-0.169f * r - 0.331f * gr + 0.5f * b + 128f).roundToInt()
            v[c] += (0.5f * r - 0.419f * gr - 0.081f * b + 128f).roundToInt()
        }
        val n = block * block
        for (c in 0 until cg * cg) {
            slot.array[slot.offset + FrameFeatures.LUMA + c] = (u[c] / n).coerceIn(0, 255).toByte()
            slot.array[slot.offset + FrameFeatures.LUMA + FrameFeatures.CHROMA + c] = (v[c] / n).coerceIn(0, 255).toByte()
        }
    }

    companion object {
        private const val TAG = "FrameSampler"
        private const val INPUT_TIMEOUT_US = 1_000L
        private const val TIMEOUT_US = 5_000L

        /** Upper bound of fingerprints kept per video (~2 minutes at 30 fps at full rate). */
        const val MAX_FRAMES = 3600
        private const val MAX_FALLBACK_SEQUENTIAL = 1800
        private const val MAX_FALLBACK_SAMPLES = 450
    }
}
