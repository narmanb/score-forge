package com.scoreforge.app.ui

object StaffResponsiveLayout {
    const val PORTRAIT_BREAKPOINT_DP = 600f
    const val DEFAULT_MIN_BEAT_WIDTH_DP = 18f
    const val PORTRAIT_MIN_BEAT_WIDTH_DP = 28f

    fun minimumBeatWidthDp(viewportWidthDp: Float): Float =
        if (viewportWidthDp < PORTRAIT_BREAKPOINT_DP) {
            PORTRAIT_MIN_BEAT_WIDTH_DP
        } else {
            DEFAULT_MIN_BEAT_WIDTH_DP
        }
}
