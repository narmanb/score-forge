package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreEditHistoryTest {
    private fun state(pitch: Int? = null, cursor: Float = 0f): ScoreEditState =
        ScoreEditState(
            events = pitch?.let {
                listOf(ScoreNote(it, NoteDuration.QUARTER, startBeat = 0f))
            } ?: emptyList(),
            cursorBeat = cursor,
        )

    @Test
    fun undoAndRedoRestoreWholeScoreState() {
        val history = ScoreEditHistory()
        val empty = state()
        val c = state(60, 1f)
        val d = state(62, 1f)

        history.recordBeforeChange(empty)
        history.recordBeforeChange(c)

        assertEquals(c, history.undo(d))
        assertEquals(empty, history.undo(c))
        assertEquals(c, history.redo(empty))
        assertEquals(d, history.redo(c))
    }

    @Test
    fun newEditClearsRedoBranch() {
        val history = ScoreEditHistory()
        val empty = state()
        val c = state(60, 1f)

        history.recordBeforeChange(empty)
        assertEquals(empty, history.undo(c))
        assertTrue(history.canRedo)

        history.recordBeforeChange(empty)
        assertFalse(history.canRedo)
        assertNull(history.redo(empty))
    }

    @Test
    fun duplicatePreEditStateIsNotAddedTwice() {
        val history = ScoreEditHistory()
        val empty = state()
        val c = state(60, 1f)

        history.recordBeforeChange(empty)
        history.recordBeforeChange(empty)

        assertEquals(empty, history.undo(c))
        assertNull(history.undo(empty))
    }

    @Test
    fun capacityDropsOldestUndoEntries() {
        val history = ScoreEditHistory(capacity = 2)
        val empty = state()
        val c = state(60, 1f)
        val d = state(62, 1f)
        val e = state(64, 1f)

        history.recordBeforeChange(empty)
        history.recordBeforeChange(c)
        history.recordBeforeChange(d)

        assertEquals(d, history.undo(e))
        assertEquals(c, history.undo(d))
        assertNull(history.undo(c))
    }
}
