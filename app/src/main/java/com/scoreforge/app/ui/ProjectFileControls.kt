package com.scoreforge.app.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.scoreforge.app.music.ScoreProjectCodec
import com.scoreforge.app.music.ScoreProjectSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProjectFileControls(
    projectName: String,
    snapshotProvider: () -> ScoreProjectSnapshot,
    onNewProject: () -> Unit,
    onRenameProject: (String) -> Unit,
    onOpenProject: (ScoreProjectSnapshot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var renameDialogOpen by remember { mutableStateOf(false) }
    var newProjectDialogOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Autosave on") }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val snapshot = snapshotProvider()
        scope.launch {
            status = "Saving…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "w").use { output ->
                        requireNotNull(output) { "Could not open the selected file for writing." }
                        output.bufferedWriter().use { writer ->
                            writer.write(ScoreProjectCodec.encode(snapshot))
                        }
                    }
                }
            }
            status = if (result.isSuccess) {
                "Saved ${snapshot.safeProjectName()}"
            } else {
                result.exceptionOrNull()?.message ?: "Save failed"
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = "Opening…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Could not open the selected file." }
                        input.bufferedReader().use { it.readText() }
                    }
                    val decoded = requireNotNull(ScoreProjectCodec.decode(raw)) {
                        "That file is not a supported Score Forge project."
                    }
                    if (decoded.projectName == "Untitled") {
                        val displayName = queryDisplayName(context, uri)
                            ?.removeSuffix(".sfp")
                            ?.trim()
                            .orEmpty()
                        if (displayName.isNotBlank()) {
                            decoded.copy(projectName = ScoreProjectSnapshot.sanitizeProjectName(displayName))
                        } else {
                            decoded
                        }
                    } else {
                        decoded
                    }
                }
            }

            result.onSuccess { snapshot ->
                onOpenProject(snapshot)
                status = "Opened ${snapshot.safeProjectName()}"
            }.onFailure { error ->
                status = error.message ?: "Open failed"
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("Project:", style = MaterialTheme.typography.labelLarge)
        Text(projectName, style = MaterialTheme.typography.labelLarge)

        OutlinedButton(onClick = { newProjectDialogOpen = true }) {
            Text("New")
        }

        OutlinedButton(
            onClick = {
                renameText = projectName
                renameDialogOpen = true
            },
        ) {
            Text("Rename")
        }

        OutlinedButton(
            onClick = {
                val safeName = ScoreProjectSnapshot.sanitizeProjectName(projectName)
                    .replace(Regex("[\\/:*?\"<>|]"), "_")
                    .ifBlank { "Untitled" }
                saveLauncher.launch("$safeName.sfp")
            },
        ) {
            Text("Save As")
        }

        OutlinedButton(
            onClick = { openLauncher.launch(arrayOf("*/*")) },
        ) {
            Text("Open")
        }

        Spacer(modifier = Modifier.weight(1f))
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (newProjectDialogOpen) {
        AlertDialog(
            onDismissRequest = { newProjectDialogOpen = false },
            title = { Text("Start a new project?") },
            text = {
                Text("The current composition will be replaced by a blank project. Use Save As first if you want a separate .sfp copy.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onNewProject()
                        status = "New project"
                        newProjectDialogOpen = false
                    },
                ) {
                    Text("New Project")
                }
            },
            dismissButton = {
                TextButton(onClick = { newProjectDialogOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (renameDialogOpen) {
        AlertDialog(
            onDismissRequest = { renameDialogOpen = false },
            title = { Text("Rename project") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(120) },
                    singleLine = true,
                    label = { Text("Project name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameProject(ScoreProjectSnapshot.sanitizeProjectName(renameText))
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

private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? =
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (column < 0) null else cursor.getString(column)
    }
