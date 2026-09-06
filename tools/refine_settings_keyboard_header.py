from pathlib import Path

ROOT = Path('.')
ui = ROOT / 'app/src/main/java/com/scoreforge/app/ui'

# 1) Restore the original natural-note labeling behavior as the explicit default.
settings_path = ui / 'ScoreForgeSettings.kt'
text = settings_path.read_text()
old = '''enum class KeyboardNoteLabelSetting(val displayName: String) {
    OFF("Off"),
    C_ONLY("C Notes Only"),
    ALL("All Notes"),
}'''
new = '''enum class KeyboardNoteLabelSetting(val displayName: String) {
    DEFAULT("Default"),
    OFF("Off"),
    C_ONLY("C Notes Only"),
    ALL("All Notes"),
}'''
if old not in text:
    raise SystemExit('keyboard label enum pattern not found')
text = text.replace(old, new)
text = text.replace(
    'val keyboardNoteLabels: KeyboardNoteLabelSetting = KeyboardNoteLabelSetting.C_ONLY,',
    'val keyboardNoteLabels: KeyboardNoteLabelSetting = KeyboardNoteLabelSetting.DEFAULT,',
)
text = text.replace(
    'keyboardNoteLabels = prefs.enumValue("keyboard_labels", KeyboardNoteLabelSetting.C_ONLY),',
    'keyboardNoteLabels = prefs.enumValue("keyboard_labels", KeyboardNoteLabelSetting.DEFAULT),',
)
settings_path.write_text(text)

# 2) Default = every natural/white key labeled; ALL adds sharp/black-key labels too.
keyboard_path = ui / 'MultitouchPianoKeyboard.kt'
text = keyboard_path.read_text()
text = text.replace(
    'noteLabelSetting: KeyboardNoteLabelSetting = KeyboardNoteLabelSetting.C_ONLY,',
    'noteLabelSetting: KeyboardNoteLabelSetting = KeyboardNoteLabelSetting.DEFAULT,',
)
old = '''                        val showLabel = when (noteLabelSetting) {
                            KeyboardNoteLabelSetting.OFF -> false
                            KeyboardNoteLabelSetting.C_ONLY -> pitch % 12 == 0
                            KeyboardNoteLabelSetting.ALL -> true
                        }'''
new = '''                        val showLabel = when (noteLabelSetting) {
                            KeyboardNoteLabelSetting.DEFAULT -> true
                            KeyboardNoteLabelSetting.OFF -> false
                            KeyboardNoteLabelSetting.C_ONLY -> pitch % 12 == 0
                            KeyboardNoteLabelSetting.ALL -> true
                        }'''
if old not in text:
    raise SystemExit('white-key label mapping pattern not found')
text = text.replace(old, new)
keyboard_path.write_text(text)

# 3) Put a gear-only Settings control at the far left of the title bar.
composer_path = ui / 'ComposerScreen.kt'
text = composer_path.read_text()
old = '''    ) {
        Column {
            Text("Score Forge", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                "$projectName • $activeTrackName • $trackCount tracks • $timeSignatureLabel • $keySignatureLabel • $measureCount measures • beat ${formatBeat(cursorBeat)} • $noteCount notes • $restCount rests",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFE0DCE5),
            )
        }

        ComposerToolbarButton(
            label = "⚙ Settings",
            onClick = onOpenSettings,
        )

        ChamferedControlButton('''
new = '''    ) {
        ChamferedControlButton(
            label = "⚙",
            onClick = onOpenSettings,
            compact = false,
        )

        Column {
            Text("Score Forge", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                "$projectName • $activeTrackName • $trackCount tracks • $timeSignatureLabel • $keySignatureLabel • $measureCount measures • beat ${formatBeat(cursorBeat)} • $noteCount notes • $restCount rests",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFE0DCE5),
            )
        }

        ChamferedControlButton('''
if old not in text:
    raise SystemExit('header settings button pattern not found')
text = text.replace(old, new)
composer_path.write_text(text)

# 4) Clarify the options in the Settings copy.
settings_screen_path = ui / 'ScoreForgeSettingsScreen.kt'
text = settings_screen_path.read_text()
text = text.replace(
    'description = "Choose how much pitch labeling appears on the touch piano.",',
    'description = "Default labels natural keys only. Off hides labels, C Notes Only labels Cs, and All Notes also labels sharps.",',
)
settings_screen_path.write_text(text)

# 5) Keep the phone-test note aligned with the revised default.
doc_path = ROOT / 'docs/settings-0.2.48-phone-test.md'
if doc_path.exists():
    text = doc_path.read_text()
    text = text.replace(
        'Keyboard Note Labels',
        'Keyboard Note Labels (Default = natural keys labeled, sharps unlabeled)',
    )
    doc_path.write_text(text)
