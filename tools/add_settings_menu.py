from pathlib import Path

ROOT = Path('.')
UI = ROOT / 'app/src/main/java/com/scoreforge/app/ui'
APP = ROOT / 'app/src/main/java/com/scoreforge/app'

settings_kt = r'''package com.scoreforge.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.ScoreClefMode

enum class HapticStrength(val displayName: String) {
    OFF("Off"),
    LIGHT("Light"),
    STANDARD("Standard"),
    STRONG("Strong"),
}

enum class ScreenOrientationSetting(val displayName: String) {
    FOLLOW_DEVICE("Follow Device"),
    PORTRAIT("Lock Portrait"),
    LANDSCAPE("Lock Landscape"),
}

enum class NoteDurationOrderSetting(val displayName: String) {
    LONG_TO_SHORT("Longest → Shortest"),
    SHORT_TO_LONG("Shortest → Longest");

    fun orderedDurations(): List<NoteDuration> = when (this) {
        LONG_TO_SHORT -> NoteDuration.entries.toList()
        SHORT_TO_LONG -> NoteDuration.entries.reversed()
    }
}

enum class KeyboardNoteLabelSetting(val displayName: String) {
    OFF("Off"),
    C_ONLY("C Notes Only"),
    ALL("All Notes"),
}

data class ScoreForgeSettings(
    val hapticStrength: HapticStrength = HapticStrength.STANDARD,
    val commandSoundsEnabled: Boolean = true,
    val screenOrientation: ScreenOrientationSetting = ScreenOrientationSetting.FOLLOW_DEVICE,
    val noteDurationOrder: NoteDurationOrderSetting = NoteDurationOrderSetting.LONG_TO_SHORT,
    val keepScreenAwake: Boolean = true,
    val defaultEditorMode: ScoreEditorMode = ScoreEditorMode.STAFF,
    val defaultClefMode: ScoreClefMode = ScoreClefMode.AUTO,
    val keyboardNoteLabels: KeyboardNoteLabelSetting = KeyboardNoteLabelSetting.C_ONLY,
    val rememberKeyboardOctave: Boolean = true,
    val rememberedKeyboardOctave: Int = 0,
    val defaultEntryMode: PianoEntryMode = PianoEntryMode.STEP,
    val staffInputDefault: Boolean = true,
    val followPlayback: Boolean = true,
    val noteDurationAuditionEnabled: Boolean = true,
    val noteDurationAuditionVolume: Float = 0.35f,
    val restoreLastProject: Boolean = true,
    val autosaveRecovery: Boolean = true,
)

object ScoreForgeSettingsRepository {
    private const val PREFS = "score_forge_settings"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): ScoreForgeSettings {
        val prefs = prefs(context)
        return ScoreForgeSettings(
            hapticStrength = prefs.enumValue("haptic_strength", HapticStrength.STANDARD),
            commandSoundsEnabled = prefs.getBoolean("command_sounds", true),
            screenOrientation = prefs.enumValue("screen_orientation", ScreenOrientationSetting.FOLLOW_DEVICE),
            noteDurationOrder = prefs.enumValue("note_duration_order", NoteDurationOrderSetting.LONG_TO_SHORT),
            keepScreenAwake = prefs.getBoolean("keep_screen_awake", true),
            defaultEditorMode = prefs.enumValue("default_editor", ScoreEditorMode.STAFF),
            defaultClefMode = prefs.enumValue("default_clef", ScoreClefMode.AUTO),
            keyboardNoteLabels = prefs.enumValue("keyboard_labels", KeyboardNoteLabelSetting.C_ONLY),
            rememberKeyboardOctave = prefs.getBoolean("remember_keyboard_octave", true),
            rememberedKeyboardOctave = prefs.getInt("keyboard_octave", 0).coerceIn(-4, 3),
            defaultEntryMode = prefs.enumValue("default_entry_mode", PianoEntryMode.STEP),
            staffInputDefault = prefs.getBoolean("staff_input_default", true),
            followPlayback = prefs.getBoolean("follow_playback", true),
            noteDurationAuditionEnabled = prefs.getBoolean("duration_audition", true),
            noteDurationAuditionVolume = prefs.getFloat("duration_audition_volume", 0.35f).coerceIn(0.05f, 0.70f),
            restoreLastProject = prefs.getBoolean("restore_last_project", true),
            autosaveRecovery = prefs.getBoolean("autosave_recovery", true),
        )
    }

    fun save(context: Context, settings: ScoreForgeSettings) {
        prefs(context).edit()
            .putString("haptic_strength", settings.hapticStrength.name)
            .putBoolean("command_sounds", settings.commandSoundsEnabled)
            .putString("screen_orientation", settings.screenOrientation.name)
            .putString("note_duration_order", settings.noteDurationOrder.name)
            .putBoolean("keep_screen_awake", settings.keepScreenAwake)
            .putString("default_editor", settings.defaultEditorMode.name)
            .putString("default_clef", settings.defaultClefMode.name)
            .putString("keyboard_labels", settings.keyboardNoteLabels.name)
            .putBoolean("remember_keyboard_octave", settings.rememberKeyboardOctave)
            .putInt("keyboard_octave", settings.rememberedKeyboardOctave.coerceIn(-4, 3))
            .putString("default_entry_mode", settings.defaultEntryMode.name)
            .putBoolean("staff_input_default", settings.staffInputDefault)
            .putBoolean("follow_playback", settings.followPlayback)
            .putBoolean("duration_audition", settings.noteDurationAuditionEnabled)
            .putFloat("duration_audition_volume", settings.noteDurationAuditionVolume.coerceIn(0.05f, 0.70f))
            .putBoolean("restore_last_project", settings.restoreLastProject)
            .putBoolean("autosave_recovery", settings.autosaveRecovery)
            .apply()
    }

    fun reset(context: Context): ScoreForgeSettings {
        prefs(context).edit().clear().apply()
        return ScoreForgeSettings()
    }

    fun hapticStrength(context: Context): HapticStrength =
        prefs(context).enumValue("haptic_strength", HapticStrength.STANDARD)

    fun commandSoundsEnabled(context: Context): Boolean =
        prefs(context).getBoolean("command_sounds", true)

    fun rememberKeyboardOctave(context: Context, octave: Int) {
        if (!prefs(context).getBoolean("remember_keyboard_octave", true)) return
        prefs(context).edit().putInt("keyboard_octave", octave.coerceIn(-4, 3)).apply()
    }

    fun applyActivityPreferences(activity: Activity, settings: ScoreForgeSettings = load(activity)) {
        val orientation = when (settings.screenOrientation) {
            ScreenOrientationSetting.FOLLOW_DEVICE -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ScreenOrientationSetting.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ScreenOrientationSetting.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        if (activity.requestedOrientation != orientation) {
            activity.requestedOrientation = orientation
        }
        if (settings.keepScreenAwake) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private inline fun <reified T : Enum<T>> android.content.SharedPreferences.enumValue(
        key: String,
        fallback: T,
    ): T {
        val raw = getString(key, null) ?: return fallback
        return enumValues<T>().firstOrNull { it.name == raw } ?: fallback
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
'''
(UI / 'ScoreForgeSettings.kt').write_text(settings_kt)

