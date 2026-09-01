package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveEntryTimingTest {
    @Test
    fun `120 bpm advances one beat every 500 ms`() {
        assertEquals(4f, LiveEntryTiming.beatAtElapsedMs(4f, 0, 120), 0.001f)
        assertEquals(5f, LiveEntryTiming.beatAtElapsedMs(4f, 500, 120), 0.001f)
        assertEquals(6f, LiveEntryTiming.beatAtElapsedMs(4f, 1000, 120), 0.001f)
    }

    @Test
    fun `live starts quantize to sixteenth grid`() {
        assertEquals(0.25f, LiveEntryTiming.quantizedBeatAtElapsedMs(0f, 130, 120), 0.001f)
        assertEquals(1f, LiveEntryTiming.quantizedBeatAtElapsedMs(0f, 510, 120), 0.001f)
    }

    @Test
    fun `live release timing can produce dotted written values`() {
        val dottedEighth = LiveEntryTiming.quantizedDurationForHoldMs(375, 120)
        assertEquals(NoteDuration.EIGHTH, dottedEighth.duration)
        assertEquals(true, dottedEighth.dotted)

        val dottedQuarter = LiveEntryTiming.quantizedDurationForHoldMs(750, 120)
        assertEquals(NoteDuration.QUARTER, dottedQuarter.duration)
        assertEquals(true, dottedQuarter.dotted)
    }

    @Test
    fun `live release timing chooses nearest conventional value`() {
        val quarter = LiveEntryTiming.quantizedDurationForHoldMs(520, 120)
        assertEquals(NoteDuration.QUARTER, quarter.duration)
        assertEquals(false, quarter.dotted)

        val half = LiveEntryTiming.quantizedDurationForHoldMs(980, 120)
        assertEquals(NoteDuration.HALF, half.duration)
        assertEquals(false, half.dotted)
    }
}
