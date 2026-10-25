package com.loopforge.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossfadeTest {

    private val frame = 33_333L

    @Test
    fun tailFadeEndsOnTheFootageBeforeTheLoop() {
        val fade = Crossfade(Crossfade.Side.TAIL, 600_000L)
        val loop = 6_000_000L
        assertEquals(1f, fade.loopAlpha(0L, loop, frame), 0f)
        assertEquals(1f, fade.loopAlpha(loop - 600_000L, loop, frame), 0f)
        assertEquals(0.5f, fade.loopAlpha(loop - 600_000L + (600_000L - frame) / 2, loop, frame), 0.01f)
        // The last frame of the pass is fully the frame right before the loop start.
        assertEquals(0f, fade.loopAlpha(loop - frame, loop, frame), 0f)
    }

    @Test
    fun headFadeStartsOnTheFootageAfterTheLoop() {
        val fade = Crossfade(Crossfade.Side.HEAD, 500_000L)
        assertEquals(0f, fade.loopAlpha(0L, 4_000_000L, frame), 0f)
        assertEquals(1f, fade.loopAlpha(500_000L, 4_000_000L, frame), 0f)
        assertEquals(1f, fade.loopAlpha(3_900_000L, 4_000_000L, frame), 0f)
    }

    @Test
    fun picksASideWithEnoughFootage() {
        assertEquals(Crossfade(Crossfade.Side.TAIL, 600_000L), Crossfade.plan(2_000_000L, 8_000_000L, 10_000_000L, 600_000L))
        assertEquals(Crossfade(Crossfade.Side.HEAD, 600_000L), Crossfade.plan(0L, 8_000_000L, 10_000_000L, 600_000L))
        // Not enough on either side: use what is there.
        assertEquals(Crossfade(Crossfade.Side.TAIL, 300_000L), Crossfade.plan(300_000L, 9_900_000L, 10_000_000L, 600_000L))
        // Loop runs from the very start to the very end: no room for a fade.
        assertNull(Crossfade.plan(0L, 10_000_000L, 10_000_000L, 600_000L))
        // Never longer than a third of the loop.
        val short = Crossfade.plan(2_000_000L, 2_900_000L, 10_000_000L, 2_000_000L)!!
        assertTrue(short.fadeUs <= 300_000L)
    }
}
