from pathlib import Path
import re


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match in {path}, found {count}: {old[:120]!r}")
    file.write_text(text.replace(old, new, 1))


def regex_once(path: str, pattern: str, replacement: str) -> None:
    file = Path(path)
    text = file.read_text()
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Regex expected one match in {path}, found {count}: {pattern[:120]!r}")
    file.write_text(updated)


# The first-stage updater intentionally runs first. It installs imports, feedback enum,
# and the enhanced chamfered button before stopping at the old compact-button matcher.
control = "app/src/main/java/com/scoreforge/app/ui/ControlShapes.kt"
compact_block = r'''@Composable\ninternal fun CompactCommandButton\(.*?\n}\n\nenum class PianoEntryMode\(val displayName: String\) \{.*?\n}\n'''
compact_replacement = '''@Composable
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
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            ScoreForgeUiFeedback.play(feedback)
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
'''
regex_once(control, compact_block, compact_replacement)

Path("app/src/main/java/com/scoreforge/app/ui/ScoreForgeUiFeedback.kt").write_text('''package com.scoreforge.app.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/** Tiny process-lifetime UI chirps for tactile command feedback. */
internal object ScoreForgeUiFeedback {
    private const val SAMPLE_RATE = 24_000
    private const val DURATION_MS = 34
    private const val VOLUME = 0.16f

    private val increaseTrack by lazy { buildTrack(startHz = 700.0, endHz = 980.0) }
    private val decreaseTrack by lazy { buildTrack(startHz = 620.0, endHz = 420.0) }
    private val neutralTrack by lazy { buildTrack(startHz = 610.0, endHz = 660.0) }

    fun play(feedback: UiCommandFeedback) {
        val track = when (feedback) {
            UiCommandFeedback.NONE -> return
            UiCommandFeedback.NEUTRAL -> neutralTrack
            UiCommandFeedback.INCREASE -> increaseTrack
            UiCommandFeedback.DECREASE -> decreaseTrack
        } ?: return

        synchronized(track) {
            try {
                if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
                track.setPlaybackHeadPosition(0)
                track.setVolume(VOLUME)
                track.play()
            } catch (_: IllegalStateException) {
                // Sonification is optional; never let a device audio quirk break a command.
            }
        }
    }

    private fun buildTrack(startHz: Double, endHz: Double): AudioTrack? = try {
        val sampleCount = (SAMPLE_RATE * DURATION_MS / 1000.0).toInt().coerceAtLeast(1)
        val pcm = ShortArray(sampleCount)
        var phase = 0.0
        for (i in pcm.indices) {
            val progress = if (pcm.lastIndex == 0) 0.0 else i.toDouble() / pcm.lastIndex.toDouble()
            val frequency = startHz + (endHz - startHz) * progress
            phase += 2.0 * PI * frequency / SAMPLE_RATE.toDouble()
            val envelope = sin(PI * progress).coerceAtLeast(0.0)
            pcm[i] = (sin(phase) * envelope * Short.MAX_VALUE * 0.55).toInt().toShort()
        }

        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
            .also { track -> track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING) }
    } catch (_: Exception) {
        null
    }
}
''')

# Directional command sounds/haptics.
time_path = "app/src/main/java/com/scoreforge/app/ui/TimeSignatureControls.kt"
for label, kind in [("Num −", "DECREASE"), ("Num +", "INCREASE"), ("Denom −", "DECREASE"), ("Denom +", "INCREASE")]:
    replace_once(time_path, f'            label = "{label}",\n            onClick = {{', f'            label = "{label}",\n            feedback = UiCommandFeedback.{kind},\n            onClick = {{')

key_path = "app/src/main/java/com/scoreforge/app/ui/KeySignatureControls.kt"
for label, kind in [("Flatter ♭", "DECREASE"), ("Sharper ♯", "INCREASE")]:
    replace_once(key_path, f'            label = "{label}",\n            onClick = {{', f'            label = "{label}",\n            feedback = UiCommandFeedback.{kind},\n            onClick = {{')

