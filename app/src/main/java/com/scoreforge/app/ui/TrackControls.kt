package com.scoreforge.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scoreforge.app.music.ScoreTrack
import com.scoreforge.app.music.ScoreTracks

@Composable
fun TrackControls(
    tracks: List<ScoreTrack>,
    activeTrackIndex: Int,
    onSelectTrack: (Int) -> Unit,
    onAddTrack: () -> Unit,
    onRenameTrack: (String) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onPanChange: (Int) -> Unit,
    onPanChangeFinished: () -> Unit,
    onDeleteTrack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = tracks.getOrNull(activeTrackIndex)
    var renameDialogOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("Tracks:", style = MaterialTheme.typography.labelLarge)

            tracks.forEachIndexed { index, track ->
                val label = buildString {
                    append(track.name)
                    if (track.solo) append(" [S]")
                    if (track.muted) append(" [M]")
                }
                if (index == activeTrackIndex) {
                    Button(onClick = { onSelectTrack(index) }) { Text(label) }
                } else {
                    OutlinedButton(onClick = { onSelectTrack(index) }) { Text(label) }
                }
            }

            OutlinedButton(
                onClick = onAddTrack,
                enabled = tracks.size < ScoreTracks.MAX_TRACKS,
            ) {
                Text("+ Track")
            }

            OutlinedButton(
                onClick = {
                    renameText = active?.name.orEmpty()
                    renameDialogOpen = true
                },
                enabled = active != null,
            ) {
                Text("Rename")
            }

            if (active != null) {
                if (active.muted) {
                    Button(onClick = onToggleMute) { Text("Muted") }
                } else {
                    OutlinedButton(onClick = onToggleMute) { Text("Mute") }
                }

                if (active.solo) {
                    Button(onClick = onToggleSolo) { Text("Solo") }
                } else {
                    OutlinedButton(onClick = onToggleSolo) { Text("Solo") }
                }
            }

            OutlinedButton(
                onClick = onDeleteTrack,
                enabled = tracks.size > 1,
            ) {
                Text("Delete Track")
            }
        }

        if (active != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Vol ${active.volume}", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = active.volume.toFloat(),
                    onValueChange = { onVolumeChange(it.toInt().coerceIn(ScoreTrack.MIN_VOLUME, ScoreTrack.MAX_VOLUME)) },
                    onValueChangeFinished = onVolumeChangeFinished,
                    valueRange = ScoreTrack.MIN_VOLUME.toFloat()..ScoreTrack.MAX_VOLUME.toFloat(),
                    modifier = Modifier.weight(1f),
                )

                Text("Pan ${panLabel(active.pan)}", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = active.pan.toFloat(),
                    onValueChange = { onPanChange(it.toInt().coerceIn(ScoreTrack.MIN_PAN, ScoreTrack.MAX_PAN)) },
                    onValueChangeFinished = onPanChangeFinished,
                    valueRange = ScoreTrack.MIN_PAN.toFloat()..ScoreTrack.MAX_PAN.toFloat(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (renameDialogOpen) {
        AlertDialog(
            onDismissRequest = { renameDialogOpen = false },
            title = { Text("Rename track") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(80) },
                    singleLine = true,
                    label = { Text("Track name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val safeName = renameText.trim()
                        if (safeName.isNotEmpty()) onRenameTrack(safeName)
                        renameDialogOpen = false
                    },
                    enabled = renameText.isNotBlank(),
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun panLabel(pan: Int): String = when {
    pan < 0 -> "L${-pan}"
    pan > 0 -> "R$pan"
    else -> "C"
}
