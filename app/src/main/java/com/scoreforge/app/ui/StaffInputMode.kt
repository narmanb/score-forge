package com.scoreforge.app.ui

enum class StaffInputMode(val label: String) {
    INPUT_ON("Input On"),
    REARRANGE("Rearrange"),
    OFF("Off");

    val allowsNoteEntry: Boolean
        get() = this == INPUT_ON

    val allowsRearrange: Boolean
        get() = this != OFF

    val allowsDelete: Boolean
        get() = this != OFF

    fun next(): StaffInputMode = when (this) {
        INPUT_ON -> REARRANGE
        REARRANGE -> OFF
        OFF -> INPUT_ON
    }

    companion object {
        fun fromInputEnabled(enabled: Boolean): StaffInputMode =
            if (enabled) INPUT_ON else OFF
    }
}
