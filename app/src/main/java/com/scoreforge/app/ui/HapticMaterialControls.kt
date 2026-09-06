package com.scoreforge.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem as MaterialDropdownMenuItem
import androidx.compose.material3.OutlinedButton as MaterialOutlinedButton
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView

internal enum class UiHapticFeedback {
    NONE,
    TICK,
    CONFIRM,
    STRONG,
}

internal fun View.performScoreForgeHaptic(feedback: UiHapticFeedback = UiHapticFeedback.TICK) {
    if (feedback == UiHapticFeedback.NONE) return
    val constant = when (feedback) {
        UiHapticFeedback.NONE -> return
        // CLOCK_TICK proved too faint on-device. VIRTUAL_KEY is still a standard Android
        // interaction haptic, but is easier to perceive without becoming a custom vibration.
        UiHapticFeedback.TICK -> HapticFeedbackConstants.VIRTUAL_KEY
        UiHapticFeedback.CONFIRM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        UiHapticFeedback.STRONG -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
    }
    performHapticFeedback(constant)
}

@Composable
internal fun ScoreForgeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    haptic: UiHapticFeedback = UiHapticFeedback.TICK,
    content: @Composable RowScope.() -> Unit,
) {
    val view = LocalView.current
    MaterialButton(
        onClick = {
            view.performScoreForgeHaptic(haptic)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
internal fun ScoreForgeOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    haptic: UiHapticFeedback = UiHapticFeedback.TICK,
    content: @Composable RowScope.() -> Unit,
) {
    val view = LocalView.current
    MaterialOutlinedButton(
        onClick = {
            view.performScoreForgeHaptic(haptic)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
internal fun ScoreForgeTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptic: UiHapticFeedback = UiHapticFeedback.TICK,
    content: @Composable RowScope.() -> Unit,
) {
    val view = LocalView.current
    MaterialTextButton(
        onClick = {
            view.performScoreForgeHaptic(haptic)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}

@Composable
internal fun ScoreForgeDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptic: UiHapticFeedback = UiHapticFeedback.TICK,
) {
    val view = LocalView.current
    MaterialDropdownMenuItem(
        text = text,
        onClick = {
            view.performScoreForgeHaptic(haptic)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
    )
}
