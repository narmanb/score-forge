package com.scoreforge.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PianoRollMappingTest {
    @Test
    fun octaveShiftProducesThreeOctaveWindow() {
        assertEquals(48, PianoRollMapping.lowPitch(0))
        assertEquals(83, PianoRollMapping.highPitch(0))
        assertEquals(60, PianoRollMapping.lowPitch(1))
        assertEquals(95, PianoRollMapping.highPitch(1))
    }

    @Test
    fun beatMappingRoundTripsAcrossEditableArea() {
        val width = 1000f
        val visibleBeats = 16f
        val beat = 7.25f
        val x = PianoRollMapping.xAtBeat(beat, visibleBeats, width)
        val restored = PianoRollMapping.beatAtX(x, visibleBeats, width)
        assertEquals(beat, restored, 0.0001f)
    }

    @Test
    fun pitchRowsMapHighNotesToTopAndLowNotesToBottom() {
        val low = 48
        val high = 83
        val height = 360f

        assertEquals(high, PianoRollMapping.pitchAtY(1f, low, high, height))
        assertEquals(low, PianoRollMapping.pitchAtY(height - 1f, low, high, height))
        assertTrue(
            PianoRollMapping.yCenterForPitch(high, low, high, height) <
                PianoRollMapping.yCenterForPitch(low, low, high, height)
        )
    }

    @Test
    fun pitchMappingRoundTripsEveryVisiblePitch() {
        val low = 36
        val high = 71
        val height = 720f
        for (pitch in low..high) {
            val y = PianoRollMapping.yCenterForPitch(pitch, low, high, height)
            assertEquals(pitch, PianoRollMapping.pitchAtY(y, low, high, height))
        }
    }
}
