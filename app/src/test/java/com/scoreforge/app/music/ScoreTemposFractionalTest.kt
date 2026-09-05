package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreTemposFractionalTest {
    @Test
    fun fractionalTempoChangeKeepsContinuousTiming() {
        val map = listOf(
            ScoreTempoChange(0f, 140),
            ScoreTempoChange(164f, 146),
            ScoreTempoChange(228.01666f, 141),
        )

        val before = ScoreTempos.secondsAtBeat(map, 228f)
        val after = ScoreTempos.secondsAtBeat(map, 229f)
        assertEquals(141, ScoreTempos.atBeat(map, 229f).bpm)
        assertEquals(1.0 * 60.0 / 141.0, after - ScoreTempos.secondsAtBeat(map, 228.01666f), 0.01)
        assertEquals(228f, ScoreTempos.beatAtSeconds(map, before), 0.01f)
    }
}
