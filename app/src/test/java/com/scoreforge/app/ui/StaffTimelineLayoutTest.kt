package com.scoreforge.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffTimelineLayoutTest {
    @Test
    fun fourMeasuresRemainDefaultViewport() {
        assertEquals(
            16f,
            StaffTimelineLayout.contentBeats(
                eventsEndBeat = 0f,
                editCursorBeat = 0f,
                playheadBeat = 0f,
            ),
            0.0001f,
        )
    }

    @Test
    fun longSongsGrowInsteadOfCompressing() {
        val beats = StaffTimelineLayout.contentBeats(
            eventsEndBeat = 108f,
            editCursorBeat = 108f,
            playheadBeat = 0f,
        )
        assertTrue(beats > 108f)
        assertEquals(112f, beats, 0.0001f)
    }

    @Test
    fun mappingKeepsConstantPixelsPerBeat() {
        val left = 100f
        val pixelsPerBeat = 42f
        assertEquals(100f, StaffTimelineLayout.xAtBeat(0f, left, pixelsPerBeat), 0.0001f)
        assertEquals(268f, StaffTimelineLayout.xAtBeat(4f, left, pixelsPerBeat), 0.0001f)
        assertEquals(
            4f,
            StaffTimelineLayout.beatAtX(268f, left, pixelsPerBeat),
            0.0001f,
        )
    }

    @Test
    fun zoomClampsToSafeRange() {
        assertEquals(StaffTimelineLayout.MIN_ZOOM, StaffTimelineLayout.clampZoom(0.1f), 0.0001f)
        assertEquals(1f, StaffTimelineLayout.clampZoom(1f), 0.0001f)
        assertEquals(StaffTimelineLayout.MAX_ZOOM, StaffTimelineLayout.clampZoom(9f), 0.0001f)
    }

    @Test
    fun entryFollowWaitsUntilCursorPassesAnchor() {
        val target = StaffTimelineLayout.entryAutoFollowTarget(
            cursorBeat = 10f,
            currentScrollPx = 0,
            maxScrollPx = 1000,
            viewportWidthPx = 1000f,
            timelineLeftPx = 80f,
            pixelsPerBeat = 56f,
        )
        assertNull(target)
    }

    @Test
    fun entryFollowAdvancesByCursorMovementAfterAnchor() {
        val first = StaffTimelineLayout.entryAutoFollowTarget(
            cursorBeat = 11f,
            currentScrollPx = 0,
            maxScrollPx = 1000,
            viewportWidthPx = 1000f,
            timelineLeftPx = 80f,
            pixelsPerBeat = 56f,
        )
        val second = StaffTimelineLayout.entryAutoFollowTarget(
            cursorBeat = 12f,
            currentScrollPx = first ?: 0,
            maxScrollPx = 1000,
            viewportWidthPx = 1000f,
            timelineLeftPx = 80f,
            pixelsPerBeat = 56f,
        )

        assertEquals(56, first)
        assertEquals(112, second)
    }
}
