package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ComfortTempoMedianTest {
    @Test
    fun sevenIntervalsUseTheFourthSortedInterval() {
        // Intervals: 400, 500, 600, 500, 500, 700, 300 -> median 500 ms -> 120 BPM.
        val attacks = listOf(0L, 400L, 900L, 1500L, 2000L, 2500L, 3200L, 3500L)
        assertEquals(120, ComfortTempo.estimateBpm(attacks))
    }
}
