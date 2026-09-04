package com.scoreforge.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scoreforge.app.music.ComfortTempo

@Composable
fun ComfortTempoControls(
    capturing: Boolean,
    attackCount: Int,
    estimatedBpm: Int?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    onTryAgain: () -> Unit,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Comfort Tempo:", style = MaterialTheme.typography.labelLarge)

        when {
            capturing -> {
                Surface(tonalElevation = 2.dp) {
                    Text(
                        "Tap any piano key ${ComfortTempo.REQUIRED_ATTACKS - attackCount} more time${if (ComfortTempo.REQUIRED_ATTACKS - attackCount == 1) "" else "s"} at a comfortable quarter-note pulse",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            }

            estimatedBpm != null -> {
                Surface(tonalElevation = 2.dp) {
                    Text(
                        "Estimated: $estimatedBpm BPM",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Button(onClick = onApply) { Text("Apply") }
                OutlinedButton(onClick = onTryAgain) { Text("Try Again") }
            }

            else -> {
                OutlinedButton(onClick = onStart) { Text("Measure") }
                Text(
                    "Tap 8 evenly spaced quarter-note attacks on the piano.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
