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
        return if (hasRequiredExtension(trimmed, extension)) trimmed else trimmed + extension
    }

    fun hasRequiredExtension(displayName: String, requiredExtension: String): Boolean {
        val extension = normalizedExtension(requiredExtension)
        return displayName.trim().endsWith(extension, ignoreCase = true)
    }

    /**
     * Ensures a newly-created SAF document really keeps the required extension even if the user
     * deletes it in Android's Create Document filename field. Some providers return a non-null URI
     * from renameDocument() without actually changing DISPLAY_NAME, so every rename/update attempt
     * is verified by querying the provider again before Score Forge writes any contents.
     */
    fun ensure(context: Context, uri: Uri, requiredExtension: String): Uri {
        val extension = normalizedExtension(requiredExtension)
        val originalName = queryCreatedDocumentDisplayName(context, uri)
            ?: throw IllegalStateException(
                "Could not verify the selected filename. Save again and leave $extension at the end of the name."
            )
        if (hasRequiredExtension(originalName, extension)) return uri

        val correctedName = correctedName(originalName, extension)
        val resolver = context.contentResolver

        val renamedUri = runCatching {
            DocumentsContract.renameDocument(resolver, uri, correctedName)
        }.getOrNull()
        val candidate = renamedUri ?: uri
        if (queryCreatedDocumentDisplayName(context, candidate)
                ?.let { hasRequiredExtension(it, extension) } == true
        ) {
            return candidate
        }

        runCatching {
            val values = ContentValues().apply {
                put(OpenableColumns.DISPLAY_NAME, correctedName)
            }
            resolver.update(candidate, values, null, null)
        }

        if (queryCreatedDocumentDisplayName(context, candidate)
                ?.let { hasRequiredExtension(it, extension) } == true
        ) {
            return candidate
        }

        throw IllegalStateException(
            "Android would not keep the required $extension filename. Save again and leave $extension at the end of the name."
        )
    }

    private fun normalizedExtension(value: String): String =
        value.trim().let { if (it.startsWith('.')) it else ".$it" }
}

private fun queryCreatedDocumentDisplayName(context: Context, uri: Uri): String? =
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
