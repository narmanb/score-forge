package com.scoreforge.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class PianoRollViewportBehaviorTest {
    @Test
    fun pitchRangeProducesScrollableVerticalContent() {
        val virtualHeightDp = PianoRollMapping.VISIBLE_PITCHES * 24f
        assertTrue(virtualHeightDp > 300f)
    }
}
