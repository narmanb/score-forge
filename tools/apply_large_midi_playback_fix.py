from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


root = Path('.')
playback_path = root / 'app/src/main/java/com/scoreforge/app/audio/ScorePlaybackEngine.kt'
soundfont_path = root / 'app/src/main/java/com/scoreforge/app/audio/SoundFontEngine.kt'
composer_path = root / 'app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt'
build_path = root / 'app/build.gradle.kts'

playback = playback_path.read_text()
soundfont = soundfont_path.read_text()
composer = composer_path.read_text()
build = build_path.read_text()

playback = replace_once(
    playback,
    '''    private data class RenderedAudio(\n        val pcm: ShortArray,\n        val channels: Int,\n    )\n''',
    '''    private data class RenderedAudio(\n        val pcm: ShortArray,\n        val channels: Int,\n    )\n\n    private data class StreamingMidiEvent(\n        val frame: Int,\n        val noteOn: Boolean,\n        val key: Int,\n        val velocity: Int,\n        val channel: Int,\n    )\n\n    private data class StreamingClick(\n        val frame: Int,\n        val frequency: Double,\n        val gain: Float,\n    )\n''',
    'streaming data classes',
)

playback = replace_once(
    playback,
    '''        ScoreTransportBus.begin(startBeat, safeThroughBeat)\n\n        thread(name = "ScoreForgePlayback", isDaemon = true) {\n''',
    '''        ScoreTransportBus.begin(startBeat, safeThroughBeat)\n\n        if (\n            soundFontEngine?.hasSoundFont == true &&\n            PlaybackStreamingPolicy.shouldStream(\n                throughBeat = safeThroughBeat - startBeat,\n                bpm = safeBpm,\n                noteCount = notes.size,\n            )\n        ) {\n            playStreamingSoundFontTracks(\n                playableTracks = playableTracks,\n                bpm = safeBpm,\n                startBeat = startBeat,\n                throughBeat = safeThroughBeat,\n                metronomeEnabled = metronomeEnabled,\n                timeSignatures = timeSignatures,\n                myGeneration = myGeneration,\n                onFinished = onFinished,\n            )\n            return\n        }\n\n        thread(name = "ScoreForgePlayback", isDaemon = true) {\n''',
    'streaming branch',
)

