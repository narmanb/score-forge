package com.scoreforge.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PianoTouchLayoutTest {
    private val width = 1400f
    private val height = 200f
    private val whiteWidth = width / PianoTouchLayout.whitePitches.size

    @Test
    fun lowerKeyboardAreaSelectsWhiteKeys() {
        assertEquals(60, PianoTouchLayout.pitchAt(whiteWidth * 0.5f, 180f, width, height))
        assertEquals(62, PianoTouchLayout.pitchAt(whiteWidth * 1.5f, 180f, width, height))
        assertEquals(83, PianoTouchLayout.pitchAt(width - 1f, 180f, width, height))
    }

    @Test
    fun blackKeysWinInsideTheirOverlappingArea() {
        val cSharpCenter = whiteWidth * (
            PianoTouchLayout.BLACK_KEY_X_OFFSET_FRACTION +
                PianoTouchLayout.BLACK_KEY_WIDTH_FRACTION / 2f
            )

        assertEquals(61, PianoTouchLayout.pitchAt(cSharpCenter, 40f, width, height))
    }

    @Test
    fun lowerAreaUsesUnderlyingWhiteKeysAcrossBlackKeyOverlap() {
        val leftSideOfCSharp = whiteWidth * 0.85f
        val rightSideOfCSharp = whiteWidth * 1.15f

        assertEquals(60, PianoTouchLayout.pitchAt(leftSideOfCSharp, 190f, width, height))
        assertEquals(62, PianoTouchLayout.pitchAt(rightSideOfCSharp, 190f, width, height))
    }

    @Test
    fun octaveShiftChangesEveryKeyByExactlyTwelveSemitones() {
        PianoTouchLayout.whitePitches.forEach { pitch ->
            assertEquals(
                PianoTouchLayout.shiftedPitch(pitch, 0) + 12,
                PianoTouchLayout.shiftedPitch(pitch, 1),
            )
        }
        PianoTouchLayout.blackKeys.forEach { key ->
            assertEquals(
                PianoTouchLayout.shiftedPitch(key.midiPitch, 0) + 12,
                PianoTouchLayout.shiftedPitch(key.midiPitch, 1),
            )
        }
    }

    @Test
    fun octaveShiftIsClampedToSupportedKeyboardRange() {
        assertEquals(12, PianoTouchLayout.shiftedPitch(60, -99))
        assertEquals(96, PianoTouchLayout.shiftedPitch(60, 99))
    }

    @Test
    fun pointsOutsideKeyboardReturnNull() {
        assertNull(PianoTouchLayout.pitchAt(-1f, 50f, width, height))
        assertNull(PianoTouchLayout.pitchAt(width, 50f, width, height))
        assertNull(PianoTouchLayout.pitchAt(20f, height, width, height))
    }
}
