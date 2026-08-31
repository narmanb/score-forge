package com.scoreforge.app.audio

/**
 * Process-lifetime live instrument preview bus.
 *
 * Score playback owns a separate FluidSynth instance; this bus is only for immediate touch/staff
 * previews. Keeping them separate means preview notes and full-score rendering cannot corrupt
 * each other's synth state.
 */
object LiveInstrumentBus {
    private val player = LiveSoundFontPlayer()

    fun loadSoundFont(soundFont: ImportedSoundFont, preset: SoundFontPreset?) {
        player.loadSoundFont(soundFont.localPath, preset)
    }

    fun selectPreset(preset: SoundFontPreset) {
        player.selectPreset(preset)
    }

    fun previewPitch(midiPitch: Int, velocity: Int = 92): Boolean =
        player.playOneShot(midiPitch, velocity)

    fun noteOn(midiPitch: Int, velocity: Int = 96): Boolean =
        player.noteOn(midiPitch, velocity)

    fun noteOff(midiPitch: Int): Boolean =
        player.noteOff(midiPitch)

    fun allNotesOff() {
        player.allNotesOff()
    }
}
