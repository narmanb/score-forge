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
}
