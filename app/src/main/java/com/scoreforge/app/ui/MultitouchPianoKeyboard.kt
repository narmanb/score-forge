package com.scoreforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.awaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.scoreforge.app.music.PitchNames

@Composable
fun MultitouchPianoKeyboard(
    chordMode: Boolean,
    onToggleChordMode: () -> Unit,
    onAdvanceChord: () -> Unit,
    onPitchDown: (Int) -> Unit,
    onPitchUp: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activePitchCounts = remember { mutableStateMapOf<Int, Int>() }

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

    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                if (chordMode) "Piano chord step entry • hold multiple keys" else "Piano step entry • press, hold, and slide",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onToggleChordMode) {
                Text(if (chordMode) "Chord: On" else "Chord: Off")
            }
            OutlinedButton(onClick = onAdvanceChord, enabled = chordMode) {
                Text("Next")
            }
            Text(
                "C4–B5",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 3.dp)
                .pointerInput(chordMode) {
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
                                        )
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
                                        )
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
                PianoTouchLayout.whitePitches.forEach { pitch ->
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
                val active = activePitchCounts.containsKey(key.midiPitch)
                Box(
                    modifier = Modifier
                        .offset(x = whiteKeyWidth * (key.whiteIndex + PianoTouchLayout.BLACK_KEY_X_OFFSET_FRACTION))
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
    }
}