streaming_methods = r'''
    private fun playStreamingSoundFontTracks(
        playableTracks: List<ScoreTrack>,
        bpm: Int,
        startBeat: Float,
        throughBeat: Float,
        metronomeEnabled: Boolean,
        timeSignatures: List<ScoreTimeSignature>,
        myGeneration: Int,
        onFinished: () -> Unit,
    ) {
        val soundFont = soundFontEngine ?: run {
            ScoreTransportBus.stop()
            mainHandler.post(onFinished)
            return
        }
        val secondsPerBeat = 60f / bpm.coerceIn(30, 300)
        val events = buildStreamingMidiEvents(
            tracks = playableTracks,
            startBeat = startBeat,
            throughBeat = throughBeat,
            secondsPerBeat = secondsPerBeat,
        )
        val clicks = if (metronomeEnabled) {
            buildStreamingClicks(
                timeSignatures = timeSignatures,
                startBeat = startBeat,
                throughBeat = throughBeat,
                secondsPerBeat = secondsPerBeat,
            )
        } else {
            emptyList()
        }

        thread(name = "ScoreForgePlaybackStream", isDaemon = true) {
            var streamTrack: AudioTrack? = null
            try {
                if (myGeneration != generation) return@thread
                if (!soundFont.prepareStreamingTracks(playableTracks)) {
                    if (myGeneration == generation) {
                        ScoreTransportBus.stop()
                        mainHandler.post(onFinished)
                    }
                    return@thread
                }

                val track = createStreamingTrack()
                streamTrack = track
                if (myGeneration != generation) return@thread
                activeTrack = track
                track.play()

                val scoreFrames = (
                    ((throughBeat - startBeat).coerceAtLeast(0f) * secondsPerBeat * sampleRate)
                    ).toInt().coerceAtLeast(1)
                val tailFrames = (sampleRate * STREAMING_TAIL_SECONDS).toInt().coerceAtLeast(1)
                val totalFrames = scoreFrames + tailFrames
                var frameCursor = 0
                var eventIndex = 0
                var failed = false

                while (myGeneration == generation && frameCursor < totalFrames) {
                    while (eventIndex < events.size && events[eventIndex].frame <= frameCursor) {
                        val event = events[eventIndex++]
                        if (event.noteOn) {
                            soundFont.noteOn(event.key, event.velocity, event.channel)
                        } else {
                            soundFont.noteOff(event.key, event.channel)
                        }
                    }

                    val nextEventFrame = events.getOrNull(eventIndex)?.frame ?: totalFrames
                    val untilEvent = (nextEventFrame - frameCursor).coerceAtLeast(1)
                    val frames = minOf(
                        STREAMING_BLOCK_FRAMES,
                        totalFrames - frameCursor,
                        untilEvent,
                    )
                    val pcm = soundFont.renderStereo(frames)
                    if (pcm.isEmpty()) {
                        failed = true
                        break
                    }
                    if (clicks.isNotEmpty()) {
                        mixStreamingMetronome(pcm, frameCursor, clicks)
                    }
                    if (!writeStreamingBlock(track, pcm)) {
                        failed = true
                        break
                    }
                    frameCursor += frames

                    val playedFrames = track.playbackHeadPosition.toLong().coerceAtLeast(0L)
                    val beat = startBeat +
                        (playedFrames.toDouble() / sampleRate.toDouble() / secondsPerBeat.toDouble()).toFloat()
                    ScoreTransportBus.progress(beat.coerceAtMost(throughBeat))
                }

                if (myGeneration == generation && !failed) {
                    val drainDeadline = SystemClock.elapsedRealtime() + STREAMING_DRAIN_TIMEOUT_MS
                    while (
                        myGeneration == generation &&
                        track.playbackHeadPosition < totalFrames - 128 &&
                        SystemClock.elapsedRealtime() < drainDeadline
                    ) {
                        val playedFrames = track.playbackHeadPosition.toLong().coerceAtLeast(0L)
                        val beat = startBeat +
                            (playedFrames.toDouble() / sampleRate.toDouble() / secondsPerBeat.toDouble()).toFloat()
                        ScoreTransportBus.progress(beat.coerceAtMost(throughBeat))
                        Thread.sleep(16L)
                    }
                    if (myGeneration == generation) {
                        ScoreTransportBus.finish(throughBeat)
                        mainHandler.post(onFinished)
                    }
                } else if (myGeneration == generation) {
                    ScoreTransportBus.stop()
                    mainHandler.post(onFinished)
                }
            } catch (_: InterruptedException) {
                // Stop/restart invalidates the generation and cleanup happens below.
            } catch (_: IllegalStateException) {
                if (myGeneration == generation) {
                    ScoreTransportBus.stop()
                    mainHandler.post(onFinished)
                }
            } finally {
                soundFont.finishStreamingTracks()
                streamTrack?.let(::releaseTrack)
                if (activeTrack === streamTrack) activeTrack = null
            }
        }
    }

    private fun buildStreamingMidiEvents(
        tracks: List<ScoreTrack>,
        startBeat: Float,
        throughBeat: Float,
        secondsPerBeat: Float,
    ): List<StreamingMidiEvent> = buildList {
        tracks.forEachIndexed trackLoop@{ channel, track ->
            val notes = track.notes
            notes.forEachIndexed noteLoop@{ index, note ->
                if (ScoreTies.isContinuation(notes, index)) return@noteLoop
                val playbackEndBeat = if (ScoreTies.hasValidTie(notes, index)) {
                    ScoreTies.chainEndBeat(notes, index)
                } else {
                    ScoreArticulations.playbackEndBeat(notes, index)
                }.takeIf { it > note.startBeat } ?: (note.startBeat + note.effectiveBeats)

                if (playbackEndBeat <= startBeat || note.startBeat >= throughBeat) return@noteLoop
                val onBeat = maxOf(note.startBeat, startBeat)
                val offBeat = minOf(playbackEndBeat, throughBeat)
                val onFrame = (
                    (onBeat - startBeat).coerceAtLeast(0f) * secondsPerBeat * sampleRate
                    ).toInt().coerceAtLeast(0)
                val offFrame = (
                    (offBeat - startBeat).coerceAtLeast(0f) * secondsPerBeat * sampleRate
                    ).toInt().coerceAtLeast(onFrame + 1)
                add(
                    StreamingMidiEvent(
                        frame = onFrame,
                        noteOn = true,
                        key = note.midiPitch,
                        velocity = ScoreArticulations.playbackVelocity(note),
                        channel = channel.coerceIn(0, 15),
                    )
                )
                add(
                    StreamingMidiEvent(
                        frame = offFrame,
                        noteOn = false,
                        key = note.midiPitch,
                        velocity = 0,
                        channel = channel.coerceIn(0, 15),
                    )
                )
            }
        }
    }.sortedWith(
        compareBy<StreamingMidiEvent> { it.frame }
            .thenBy { if (it.noteOn) 1 else 0 }
            .thenBy { it.channel }
            .thenBy { it.key }
    )

    private fun buildStreamingClicks(
        timeSignatures: List<ScoreTimeSignature>,
        startBeat: Float,
        throughBeat: Float,
        secondsPerBeat: Float,
    ): List<StreamingClick> = ScoreMetronome.clicks(timeSignatures, throughBeat)
        .asSequence()
        .filter { it.beat + 0.001f >= startBeat }
        .map { click ->
            val (frequency, gain) = when (click.accent) {
                MetronomeAccent.DOWNBEAT -> 1_600.0 to 0.34f
                MetronomeAccent.GROUP -> 1_250.0 to 0.26f
                MetronomeAccent.BEAT -> 950.0 to 0.18f
            }
            StreamingClick(
                frame = (
                    (click.beat - startBeat).coerceAtLeast(0f) * secondsPerBeat * sampleRate
                    ).toInt(),
                frequency = frequency,
                gain = gain,
            )
        }
        .toList()

    private fun mixStreamingMetronome(
        pcm: ShortArray,
        chunkStartFrame: Int,
        clicks: List<StreamingClick>,
    ) {
        val chunkFrames = pcm.size / 2
        if (chunkFrames <= 0) return
        val chunkEndFrame = chunkStartFrame + chunkFrames
        val clickFrames = (sampleRate * 0.032f).toInt().coerceAtLeast(1)
        clicks.forEach { click ->
            val clickEnd = click.frame + clickFrames
            if (click.frame >= chunkEndFrame || clickEnd <= chunkStartFrame) return@forEach
            val overlapStart = maxOf(chunkStartFrame, click.frame)
            val overlapEnd = minOf(chunkEndFrame, clickEnd)
            for (absoluteFrame in overlapStart until overlapEnd) {
                val clickIndex = absoluteFrame - click.frame
                val t = clickIndex.toDouble() / sampleRate
                val progress = clickIndex.toFloat() / clickFrames
                val envelope = (1f - progress).coerceIn(0f, 1f).let { it * it }
                val clickSample = (
                    sin(2.0 * PI * click.frequency * t) *
                        envelope * click.gain * Short.MAX_VALUE
                    ).toInt()
                val localFrame = absoluteFrame - chunkStartFrame
                repeat(2) { channel ->
                    val sampleIndex = localFrame * 2 + channel
                    pcm[sampleIndex] = (pcm[sampleIndex].toInt() + clickSample)
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                }
            }
        }
    }

    private fun createStreamingTrack(): AudioTrack {
        val channelMask = AudioFormat.CHANNEL_OUT_STEREO
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBytes * 2)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
    }

    private fun writeStreamingBlock(track: AudioTrack, pcm: ShortArray): Boolean {
        var offset = 0
        while (offset < pcm.size) {
            val written = track.write(
                pcm,
                offset,
                pcm.size - offset,
                AudioTrack.WRITE_BLOCKING,
            )
            if (written <= 0) return false
            offset += written
        }
        return true
    }

'''

