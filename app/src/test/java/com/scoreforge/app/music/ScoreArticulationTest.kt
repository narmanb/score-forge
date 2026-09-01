package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreArticulationTest {
    @Test
    fun staccatoShortensPlaybackWithoutChangingWrittenDuration() {
        val note = ScoreNote(
            midiPitch = 60,
            duration = NoteDuration.QUARTER,
            articulation = NoteArticulation.STACCATO,
        )
        val notes = listOf(note)

        assertEquals(1f, note.effectiveBeats, 0.0001f)
        assertEquals(0.5f, ScoreArticulations.playbackEndBeat(notes, 0), 0.0001f)
    }

    @Test
    fun accentRaisesPlaybackVelocityAndUsesShorterGate() {
        val note = ScoreNote(
            midiPitch = 64,
            duration = NoteDuration.QUARTER,
            velocity = 96,
            articulation = NoteArticulation.ACCENT,
        )
        val notes = listOf(note)

        assertEquals(118, ScoreArticulations.playbackVelocity(note))
        assertEquals(0.9f, ScoreArticulations.playbackEndBeat(notes, 0), 0.0001f)
    }

    @Test
    fun legatoBridgesAnAdjacentDifferentPitch() {
        val notes = listOf(
            ScoreNote(
                midiPitch = 60,
                duration = NoteDuration.QUARTER,
                startBeat = 0f,
                articulation = NoteArticulation.LEGATO,
            ),
            ScoreNote(62, NoteDuration.QUARTER, startBeat = 1f),
        )

        assertEquals(1.08f, ScoreArticulations.playbackEndBeat(notes, 0), 0.0001f)
    }

    @Test
    fun codecPreservesInputAndPerNoteArticulation() {
        val note = ScoreNote(
            midiPitch = 67,
            duration = NoteDuration.HALF,
            articulation = NoteArticulation.STACCATO,
        )
        val original = ScoreProjectSnapshot(
            events = listOf(note),
            selectedArticulation = NoteArticulation.LEGATO,
        )

        val decoded = requireNotNull(ScoreProjectCodec.decode(ScoreProjectCodec.encode(original)))
        assertEquals(NoteArticulation.LEGATO, decoded.selectedArticulation)
        assertEquals(
            NoteArticulation.STACCATO,
            (decoded.tracks.single().events.single() as ScoreNote).articulation,
        )
    }

    @Test
    fun olderVersionTwoNoteDefaultsToNormalArticulation() {
        val decoded = requireNotNull(
            ScoreProjectCodec.decode(
                """
                SCOREFORGE\t2
                BPM\t120
                ACTIVE_TRACK\t0
                TRACK\t1\tTrack 1\t1.0\t0\t-1\t-1
                N\t60\tQUARTER\t0.0\t96
                END_TRACK
                """.trimIndent().replace("\\t", "\t")
            )
        )

        assertEquals(NoteArticulation.NORMAL, decoded.selectedArticulation)
        assertEquals(
            NoteArticulation.NORMAL,
            (decoded.tracks.single().events.single() as ScoreNote).articulation,
        )
    }
}
