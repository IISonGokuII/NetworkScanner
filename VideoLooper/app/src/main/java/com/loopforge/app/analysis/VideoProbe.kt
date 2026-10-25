package com.loopforge.app.analysis

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

data class VideoInfo(
    val uri: Uri,
    val name: String,
    val durationUs: Long,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val hasAudio: Boolean,
) {
    val displayWidth: Int get() = if (rotation % 180 == 0) width else height
    val displayHeight: Int get() = if (rotation % 180 == 0) height else width
    val aspectRatio: Float
        get() = if (displayHeight > 0) displayWidth.toFloat() / displayHeight else 16f / 9f
}

/**
 * Metadata and preview frames of one video. [MediaMetadataRetriever] is not thread safe, so every
 * call goes through a single-threaded dispatcher.
 */
class VideoProbe private constructor(private val retriever: MediaMetadataRetriever) : Closeable {

    @Volatile
    private var closed = false

    suspend fun frame(timeUs: Long, maxWidth: Int, maxHeight: Int, exact: Boolean): Bitmap? = withContext(io) {
        if (closed) return@withContext null
        val option = if (exact) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        try {
            retriever.getScaledFrameAtTime(timeUs.coerceAtLeast(0L), option, maxWidth, maxHeight)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun filmstrip(durationUs: Long, count: Int, maxWidth: Int, maxHeight: Int): List<Bitmap> {
        val result = ArrayList<Bitmap>(count)
        for (i in 0 until count) {
            val t = ((i + 0.5) * durationUs / count).toLong()
            frame(t, maxWidth, maxHeight, exact = false)?.let(result::add)
        }
        return result
    }

    override fun close() {
        closed = true
        try {
            retriever.release()
        } catch (_: Exception) {
        }
    }

    companion object {
        private val io = Dispatchers.IO.limitedParallelism(1)

        suspend fun open(context: Context, uri: Uri): Pair<VideoProbe, VideoInfo> = withContext(io) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                fun meta(key: Int) = retriever.extractMetadata(key)
                val duration = (meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
                if (meta(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == null && duration == 0L) {
                    error("Die Datei enthält kein lesbares Video")
                }
                val info = VideoInfo(
                    uri = uri,
                    name = displayName(context, uri),
                    durationUs = duration,
                    width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                    height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                    rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0,
                    hasAudio = meta(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes",
                )
                VideoProbe(retriever) to info
            } catch (e: Exception) {
                retriever.release()
                throw e
            }
        }

        private fun displayName(context: Context, uri: Uri): String {
            return try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } catch (_: Exception) {
                null
            } ?: uri.lastPathSegment ?: "Video"
        }
    }
}