playback = replace_once(
    playback,
    '''    private fun mixMetronome(\n''',
    streaming_methods + '''    private fun mixMetronome(\n''',
    'streaming methods',
)

playback = replace_once(
    playback,
    '''class ScorePlaybackEngine {\n    private val sampleRate = 44_100\n''',
    '''class ScorePlaybackEngine {\n    private val sampleRate = 44_100\n\n    companion object {\n        private const val STREAMING_BLOCK_FRAMES = 2_048\n        private const val STREAMING_TAIL_SECONDS = 0.45f\n        private const val STREAMING_DRAIN_TIMEOUT_MS = 2_000L\n    }\n''',
    'streaming constants',
)

soundfont = replace_once(
    soundfont,
    '''    fun renderStereo(frames: Int): ShortArray = synchronized(lock) {\n        if (handle == 0L || soundFontId < 0 || frames <= 0) {\n            ShortArray(0)\n        } else {\n            NativeFluidSynth.renderStereo(handle, frames)\n        }\n    }\n''',
    '''    fun renderStereo(frames: Int): ShortArray = synchronized(lock) {\n        if (handle == 0L || soundFontId < 0 || frames <= 0) {\n            ShortArray(0)\n        } else {\n            NativeFluidSynth.renderStereo(handle, frames)\n        }\n    }\n\n    /** Configure FluidSynth channels for incremental/streaming score playback. */\n    fun prepareStreamingTracks(tracks: List<ScoreTrack>): Boolean = synchronized(lock) {\n        if (handle == 0L || soundFontId < 0) return@synchronized false\n        val playable = ScoreTracks.audibleTracks(tracks).take(ScoreTracks.MAX_TRACKS)\n        if (playable.none { it.notes.isNotEmpty() }) return@synchronized false\n        val fallbackPreset = activePreset ?: loadedPresets.firstOrNull()\n\n        repeat(16) { NativeFluidSynth.allNotesOff(handle, it) }\n        playable.forEachIndexed { channel, track ->\n            if (track.notes.isEmpty()) return@forEachIndexed\n            val requestedPreset = if (track.presetBank != null && track.presetProgram != null) {\n                loadedPresets.firstOrNull {\n                    it.bank == track.presetBank && it.program == track.presetProgram\n                }\n            } else {\n                null\n            }\n            (requestedPreset ?: fallbackPreset)?.let {\n                selectPresetOnChannelLocked(it, channel.coerceIn(0, 15))\n            }\n            setChannelMixerLocked(channel.coerceIn(0, 15), track.volume, track.pan)\n        }\n        true\n    }\n\n    /** End streaming playback without changing the user's selected library preset. */\n    fun finishStreamingTracks() = synchronized(lock) {\n        if (handle == 0L) return@synchronized\n        repeat(16) { NativeFluidSynth.allNotesOff(handle, it) }\n        activePreset?.let { selectPresetOnChannelLocked(it, channel = 0) }\n        setChannelMixerLocked(\n            channel = 0,\n            volume = ScoreTrack.DEFAULT_VOLUME,\n            pan = ScoreTrack.CENTER_PAN,\n        )\n    }\n''',
    'soundfont streaming channel setup',
)

