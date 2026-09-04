from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COMPOSER = ROOT / "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt"
STAFF = ROOT / "app/src/main/java/com/scoreforge/app/ui/ScoreStaffEditor.kt"
GRADLE = ROOT / "app/build.gradle.kts"


def replace_once(path: Path, old: str, new: str):
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected exactly one match in {path}, found {count}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1))


def replace_count(path: Path, old: str, new: str, expected: int):
    text = path.read_text()
    count = text.count(old)
    if count != expected:
        raise RuntimeError(f"Expected {expected} matches in {path}, found {count}: {old[:120]!r}")
    path.write_text(text.replace(old, new))


replace_once(
    COMPOSER,
    "import com.scoreforge.app.music.LiveEntryTiming\n",
    "import com.scoreforge.app.music.ComfortTempo\nimport com.scoreforge.app.music.LiveEntryTiming\n",
)

replace_once(
    COMPOSER,
    '''    var bpm by rememberSaveable { mutableIntStateOf(120) }\n    var timeSignatures by remember { mutableStateOf(listOf(ScoreTimeSignatures.DEFAULT)) }''',
    '''    var bpm by rememberSaveable { mutableIntStateOf(120) }\n    var comfortTempoCapturing by rememberSaveable { mutableStateOf(false) }\n    var comfortTempoAttackTimes by remember { mutableStateOf(emptyList<Long>()) }\n    var comfortTempoEstimate by rememberSaveable { mutableStateOf<Int?>(null) }\n    var timeSignatures by remember { mutableStateOf(listOf(ScoreTimeSignatures.DEFAULT)) }''',
)

replace_once(
    COMPOSER,
    '''        keySignatures = snapshot.effectiveKeySignatures()\n        metronomeEnabled = snapshot.metronomeEnabled\n        mixerGestureHistoryRecorded = false''',
    '''        keySignatures = snapshot.effectiveKeySignatures()\n        metronomeEnabled = snapshot.metronomeEnabled\n        comfortTempoCapturing = false\n        comfortTempoAttackTimes = emptyList()\n        comfortTempoEstimate = null\n        mixerGestureHistoryRecorded = false''',
)

replace_once(
    COMPOSER,
    '''    fun stopPlayback() {\n        playback.stop()\n        isPlaying = false\n    }\n\n    fun openProject(snapshot: ScoreProjectSnapshot) {''',
    '''    fun stopPlayback() {\n        playback.stop()\n        isPlaying = false\n    }\n\n    fun startComfortTempoMeasurement() {\n        stopPlayback()\n        stopLiveRecording()\n        cancelNaturalEntryGroup()\n        LiveInstrumentBus.allNotesOff()\n        comfortTempoAttackTimes = emptyList()\n        comfortTempoEstimate = null\n        comfortTempoCapturing = true\n        showPianoKeyboard = true\n    }\n\n    fun cancelComfortTempoMeasurement() {\n        comfortTempoCapturing = false\n        comfortTempoAttackTimes = emptyList()\n        comfortTempoEstimate = null\n        LiveInstrumentBus.allNotesOff()\n    }\n\n    fun recordComfortTempoAttack(pitch: Int) {\n        if (!comfortTempoCapturing) return\n        val updated = ComfortTempo.addAttack(\n            comfortTempoAttackTimes,\n            SystemClock.elapsedRealtime(),\n        )\n        if (updated.size == comfortTempoAttackTimes.size) return\n        comfortTempoAttackTimes = updated\n        playback.previewPitch(pitch)\n        if (updated.size >= ComfortTempo.REQUIRED_ATTACKS) {\n            comfortTempoEstimate = ComfortTempo.estimateBpm(updated)\n            comfortTempoCapturing = false\n        }\n    }\n\n    fun applyComfortTempoEstimate() {\n        val estimate = comfortTempoEstimate ?: return\n        bpm = estimate.coerceIn(ComfortTempo.MIN_BPM, ComfortTempo.MAX_BPM)\n        comfortTempoEstimate = null\n        comfortTempoAttackTimes = emptyList()\n    }\n\n    fun openProject(snapshot: ScoreProjectSnapshot) {''',
)

replace_count(
    COMPOSER,
    "canPlay = playableNoteCount > 0 && !liveRecordingActive,",
    "canPlay = playableNoteCount > 0 && !liveRecordingActive && !comfortTempoCapturing,",
    2,
)

replace_once(
    COMPOSER,
    '''                    onPlay = ::startPlayback,\n                    onStop = ::stopPlayback,\n                )\n\n                ProjectFileControls(''',
    '''                    onPlay = ::startPlayback,\n                    onStop = ::stopPlayback,\n                )\n\n                ComfortTempoControls(\n                    capturing = comfortTempoCapturing,\n                    attackCount = comfortTempoAttackTimes.size,\n                    estimatedBpm = comfortTempoEstimate,\n                    onStart = ::startComfortTempoMeasurement,\n                    onCancel = ::cancelComfortTempoMeasurement,\n                    onApply = ::applyComfortTempoEstimate,\n                    onTryAgain = ::startComfortTempoMeasurement,\n                )\n\n                ProjectFileControls(''',
)

replace_once(
    COMPOSER,
    '''                        onPitchDown = { pitch ->\n                            when (pianoEntryMode) {\n                                PianoEntryMode.STEP -> {\n                                    insertStepNote(pitch, preview = false)\n                                    if (!LiveInstrumentBus.noteOn(pitch, velocity = 96)) {\n                                        playback.previewPitch(pitch)\n                                    }\n                                }\n                                PianoEntryMode.NATURAL -> beginNaturalPitch(pitch)\n                                PianoEntryMode.LIVE -> beginLivePitch(pitch)\n                            }\n                        },''',
    '''                        onPitchDown = { pitch ->\n                            if (comfortTempoCapturing) {\n                                recordComfortTempoAttack(pitch)\n                            } else {\n                                when (pianoEntryMode) {\n                                    PianoEntryMode.STEP -> {\n                                        insertStepNote(pitch, preview = false)\n                                        if (!LiveInstrumentBus.noteOn(pitch, velocity = 96)) {\n                                            playback.previewPitch(pitch)\n                                        }\n                                    }\n                                    PianoEntryMode.NATURAL -> beginNaturalPitch(pitch)\n                                    PianoEntryMode.LIVE -> beginLivePitch(pitch)\n                                }\n                            }\n                        },''',
)

replace_once(
    STAFF,
    '''            val viewportWidth = maxWidth\n            val baseBeatWidth = maxOf(\n                18.dp,\n                (viewportWidth - NOTATION_HEADER_WIDTH - TIMELINE_RIGHT_PADDING) /\n                    StaffTimelineLayout.DEFAULT_VISIBLE_BEATS,\n            )''',
    '''            val viewportWidth = maxWidth\n            val responsiveMinimumBeatWidth =\n                StaffResponsiveLayout.minimumBeatWidthDp(viewportWidth.value).dp\n            val baseBeatWidth = maxOf(\n                responsiveMinimumBeatWidth,\n                (viewportWidth - NOTATION_HEADER_WIDTH - TIMELINE_RIGHT_PADDING) /\n                    StaffTimelineLayout.DEFAULT_VISIBLE_BEATS,\n            )''',
)

replace_once(GRADLE, 'versionCode = 22', 'versionCode = 23')
replace_once(GRADLE, 'versionName = "0.2.19"', 'versionName = "0.2.20"')

print("Applied Comfort Tempo and portrait staff patch")
