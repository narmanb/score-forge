from pathlib import Path


def replace_once(path_str: str, old: str, new: str) -> None:
    path = Path(path_str)
    text = path.read_text()
    if old not in text:
        raise RuntimeError(f"Pattern not found in {path}: {old[:180]!r}")
    path.write_text(text.replace(old, new, 1))


# --- Multitouch piano: three-state chord mode + auto-advance gate ---
p = "app/src/main/java/com/scoreforge/app/ui/MultitouchPianoKeyboard.kt"
replace_once(
    p,
    "import com.scoreforge.app.music.PitchNames\n\n@Composable\nfun MultitouchPianoKeyboard(\n    chordMode: Boolean,",
    "import com.scoreforge.app.music.PitchNames\n\ninternal enum class StepChordMode {\n    OFF,\n    MANUAL,\n    AUTO;\n\n    val holdsStepCursor: Boolean\n        get() = this != OFF\n\n    fun next(): StepChordMode = when (this) {\n        OFF -> MANUAL\n        MANUAL -> AUTO\n        AUTO -> OFF\n    }\n}\n\ninternal object AutoChordAdvancePolicy {\n    fun shouldAdvance(\n        mode: StepChordMode,\n        hadPressedPointers: Boolean,\n        hasPressedPointers: Boolean,\n        chordHadNote: Boolean,\n    ): Boolean =\n        mode == StepChordMode.AUTO &&\n            hadPressedPointers &&\n            !hasPressedPointers &&\n            chordHadNote\n}\n\n@Composable\nfun MultitouchPianoKeyboard(\n    chordMode: StepChordMode,"
)
replace_once(
    p,
    "    onToggleChordMode: () -> Unit,\n    onAdvanceChord: () -> Unit,",
    "    onCycleChordMode: () -> Unit,\n    onAdvanceChord: () -> Unit,"
)
replace_once(
    p,
    "                .pointerInput(chordMode, entryMode) {\n                    val pointerPitches = mutableMapOf<PointerId, Int>()\n\n                    try {\n                        awaitPointerEventScope {\n                            while (true) {\n                                val event = awaitPointerEvent()\n                                event.changes.forEach { change ->",
    "                .pointerInput(chordMode, entryMode) {\n                    val pointerPitches = mutableMapOf<PointerId, Int>()\n                    var hadPressedPointers = false\n                    var autoChordHadNote = false\n\n                    try {\n                        awaitPointerEventScope {\n                            while (true) {\n                                val event = awaitPointerEvent()\n                                event.changes.forEach { change ->"
)
replace_once(
    p,
    "                                        if (newPitch != null) {\n                                            pointerPitches[change.id] = newPitch\n                                            activatePitch(newPitch)\n                                            change.consume()\n                                        }",
    "                                        if (newPitch != null) {\n                                            pointerPitches[change.id] = newPitch\n                                            activatePitch(newPitch)\n                                            if (entryMode == PianoEntryMode.STEP && chordMode == StepChordMode.AUTO) {\n                                                autoChordHadNote = true\n                                            }\n                                            change.consume()\n                                        }"
)
replace_once(
    p,
    "                                    } else if (change.previousPressed && !change.pressed) {\n                                        pointerPitches.remove(change.id)?.let(::deactivatePitch)\n                                        change.consume()\n                                    }\n                                }\n                            }",
    "                                    } else if (change.previousPressed && !change.pressed) {\n                                        pointerPitches.remove(change.id)?.let(::deactivatePitch)\n                                        change.consume()\n                                    }\n                                }\n\n                                val hasPressedPointers = event.changes.any { it.pressed }\n                                if (\n                                    entryMode == PianoEntryMode.STEP &&\n                                    AutoChordAdvancePolicy.shouldAdvance(\n                                        mode = chordMode,\n                                        hadPressedPointers = hadPressedPointers,\n                                        hasPressedPointers = hasPressedPointers,\n                                        chordHadNote = autoChordHadNote,\n                                    )\n                                ) {\n                                    onAdvanceChord()\n                                    autoChordHadNote = false\n                                }\n                                hadPressedPointers = hasPressedPointers\n                            }"
)
replace_once(
    p,
    "                    ChamferedControlButton(\n                        label = if (chordMode) \"Chord On\" else \"Chord Off\",\n                        onClick = {\n                            releaseAllPitches()\n                            onToggleChordMode()\n                        },\n                        selected = chordMode,\n                    )\n                    ChamferedControlButton(\n                        label = \"Next Chord\",\n                        onClick = {\n                            releaseAllPitches()\n                            onAdvanceChord()\n                        },\n                        enabled = chordMode,\n                    )",
    "                    ChamferedControlButton(\n                        label = when (chordMode) {\n                            StepChordMode.OFF -> \"Chord Off\"\n                            StepChordMode.MANUAL -> \"Chord Manual\"\n                            StepChordMode.AUTO -> \"Chord Auto\"\n                        },\n                        onClick = {\n                            releaseAllPitches()\n                            onCycleChordMode()\n                        },\n                        selected = chordMode != StepChordMode.OFF,\n                    )\n                    ChamferedControlButton(\n                        label = \"Next Chord\",\n                        onClick = {\n                            releaseAllPitches()\n                            onAdvanceChord()\n                        },\n                        enabled = chordMode != StepChordMode.OFF,\n                    )"
)