# Hold-mode controls and live duration readout.
keyboard = "app/src/main/java/com/scoreforge/app/ui/MultitouchPianoKeyboard.kt"
replace_once(keyboard, '''    entryMode: PianoEntryMode,
    liveRecordingActive: Boolean,
    selectedDuration: NoteDuration,
''', '''    entryMode: PianoEntryMode,
    liveRecordingActive: Boolean,
    holdPreviewDuration: NoteDuration?,
    holdPreviewDotted: Boolean,
    selectedDuration: NoteDuration,
''')
replace_once(keyboard, "// Natural and Live mode lock one finger to the key it first pressed.", "// Natural, Hold, and Live modes lock one finger to the key it first pressed.")
replace_once(keyboard, '''                ChamferedControlButton(
                    label = "Oct −",
                    onClick = {
''', '''                ChamferedControlButton(
                    label = "Oct −",
                    feedback = UiCommandFeedback.DECREASE,
                    onClick = {
''')
replace_once(keyboard, '''                ChamferedControlButton(
                    label = "Oct +",
                    onClick = {
''', '''                ChamferedControlButton(
                    label = "Oct +",
                    feedback = UiCommandFeedback.INCREASE,
                    onClick = {
''')
replace_once(keyboard, '''                ChamferedControlButton(
                    label = "Natural",
                    onClick = {
                        if (entryMode != PianoEntryMode.NATURAL) {
                            releaseAllPitches()
                            onEntryModeChanged(PianoEntryMode.NATURAL)
                        }
                    },
                    selected = entryMode == PianoEntryMode.NATURAL,
                )
                ChamferedControlButton(
                    label = when {
''', '''                ChamferedControlButton(
                    label = "Natural",
                    onClick = {
                        if (entryMode != PianoEntryMode.NATURAL) {
                            releaseAllPitches()
                            onEntryModeChanged(PianoEntryMode.NATURAL)
                        }
                    },
                    selected = entryMode == PianoEntryMode.NATURAL,
                )
                ChamferedControlButton(
                    label = "Hold",
                    onClick = {
                        if (entryMode != PianoEntryMode.HOLD) {
                            releaseAllPitches()
                            onEntryModeChanged(PianoEntryMode.HOLD)
                        }
                    },
                    selected = entryMode == PianoEntryMode.HOLD,
                )
                ChamferedControlButton(
                    label = when {
''')
replace_once(keyboard, '''                ChamferedControlButton(
                    label = durationControlLabel(selectedDuration, selectedDotted),
                    onClick = {
                        releaseAllPitches()
                        articulationPaletteOpen = false
                        durationPaletteOpen = true
                    },
                )

                ChamferedControlButton(
''', '''                if (entryMode == PianoEntryMode.HOLD) {
                    Text(
                        text = holdPreviewDuration?.let {
                            "Now: ${durationControlLabel(it, holdPreviewDotted)}"
                        } ?: "Hold: press a key",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                } else {
                    ChamferedControlButton(
                        label = durationControlLabel(selectedDuration, selectedDotted),
                        onClick = {
                            releaseAllPitches()
                            articulationPaletteOpen = false
                            durationPaletteOpen = true
                        },
                    )
                }

                ChamferedControlButton(
''')

