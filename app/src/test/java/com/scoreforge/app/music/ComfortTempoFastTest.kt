package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ComfortTempoFastTest {
    @Test
    fun fastQuarterNotesEstimateTwoHundredBpm() {
        val attacks = listOf(0L, 300L, 600L, 900L, 1200L, 1500L, 1800L, 2100L)
        assertEquals(200, ComfortTempo.estimateBpm(attacks))
    }
}
