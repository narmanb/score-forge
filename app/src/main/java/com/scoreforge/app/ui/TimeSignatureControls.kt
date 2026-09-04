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
import com.scoreforge.app.music.ScoreTimeSignature
import com.scoreforge.app.music.ScoreTimeSignatures

@Composable
fun TimeSignatureControls(
    timeSignatures: List<ScoreTimeSignature>,
    cursorBeat: Float,
    onSetSignature: (startBeat: Float, numerator: Int, denominator: Int) -> Unit,
    onRemoveSignature: (startBeat: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalized = ScoreTimeSignatures.normalize(timeSignatures)
    val measureStart = ScoreTimeSignatures.measureStartAt(normalized, cursorBeat)
    val active = ScoreTimeSignatures.atBeat(normalized, measureStart)
    val hasExplicitChange = measureStart > 0.001f &&
        normalized.any { kotlin.math.abs(it.startBeat - measureStart) <= 0.001f }
    val denominators = ScoreTimeSignatures.SUPPORTED_DENOMINATORS
    val denominatorIndex = denominators.indexOf(active.denominator).coerceAtLeast(0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("Time:", style = MaterialTheme.typography.labelLarge)
        Text(active.displayName, style = MaterialTheme.typography.labelLarge)
        Text(
            if (measureStart <= 0.001f) "from start" else "from beat ${formatMeterBeat(measureStart)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        CompactCommandButton(
            label = "Num −",
            onClick = {
                onSetSignature(measureStart, (active.numerator - 1).coerceAtLeast(1), active.denominator)
            },
            enabled = active.numerator > 1,
        )

        CompactCommandButton(
            label = "Num +",
            onClick = {
                onSetSignature(measureStart, (active.numerator + 1).coerceAtMost(32), active.denominator)
            },
            enabled = active.numerator < 32,
        )

        CompactCommandButton(
            label = "Denom −",
            onClick = {
                val nextIndex = (denominatorIndex - 1).coerceAtLeast(0)
                onSetSignature(measureStart, active.numerator, denominators[nextIndex])
            },
            enabled = denominatorIndex > 0,
        )

        CompactCommandButton(
            label = "Denom +",
            onClick = {
                val nextIndex = (denominatorIndex + 1).coerceAtMost(denominators.lastIndex)
                onSetSignature(measureStart, active.numerator, denominators[nextIndex])
            },
            enabled = denominatorIndex < denominators.lastIndex,
        )

        if (hasExplicitChange) {
            CompactCommandButton(
                label = "Remove Change",
                onClick = { onRemoveSignature(measureStart) },
            )
        }

        Text(
            "Move the red edit cursor into a measure, then change its meter. The change begins at that measure's barline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatMeterBeat(beat: Float): String =
    if (beat % 1f == 0f) beat.toInt().toString() else "%.2f".format(beat)
