package com.scoreforge.app.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.scoreforge.app.ExternalFileTypes
import com.scoreforge.app.music.MidiExporter
import com.scoreforge.app.music.MidiImporter
import com.scoreforge.app.music.ScoreProjectCodec
import com.scoreforge.app.music.ScoreProjectSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProjectFileControls(
    projectName: String,
    activeTrackName: String,
    canClearTrack: Boolean,
    snapshotProvider: () -> ScoreProjectSnapshot,
    onNewProject: () -> Unit,
    onClearTrack: () -> Unit,
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
    var midiImportWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var midiExportWarnings by remember { mutableStateOf<List<String>>(emptyList()) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExternalFileTypes.SCORE_FORGE_PROJECT_MIME),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val snapshot = snapshotProvider()
        scope.launch {
            status = "Saving…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val safeUri = DocumentFileExtensions.ensure(context, uri, ".sfp")
                    context.contentResolver.openOutputStream(safeUri, "w").use { output ->
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
                        val displayName = queryDisplayNameLocal(context, uri)
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

    val midiImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = "Importing MIDI…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val displayName = queryDisplayNameLocal(context, uri).orEmpty()
                    val projectNameFromFile = displayName
                        .replace(Regex("(?i)\\.(mid|midi)$"), "")
                        .trim()
                        .ifBlank { "Imported MIDI" }
                    val bytes = context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Could not open the selected MIDI file." }
                        input.readBytes()
                    }
                    MidiImporter.import(bytes, projectNameFromFile)
                }
            }

            result.onSuccess { imported ->
                onOpenProject(imported.snapshot)
                status = imported.statusText()
                midiImportWarnings = imported.warnings
            }.onFailure { error ->
                status = error.message ?: "MIDI import failed"
            }
        }
    }

    val midiExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/midi"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val snapshot = snapshotProvider()
        scope.launch {
            status = "Exporting MIDI…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val safeUri = DocumentFileExtensions.ensure(context, uri, ".mid")
                    val exported = MidiExporter.export(snapshot)
                    context.contentResolver.openOutputStream(safeUri, "w").use { output ->
                        requireNotNull(output) { "Could not open the selected MIDI file for writing." }
                        output.write(exported.bytes)
                        output.flush()
                    }
                    exported
                }
            }

            result.onSuccess { exported ->
                status = buildString {
                    append("Exported ")
                    append(exported.exportedTrackCount)
                    append(if (exported.exportedTrackCount == 1) " track" else " tracks")
                    append(" • ")
                    append(exported.exportedNoteCount)
                    append(if (exported.exportedNoteCount == 1) " note" else " notes")
                }
                midiExportWarnings = exported.warnings
            }.onFailure { error ->
                status = error.message ?: "MIDI export failed"
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
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
                onClearTrack()
                status = "Cleared $activeTrackName"
            },
            enabled = canClearTrack,
        ) {
            Text("Clear Track")
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
            onClick = {
                openLauncher.launch(arrayOf(ExternalFileTypes.SCORE_FORGE_PROJECT_MIME))
            },
        ) {
            Text("Open")
        }

        OutlinedButton(
            onClick = {
                midiImportWarnings = emptyList()
                midiImportLauncher.launch(
                    arrayOf(
                        "audio/midi",
                        "audio/x-midi",
                        "audio/sp-midi",
                        "application/x-midi",
                    )
                )
            },
        ) {
            Text("Import MIDI")
        }

        OutlinedButton(
            onClick = {
                midiExportWarnings = emptyList()
                val safeName = ScoreProjectSnapshot.sanitizeProjectName(projectName)
                    .replace(Regex("[\\/:*?\"<>|]"), "_")
                    .ifBlank { "Untitled" }
                midiExportLauncher.launch("$safeName.mid")
            },
        ) {
            Text("Export MIDI")
        }

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

    if (midiImportWarnings.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { midiImportWarnings = emptyList() },
            title = { Text("MIDI import notes") },
            text = {
                Text(midiImportWarnings.joinToString(separator = "\n\n") { "• $it" })
            },
            confirmButton = {
                TextButton(onClick = { midiImportWarnings = emptyList() }) {
                    Text("OK")
                }
            },
        )
    }

    if (midiExportWarnings.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { midiExportWarnings = emptyList() },
            title = { Text("MIDI export notes") },
            text = {
                Text(midiExportWarnings.joinToString(separator = "\n\n") { "• $it" })
            },
            confirmButton = {
                TextButton(onClick = { midiExportWarnings = emptyList() }) {
                    Text("OK")
                }
            },
        )
    }
}

private fun queryDisplayNameLocal(context: android.content.Context, uri: android.net.Uri): String? =
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
