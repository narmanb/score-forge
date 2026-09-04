from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STAFF = ROOT / "app/src/main/java/com/scoreforge/app/ui/ScoreStaffEditor.kt"
COMPOSER = ROOT / "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


staff = STAFF.read_text()
staff = replace_once(
    staff,
    '''                    drawLine(
                        Color(0xFFB34747),
                        Offset(entryCursorX, geometry.rulerY + geometry.lineSpacing * 0.22f),
                        Offset(entryCursorX, geometry.staffBottom + geometry.lineSpacing * 0.72f),
                        1.5f,
                    )
                    drawCircle(
                        Color(0xFFB34747),
                        radius = 4f,
                        center = Offset(entryCursorX, geometry.rulerY + geometry.lineSpacing * 0.12f),
                    )
''',
    '''                    drawLine(
                        Color(0xFFB34747),
                        Offset(entryCursorX, geometry.staffTop - geometry.lineSpacing * 0.30f),
                        Offset(entryCursorX, geometry.staffBottom + geometry.lineSpacing * 0.72f),
                        1.5f,
                    )
''',
    "separate red entry cursor from playback gutter",
)
staff = replace_once(
    staff,
    '''                    drawLine(
                        Color(0xFF6A52A3),
                        Offset(playheadX, geometry.rulerY - geometry.lineSpacing * 0.18f),
                        Offset(playheadX, geometry.staffBottom + geometry.lineSpacing * 1.35f),
                        3f,
                    )
''',
    '''                    drawLine(
                        Color(0xFF6A52A3),
                        Offset(playheadX, geometry.rulerY - geometry.lineSpacing * 0.18f),
                        Offset(playheadX, geometry.staffBottom + geometry.lineSpacing * 0.30f),
                        3f,
                    )
                    drawCircle(
                        Color(0xFF6A52A3),
                        radius = 4f,
                        center = Offset(
                            playheadX,
                            geometry.rulerY - geometry.lineSpacing * 0.18f,
                        ),
                    )
''',
    "separate purple playhead from entry gutter",
)
STAFF.write_text(staff)

composer = COMPOSER.read_text()
composer = replace_once(
    composer,
    '''    fun moveEntryCursor(beat: Float) {
        if (pianoEntryMode == PianoEntryMode.NATURAL) {
''',
    '''    fun moveEntryCursor(beat: Float) {
        repairUnexpectedTransportForEntry()
        if (pianoEntryMode == PianoEntryMode.NATURAL) {
''',
    "entry cursor transport repair",
)
COMPOSER.write_text(composer)
