# Score Forge 0.2.43 background playback phone test

This build moves normal score playback behind an Android foreground media service while keeping the existing Score Forge transport and FluidSynth playback engine.

Phone checks:

1. Start a song, press Home, and verify playback continues.
2. Turn the screen off and verify playback continues.
3. Verify Android shows Score Forge media controls with Play/Pause and Stop.
4. Pause from Android media controls, return to Score Forge, and verify the playhead remains at the paused position.
5. Resume from Android media controls and verify playback continues from that position.
6. Stop from Android media controls and verify playback stops and the media notification/card is dismissed.
7. Rotate the phone during playback and verify the song does not stop, restart, or duplicate.
8. Open Score Forge from the media notification/card and verify only one playback instance is active.
9. Recheck a tempo-map MIDI such as Homeward for normal playback and tempo changes.
