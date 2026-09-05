package com.scoreforge.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import com.scoreforge.app.MainActivity
import com.scoreforge.app.R
import com.scoreforge.app.music.ScoreTempos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Foreground media service for score playback.
 *
 * Audio rendering remains in [ScoreForgeAudioSession]'s process-lifetime engine. The service gives
 * that engine foreground priority, media/lock-screen controls, audio-focus handling, and a partial
 * wake lock so playback can continue reliably with the app backgrounded or the screen off.
 */
class ScorePlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager
    private lateinit var mediaSession: MediaSession
    private lateinit var audioFocusRequest: AudioFocusRequest
    private var wakeLock: PowerManager.WakeLock? = null
    private var foregroundStarted = false
    private var noisyReceiverRegistered = false
    private var resumeAfterTransientFocusLoss = false
    private var lastPlayingState: Boolean? = null
    private var lastSessionUpdateMs = 0L

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeAfterTransientFocusLoss) {
                    resumeAfterTransientFocusLoss = false
                    beginPlayback(requestFocus = false)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (ScoreTransportBus.state.value.isPlaying) {
                    resumeAfterTransientFocusLoss = true
                    pausePlayback(abandonFocus = false)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeAfterTransientFocusLoss = false
                pausePlayback(abandonFocus = false)
            }
        }
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                resumeAfterTransientFocusLoss = false
                pausePlayback(abandonFocus = true)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ScoreForgeAudioSession.setServiceRunning(true)
        notificationManager = getSystemService(NotificationManager::class.java)
        audioManager = getSystemService(AudioManager::class.java)
        createNotificationChannel()
        createAudioFocusRequest()
        createMediaSession()
        registerNoisyReceiver()

        scope.launch {
            ScoreTransportBus.state.collect { state ->
                val now = SystemClock.elapsedRealtime()
                val playingChanged = lastPlayingState != state.isPlaying
                if (playingChanged || now - lastSessionUpdateMs >= 500L) {
                    updateMediaSession(state)
                    lastSessionUpdateMs = now
                }
                if (playingChanged && foregroundStarted) {
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
                }
                lastPlayingState = state.isPlaying
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_PLAY) {
            ACTION_PLAY -> beginPlayback(requestFocus = true)
            ACTION_PAUSE -> pausePlayback(abandonFocus = true)
            ACTION_TOGGLE -> {
                if (ScoreTransportBus.state.value.isPlaying) {
                    pausePlayback(abandonFocus = true)
                } else {
                    beginPlayback(requestFocus = true)
                }
            }
            ACTION_STOP -> stopPlaybackAndService()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Deliberately keep playing. The foreground service owns playback, not the Activity task.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        ScoreForgeAudioSession.setServiceRunning(false)
        ScoreForgeAudioSession.stopFromService(clearRequest = false)
        releaseWakeLock()
        abandonAudioFocus()
        unregisterNoisyReceiver()
        mediaSession.isActive = false
        mediaSession.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun beginPlayback(requestFocus: Boolean) {
        val request = ScoreForgeAudioSession.currentRequest()
        if (request == null) {
            stopPlaybackAndService()
            return
        }

        ensureForeground()
        mediaSession.isActive = true
        updateMetadata(request)

        if (requestFocus && !requestAudioFocus()) {
            ScoreForgeAudioSession.pauseFromService()
            refreshNotification()
            return
        }

        acquireWakeLock()
        val started = ScoreForgeAudioSession.playFromService {
            handleNaturalFinish()
        }
        if (!started) {
            stopPlaybackAndService()
            return
        }
        refreshNotification()
    }

    private fun pausePlayback(abandonFocus: Boolean) {
        ScoreForgeAudioSession.pauseFromService()
        releaseWakeLock()
        if (abandonFocus) abandonAudioFocus()
        refreshNotification()
    }

    private fun stopPlaybackAndService() {
        resumeAfterTransientFocusLoss = false
        ScoreForgeAudioSession.stopFromService(clearRequest = true)
        releaseWakeLock()
        abandonAudioFocus()
        mediaSession.isActive = false
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        stopSelf()
    }

    private fun handleNaturalFinish() {
        ScoreForgeAudioSession.clearFinishedRequest()
        releaseWakeLock()
        abandonAudioFocus()
        updateMediaSession(ScoreTransportBus.state.value)
        mediaSession.isActive = false
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        stopSelf()
    }

    private fun ensureForeground() {
        if (foregroundStarted) return
        val initial = buildNotification(ScoreTransportBus.state.value)
        startForeground(NOTIFICATION_ID, initial)
        foregroundStarted = true
    }

    private fun refreshNotification() {
        val state = ScoreTransportBus.state.value
        updateMediaSession(state)
        if (foregroundStarted) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
        }
        lastPlayingState = state.isPlaying
        lastSessionUpdateMs = SystemClock.elapsedRealtime()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Score playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Playback controls for Score Forge compositions"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createMediaSession() {
        mediaSession = MediaSession(this, "ScoreForgePlayback").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    beginPlayback(requestFocus = true)
                }

                override fun onPause() {
                    pausePlayback(abandonFocus = true)
                }

                override fun onStop() {
                    stopPlaybackAndService()
                }
            })
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
        }
    }

    private fun updateMetadata(request: ScoreForgeAudioSession.PlaybackRequest) {
        val durationMs = (
            ScoreTempos.secondsAtBeat(request.tempoChanges, request.throughBeat) * 1000.0
            ).toLong().coerceAtLeast(0L)
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, request.projectName.ifBlank { "Untitled" })
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Score Forge")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
                .build()
        )
    }

    private fun updateMediaSession(state: ScoreTransportState) {
        val request = ScoreForgeAudioSession.currentRequest()
        val positionMs = if (request != null) {
            (ScoreTempos.secondsAtBeat(request.tempoChanges, state.beat) * 1000.0)
                .toLong()
                .coerceAtLeast(0L)
        } else {
            0L
        }
        val playbackState = when {
            state.isPlaying -> PlaybackState.STATE_PLAYING
            request != null -> PlaybackState.STATE_PAUSED
            else -> PlaybackState.STATE_STOPPED
        }
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_STOP
                )
                .setState(
                    playbackState,
                    positionMs,
                    if (state.isPlaying) 1f else 0f,
                    SystemClock.elapsedRealtime(),
                )
                .build()
        )
    }

    private fun buildNotification(state: ScoreTransportState): Notification {
        val request = ScoreForgeAudioSession.currentRequest()
        val isPlaying = state.isPlaying
        val contentIntent = PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggleIntent = PendingIntent.getService(
            this,
            101,
            Intent(this, ScorePlaybackService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            102,
            Intent(this, ScorePlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(request?.projectName?.ifBlank { "Untitled" } ?: "Score Forge")
            .setContentText(if (isPlaying) "Playing in Score Forge" else "Playback paused")
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(
                Notification.Action.Builder(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Play",
                    toggleIntent,
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    stopIntent,
                ).build()
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .build()
    }

    private fun createAudioFocusRequest() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(audioFocusListener)
            .build()
    }

    private fun requestAudioFocus(): Boolean =
        audioManager.requestAudioFocus(audioFocusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    private fun abandonAudioFocus() {
        runCatching { audioManager.abandonAudioFocusRequest(audioFocusRequest) }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ScoreForge:Playback",
        ).apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        wakeLock = null
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(becomingNoisyReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(becomingNoisyReceiver, filter)
        }
        noisyReceiverRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        runCatching { unregisterReceiver(becomingNoisyReceiver) }
        noisyReceiverRegistered = false
    }

    companion object {
        private const val CHANNEL_ID = "score_forge_playback"
        private const val NOTIFICATION_ID = 24043
        private const val ACTION_PLAY = "com.scoreforge.app.action.PLAY"
        private const val ACTION_PAUSE = "com.scoreforge.app.action.PAUSE"
        private const val ACTION_TOGGLE = "com.scoreforge.app.action.TOGGLE_PLAYBACK"
        private const val ACTION_STOP = "com.scoreforge.app.action.STOP_PLAYBACK"

        fun requestPlay(context: Context) {
            context.startForegroundService(
                Intent(context, ScorePlaybackService::class.java).setAction(ACTION_PLAY)
            )
        }

        fun requestPause(context: Context) {
            context.startService(
                Intent(context, ScorePlaybackService::class.java).setAction(ACTION_PAUSE)
            )
        }

        fun requestStop(context: Context) {
            context.startService(
                Intent(context, ScorePlaybackService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
