package com.scoreforge.app.music

/** Removes tie flags that no longer point to a contiguous same-pitch note after an edit. */
fun sanitizeScoreTies(events: List<ScoreEvent>): List<ScoreEvent> =
    events.mapIndexed { index, event ->
        val note = event as? ScoreNote ?: return@mapIndexed event
        if (note.tieToNext && ScoreTies.targetIndex(events, index) == null) {
            note.copy(tieToNext = false)
        } else {
            note
        }
    }
