package com.loopforge.app.export

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.util.Arrays

/**
 * Joins already encoded MP4 segments without re-encoding them: every sample is copied as-is
 * with shifted timestamps. Because each segment starts with a key frame this is lossless and
 * takes seconds even for very long outputs.
 */
object Mp4Concat {

    private const val END = -1L

    class Source(val file: File, val repeats: Int)

    /** A string that is equal for two files exactly when their samples can be mixed in one track. */
    fun signature(file: File): String {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            return (0 until extractor.trackCount).map { i ->
                val f = extractor.getTrackFormat(i)
                listOf(
                    f.getString(MediaFormat.KEY_MIME),
                    f.int(MediaFormat.KEY_WIDTH), f.int(MediaFormat.KEY_HEIGHT), f.int(MediaFormat.KEY_ROTATION),
                    f.int(MediaFormat.KEY_SAMPLE_RATE), f.int(MediaFormat.KEY_CHANNEL_COUNT),
                    f.bytesHash("csd-0"), f.bytesHash("csd-1"), f.bytesHash("csd-2"),
                ).joinToString("/")
            }.sorted().joinToString("|")
        } finally {
            extractor.release()
        }
    }

    /** Writes all sources (each [Source.repeats] times) into [out]; returns the total duration. */
    fun concat(sources: List<Source>, out: FileDescriptor, onProgress: (Float) -> Unit, isCancelled: () -> Boolean): Long {
        require(sources.isNotEmpty())
        val reference = MediaExtractor().apply { setDataSource(sources.first().file.path) }
        val videoTrack = reference.findTrack("video/")
        val audioTrack = reference.findTrack("audio/")
        require(videoTrack >= 0) { "Segment ohne Videospur" }
        val videoFormat = reference.getTrackFormat(videoTrack)
        val audioFormat = if (audioTrack >= 0) reference.getTrackFormat(audioTrack) else null
        reference.release()

        val muxer = MediaMuxer(out, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        try {
            videoFormat.int(MediaFormat.KEY_ROTATION)?.let { if (it != 0) muxer.setOrientationHint(it) }
            val videoOut = muxer.addTrack(videoFormat)
            val audioOut = audioFormat?.let { muxer.addTrack(it) } ?: -1
            muxer.start()
            started = true

            val capacity = maxOf(videoFormat.int(MediaFormat.KEY_MAX_INPUT_SIZE) ?: 0, audioFormat?.int(MediaFormat.KEY_MAX_INPUT_SIZE) ?: 0, 2 shl 20)
            val buffer = ByteBuffer.allocateDirect(capacity)
            val info = MediaCodec.BufferInfo()
            val totalUnits = sources.sumOf { it.repeats }.coerceAtLeast(1)
            var doneUnits = 0
            var offsetUs = 0L
            var lastAudioUs = Long.MIN_VALUE

            for (source in sources) {
                val segmentUs = videoDurationUs(source.file)
                val video = MediaExtractor().apply { setDataSource(source.file.path); selectTrack(findTrack("video/")) }
                // All segments share one signature, so each has an audio track if the first one has.
                val audio = if (audioOut >= 0) {
                    MediaExtractor().apply { setDataSource(source.file.path); selectTrack(findTrack("audio/")) }
                } else null
                try {
                    repeat(source.repeats) {
                        if (isCancelled()) throw kotlinx.coroutines.CancellationException()
                        video.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                        audio?.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                        while (true) {
                            // -1 marks the end of a track; other negative times are AAC priming.
                            val vt = video.sampleTime
                            val at = audio?.sampleTime ?: END
                            val videoLeft = vt != END
                            val audioLeft = at != END && at < segmentUs
                            if (!videoLeft && !audioLeft) break
                            val useVideo = videoLeft && (!audioLeft || vt <= at)
                            val extractor = if (useVideo) video else audio!!
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                extractor.advance()
                                continue
                            }
                            val pts = offsetUs + extractor.sampleTime
                            val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                            info.set(0, size, pts, flags)
                            if (useVideo) {
                                muxer.writeSampleData(videoOut, buffer, info)
                            } else if (pts > lastAudioUs && pts >= 0) {
                                muxer.writeSampleData(audioOut, buffer, info)
                                lastAudioUs = pts
                            }
                            extractor.advance()
                        }
                        offsetUs += segmentUs
                        doneUnits++
                        onProgress(doneUnits / totalUnits.toFloat())
                    }
                } finally {
                    video.release()
                    audio?.release()
                }
            }
            return offsetUs
        } finally {
            try {
                if (started) muxer.stop()
            } finally {
                muxer.release()
            }
        }
    }

    /** Duration of the video track: last frame time plus one frame interval. */
    private fun videoDurationUs(file: File): Long {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            extractor.selectTrack(extractor.findTrack("video/"))
            val times = ArrayList<Long>()
            while (true) {
                val t = extractor.sampleTime
                if (t == END) break
                times += t
                extractor.advance()
            }
            if (times.isEmpty()) return 0L
            times.sort()
            val last = times.last()
            val step = if (times.size >= 2) last - times[times.size - 2] else 33_333L
            return last + step
        } finally {
            extractor.release()
        }
    }

    private fun MediaExtractor.findTrack(prefix: String): Int =
        (0 until trackCount).firstOrNull { getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true } ?: -1

    private fun MediaFormat.int(key: String): Int? = if (containsKey(key)) getInteger(key) else null

    private fun MediaFormat.bytesHash(key: String): Int {
        if (!containsKey(key)) return 0
        val b = getByteBuffer(key) ?: return 0
        val copy = ByteArray(b.remaining())
        b.duplicate().get(copy)
        return Arrays.hashCode(copy)
    }
}
