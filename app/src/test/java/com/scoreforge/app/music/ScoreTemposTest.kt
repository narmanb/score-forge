package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreTemposTest {
    @Test
    fun normalizeEnsuresStartTempoAndMergesSameBeat() {
        val normalized = ScoreTempos.normalize(
            listOf(
                ScoreTempoChange(8f, 90),
                ScoreTempoChange(8f, 100),
            )
        )

        assertEquals(2, normalized.size)
        assertEquals(0f, normalized[0].startBeat, 0.0001f)
        assertEquals(120, normalized[0].bpm)
        assertEquals(8f, normalized[1].startBeat, 0.0001f)
        assertEquals(100, normalized[1].bpm)
    }

    @Test
    fun tempoAtBeatUsesLatestChange() {
        val map = listOf(
            ScoreTempoChange(0f, 120),
            ScoreTempoChange(4f, 60),
            ScoreTempoChange(8f, 180),
        )

        assertEquals(120, ScoreTempos.atBeat(map, 3.99f).bpm)
        assertEquals(60, ScoreTempos.atBeat(map, 4f).bpm)
        assertEquals(180, ScoreTempos.atBeat(map, 9f).bpm)
    }

    @Test
    fun beatAndSecondsConversionsRespectTempoChanges() {
        val map = listOf(
            ScoreTempoChange(0f, 120),
            ScoreTempoChange(4f, 60),
            ScoreTempoChange(6f, 240),
        )

        // 4 beats at 120 BPM = 2s, then 2 beats at 60 BPM = 2s, then 4 beats at 240 = 1s.
        assertEquals(2.0, ScoreTempos.secondsAtBeat(map, 4f), 0.0001)
        assertEquals(4.0, ScoreTempos.secondsAtBeat(map, 6f), 0.0001)
        assertEquals(5.0, ScoreTempos.secondsAtBeat(map, 10f), 0.0001)
        assertEquals(10f, ScoreTempos.beatAtSeconds(map, 5.0), 0.001f)
        assertTrue(ScoreTempos.durationSeconds(map, 3f, 7f) > 2.0)
    }

    @Test
    fun removingStartTempoIsIgnored() {
        val map = listOf(ScoreTempoChange(0f, 100), ScoreTempoChange(4f, 140))
        val result = ScoreTempos.withoutChange(map, 0f)
        assertEquals(100, result.first().bpm)
        assertEquals(2, result.size)
    }
}
