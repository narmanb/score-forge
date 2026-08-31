package com.scoreforge.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StaffTimeMappingTest {
    private val width = 1050f
    private val visibleBeats = 16f

    @Test
    fun staffMarginsMapToTimelineEdges() {
        assertEquals(
            0f,
            StaffTimeMapping.beatAtX(StaffTimeMapping.LEFT_MARGIN_PX, visibleBeats, width),
            0.0001f,
        )
        assertEquals(
            visibleBeats,
            StaffTimeMapping.beatAtX(
                width - StaffTimeMapping.RIGHT_MARGIN_PX,
                visibleBeats,
                width,
            ),
            0.0001f,
        )
    }

    @Test
    fun beatAndPixelMappingRoundTrip() {
        listOf(0f, 1f, 4f, 7.5f, 12f, 16f).forEach { beat ->
            val x = StaffTimeMapping.xAtBeat(beat, visibleBeats, width)
            assertEquals(
                beat,
                StaffTimeMapping.beatAtX(x, visibleBeats, width),
                0.0001f,
            )
        }
    }

    @Test
    fun pointsOutsideStaffMarginsClampToTimeline() {
        assertEquals(0f, StaffTimeMapping.beatAtX(-500f, visibleBeats, width), 0.0001f)
        assertEquals(16f, StaffTimeMapping.beatAtX(5000f, visibleBeats, width), 0.0001f)
    }
}
