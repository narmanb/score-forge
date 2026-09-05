package com.scoreforge.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Receives external file-manager VIEW intents and immediately relaunches the request into
 * Score Forge's own task. Some file managers place the exported target activity inside their
 * task even when they include FLAG_ACTIVITY_NEW_TASK, which makes Recents appear to contain a
 * duplicate Score Forge instance. Keeping the external entry point separate lets MainActivity
 * remain the single app task while this short-lived dispatcher disappears from the caller task.
 */
class FileOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            forwardExternalOpen(intent)
        }
        finishWithoutAnimation()
    }

    private fun forwardExternalOpen(source: Intent) {
        if (source.action != Intent.ACTION_VIEW || source.data == null) return

        val uriGrantFlags = source.flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )

        val forwarded = Intent(source).apply {
            setClass(this@FileOpenActivity, MainActivity::class.java)
            flags = uriGrantFlags or
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ROUTED_EXTERNAL_OPEN, true)
        }

        startActivity(forwarded)
    }

    private fun finishWithoutAnimation() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_ROUTED_EXTERNAL_OPEN =
            "com.scoreforge.app.extra.ROUTED_EXTERNAL_OPEN"
    }
}
