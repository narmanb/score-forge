package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalEntryOnsetTimingTest {
    @Test
    fun `quarter note attack spacing stays quarter even with a crisp release`() {
        val onset = NaturalEntryTiming.writtenForOnsetIntervalMs(500, 120)
        val shortHold = NaturalEntryTiming.writtenForHoldMs(200, 120)

        assertEquals(NoteDuration.QUARTER, onset.duration)
        assertFalse(onset.dotted)
        assertTrue(shortHold.beats < onset.beats)
    }

    @Test
    fun `onset spacing recognizes eighths and dotted rhythms`() {
        val eighth = NaturalEntryTiming.writtenForOnsetIntervalMs(250, 120)
        val dottedEighth = NaturalEntryTiming.writtenForOnsetIntervalMs(375, 120)
        val dottedQuarter = NaturalEntryTiming.writtenForOnsetIntervalMs(750, 120)

        assertEquals(NoteDuration.EIGHTH, eighth.duration)
        assertFalse(eighth.dotted)
        assertEquals(NoteDuration.EIGHTH, dottedEighth.duration)
        assertTrue(dottedEighth.dotted)
        assertEquals(NoteDuration.QUARTER, dottedQuarter.duration)
        assertTrue(dottedQuarter.dotted)
    }

    @Test
    fun `near simultaneous attacks form a chord but eighth notes do not`() {
        val window = NaturalEntryTiming.chordWindowMs(120)
        assertTrue(window in 55L..120L)
        assertTrue(NaturalEntryTiming.isSameOnsetGroup(1_000L, 1_080L, 120))
        assertFalse(NaturalEntryTiming.isSameOnsetGroup(1_000L, 1_250L, 120))
    }

    @Test
    fun `long phrase gap is preserved as spacing instead of giant note duration`() {
        val intervalMs = 3_000L // six beats at 120 BPM
        assertFalse(NaturalEntryTiming.shouldUseOnsetAsWrittenDuration(intervalMs, 120))
        assertEquals(6f, NaturalEntryTiming.quantizedOnsetSpacingBeats(intervalMs, 120), 0.001f)
    }
}
