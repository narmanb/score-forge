package com.scoreforge.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

/**
 * A low-profile elongated hexagon for one-shot commands.
 * Unlike mode/selection controls, command buttons never stay highlighted after being pressed.
 */
internal val CompactCommandShape = GenericShape { size, _ ->
    val cut = minOf(size.width * 0.12f, size.height * 0.42f)
    moveTo(cut, 0f)
    lineTo(size.width - cut, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width - cut, size.height)
    lineTo(cut, size.height)
    lineTo(0f, size.height / 2f)
    close()
}

enum class UiCommandFeedback {
    NONE,
    NEUTRAL,
    INCREASE,
    DECREASE,
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
    feedback: UiCommandFeedback = UiCommandFeedback.NONE,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val physicallyPressed by interactionSource.collectIsPressedAsState()
    val latchedPressed = remember { androidx.compose.runtime.mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val visuallyPressed = physicallyPressed || latchedPressed.value

    Button(
        onClick = {
            view.performScoreForgeHaptic(UiHapticFeedback.TICK)
            if (feedback != UiCommandFeedback.NONE) {
                ScoreForgeUiFeedback.play(feedback, view.context)
                latchedPressed.value = true
                scope.launch {
                    delay(110L)
                    latchedPressed.value = false
                }
            }
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .height(if (compact) 28.dp else 42.dp)
            .offset(
                y = when {
                    visuallyPressed -> 2.dp
                    selected -> 1.dp
                    else -> 0.dp
                }
            ),
        shape = ChamferedControlShape,
        border = BorderStroke(
            if (selected && !visuallyPressed) 2.dp else 1.dp,
            if (enabled) ComposerControlOutlineColor else ComposerControlDisabledOutline,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                visuallyPressed -> ComposerControlPressedColor
                selected -> ComposerControlPressedColor
                else -> ComposerControlButtonColor
            },
            contentColor = Color.White,
            disabledContainerColor = ComposerControlDisabledColor,
            disabledContentColor = Color(0xFFAAA5B0),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (selected || visuallyPressed) 0.dp else 3.dp,
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

@Composable
internal fun ComposerToolbarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val view = LocalView.current
    Button(
        onClick = {
            view.performScoreForgeHaptic(UiHapticFeedback.TICK)
            onClick()
        },
        enabled = enabled,
        modifier = modifier.height(31.dp),
        shape = ChamferedControlShape,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (enabled) ComposerControlOutlineColor else ComposerControlDisabledOutline,
        ),
        colors = ButtonDefaults.buttonColors(
            // Root categories and Back deliberately use the same dark family as a selected
            // lower-keyboard control so the transforming toolbar reads as its own control strip.
            containerColor = ComposerControlPressedColor,
            contentColor = Color.White,
            disabledContainerColor = ComposerControlDisabledColor,
            disabledContentColor = Color(0xFFAAA5B0),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Selection/action control used inside a transforming-toolbar submenu.
 * Unselected controls reuse the old light toolbar gray; selected controls use the exact same
 * dark latched color as Step and the duration palette beneath the piano keyboard.
 */
@Composable
internal fun ComposerSubmenuButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val view = LocalView.current
    Button(
        onClick = {
            view.performScoreForgeHaptic(UiHapticFeedback.TICK)
            onClick()
        },
        enabled = enabled,
        modifier = modifier.height(32.dp),
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
            defaultElevation = if (selected) 0.dp else 2.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 0.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Compact toolbar command: keep the already-small angular command geometry but use submenu fill. */
@Composable
internal fun ComposerSubmenuCommandButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    feedback: UiCommandFeedback = UiCommandFeedback.NEUTRAL,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val physicallyPressed by interactionSource.collectIsPressedAsState()
    val latchedPressed = remember { androidx.compose.runtime.mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val visuallyPressed = physicallyPressed || latchedPressed.value

    Button(
        onClick = {
            view.performScoreForgeHaptic(UiHapticFeedback.TICK)
            ScoreForgeUiFeedback.play(feedback, view.context)
            latchedPressed.value = true
            scope.launch {
                delay(110L)
                latchedPressed.value = false
            }
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .height(30.dp)
            .offset(y = if (visuallyPressed) 2.dp else 0.dp),
        shape = CompactCommandShape,
        border = BorderStroke(
            1.dp,
            if (enabled) ComposerControlOutlineColor else ComposerControlDisabledOutline,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (visuallyPressed) ComposerControlPressedColor else ComposerControlButtonColor,
            contentColor = Color.White,
            disabledContainerColor = ComposerControlDisabledColor,
            disabledContentColor = Color(0xFFAAA5B0),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (visuallyPressed) 0.dp else 2.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun CompactCommandButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    feedback: UiCommandFeedback = UiCommandFeedback.NEUTRAL,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val physicallyPressed by interactionSource.collectIsPressedAsState()
    val latchedPressed = remember { androidx.compose.runtime.mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val colors = MaterialTheme.colorScheme
    val visuallyPressed = physicallyPressed || latchedPressed.value

    Button(
        onClick = {
            view.performScoreForgeHaptic(UiHapticFeedback.TICK)
            ScoreForgeUiFeedback.play(feedback, view.context)
            latchedPressed.value = true
            scope.launch {
                delay(110L)
                latchedPressed.value = false
            }
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .height(30.dp)
            .offset(y = if (visuallyPressed) 2.dp else 0.dp),
        shape = CompactCommandShape,
        border = BorderStroke(
            1.dp,
            when {
                !enabled -> colors.outline.copy(alpha = 0.40f)
                visuallyPressed -> colors.outline.copy(alpha = 0.55f)
                else -> colors.outline
            },
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (visuallyPressed) {
                colors.onSurface.copy(alpha = 0.18f)
            } else {
                Color.Transparent
            },
            contentColor = colors.onSurface,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.onSurface.copy(alpha = 0.38f),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (visuallyPressed) 0.dp else 3.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

enum class PianoEntryMode(val displayName: String) {
    STEP("Step"),
    NATURAL("Natural"),
    HOLD("Hold"),
    LIVE("Live"),
}
