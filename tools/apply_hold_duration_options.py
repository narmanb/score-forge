from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


composer_path = Path("app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt")
composer = composer_path.read_text()
composer = replace_once(
    composer,
    "import com.scoreforge.app.music.HoldEntryTiming\n",
    "import com.scoreforge.app.music.HoldDurationMode\nimport com.scoreforge.app.music.HoldDurationTiming\nimport com.scoreforge.app.music.HoldEntryTiming\n",
    "composer imports",
)
composer = replace_once(
    composer,
    "    var pianoEntryMode by rememberSaveable { mutableStateOf(PianoEntryMode.STEP) }\n",
    "    var pianoEntryMode by rememberSaveable { mutableStateOf(PianoEntryMode.STEP) }\n    var holdDurationMode by rememberSaveable { mutableStateOf(HoldDurationMode.STANDARD) }\n",
    "hold mode state",
)
composer = replace_once(
    composer,
    "        val candidate = NaturalEntryTiming.writtenForHoldMs(elapsedMs, group.bpm)\n",
    "        val candidate = HoldDurationTiming.writtenForHoldMs(\n            holdMs = elapsedMs,\n            bpm = group.bpm,\n            mode = holdDurationMode,\n        )\n",
    "hold update timing",
)
composer = replace_once(
    composer,
    "        else NaturalEntryTiming.writtenForHoldMs(0L, bpm)\n",
    "        else HoldDurationTiming.writtenForHoldMs(0L, bpm, holdDurationMode)\n",
    "hold initial timing",
)
composer = replace_once(
    composer,
    "                        entryMode = pianoEntryMode,\n                        liveRecordingActive = liveRecordingActive,\n",
    "                        entryMode = pianoEntryMode,\n                        holdDurationMode = holdDurationMode,\n                        liveRecordingActive = liveRecordingActive,\n",
    "keyboard hold mode argument",
)
composer = replace_once(
    composer,
    "                            pianoEntryMode = mode\n                        },\n                        onStopLive = ::stopLiveRecording,\n",
    "                            pianoEntryMode = mode\n                        },\n                        onHoldDurationModeChanged = { mode ->\n                            if (holdDurationMode != mode) {\n                                finishHoldGroupForUiBreak()\n                                LiveInstrumentBus.allNotesOff()\n                                holdPreviewWritten = null\n                                holdDurationMode = mode\n                            }\n                        },\n                        onStopLive = ::stopLiveRecording,\n",
    "hold mode callback",
)
composer_path.write_text(composer)

keyboard_path = Path("app/src/main/java/com/scoreforge/app/ui/MultitouchPianoKeyboard.kt")
keyboard = keyboard_path.read_text()
keyboard = replace_once(
    keyboard,
    "import com.scoreforge.app.music.NoteArticulation\n",
    "import com.scoreforge.app.music.HoldDurationMode\nimport com.scoreforge.app.music.NoteArticulation\n",
    "keyboard import",
)
keyboard = replace_once(
    keyboard,
    "    entryMode: PianoEntryMode,\n    liveRecordingActive: Boolean,\n",
    "    entryMode: PianoEntryMode,\n    holdDurationMode: HoldDurationMode,\n    liveRecordingActive: Boolean,\n",
    "keyboard mode parameter",
)
keyboard = replace_once(
    keyboard,
    "    onEntryModeChanged: (PianoEntryMode) -> Unit,\n    onStopLive: () -> Unit,\n",
    "    onEntryModeChanged: (PianoEntryMode) -> Unit,\n    onHoldDurationModeChanged: (HoldDurationMode) -> Unit,\n    onStopLive: () -> Unit,\n",
    "keyboard callback parameter",
)
old_hold_block = '''                if (entryMode == PianoEntryMode.HOLD) {
                    Text(
                        text = holdPreviewDuration?.let {
                            "Now: ${durationControlLabel(it, holdPreviewDotted)}"
                        } ?: "Hold: press a key",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                } else {
'''
new_hold_block = '''                if (entryMode == PianoEntryMode.HOLD) {
                    Text(
                        text = holdPreviewDuration?.let {
                            "Now: ${durationControlLabel(it, holdPreviewDotted)}"
                        } ?: "Hold: press a key",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                    Text("Length", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    HoldDurationMode.entries.forEach { option ->
                        ChamferedControlButton(
                            label = option.displayName,
                            onClick = {
                                if (holdDurationMode != option) {
                                    releaseAllPitches()
                                    onHoldDurationModeChanged(option)
                                }
                            },
                            selected = holdDurationMode == option,
                        )
                    }
                } else {
'''
keyboard = replace_once(keyboard, old_hold_block, new_hold_block, "hold option controls")
keyboard_path.write_text(keyboard)

build_path = Path("app/build.gradle.kts")
build = build_path.read_text()
build = replace_once(build, '        versionCode = 33\n', '        versionCode = 34\n', "version code")
build = replace_once(build, '        versionName = "0.2.30"\n', '        versionName = "0.2.31"\n', "version name")
build_path.write_text(build)
