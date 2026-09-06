from pathlib import Path

ROOT = Path('.')
UI = ROOT / 'app/src/main/java/com/scoreforge/app/ui'
TEST = ROOT / 'app/src/test/java/com/scoreforge/app/ui'


def replace_once(path: Path, old: str, new: str):
    text = path.read_text()
    if old not in text:
        raise SystemExit(f'Pattern not found in {path}: {old[:120]!r}')
    path.write_text(text.replace(old, new, 1))


# 1) Dedicated Variant-A Settings launcher instead of special-casing the generic button.
control = UI / 'ControlShapes.kt'
text = control.read_text()
for unused in [
    'import androidx.compose.foundation.clickable\n',
    'import androidx.compose.foundation.layout.Box\n',
    'import androidx.compose.foundation.layout.size\n',
    'import androidx.compose.ui.unit.sp\n',
]:
    text = text.replace(unused, '')
text = text.replace(
    'enum class UiCommandFeedback {\n    NONE,\n    NEUTRAL,\n    INCREASE,\n    DECREASE,\n}',
    'enum class UiCommandFeedback {\n    NONE,\n    NEUTRAL,\n    INCREASE,\n    DECREASE,\n    SETTINGS,\n}',
)
old_special = '''    // The app-level Settings control is intentionally just a large accent gear.\n    // Keep a generous invisible touch target, haptic feedback, and no button chrome.\n    if (label == "⚙" && !compact) {\n        Box(\n            modifier = modifier\n                .size(50.dp)\n                .clickable(enabled = enabled) {\n                    view.performScoreForgeHaptic(UiHapticFeedback.TICK)\n                    onClick()\n                },\n            contentAlignment = Alignment.Center,\n        ) {\n            Text(\n                text = "⚙",\n                color = if (enabled) Color(0xFFD0B8FF) else Color(0xFF8A8197),\n                fontSize = 40.sp,\n            )\n        }\n        return\n    }\n\n'''
if old_special not in text:
    raise SystemExit('Old standalone gear special-case not found')
text = text.replace(old_special, '', 1)
control.write_text(text)

(UI / 'SettingsLaunchButton.kt').write_text(r'''package com.scoreforge.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * App-level Settings launcher. Deliberately more prominent than ordinary command buttons:
 * Variant A from the phone mockups — lavender gear on a compact dark rounded tile.
 */
@Composable
internal fun SettingsLaunchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var activated by remember { mutableStateOf(false) }

    val gearRotation by animateFloatAsState(
        targetValue = if (activated) 18f else 0f,
        animationSpec = tween(durationMillis = 105),
        label = "settingsGearRotation",
    )
    val gearScale by animateFloatAsState(
        targetValue = if (activated) 0.84f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "settingsGearScale",
    )
    val tileColor by animateColorAsState(
        targetValue = if (activated) Color(0xFF494253) else Color(0xFF35323B),
        animationSpec = tween(durationMillis = 90),
        label = "settingsTileColor",
    )

    Surface(
        modifier = modifier
            .size(52.dp)
            .clickable(enabled = enabled) {
                if (activated) return@clickable
                view.performScoreForgeHaptic(UiHapticFeedback.TICK)
                ScoreForgeUiFeedback.play(UiCommandFeedback.SETTINGS, view.context)
                activated = true
                scope.launch {
                    // Let the gear visibly react before replacing the composer with Settings.
                    delay(120L)
                    activated = false
                    delay(35L)
                    onClick()
                }
            },
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) tileColor else Color(0xFF302E35),
        border = BorderStroke(1.dp, if (enabled) Color(0xFF756D82) else Color(0xFF56515E)),
        tonalElevation = 2.dp,
        shadowElevation = if (activated) 0.dp else 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "⚙",
                color = if (enabled) Color(0xFFD0B8FF) else Color(0xFF8A8197),
                fontSize = 37.sp,
                modifier = Modifier.graphicsLayer {
                    rotationZ = gearRotation
                    scaleX = gearScale
                    scaleY = gearScale
                },
            )
        }
    }
}
''')

