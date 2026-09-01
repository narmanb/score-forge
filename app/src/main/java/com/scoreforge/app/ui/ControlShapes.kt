package com.scoreforge.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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

internal val ComposerControlStripColor = Color(0xFF4A4752)
private val ComposerControlButtonColor = Color(0xFF5D5966)
private val ComposerControlPressedColor = Color(0xFF302D37)
private val ComposerControlOutlineColor = Color(0xFFE1DAE9)
private val ComposerControlDisabledColor = Color(0xFF4F4C56)
private val ComposerControlDisabledOutline = Color(0xFF77727E)

@Composable
internal fun ChamferedControlButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(if (compact) 28.dp else 42.dp)
            .offset(y = if (selected) 1.dp else 0.dp),
        shape = ChamferedControlShape,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (enabled) ComposerControlOutlineColor else ComposerControlDisabledOutline,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) ComposerControlPressedColor else ComposerControlButtonColor,
            contentColor = Color.White,
            disabledContainerColor = ComposerControlDisabledColor,
            disabledContentColor = Color(0xFFAAA5B0),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (selected) 0.dp else 3.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        contentPadding = PaddingValues(
            horizontal = if (compact) 11.dp else 16.dp,
            vertical = 0.dp,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

enum class PianoEntryMode(val displayName: String) {
    STEP("Step"),
    NATURAL("Natural"),
    LIVE("Live"),
}
