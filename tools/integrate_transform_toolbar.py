from pathlib import Path

path = Path("app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt")
text = path.read_text()

MARKER = "ComposerTransformToolbar("
if MARKER in text:
    print("Composer toolbar already integrated; nothing to do.")
    raise SystemExit(0)


def remove_call(source: str, name: str) -> str:
    needle = f"\n                {name}("
    start = source.find(needle)
    if start < 0:
        raise RuntimeError(f"Could not find {name} invocation")
    open_paren = source.find("(", start + 1)
    depth = 0
    in_string = False
    escaped = False
    i = open_paren
    while i < len(source):
        ch = source[i]
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
        else:
            if ch == '"':
                in_string = True
            elif ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    end = i + 1
                    # Consume one following newline so removed rows do not leave large gaps.
                    if end < len(source) and source[end] == "\n":
                        end += 1
                    return source[:start] + source[end:]
        i += 1
    raise RuntimeError(f"Unbalanced invocation for {name}")


def find_call_end(source: str, name: str) -> int:
    needle = f"\n                {name}("
    start = source.find(needle)
    if start < 0:
        raise RuntimeError(f"Could not find {name} invocation")
    open_paren = source.find("(", start + 1)
    depth = 0
    in_string = False
    escaped = False
    i = open_paren
    while i < len(source):
        ch = source[i]
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
        else:
            if ch == '"':
                in_string = True
            elif ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    return i + 1
        i += 1
    raise RuntimeError(f"Unbalanced invocation for {name}")


# These formerly occupied seven persistent rows above the editor.
for call_name in (
    "ComfortTempoControls",
    "TempoControls",
    "TimeSignatureControls",
    "KeySignatureControls",
    "DurationSelector",
    "EditorModeControls",
    "ClefControls",
):
    text = remove_call(text, call_name)

# Put the transforming row immediately after the per-track instrument controls and before the editor.
insert_at = find_call_end(text, "SoundFontControls")
toolbar = r'''

                ComposerTransformToolbar(
                    tempoChanges = tempoChanges,
                    timeSignatures = timeSignatures,
                    keySignatures = keySignatures,
                    cursorBeat = activeCursorBeat,
                    clefMode = activeTrack.clefMode,
                    effectiveClef = ScoreClefs.effective(activeTrack.clefMode, activeEvents),
                    selectedDuration = selectedDuration,
                    dotted = selectedDotted,
                    sharpInput = staffSharpInput,
                    tieEnabled = canTieSelected,
                    tieActive = selectedTieActive,
                    editorMode = editorMode,
                    showPianoKeyboard = showPianoKeyboard,
                    measureNumber = ScoreTimeline.measureCount(
                        emptyList(),
                        activeCursorBeat,
                        timeSignatures = timeSignatures,
                    ).coerceAtLeast(1),
                    comfortTempoCapturing = comfortTempoCapturing,
                    comfortTempoAttackCount = comfortTempoAttackTimes.size,
                    comfortTempoEstimate = comfortTempoEstimate,
                    onSetTempo = ::setTempoChange,
                    onRemoveTempo = ::removeTempoChange,
                    onStartComfortTempo = ::startComfortTempoMeasurement,
                    onCancelComfortTempo = ::cancelComfortTempoMeasurement,
                    onApplyComfortTempo = ::applyComfortTempoEstimate,
                    onTryComfortTempoAgain = ::startComfortTempoMeasurement,
                    onSetTimeSignature = { startBeat, numerator, denominator ->
                        timeSignatures = ScoreTimeSignatures.withChange(
                            timeSignatures,
                            startBeat,
                            numerator,
                            denominator,
                        )
                    },
                    onRemoveTimeSignature = { startBeat ->
                        timeSignatures = ScoreTimeSignatures.withoutChange(timeSignatures, startBeat)
                    },
                    onSetKeySignature = { startBeat, fifths, minor ->
                        keySignatures = ScoreKeySignatures.withChange(
                            keySignatures,
                            startBeat,
                            fifths,
                            minor,
                        )
                    },
                    onRemoveKeySignature = { startBeat ->
                        keySignatures = ScoreKeySignatures.withoutChange(keySignatures, startBeat)
                    },
                    onClefModeChanged = ::setActiveTrackClefMode,
                    onDurationSelected = { selectedDuration = it },
                    onToggleDotted = { selectedDotted = !selectedDotted },
                    onInsertRest = ::insertRest,
                    onToggleSharpInput = { staffSharpInput = !staffSharpInput },
                    onToggleTie = ::toggleSelectedTie,
                    onEditorModeChanged = { editorMode = it },
                    onTogglePianoKeyboard = {
                        stopLiveRecording()
                        cancelNaturalEntryGroup()
                        LiveInstrumentBus.allNotesOff()
                        showPianoKeyboard = !showPianoKeyboard
                    },
                )'''
text = text[:insert_at] + toolbar + text[insert_at:]

path.write_text(text)
print("Integrated ComposerTransformToolbar into ComposerScreen.kt")
