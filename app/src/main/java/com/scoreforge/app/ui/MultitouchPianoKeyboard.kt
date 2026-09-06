package com.scoreforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.scoreforge.app.music.HoldDurationMode
import com.scoreforge.app.music.NoteArticulation
import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.PitchNames

enum class StepChordMode {
    OFF,
    MANUAL,
    AUTO;

    val holdsStepCursor: Boolean
        get() = this != OFF

    fun next(): StepChordMode = when (this) {
        OFF -> MANUAL
        MANUAL -> AUTO
        AUTO -> OFF
    }
}

internal object AutoChordAdvancePolicy {
    fun shouldAdvance(
        mode: StepChordMode,
        hadPressedPointers: Boolean,
        hasPressedPointers: Boolean,
        chordHadNote: Boolean,
    ): Boolean =
        mode == StepChordMode.AUTO &&
            hadPressedPointers &&
            !hasPressedPointers &&
            chordHadNote
}

@Composable
fun MultitouchPianoKeyboard(
    chordMode: StepChordMode,
    octaveShift: Int,
    entryMode: PianoEntryMode,
    holdDurationMode: HoldDurationMode,
    liveRecordingActive: Boolean,
    holdPreviewDuration: NoteDuration?,
    holdPreviewDotted: Boolean,
    selectedDuration: NoteDuration,
    durationOrder: NoteDurationOrderSetting = NoteDurationOrderSetting.LONG_TO_SHORT,
    noteLabelSetting: KeyboardNoteLabelSetting = KeyboardNoteLabelSetting.C_ONLY,
    selectedDotted: Boolean,
    selectedArticulation: NoteArticulation,
    tieEnabled: Boolean,
    tieActive: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDurationSelected: (NoteDuration) -> Unit,
    onToggleDotted: () -> Unit,
    onArticulationSelected: (NoteArticulation) -> Unit,
    onToggleTie: () -> Unit,
    onEntryModeChanged: (PianoEntryMode) -> Unit,
    onHoldDurationModeChanged: (HoldDurationMode) -> Unit,
    onStopLive: () -> Unit,
    onCycleChordMode: () -> Unit,
    onAdvanceChord: () -> Unit,
    onInsertRest: () -> Unit,
    onOctaveDown: () -> Unit,
    onOctaveUp: () -> Unit,
    onPitchDown: (Int) -> Unit,
    onPitchUp: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activePitchCounts = remember { mutableStateMapOf<Int, Int>() }
    val safeOctaveShift = octaveShift.coerceIn(-4, 3)
    val currentOctaveShift by rememberUpdatedState(safeOctaveShift)
    var durationPaletteOpen by rememberSaveable { mutableStateOf(false) }
    var articulationPaletteOpen by rememberSaveable { mutableStateOf(false) }

    fun visualPitch(layoutPitch: Int): Int =
        PianoTouchLayout.shiftedPitch(layoutPitch, safeOctaveShift)

    fun livePitch(layoutPitch: Int): Int =
        PianoTouchLayout.shiftedPitch(layoutPitch, currentOctaveShift)

    fun activatePitch(pitch: Int) {
        val previous = activePitchCounts[pitch] ?: 0
        activePitchCounts[pitch] = previous + 1
        if (previous == 0) onPitchDown(pitch)
    }

    fun deactivatePitch(pitch: Int) {
        val previous = activePitchCounts[pitch] ?: return
        if (previous <= 1) {
            activePitchCounts.remove(pitch)
            onPitchUp(pitch)
        } else {
            activePitchCounts[pitch] = previous - 1
        }
    }

    fun releaseAllPitches() {
        activePitchCounts.keys.toList().forEach { pitch ->
            activePitchCounts.remove(pitch)
            onPitchUp(pitch)
        }
    }

    LaunchedEffect(safeOctaveShift) {
        releaseAllPitches()
    }

    Column(
        modifier = Modifier
            .height(170.dp)
            .then(modifier)
            .background(ComposerControlStripColor)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 1.dp)
                .pointerInput(chordMode, entryMode) {
                    val pointerPitches = mutableMapOf<PointerId, Int>()
                    var hadPressedPointers = false
                    var autoChordHadNote = false

                    try {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    val oldPitch = pointerPitches[change.id]

                                    if (!change.previousPressed && change.pressed) {
                                        val newPitch = PianoTouchLayout.pitchAt(
                                            x = change.position.x,
                                            y = change.position.y,
                                            width = size.width.toFloat(),
                                            height = size.height.toFloat(),
                                        )?.let(::livePitch)
                                        if (newPitch != null) {
                                            pointerPitches[change.id] = newPitch
                                            activatePitch(newPitch)
                                            if (entryMode == PianoEntryMode.STEP && chordMode == StepChordMode.AUTO) {
                                                autoChordHadNote = true
                                            }
                                            change.consume()
                                        }
                                    } else if (change.previousPressed && change.pressed) {
                                        if (entryMode == PianoEntryMode.STEP) {
                                            val newPitch = PianoTouchLayout.pitchAt(
                                                x = change.position.x,
                                                y = change.position.y,
                                                width = size.width.toFloat(),
                                                height = size.height.toFloat(),
                                            )?.let(::livePitch)
                                            if (newPitch != oldPitch) {
                                                if (oldPitch != null) deactivatePitch(oldPitch)
                                                if (newPitch != null) {
                                                    pointerPitches[change.id] = newPitch
                                                    activatePitch(newPitch)
                                                } else {
                                                    pointerPitches.remove(change.id)
                                                }
                                                change.consume()
                                            }
                                        } else if (oldPitch != null) {
                                            // Natural, Hold, and Live modes lock one finger to the key it first pressed.
                                            // Small finger drift must never reset the hold timer or change pitch.
                                            change.consume()
                                        }
                                    } else if (change.previousPressed && !change.pressed) {
                                        pointerPitches.remove(change.id)?.let(::deactivatePitch)
                                        change.consume()
                                    }
                                }

                                val hasPressedPointers = event.changes.any { it.pressed }
                                if (
                                    entryMode == PianoEntryMode.STEP &&
                                    AutoChordAdvancePolicy.shouldAdvance(
                                        mode = chordMode,
                                        hadPressedPointers = hadPressedPointers,
                                        hasPressedPointers = hasPressedPointers,
                                        chordHadNote = autoChordHadNote,
                                    )
                                ) {
                                    onAdvanceChord()
                                    autoChordHadNote = false
                                }
                                hadPressedPointers = hasPressedPointers
                            }
                        }
                    } finally {
                        pointerPitches.values.toList().forEach(::deactivatePitch)
                        pointerPitches.clear()
                    }
                },
        ) {
            val whiteKeyWidth = maxWidth / PianoTouchLayout.whitePitches.size

            Row(modifier = Modifier.fillMaxSize()) {
                PianoTouchLayout.whitePitches.forEach { layoutPitch ->
                    val pitch = visualPitch(layoutPitch)
                    val active = activePitchCounts.containsKey(pitch)
                    Box(
                        modifier = Modifier
                            .width(whiteKeyWidth)
                            .fillMaxHeight()
                            .background(if (active) Color(0xFFD5E5FF) else Color.White)
                            .border(0.8.dp, Color(0xFF555555)),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        val showLabel = when (noteLabelSetting) {
                            KeyboardNoteLabelSetting.OFF -> false
                            KeyboardNoteLabelSetting.C_ONLY -> pitch % 12 == 0
                            KeyboardNoteLabelSetting.ALL -> true
                        }
                        if (showLabel) {
                            Text(
                                PitchNames.name(pitch),
                                modifier = Modifier.padding(bottom = 5.dp),
                                color = Color(0xFF222222),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            PianoTouchLayout.blackKeys.forEach { key ->
                val pitch = visualPitch(key.midiPitch)
                val active = activePitchCounts.containsKey(pitch)
                Box(
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
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(ComposerControlStripColor)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("Piano", style = MaterialTheme.typography.labelMedium, color = Color.White)

            if (durationPaletteOpen) {
                ChamferedControlButton(
                    label = "← Back",
                    onClick = {
                        releaseAllPitches()
                        durationPaletteOpen = false
                    },
                )
                ChamferedControlButton(
                    label = "↶",
                    onClick = {
                        releaseAllPitches()
                        onUndo()
                    },
                    enabled = canUndo,
                )
                ChamferedControlButton(
                    label = "↷",
                    onClick = {
                        releaseAllPitches()
                        onRedo()
                    },
                    enabled = canRedo,
                )

                durationOrder.orderedDurations().forEach { duration ->
                    ChamferedControlButton(
                        label = durationControlLabel(duration, dotted = false),
                        onClick = {
                            releaseAllPitches()
                            onDurationSelected(duration)
                        },
                        selected = selectedDuration == duration,
                    )
                }

                ChamferedControlButton(
                    label = if (selectedDotted) "Dot On" else "Dot",
                    onClick = {
                        releaseAllPitches()
                        onToggleDotted()
                    },
                    selected = selectedDotted,
                )
                ChamferedControlButton(
                    label = if (tieActive) "Tie On" else "Tie",
                    onClick = {
                        releaseAllPitches()
                        onToggleTie()
                    },
                    selected = tieActive,
                    enabled = tieEnabled,
                )
                ChamferedControlButton(
                    label = "Rest",
                    onClick = {
                        releaseAllPitches()
                        onInsertRest()
                    },
                    enabled = entryMode != PianoEntryMode.LIVE,
                )
            } else if (articulationPaletteOpen) {
                ChamferedControlButton(
                    label = "← Back",
                    onClick = {
                        releaseAllPitches()
                        articulationPaletteOpen = false
                    },
                )
                NoteArticulation.entries.forEach { articulation ->
                    ChamferedControlButton(
                        label = articulationControlLabel(articulation, includePrefix = false),
                        onClick = {
                            releaseAllPitches()
                            onArticulationSelected(articulation)
                        },
                        selected = selectedArticulation == articulation,
                    )
                }
            } else {
                ChamferedControlButton(
                    label = "↶",
                    onClick = {
                        releaseAllPitches()
                        onUndo()
                    },
                    enabled = canUndo,
                )
                ChamferedControlButton(
                    label = "↷",
                    onClick = {
                        releaseAllPitches()
                        onRedo()
                    },
                    enabled = canRedo,
                )

                ChamferedControlButton(
                    label = "Oct −",
                    feedback = UiCommandFeedback.DECREASE,
                    onClick = {
                        releaseAllPitches()
                        onOctaveDown()
                    },
                    enabled = safeOctaveShift > -4,
                )
                ChamferedControlButton(
                    label = "Oct +",
                    feedback = UiCommandFeedback.INCREASE,
                    onClick = {
                        releaseAllPitches()
                        onOctaveUp()
                    },
                    enabled = safeOctaveShift < 3,
                )

                Text("Mode", style = MaterialTheme.typography.labelSmall, color = Color.White)
                ChamferedControlButton(
                    label = "Step",
                    onClick = {
                        if (entryMode != PianoEntryMode.STEP) {
                            releaseAllPitches()
                            onEntryModeChanged(PianoEntryMode.STEP)
                        }
                    },
                    selected = entryMode == PianoEntryMode.STEP,
                )
                ChamferedControlButton(
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
                        entryMode == PianoEntryMode.LIVE && liveRecordingActive -> "Stop Live"
                        entryMode == PianoEntryMode.LIVE -> "Live Armed"
                        else -> "Live"
                    },
                    onClick = {
                        if (entryMode == PianoEntryMode.LIVE) {
                            if (liveRecordingActive) onStopLive()
                        } else {
                            releaseAllPitches()
                            onEntryModeChanged(PianoEntryMode.LIVE)
                        }
                    },
                    selected = entryMode == PianoEntryMode.LIVE,
                )

                if (entryMode == PianoEntryMode.HOLD) {
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
                    label = articulationControlLabel(selectedArticulation, includePrefix = true),
                    onClick = {
                        releaseAllPitches()
                        durationPaletteOpen = false
                        articulationPaletteOpen = true
                    },
                )

                ChamferedControlButton(
                    label = "Rest",
                    onClick = {
                        releaseAllPitches()
                        onInsertRest()
                    },
                    enabled = entryMode != PianoEntryMode.LIVE,
                )

                if (entryMode == PianoEntryMode.STEP) {
                    ChamferedControlButton(
                        label = when (chordMode) {
                            StepChordMode.OFF -> "Chord Off"
                            StepChordMode.MANUAL -> "Chord Manual"
                            StepChordMode.AUTO -> "Chord Auto"
                        },
                        onClick = {
                            releaseAllPitches()
                            onCycleChordMode()
                        },
                        selected = chordMode != StepChordMode.OFF,
                    )
                    ChamferedControlButton(
                        label = "Next Chord",
                        onClick = {
                            releaseAllPitches()
                            onAdvanceChord()
                        },
                        enabled = chordMode != StepChordMode.OFF,
                    )
                }

                Text(
                    "${PitchNames.name(visualPitch(60))}–${PitchNames.name(visualPitch(83))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }

    }
}

private fun articulationControlLabel(
    articulation: NoteArticulation,
    includePrefix: Boolean,
): String {
    val glyph = when (articulation) {
        NoteArticulation.NORMAL -> ""
        NoteArticulation.STACCATO -> "• "
        NoteArticulation.TENUTO -> "— "
        NoteArticulation.ACCENT -> "> "
        NoteArticulation.LEGATO -> "⌒ "
    }
    val core = "$glyph${articulation.displayName}"
    return if (includePrefix) "Art $core" else core
}

private fun durationControlLabel(duration: NoteDuration, dotted: Boolean): String {
    val glyph = when (duration) {
        NoteDuration.WHOLE -> "𝅝"
        NoteDuration.HALF -> "𝅗𝅥"
        NoteDuration.QUARTER -> "♩"
        NoteDuration.EIGHTH -> "♪"
        NoteDuration.SIXTEENTH -> "𝅘𝅥𝅯"
    }
    return "$glyph ${duration.displayName}${if (dotted) " •" else ""}"
}
