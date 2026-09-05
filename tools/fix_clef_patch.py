from pathlib import Path

p = Path("app/src/main/java/com/scoreforge/app/ui/ScoreStaffEditor.kt")
s = p.read_text()

# Manual clef changes must restart both pointer handlers so hit testing/input immediately use the new clef.
s = s.replace(
'''                            staffInputEnabled,
                            notationGaps,
                        ) {''',
'''                            staffInputEnabled,
                            notationGaps,
                            effectiveClef,
                        ) {''',
1,
)
s = s.replace(
'''                        .pointerInput(events, contentBeats, beatWidthPx, timelineLeftPx, notationGaps) {''',
'''                        .pointerInput(events, contentBeats, beatWidthPx, timelineLeftPx, notationGaps, effectiveClef) {''',
1,
)

# Legato rendering also needs the current clef for Y mapping.
old = '''                                beatWidthPx,
                                geometry,
                                normalizedKeySignatures,
                                notationGaps,
                            )'''
new = '''                                beatWidthPx,
                                geometry,
                                effectiveClef,
                                normalizedKeySignatures,
                                notationGaps,
                            )'''
if old not in s:
    raise SystemExit("missing legato call patch target")
s = s.replace(old, new, 1)

# Standard bass-clef key-signature placement keeps the later accidentals within the staff.
s = s.replace('KeySymbolPosition(5, 2 * 7 + 5), // A2\n    KeySymbolPosition(2, 3 * 7 + 2), // E3', 'KeySymbolPosition(5, 3 * 7 + 5), // A3\n    KeySymbolPosition(2, 3 * 7 + 2), // E3', 1)
s = s.replace('KeySymbolPosition(4, 2 * 7 + 4), // G2\n    KeySymbolPosition(0, 3 * 7 + 0), // C3', 'KeySymbolPosition(4, 3 * 7 + 4), // G3\n    KeySymbolPosition(0, 3 * 7 + 0), // C3', 1)

p.write_text(s)
