from pathlib import Path
p = Path('app/src/main/java/com/scoreforge/app/ui/MultitouchPianoKeyboard.kt')
text = p.read_text()
old = 'internal enum class StepChordMode {'
new = 'enum class StepChordMode {'
if old not in text:
    raise RuntimeError('StepChordMode visibility pattern not found')
p.write_text(text.replace(old, new, 1))
print('StepChordMode visibility fixed')
