package com.scoreforge.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PianoRollGestureRoutingTest {
    @Test
    fun `vertical grid drag belongs to page scroll`() {
        assertEquals(
            PianoRollEmptyDragTarget.PAGE,
            PianoRollMapping.emptyDragTarget(
                startX = PianoRollMapping.LEFT_GUTTER_PX + 20f,
                dragX = 2f,
                dragY = 18f,
            ),
        )
    }

    @Test
    fun `horizontal grid drag pans timeline`() {
        assertEquals(
            PianoRollEmptyDragTarget.TIMELINE,
            PianoRollMapping.emptyDragTarget(
                startX = PianoRollMapping.LEFT_GUTTER_PX + 20f,
                dragX = 18f,
                dragY = 2f,
            ),
        )
    }

    @Test
    fun `pitch gutter retains vertical pitch browsing`() {
        assertEquals(
            PianoRollEmptyDragTarget.PITCH,
            PianoRollMapping.emptyDragTarget(
                startX = PianoRollMapping.LEFT_GUTTER_PX - 1f,
                dragX = 0f,
                dragY = 18f,
            ),
        )
    }
}
