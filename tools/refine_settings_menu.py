from pathlib import Path

ROOT = Path('.')
UI = ROOT / 'app/src/main/java/com/scoreforge/app/ui'

# Give editor modes user-facing labels for Settings.
path = UI / 'EditorModeControls.kt'
text = path.read_text()
old = '''enum class ScoreEditorMode {
    STAFF,
    PIANO_ROLL,
}'''
new = '''enum class ScoreEditorMode(val displayName: String) {
    STAFF("Staff"),
    PIANO_ROLL("Piano Roll"),
}'''
if old not in text:
    raise SystemExit('ScoreEditorMode enum block not found')
path.write_text(text.replace(old, new, 1))

# Do not overwrite the separately remembered octave whenever an unrelated setting is saved.
path = UI / 'ScoreForgeSettings.kt'
text = path.read_text()
needle = '            .putInt("keyboard_octave", settings.rememberedKeyboardOctave.coerceIn(-4, 3))\n'
if needle not in text:
    raise SystemExit('keyboard octave save line not found')
path.write_text(text.replace(needle, '', 1))

# Make "All Notes" actually include black-key labels.
path = UI / 'MultitouchPianoKeyboard.kt'
text = path.read_text()
old = '''                Box(
                    modifier = Modifier
                        .offset(
                            x = whiteKeyWidth *
                                (key.whiteIndex + PianoTouchLayout.BLACK_KEY_X_OFFSET_FRACTION)
                        )
                        .width(whiteKeyWidth * PianoTouchLayout.BLACK_KEY_WIDTH_FRACTION)
                        .fillMaxHeight(PianoTouchLayout.BLACK_KEY_HEIGHT_FRACTION)
                        .zIndex(2f)
                        .background(
                            if (active) Color(0xFF536A91) else Color(0xFF151515),
                            RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp),
                        ),
                )
'''
new = '''                Box(
                    modifier = Modifier
                        .offset(
                            x = whiteKeyWidth *
                                (key.whiteIndex + PianoTouchLayout.BLACK_KEY_X_OFFSET_FRACTION)
                        )
                        .width(whiteKeyWidth * PianoTouchLayout.BLACK_KEY_WIDTH_FRACTION)
                        .fillMaxHeight(PianoTouchLayout.BLACK_KEY_HEIGHT_FRACTION)
                        .zIndex(2f)
                        .background(
                            if (active) Color(0xFF536A91) else Color(0xFF151515),
                            RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp),
                        ),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (noteLabelSetting == KeyboardNoteLabelSetting.ALL) {
                        Text(
                            PitchNames.name(pitch),
                            modifier = Modifier.padding(bottom = 3.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
'''
if old not in text:
    raise SystemExit('black key box block not found')
path.write_text(text.replace(old, new, 1))

# Honor defaults on New/Open Project and make Autosave/Recovery truly disable exit-time saving.
path = UI / 'ComposerScreen.kt'
text = path.read_text()
old = '''    DisposableEffect(Unit) {
        onDispose {
            ScoreProjectRepository.saveDraft(context, currentProjectSnapshot())
            cancelNaturalEntryGroup()
'''
new = '''    DisposableEffect(Unit) {
        onDispose {
            if (ScoreForgeSettingsRepository.load(context).autosaveRecovery) {
                ScoreProjectRepository.saveDraft(context, currentProjectSnapshot())
            }
            cancelNaturalEntryGroup()
'''
if old not in text:
    raise SystemExit('DisposableEffect draft save block not found')
text = text.replace(old, new, 1)

old = '''        chordMode = StepChordMode.OFF
        pianoEntryMode = PianoEntryMode.STEP
    }

    fun newProject()'''
new = '''        chordMode = StepChordMode.OFF
        pianoEntryMode = appSettings.defaultEntryMode
        editorMode = appSettings.defaultEditorMode
    }

    fun newProject()'''
if old not in text:
    raise SystemExit('openProject default block not found')
text = text.replace(old, new, 1)

old = '''        val blankTrack = ScoreTracks.defaultTrack().copy(
            presetBank = preset?.bank,
            presetProgram = preset?.program,
        )'''
new = '''        val blankTrack = ScoreTracks.defaultTrack().copy(
            presetBank = preset?.bank,
            presetProgram = preset?.program,
            clefMode = appSettings.defaultClefMode,
        )'''
if old not in text:
    raise SystemExit('newProject blank track block not found')
text = text.replace(old, new, 1)

old = '''                pianoOctaveShift = 0,
                staffSharpInput = false,'''
new = '''                pianoOctaveShift = if (appSettings.rememberKeyboardOctave) pianoOctaveShift else 0,
                staffSharpInput = false,'''
if old not in text:
    raise SystemExit('newProject octave block not found')
text = text.replace(old, new, 1)

old = '''        chordMode = StepChordMode.OFF
        pianoEntryMode = PianoEntryMode.STEP
        ScoreTransportBus.seek(0f)
'''
new = '''        chordMode = StepChordMode.OFF
        pianoEntryMode = appSettings.defaultEntryMode
        editorMode = appSettings.defaultEditorMode
        ScoreTransportBus.seek(0f)
'''
if old not in text:
    raise SystemExit('newProject mode block not found')
text = text.replace(old, new, 1)
path.write_text(text)
