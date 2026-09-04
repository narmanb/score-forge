package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class HoldPerformanceSpacingTest {
    @Test
    fun quarterBeatAttackSpacingIsPreservedAt120Bpm() {
        assertEquals(
            11.0f,
            HoldEntryTiming.nextStartBeat(
                previousStartBeat = 10f,
                previousOnsetMs = 1_000L,
                currentOnsetMs = 1_500L,
                bpm = 120,
            ),
            0.0001f,
        )
    }

    @Test
    fun halfNoteAttackSpacingIsIndependentOfWrittenHoldLength() {
        assertEquals(
            14.0f,
            HoldEntryTiming.nextStartBeat(
                previousStartBeat = 12f,
                previousOnsetMs = 2_000L,
                currentOnsetMs = 3_000L,
                bpm = 120,
            ),
            0.0001f,
        )
    }

    @Test
    fun dottedQuarterAttackSpacingIsPreserved() {
        assertEquals(
            21.5f,
            HoldEntryTiming.nextStartBeat(
                previousStartBeat = 20f,
                previousOnsetMs = 5_000L,
                currentOnsetMs = 5_750L,
                bpm = 120,
            ),
            0.0001f,
        )
    }
}
