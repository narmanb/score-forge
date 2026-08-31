package com.scoreforge.app.ui

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    onSoundFontLoaded: (ImportedSoundFont, SoundFontPreset?) -> Unit = { _, _ -> },
    onPresetSelected: (SoundFontPreset) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var currentSoundFont by remember { mutableStateOf<ImportedSoundFont?>(null) }
    var presets by remember { mutableStateOf<List<SoundFontPreset>>(emptyList()) }
    var presetIndex by remember { mutableIntStateOf(-1) }
    var status by remember {
        mutableStateOf(
            if (engine == null) "Native SoundFont engine unavailable" else "Built-in preview synth"
        )
    }
    var isImporting by remember { mutableStateOf(false) }

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
        status = when {
            restored && discoveredPresets.isNotEmpty() ->
                "Restored • ${discoveredPresets.size} presets • live piano ready"
            restored -> "Restored • default program"
            discoveredPresets.isNotEmpty() -> "${discoveredPresets.size} presets • live piano ready"
            else -> "Loaded • default program"
        }

        SoundFontRepository.saveActiveSelection(context, soundFont, selectedPreset)
        LiveInstrumentBus.loadSoundFont(soundFont, selectedPreset)
        onSoundFontLoaded(soundFont, selectedPreset)
    }

    LaunchedEffect(engine) {
        if (engine == null) return@LaunchedEffect
        val saved = SoundFontRepository.loadActiveSelection(context) ?: return@LaunchedEffect

        isImporting = true
        status = "Restoring ${saved.soundFont.displayName}…"

        thread(name = "ScoreForgeSoundFontRestore", isDaemon = true) {
            val loaded = engine.loadSoundFont(saved.soundFont.localPath)
            if (loaded && saved.bank != null && saved.program != null) {
                engine.presets
                    .firstOrNull { it.bank == saved.bank && it.program == saved.program }
                    ?.let(engine::selectPreset)
            }

            val discoveredPresets = if (loaded) engine.presets else emptyList()
            val discoveredIndex = if (loaded) engine.selectedPresetIndex() else -1
            val selectedPreset = if (loaded) engine.selectedPreset else null

            mainHandler.post {
                isImporting = false
                if (loaded) {
                    publishLoadedSoundFont(
                        soundFont = saved.soundFont,
                        discoveredPresets = discoveredPresets,
                        discoveredIndex = discoveredIndex,
                        selectedPreset = selectedPreset,
                        restored = true,
                    )
                } else {
                    SoundFontRepository.clearActiveSelection(context)
                    status = "Saved SoundFont could not be restored"
                }
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null || engine == null) return@rememberLauncherForActivityResult

        isImporting = true
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
                isImporting = false
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
            status = "${presets.size} presets"
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
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("Instrument:", style = MaterialTheme.typography.labelLarge)

        Button(
            onClick = { launcher.launch(arrayOf("audio/*", "application/octet-stream", "*/*")) },
            enabled = engine != null && !isImporting,
        ) {
            Text(if (currentSoundFont == null) "Import SoundFont" else "Change SoundFont")
        }

        if (presets.isNotEmpty()) {
            OutlinedButton(
                onClick = { selectPreset((presetIndex - 1).coerceAtLeast(0)) },
                enabled = presetIndex > 0,
            ) {
                Text("◀")
            }

            Text(
                text = presets.getOrNull(presetIndex)?.displayName ?: "Preset",
                style = MaterialTheme.typography.labelLarge,
            )

            OutlinedButton(
                onClick = { selectPreset((presetIndex + 1).coerceAtMost(presets.lastIndex)) },
                enabled = presetIndex in 0 until presets.lastIndex,
            ) {
                Text("▶")
            }
        } else if (currentSoundFont != null) {
            Text("Default program", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.weight(1f))
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
