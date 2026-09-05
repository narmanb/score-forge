package com.scoreforge.app

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.scoreforge.app.ui.ScoreForgeComposerScreen

class MainActivity : ComponentActivity() {
    private var externalOpenRequest by mutableStateOf<ExternalOpenRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        externalOpenRequest = if (savedInstanceState == null) {
            intent.toExternalOpenRequestOrNull()
        } else {
            savedInstanceState.restoreExternalOpenRequest()
        }
        setContent {
            ScoreForgeComposerScreen(
                externalOpenRequest = externalOpenRequest,
                onExternalOpenConsumed = { request ->
                    if (externalOpenRequest?.requestId == request.requestId) {
                        externalOpenRequest = null
                    }
                },
            )
        }
        requestImmersiveMode()
        if (savedInstanceState == null) {
            maybeShowExternalOpenDiagnostics(intent, entryPoint = "onCreate")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalOpenRequest = intent.toExternalOpenRequestOrNull()
        requestImmersiveMode()
        maybeShowExternalOpenDiagnostics(intent, entryPoint = "onNewIntent")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        externalOpenRequest?.let { request ->
            outState.putString(STATE_EXTERNAL_URI, request.uri.toString())
            outState.putString(STATE_EXTERNAL_MIME, request.mimeType)
            outState.putLong(STATE_EXTERNAL_REQUEST_ID, request.requestId)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        requestImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) requestImmersiveMode()
    }

    private fun maybeShowExternalOpenDiagnostics(intent: Intent, entryPoint: String) {
        if (intent.action != Intent.ACTION_VIEW || intent.data == null) return
        window.decorView.post {
            if (!isFinishing && !isDestroyed) {
                showExternalOpenDiagnostics(intent, entryPoint)
            }
        }
    }

    private fun showExternalOpenDiagnostics(intent: Intent, entryPoint: String) {
        val report = buildExternalOpenDiagnostics(intent, entryPoint)
        AlertDialog.Builder(this)
            .setTitle("External-open diagnostics")
            .setMessage(report)
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Score Forge external-open diagnostics", report))
                Toast.makeText(this, "Diagnostics copied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun buildExternalOpenDiagnostics(intent: Intent, entryPoint: String): String {
        val flagNames = listOf(
            Intent.FLAG_ACTIVITY_NEW_TASK to "NEW_TASK",
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK to "MULTIPLE_TASK",
            Intent.FLAG_ACTIVITY_NEW_DOCUMENT to "NEW_DOCUMENT",
            Intent.FLAG_ACTIVITY_CLEAR_TOP to "CLEAR_TOP",
            Intent.FLAG_ACTIVITY_SINGLE_TOP to "SINGLE_TOP",
            Intent.FLAG_ACTIVITY_CLEAR_TASK to "CLEAR_TASK",
            Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS to "RETAIN_IN_RECENTS",
            Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP to "PREVIOUS_IS_TOP",
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT to "REORDER_TO_FRONT",
            Intent.FLAG_ACTIVITY_NO_HISTORY to "NO_HISTORY",
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS to "EXCLUDE_FROM_RECENTS",
        ).filter { (flag, _) -> intent.flags and flag != 0 }
            .joinToString(separator = ", ") { (_, name) -> name }
            .ifBlank { "none of the tracked activity flags" }

        val appTaskLines = runCatching {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.appTasks.mapIndexed { index, appTask ->
                val info = appTask.taskInfo
                val baseFlags = info.baseIntent?.flags ?: 0
                "  [$index] taskId=${info.taskId}, activities=${info.numActivities}, " +
                    "base=${info.baseActivity?.flattenToShortString()}, " +
                    "top=${info.topActivity?.flattenToShortString()}, " +
                    "baseIntentFlags=0x${baseFlags.toUInt().toString(16)}"
            }
        }.getOrElse { error ->
            listOf("  unavailable: ${error::class.java.simpleName}: ${error.message}")
        }

        return buildString {
            appendLine("Score Forge 0.2.39")
            appendLine("entryPoint=$entryPoint")
            appendLine("routedByFileOpenActivity=${intent.getBooleanExtra(FileOpenActivity.EXTRA_ROUTED_EXTERNAL_OPEN, false)}")
            appendLine("taskId=$taskId")
            appendLine("isTaskRoot=$isTaskRoot")
            appendLine("component=${componentName.flattenToShortString()}")
            appendLine("action=${intent.action}")
            appendLine("mime=${intent.type}")
            appendLine("uriScheme=${intent.data?.scheme}")
            appendLine("flags=0x${intent.flags.toUInt().toString(16)}")
            appendLine("decodedFlags=$flagNames")
            appendLine("referrer=$referrer")
            appendLine("callingPackage=$callingPackage")
            appendLine("categories=${intent.categories?.joinToString() ?: "none"}")
            appendLine("appTasks=${appTaskLines.size}")
            appTaskLines.forEach(::appendLine)
        }.trimEnd()
    }

    private fun requestImmersiveMode() {
        window.decorView.post {
            runCatching { hideSystemBars() }
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.decorView.windowInsetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun Intent.toExternalOpenRequestOrNull(): ExternalOpenRequest? {
        if (action != Intent.ACTION_VIEW) return null
        val uri = data ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "content" && scheme != "file") return null
        return ExternalOpenRequest(
            uri = uri,
            mimeType = type,
            requestId = SystemClock.elapsedRealtimeNanos(),
        )
    }

    private fun Bundle.restoreExternalOpenRequest(): ExternalOpenRequest? {
        val uriText = getString(STATE_EXTERNAL_URI) ?: return null
        val requestId = getLong(STATE_EXTERNAL_REQUEST_ID, -1L)
        if (requestId < 0L) return null
        return ExternalOpenRequest(
            uri = Uri.parse(uriText),
            mimeType = getString(STATE_EXTERNAL_MIME),
            requestId = requestId,
        )
    }

    companion object {
        private const val STATE_EXTERNAL_URI = "score_forge_external_uri"
        private const val STATE_EXTERNAL_MIME = "score_forge_external_mime"
        private const val STATE_EXTERNAL_REQUEST_ID = "score_forge_external_request_id"
    }
}
