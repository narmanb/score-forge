package com.scoreforge.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PianoRollFocusTest {
    @Test
    fun `note median drives piano roll focus`() {
        assertEquals(64, PianoRollMapping.focusPitch(listOf(48, 60, 64, 67, 84), octaveShift = 3))
    }

    @Test
    fun `empty score falls back to keyboard octave`() {
        assertEquals(65, PianoRollMapping.focusPitch(emptyList(), octaveShift = 0))
        assertEquals(77, PianoRollMapping.focusPitch(emptyList(), octaveShift = 1))
    }

    @Test
    fun `full pitch range always includes midi notes`() {
        assertEquals(0, PianoRollMapping.FULL_LOW_PITCH)
        assertEquals(127, PianoRollMapping.FULL_HIGH_PITCH)
    }
}
