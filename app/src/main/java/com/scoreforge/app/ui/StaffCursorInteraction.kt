package com.scoreforge.app.ui

internal enum class StaffCursorZone {
    PLAYBACK,
    STAFF,
    ENTRY,
}

/** Keeps transport seeking and note-entry positioning in separate touch gutters. */
internal object StaffCursorInteraction {
    private const val PLAYBACK_GUTTER_SPACING = 0.85f
    private const val ENTRY_GUTTER_SPACING = 0.55f

    fun zoneForY(
        y: Float,
        staffTop: Float,
        staffBottom: Float,
        lineSpacing: Float,
    ): StaffCursorZone {
        val safeSpacing = lineSpacing.coerceAtLeast(0f)
        val playbackBottom = staffTop - safeSpacing * PLAYBACK_GUTTER_SPACING
        val entryTop = staffBottom + safeSpacing * ENTRY_GUTTER_SPACING
        return when {
            y <= playbackBottom -> StaffCursorZone.PLAYBACK
            y >= entryTop -> StaffCursorZone.ENTRY
            else -> StaffCursorZone.STAFF
        }
    }
}
