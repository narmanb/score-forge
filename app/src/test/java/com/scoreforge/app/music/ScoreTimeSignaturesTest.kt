package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreTimeSignaturesTest {
    @Test
    fun threeFourUsesThreeQuarterNoteBeatsPerMeasure() {
        val signatures = listOf(ScoreTimeSignature(0f, 3, 4))

        assertEquals(3f, signatures.single().beatsPerMeasure, 0.0001f)
        assertEquals(2, ScoreTimeSignatures.measureCount(signatures, throughBeat = 6f))
        assertEquals(12f, ScoreTimeline.visibleBeats(emptyList(), timeSignatures = signatures), 0.0001f)
    }

    @Test
    fun sixEightAlsoOccupiesThreeQuarterNoteBeatsPerMeasure() {
        val signatures = listOf(ScoreTimeSignature(0f, 6, 8))

        assertEquals(3f, signatures.single().beatsPerMeasure, 0.0001f)
        assertEquals(
            listOf(0f, 3f, 6f, 9f),
            ScoreTimeSignatures.measureBoundaries(signatures, throughBeat = 9f),
        )
    }

    @Test
    fun meterChangesResetMeasureBoundariesAtTheChange() {
        val signatures = listOf(
            ScoreTimeSignature(0f, 4, 4),
            ScoreTimeSignature(8f, 3, 4),
        )

        assertEquals(
            listOf(0f, 4f, 8f, 11f, 14f),
            ScoreTimeSignatures.measureBoundaries(signatures, throughBeat = 14f),
        )
        assertEquals(4, ScoreTimeSignatures.measureCount(signatures, throughBeat = 14f))
        assertEquals(ScoreTimeSignature(0f, 4, 4), ScoreTimeSignatures.atBeat(signatures, 7.99f))
        assertEquals(ScoreTimeSignature(8f, 3, 4), ScoreTimeSignatures.atBeat(signatures, 8f))
    }

    @Test
    fun unusualMidMeasureChangeStartsANewMeasureImmediately() {
        val signatures = listOf(
            ScoreTimeSignature(0f, 4, 4),
            ScoreTimeSignature(6f, 5, 8),
        )

        assertEquals(
            listOf(0f, 4f, 6f, 8.5f, 11f),
            ScoreTimeSignatures.measureBoundaries(signatures, throughBeat = 11f),
        )
    }

    @Test
    fun missingInitialSignatureGetsSafeFourFourDefault() {
        val normalized = ScoreTimeSignatures.normalize(
            listOf(ScoreTimeSignature(8f, 7, 8))
        )

        assertEquals(ScoreTimeSignature(), normalized[0])
        assertEquals(ScoreTimeSignature(8f, 7, 8), normalized[1])
    }
}
