package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldEntryTimingTest {
    @Test
    fun writtenDurationNeverShrinksWhileHoldTimeIncreases() {
        var previousBeats = 0f
        for (holdMs in 0L..4_000L step 25L) {
            val written = NaturalEntryTiming.writtenForHoldMs(holdMs, bpm = 120)
            assertTrue(written.beats >= previousBeats)
            previousBeats = written.beats
        }
    }

    @Test
    fun dottedQuarterTransitionsToHalfBeforeReleaseAt120Bpm() {
        val dottedQuarter = NaturalEntryTiming.writtenForHoldMs(800L, bpm = 120)
        assertEquals(NoteDuration.QUARTER, dottedQuarter.duration)
        assertTrue(dottedQuarter.dotted)

        val half = NaturalEntryTiming.writtenForHoldMs(900L, bpm = 120)
        assertEquals(NoteDuration.HALF, half.duration)
        assertFalse(half.dotted)
    }
}
