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
import com.scoreforge.app.music.ScoreKeySignature
import com.scoreforge.app.music.ScoreKeySignatures
import com.scoreforge.app.music.ScoreTimeSignature
import com.scoreforge.app.music.ScoreTimeSignatures

@Composable
fun KeySignatureControls(
    keySignatures: List<ScoreKeySignature>,
    timeSignatures: List<ScoreTimeSignature>,
    cursorBeat: Float,
    onSetSignature: (startBeat: Float, fifths: Int, minor: Boolean) -> Unit,
    onRemoveSignature: (startBeat: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalizedKeys = ScoreKeySignatures.normalize(keySignatures)
    val measureStart = ScoreTimeSignatures.measureStartAt(timeSignatures, cursorBeat)
    val active = ScoreKeySignatures.atBeat(normalizedKeys, measureStart)
    val hasExplicitChange = measureStart > 0.001f &&
        normalizedKeys.any { kotlin.math.abs(it.startBeat - measureStart) <= 0.001f }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("Key:", style = MaterialTheme.typography.labelLarge)
        Text(active.displayName, style = MaterialTheme.typography.labelLarge)
        Text(
            if (measureStart <= 0.001f) "from start" else "from beat ${formatKeyBeat(measureStart)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        CompactCommandButton(
            label = "Flatter ♭",
            onClick = {
                onSetSignature(measureStart, (active.fifths - 1).coerceAtLeast(-7), active.minor)
            },
            enabled = active.fifths > -7,
        )

        CompactCommandButton(
            label = "Sharper ♯",
            onClick = {
                onSetSignature(measureStart, (active.fifths + 1).coerceAtMost(7), active.minor)
            },
            enabled = active.fifths < 7,
        )

        CompactCommandButton(
            label = if (active.minor) "Minor → Major" else "Major → Minor",
            onClick = { onSetSignature(measureStart, active.fifths, !active.minor) },
        )

        if (hasExplicitChange) {
            CompactCommandButton(
                label = "Remove Change",
                onClick = { onRemoveSignature(measureStart) },
            )
        }

        Text(
            "Move the red edit cursor into a measure, then change its key. Manual changes begin at that measure's barline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatKeyBeat(beat: Float): String =
    if (beat % 1f == 0f) beat.toInt().toString() else "%.2f".format(beat)