composer = "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt"
replace_once(composer, '''private data class NaturalOnsetGroup(
    val onsetMs: Long,
    val startBeat: Float,
    val bpm: Int,
    val eventIndices: List<Int>,
    val maxReleasedHoldMs: Long = 0L,
)

private data class LiveHeldInput(
''', '''private data class NaturalOnsetGroup(
    val onsetMs: Long,
    val startBeat: Float,
    val bpm: Int,
    val eventIndices: List<Int>,
    val maxReleasedHoldMs: Long = 0L,
)

private data class HoldHeldInput(
    val eventIndex: Int,
    val groupOnsetMs: Long,
)

private data class HoldOnsetGroup(
    val onsetMs: Long,
    val startBeat: Float,
    val bpm: Int,
    val eventIndices: List<Int>,
    val currentWritten: NaturalEntryTiming.WrittenDuration,
)

private data class LiveHeldInput(
''')
replace_once(composer, '''    val naturalHeldInputs = remember { mutableMapOf<Int, NaturalHeldInput>() }
    val liveHeldInputs = remember { mutableMapOf<Int, LiveHeldInput>() }
''', '''    val naturalHeldInputs = remember { mutableMapOf<Int, NaturalHeldInput>() }
    val holdHeldInputs = remember { mutableMapOf<Int, HoldHeldInput>() }
    val liveHeldInputs = remember { mutableMapOf<Int, LiveHeldInput>() }
''')
replace_once(composer, '''    var naturalCurrentGroup by remember { mutableStateOf<NaturalOnsetGroup?>(null) }
    var naturalRecentIntervalsMs by remember { mutableStateOf(emptyList<Long>()) }
    var liveRecordingStartedAtMs by remember { mutableStateOf<Long?>(null) }
''', '''    var naturalCurrentGroup by remember { mutableStateOf<NaturalOnsetGroup?>(null) }
    var naturalRecentIntervalsMs by remember { mutableStateOf(emptyList<Long>()) }
    var holdCurrentGroup by remember { mutableStateOf<HoldOnsetGroup?>(null) }
    var holdPreviewWritten by remember { mutableStateOf<NaturalEntryTiming.WrittenDuration?>(null) }
    var liveRecordingStartedAtMs by remember { mutableStateOf<Long?>(null) }
''')
replace_once(composer, '''    fun cancelNaturalEntryGroup() {
        naturalHeldInputs.clear()
        naturalCurrentGroup = null
        naturalRecentIntervalsMs = emptyList()
    }
''', '''    fun cancelNaturalEntryGroup() {
        naturalHeldInputs.clear()
        naturalCurrentGroup = null
        naturalRecentIntervalsMs = emptyList()
        holdHeldInputs.clear()
        holdCurrentGroup = null
        holdPreviewWritten = null
    }
''')

