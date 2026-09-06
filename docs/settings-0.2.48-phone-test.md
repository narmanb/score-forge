# Score Forge 0.2.48 Settings phone test

Verify the hybrid full-screen Settings page opens from the header and Back returns to the composer.

Core checks:
- Settings persist after force-closing and reopening Score Forge.
- Screen Orientation: Follow Device / Lock Portrait / Lock Landscape all behave correctly.
- Haptic Off / Light / Standard / Strong are distinguishable; Standard matches 0.2.47.
- UI command sounds can be disabled and re-enabled.
- Note duration order changes both top toolbar and lower piano duration palette.
- Keep Screen Awake applies immediately.
- Keyboard note labels support Off / C notes only / All notes.
- Remember Keyboard Octave persists the last octave when enabled.
- Default Editor / Clef / Entry Mode / Staff Input apply on fresh sessions/tracks as described.
- Follow Playback disables/enables automatic playhead following.
- Restore Last Project and Autosave / Recovery toggles control draft behavior.
- Duration audition settings persist; actual duration-preview audio lands in the next QOL slice.
- Reset Settings to Defaults changes preferences without touching project data.
