package com.scoreforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.scoreforge.app.music.PitchNames

@Composable
fun MultitouchPianoKeyboard(
    chordMode: Boolean,
    octaveShift: Int,
    entryMode: PianoEntryMode,
    onEntryModeChanged: (PianoEntryMode) -> Unit,
    onToggleChordMode: () -> Unit,
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
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 1.dp)
                .pointerInput(chordMode, entryMode) {
                    val pointerPitches = mutableMapOf<PointerId, Int>()

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
                                            change.consume()
                                        }
                                    } else if (change.previousPressed && change.pressed) {
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
                                    } else if (change.previousPressed && !change.pressed) {
                                        pointerPitches.remove(change.id)?.let(::deactivatePitch)
                                        change.consume()
                                    }
                                }
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
                            .border(0.6.dp, Color(0xFF555555)),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Text(
                            PitchNames.name(pitch),
                            modifier = Modifier.padding(bottom = 5.dp),
                            color = Color(0xFF222222),
                            style = MaterialTheme.typography.labelSmall,
                        )
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
                .height(30.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Piano", style = MaterialTheme.typography.labelMedium)

            FilledTonalButton(
                onClick = {
                    releaseAllPitches()
                    onOctaveDown()
                },
                enabled = safeOctaveShift > -4,
                modifier = Modifier.height(26.dp),
                shape = ChamferedControlShape,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                Text("Oct −", style = MaterialTheme.typography.labelMedium)
            }
            FilledTonalButton(
                onClick = {
                    releaseAllPitches()
                    onOctaveUp()
                },
                enabled = safeOctaveShift < 3,
                modifier = Modifier.height(26.dp),
                shape = ChamferedControlShape,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                Text("Oct +", style = MaterialTheme.typography.labelMedium)
            }

            Text("Mode", style = MaterialTheme.typography.labelSmall)
            if (entryMode == PianoEntryMode.STEP) {
                FilledTonalButton(
                    onClick = {},
                    modifier = Modifier.height(26.dp),
                    shape = ChamferedControlShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) { Text("Step", style = MaterialTheme.typography.labelMedium) }
            } else {
                OutlinedButton(
                    onClick = {
                        releaseAllPitches()
                        onEntryModeChanged(PianoEntryMode.STEP)
                    },
                    modifier = Modifier.height(26.dp),
                    shape = ChamferedControlShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) { Text("Step", style = MaterialTheme.typography.labelMedium) }
            }

            if (entryMode == PianoEntryMode.NATURAL) {
                FilledTonalButton(
                    onClick = {},
                    modifier = Modifier.height(26.dp),
                    shape = ChamferedControlShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) { Text("Natural", style = MaterialTheme.typography.labelMedium) }
            } else {
                OutlinedButton(
                    onClick = {
                        releaseAllPitches()
                        onEntryModeChanged(PianoEntryMode.NATURAL)
                    },
                    modifier = Modifier.height(26.dp),
                    shape = ChamferedControlShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) { Text("Natural", style = MaterialTheme.typography.labelMedium) }
            }

            FilledTonalButton(
                onClick = {
                    releaseAllPitches()
                    onInsertRest()
                },
                modifier = Modifier.height(26.dp),
                shape = ChamferedControlShape,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                Text("Rest", style = MaterialTheme.typography.labelMedium)
            }

            if (entryMode == PianoEntryMode.STEP) {
                FilledTonalButton(
                    onClick = {
                        releaseAllPitches()
                        onToggleChordMode()
                    },
                    modifier = Modifier.height(26.dp),
                    shape = ChamferedControlShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Text(
                        if (chordMode) "Chord On" else "Chord Off",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                FilledTonalButton(
                    onClick = {
                        releaseAllPitches()
                        onAdvanceChord()
                    },
                    enabled = chordMode,
                    modifier = Modifier.height(26.dp),
                    shape = ChamferedControlShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Text("Next", style = MaterialTheme.typography.labelMedium)
                }
            }

            Text(
                "${PitchNames.name(visualPitch(60))}–${PitchNames.name(visualPitch(83))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
