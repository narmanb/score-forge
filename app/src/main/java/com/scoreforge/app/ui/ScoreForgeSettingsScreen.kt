package com.scoreforge.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.scoreforge.app.music.ScoreClefMode

private data class SettingChoice(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun ScoreForgeSettingsScreen(
    onBack: () -> Unit,
    onSettingsChanged: (ScoreForgeSettings) -> Unit = {},
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(ScoreForgeSettingsRepository.load(context)) }
    var resetDialogOpen by remember { mutableStateOf(false) }

    fun update(next: ScoreForgeSettings) {
        settings = next
        ScoreForgeSettingsRepository.save(context, next)
        context.findActivity()?.let { ScoreForgeSettingsRepository.applyActivityPreferences(it, next) }
        onSettingsChanged(next)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ComposerToolbarButton(label = "← Back", onClick = onBack)
                Column {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "App-wide preferences • changes save immediately",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsSection("Interface") {
                ChoiceSetting(
                    label = "Screen Orientation",
                    description = "Follow the phone, or force Score Forge to portrait or landscape.",
                    choices = ScreenOrientationSetting.entries.map { option ->
                        SettingChoice(option.displayName, settings.screenOrientation == option) {
                            update(settings.copy(screenOrientation = option))
                        }
                    },
                )
                ChoiceSetting(
                    label = "Haptic Feedback",
                    description = "Standard is the haptic strength approved in 0.2.47.",
                    choices = HapticStrength.entries.map { option ->
                        SettingChoice(option.displayName, settings.hapticStrength == option) {
                            update(settings.copy(hapticStrength = option))
                        }
                    },
                )
                ToggleSetting(
                    label = "UI Command Sounds",
                    description = "Small command chirps, including the distinct Settings-open sound.",
                    enabled = settings.commandSoundsEnabled,
                    onChanged = { update(settings.copy(commandSoundsEnabled = it)) },
                )
                ChoiceSetting(
                    label = "Note Duration Order",
                    description = "Controls Whole/Half/Quarter/Eighth/16th ordering in duration palettes.",
                    choices = NoteDurationOrderSetting.entries.map { option ->
                        SettingChoice(option.displayName, settings.noteDurationOrder == option) {
                            update(settings.copy(noteDurationOrder = option))
                        }
                    },
                )
                ToggleSetting(
                    label = "Keep Screen Awake",
                    description = "Prevent the display from sleeping while Score Forge is open.",
                    enabled = settings.keepScreenAwake,
                    onChanged = { update(settings.copy(keepScreenAwake = it)) },
                )
            }

            SettingsSection("Input & Editing") {
                ChoiceSetting(
                    label = "Default Editor",
                    description = "Used when starting a fresh Score Forge session.",
                    choices = ScoreEditorMode.entries.map { option ->
                        SettingChoice(option.displayName, settings.defaultEditorMode == option) {
                            update(settings.copy(defaultEditorMode = option))
                        }
                    },
                )
                ChoiceSetting(
                    label = "Default Clef",
                    description = "Used for new tracks; imported/project clefs are preserved.",
                    choices = ScoreClefMode.entries.map { option ->
                        SettingChoice(option.displayName, settings.defaultClefMode == option) {
                            update(settings.copy(defaultClefMode = option))
                        }
                    },
                )
                ChoiceSetting(
                    label = "Keyboard Note Labels",
                    description = "Default labels natural keys only. Off hides labels, C Notes Only labels Cs, and All Notes also labels sharps.",
                    choices = KeyboardNoteLabelSetting.entries.map { option ->
                        SettingChoice(option.displayName, settings.keyboardNoteLabels == option) {
                            update(settings.copy(keyboardNoteLabels = option))
                        }
                    },
                )
                ToggleSetting(
                    label = "Remember Keyboard Octave",
                    description = "Restore your last touch-keyboard octave the next time Score Forge opens.",
                    enabled = settings.rememberKeyboardOctave,
                    onChanged = { update(settings.copy(rememberKeyboardOctave = it)) },
                )
                ChoiceSetting(
                    label = "Default Piano Entry Mode",
                    description = "Live recording never starts automatically.",
                    choices = listOf(PianoEntryMode.STEP, PianoEntryMode.NATURAL, PianoEntryMode.HOLD).map { option ->
                        SettingChoice(option.displayName, settings.defaultEntryMode == option) {
                            update(settings.copy(defaultEntryMode = option))
                        }
                    },
                )
                ToggleSetting(
                    label = "Staff Input Default",
                    description = "Whether direct staff note entry starts enabled.",
                    enabled = settings.staffInputDefault,
                    onChanged = { update(settings.copy(staffInputDefault = it)) },
                )
            }

            SettingsSection("Playback") {
                ToggleSetting(
                    label = "Follow Playback",
                    description = "Automatically scroll the staff or piano roll to keep the playhead visible.",
                    enabled = settings.followPlayback,
                    onChanged = { update(settings.copy(followPlayback = it)) },
                )
            }

            SettingsSection("Audio & Feedback") {
                ToggleSetting(
                    label = "Note Duration Audition",
                    description = "Play a quiet Middle C using the selected instrument when choosing note lengths.",
                    enabled = settings.noteDurationAuditionEnabled,
                    onChanged = { update(settings.copy(noteDurationAuditionEnabled = it)) },
                )
                Text(
                    "Duration Audition Volume • ${(settings.noteDurationAuditionVolume * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    "Independent of the touch piano volume. Default is intentionally quieter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = settings.noteDurationAuditionVolume,
                    enabled = settings.noteDurationAuditionEnabled,
                    onValueChange = { update(settings.copy(noteDurationAuditionVolume = it.coerceIn(0.05f, 0.70f))) },
                    valueRange = 0.05f..0.70f,
                    steps = 12,
                )
                Text(
                    "Audition pitch: Middle C",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsSection("Projects & Files") {
                ToggleSetting(
                    label = "Restore Last Project",
                    description = "Load the recovery draft automatically when Score Forge starts.",
                    enabled = settings.restoreLastProject,
                    onChanged = { update(settings.copy(restoreLastProject = it)) },
                )
                ToggleSetting(
                    label = "Autosave / Recovery",
                    description = "Keep the app-private recovery draft updated while you work.",
                    enabled = settings.autosaveRecovery,
                    onChanged = { update(settings.copy(autosaveRecovery = it)) },
                )
            }

            SettingsSection("Advanced") {
                Text(
                    "Advanced Audio, Export Defaults, and future MIDI settings will use dedicated subpages as those features are added.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                ComposerSubmenuButton(
                    label = "Reset Settings to Defaults",
                    onClick = { resetDialogOpen = true },
                )
            }
        }
    }

    if (resetDialogOpen) {
        AlertDialog(
            onDismissRequest = { resetDialogOpen = false },
            title = { Text("Reset settings?") },
            text = { Text("All Score Forge app settings will return to their defaults. Projects and music are not affected.") },
            confirmButton = {
                ScoreForgeTextButton(
                    onClick = {
                        val defaults = ScoreForgeSettingsRepository.reset(context)
                        settings = defaults
                        context.findActivity()?.let { ScoreForgeSettingsRepository.applyActivityPreferences(it, defaults) }
                        onSettingsChanged(defaults)
                        resetDialogOpen = false
                    },
                    haptic = UiHapticFeedback.CONFIRM,
                ) { Text("Reset") }
            },
            dismissButton = {
                ScoreForgeTextButton(onClick = { resetDialogOpen = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(8.dp),
        color = ComposerControlStripColor.copy(alpha = 0.44f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            content()
        }
    }
}

@Composable
private fun ChoiceSetting(
    label: String,
    description: String,
    choices: List<SettingChoice>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            choices.forEach { choice ->
                ComposerSubmenuButton(
                    label = choice.label,
                    onClick = choice.onClick,
                    selected = choice.selected,
                )
            }
        }
    }
}

@Composable
private fun ToggleSetting(
    label: String,
    description: String,
    enabled: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    ChoiceSetting(
        label = label,
        description = description,
        choices = listOf(
            SettingChoice("On", enabled) { if (!enabled) onChanged(true) },
            SettingChoice("Off", !enabled) { if (enabled) onChanged(false) },
        ),
    )
}