# --- Composer: shared transport action + chord state semantics ---
p = "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt"
replace_once(
    p,
    "    var chordMode by remember { mutableStateOf(false) }",
    "    var chordMode by remember { mutableStateOf(StepChordMode.OFF) }"
)
replace_once(p, "        chordMode = false\n        pianoEntryMode = PianoEntryMode.STEP", "        chordMode = StepChordMode.OFF\n        pianoEntryMode = PianoEntryMode.STEP")
replace_once(
    p,
    "    fun stopPlayback() {\n        playback.stop()\n        isPlaying = false\n    }",
    "    fun startPlayback() {\n        if (playableNoteCount <= 0 || liveRecordingActive || isPlaying) return\n        isPlaying = true\n        playback.playTracks(\n            tracks = tracks,\n            bpm = bpm,\n            throughBeat = ScoreTracks.endBeat(tracks),\n        ) { isPlaying = false }\n    }\n\n    fun stopPlayback() {\n        playback.stop()\n        isPlaying = false\n    }"
)
replace_once(
    p,
    "            advanceCursor && chordMode -> track.cursorBeat",
    "            advanceCursor && chordMode.holdsStepCursor -> track.cursorBeat"
)
# Three cursor-preservation sites: delete, note move, rest move.
text = Path(p).read_text()
count = text.count("cursorBeat = if (chordMode) it.cursorBeat else ScoreTimeline.endBeat(updatedEvents)")
if count != 3:
    raise RuntimeError(f"Expected 3 chord cursor preservation sites, found {count}")
text = text.replace(
    "cursorBeat = if (chordMode) it.cursorBeat else ScoreTimeline.endBeat(updatedEvents)",
    "cursorBeat = if (chordMode.holdsStepCursor) it.cursorBeat else ScoreTimeline.endBeat(updatedEvents)",
)
Path(p).write_text(text)
replace_once(
    p,
    "                    onPlay = {\n                        if (playableNoteCount > 0 && !liveRecordingActive) {\n                            isPlaying = true\n                            playback.playTracks(\n                                tracks = tracks,\n                                bpm = bpm,\n                                throughBeat = ScoreTracks.endBeat(tracks),\n                            ) { isPlaying = false }\n                        }\n                    },\n                    onStop = ::stopPlayback,",
    "                    onPlay = ::startPlayback,\n                    onStop = ::stopPlayback,"
)
replace_once(
    p,
    "                        selectedEventIndex = selectedEventIndex,\n                        onAddPitch = { naturalPitch, tappedBeat ->",
    "                        selectedEventIndex = selectedEventIndex,\n                        isPlaying = isPlaying,\n                        canPlay = playableNoteCount > 0 && !liveRecordingActive,\n                        onPlay = ::startPlayback,\n                        onStop = ::stopPlayback,\n                        onAddPitch = { naturalPitch, tappedBeat ->"
)
replace_once(
    p,
    "                            chordMode = false\n                            if (mode == PianoEntryMode.LIVE) stopPlayback()",
    "                            chordMode = StepChordMode.OFF\n                            if (mode == PianoEntryMode.LIVE) stopPlayback()"
)
replace_once(
    p,
    "                        onToggleChordMode = {\n                            LiveInstrumentBus.allNotesOff()\n                            val track = currentTrack()\n                            if (chordMode) {\n                                chordMode = false\n                                replaceActiveTrack {\n                                    it.copy(cursorBeat = maxOf(it.cursorBeat, ScoreTimeline.endBeat(it.events)))\n                                }\n                            } else {\n                                chordMode = true\n                                replaceActiveTrack { it.copy(cursorBeat = ScoreTimeline.endBeat(track.events)) }\n                            }\n                        },",
    "                        onCycleChordMode = {\n                            LiveInstrumentBus.allNotesOff()\n                            val previous = chordMode\n                            val next = previous.next()\n                            chordMode = next\n                            when {\n                                next == StepChordMode.OFF -> replaceActiveTrack {\n                                    it.copy(cursorBeat = maxOf(it.cursorBeat, ScoreTimeline.endBeat(it.events)))\n                                }\n                                previous == StepChordMode.OFF -> {\n                                    val track = currentTrack()\n                                    replaceActiveTrack { it.copy(cursorBeat = ScoreTimeline.endBeat(track.events)) }\n                                }\n                            }\n                        },"
)

