package com.scoreforge.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
                    ScoreForgeButton(onClick = { onSelectTrack(index) }) { Text(label) }
                } else {
                    ScoreForgeOutlinedButton(onClick = { onSelectTrack(index) }) { Text(label) }
                }
            }

            ScoreForgeOutlinedButton(
                onClick = onAddTrack,
                enabled = tracks.size < ScoreTracks.MAX_TRACKS,
            ) {
                Text("+ Track")
            }

            ScoreForgeOutlinedButton(
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
                    ScoreForgeButton(onClick = onToggleMute) { Text("Muted") }
                } else {
                    ScoreForgeOutlinedButton(onClick = onToggleMute) { Text("Mute") }
                }

                if (active.solo) {
                    ScoreForgeButton(onClick = onToggleSolo) { Text("Solo") }
                } else {
                    ScoreForgeOutlinedButton(onClick = onToggleSolo) { Text("Solo") }
                }
            }

            ScoreForgeOutlinedButton(
                onClick = onDeleteTrack,
                enabled = tracks.size > 1,
                haptic = UiHapticFeedback.STRONG,
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
                ScoreForgeTextButton(
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
                ScoreForgeTextButton(onClick = { renameDialogOpen = false }) {
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
