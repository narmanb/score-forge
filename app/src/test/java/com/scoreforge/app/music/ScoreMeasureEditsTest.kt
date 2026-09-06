package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreMeasureEditsTest {
    private fun note(
        pitch: Int,
        start: Float,
        duration: NoteDuration = NoteDuration.QUARTER,
        dotted: Boolean = false,
        velocity: Int = 96,
        tie: Boolean = false,
        articulation: NoteArticulation = NoteArticulation.NORMAL,
    ) = ScoreNote(
        midiPitch = pitch,
        duration = duration,
        startBeat = start,
        dotted = dotted,
        velocity = velocity,
        tieToNext = tie,
        articulation = articulation,
    )

    @Test
    fun boundsFollowTimeSignatureMap() {
        val signatures = listOf(
            ScoreTimeSignature(0f, 3, 4),
            ScoreTimeSignature(6f, 5, 8),
        )

        assertEquals(0f, ScoreMeasureEdits.boundsAt(signatures, 2f).startBeat, 0.001f)
        assertEquals(3f, ScoreMeasureEdits.boundsAt(signatures, 2f).endBeat, 0.001f)
        assertEquals(6f, ScoreMeasureEdits.boundsAt(signatures, 6.5f).startBeat, 0.001f)
        assertEquals(8.5f, ScoreMeasureEdits.boundsAt(signatures, 6.5f).endBeat, 0.001f)
    }

    @Test
    fun copyKeepsOnlyEventsStartingInsideMeasureAndPreservesProperties() {
        val events = listOf(
            note(60, 0f),
            note(
                pitch = 64,
                start = 3.5f,
                duration = NoteDuration.HALF,
                dotted = true,
                velocity = 71,
                articulation = NoteArticulation.ACCENT,
            ),
            ScoreRest(NoteDuration.EIGHTH, startBeat = 5f, dotted = true),
            note(67, 8f),
        )

        val clipboard = ScoreMeasureEdits.copyMeasure(events, listOf(ScoreTimeSignature()), 4.25f)

        assertEquals(4f, clipboard.sourceLengthBeats, 0.001f)
        assertEquals(2, clipboard.events.size)
        val copiedNote = clipboard.events[0] as ScoreNote
        assertEquals(64, copiedNote.midiPitch)
        assertEquals(0f, copiedNote.startBeat, 0.001f)
        assertEquals(NoteDuration.HALF, copiedNote.duration)
        assertTrue(copiedNote.dotted)
        assertEquals(71, copiedNote.velocity)
        assertEquals(NoteArticulation.ACCENT, copiedNote.articulation)
        val copiedRest = clipboard.events[1] as ScoreRest
        assertEquals(1f, copiedRest.startBeat, 0.001f)
        assertTrue(copiedRest.dotted)
    }

    @Test
    fun copyDropsTieThatTargetsOutsideMeasure() {
        val events = listOf(
            note(60, 3f, tie = true),
            note(60, 4f),
        )

        val clipboard = ScoreMeasureEdits.copyMeasure(events, listOf(ScoreTimeSignature()), 1f)

        val copied = clipboard.events.single() as ScoreNote
        assertFalse(copied.tieToNext)
    }

    @Test
    fun pasteReplaceRemovesDestinationStartsWithoutTouchingOutsideEvents() {
        val events = listOf(
            note(50, 0f),
            note(51, 4f),
            ScoreRest(NoteDuration.QUARTER, startBeat = 5f),
            note(52, 8f),
        )
        val clipboard = ScoreMeasureClipboard(
            sourceLengthBeats = 4f,
            events = listOf(
                note(70, 0f, velocity = 88),
                ScoreRest(NoteDuration.HALF, startBeat = 2f, dotted = true),
            ),
        )

        val pasted = ScoreMeasureEdits.pasteReplace(
            events,
            listOf(ScoreTimeSignature()),
            destinationBeat = 5f,
            clipboard = clipboard,
        )

        assertEquals(4, pasted.size)
        assertTrue(pasted.any { it is ScoreNote && it.midiPitch == 50 && it.startBeat == 0f })
        assertTrue(pasted.any { it is ScoreNote && it.midiPitch == 52 && it.startBeat == 8f })
        assertFalse(pasted.any { it is ScoreNote && it.midiPitch == 51 })
        assertTrue(pasted.any { it is ScoreNote && it.midiPitch == 70 && it.startBeat == 4f && it.velocity == 88 })
        assertTrue(pasted.any { it is ScoreRest && it.startBeat == 6f && it.dotted })
    }

    @Test
    fun pasteRejectsCopiedOnsetThatWouldStartBeyondShorterDestinationBar() {
        val signatures = listOf(
            ScoreTimeSignature(0f, 4, 4),
            ScoreTimeSignature(4f, 3, 4),
        )
        val original = listOf(
            note(50, 4f),
            note(51, 6f),
        )
        val clipboard = ScoreMeasureClipboard(
            sourceLengthBeats = 4f,
            events = listOf(
                note(70, 0f),
                note(71, 3.5f),
            ),
        )

        assertFalse(ScoreMeasureEdits.canPasteAt(signatures, 5f, clipboard))
        assertEquals(original, ScoreMeasureEdits.pasteReplace(original, signatures, 5f, clipboard))
    }

    @Test
    fun duplicateInsertsCopiesAndShiftsLaterEvents() {
        val events = listOf(
            note(60, 0f),
            ScoreRest(NoteDuration.QUARTER, startBeat = 2f),
            note(72, 4f),
        )

        val duplicated = ScoreMeasureEdits.duplicateMeasure(
            events,
            listOf(ScoreTimeSignature()),
            beat = 1f,
            copies = 2,
        )

        assertEquals(7, duplicated.size)
        val starts60 = duplicated.filterIsInstance<ScoreNote>()
            .filter { it.midiPitch == 60 }
            .map { it.startBeat }
        assertEquals(listOf(0f, 4f, 8f), starts60)
        val shiftedLater = duplicated.filterIsInstance<ScoreNote>().single { it.midiPitch == 72 }
        assertEquals(12f, shiftedLater.startBeat, 0.001f)
    }

    @Test
    fun duplicateUsesActualThreeFourMeasureLength() {
        val signatures = listOf(ScoreTimeSignature(0f, 3, 4))
        val events = listOf(
            note(60, 0f),
            note(62, 3f),
        )

        val duplicated = ScoreMeasureEdits.duplicateMeasure(events, signatures, beat = 1f, copies = 1)

        val copied = duplicated.filterIsInstance<ScoreNote>().single { it.midiPitch == 60 && it.startBeat > 0f }
        val shifted = duplicated.filterIsInstance<ScoreNote>().single { it.midiPitch == 62 }
        assertEquals(3f, copied.startBeat, 0.001f)
        assertEquals(6f, shifted.startBeat, 0.001f)
    }
}
