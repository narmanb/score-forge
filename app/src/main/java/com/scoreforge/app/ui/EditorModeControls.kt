package com.scoreforge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class ScoreEditorMode {
    STAFF,
    PIANO_ROLL,
}

@Composable
fun EditorModeControls(
    mode: ScoreEditorMode,
    showPianoKeyboard: Boolean,
    onModeChanged: (ScoreEditorMode) -> Unit,
    onTogglePianoKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("Editor:")
        if (mode == ScoreEditorMode.STAFF) {
            Button(onClick = { onModeChanged(ScoreEditorMode.STAFF) }) { Text("Staff") }
        } else {
            OutlinedButton(onClick = { onModeChanged(ScoreEditorMode.STAFF) }) { Text("Staff") }
        }

        if (mode == ScoreEditorMode.PIANO_ROLL) {
            Button(onClick = { onModeChanged(ScoreEditorMode.PIANO_ROLL) }) { Text("Piano Roll") }
        } else {
            OutlinedButton(onClick = { onModeChanged(ScoreEditorMode.PIANO_ROLL) }) { Text("Piano Roll") }
        }

        Spacer(modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onTogglePianoKeyboard) {
            Text(if (showPianoKeyboard) "Hide Piano" else "Show Piano")
        }
    }
}
