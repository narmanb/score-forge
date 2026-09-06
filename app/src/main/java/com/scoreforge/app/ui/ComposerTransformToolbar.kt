package com.scoreforge.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scoreforge.app.music.ComfortTempo
import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.ScoreClef
import com.scoreforge.app.music.ScoreClefMode
import com.scoreforge.app.music.ScoreKeySignature
import com.scoreforge.app.music.ScoreKeySignatures
import com.scoreforge.app.music.ScoreTempoChange
import com.scoreforge.app.music.ScoreTempos
import com.scoreforge.app.music.ScoreTimeSignature
import com.scoreforge.app.music.ScoreTimeSignatures
import com.scoreforge.app.music.ScoreTimeline
import kotlin.math.abs

enum class ComposerToolbarSection {
    ROOT,
    TEMPO,
    TIME,
    KEY,
    CLEF,
    NOTES,
    EDITOR,
    MEASURE,
}

/**
 * One-row composer toolbar that replaces several always-visible control rows.
 * Opening a category transforms the same row instead of pushing the editor down.
 */
@Composable
fun ComposerTransformToolbar(
    tempoChanges: List<ScoreTempoChange>,
    timeSignatures: List<ScoreTimeSignature>,
    keySignatures: List<ScoreKeySignature>,
    cursorBeat: Float,
    clefMode: ScoreClefMode,
    effectiveClef: ScoreClef,
    selectedDuration: NoteDuration,
    dotted: Boolean,
    sharpInput: Boolean,
    tieEnabled: Boolean,
    tieActive: Boolean,
    editorMode: ScoreEditorMode,
    showPianoKeyboard: Boolean,
    measureNumber: Int,
    comfortTempoCapturing: Boolean,
    comfortTempoAttackCount: Int,
    comfortTempoEstimate: Int?,
    onSetTempo: (startBeat: Float, bpm: Int) -> Unit,
    onRemoveTempo: (startBeat: Float) -> Unit,
    onStartComfortTempo: () -> Unit,
    onCancelComfortTempo: () -> Unit,
    onApplyComfortTempo: () -> Unit,
    onTryComfortTempoAgain: () -> Unit,
    onSetTimeSignature: (startBeat: Float, numerator: Int, denominator: Int) -> Unit,
    onRemoveTimeSignature: (startBeat: Float) -> Unit,
    onSetKeySignature: (startBeat: Float, fifths: Int, minor: Boolean) -> Unit,
    onRemoveKeySignature: (startBeat: Float) -> Unit,
    onClefModeChanged: (ScoreClefMode) -> Unit,
    onDurationSelected: (NoteDuration) -> Unit,
    onToggleDotted: () -> Unit,
    onInsertRest: () -> Unit,
    onToggleSharpInput: () -> Unit,
    onToggleTie: () -> Unit,
    onEditorModeChanged: (ScoreEditorMode) -> Unit,
    onTogglePianoKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var section by rememberSaveable { mutableStateOf(ComposerToolbarSection.ROOT) }

    LaunchedEffect(comfortTempoCapturing, comfortTempoEstimate) {
        if (comfortTempoCapturing || comfortTempoEstimate != null) {
            section = ComposerToolbarSection.TEMPO
        }
    }

    val normalizedTempo = ScoreTempos.normalize(tempoChanges)
    val tempoEditBeat = ScoreTimeline.quantizeBeat(cursorBeat).coerceAtLeast(0f)
    val activeTempo = ScoreTempos.atBeat(normalizedTempo, tempoEditBeat)
    val removableTempo = tempoEditBeat > 0.001f &&
        normalizedTempo.any { abs(it.startBeat - tempoEditBeat) <= 0.001f }

    val normalizedTime = ScoreTimeSignatures.normalize(timeSignatures)
    val measureStart = ScoreTimeSignatures.measureStartAt(normalizedTime, cursorBeat)
    val activeTime = ScoreTimeSignatures.atBeat(normalizedTime, measureStart)
    val removableTime = measureStart > 0.001f &&
        normalizedTime.any { abs(it.startBeat - measureStart) <= 0.001f }
    val denominators = ScoreTimeSignatures.SUPPORTED_DENOMINATORS
    val denominatorIndex = denominators.indexOf(activeTime.denominator).coerceAtLeast(0)

    val normalizedKeys = ScoreKeySignatures.normalize(keySignatures)
    val activeKey = ScoreKeySignatures.atBeat(normalizedKeys, measureStart)
    val removableKey = measureStart > 0.001f &&
        normalizedKeys.any { abs(it.startBeat - measureStart) <= 0.001f }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        when (section) {
            ComposerToolbarSection.ROOT -> {
                OutlinedButton(onClick = { section = ComposerToolbarSection.TEMPO }) {
                    Text("Tempo ${activeTempo.bpm}")
                }
                OutlinedButton(onClick = { section = ComposerToolbarSection.TIME }) {
                    Text("Time ${activeTime.displayName}")
                }
                OutlinedButton(onClick = { section = ComposerToolbarSection.KEY }) {
                    Text("Key ${activeKey.displayName}")
                }
                OutlinedButton(onClick = { section = ComposerToolbarSection.CLEF }) {
                    Text("Clef ${clefMode.displayName}")
                }
                OutlinedButton(onClick = { section = ComposerToolbarSection.NOTES }) {
                    Text("Notes ${selectedDuration.displayName}${if (dotted) " •" else ""}")
                }
                OutlinedButton(onClick = { section = ComposerToolbarSection.EDITOR }) {
                    Text(if (editorMode == ScoreEditorMode.STAFF) "Editor Staff" else "Editor Piano Roll")
                }
                OutlinedButton(onClick = { section = ComposerToolbarSection.MEASURE }) {
                    Text("Measure ${measureNumber.coerceAtLeast(1)}")
                }
            }

            ComposerToolbarSection.TEMPO -> {
                BackButton { section = ComposerToolbarSection.ROOT }
                Text("${activeTempo.bpm} BPM", style = MaterialTheme.typography.labelLarge)
                CompactCommandButton(
                    label = "−5",
                    feedback = UiCommandFeedback.DECREASE,
                    onClick = {
                        onSetTempo(
                            tempoEditBeat,
                            (activeTempo.bpm - 5).coerceAtLeast(ScoreTempos.MIN_BPM),
                        )
                    },
                    enabled = activeTempo.bpm > ScoreTempos.MIN_BPM,
                )
                CompactCommandButton(
                    label = "+5",
                    feedback = UiCommandFeedback.INCREASE,
                    onClick = {
                        onSetTempo(
                            tempoEditBeat,
                            (activeTempo.bpm + 5).coerceAtMost(ScoreTempos.MAX_BPM),
                        )
                    },
                    enabled = activeTempo.bpm < ScoreTempos.MAX_BPM,
                )
                if (removableTempo) {
                    CompactCommandButton(
                        label = "Remove Change",
                        onClick = { onRemoveTempo(tempoEditBeat) },
                    )
                }

                when {
                    comfortTempoCapturing -> {
                        val remaining = (ComfortTempo.REQUIRED_ATTACKS - comfortTempoAttackCount).coerceAtLeast(0)
                        Text("Tap piano $remaining more", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = onCancelComfortTempo) { Text("Cancel") }
                    }

                    comfortTempoEstimate != null -> {
                        Text("Measured: $comfortTempoEstimate BPM", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onApplyComfortTempo) { Text("Apply") }
                        OutlinedButton(onClick = onTryComfortTempoAgain) { Text("Try Again") }
                    }

                    else -> {
                        OutlinedButton(onClick = onStartComfortTempo) { Text("Measure Tempo") }
                    }
                }
            }

            ComposerToolbarSection.TIME -> {
                BackButton { section = ComposerToolbarSection.ROOT }
                Text(activeTime.displayName, style = MaterialTheme.typography.labelLarge)
                CompactCommandButton(
                    label = "Num −",
                    feedback = UiCommandFeedback.DECREASE,
                    onClick = {
                        onSetTimeSignature(
                            measureStart,
                            (activeTime.numerator - 1).coerceAtLeast(1),
                            activeTime.denominator,
                        )
                    },
                    enabled = activeTime.numerator > 1,
                )
                CompactCommandButton(
                    label = "Num +",
                    feedback = UiCommandFeedback.INCREASE,
                    onClick = {
                        onSetTimeSignature(
                            measureStart,
                            (activeTime.numerator + 1).coerceAtMost(32),
                            activeTime.denominator,
                        )
                    },
                    enabled = activeTime.numerator < 32,
                )
                CompactCommandButton(
                    label = "Denom −",
                    feedback = UiCommandFeedback.DECREASE,
                    onClick = {
                        onSetTimeSignature(
                            measureStart,
                            activeTime.numerator,
                            denominators[(denominatorIndex - 1).coerceAtLeast(0)],
                        )
                    },
                    enabled = denominatorIndex > 0,
                )
                CompactCommandButton(
                    label = "Denom +",
                    feedback = UiCommandFeedback.INCREASE,
                    onClick = {
                        onSetTimeSignature(
                            measureStart,
                            activeTime.numerator,
                            denominators[(denominatorIndex + 1).coerceAtMost(denominators.lastIndex)],
                        )
                    },
                    enabled = denominatorIndex < denominators.lastIndex,
                )
                if (removableTime) {
                    CompactCommandButton(
                        label = "Remove Change",
                        onClick = { onRemoveTimeSignature(measureStart) },
                    )
                }
            }

            ComposerToolbarSection.KEY -> {
                BackButton { section = ComposerToolbarSection.ROOT }
                Text(activeKey.displayName, style = MaterialTheme.typography.labelLarge)
                CompactCommandButton(
                    label = "Flatter ♭",
                    feedback = UiCommandFeedback.DECREASE,
                    onClick = {
                        onSetKeySignature(
                            measureStart,
                            (activeKey.fifths - 1).coerceAtLeast(-7),
                            activeKey.minor,
                        )
                    },
                    enabled = activeKey.fifths > -7,
                )
                CompactCommandButton(
                    label = "Sharper ♯",
                    feedback = UiCommandFeedback.INCREASE,
                    onClick = {
                        onSetKeySignature(
                            measureStart,
                            (activeKey.fifths + 1).coerceAtMost(7),
                            activeKey.minor,
                        )
                    },
                    enabled = activeKey.fifths < 7,
                )
                CompactCommandButton(
                    label = if (activeKey.minor) "Minor → Major" else "Major → Minor",
                    onClick = {
                        onSetKeySignature(measureStart, activeKey.fifths, !activeKey.minor)
                    },
                )
                if (removableKey) {
                    CompactCommandButton(
                        label = "Remove Change",
                        onClick = { onRemoveKeySignature(measureStart) },
                    )
                }
            }

            ComposerToolbarSection.CLEF -> {
                BackButton { section = ComposerToolbarSection.ROOT }
                ScoreClefMode.entries.forEach { option ->
                    if (option == clefMode) {
                        Button(onClick = { onClefModeChanged(option) }) { Text(option.displayName) }
                    } else {
                        OutlinedButton(onClick = { onClefModeChanged(option) }) { Text(option.displayName) }
                    }
                }
                if (clefMode == ScoreClefMode.AUTO) {
                    Text("Using ${effectiveClef.displayName}", style = MaterialTheme.typography.bodySmall)
                }
            }

            ComposerToolbarSection.NOTES -> {
                BackButton { section = ComposerToolbarSection.ROOT }
                NoteDuration.entries.forEach { duration ->
                    if (duration == selectedDuration) {
                        Button(onClick = { onDurationSelected(duration) }) { Text(duration.displayName) }
                    } else {
                        OutlinedButton(onClick = { onDurationSelected(duration) }) { Text(duration.displayName) }
                    }
                }
                if (dotted) Button(onClick = onToggleDotted) { Text("Dot •") }
                else OutlinedButton(onClick = onToggleDotted) { Text("Dot") }

                OutlinedButton(onClick = onInsertRest) {
                    Text(if (dotted) "Dotted Rest" else "Rest")
                }

                if (tieActive) Button(onClick = onToggleTie, enabled = tieEnabled) { Text("Tie →") }
                else OutlinedButton(onClick = onToggleTie, enabled = tieEnabled) { Text("Tie →") }

                if (sharpInput) Button(onClick = onToggleSharpInput) { Text("Staff ♯") }
                else OutlinedButton(onClick = onToggleSharpInput) { Text("Staff ♯") }
            }

            ComposerToolbarSection.EDITOR -> {
                BackButton { section = ComposerToolbarSection.ROOT }
                if (editorMode == ScoreEditorMode.STAFF) {
                    Button(onClick = { onEditorModeChanged(ScoreEditorMode.STAFF) }) { Text("Staff") }
                } else {
                    OutlinedButton(onClick = { onEditorModeChanged(ScoreEditorMode.STAFF) }) { Text("Staff") }
                }
                if (editorMode == ScoreEditorMode.PIANO_ROLL) {
                    Button(onClick = { onEditorModeChanged(ScoreEditorMode.PIANO_ROLL) }) { Text("Piano Roll") }
                } else {
                    OutlinedButton(onClick = { onEditorModeChanged(ScoreEditorMode.PIANO_ROLL) }) { Text("Piano Roll") }
                }
                OutlinedButton(onClick = onTogglePianoKeyboard) {
                    Text(if (showPianoKeyboard) "Hide Piano" else "Show Piano")
                }
            }

            ComposerToolbarSection.MEASURE -> {
                BackButton { section = ComposerToolbarSection.ROOT }
                Text("Measure ${measureNumber.coerceAtLeast(1)}", style = MaterialTheme.typography.labelLarge)
                Text("Copy / paste / duplicate tools go here next", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text("← Back") }
}
