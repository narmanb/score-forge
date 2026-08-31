package com.scoreforge.app.music

data class ScoreEditState(
    val events: List<ScoreEvent>,
    val cursorBeat: Float,
    val tracks: List<ScoreTrack> = listOf(
        ScoreTrack(
            id = 1,
            name = "Track 1",
            events = events,
            cursorBeat = cursorBeat,
        )
    ),
    val activeTrackIndex: Int = 0,
) {
    fun frozen(): ScoreEditState {
        val safeTracks = tracks.ifEmpty {
            listOf(
                ScoreTrack(
                    id = 1,
                    name = "Track 1",
                    events = events,
                    cursorBeat = cursorBeat,
                )
            )
        }.map { it.copy(events = it.events.toList()) }
        val safeIndex = activeTrackIndex.coerceIn(0, safeTracks.lastIndex)
        val active = safeTracks[safeIndex]
        return copy(
            events = active.events.toList(),
            cursorBeat = active.cursorBeat,
            tracks = safeTracks,
            activeTrackIndex = safeIndex,
        )
    }
}

/**
 * Bounded score-content undo/redo history.
 *
 * Call [recordBeforeChange] once before a logical edit. Continuous gestures such as a drag should
 * therefore record at drag start, not for every pointer movement. Multi-track state is frozen so
 * adding/removing/muting tracks and editing any staff can be reversed safely.
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
        val frozen = state.frozen()
        if (undoStack.lastOrNull() != frozen) {
            undoStack.addLast(frozen)
            while (undoStack.size > capacity) undoStack.removeFirst()
        }
        redoStack.clear()
    }

    fun undo(current: ScoreEditState): ScoreEditState? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current.frozen())
        return previous.frozen()
    }

    fun redo(current: ScoreEditState): ScoreEditState? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current.frozen())
        while (undoStack.size > capacity) undoStack.removeFirst()
        return next.frozen()
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
