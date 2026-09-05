# Score Forge 0.2.41 phone-test focus

This hotfix addresses the 0.2.40 silence seen with variable-tempo projects and makes tempo-change locations visible.

Phone checks:

1. Import `Paul Albrecht - Homeward.mid` and verify score playback produces SoundFont audio.
2. Verify manually inserted notes produce preview/playback audio.
3. Verify the imported tempo map starts at 140 BPM and changes later in the song.
4. Verify staff tempo marks such as `♩ = 140` are visible at tempo-change locations.
5. Verify the tempo control row lists the full tempo map.
6. Verify a manually inserted time signature changes notation/bar grouping; with metronome enabled, accents should follow the new meter.
