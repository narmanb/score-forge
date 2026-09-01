package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalEntryTimingTest {
    @Test
    fun `120 bpm maps relaxed hold lengths to conventional durations`() {
        assertEquals(NoteDuration.SIXTEENTH, NaturalEntryTiming.durationForHoldMs(100, 120))
        assertEquals(NoteDuration.EIGHTH, NaturalEntryTiming.durationForHoldMs(250, 120))
        assertEquals(NoteDuration.QUARTER, NaturalEntryTiming.durationForHoldMs(500, 120))
        assertEquals(NoteDuration.HALF, NaturalEntryTiming.durationForHoldMs(1000, 120))
        assertEquals(NoteDuration.WHOLE, NaturalEntryTiming.durationForHoldMs(2000, 120))
    }

    @Test
    fun `thresholds scale with tempo`() {
        assertEquals(NoteDuration.QUARTER, NaturalEntryTiming.durationForHoldMs(1000, 60))
        assertEquals(NoteDuration.QUARTER, NaturalEntryTiming.durationForHoldMs(250, 240))
    }

    @Test
    fun `tempo is clamped to project limits`() {
        assertEquals(
            NaturalEntryTiming.durationForHoldMs(500, 30),
            NaturalEntryTiming.durationForHoldMs(500, 1),
        )
        assertEquals(
            NaturalEntryTiming.durationForHoldMs(500, 300),
            NaturalEntryTiming.durationForHoldMs(500, 999),
        )
    }
}
