package com.scoreforge.app.ui

/** Geometry shared by the touchscreen piano renderer and touch hit-testing. */
object PianoTouchLayout {
    val whitePitches = listOf(60, 62, 64, 65, 67, 69, 71, 72, 74, 76, 77, 79, 81, 83)

    data class BlackKey(
        val whiteIndex: Int,
        val midiPitch: Int,
    )

    val blackKeys = listOf(
        BlackKey(0, 61),
        BlackKey(1, 63),
        BlackKey(3, 66),
        BlackKey(4, 68),
        BlackKey(5, 70),
        BlackKey(7, 73),
        BlackKey(8, 75),
        BlackKey(10, 78),
        BlackKey(11, 80),
        BlackKey(12, 82),
    )

    const val BLACK_KEY_HEIGHT_FRACTION = 0.62f
    const val BLACK_KEY_WIDTH_FRACTION = 0.64f
    const val BLACK_KEY_X_OFFSET_FRACTION = 0.68f

    /** The one authoritative conversion from a displayed keyboard key to its played MIDI pitch. */
    fun shiftedPitch(layoutPitch: Int, octaveShift: Int): Int =
        (layoutPitch + octaveShift.coerceIn(-4, 3) * 12).coerceIn(0, 127)

    /**
     * Returns the unshifted layout MIDI pitch under a point in keyboard-local pixels.
     * Black keys are tested first because they visually overlap the white keys.
     */
    fun pitchAt(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ): Int? {
        if (width <= 0f || height <= 0f || x < 0f || y < 0f || x >= width || y >= height) {
            return null
        }

        val whiteWidth = width / whitePitches.size

        if (y < height * BLACK_KEY_HEIGHT_FRACTION) {
            blackKeys.forEach { key ->
                val left = whiteWidth * (key.whiteIndex + BLACK_KEY_X_OFFSET_FRACTION)
                val right = left + whiteWidth * BLACK_KEY_WIDTH_FRACTION
                if (x >= left && x < right) return key.midiPitch
            }
        }

        val whiteIndex = (x / whiteWidth).toInt().coerceIn(0, whitePitches.lastIndex)
        return whitePitches[whiteIndex]
    }
}
