# Score Forge 0.2.45 — WAV export phone test

Do not merge this branch until the APK is verified on a real Android device.

## Core export

1. Open or create a project with at least two audible notes.
2. Tap **Export WAV**.
3. Save with the suggested `.wav` filename.
4. Confirm the export finishes without freezing Score Forge.
5. Open the WAV in an ordinary Android audio player and confirm it plays.

## Musical fidelity

Use a project with more than one track if possible.

- Confirm the expected instruments/SoundFont are heard.
- Confirm muted tracks are absent.
- Confirm solo behaves the same as Score Forge playback.
- Confirm track volume and pan are audible in the render.
- Confirm note velocity/articulation sounds consistent with playback.
- Confirm tied notes are sustained rather than re-attacked.
- Confirm percussion tracks render as percussion.

## Tempo map

1. Create or open a project with a mid-song tempo change.
2. Play it in Score Forge and note where the tempo changes.
3. Export WAV.
4. Confirm the WAV changes tempo at the same musical position.

## Long-song / memory regression

Export a reasonably long imported MIDI project.

- Score Forge should remain responsive enough to complete the export.
- The export should not require allocating the full PCM song in memory; the implementation renders in blocks.
- The resulting WAV should play to the end and contain a short synth/reverb tail.

## Filename behavior

1. Start **Export WAV**.
2. Delete `.wav` from the filename before saving.
3. Confirm Score Forge restores `.wav` and the resulting file opens normally in an audio player.

## Expected scope for 0.2.45

- 44.1 kHz
- 16-bit PCM
- stereo WAV
- active `.sf2` SoundFont / starter instruments
- tempo-map aware
- no metronome click in exported audio

FLAC, OGG, MP3, and AAC are intentionally not part of this PR.
