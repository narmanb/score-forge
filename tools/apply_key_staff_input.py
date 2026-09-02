from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path, old, new):
    p = ROOT / path
    text = p.read_text()
    if text.count(old) != 1:
        raise RuntimeError(f"Expected one match in {path}, got {text.count(old)}")
    p.write_text(text.replace(old, new, 1))


replace_once(
    "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt",
    '''                        onAddPitch = { naturalPitch, tappedBeat ->
                            val pitch = if (staffSharpInput) {
                                PitchNames.sharpenIfAvailable(naturalPitch)
                            } else {
                                naturalPitch
                            }
                            insertNoteAt(pitch, tappedBeat, preview = true, advanceCursor = false)
                        },''',
    '''                        onAddPitch = { naturalPitch, tappedBeat ->
                            val keyedPitch = ScoreKeySignatures.applyToNaturalPitch(
                                naturalPitch,
                                ScoreKeySignatures.atBeat(keySignatures, tappedBeat),
                            )
                            val pitch = if (staffSharpInput) {
                                PitchNames.sharpenIfAvailable(naturalPitch)
                            } else {
                                keyedPitch
                            }
                            insertNoteAt(pitch, tappedBeat, preview = true, advanceCursor = false)
                        },''',
)

replace_once(
    "app/src/main/java/com/scoreforge/app/ui/ScoreStaffEditor.kt",
    '''                                when (event) {
                                    is ScoreNote -> onMoveNote(
                                        draggingEventIndex,
                                        pitchFromY(
                                            change.position.y,
                                            geometry,
                                            preferSharp = PitchNames.hasSharp(event.midiPitch),
                                        ),
                                        movedBeat,
                                    )
                                    is ScoreRest -> onMoveRest(draggingEventIndex, movedBeat)
                                }''',
    '''                                when (event) {
                                    is ScoreNote -> {
                                        val naturalPitch = pitchFromY(
                                            change.position.y,
                                            geometry,
                                            preferSharp = false,
                                        )
                                        val movedKey = ScoreKeySignatures.atBeat(keySignatures, movedBeat)
                                        onMoveNote(
                                            draggingEventIndex,
                                            ScoreKeySignatures.applyToNaturalPitch(naturalPitch, movedKey),
                                            movedBeat,
                                        )
                                    }
                                    is ScoreRest -> onMoveRest(draggingEventIndex, movedBeat)
                                }''',
)

print("Applied key-aware staff entry and dragging")