hold_functions = '''

    fun applyHoldGroupDuration(
        group: HoldOnsetGroup,
        written: NaturalEntryTiming.WrittenDuration,
    ) {
        val track = currentTrack()
        val updatedEvents = track.events.toMutableList()
        group.eventIndices.forEach { index ->
            val note = updatedEvents.getOrNull(index) as? ScoreNote ?: return@forEach
            updatedEvents[index] = note.copy(duration = written.duration, dotted = written.dotted)
        }
        replaceActiveTrack {
            it.copy(events = updatedEvents, cursorBeat = group.startBeat + written.beats)
        }
    }

    fun updateHoldGroupAt(nowMs: Long): NaturalEntryTiming.WrittenDuration? {
        val group = holdCurrentGroup ?: return null
        val elapsedMs = (nowMs - group.onsetMs).coerceAtLeast(0L)
        val candidate = NaturalEntryTiming.writtenForHoldMs(elapsedMs, group.bpm)
        val written = if (candidate.beats >= group.currentWritten.beats) candidate else group.currentWritten
        val updatedGroup = if (written != group.currentWritten) group.copy(currentWritten = written) else group
        holdCurrentGroup = updatedGroup
        holdPreviewWritten = written
        applyHoldGroupDuration(updatedGroup, written)
        return written
    }

    fun beginHoldPitch(pitch: Int) {
        repairUnexpectedTransportForEntry()
        if (holdHeldInputs.containsKey(pitch)) return
        val now = SystemClock.elapsedRealtime()
        val previousGroup = holdCurrentGroup
        val joinsCurrentChord = previousGroup != null && holdHeldInputs.isNotEmpty()
        val startBeat = if (joinsCurrentChord) previousGroup!!.startBeat else currentTrack().cursorBeat
        val initialWritten = if (joinsCurrentChord) previousGroup!!.currentWritten
        else NaturalEntryTiming.writtenForHoldMs(0L, bpm)

        if (!joinsCurrentChord) recordBeforeScoreEdit()

        val eventIndex = currentTrack().events.size
        replaceActiveTrack {
            it.copy(
                events = it.events + ScoreNote(
                    midiPitch = pitch,
                    duration = initialWritten.duration,
                    startBeat = startBeat,
                    dotted = initialWritten.dotted,
                    articulation = selectedArticulation,
                ),
                cursorBeat = startBeat + initialWritten.beats,
            )
        }

        val group = if (joinsCurrentChord) {
            previousGroup!!.copy(eventIndices = previousGroup.eventIndices + eventIndex)
        } else {
            HoldOnsetGroup(
                onsetMs = now,
                startBeat = startBeat,
                bpm = bpm,
                eventIndices = listOf(eventIndex),
                currentWritten = initialWritten,
            )
        }
        holdCurrentGroup = group
        holdHeldInputs[pitch] = HoldHeldInput(eventIndex = eventIndex, groupOnsetMs = group.onsetMs)
        holdPreviewWritten = initialWritten
        selectedEventIndex = eventIndex
        if (!LiveInstrumentBus.noteOn(pitch, velocity = 96)) playback.previewPitch(pitch)
    }

    fun finishHoldPitch(pitch: Int) {
        LiveInstrumentBus.noteOff(pitch)
        val held = holdHeldInputs.remove(pitch) ?: return
        val group = holdCurrentGroup
        if (group != null && group.onsetMs == held.groupOnsetMs) {
            updateHoldGroupAt(SystemClock.elapsedRealtime())
        }
        if (holdHeldInputs.values.none { it.groupOnsetMs == held.groupOnsetMs }) {
            holdCurrentGroup = null
            syncHistoryButtons()
        }
    }

    fun finishHoldGroupForUiBreak() {
        if (holdCurrentGroup != null) updateHoldGroupAt(SystemClock.elapsedRealtime())
        if (holdHeldInputs.isNotEmpty()) LiveInstrumentBus.allNotesOff()
        holdHeldInputs.clear()
        holdCurrentGroup = null
    }
'''
replace_once(composer, '    fun restoreEditState(state: ScoreEditState) {\n', hold_functions + '\n    fun restoreEditState(state: ScoreEditState) {\n')
replace_once(composer, '    LaunchedEffect(pianoEntryMode, liveRecordingStartedAtMs, isPlaying) {\n', '''    LaunchedEffect(pianoEntryMode, holdCurrentGroup?.onsetMs) {
        val onset = holdCurrentGroup?.onsetMs ?: return@LaunchedEffect
        if (pianoEntryMode != PianoEntryMode.HOLD) return@LaunchedEffect
        while (
            pianoEntryMode == PianoEntryMode.HOLD &&
            holdCurrentGroup?.onsetMs == onset &&
            holdHeldInputs.values.any { it.groupOnsetMs == onset }
        ) {
            updateHoldGroupAt(SystemClock.elapsedRealtime())
            delay(33L)
        }
    }

    LaunchedEffect(pianoEntryMode, liveRecordingStartedAtMs, isPlaying) {
''')
replace_once(composer, '''        if (pianoEntryMode == PianoEntryMode.NATURAL) {
            finishNaturalPhraseForStaffBrowse()
            LiveInstrumentBus.allNotesOff()
        } else if (pianoEntryMode == PianoEntryMode.LIVE && liveRecordingActive) {
            stopLiveRecording()
        }
''', '''        if (pianoEntryMode == PianoEntryMode.NATURAL) {
            finishNaturalPhraseForStaffBrowse()
            LiveInstrumentBus.allNotesOff()
        } else if (pianoEntryMode == PianoEntryMode.HOLD) {
            finishHoldGroupForUiBreak()
        } else if (pianoEntryMode == PianoEntryMode.LIVE && liveRecordingActive) {
            stopLiveRecording()
        }
''')
replace_once(composer, '''        ChamferedControlButton(
            label = "−5",
            onClick = onTempoDown,
''', '''        ChamferedControlButton(
            label = "−5",
            feedback = UiCommandFeedback.DECREASE,
            onClick = onTempoDown,
''')
replace_once(composer, '''        ChamferedControlButton(
            label = "+5",
            onClick = onTempoUp,
''', '''        ChamferedControlButton(
            label = "+5",
            feedback = UiCommandFeedback.INCREASE,
            onClick = onTempoUp,
''')
replace_once(composer, '''                        entryMode = pianoEntryMode,
                        liveRecordingActive = liveRecordingActive,
                        selectedDuration = selectedDuration,
''', '''                        entryMode = pianoEntryMode,
                        liveRecordingActive = liveRecordingActive,
                        holdPreviewDuration = holdPreviewWritten?.duration,
                        holdPreviewDotted = holdPreviewWritten?.dotted ?: false,
                        selectedDuration = selectedDuration,
''')
replace_once(composer, '''                        onManualBrowse = {
                            if (pianoEntryMode == PianoEntryMode.NATURAL) {
                                finishNaturalPhraseForStaffBrowse()
                            }
                        },
''', '''                        onManualBrowse = {
                            when (pianoEntryMode) {
                                PianoEntryMode.NATURAL -> finishNaturalPhraseForStaffBrowse()
                                PianoEntryMode.HOLD -> finishHoldGroupForUiBreak()
                                else -> Unit
                            }
                        },
''')
replace_once(composer, '''                                    PianoEntryMode.NATURAL -> beginNaturalPitch(pitch)
                                    PianoEntryMode.LIVE -> beginLivePitch(pitch)
''', '''                                    PianoEntryMode.NATURAL -> beginNaturalPitch(pitch)
                                    PianoEntryMode.HOLD -> beginHoldPitch(pitch)
                                    PianoEntryMode.LIVE -> beginLivePitch(pitch)
''')
replace_once(composer, '''                                PianoEntryMode.NATURAL -> finishNaturalPitch(pitch)
                                PianoEntryMode.LIVE -> finishLivePitch(pitch)
''', '''                                PianoEntryMode.NATURAL -> finishNaturalPitch(pitch)
                                PianoEntryMode.HOLD -> finishHoldPitch(pitch)
                                PianoEntryMode.LIVE -> finishLivePitch(pitch)
''')

