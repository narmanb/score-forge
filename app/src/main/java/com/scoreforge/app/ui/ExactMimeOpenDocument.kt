package com.scoreforge.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import com.scoreforge.app.ExternalFileTypes

/**
 * ACTION_OPEN_DOCUMENT contract that uses an exact base MIME type when Android can reliably
 * identify the format. MIDI benefits from that stricter filtering. Score Forge project files use
 * a custom .sfp extension, however, and Android providers commonly report them as a generic type.
 * For .sfp requests use a wildcard base plus the known provider MIME variants, then validate the
 * filename and Score Forge contents after selection.
 */
internal class ExactMimeOpenDocument : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            if (input == ExternalFileTypes.SCORE_FORGE_PROJECT_MIME) {
                type = "*/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf(
                        ExternalFileTypes.SCORE_FORGE_PROJECT_MIME,
                        "application/octet-stream",
                        "text/plain",
                    ),
                )
            } else {
                type = input
            }
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}
