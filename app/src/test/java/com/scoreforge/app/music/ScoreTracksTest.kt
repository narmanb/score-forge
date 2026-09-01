package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreTracksTest {
    @Test
    fun nextIdSurvivesDeletedTrackNumbers() {
        val tracks = listOf(
            ScoreTrack(id = 1, name = "Track 1"),
            ScoreTrack(id = 4, name = "Track 4"),
        )

        assertEquals(5, ScoreTracks.nextId(tracks))
        assertEquals("Track 5", ScoreTracks.newTrack(tracks).name)
    }

    @Test
    fun mutedTracksDoNotExtendPlayableEndOrFlattenIntoPlayback() {
        val audible = ScoreTrack(
            id = 1,
            name = "Audible",
            events = listOf(ScoreNote(60, NoteDuration.QUARTER, startBeat = 0f)),
        )
        val muted = ScoreTrack(
            id = 2,
            name = "Muted",
            muted = true,
            events = listOf(ScoreNote(72, NoteDuration.WHOLE, startBeat = 8f)),
        )

        assertEquals(1f, ScoreTracks.endBeat(listOf(audible, muted)), 0.0001f)
        assertEquals(listOf(60), ScoreTracks.allNotes(listOf(audible, muted)).map { it.midiPitch })
    }

    @Test
    fun soloRestrictsAudibleTracksAndMuteStillWins() {
        val normal = ScoreTrack(
            id = 1,
            name = "Normal",
            events = listOf(ScoreNote(61, NoteDuration.QUARTER, 0f)),
        )
        val solo = ScoreTrack(
            id = 2,
            name = "Solo",
            solo = true,
            events = listOf(ScoreNote(62, NoteDuration.QUARTER, 0f)),
        )
        val mutedSolo = ScoreTrack(
            id = 3,
            name = "Muted Solo",
            muted = true,
            solo = true,
            events = listOf(ScoreNote(63, NoteDuration.QUARTER, 0f)),
        )

        val tracks = listOf(normal, solo, mutedSolo)
        assertEquals(listOf(2), ScoreTracks.audibleTracks(tracks).map { it.id })
        assertEquals(listOf(62), ScoreTracks.allNotes(tracks).map { it.midiPitch })
    }

    @Test
    fun withoutSoloEveryUnmutedTrackIsAudible() {
        val tracks = listOf(
            ScoreTrack(id = 1, name = "One"),
            ScoreTrack(id = 2, name = "Two", muted = true),
            ScoreTrack(id = 3, name = "Three"),
        )
        assertEquals(listOf(1, 3), ScoreTracks.audibleTracks(tracks).map { it.id })
    }

    @Test
    fun normalizationRepairsCursorNamePresetAndMixerValues() {
        val track = ScoreTrack(
            id = 3,
            name = "  Lead\tSynth\n ",
            cursorBeat = 0f,
            presetBank = -5,
            presetProgram = 900,
            volume = 999,
            pan = -999,
            events = listOf(ScoreRest(NoteDuration.HALF, startBeat = 4f)),
        ).normalized()

        assertEquals("Lead Synth", track.name)
        assertEquals(0f, track.cursorBeat, 0.0001f)
        assertEquals(0, track.presetBank)
        assertEquals(127, track.presetProgram)
        assertEquals(ScoreTrack.MAX_VOLUME, track.volume)
        assertEquals(ScoreTrack.MIN_PAN, track.pan)
        assertFalse(track.muted)
        assertFalse(track.solo)
        assertTrue(track.events.isNotEmpty())
    }

    @Test
    fun normalizationPreservesChordAnchorBeforeEventEnd() {
        val anchor = 2f
        val first = ScoreTrack(
            id = 1,
            name = "Chord",
            cursorBeat = anchor,
            events = listOf(ScoreNote(60, NoteDuration.QUARTER, startBeat = anchor)),
        ).normalized()
        val second = first.copy(
            events = first.events + ScoreNote(64, NoteDuration.QUARTER, startBeat = first.cursorBeat),
        ).normalized()

        assertEquals(anchor, first.cursorBeat, 0.0001f)
        assertEquals(anchor, second.cursorBeat, 0.0001f)
        assertEquals(listOf(anchor, anchor), second.notes.map { it.startBeat })
    }
}
