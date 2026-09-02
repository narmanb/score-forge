package com.scoreforge.app.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreProjectMetronomePersistenceTest {
    @Test
    fun metronomeSettingRoundTripsWithProject() {
        val snapshot = ScoreProjectSnapshot(
            events = emptyList(),
            metronomeEnabled = true,
        )

        val decoded = ScoreProjectCodec.decode(ScoreProjectCodec.encode(snapshot))
        assertTrue(decoded?.metronomeEnabled == true)
    }

    @Test
    fun oldV2ProjectWithoutMetronomeLineDefaultsOff() {
        val raw = """
            SCOREFORGE\t2
            PROJECT_NAME\tLegacy V2
            BPM\t120
            DURATION\tQUARTER
            DOTTED_INPUT\t0
            ARTICULATION\tNORMAL
            PIANO_OCTAVE\t0
            STAFF_SHARP\t0
            ACTIVE_TRACK\t0
            TRACK\t1\tTrack 1\t0.0\t0\t-1\t-1\t0\t100\t0
            END_TRACK
        """.trimIndent()

        val decoded = ScoreProjectCodec.decode(raw)
        assertFalse(decoded?.metronomeEnabled ?: true)
    }
}
