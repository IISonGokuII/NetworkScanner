package com.loopforge.app.analysis

/**
 * Compact visual fingerprints of the decoded frames of a video.
 *
 * Every frame is reduced to a 32x32 luma grid plus two 8x8 chroma grids (unsigned bytes).
 * That is small enough to keep several thousand frames in memory and still precise enough
 * to tell apart frames that differ by a few pixels of motion.
 */
class FrameFeatures(
    val count: Int,
    /** Presentation timestamps in microseconds, strictly increasing. */
    val timestampsUs: LongArray,
    /** [count] * [DIM] bytes, frame after frame. */
    val data: ByteArray,
    /** Duration of the whole source video in microseconds. */
    val durationUs: Long,
) {
    init {
        require(timestampsUs.size >= count && data.size >= count * DIM)
    }

    companion object {
        const val GRID = 32
        const val CHROMA_GRID = 8
        const val LUMA = GRID * GRID
        const val CHROMA = CHROMA_GRID * CHROMA_GRID
        const val DIM = LUMA + 2 * CHROMA
    }
}

/** Collects frames in decode order and produces [FrameFeatures] sorted by timestamp. */
class FrameFeatureBuilder(initialCapacity: Int = 256) {
    private var timestamps = LongArray(initialCapacity)
    private var data = ByteArray(initialCapacity * FrameFeatures.DIM)
    var count = 0
        private set

    /** Returns the buffer slot for the next frame; fill it and call [commit]. */
    fun next(timestampUs: Long): Slot {
        if (count == timestamps.size) {
            val newCapacity = timestamps.size * 2
            timestamps = timestamps.copyOf(newCapacity)
            data = data.copyOf(newCapacity * FrameFeatures.DIM)
        }
        timestamps[count] = timestampUs
        return Slot(data, count * FrameFeatures.DIM)
    }

    fun commit() {
        count++
    }

    fun build(durationUs: Long): FrameFeatures {
        // Decoders emit frames in presentation order, but be defensive about odd streams.
        val order = (0 until count).sortedBy { timestamps[it] }
        val ts = LongArray(count)
        val out = ByteArray(count * FrameFeatures.DIM)
        var written = 0
        var last = Long.MIN_VALUE
        for (src in order) {
            if (timestamps[src] == last) continue // drop duplicate timestamps
            last = timestamps[src]
            ts[written] = last
            System.arraycopy(data, src * FrameFeatures.DIM, out, written * FrameFeatures.DIM, FrameFeatures.DIM)
            written++
        }
        val end = maxOf(durationUs, if (written > 0) ts[written - 1] else 0L)
        return FrameFeatures(written, ts, out, end)
    }

    class Slot(val array: ByteArray, val offset: Int)
}
