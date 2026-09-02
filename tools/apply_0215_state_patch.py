from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, found {count}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1))

composer = "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt"
replace_once(
    composer,
    "import androidx.compose.runtime.remember\nimport androidx.compose.runtime.setValue\n",
    "import androidx.compose.runtime.remember\nimport androidx.compose.runtime.saveable.rememberSaveable\nimport androidx.compose.runtime.setValue\n",
)
for old, new in [
    ("var selectedDuration by remember { mutableStateOf(NoteDuration.QUARTER) }", "var selectedDuration by rememberSaveable { mutableStateOf(NoteDuration.QUARTER) }"),
    ("var selectedDotted by remember { mutableStateOf(false) }", "var selectedDotted by rememberSaveable { mutableStateOf(false) }"),
    ("var selectedArticulation by remember { mutableStateOf(NoteArticulation.NORMAL) }", "var selectedArticulation by rememberSaveable { mutableStateOf(NoteArticulation.NORMAL) }"),
    ("var bpm by remember { mutableIntStateOf(120) }", "var bpm by rememberSaveable { mutableIntStateOf(120) }"),
    ("var chordMode by remember { mutableStateOf(StepChordMode.OFF) }", "var chordMode by rememberSaveable { mutableStateOf(StepChordMode.OFF) }"),
    ("var pianoEntryMode by remember { mutableStateOf(PianoEntryMode.STEP) }", "var pianoEntryMode by rememberSaveable { mutableStateOf(PianoEntryMode.STEP) }"),
    ("var pianoOctaveShift by remember { mutableIntStateOf(0) }", "var pianoOctaveShift by rememberSaveable { mutableIntStateOf(0) }"),
    ("var staffSharpInput by remember { mutableStateOf(false) }", "var staffSharpInput by rememberSaveable { mutableStateOf(false) }"),
    ("var editorMode by remember { mutableStateOf(ScoreEditorMode.STAFF) }", "var editorMode by rememberSaveable { mutableStateOf(ScoreEditorMode.STAFF) }"),
    ("var showPianoKeyboard by remember { mutableStateOf(true) }", "var showPianoKeyboard by rememberSaveable { mutableStateOf(true) }"),
]:
    replace_once(composer, old, new)

replace_once(
    composer,
    "        staffSharpInput = snapshot.staffSharpInput\n        chordMode = StepChordMode.OFF\n        pianoEntryMode = PianoEntryMode.STEP\n        mixerGestureHistoryRecorded = false\n",
    "        staffSharpInput = snapshot.staffSharpInput\n        mixerGestureHistoryRecorded = false\n",
)
replace_once(
    composer,
    "        applyProjectSnapshot(snapshot, clearHistory = true)\n    }\n\n    fun newProject()",
    "        applyProjectSnapshot(snapshot, clearHistory = true)\n        chordMode = StepChordMode.OFF\n        pianoEntryMode = PianoEntryMode.STEP\n    }\n\n    fun newProject()",
)
replace_once(
    composer,
    "        ScoreTransportBus.seek(0f)\n    }\n\n    fun renameProject",
    "        chordMode = StepChordMode.OFF\n        pianoEntryMode = PianoEntryMode.STEP\n        ScoreTransportBus.seek(0f)\n    }\n\n    fun renameProject",
)
replace_once(
    composer,
    "        onDispose {\n            cancelNaturalEntryGroup()\n            cancelLiveRecording()\n",
    "        onDispose {\n            // Configuration changes recreate the Activity. Flush the latest score immediately so\n            // a rotation can never reload a draft that is up to 250 ms behind the visible editor.\n            ScoreProjectRepository.saveDraft(context, currentProjectSnapshot())\n            cancelNaturalEntryGroup()\n            cancelLiveRecording()\n",
)

keyboard = "app/src/main/java/com/scoreforge/app/ui/MultitouchPianoKeyboard.kt"
replace_once(
    keyboard,
    "import androidx.compose.runtime.remember\nimport androidx.compose.runtime.rememberUpdatedState\n",
    "import androidx.compose.runtime.remember\nimport androidx.compose.runtime.rememberUpdatedState\nimport androidx.compose.runtime.saveable.rememberSaveable\n",
)
replace_once(
    keyboard,
    "    var durationPaletteOpen by remember { mutableStateOf(false) }\n    var articulationPaletteOpen by remember { mutableStateOf(false) }\n",
    "    var durationPaletteOpen by rememberSaveable { mutableStateOf(false) }\n    var articulationPaletteOpen by rememberSaveable { mutableStateOf(false) }\n",
)

roll = "app/src/main/java/com/scoreforge/app/ui/PianoRollEditor.kt"
replace_once(
    roll,
    "import androidx.compose.runtime.remember\nimport androidx.compose.runtime.setValue\n",
    "import androidx.compose.runtime.remember\nimport androidx.compose.runtime.saveable.rememberSaveable\nimport androidx.compose.runtime.setValue\n",
)
replace_once(
    roll,
    "    var horizontalOffsetPx by remember { mutableFloatStateOf(0f) }\n    var verticalOffsetPx by remember { mutableFloatStateOf(0f) }\n",
    "    var horizontalOffsetPx by rememberSaveable { mutableFloatStateOf(0f) }\n    var verticalOffsetPx by rememberSaveable { mutableFloatStateOf(0f) }\n",
)

staff = "app/src/main/java/com/scoreforge/app/ui/ScoreStaffEditor.kt"
replace_once(
    staff,
    "    var zoom by remember { mutableFloatStateOf(1f) }\n",
    "    var zoom by rememberSaveable { mutableFloatStateOf(1f) }\n",
)

build = "app/build.gradle.kts"
replace_once(build, '        versionCode = 17\n        versionName = "0.2.14"', '        versionCode = 18\n        versionName = "0.2.15"')
