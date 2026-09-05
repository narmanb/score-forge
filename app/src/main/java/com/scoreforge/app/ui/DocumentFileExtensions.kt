package com.scoreforge.app.ui

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns

internal object DocumentFileExtensions {
    fun correctedName(displayName: String, requiredExtension: String): String {
        val extension = normalizedExtension(requiredExtension)
        val trimmed = displayName.trim().ifBlank { "Untitled" }
        return if (trimmed.endsWith(extension, ignoreCase = true)) trimmed else trimmed + extension
    }

    /**
     * Ensures a newly-created SAF document keeps the required extension even if the user deletes it
     * in Android's Create Document filename field. Providers differ in rename support, so try the
     * standard DocumentsContract rename first and then a display-name update. If neither works,
     * fail before writing instead of silently creating a file that is difficult to reopen/share.
     */
    fun ensure(context: Context, uri: Uri, requiredExtension: String): Uri {
        val extension = normalizedExtension(requiredExtension)
        val originalName = queryDisplayName(context, uri) ?: return uri
        if (originalName.endsWith(extension, ignoreCase = true)) return uri

        val correctedName = correctedName(originalName, extension)
        val resolver = context.contentResolver

        val renamedUri = runCatching {
            DocumentsContract.renameDocument(resolver, uri, correctedName)
        }.getOrNull()
        if (renamedUri != null) return renamedUri

        val updatedRows = runCatching {
            val values = ContentValues().apply {
                put(OpenableColumns.DISPLAY_NAME, correctedName)
            }
            resolver.update(uri, values, null, null)
        }.getOrDefault(0)
        if (updatedRows > 0) return uri

        throw IllegalStateException(
            "Could not preserve the required $extension filename. Save again and leave $extension at the end of the name."
        )
    }

    private fun normalizedExtension(value: String): String =
        value.trim().let { if (it.startsWith('.')) it else ".$it" }
}

internal fun queryDisplayName(context: Context, uri: Uri): String? =
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
