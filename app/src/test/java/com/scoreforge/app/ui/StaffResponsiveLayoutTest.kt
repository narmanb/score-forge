package com.scoreforge.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StaffResponsiveLayoutTest {
    @Test
    fun portraitUsesReadableMinimumBeatWidth() {
        assertEquals(28f, StaffResponsiveLayout.minimumBeatWidthDp(420f), 0.001f)
    }

    @Test
    fun landscapeKeepsExistingMinimumBeatWidth() {
        assertEquals(18f, StaffResponsiveLayout.minimumBeatWidthDp(760f), 0.001f)
    }

    @Test
    fun breakpointUsesLandscapePolicyAt600Dp() {
        assertEquals(18f, StaffResponsiveLayout.minimumBeatWidthDp(600f), 0.001f)
    }
}
