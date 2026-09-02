package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreMetronomeTest {
    @Test
    fun threeFourClicksQuarterNoteBeatsWithDownbeats() {
        val clicks = ScoreMetronome.clicks(
            listOf(ScoreTimeSignature(0f, 3, 4)),
            throughBeat = 6f,
        )

        assertEquals(
            listOf(0f, 1f, 2f, 3f, 4f, 5f, 6f),
            clicks.map { it.beat },
        )
        assertEquals(MetronomeAccent.DOWNBEAT, clicks[0].accent)
        assertEquals(MetronomeAccent.DOWNBEAT, clicks[3].accent)
        assertEquals(MetronomeAccent.DOWNBEAT, clicks[6].accent)
    }

    @Test
    fun sixEightUsesEighthNoteClicksAndThreePlusThreeGrouping() {
        val clicks = ScoreMetronome.clicks(
            listOf(ScoreTimeSignature(0f, 6, 8)),
            throughBeat = 3f,
        )

        assertEquals(
            listOf(0f, 0.5f, 1f, 1.5f, 2f, 2.5f, 3f),
            clicks.map { it.beat },
        )
        assertEquals(MetronomeAccent.DOWNBEAT, clicks[0].accent)
        assertEquals(MetronomeAccent.GROUP, clicks[3].accent)
        assertEquals(MetronomeAccent.DOWNBEAT, clicks[6].accent)
    }

    @Test
    fun oddMetersUseDocumentedDefaultGroupings() {
        assertEquals(
            setOf(3),
            ScoreMetronome.secondaryGroupStarts(ScoreTimeSignature(0f, 5, 8)),
        )
        assertEquals(
            setOf(3, 5),
            ScoreMetronome.secondaryGroupStarts(ScoreTimeSignature(0f, 7, 8)),
        )
    }

    @Test
    fun meterChangesImmediatelyDriveNewClickSpacing() {
        val clicks = ScoreMetronome.clicks(
            listOf(
                ScoreTimeSignature(0f, 3, 4),
                ScoreTimeSignature(6f, 5, 8),
                ScoreTimeSignature(11f, 6, 8),
            ),
            throughBeat = 12f,
        )

        val aroundFirstChange = clicks.filter { it.beat in 5f..7f }
        assertEquals(
            listOf(5f, 6f, 6.5f, 7f),
            aroundFirstChange.map { it.beat },
        )
        assertEquals(MetronomeAccent.DOWNBEAT, aroundFirstChange[1].accent)

        val aroundSecondChange = clicks.filter { it.beat in 10.5f..12f }
        assertEquals(
            listOf(10.5f, 11f, 11.5f, 12f),
            aroundSecondChange.map { it.beat },
        )
        assertEquals(MetronomeAccent.DOWNBEAT, aroundSecondChange[1].accent)
    }
}
