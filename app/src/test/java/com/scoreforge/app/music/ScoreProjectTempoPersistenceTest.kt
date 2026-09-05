package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreProjectTempoPersistenceTest {
    @Test
    fun tempoMapRoundTripsWithoutChangingExistingFormatVersion() {
        val snapshot = ScoreProjectSnapshot(
            events = emptyList(),
            bpm = 120,
            tempoChanges = listOf(
                ScoreTempoChange(0f, 120),
                ScoreTempoChange(4f, 90),
                ScoreTempoChange(12.5f, 160),
            ),
        )
        val decoded = requireNotNull(ScoreProjectCodec.decode(ScoreProjectCodec.encode(snapshot)))
        assertEquals(snapshot.tempoChanges, decoded.effectiveTempoChanges())
        assertEquals(120, decoded.bpm)
    }

    @Test
    fun oldV2ProjectWithoutTempoRowsUsesStoredBpm() {
        val raw = """SCOREFORGE	2
PROJECT_NAME	Old
BPM	135
ACTIVE_TRACK	0
TRACK	1	Track 1	0.0	0	-1	-1	0	100	0	AUTO
END_TRACK
"""
        val decoded = requireNotNull(ScoreProjectCodec.decode(raw))
        assertEquals(listOf(ScoreTempoChange(0f, 135)), decoded.effectiveTempoChanges())
    }
}
