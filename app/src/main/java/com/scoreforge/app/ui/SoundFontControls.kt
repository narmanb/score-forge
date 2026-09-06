package com.scoreforge.app.ui

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.scoreforge.app.audio.ImportedSoundFont
import com.scoreforge.app.audio.LiveInstrumentBus
import com.scoreforge.app.audio.SoundFontEngine
import com.scoreforge.app.audio.SoundFontRepository
import com.scoreforge.app.audio.SoundFontPreset
import kotlin.concurrent.thread

@Composable
fun SoundFontControls(
    engine: SoundFontEngine?,
    playbackActive: Boolean = false,
    requestedPresetBank: Int? = null,
    requestedPresetProgram: Int? = null,
    onSoundFontLoaded: (ImportedSoundFont, SoundFontPreset?) -> Unit = { _, _ -> },
    onPresetSelected: (SoundFontPreset) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var currentSoundFont by remember { mutableStateOf<ImportedSoundFont?>(null) }
    var presets by remember { mutableStateOf<List<SoundFontPreset>>(emptyList()) }
    var presetIndex by remember { mutableIntStateOf(-1) }
    var presetMenuExpanded by remember { mutableStateOf(false) }
    var libraryMenuExpanded by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(
            if (engine == null) "Native SoundFont engine unavailable" else "Loading instruments…"
        )
    }
    var isLoading by remember { mutableStateOf(false) }

    fun publishLoadedSoundFont(
        soundFont: ImportedSoundFont,
        discoveredPresets: List<SoundFontPreset>,
        discoveredIndex: Int,
        selectedPreset: SoundFontPreset?,
        restored: Boolean,
    ) {
        currentSoundFont = soundFont
        presets = discoveredPresets
        presetIndex = discoveredIndex
        presetMenuExpanded = false
        libraryMenuExpanded = false
        status = when {
            restored && discoveredPresets.isNotEmpty() ->
                "${discoveredPresets.size} instruments ready"
            restored -> "Default program ready"
            discoveredPresets.isNotEmpty() -> "${discoveredPresets.size} instruments ready"
            else -> "Default program ready"
        }

        SoundFontRepository.saveActiveSelection(context, soundFont, selectedPreset)
        LiveInstrumentBus.loadSoundFont(soundFont, selectedPreset)
        onSoundFontLoaded(soundFont, selectedPreset)
    }

    fun loadSoundFont(
        soundFont: ImportedSoundFont,
        preferredBank: Int? = null,
        preferredProgram: Int? = null,
        restored: Boolean = false,
    ) {
        val activeEngine = engine ?: return
        isLoading = true
        presetMenuExpanded = false
        libraryMenuExpanded = false
        status = "Loading ${soundFont.displayName}…"

        thread(name = "ScoreForgeSoundFontLoad", isDaemon = true) {
            val loaded = activeEngine.loadSoundFont(soundFont.localPath)
            if (loaded && preferredBank != null && preferredProgram != null) {
                activeEngine.presets
                    .firstOrNull { it.bank == preferredBank && it.program == preferredProgram }
                    ?.let(activeEngine::selectPreset)
            }

            val discoveredPresets = if (loaded) activeEngine.presets else emptyList()
            val discoveredIndex = if (loaded) activeEngine.selectedPresetIndex() else -1
            val selectedPreset = if (loaded) activeEngine.selectedPreset else null

            mainHandler.post {
                isLoading = false
                if (loaded) {
                    publishLoadedSoundFont(
                        soundFont = soundFont,
                        discoveredPresets = discoveredPresets,
                        discoveredIndex = discoveredIndex,
                        selectedPreset = selectedPreset,
                        restored = restored,
                    )
                } else {
                    status = "FluidSynth could not load ${soundFont.displayName}"
                }
            }
        }
    }

    fun useStarterInstruments() {
        val activeEngine = engine ?: return
        isLoading = true
        presetMenuExpanded = false
        libraryMenuExpanded = false
        status = "Loading starter instruments…"

        thread(name = "ScoreForgeStarterSoundFont", isDaemon = true) {
            val starterResult = SoundFontRepository.installBundledStarter(context)
            val starter = starterResult.getOrNull()
            val loaded = starter?.let { activeEngine.loadSoundFont(it.localPath) } == true
            val discoveredPresets = if (loaded) activeEngine.presets else emptyList()
            val discoveredIndex = if (loaded) activeEngine.selectedPresetIndex() else -1
            val selectedPreset = if (loaded) activeEngine.selectedPreset else null
            val error = starterResult.exceptionOrNull()?.message

            mainHandler.post {
                isLoading = false
                if (loaded) {
                    publishLoadedSoundFont(
                        soundFont = checkNotNull(starter),
                        discoveredPresets = discoveredPresets,
                        discoveredIndex = discoveredIndex,
                        selectedPreset = selectedPreset,
                        restored = false,
                    )
                } else {
                    status = error ?: "Starter instruments unavailable • preview synth active"
                }
            }
        }
    }

    LaunchedEffect(engine) {
        val activeEngine = engine ?: return@LaunchedEffect

        // The playback SoundFont now has process lifetime. If the Activity is recreated during
        // background playback, rebuild only the controls from saved metadata; reloading FluidSynth
        // underneath a playing score would interrupt/corrupt the stream.
        if (activeEngine.hasSoundFont) {
            val saved = SoundFontRepository.loadActiveSelection(context)
            if (saved != null) {
                val discoveredPresets = activeEngine.presets
                val discoveredIndex = activeEngine.selectedPresetIndex()
                val selectedPreset = activeEngine.selectedPreset
                publishLoadedSoundFont(
                    soundFont = saved.soundFont,
                    discoveredPresets = discoveredPresets,
                    discoveredIndex = discoveredIndex,
                    selectedPreset = selectedPreset,
                    restored = true,
                )
                return@LaunchedEffect
            }
        }

        isLoading = true
        status = "Loading instruments…"

        thread(name = "ScoreForgeSoundFontRestore", isDaemon = true) {
            val saved = SoundFontRepository.loadActiveSelection(context)
            val starterResult = if (saved == null) {
                SoundFontRepository.installBundledStarter(context)
            } else {
                null
            }
            val soundFont = saved?.soundFont ?: starterResult?.getOrNull()

            if (soundFont == null) {
                mainHandler.post {
                    isLoading = false
                    status = "Built-in preview synth"
                }
                return@thread
            }

            val loaded = activeEngine.loadSoundFont(soundFont.localPath)
            if (loaded && saved?.bank != null && saved.program != null) {
                activeEngine.presets
                    .firstOrNull { it.bank == saved.bank && it.program == saved.program }
                    ?.let(activeEngine::selectPreset)
            }

            val discoveredPresets = if (loaded) activeEngine.presets else emptyList()
            val discoveredIndex = if (loaded) activeEngine.selectedPresetIndex() else -1
            val selectedPreset = if (loaded) activeEngine.selectedPreset else null

            mainHandler.post {
                isLoading = false
                if (loaded) {
                    publishLoadedSoundFont(
                        soundFont = soundFont,
                        discoveredPresets = discoveredPresets,
                        discoveredIndex = discoveredIndex,
                        selectedPreset = selectedPreset,
                        restored = saved != null,
                    )
                } else {
                    if (saved != null) SoundFontRepository.clearActiveSelection(context)
                    status = "SoundFont unavailable • preview synth active"
                }
            }
        }
    }

    /**
     * Switching Score Forge tracks should switch the live piano and preset display too, but must
     * not call onPresetSelected because that callback represents a user edit to the active track.
     */
    LaunchedEffect(
        engine,
        currentSoundFont?.localPath,
        presets,
        requestedPresetBank,
        requestedPresetProgram,
        playbackActive,
    ) {
        if (engine == null || presets.isEmpty()) return@LaunchedEffect
        val bank = requestedPresetBank ?: return@LaunchedEffect
        val program = requestedPresetProgram ?: return@LaunchedEffect
        val requestedIndex = presets.indexOfFirst { it.bank == bank && it.program == program }
        if (requestedIndex < 0) return@LaunchedEffect

        // The playback engine and editor share this SoundFontEngine. During score playback,
        // changing the selected track may update the live keyboard/preset display, but must not
        // reprogram a channel underneath the streaming song. When playback ends this effect runs
        // again and synchronizes the engine to the selected track.
        if (!playbackActive && !engine.selectPresetAt(requestedIndex)) return@LaunchedEffect

        presetIndex = requestedIndex
        presetMenuExpanded = false
        val selected = presets[requestedIndex]
        currentSoundFont?.let {
            SoundFontRepository.saveActiveSelection(context, it, selected)
        }
        LiveInstrumentBus.selectPreset(selected)
        status = "${presets.size} instruments ready"
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null || engine == null) return@rememberLauncherForActivityResult

        isLoading = true
        presetMenuExpanded = false
        libraryMenuExpanded = false
        status = "Importing SoundFont…"

        thread(name = "ScoreForgeSoundFontImport", isDaemon = true) {
            val imported = SoundFontRepository.importToAppStorage(context, uri)
            val importedFile = imported.getOrNull()
            val loaded = importedFile?.let { engine.loadSoundFont(it.localPath) } == true
            val discoveredPresets = if (loaded) engine.presets else emptyList()
            val discoveredIndex = if (loaded) engine.selectedPresetIndex() else -1
            val selectedPreset = if (loaded) engine.selectedPreset else null
            val error = imported.exceptionOrNull()?.message

            mainHandler.post {
                isLoading = false
                if (loaded) {
                    publishLoadedSoundFont(
                        soundFont = checkNotNull(importedFile),
                        discoveredPresets = discoveredPresets,
                        discoveredIndex = discoveredIndex,
                        selectedPreset = selectedPreset,
                        restored = false,
                    )
                } else {
                    status = error ?: "FluidSynth could not load that SoundFont"
                }
            }
        }
    }

    fun selectPreset(index: Int) {
        if (engine == null || index !in presets.indices) return
        if (engine.selectPresetAt(index)) {
            presetIndex = index
            val selected = presets[index]
            status = "${presets.size} instruments ready"
            currentSoundFont?.let {
                SoundFontRepository.saveActiveSelection(context, it, selected)
            }
            LiveInstrumentBus.selectPreset(selected)
            onPresetSelected(selected)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("Instrument:", style = MaterialTheme.typography.labelLarge)

        if (presets.isNotEmpty()) {
            ScoreForgeOutlinedButton(
                onClick = { selectPreset((presetIndex - 1).coerceAtLeast(0)) },
                enabled = presetIndex > 0 && !isLoading,
            ) {
                Text("◀")
            }

            Box {
                ScoreForgeOutlinedButton(
                    onClick = { presetMenuExpanded = true },
                    enabled = !isLoading,
                ) {
                    val selectedName = presets.getOrNull(presetIndex)?.displayName ?: "Choose instrument"
                    val position = if (presetIndex in presets.indices) {
                        "${presetIndex + 1}/${presets.size}"
                    } else {
                        presets.size.toString()
                    }
                    Text("$selectedName • $position")
                }

                DropdownMenu(
                    expanded = presetMenuExpanded,
                    onDismissRequest = { presetMenuExpanded = false },
                ) {
                    presets.forEachIndexed { index, preset ->
                        ScoreForgeDropdownMenuItem(
                            text = { Text("${index + 1}. ${preset.displayName}") },
                            onClick = {
                                selectPreset(index)
                                presetMenuExpanded = false
                            },
                        )
                    }
                }
            }

            ScoreForgeOutlinedButton(
                onClick = { selectPreset((presetIndex + 1).coerceAtMost(presets.lastIndex)) },
                enabled = presetIndex in 0 until presets.lastIndex && !isLoading,
            ) {
                Text("▶")
            }
        } else if (currentSoundFont != null) {
            Text("Default program", style = MaterialTheme.typography.labelLarge)
        }

        Box {
            ScoreForgeOutlinedButton(
                onClick = { libraryMenuExpanded = true },
                enabled = engine != null && !isLoading,
            ) {
                Text("Library")
            }

            DropdownMenu(
                expanded = libraryMenuExpanded,
                onDismissRequest = { libraryMenuExpanded = false },
            ) {
                ScoreForgeDropdownMenuItem(
                    text = { Text("Use ${SoundFontRepository.STARTER_DISPLAY_NAME}") },
                    onClick = ::useStarterInstruments,
                )
                ScoreForgeDropdownMenuItem(
                    text = { Text("Import SoundFont…") },
                    onClick = {
                        libraryMenuExpanded = false
                        launcher.launch(arrayOf("audio/*", "application/octet-stream", "*/*"))
                    },
                )
            }
        }

        Text(
            text = buildString {
                currentSoundFont?.let { append(it.displayName).append(" • ") }
                append(status)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
