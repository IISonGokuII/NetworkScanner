package com.loopforge.app.export

import kotlin.math.min

/**
 * A soft transition at the loop seam.
 *
 * [TAIL]: the last [fadeUs] of the loop dissolve into the footage just *before* the loop start,
 * so when the loop jumps back to its start, the picture is already there.
 * [HEAD]: the first [fadeUs] of the loop dissolve out of the footage just *after* the loop end,
 * used when the loop starts too close to the beginning of the video for [TAIL].
 */
data class Crossfade(val side: Side, val fadeUs: Long) {

    enum class Side { TAIL, HEAD }

    /** Opacity of the loop itself (the top layer) at [timeUs] since the start of the loop piece. */
    fun loopAlpha(timeUs: Long, loopUs: Long, frameUs: Long): Float {
        val span = (fadeUs - frameUs).coerceAtLeast(1L).toFloat()
        val w = when (side) {
            Side.TAIL -> (timeUs - (loopUs - fadeUs)) / span
            Side.HEAD -> 1f - timeUs / span
        }.coerceIn(0f, 1f)
        val eased = w * w * (3f - 2f * w)
        return 1f - eased
    }

    companion object {
        const val MIN_FADE_US = 100_000L

        /** Picks the side with enough footage around the loop; null when no fade is possible. */
        fun plan(loopStartUs: Long, loopEndUs: Long, sourceDurationUs: Long, wantedUs: Long): Crossfade? {
            val wanted = min(wantedUs, (loopEndUs - loopStartUs) / 3)
            if (wanted < MIN_FADE_US) return null
            val before = loopStartUs
            val after = sourceDurationUs - loopEndUs
            return when {
                before >= wanted -> Crossfade(Side.TAIL, wanted)
                after >= wanted -> Crossfade(Side.HEAD, wanted)
                before >= after && before >= MIN_FADE_US -> Crossfade(Side.TAIL, before)
                after >= MIN_FADE_US -> Crossfade(Side.HEAD, after)
                else -> null
            }
        }
    }
}
