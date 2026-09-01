package com.scoreforge.app.ui

import androidx.compose.foundation.shape.GenericShape

internal val ChamferedControlShape = GenericShape { size, _ ->
    val cut = minOf(size.width, size.height) * 0.20f
    moveTo(cut, 0f)
    lineTo(size.width - cut, 0f)
    lineTo(size.width, cut)
    lineTo(size.width, size.height - cut)
    lineTo(size.width - cut, size.height)
    lineTo(cut, size.height)
    lineTo(0f, size.height - cut)
    lineTo(0f, cut)
    close()
}

enum class PianoEntryMode(val displayName: String) {
    STEP("Step"),
    NATURAL("Natural"),
}
