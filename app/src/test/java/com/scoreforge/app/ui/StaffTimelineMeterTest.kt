package com.scoreforge.app.ui

import com.scoreforge.app.music.ScoreTimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffTimelineMeterTest {
    @Test
    fun threeFourTimelineLeavesAFullWorkingMeasure() {
        val content = StaffTimelineLayout.contentBeats(
            eventsEndBeat = 7f,
            editCursorBeat = 7f,
            playheadBeat = 0f,
            timeSignatures = listOf(ScoreTimeSignature(0f, 3, 4)),
        )
        assertTrue(content >= 10f)
    }

    @Test
    fun defaultFourFourBehaviorStillKeepsSixteenBeatViewport() {
        assertEquals(
            16f,
            StaffTimelineLayout.contentBeats(0f, 0f, 0f),
            0.0001f,
        )
    }
}
