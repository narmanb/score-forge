package com.scoreforge.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class StepChordModeTest {
    @Test
    fun chordModeCyclesOffManualAutoOff() {
        assertEquals(StepChordMode.MANUAL, StepChordMode.OFF.next())
        assertEquals(StepChordMode.AUTO, StepChordMode.MANUAL.next())
        assertEquals(StepChordMode.OFF, StepChordMode.AUTO.next())
    }

    @Test
    fun onlyActiveChordModesHoldTheStepCursor() {
        assertFalse(StepChordMode.OFF.holdsStepCursor)
        assertTrue(StepChordMode.MANUAL.holdsStepCursor)
        assertTrue(StepChordMode.AUTO.holdsStepCursor)
    }

    @Test
    fun autoAdvanceRequiresFinalReleaseAfterAPlayedNote() {
        assertTrue(
            AutoChordAdvancePolicy.shouldAdvance(
                mode = StepChordMode.AUTO,
                hadPressedPointers = true,
                hasPressedPointers = false,
                chordHadNote = true,
            )
        )
        assertFalse(
            AutoChordAdvancePolicy.shouldAdvance(
                mode = StepChordMode.MANUAL,
                hadPressedPointers = true,
                hasPressedPointers = false,
                chordHadNote = true,
            )
        )
        assertFalse(
            AutoChordAdvancePolicy.shouldAdvance(
                mode = StepChordMode.AUTO,
                hadPressedPointers = true,
                hasPressedPointers = true,
                chordHadNote = true,
            )
        )
        assertFalse(
            AutoChordAdvancePolicy.shouldAdvance(
                mode = StepChordMode.AUTO,
                hadPressedPointers = true,
                hasPressedPointers = false,
                chordHadNote = false,
            )
        )
    }
}
