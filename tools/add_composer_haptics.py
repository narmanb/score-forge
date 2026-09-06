from pathlib import Path
import re

ROOT = Path('.')
UI = ROOT / 'app/src/main/java/com/scoreforge/app/ui'

# Add shared Material wrappers that provide button haptics without touching performance surfaces.
haptic_file = UI / 'HapticMaterialControls.kt'
haptic_file.write_text('''package com.scoreforge.app.ui

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
        UiHapticFeedback.TICK -> HapticFeedbackConstants.CLOCK_TICK
        UiHapticFeedback.CONFIRM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        UiHapticFeedback.STRONG -> HapticFeedbackConstants.LONG_PRESS
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
''')

# Make every custom composer control tick, while preserving its existing sound feedback behavior.
control_path = UI / 'ControlShapes.kt'
control = control_path.read_text()
old = '''        onClick = {
            if (feedback != UiCommandFeedback.NONE) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                ScoreForgeUiFeedback.play(feedback)
'''
new = '''        onClick = {
            view.performScoreForgeHaptic(UiHapticFeedback.TICK)
            if (feedback != UiCommandFeedback.NONE) {
                ScoreForgeUiFeedback.play(feedback)
'''
if old not in control:
    raise SystemExit('ChamferedControlButton haptic block not found')
control = control.replace(old, new, 1)

# Main transforming toolbar button.
old = '''internal fun ComposerToolbarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
'''
new = '''internal fun ComposerToolbarButton(
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
'''
if old not in control:
    raise SystemExit('ComposerToolbarButton block not found')
control = control.replace(old, new, 1)

# Transforming toolbar submenu selection/action button.
old = '''internal fun ComposerSubmenuButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
'''
new = '''internal fun ComposerSubmenuButton(
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
'''
if old not in control:
    raise SystemExit('ComposerSubmenuButton block not found')
control = control.replace(old, new, 1)

# Existing command controls already haptic; route them through the common helper for consistency.
control = control.replace(
    'view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)',
    'view.performScoreForgeHaptic(UiHapticFeedback.TICK)',
)
control_path.write_text(control)

# Replace active Material controls with haptic wrappers. Do not touch Canvas/pointer input surfaces.
active_files = [
    'ComposerScreen.kt',
    'ProjectFileControls.kt',
    'TrackControls.kt',
    'SoundFontControls.kt',
    'ScoreStaffEditor.kt',
]
for name in active_files:
    path = UI / name
    text = path.read_text()
    text = text.replace('import androidx.compose.material3.Button\n', '')
    text = text.replace('import androidx.compose.material3.OutlinedButton\n', '')
    text = text.replace('import androidx.compose.material3.TextButton\n', '')
    text = text.replace('import androidx.compose.material3.DropdownMenuItem\n', '')
    text = re.sub(r'(?<![A-Za-z0-9_])OutlinedButton\(', 'ScoreForgeOutlinedButton(', text)
    text = re.sub(r'(?<![A-Za-z0-9_])TextButton\(', 'ScoreForgeTextButton(', text)
    text = re.sub(r'(?<![A-Za-z0-9_])DropdownMenuItem\(', 'ScoreForgeDropdownMenuItem(', text)
    text = re.sub(r'(?<![A-Za-z0-9_])Button\(', 'ScoreForgeButton(', text)
    path.write_text(text)

# Stronger feedback for the two direct destructive commands.
project_path = UI / 'ProjectFileControls.kt'
project = project_path.read_text()
needle = '''        ScoreForgeOutlinedButton(
            onClick = {
                onClearTrack()
                status = "Cleared $activeTrackName"
            },
            enabled = canClearTrack,
'''
replacement = '''        ScoreForgeOutlinedButton(
            onClick = {
                onClearTrack()
                status = "Cleared $activeTrackName"
            },
            enabled = canClearTrack,
            haptic = UiHapticFeedback.STRONG,
'''
if needle not in project:
    raise SystemExit('Clear Track button block not found')
project = project.replace(needle, replacement, 1)

needle = '''                ScoreForgeTextButton(
                    onClick = {
                        onNewProject()
                        status = "New project"
                        newProjectDialogOpen = false
                    },
'''
replacement = '''                ScoreForgeTextButton(
                    onClick = {
                        onNewProject()
                        status = "New project"
                        newProjectDialogOpen = false
                    },
                    haptic = UiHapticFeedback.CONFIRM,
'''
if needle not in project:
    raise SystemExit('New Project confirm block not found')
project = project.replace(needle, replacement, 1)
project_path.write_text(project)

track_path = UI / 'TrackControls.kt'
track = track_path.read_text()
needle = '''            ScoreForgeOutlinedButton(
                onClick = onDeleteTrack,
                enabled = tracks.size > 1,
'''
replacement = '''            ScoreForgeOutlinedButton(
                onClick = onDeleteTrack,
                enabled = tracks.size > 1,
                haptic = UiHapticFeedback.STRONG,
'''
if needle not in track:
    raise SystemExit('Delete Track button block not found')
track = track.replace(needle, replacement, 1)
track_path.write_text(track)

# Bump phone-test build.
build_path = ROOT / 'app/build.gradle.kts'
build = build_path.read_text()
build = build.replace('versionCode = 49', 'versionCode = 50', 1)
build = build.replace('versionName = "0.2.46"', 'versionName = "0.2.47"', 1)
build_path.write_text(build)
