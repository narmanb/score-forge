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
    fun normalizationRepairsCursorAndUnsafeName() {
        val track = ScoreTrack(
            id = 3,
            name = "  Lead\tSynth\n ",
            cursorBeat = 0f,
            presetBank = -5,
            presetProgram = 900,
            events = listOf(ScoreRest(NoteDuration.HALF, startBeat = 4f)),
        ).normalized()

        assertEquals("Lead Synth", track.name)
        assertEquals(6f, track.cursorBeat, 0.0001f)
        assertEquals(0, track.presetBank)
        assertEquals(127, track.presetProgram)
        assertFalse(track.muted)
        assertTrue(track.events.isNotEmpty())
    }
}
