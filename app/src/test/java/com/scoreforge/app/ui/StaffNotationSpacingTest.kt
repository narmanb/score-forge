package com.scoreforge.app.ui

import com.scoreforge.app.music.ScoreKeySignature
import com.scoreforge.app.music.ScoreTimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffNotationSpacingTest {
    @Test
    fun keyChangeReservesEnoughRoomAndKeepsLaterBeatSpacingStable() {
        val gaps = StaffNotationSpacing.gaps(
            timeSignatures = listOf(ScoreTimeSignature()),
            keySignatures = listOf(
                ScoreKeySignature(0f, -3, false), // E-flat major
                ScoreKeySignature(4f, 3, false),  // A major: 3 naturals + 3 sharps
            ),
        )
        val gap = gaps.single()
        assertEquals(4f, gap.beat, 0.0001f)
        assertTrue(gap.widthBeats > 1.5f)

        val left = 100f
        val pixelsPerBeat = 80f
        val barlineX = StaffNotationSpacing.xAtBeat(4f, left, pixelsPerBeat, gaps, false)
        val firstNoteZoneX = StaffNotationSpacing.xAtBeat(4f, left, pixelsPerBeat, gaps, true)
        assertTrue(firstNoteZoneX - barlineX > 120f)

        val beatFiveX = StaffNotationSpacing.xAtBeat(5f, left, pixelsPerBeat, gaps, true)
        assertEquals(pixelsPerBeat, beatFiveX - firstNoteZoneX, 0.0001f)
    }

    @Test
    fun tappingInsideReservedGapMapsToChangeBeat() {
        val gaps = StaffNotationSpacing.gaps(
            timeSignatures = listOf(ScoreTimeSignature()),
            keySignatures = listOf(
                ScoreKeySignature(0f, 1, false),
                ScoreKeySignature(8f, -3, false),
            ),
        )
        val left = 120f
        val pixelsPerBeat = 60f
        val start = StaffNotationSpacing.xAtBeat(8f, left, pixelsPerBeat, gaps, false)
        val end = StaffNotationSpacing.xAtBeat(8f, left, pixelsPerBeat, gaps, true)

        assertEquals(8f, StaffNotationSpacing.beatAtX((start + end) / 2f, left, pixelsPerBeat, gaps), 0.0001f)
        assertEquals(8.5f, StaffNotationSpacing.beatAtX(end + pixelsPerBeat * 0.5f, left, pixelsPerBeat, gaps), 0.0001f)
    }

    @Test
    fun simultaneousMeterAndKeyChangeGetsCombinedGap() {
        val keyOnly = StaffNotationSpacing.gaps(
            timeSignatures = listOf(ScoreTimeSignature()),
            keySignatures = listOf(
                ScoreKeySignature(0f, 0, false),
                ScoreKeySignature(4f, -2, false),
            ),
        ).single().widthBeats

        val combined = StaffNotationSpacing.gaps(
            timeSignatures = listOf(
                ScoreTimeSignature(),
                ScoreTimeSignature(4f, 6, 8),
            ),
            keySignatures = listOf(
                ScoreKeySignature(0f, 0, false),
                ScoreKeySignature(4f, -2, false),
            ),
        ).single().widthBeats

        assertTrue(combined > keyOnly)
    }
}