settings_screen = r'''package com.scoreforge.app.ui

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
                    description = "Small chirps on supported increase/decrease command buttons.",
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
                    description = "Choose how much pitch labeling appears on the touch piano.",
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
                    description = "Controls the upcoming Middle-C duration preview when choosing note lengths.",
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
'''
(UI / 'ScoreForgeSettingsScreen.kt').write_text(settings_screen)

# Haptic strength becomes app-configurable.
haptic_path = UI / 'HapticMaterialControls.kt'
haptic = haptic_path.read_text()
old = '''internal fun View.performScoreForgeHaptic(feedback: UiHapticFeedback = UiHapticFeedback.TICK) {
    if (feedback == UiHapticFeedback.NONE) return
    val constant = when (feedback) {
        UiHapticFeedback.NONE -> return
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
'''
new = '''internal fun View.performScoreForgeHaptic(feedback: UiHapticFeedback = UiHapticFeedback.TICK) {
    if (feedback == UiHapticFeedback.NONE) return
    val strength = ScoreForgeSettingsRepository.hapticStrength(context)
    if (strength == HapticStrength.OFF) return
    val constant = when (strength) {
        HapticStrength.OFF -> return
        HapticStrength.LIGHT -> when (feedback) {
            UiHapticFeedback.NONE -> return
            UiHapticFeedback.TICK -> HapticFeedbackConstants.CLOCK_TICK
            UiHapticFeedback.CONFIRM -> HapticFeedbackConstants.VIRTUAL_KEY
            UiHapticFeedback.STRONG -> HapticFeedbackConstants.LONG_PRESS
        }
        HapticStrength.STANDARD -> when (feedback) {
            UiHapticFeedback.NONE -> return
            UiHapticFeedback.TICK -> HapticFeedbackConstants.VIRTUAL_KEY
            UiHapticFeedback.CONFIRM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            UiHapticFeedback.STRONG -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        }
        HapticStrength.STRONG -> when (feedback) {
            UiHapticFeedback.NONE -> return
            UiHapticFeedback.TICK -> HapticFeedbackConstants.LONG_PRESS
            UiHapticFeedback.CONFIRM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
            UiHapticFeedback.STRONG -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        }
    }
    performHapticFeedback(constant)
}
'''
if old not in haptic:
    raise SystemExit('expected haptic mapping not found')
