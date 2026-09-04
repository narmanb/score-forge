package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NaturalBrowseResumeTest {
    @Test
    fun `staff browsing seals the phrase at learned pulse instead of browsing time`() {
        val written = NaturalEntryTiming.writtenForUiBreak(
            recentIntervalsMs = listOf(498L, 505L, 502L),
            bpm = 120,
            holdFallbackMs = 180L,
        )

        assertEquals(NoteDuration.QUARTER, written.duration)
        assertFalse(written.dotted)
    }

    @Test
    fun `staff browsing falls back to released hold before pulse is learned`() {
        val written = NaturalEntryTiming.writtenForUiBreak(
            recentIntervalsMs = emptyList(),
            bpm = 120,
            holdFallbackMs = 510L,
        )

        assertEquals(NoteDuration.QUARTER, written.duration)
        assertFalse(written.dotted)
    }
}
