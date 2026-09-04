package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComfortTempoTest {
    @Test
    fun steadyHalfSecondQuarterNotesEstimate120Bpm() {
        val attacks = listOf(0L, 500L, 1000L, 1500L, 2000L, 2500L, 3000L, 3500L)
        assertEquals(120, ComfortTempo.estimateBpm(attacks))
    }

    @Test
    fun medianIntervalRejectsOneEarlyAndOneLateTap() {
        val attacks = listOf(0L, 500L, 1010L, 1490L, 2020L, 2495L, 3010L, 3505L)
        assertEquals(120, ComfortTempo.estimateBpm(attacks))
    }

    @Test
    fun requiresEightAttacks() {
        assertNull(ComfortTempo.estimateBpm(listOf(0L, 500L, 1000L, 1500L, 2000L, 2500L, 3000L)))
    }

    @Test
    fun duplicateFastTouchIsIgnored() {
        val attacks = ComfortTempo.addAttack(listOf(1000L), 1050L)
        assertEquals(listOf(1000L), attacks)
    }

    @Test
    fun bpmIsClampedToSupportedTempoRange() {
        assertEquals(
            ComfortTempo.MAX_BPM,
            ComfortTempo.estimateBpm(listOf(0L, 100L, 200L, 300L, 400L, 500L, 600L, 700L)),
        )
    }
}