haptic_path.write_text(haptic.replace(old, new, 1))

# UI command sounds respect the settings toggle.
feedback_path = UI / 'ScoreForgeUiFeedback.kt'
feedback = feedback_path.read_text()
feedback = feedback.replace('import android.media.AudioTrack\n', 'import android.media.AudioTrack\nimport android.content.Context\n')
feedback = feedback.replace('fun play(feedback: UiCommandFeedback) {', 'fun play(feedback: UiCommandFeedback, context: Context) {\n        if (!ScoreForgeSettingsRepository.commandSoundsEnabled(context)) return')
feedback_path.write_text(feedback)

control_path = UI / 'ControlShapes.kt'
control = control_path.read_text()
control = control.replace('ScoreForgeUiFeedback.play(feedback)', 'ScoreForgeUiFeedback.play(feedback, view.context)')
control_path.write_text(control)

# MainActivity applies persisted orientation and keep-awake preferences.
main_path = APP / 'MainActivity.kt'
main = main_path.read_text()
main = main.replace('import com.scoreforge.app.ui.ScoreForgeComposerScreen\n', 'import com.scoreforge.app.ui.ScoreForgeComposerScreen\nimport com.scoreforge.app.ui.ScoreForgeSettingsRepository\n')
main = main.replace('        super.onCreate(savedInstanceState)\n', '        super.onCreate(savedInstanceState)\n        ScoreForgeSettingsRepository.applyActivityPreferences(this)\n', 1)
main = main.replace('    override fun onResume() {\n        super.onResume()\n        requestImmersiveMode()\n', '    override fun onResume() {\n        super.onResume()\n        ScoreForgeSettingsRepository.applyActivityPreferences(this)\n        requestImmersiveMode()\n')
main_path.write_text(main)

# Transforming toolbar duration order.
toolbar_path = UI / 'ComposerTransformToolbar.kt'
toolbar = toolbar_path.read_text()
toolbar = toolbar.replace('    selectedDuration: NoteDuration,\n', '    selectedDuration: NoteDuration,\n    durationOrder: NoteDurationOrderSetting = NoteDurationOrderSetting.LONG_TO_SHORT,\n', 1)
toolbar = toolbar.replace('                NoteDuration.entries.forEach { duration ->', '                durationOrder.orderedDurations().forEach { duration ->', 1)
toolbar_path.write_text(toolbar)

# Piano duration order + note-label preference.
piano_path = UI / 'MultitouchPianoKeyboard.kt'
piano = piano_path.read_text()
piano = piano.replace('    selectedDuration: NoteDuration,\n', '    selectedDuration: NoteDuration,\n    durationOrder: NoteDurationOrderSetting = NoteDurationOrderSetting.LONG_TO_SHORT,\n    noteLabelSetting: KeyboardNoteLabelSetting = KeyboardNoteLabelSetting.C_ONLY,\n', 1)
piano = piano.replace('''                    ) {
                        Text(
                            PitchNames.name(pitch),
                            modifier = Modifier.padding(bottom = 5.dp),
                            color = Color(0xFF222222),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
''', '''                    ) {
                        val showLabel = when (noteLabelSetting) {
                            KeyboardNoteLabelSetting.OFF -> false
                            KeyboardNoteLabelSetting.C_ONLY -> pitch % 12 == 0
                            KeyboardNoteLabelSetting.ALL -> true
                        }
                        if (showLabel) {
                            Text(
                                PitchNames.name(pitch),
                                modifier = Modifier.padding(bottom = 5.dp),
                                color = Color(0xFF222222),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
''', 1)
piano = piano.replace('                NoteDuration.entries.forEach { duration ->', '                durationOrder.orderedDurations().forEach { duration ->', 1)
piano_path.write_text(piano)