# 2) Unique Settings open sound, still governed by the UI Command Sounds preference.
feedback = UI / 'ScoreForgeUiFeedback.kt'
text = feedback.read_text()
text = text.replace(
    '    private val neutralTrack by lazy { buildTrack(startHz = 610.0, endHz = 660.0) }\n',
    '    private val neutralTrack by lazy { buildTrack(startHz = 610.0, endHz = 660.0) }\n'
    '    private val settingsTrack by lazy { buildTrack(startHz = 480.0, endHz = 760.0, durationMs = 68) }\n',
    1,
)
text = text.replace(
    '            UiCommandFeedback.DECREASE -> decreaseTrack\n',
    '            UiCommandFeedback.DECREASE -> decreaseTrack\n'
    '            UiCommandFeedback.SETTINGS -> settingsTrack\n',
    1,
)
text = text.replace(
    '    private fun buildTrack(startHz: Double, endHz: Double): AudioTrack? = try {\n'
    '        val sampleCount = (SAMPLE_RATE * DURATION_MS / 1000.0).toInt().coerceAtLeast(1)\n',
    '    private fun buildTrack(startHz: Double, endHz: Double, durationMs: Int = DURATION_MS): AudioTrack? = try {\n'
    '        val sampleCount = (SAMPLE_RATE * durationMs / 1000.0).toInt().coerceAtLeast(1)\n',
    1,
)
feedback.write_text(text)

# 3) Pure timing/velocity helper for the note-duration audition.
(UI / 'NoteDurationAudition.kt').write_text(r'''package com.scoreforge.app.ui

import com.scoreforge.app.music.NoteDuration
import kotlin.math.roundToInt

internal object NoteDurationAudition {
    const val MIDI_PITCH = 60 // Middle C (C4)
    private const val KEYBOARD_REFERENCE_VELOCITY = 96

    fun durationMs(duration: NoteDuration, dotted: Boolean, bpm: Int): Long {
        val safeBpm = bpm.coerceIn(30, 300)
        val quarterMs = 60_000f / safeBpm.toFloat()
        return (duration.effectiveBeats(dotted) * quarterMs).roundToInt().coerceAtLeast(1).toLong()
    }

    /** Maps the independent audition-volume preference against the keyboard's normal velocity. */
    fun velocity(volume: Float): Int =
        (KEYBOARD_REFERENCE_VELOCITY * volume.coerceIn(0.05f, 0.70f))
            .roundToInt()
            .coerceIn(1, 127)
}
''')

TEST.mkdir(parents=True, exist_ok=True)
(TEST / 'NoteDurationAuditionTest.kt').write_text(r'''package com.scoreforge.app.ui

import com.scoreforge.app.music.NoteDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteDurationAuditionTest {
    @Test
    fun durationTracksWrittenLengthAtCurrentTempo() {
        assertEquals(2_000L, NoteDurationAudition.durationMs(NoteDuration.WHOLE, dotted = false, bpm = 120))
        assertEquals(500L, NoteDurationAudition.durationMs(NoteDuration.QUARTER, dotted = false, bpm = 120))
        assertEquals(250L, NoteDurationAudition.durationMs(NoteDuration.EIGHTH, dotted = false, bpm = 120))
        assertEquals(1_500L, NoteDurationAudition.durationMs(NoteDuration.HALF, dotted = true, bpm = 120))
    }

    @Test
    fun defaultAuditionVolumeIsQuieterThanKeyboardVelocity() {
        assertEquals(34, NoteDurationAudition.velocity(0.35f))
    }
}
''')

