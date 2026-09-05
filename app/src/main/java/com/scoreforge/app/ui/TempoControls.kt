package com.scoreforge.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scoreforge.app.music.ScoreTempoChange
import com.scoreforge.app.music.ScoreTempos
import com.scoreforge.app.music.ScoreTimeline
import kotlin.math.abs

@Composable
fun TempoControls(
    tempoChanges: List<ScoreTempoChange>,
    cursorBeat: Float,
    onSetTempo: (startBeat: Float, bpm: Int) -> Unit,
    onRemoveTempo: (startBeat: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalized = ScoreTempos.normalize(tempoChanges)
    val editBeat = ScoreTimeline.quantizeBeat(cursorBeat).coerceAtLeast(0f)
    val active = ScoreTempos.atBeat(normalized, editBeat)
    val explicitAtCursor = normalized.firstOrNull { abs(it.startBeat - editBeat) <= 0.001f }
    val hasRemovableChange = editBeat > 0.001f && explicitAtCursor != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("Tempo:", style = MaterialTheme.typography.labelLarge)
        Text("${active.bpm} BPM", style = MaterialTheme.typography.labelLarge)
        Text(
            if (active.startBeat <= 0.001f) "from start" else "from beat ${formatTempoBeat(active.startBeat)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Map: " + normalized.joinToString(" • ") { change ->
                val where = if (change.startBeat <= 0.001f) "start" else "beat ${formatTempoBeat(change.startBeat)}"
                "$where = ${change.bpm}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        CompactCommandButton(
            label = "−5",
            feedback = UiCommandFeedback.DECREASE,
            onClick = { onSetTempo(editBeat, (active.bpm - 5).coerceAtLeast(ScoreTempos.MIN_BPM)) },
            enabled = active.bpm > ScoreTempos.MIN_BPM,
        )
        CompactCommandButton(
            label = "+5",
            feedback = UiCommandFeedback.INCREASE,
            onClick = { onSetTempo(editBeat, (active.bpm + 5).coerceAtMost(ScoreTempos.MAX_BPM)) },
            enabled = active.bpm < ScoreTempos.MAX_BPM,
        )

        if (hasRemovableChange) {
            CompactCommandButton(
                label = "Remove Change",
                onClick = { onRemoveTempo(editBeat) },
            )
        }

        Text(
            "Move the red edit cursor, then change BPM. A tempo change begins at that beat; beat 0 sets the starting tempo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatTempoBeat(beat: Float): String =
    if (beat % 1f == 0f) beat.toInt().toString() else "%.2f".format(beat)