composer = replace_once(
    composer,
    '''        applyProjectSnapshot(snapshot, clearHistory = true)\n        chordMode = StepChordMode.OFF\n        pianoEntryMode = PianoEntryMode.STEP\n''',
    '''        applyProjectSnapshot(snapshot, clearHistory = true)\n        ScoreTransportBus.seek(0f)\n        chordMode = StepChordMode.OFF\n        pianoEntryMode = PianoEntryMode.STEP\n''',
    'reset transport when opening/importing project',
)

build = replace_once(build, 'versionCode = 34', 'versionCode = 35', 'version code')
build = replace_once(build, 'versionName = "0.2.31"', 'versionName = "0.2.32"', 'version name')

playback_path.write_text(playback)
soundfont_path.write_text(soundfont)
composer_path.write_text(composer)
build_path.write_text(build)

policy = root / 'app/src/main/java/com/scoreforge/app/audio/PlaybackStreamingPolicy.kt'
policy.write_text('''package com.scoreforge.app.audio\n\n/** Selects bounded-memory streaming before static PCM rendering becomes expensive. */\ninternal object PlaybackStreamingPolicy {\n    private const val DURATION_THRESHOLD_SECONDS = 45.0\n    private const val NOTE_THRESHOLD = 1_500\n\n    fun shouldStream(throughBeat: Float, bpm: Int, noteCount: Int): Boolean {\n        val safeBpm = bpm.coerceIn(30, 300)\n        val estimatedSeconds = throughBeat.coerceAtLeast(0f) * (60.0 / safeBpm.toDouble())\n        return estimatedSeconds >= DURATION_THRESHOLD_SECONDS || noteCount >= NOTE_THRESHOLD\n    }\n}\n''')

test = root / 'app/src/test/java/com/scoreforge/app/audio/PlaybackStreamingPolicyTest.kt'
test.parent.mkdir(parents=True, exist_ok=True)
test.write_text('''package com.scoreforge.app.audio\n\nimport org.junit.Assert.assertFalse\nimport org.junit.Assert.assertTrue\nimport org.junit.Test\n\nclass PlaybackStreamingPolicyTest {\n    @Test\n    fun shortSmallScoreKeepsStaticPath() {\n        assertFalse(PlaybackStreamingPolicy.shouldStream(16f, 120, 64))\n    }\n\n    @Test\n    fun longMidiUsesStreamingPath() {\n        assertTrue(PlaybackStreamingPolicy.shouldStream(989f, 120, 8979))\n    }\n\n    @Test\n    fun denseScoreStreamsEvenWhenShort() {\n        assertTrue(PlaybackStreamingPolicy.shouldStream(24f, 120, 2000))\n    }\n}\n''')