# Staff input default + playback-follow toggle.
staff_path = UI / 'ScoreStaffEditor.kt'
staff = staff_path.read_text()
staff = staff.replace('    canPlay: Boolean = false,\n', '    canPlay: Boolean = false,\n    initialInputEnabled: Boolean = true,\n    followPlayback: Boolean = true,\n', 1)
staff = staff.replace('var staffInputEnabled by rememberSaveable { mutableStateOf(true) }', 'var staffInputEnabled by rememberSaveable { mutableStateOf(initialInputEnabled) }', 1)
staff = staff.replace('if (!transport.isPlaying || scrollState.maxValue <= 0) return@LaunchedEffect', 'if (!followPlayback || !transport.isPlaying || scrollState.maxValue <= 0) return@LaunchedEffect', 1)
staff_path.write_text(staff)

roll_path = UI / 'PianoRollEditor.kt'
roll = roll_path.read_text()
roll = roll.replace('    selectedEventIndex: Int,\n', '    selectedEventIndex: Int,\n    followPlayback: Boolean = true,\n', 1)
roll = roll.replace('if (horizontalScroll.maxValue <= 0) return@LaunchedEffect', 'if (horizontalScroll.maxValue <= 0) return@LaunchedEffect\n                if (transport.isPlaying && !followPlayback) return@LaunchedEffect', 1)
roll_path.write_text(roll)

# Composer integration.
composer_path = UI / 'ComposerScreen.kt'
composer = composer_path.read_text()
composer = composer.replace('''    val context = LocalContext.current
    val notificationPermissionLauncher''', '''    val context = LocalContext.current
    var appSettings by remember { mutableStateOf(ScoreForgeSettingsRepository.load(context)) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher''', 1)
composer = composer.replace('val tracks = remember { mutableStateListOf(ScoreTracks.defaultTrack()) }', 'val tracks = remember { mutableStateListOf(ScoreTracks.defaultTrack().copy(clefMode = appSettings.defaultClefMode)) }', 1)
composer = composer.replace('var pianoEntryMode by rememberSaveable { mutableStateOf(PianoEntryMode.STEP) }', 'var pianoEntryMode by rememberSaveable { mutableStateOf(appSettings.defaultEntryMode) }', 1)
composer = composer.replace('var pianoOctaveShift by rememberSaveable { mutableIntStateOf(0) }', 'var pianoOctaveShift by rememberSaveable { mutableIntStateOf(if (appSettings.rememberKeyboardOctave) appSettings.rememberedKeyboardOctave else 0) }', 1)
composer = composer.replace('var editorMode by rememberSaveable { mutableStateOf(ScoreEditorMode.STAFF) }', 'var editorMode by rememberSaveable { mutableStateOf(appSettings.defaultEditorMode) }', 1)
composer = composer.replace('''        val restored = withContext(Dispatchers.IO) { ScoreProjectRepository.loadDraft(context) }
        if (restored != null) {''', '''        val restored = if (appSettings.restoreLastProject) {
            withContext(Dispatchers.IO) { ScoreProjectRepository.loadDraft(context) }
        } else null
        if (restored != null) {''', 1)
composer = composer.replace('if (!draftLoaded || draftTracks.isEmpty()) return@LaunchedEffect', 'if (!draftLoaded || draftTracks.isEmpty() || !appSettings.autosaveRecovery) return@LaunchedEffect', 1)
composer = composer.replace('''        val newTrack = ScoreTracks.newTrack(tracks).copy(
            presetBank = preset?.bank,
            presetProgram = preset?.program,
        )''', '''        val newTrack = ScoreTracks.newTrack(tracks).copy(
            presetBank = preset?.bank,
            presetProgram = preset?.program,
            clefMode = appSettings.defaultClefMode,
        )''', 1)
composer = composer.replace('''    MaterialTheme(colorScheme = darkColorScheme()) {
        ExternalOpenHandler(''', '''    MaterialTheme(colorScheme = darkColorScheme()) {
        if (settingsOpen) {
            ScoreForgeSettingsScreen(
                onBack = { settingsOpen = false },
                onSettingsChanged = { updated -> appSettings = updated },
            )
        } else {
        ExternalOpenHandler(''', 1)
composer = composer.replace('''                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ClefControls''', '''                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        }
    }
}

@Composable
private fun ClefControls''', 1)
composer = composer.replace('''                    onPlay = ::startPlayback,
                    onStop = ::stopPlayback,
                )''', '''                    onPlay = ::startPlayback,
                    onStop = ::stopPlayback,
                    onOpenSettings = { settingsOpen = true },
                )''', 1)
