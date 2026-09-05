package com.scoreforge.app.audio

import android.content.Context
import com.scoreforge.app.music.ScoreTempoChange
import com.scoreforge.app.music.ScoreTimeSignature
import com.scoreforge.app.music.ScoreTrack

/**
 * Process-lifetime owner for Score Forge's score-playback synth.
 *
 * The composer can be recreated by rotation, task switching, or Android while a foreground media
 * service is still playing. Keeping the playback engine and its FluidSynth instance here prevents
 * an Activity recreation from tearing down audio that legitimately belongs to the app process.
 */
object ScoreForgeAudioSession {
    data class PlaybackRequest(
        val tracks: List<ScoreTrack>,
        val bpm: Int,
        val tempoChanges: List<ScoreTempoChange>,
        val throughBeat: Float,
        val metronomeEnabled: Boolean,
        val timeSignatures: List<ScoreTimeSignature>,
        val projectName: String,
    ) {
        fun frozen(): PlaybackRequest = copy(
            tracks = tracks.map { it.copy(events = it.events.toList()) },
            tempoChanges = tempoChanges.toList(),
            timeSignatures = timeSignatures.toList(),
        )
    }

    val soundFontEngine: SoundFontEngine? by lazy { SoundFontEngine.createOrNull() }

    val playbackEngine: ScorePlaybackEngine by lazy {
        ScorePlaybackEngine().also { it.setSoundFontEngine(soundFontEngine) }
    }

    @Volatile
    private var playbackRequest: PlaybackRequest? = null

    @Volatile
    private var serviceRunning: Boolean = false

    fun startPlayback(context: Context, request: PlaybackRequest) {
        playbackRequest = request.frozen()
        // Force initialization while the UI is foregrounded, before the service begins rendering.
        playbackEngine
        ScorePlaybackService.requestPlay(context.applicationContext)
    }

    fun stopPlayback(context: Context) {
        playbackEngine.stop()
        playbackRequest = null
        if (serviceRunning) ScorePlaybackService.requestStop(context.applicationContext)
    }

    fun pausePlayback(context: Context) {
        playbackEngine.stop()
        if (serviceRunning) ScorePlaybackService.requestPause(context.applicationContext)
    }

    internal fun playFromService(onFinished: () -> Unit): Boolean {
        val request = playbackRequest ?: return false
        playbackEngine.playTracks(
            tracks = request.tracks,
            bpm = request.bpm,
            tempoChanges = request.tempoChanges,
            throughBeat = request.throughBeat,
            metronomeEnabled = request.metronomeEnabled,
            timeSignatures = request.timeSignatures,
            onFinished = onFinished,
        )
        return true
    }

    internal fun pauseFromService() {
        playbackEngine.stop()
    }

    internal fun stopFromService(clearRequest: Boolean) {
        playbackEngine.stop()
        if (clearRequest) playbackRequest = null
    }

    internal fun currentRequest(): PlaybackRequest? = playbackRequest

    internal fun clearFinishedRequest() {
        playbackRequest = null
    }

    internal fun setServiceRunning(running: Boolean) {
        serviceRunning = running
    }
}
