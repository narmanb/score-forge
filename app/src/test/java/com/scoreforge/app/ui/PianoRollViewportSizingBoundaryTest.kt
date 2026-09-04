package com.scoreforge.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class PianoRollViewportSizingBoundaryTest {
    @Test
    fun defaultPitchRangeRequiresVerticalOverflow() {
        val viewportHeightDp = 300f
        val virtualHeightDp = PianoRollMapping.VISIBLE_PITCHES * 24f
        assertTrue("piano roll needs off-screen vertical content to pan", virtualHeightDp > viewportHeightDp)
    }
}
