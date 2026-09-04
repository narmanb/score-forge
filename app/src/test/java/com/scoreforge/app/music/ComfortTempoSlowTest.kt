package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ComfortTempoSlowTest {
    @Test
    fun slowQuarterNotesEstimateSixtyBpm() {
        val attacks = listOf(0L, 1000L, 2000L, 3000L, 4000L, 5000L, 6000L, 7000L)
        assertEquals(60, ComfortTempo.estimateBpm(attacks))
    }
}
