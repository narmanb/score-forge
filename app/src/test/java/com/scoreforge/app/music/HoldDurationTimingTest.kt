package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldDurationTimingTest {
    @Test
    fun standardStillRoundsOnePointSixTwoBeatsToDottedQuarter() {
        val written = HoldDurationTiming.writtenForHoldMs(
            holdMs = 810L, // 1.62 beats at 120 BPM
            bpm = 120,
            mode = HoldDurationMode.STANDARD,
        )

        assertEquals(NoteDuration.QUARTER, written.duration)
        assertTrue(written.dotted)
    }

    @Test
    fun favorLongerRoundsOnePointSixTwoBeatsToHalf() {
        val written = HoldDurationTiming.writtenForHoldMs(
            holdMs = 810L,
            bpm = 120,
            mode = HoldDurationMode.FAVOR_LONGER,
        )

        assertEquals(NoteDuration.HALF, written.duration)
        assertFalse(written.dotted)
    }

    @Test
    fun favorLongerKeepsExactDottedQuarterAsDottedQuarter() {
        val written = HoldDurationTiming.writtenForHoldMs(
            holdMs = 750L, // exactly 1.5 beats at 120 BPM
            bpm = 120,
            mode = HoldDurationMode.FAVOR_LONGER,
        )

        assertEquals(NoteDuration.QUARTER, written.duration)
        assertTrue(written.dotted)
    }

    @Test
    fun noDotsNeverReturnsDottedValue() {
        val written = HoldDurationTiming.writtenForHoldMs(
            holdMs = 810L,
            bpm = 120,
            mode = HoldDurationMode.NO_DOTTED,
        )

        assertEquals(NoteDuration.HALF, written.duration)
        assertFalse(written.dotted)
    }

    @Test
    fun noDotsKeepsShorterPerformanceAtQuarter() {
        val written = HoldDurationTiming.writtenForHoldMs(
            holdMs = 600L, // 1.2 beats at 120 BPM
            bpm = 120,
            mode = HoldDurationMode.NO_DOTTED,
        )

        assertEquals(NoteDuration.QUARTER, written.duration)
        assertFalse(written.dotted)
    }
}
