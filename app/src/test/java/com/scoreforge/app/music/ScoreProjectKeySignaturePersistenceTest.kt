package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreProjectKeySignaturePersistenceTest {
    @Test
    fun keySignatureMapRoundTripsWithProject() {
        val keys = listOf(
            ScoreKeySignature(startBeat = 0f, fifths = 1, minor = false),
            ScoreKeySignature(startBeat = 4f, fifths = -3, minor = false),
            ScoreKeySignature(startBeat = 8f, fifths = -1, minor = true),
        )
        val snapshot = ScoreProjectSnapshot(
            events = emptyList(),
            projectName = "Key Test",
            keySignatures = keys,
        )

        val decoded = ScoreProjectCodec.decode(ScoreProjectCodec.encode(snapshot))

        assertEquals(keys, decoded?.effectiveKeySignatures())
    }

    @Test
    fun oldV2ProjectWithoutKeySignatureDefaultsToCmajor() {
        val raw = listOf(
            "SCOREFORGE\t2",
            "PROJECT_NAME\tLegacy V2",
            "BPM\t120",
            "DURATION\tQUARTER",
            "DOTTED_INPUT\t0",
            "ARTICULATION\tNORMAL",
            "PIANO_OCTAVE\t0",
            "STAFF_SHARP\t0",
            "ACTIVE_TRACK\t0",
            "TRACK\t1\tTrack 1\t0.0\t0\t-1\t-1\t0\t100\t0",
            "END_TRACK",
        ).joinToString("\n")

        val decoded = ScoreProjectCodec.decode(raw)

        assertEquals(listOf(ScoreKeySignatures.DEFAULT), decoded?.effectiveKeySignatures())
    }
}
