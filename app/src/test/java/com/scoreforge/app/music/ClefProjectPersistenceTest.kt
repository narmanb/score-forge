package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ClefProjectPersistenceTest {
    @Test fun trackClefRoundTripsThroughProjectCodec() {
        val track = ScoreTrack(id = 1, name = "Bass", clefMode = ScoreClefMode.BASS)
        val snapshot = ScoreProjectSnapshot(events = emptyList(), tracks = listOf(track))
        val decoded = ScoreProjectCodec.decode(ScoreProjectCodec.encode(snapshot))
        assertNotNull(decoded)
        assertEquals(ScoreClefMode.BASS, decoded!!.effectiveTracks().first().clefMode)
    }

    @Test fun oldTrackHeaderWithoutClefDefaultsToAuto() {
        val oldV2 = """SCOREFORGE	2
PROJECT_NAME	Old
BPM	120
ACTIVE_TRACK	0
TRACK	1	Track 1	0.0	0	-1	-1	0	100	0
END_TRACK
"""
        val decoded = ScoreProjectCodec.decode(oldV2)
        assertNotNull(decoded)
        assertEquals(ScoreClefMode.AUTO, decoded!!.effectiveTracks().first().clefMode)
    }
}
