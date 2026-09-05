package com.scoreforge.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.scoreforge.app.ExternalFileKind
import com.scoreforge.app.ExternalFileTypes
import com.scoreforge.app.ExternalOpenRequest
import com.scoreforge.app.music.MidiImporter
import com.scoreforge.app.music.ScoreProjectCodec
import com.scoreforge.app.music.ScoreProjectSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ExternalOpenOutcome(
    val snapshot: ScoreProjectSnapshot,
    val dialogTitle: String? = null,
    val dialogMessage: String? = null,
)

@Composable
fun ExternalOpenHandler(
    request: ExternalOpenRequest?,
    onOpenProject: (ScoreProjectSnapshot) -> Unit,
    onConsumed: (ExternalOpenRequest) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(request?.requestId) {
        val pending = request ?: return@LaunchedEffect
        try {
            val outcome = withContext(Dispatchers.IO) {
                openExternalFile(context, pending)
            }
            onOpenProject(outcome.snapshot)
            dialogTitle = outcome.dialogTitle
            dialogMessage = outcome.dialogMessage
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            dialogTitle = "Could not open file"
            dialogMessage = error.message ?: "The selected file is not supported by Score Forge."
        } finally {
            onConsumed(pending)
        }
    }

    val message = dialogMessage
    if (message != null) {
        AlertDialog(
            onDismissRequest = {
                dialogTitle = null
                dialogMessage = null
            },
            title = { Text(dialogTitle ?: "Score Forge") },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialogTitle = null
                        dialogMessage = null
                    },
                ) {
                    Text("OK")
                }
            },
        )
    }
}

private fun openExternalFile(
    context: Context,
    request: ExternalOpenRequest,
): ExternalOpenOutcome {
    val displayName = queryDisplayName(context, request.uri)
    val mimeType = request.mimeType ?: context.contentResolver.getType(request.uri)
    return when (ExternalFileTypes.classify(displayName, mimeType)) {
        ExternalFileKind.SCORE_FORGE_PROJECT -> openScoreForgeProject(context, request.uri, displayName)
        ExternalFileKind.MIDI -> importMidi(context, request.uri, displayName)
        ExternalFileKind.UNKNOWN -> error(
            "That file is not a supported Score Forge project or MIDI file."
        )
    }
}

private fun openScoreForgeProject(
    context: Context,
    uri: Uri,
    displayName: String?,
): ExternalOpenOutcome {
    val raw = context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Could not open the selected file." }
        input.bufferedReader().use { it.readText() }
    }
    var snapshot = requireNotNull(ScoreProjectCodec.decode(raw)) {
        "That file is not a supported Score Forge project."
    }
    if (snapshot.projectName == "Untitled") {
        val inferredName = displayName
            ?.removeSuffix(".sfp")
            ?.trim()
            .orEmpty()
        if (inferredName.isNotBlank()) {
            snapshot = snapshot.copy(
                projectName = ScoreProjectSnapshot.sanitizeProjectName(inferredName)
            )
        }
    }
    return ExternalOpenOutcome(snapshot = snapshot)
}

private fun importMidi(
    context: Context,
    uri: Uri,
    displayName: String?,
): ExternalOpenOutcome {
    val projectName = displayName.orEmpty()
        .replace(Regex("(?i)\\.(mid|midi)$"), "")
        .trim()
        .ifBlank { "Imported MIDI" }
    val bytes = context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Could not open the selected MIDI file." }
        input.readBytes()
    }
    val imported = MidiImporter.import(bytes, projectName)
    val message = imported.warnings
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "\n\n") { "• $it" }
    return ExternalOpenOutcome(
        snapshot = imported.snapshot,
        dialogTitle = if (message != null) "MIDI import notes" else null,
        dialogMessage = message,
    )
}

private fun queryDisplayName(context: Context, uri: Uri): String? =
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
