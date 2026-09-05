package com.scoreforge.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalOpenRequest = intent.toExternalOpenRequestOrNull()
        requestImmersiveMode()
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
