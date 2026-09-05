package com.scoreforge.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/**
 * ACTION_OPEN_DOCUMENT contract that puts the requested MIME type directly in Intent.type.
 * AndroidX OpenDocument uses */* as the base type and sends requested types through
 * EXTRA_MIME_TYPES; some vendor pickers treat that extra loosely and leave unrelated files
 * selectable. Using the exact base type gives those pickers a strict filter to apply.
 */
internal class ExactMimeOpenDocument : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}
