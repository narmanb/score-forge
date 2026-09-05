package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreClefsTest {
    @Test fun emptyAutoDefaultsToTreble() {
        assertEquals(ScoreClef.TREBLE, ScoreClefs.effective(ScoreClefMode.AUTO, emptyList()))
    }

    @Test fun lowTrackAutomaticallyUsesBass() {
        val events = listOf(
            ScoreNote(36, NoteDuration.QUARTER, 0f),
            ScoreNote(43, NoteDuration.QUARTER, 1f),
            ScoreNote(48, NoteDuration.QUARTER, 2f),
        )
        assertEquals(ScoreClef.BASS, ScoreClefs.effective(ScoreClefMode.AUTO, events))
    }

    @Test fun highTrackAutomaticallyUsesTreble() {
        val events = listOf(
            ScoreNote(64, NoteDuration.QUARTER, 0f),
            ScoreNote(67, NoteDuration.QUARTER, 1f),
            ScoreNote(72, NoteDuration.QUARTER, 2f),
        )
        assertEquals(ScoreClef.TREBLE, ScoreClefs.effective(ScoreClefMode.AUTO, events))
    }

    @Test fun manualModeOverridesRange() {
        val low = listOf(ScoreNote(36, NoteDuration.QUARTER, 0f))
        assertEquals(ScoreClef.TREBLE, ScoreClefs.effective(ScoreClefMode.TREBLE, low))
        assertEquals(ScoreClef.BASS, ScoreClefs.effective(ScoreClefMode.BASS, emptyList()))
    }

    @Test fun bottomLineReferencesMatchStandardClefs() {
        assertEquals(4 * 7 + 2, ScoreClefs.bottomLineDiatonic(ScoreClef.TREBLE)) // E4
        assertEquals(2 * 7 + 4, ScoreClefs.bottomLineDiatonic(ScoreClef.BASS)) // G2
    }
}
