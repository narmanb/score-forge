package com.scoreforge.app.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

data class ImportedSoundFont(
    val displayName: String,
    val localPath: String,
)

data class SavedSoundFontSelection(
    val soundFont: ImportedSoundFont,
    val bank: Int?,
    val program: Int?,
)

object SoundFontRepository {
    private const val PREFS_NAME = "score_forge_soundfont"
    private const val KEY_PATH = "active_path"
    private const val KEY_NAME = "active_name"
    private const val KEY_BANK = "active_bank"
    private const val KEY_PROGRAM = "active_program"
    private const val NO_PRESET = -1

    fun importToAppStorage(context: Context, uri: Uri): Result<ImportedSoundFont> = runCatching {
        val displayName = queryDisplayName(context, uri)
            ?.takeIf { it.isNotBlank() }
            ?: "imported.sf2"

        require(
            displayName.endsWith(".sf2", ignoreCase = true) ||
                displayName.endsWith(".sf3", ignoreCase = true)
        ) {
            "Score Forge supports .sf2 and .sf3 SoundFonts."
        }

        val safeName = displayName
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .take(120)
            .ifBlank { "imported.sf2" }

        val folder = File(context.filesDir, "soundfonts").apply { mkdirs() }
        val destination = uniqueDestination(folder, safeName)

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected SoundFont." }
            destination.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        }

        ImportedSoundFont(
            displayName = destination.name,
            localPath = destination.absolutePath,
        )
    }

    fun saveActiveSelection(
        context: Context,
        soundFont: ImportedSoundFont,
        preset: SoundFontPreset?,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PATH, soundFont.localPath)
            .putString(KEY_NAME, soundFont.displayName)
            .putInt(KEY_BANK, preset?.bank ?: NO_PRESET)
            .putInt(KEY_PROGRAM, preset?.program ?: NO_PRESET)
            .apply()
    }

    fun loadActiveSelection(context: Context): SavedSoundFontSelection? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val path = prefs.getString(KEY_PATH, null)?.takeIf { it.isNotBlank() } ?: return null
        val file = File(path)

        if (!file.isFile) {
            clearActiveSelection(context)
            return null
        }

        if (!file.name.endsWith(".sf2", ignoreCase = true) &&
            !file.name.endsWith(".sf3", ignoreCase = true)
        ) {
            clearActiveSelection(context)
            return null
        }

        val bank = prefs.getInt(KEY_BANK, NO_PRESET).takeIf { it >= 0 }
        val program = prefs.getInt(KEY_PROGRAM, NO_PRESET).takeIf { it >= 0 }

        return SavedSoundFontSelection(
            soundFont = ImportedSoundFont(
                displayName = prefs.getString(KEY_NAME, file.name)
                    ?.takeIf { it.isNotBlank() }
                    ?: file.name,
                localPath = file.absolutePath,
            ),
            bank = bank,
            program = program,
        )
    }

    fun clearActiveSelection(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(
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
    }

    private fun uniqueDestination(folder: File, requestedName: String): File {
        val direct = File(folder, requestedName)
        if (!direct.exists()) return direct

        val dot = requestedName.lastIndexOf('.')
        val stem = if (dot > 0) requestedName.substring(0, dot) else requestedName
        val extension = if (dot > 0) requestedName.substring(dot) else ""

        var index = 2
        while (true) {
            val candidate = File(folder, "$stem ($index)$extension")
            if (!candidate.exists()) return candidate
            index++
        }
    }
}
