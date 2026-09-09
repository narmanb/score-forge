package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreMeasureTrackEditsTest {
    private fun note(pitch: Int, start: Float) = ScoreNote(
        midiPitch = pitch,
        duration = NoteDuration.QUARTER,
        startBeat = start,
    )

    private fun track(id: Int, name: String, vararg events: ScoreEvent) = ScoreTrack(
        id = id,
        name = name,
        events = events.toList(),
    )

    @Test
    fun copyAllTracksKeepsTrackContentSeparate() {
        val tracks = listOf(
            track(1, "Piano", note(60, 0.5f), note(61, 4.5f)),
            track(2, "Bass", note(40, 1f), note(41, 5f)),
        )

        val clipboard = ScoreMeasureTrackEdits.copyAllTracks(
            tracks = tracks,
            timeSignatures = listOf(ScoreTimeSignature()),
            beat = 0f,
            measureCount = 2,
        )

        assertEquals(2, clipboard.sourceMeasureCount)
        assertEquals(listOf(4f, 4f), clipboard.sourceMeasureLengths)
        assertEquals(listOf(60, 61), clipboard.tracks[0].clipboard.events.filterIsInstance<ScoreNote>().map { it.midiPitch })
        assertEquals(listOf(40, 41), clipboard.tracks[1].clipboard.events.filterIsInstance<ScoreNote>().map { it.midiPitch })
    }

    @Test
    fun replaceAllTracksReplacesSameDestinationRangeOnEveryTrack() {
        val source = listOf(
            track(1, "Piano", note(60, 0f)),
            track(2, "Bass", note(40, 1f)),
        )
        val clipboard = ScoreMeasureTrackEdits.copyAllTracks(
            source,
            listOf(ScoreTimeSignature()),
            beat = 0f,
            measureCount = 1,
        )
        val destination = listOf(
            track(1, "Piano", note(70, 4f), note(71, 8f)),
            track(2, "Bass", note(50, 5f), note(51, 8f)),
        )

        val replaced = ScoreMeasureTrackEdits.pasteReplaceAllTracks(
            destination,
            listOf(ScoreTimeSignature()),
            destinationBeat = 5f,
            clipboard = clipboard,
        )

        val piano = replaced[0].events.filterIsInstance<ScoreNote>()
        val bass = replaced[1].events.filterIsInstance<ScoreNote>()
        assertTrue(piano.any { it.midiPitch == 60 && it.startBeat == 4f })
        assertTrue(piano.none { it.midiPitch == 70 })
        assertTrue(piano.any { it.midiPitch == 71 && it.startBeat == 8f })
        assertTrue(bass.any { it.midiPitch == 40 && it.startBeat == 5f })
        assertTrue(bass.none { it.midiPitch == 50 })
        assertTrue(bass.any { it.midiPitch == 51 && it.startBeat == 8f })
    }

    @Test
    fun insertAllTracksShiftsTrackAddedAfterCopyEvenWithNoClipboardEvents() {
        val source = listOf(track(1, "Piano", note(60, 0f)))
        val clipboard = ScoreMeasureTrackEdits.copyAllTracks(
            source,
            listOf(ScoreTimeSignature()),
            beat = 0f,
            measureCount = 1,
        )
        val destination = listOf(
            track(1, "Piano", note(70, 4f)),
            track(2, "New Track", note(50, 4f)),
        )

        val inserted = ScoreMeasureTrackEdits.pasteInsertAllTracks(
            destination,
            listOf(ScoreTimeSignature()),
            destinationBeat = 4f,
            clipboard = clipboard,
        )

        val piano = inserted[0].events.filterIsInstance<ScoreNote>()
        val newTrack = inserted[1].events.filterIsInstance<ScoreNote>()
        assertTrue(piano.any { it.midiPitch == 60 && it.startBeat == 4f })
        assertTrue(piano.any { it.midiPitch == 70 && it.startBeat == 8f })
        assertEquals(8f, newTrack.single().startBeat, 0.001f)
    }

    @Test
    fun pasteProblemReportsTrackContainingInvalidOnset() {
        val signatures = listOf(
            ScoreTimeSignature(0f, 4, 4),
            ScoreTimeSignature(4f, 3, 4),
        )
        val clipboard = ScoreAllTracksMeasureClipboard(
            sourceMeasureLengths = listOf(4f),
            tracks = listOf(
                ScoreTrackMeasureClipboard(
                    trackId = 1,
                    clipboard = ScoreMeasureClipboard(4f, listOf(note(60, 1f))),
                ),
                ScoreTrackMeasureClipboard(
                    trackId = 2,
                    clipboard = ScoreMeasureClipboard(4f, listOf(note(40, 3.5f))),
                ),
            ),
        )

        val problem = ScoreMeasureTrackEdits.pasteProblemAt(signatures, 5f, clipboard)

        assertNotNull(problem)
        assertEquals(2, problem?.trackId)
        assertEquals(3.5f, problem?.problem?.onsetWithinMeasure ?: -1f, 0.001f)
    }

    @Test
    fun duplicateAllTracksShiftsLaterMusicOnEveryTrack() {
        val tracks = listOf(
            track(1, "Piano", note(60, 0f), note(61, 4f)),
            track(2, "Bass", note(40, 1f), note(41, 4f)),
        )

        val duplicated = ScoreMeasureTrackEdits.duplicateMeasureAllTracks(
            tracks,
            listOf(ScoreTimeSignature()),
            beat = 1f,
            copies = 1,
        )

        val piano = duplicated[0].events.filterIsInstance<ScoreNote>()
        val bass = duplicated[1].events.filterIsInstance<ScoreNote>()
        assertEquals(listOf(0f, 4f), piano.filter { it.midiPitch == 60 }.map { it.startBeat })
        assertEquals(8f, piano.single { it.midiPitch == 61 }.startBeat, 0.001f)
        assertEquals(listOf(1f, 5f), bass.filter { it.midiPitch == 40 }.map { it.startBeat })
        assertEquals(8f, bass.single { it.midiPitch == 41 }.startBeat, 0.001f)
    }
}
