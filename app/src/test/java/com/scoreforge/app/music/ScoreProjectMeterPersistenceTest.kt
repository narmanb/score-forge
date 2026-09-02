package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreProjectMeterPersistenceTest {
    @Test
    fun snapshotRecoversMeterMapFromEditorTracksWhenNotPassedExplicitly() {
        val signatures = listOf(
            ScoreTimeSignature(0f, 3, 4),
            ScoreTimeSignature(6f, 6, 8),
        )
        val track = ScoreTrack(
            id = 1,
            name = "Imported Piano",
            events = listOf(ScoreNote(60, NoteDuration.QUARTER)),
            timeSignatures = signatures,
        )

        val snapshot = ScoreProjectSnapshot(
            events = track.events,
            tracks = listOf(track),
        )

        assertEquals(signatures, snapshot.timeSignatures)
        assertEquals(signatures, snapshot.effectiveTracks().single().timeSignatures)
    }

    @Test
    fun explicitProjectMeterMapIsInjectedIntoEveryEditorTrack() {
        val signatures = listOf(
            ScoreTimeSignature(0f, 7, 8),
            ScoreTimeSignature(7f, 4, 4),
        )
        val snapshot = ScoreProjectSnapshot(
            events = emptyList(),
            tracks = listOf(
                ScoreTrack(id = 1, name = "One"),
                ScoreTrack(id = 2, name = "Two"),
            ),
            timeSignatures = signatures,
        )

        val editorTracks = snapshot.effectiveTracks()

        assertEquals(2, editorTracks.size)
        assertEquals(signatures, editorTracks[0].timeSignatures)
        assertEquals(signatures, editorTracks[1].timeSignatures)
    }

    @Test
    fun editorStyleSnapshotRoundTripDoesNotLoseImportedMeterChanges() {
        val signatures = listOf(
            ScoreTimeSignature(0f, 4, 4),
            ScoreTimeSignature(8f, 5, 4),
        )
        val imported = ScoreProjectSnapshot(
            events = listOf(ScoreNote(64, NoteDuration.HALF)),
            tracks = listOf(ScoreTrack(id = 1, name = "Lead")),
            timeSignatures = signatures,
        )
        val editorTracks = imported.effectiveTracks()

        // Mirrors ComposerScreen.currentProjectSnapshot(): it supplies the editor tracks but does not
        // separately pass a meter map. The snapshot must recover the imported meter map from them.
        val editorSnapshot = ScoreProjectSnapshot(
            events = editorTracks[0].events,
            tracks = editorTracks,
            activeTrackIndex = 0,
        )
        val decoded = requireNotNull(
            ScoreProjectCodec.decode(ScoreProjectCodec.encode(editorSnapshot))
        )

        assertEquals(signatures, editorSnapshot.timeSignatures)
        assertEquals(signatures, decoded.timeSignatures)
        assertEquals(signatures, decoded.effectiveTracks()[0].timeSignatures)
    }
}