# --- Staff: local transport and separate edit cursor ---
p = "app/src/main/java/com/scoreforge/app/ui/ScoreStaffEditor.kt"
replace_once(
    p,
    "    cursorBeat: Float,\n    selectedEventIndex: Int,\n    onAddPitch: (pitch: Int, startBeat: Float) -> Unit,",
    "    cursorBeat: Float,\n    selectedEventIndex: Int,\n    isPlaying: Boolean = false,\n    canPlay: Boolean = false,\n    onPlay: () -> Unit = {},\n    onStop: () -> Unit = {},\n    onAddPitch: (pitch: Int, startBeat: Float) -> Unit,"
)
replace_once(
    p,
    "                    val playheadX = StaffTimelineLayout.xAtBeat(\n                        transport.beat.coerceIn(0f, contentBeats),\n                        timelineLeftPx,\n                        beatWidthPx,\n                    )\n                    drawLine(\n                        Color(0xFF6A52A3),\n                        Offset(playheadX, geometry.rulerY - geometry.lineSpacing * 0.18f),\n                        Offset(playheadX, geometry.staffBottom + geometry.lineSpacing * 1.35f),\n                        3f,\n                    )",
    "                    val entryCursorX = StaffTimelineLayout.xAtBeat(\n                        cursorBeat.coerceIn(0f, contentBeats),\n                        timelineLeftPx,\n                        beatWidthPx,\n                    )\n                    drawLine(\n                        Color(0xFFB34747),\n                        Offset(entryCursorX, geometry.rulerY + geometry.lineSpacing * 0.22f),\n                        Offset(entryCursorX, geometry.staffBottom + geometry.lineSpacing * 0.72f),\n                        1.5f,\n                    )\n                    drawCircle(\n                        Color(0xFFB34747),\n                        radius = 4f,\n                        center = Offset(entryCursorX, geometry.rulerY + geometry.lineSpacing * 0.12f),\n                    )\n\n                    val playheadX = StaffTimelineLayout.xAtBeat(\n                        transport.beat.coerceIn(0f, contentBeats),\n                        timelineLeftPx,\n                        beatWidthPx,\n                    )\n                    drawLine(\n                        Color(0xFF6A52A3),\n                        Offset(playheadX, geometry.rulerY - geometry.lineSpacing * 0.18f),\n                        Offset(playheadX, geometry.staffBottom + geometry.lineSpacing * 1.35f),\n                        3f,\n                    )"
)
replace_once(
    p,
    "            ) {\n                if (staffInputEnabled) {",
    "            ) {\n                if (isPlaying) {\n                    Button(\n                        onClick = onStop,\n                        modifier = Modifier.height(28.dp),\n                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),\n                    ) { Text(\"■\", style = MaterialTheme.typography.labelSmall) }\n                } else {\n                    OutlinedButton(\n                        onClick = onPlay,\n                        enabled = canPlay,\n                        modifier = Modifier.height(28.dp),\n                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),\n                        colors = ButtonDefaults.outlinedButtonColors(\n                            contentColor = Color(0xFF222222)\n                        ),\n                    ) { Text(\"▶\", style = MaterialTheme.typography.labelSmall) }\n                }\n\n                if (staffInputEnabled) {"
)

# --- Version ---
p = "app/build.gradle.kts"
replace_once(
    p,
    '        versionCode = 16\n        versionName = "0.2.13"',
    '        versionCode = 17\n        versionName = "0.2.14"'
)

# --- Unit tests for the chord state machine and auto-release policy ---
test_path = Path("app/src/test/java/com/scoreforge/app/ui/StepChordModeTest.kt")
test_path.write_text('''package com.scoreforge.app.ui\n\nimport org.junit.Assert.assertFalse\nimport org.junit.Assert.assertTrue\nimport org.junit.Assert.assertEquals\nimport org.junit.Test\n\nclass StepChordModeTest {\n    @Test\n    fun chordModeCyclesOffManualAutoOff() {\n        assertEquals(StepChordMode.MANUAL, StepChordMode.OFF.next())\n        assertEquals(StepChordMode.AUTO, StepChordMode.MANUAL.next())\n        assertEquals(StepChordMode.OFF, StepChordMode.AUTO.next())\n    }\n\n    @Test\n    fun onlyActiveChordModesHoldTheStepCursor() {\n        assertFalse(StepChordMode.OFF.holdsStepCursor)\n        assertTrue(StepChordMode.MANUAL.holdsStepCursor)\n        assertTrue(StepChordMode.AUTO.holdsStepCursor)\n    }\n\n    @Test\n    fun autoAdvanceRequiresFinalReleaseAfterAPlayedNote() {\n        assertTrue(\n            AutoChordAdvancePolicy.shouldAdvance(\n                mode = StepChordMode.AUTO,\n                hadPressedPointers = true,\n                hasPressedPointers = false,\n                chordHadNote = true,\n            )\n        )\n        assertFalse(\n            AutoChordAdvancePolicy.shouldAdvance(\n                mode = StepChordMode.MANUAL,\n                hadPressedPointers = true,\n                hasPressedPointers = false,\n                chordHadNote = true,\n            )\n        )\n        assertFalse(\n            AutoChordAdvancePolicy.shouldAdvance(\n                mode = StepChordMode.AUTO,\n                hadPressedPointers = true,\n                hasPressedPointers = true,\n                chordHadNote = true,\n            )\n        )\n        assertFalse(\n            AutoChordAdvancePolicy.shouldAdvance(\n                mode = StepChordMode.AUTO,\n                hadPressedPointers = true,\n                hasPressedPointers = false,\n                chordHadNote = false,\n            )\n        )\n    }\n}\n''')

print("Chord auto + local transport patch applied")