replace_once("app/build.gradle.kts", '        versionCode = 30\n        versionName = "0.2.27"', '        versionCode = 31\n        versionName = "0.2.28"')

Path("app/src/test/java/com/scoreforge/app/music/HoldEntryTimingTest.kt").write_text('''package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldEntryTimingTest {
    @Test
    fun writtenDurationNeverShrinksWhileHoldTimeIncreases() {
        var previousBeats = 0f
        for (holdMs in 0L..4_000L step 25L) {
            val written = NaturalEntryTiming.writtenForHoldMs(holdMs, bpm = 120)
            assertTrue(written.beats >= previousBeats)
            previousBeats = written.beats
        }
    }

    @Test
    fun dottedQuarterTransitionsToHalfBeforeReleaseAt120Bpm() {
        val dottedQuarter = NaturalEntryTiming.writtenForHoldMs(800L, bpm = 120)
        assertEquals(NoteDuration.QUARTER, dottedQuarter.duration)
        assertTrue(dottedQuarter.dotted)

        val half = NaturalEntryTiming.writtenForHoldMs(900L, bpm = 120)
        assertEquals(NoteDuration.HALF, half.duration)
        assertFalse(half.dotted)
    }
}
''')

print("Applied Score Forge 0.2.28 second-stage update")
