package com.scoreforge.app.ui

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