# 4) Composer wiring: monophonic/cancelable Middle-C preview from both duration palettes.
composer = UI / 'ComposerScreen.kt'
text = composer.read_text()
text = text.replace(
    'import androidx.compose.runtime.remember\n',
    'import androidx.compose.runtime.remember\nimport androidx.compose.runtime.rememberCoroutineScope\n',
    1,
)
text = text.replace(
    'import kotlinx.coroutines.Dispatchers\n',
    'import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.Job\n',
    1,
)
text = text.replace(
    'import kotlinx.coroutines.flow.collect\n',
    'import kotlinx.coroutines.flow.collect\nimport kotlinx.coroutines.launch\n',
    1,
)
text = text.replace(
    '    val liveHeldInputs = remember { mutableMapOf<Int, LiveHeldInput>() }\n',
    '    val liveHeldInputs = remember { mutableMapOf<Int, LiveHeldInput>() }\n'
    '    val durationAuditionScope = rememberCoroutineScope()\n'
    '    var durationAuditionJob by remember { mutableStateOf<Job?>(null) }\n',
    1,
)
needle = '    val activeTempoBpm = ScoreTempos.atBeat(tempoChanges, activeCursorBeat).bpm\n\n'
insert = r'''    val activeTempoBpm = ScoreTempos.atBeat(tempoChanges, activeCursorBeat).bpm

    fun stopDurationAudition() {
        durationAuditionJob?.cancel()
        durationAuditionJob = null
        LiveInstrumentBus.noteOff(NoteDurationAudition.MIDI_PITCH)
    }

    fun selectDurationWithAudition(duration: NoteDuration) {
        selectedDuration = duration
        stopDurationAudition()
        if (
            !appSettings.noteDurationAuditionEnabled ||
            isPlaying ||
            liveRecordingActive ||
            comfortTempoCapturing
        ) return

        val previewVelocity = NoteDurationAudition.velocity(appSettings.noteDurationAuditionVolume)
        if (!LiveInstrumentBus.noteOn(NoteDurationAudition.MIDI_PITCH, velocity = previewVelocity)) return

        val previewMs = NoteDurationAudition.durationMs(
            duration = duration,
            dotted = selectedDotted,
            bpm = activeTempoBpm,
        )
        durationAuditionJob = durationAuditionScope.launch {
            try {
                delay(previewMs)
            } finally {
                LiveInstrumentBus.noteOff(NoteDurationAudition.MIDI_PITCH)
            }
        }
    }

'''
if needle not in text:
    raise SystemExit('activeTempoBpm insertion point not found')
text = text.replace(needle, insert, 1)
# Stop any preview when the composable leaves the screen/process composition.
transport_effect = '''    LaunchedEffect(Unit) {\n        ScoreTransportBus.state.collect { state ->\n            isPlaying = state.isPlaying\n        }\n    }\n\n'''
if transport_effect not in text:
    raise SystemExit('Transport effect block not found')
text = text.replace(
    transport_effect,
    transport_effect + '''    DisposableEffect(Unit) {\n        onDispose { stopDurationAudition() }\n    }\n\n''',
    1,
)
if text.count('onDurationSelected = { selectedDuration = it },') != 2:
    raise SystemExit(f'Expected two duration callbacks, found {text.count("onDurationSelected = { selectedDuration = it },")}')
text = text.replace('onDurationSelected = { selectedDuration = it },', 'onDurationSelected = ::selectDurationWithAudition,', 2)
text = text.replace(
    '                    onOpenSettings = { settingsOpen = true },\n',
    '                    onOpenSettings = {\n'
    '                        stopDurationAudition()\n'
    '                        settingsOpen = true\n'
    '                    },\n',
    1,
)
old_gear = '''        ChamferedControlButton(\n            label = "⚙",\n            onClick = onOpenSettings,\n            compact = false,\n        )\n'''
if old_gear not in text:
    raise SystemExit('Header gear block not found')
text = text.replace(old_gear, '        SettingsLaunchButton(onClick = onOpenSettings)\n', 1)
composer.write_text(text)

# 5) Settings copy now describes the live feature and disables the volume slider when audition is off.
settings_screen = UI / 'ScoreForgeSettingsScreen.kt'
text = settings_screen.read_text()
text = text.replace(
    'description = "Small chirps on supported increase/decrease command buttons.",',
    'description = "Small command chirps, including the distinct Settings-open sound.",',
    1,
)
text = text.replace(
    'description = "Controls the upcoming Middle-C duration preview when choosing note lengths.",',
    'description = "Play a quiet Middle C using the selected instrument when choosing note lengths.",',
    1,
)
text = text.replace(
    '                Slider(\n                    value = settings.noteDurationAuditionVolume,\n',
    '                Slider(\n                    value = settings.noteDurationAuditionVolume,\n                    enabled = settings.noteDurationAuditionEnabled,\n',
    1,
)
settings_screen.write_text(text)

# 6) Phone-test version bump so this audio-capable build is unmistakable.
gradle = ROOT / 'app/build.gradle.kts'
text = gradle.read_text()
text = text.replace('versionCode = 51', 'versionCode = 52', 1)
text = text.replace('versionName = "0.2.48"', 'versionName = "0.2.49"', 1)
gradle.write_text(text)