composer = composer.replace('''                    selectedDuration = selectedDuration,
                    dotted = selectedDotted,''', '''                    selectedDuration = selectedDuration,
                    durationOrder = appSettings.noteDurationOrder,
                    dotted = selectedDotted,''', 1)
composer = composer.replace('''                        canPlay = playableNoteCount > 0 && !liveRecordingActive && !comfortTempoCapturing,
                        onPlay = ::startPlayback,''', '''                        canPlay = playableNoteCount > 0 && !liveRecordingActive && !comfortTempoCapturing,
                        initialInputEnabled = appSettings.staffInputDefault,
                        followPlayback = appSettings.followPlayback,
                        onPlay = ::startPlayback,''', 1)
composer = composer.replace('''                        selectedEventIndex = selectedEventIndex,
                        onAddPitch = { pitch, tappedBeat ->''', '''                        selectedEventIndex = selectedEventIndex,
                        followPlayback = appSettings.followPlayback,
                        onAddPitch = { pitch, tappedBeat ->''', 1)
composer = composer.replace('''                        selectedDuration = selectedDuration,
                        selectedDotted = selectedDotted,''', '''                        selectedDuration = selectedDuration,
                        durationOrder = appSettings.noteDurationOrder,
                        noteLabelSetting = appSettings.keyboardNoteLabels,
                        selectedDotted = selectedDotted,''', 1)
# Remember octave whenever the existing octave-changing helper updates it.
composer = composer.replace('''        pianoOctaveShift = (pianoOctaveShift + delta).coerceIn(-4, 3)
''', '''        pianoOctaveShift = (pianoOctaveShift + delta).coerceIn(-4, 3)
        ScoreForgeSettingsRepository.rememberKeyboardOctave(context, pianoOctaveShift)
''', 1)
# Fresh default track in edit-state fallback uses current app clef default.
composer = composer.replace('state.tracks.ifEmpty { listOf(ScoreTracks.defaultTrack()) }', 'state.tracks.ifEmpty { listOf(ScoreTracks.defaultTrack().copy(clefMode = appSettings.defaultClefMode)) }', 1)
# Header signature + button.
composer = composer.replace('''    onPlay: () -> Unit,
    onStop: () -> Unit,
) {''', '''    onPlay: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
) {''', 1)
composer = composer.replace('''        Column {
            Text("Score Forge", style = MaterialTheme.typography.titleLarge, color = Color.White)''', '''        Column {
            Text("Score Forge", style = MaterialTheme.typography.titleLarge, color = Color.White)''', 1)
# Insert settings button after header information column closes, immediately before tempo -5 button.
needle = '''        ChamferedControlButton(
            label = "−5",'''
replacement = '''        ComposerToolbarButton(
            label = "⚙ Settings",
            onClick = onOpenSettings,
        )

        ChamferedControlButton(
            label = "−5",'''
if needle not in composer:
    raise SystemExit('header tempo button not found')
composer = composer.replace(needle, replacement, 1)
composer_path.write_text(composer)

build_path = ROOT / 'app/build.gradle.kts'
build = build_path.read_text().replace('versionCode = 50', 'versionCode = 51', 1).replace('versionName = "0.2.47"', 'versionName = "0.2.48"', 1)
build_path.write_text(build)

(ROOT / 'docs/settings-0.2.48-phone-test.md').write_text('''# Score Forge 0.2.48 Settings phone test\n\nVerify the hybrid full-screen Settings page opens from the header and Back returns to the composer.\n\nCore checks:\n- Settings persist after force-closing and reopening Score Forge.\n- Screen Orientation: Follow Device / Lock Portrait / Lock Landscape all behave correctly.\n- Haptic Off / Light / Standard / Strong are distinguishable; Standard matches 0.2.47.\n- UI command sounds can be disabled and re-enabled.\n- Note duration order changes both top toolbar and lower piano duration palette.\n- Keep Screen Awake applies immediately.\n- Keyboard note labels support Off / C notes only / All notes.\n- Remember Keyboard Octave persists the last octave when enabled.\n- Default Editor / Clef / Entry Mode / Staff Input apply on fresh sessions/tracks as described.\n- Follow Playback disables/enables automatic playhead following.\n- Restore Last Project and Autosave / Recovery toggles control draft behavior.\n- Duration audition settings persist; actual duration-preview audio lands in the next QOL slice.\n- Reset Settings to Defaults changes preferences without touching project data.\n''')
