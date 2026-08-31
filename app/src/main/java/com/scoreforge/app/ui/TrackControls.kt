package com.scoreforge.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
    onDeleteTrack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = tracks.getOrNull(activeTrackIndex)
    var renameDialogOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    Row(
        modifier = modifier
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
        }

        OutlinedButton(
            onClick = onDeleteTrack,
            enabled = tracks.size > 1,
        ) {
            Text("Delete Track")
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
