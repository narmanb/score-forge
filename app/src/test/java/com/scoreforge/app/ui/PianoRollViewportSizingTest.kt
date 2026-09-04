package com.scoreforge.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class PianoRollViewportSizingTest {
    @Test
    fun pianoRollVirtualCanvasMustBeTallerThanViewportForPitchPanning() {
        val viewportHeightDp = 300f
        val virtualHeightDp = PianoRollMapping.VISIBLE_PITCHES * 24f
        assertTrue(virtualHeightDp > viewportHeightDp)
    }
}
