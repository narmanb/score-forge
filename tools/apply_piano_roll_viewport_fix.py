from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PIANO = ROOT / "app/src/main/java/com/scoreforge/app/ui/PianoRollEditor.kt"
BUILD = ROOT / "app/build.gradle.kts"

text = PIANO.read_text()
text = text.replace(
    "import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.width\n",
    "import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.requiredHeight\nimport androidx.compose.foundation.layout.requiredWidth\n",
)
old = """                    .width(contentWidth)\n                    .height(contentHeight)\n"""
new = """                    // This canvas intentionally exceeds the viewport so manual panning has\n                    // real off-screen content to reveal. Plain width/height are constrained back\n                    // to the BoxWithConstraints viewport, which can make the offset canvas vanish.\n                    .requiredWidth(contentWidth)\n                    .requiredHeight(contentHeight)\n"""
if old not in text:
    raise SystemExit("piano roll size block not found")
text = text.replace(old, new, 1)
PIANO.write_text(text)

build = BUILD.read_text()
build = build.replace('versionCode = 23', 'versionCode = 24')
build = build.replace('versionName = "0.2.20"', 'versionName = "0.2.21"')
BUILD.write_text(build)
