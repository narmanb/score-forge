package com.scoreforge.app.music

data class ScoreEditState(
    val events: List<ScoreEvent>,
    val cursorBeat: Float,
)

/**
 * Bounded score-content undo/redo history.
 *
 * Call [recordBeforeChange] once before a logical edit. Continuous gestures such as a drag should
 * therefore record at drag start, not for every pointer movement.
 */
class ScoreEditHistory(
    private val capacity: Int = 100,
) {
    private val undoStack = ArrayDeque<ScoreEditState>()
    private val redoStack = ArrayDeque<ScoreEditState>()

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    fun recordBeforeChange(state: ScoreEditState) {
        if (capacity <= 0) return
        if (undoStack.lastOrNull() != state) {
            undoStack.addLast(state.copy(events = state.events.toList()))
            while (undoStack.size > capacity) undoStack.removeFirst()
        }
        redoStack.clear()
    }

    fun undo(current: ScoreEditState): ScoreEditState? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current.copy(events = current.events.toList()))
        return previous
    }

    fun redo(current: ScoreEditState): ScoreEditState? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current.copy(events = current.events.toList()))
        while (undoStack.size > capacity) undoStack.removeFirst()
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
