package com.scoreforge.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class StaffResponsiveLayoutBoundaryTest {
    @Test
    fun portraitMinimumIsWideEnoughToForceScrollOnNarrowPhones() {
        assertTrue(
            StaffResponsiveLayout.PORTRAIT_MIN_BEAT_WIDTH_DP >
                StaffResponsiveLayout.DEFAULT_MIN_BEAT_WIDTH_DP,
        )
    }
}
