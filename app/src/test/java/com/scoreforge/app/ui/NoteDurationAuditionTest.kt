package com.scoreforge.app.ui

import com.scoreforge.app.music.NoteDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteDurationAuditionTest {
    @Test
    fun durationTracksWrittenLengthAtCurrentTempo() {
        assertEquals(2_000L, NoteDurationAudition.durationMs(NoteDuration.WHOLE, dotted = false, bpm = 120))
        assertEquals(500L, NoteDurationAudition.durationMs(NoteDuration.QUARTER, dotted = false, bpm = 120))
        assertEquals(250L, NoteDurationAudition.durationMs(NoteDuration.EIGHTH, dotted = false, bpm = 120))
        assertEquals(1_500L, NoteDurationAudition.durationMs(NoteDuration.HALF, dotted = true, bpm = 120))
    }

    @Test
    fun defaultAuditionVolumeIsQuieterThanKeyboardVelocity() {
        assertEquals(34, NoteDurationAudition.velocity(0.35f))
    }
}
