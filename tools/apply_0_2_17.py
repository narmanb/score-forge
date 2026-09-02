from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: str, old: str, new: str) -> None:
    p = ROOT / path
    text = p.read_text()
    count = text.count(old)
    if count == 0:
        if new in text:
            print(f"already applied: {path}")
            return
        raise RuntimeError(f"pattern not found in {path}: {old[:120]!r}")
    if count != 1:
        raise RuntimeError(f"expected one match in {path}, found {count}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))
    print(f"patched {path}")


replace_once(
    "app/build.gradle.kts",
    '        versionCode = 19\n        versionName = "0.2.16"',
    '        versionCode = 20\n        versionName = "0.2.17"',
)

replace_once(
    "app/src/main/AndroidManifest.xml",
    '        android:allowBackup="true"\n        android:label="Score Forge"',
    '        android:allowBackup="true"\n        android:icon="@mipmap/ic_launcher"\n        android:roundIcon="@mipmap/ic_launcher_round"\n        android:label="Score Forge"',
)

replace_once(
    "app/src/main/java/com/scoreforge/app/music/ScoreProjectStorage.kt",
    '''    val timeSignatures: List<ScoreTimeSignature> =\n        tracks.firstOrNull()?.timeSignatures ?: listOf(ScoreTimeSignatures.DEFAULT),\n) {''',
    '''    val timeSignatures: List<ScoreTimeSignature> =\n        tracks.firstOrNull()?.timeSignatures ?: listOf(ScoreTimeSignatures.DEFAULT),\n    val metronomeEnabled: Boolean = false,\n) {''',
)
replace_once(
    "app/src/main/java/com/scoreforge/app/music/ScoreProjectStorage.kt",
    '''        append("BPM\\t").append(snapshot.bpm.coerceIn(30, 300)).append('\\n')\n        snapshot.effectiveTimeSignatures().forEach { signature ->''',
    '''        append("BPM\\t").append(snapshot.bpm.coerceIn(30, 300)).append('\\n')\n        append("METRONOME\\t").append(if (snapshot.metronomeEnabled) 1 else 0).append('\\n')\n        snapshot.effectiveTimeSignatures().forEach { signature ->''',
)
replace_once(
    "app/src/main/java/com/scoreforge/app/music/ScoreProjectStorage.kt",
    '''        var projectName = "Untitled"\n        var bpm = 120\n        var selectedDuration = NoteDuration.QUARTER''',
    '''        var projectName = "Untitled"\n        var bpm = 120\n        var metronomeEnabled = false\n        var selectedDuration = NoteDuration.QUARTER''',
)
replace_once(
    "app/src/main/java/com/scoreforge/app/music/ScoreProjectStorage.kt",
    '''                "BPM" -> parts.getOrNull(1)?.toIntOrNull()?.let { bpm = it.coerceIn(30, 300) }\n                "TIME_SIGNATURE" -> decodeTimeSignature(parts)?.let(timeSignatures::add)''',
    '''                "BPM" -> parts.getOrNull(1)?.toIntOrNull()?.let { bpm = it.coerceIn(30, 300) }\n                "METRONOME" -> metronomeEnabled = parts.getOrNull(1) == "1"\n                "TIME_SIGNATURE" -> decodeTimeSignature(parts)?.let(timeSignatures::add)''',
)
replace_once(
    "app/src/main/java/com/scoreforge/app/music/ScoreProjectStorage.kt",
    '''            projectName = projectName,\n            timeSignatures = ScoreTimeSignatures.normalize(timeSignatures),\n        )''',
    '''            projectName = projectName,\n            timeSignatures = ScoreTimeSignatures.normalize(timeSignatures),\n            metronomeEnabled = metronomeEnabled,\n        )''',
)

replace_once(
    "app/src/main/java/com/scoreforge/app/audio/ScorePlaybackEngine.kt",
    '''import com.scoreforge.app.music.NoteDuration\nimport com.scoreforge.app.music.ScoreArticulations''',
    '''import com.scoreforge.app.music.NoteDuration\nimport com.scoreforge.app.music.MetronomeAccent\nimport com.scoreforge.app.music.ScoreMetronome\nimport com.scoreforge.app.music.ScoreTimeSignature\nimport com.scoreforge.app.music.ScoreTimeSignatures\nimport com.scoreforge.app.music.ScoreArticulations''',
)
replace_once(
    "app/src/main/java/com/scoreforge/app/audio/ScorePlaybackEngine.kt",
    '''        bpm: Int,\n        throughBeat: Float = ScoreTimeline.endBeat(notes),\n        onFinished: () -> Unit = {},''',
    '''        bpm: Int,\n        throughBeat: Float = ScoreTimeline.endBeat(notes),\n        metronomeEnabled: Boolean = false,\n        timeSignatures: List<ScoreTimeSignature> = listOf(ScoreTimeSignatures.DEFAULT),\n        onFinished: () -> Unit = {},''',
)
replace_once(
    "app/src/main/java/com/scoreforge/app/audio/ScorePlaybackEngine.kt",
    '''            bpm = bpm,\n            throughBeat = throughBeat,\n            onFinished = onFinished,''',
    '''            bpm = bpm,\n            throughBeat = throughBeat,\n            metronomeEnabled = metronomeEnabled,\n            timeSignatures = timeSignatures,\n            onFinished = onFinished,''',
)
replace_once(
    "app/src/main/java/com/scoreforge/app/audio/ScorePlaybackEngine.kt",
    '''        bpm: Int,\n        throughBeat: Float = ScoreTracks.endBeat(tracks),\n        onFinished: () -> Unit = {},\n    ) {''',
    '''        bpm: Int,\n        throughBeat: Float = ScoreTracks.endBeat(tracks),\n        metronomeEnabled: Boolean = false,\n        timeSignatures: List<ScoreTimeSignature> = listOf(ScoreTimeSignatures.DEFAULT),\n        onFinished: () -> Unit = {},\n    ) {''',
)
replace_once(
    "app/src/main/java/com/scoreforge/app/audio/ScorePlaybackEngine.kt",
    '''            val rendered = renderBestAvailableTracks(playableTracks, safeBpm, safeThroughBeat)\n            if (myGeneration != generation || rendered.pcm.isEmpty()) {''',
    '''            var rendered = renderBestAvailableTracks(playableTracks, safeBpm, safeThroughBeat)\n            if (metronomeEnabled && rendered.pcm.isNotEmpty()) {\n                rendered = mixMetronome(\n                    rendered = rendered,\n                    bpm = safeBpm,\n                    throughBeat = safeThroughBeat,\n                    timeSignatures = timeSignatures,\n                )\n            }\n            if (myGeneration != generation || rendered.pcm.isEmpty()) {''',
)
replace_once(
    "app/src/main/java/com/scoreforge/app/audio/ScorePlaybackEngine.kt",
    '''    private fun createStaticTrack(pcm: ShortArray, channels: Int): AudioTrack =''',
    '''    private fun mixMetronome(\n        rendered: RenderedAudio,\n        bpm: Int,\n        throughBeat: Float,\n        timeSignatures: List<ScoreTimeSignature>,\n    ): RenderedAudio {\n        if (rendered.pcm.isEmpty()) return rendered\n\n        val mixed = rendered.pcm.copyOf()\n        val channels = rendered.channels.coerceAtLeast(1)\n        val totalFrames = mixed.size / channels\n        val secondsPerBeat = 60f / bpm.coerceIn(30, 300)\n        val clickFrames = (sampleRate * 0.032f).toInt().coerceAtLeast(1)\n\n        ScoreMetronome.clicks(timeSignatures, throughBeat).forEach { click ->\n            val startFrame = (click.beat * secondsPerBeat * sampleRate).toInt()\n            val (frequency, gain) = when (click.accent) {\n                MetronomeAccent.DOWNBEAT -> 1_600.0 to 0.34f\n                MetronomeAccent.GROUP -> 1_250.0 to 0.26f\n                MetronomeAccent.BEAT -> 950.0 to 0.18f\n            }\n\n            for (i in 0 until clickFrames) {\n                val frame = startFrame + i\n                if (frame !in 0 until totalFrames) break\n                val t = i.toDouble() / sampleRate\n                val progress = i.toFloat() / clickFrames\n                val envelope = (1f - progress).coerceIn(0f, 1f).let { it * it }\n                val clickSample = (\n                    sin(2.0 * PI * frequency * t) *\n                        envelope *\n                        gain *\n                        Short.MAX_VALUE\n                    ).toInt()\n\n                repeat(channels) { channel ->\n                    val sampleIndex = frame * channels + channel\n                    mixed[sampleIndex] = (mixed[sampleIndex].toInt() + clickSample)\n                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())\n                        .toShort()\n                }\n            }\n        }\n\n        return RenderedAudio(mixed, rendered.channels)\n    }\n\n    private fun createStaticTrack(pcm: ShortArray, channels: Int): AudioTrack =''',
)

replace_once(
    "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt",
    '''    var bpm by rememberSaveable { mutableIntStateOf(120) }\n    var timeSignatures by remember { mutableStateOf(listOf(ScoreTimeSignatures.DEFAULT)) }\n    var isPlaying by remember { mutableStateOf(false) }''',
    '''    var bpm by rememberSaveable { mutableIntStateOf(120) }\n    var timeSignatures by remember { mutableStateOf(listOf(ScoreTimeSignatures.DEFAULT)) }\n    var metronomeEnabled by rememberSaveable { mutableStateOf(false) }\n    var isPlaying by remember { mutableStateOf(false) }''',
)
replace_once(
    "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt",
    '''            projectName = projectName,\n            timeSignatures = timeSignatures,\n        )''',
    '''            projectName = projectName,\n            timeSignatures = timeSignatures,\n            metronomeEnabled = metronomeEnabled,\n        )''',
)
replace_once(
    "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt",
    '''        staffSharpInput = snapshot.staffSharpInput\n        timeSignatures = snapshot.effectiveTimeSignatures()\n        mixerGestureHistoryRecorded = false''',
    '''        staffSharpInput = snapshot.staffSharpInput\n        timeSignatures = snapshot.effectiveTimeSignatures()\n        metronomeEnabled = snapshot.metronomeEnabled\n        mixerGestureHistoryRecorded = false''',
)
replace_once(
    "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt",
    '''        bpm,\n        timeSignatures,\n        selectedDuration,''',
    '''        bpm,\n        timeSignatures,\n        metronomeEnabled,\n        selectedDuration,''',
)
replace_once(
    "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt",
    '''            tracks = tracks,\n            bpm = bpm,\n            throughBeat = ScoreTracks.endBeat(tracks),\n        ) { isPlaying = false }''',
    '''            tracks = tracks,\n            bpm = bpm,\n            throughBeat = ScoreTracks.endBeat(tracks),\n            metronomeEnabled = metronomeEnabled,\n            timeSignatures = timeSignatures,\n        ) { isPlaying = false }''',
)
replace_once(
    "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt",
    '''                    isPlaying = isPlaying,\n                    canPlay = playableNoteCount > 0 && !liveRecordingActive,\n                    onTempoDown = { bpm = (bpm - 5).coerceAtLeast(30) },''',
    '''                    isPlaying = isPlaying,\n                    canPlay = playableNoteCount > 0 && !liveRecordingActive,\n                    metronomeEnabled = metronomeEnabled,\n                    onToggleMetronome = {\n                        if (isPlaying) stopPlayback()\n                        metronomeEnabled = !metronomeEnabled\n                    },\n                    onTempoDown = { bpm = (bpm - 5).coerceAtLeast(30) },''',
)
replace_once(
    "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt",
    '''    isPlaying: Boolean,\n    canPlay: Boolean,\n    onTempoDown: () -> Unit,''',
    '''    isPlaying: Boolean,\n    canPlay: Boolean,\n    metronomeEnabled: Boolean,\n    onToggleMetronome: () -> Unit,\n    onTempoDown: () -> Unit,''',
)
replace_once(
    "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt",
    '''        ChamferedControlButton(\n            label = "+5",\n            onClick = onTempoUp,\n            enabled = bpm < 300,\n            compact = false,\n        )\n\n        if (isPlaying) Button(onClick = onStop) { Text("Stop") }''',
    '''        ChamferedControlButton(\n            label = "+5",\n            onClick = onTempoUp,\n            enabled = bpm < 300,\n            compact = false,\n        )\n\n        if (metronomeEnabled) {\n            Button(onClick = onToggleMetronome) { Text("Metronome On") }\n        } else {\n            OutlinedButton(onClick = onToggleMetronome) { Text("Metronome Off") }\n        }\n\n        if (isPlaying) Button(onClick = onStop) { Text("Stop") }''',
)

print("0.2.17 patch complete")
