from pathlib import Path

control_path = Path("app/src/main/java/com/scoreforge/app/ui/ControlShapes.kt")
control = control_path.read_text()

old_toolbar = '''@Composable
internal fun ComposerToolbarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(36.dp),
        shape = ChamferedControlShape,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (enabled) ComposerControlOutlineColor else ComposerControlDisabledOutline,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) ComposerControlPressedColor else ComposerControlButtonColor,
            contentColor = Color.White,
            disabledContainerColor = ComposerControlDisabledColor,
            disabledContentColor = Color(0xFFAAA5B0),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (selected) 0.dp else 2.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
'''

new_toolbar = '''@Composable
internal fun ComposerToolbarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(31.dp),
        shape = ChamferedControlShape,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (enabled) ComposerControlOutlineColor else ComposerControlDisabledOutline,
        ),
        colors = ButtonDefaults.buttonColors(
            // Root categories and Back deliberately use the same dark family as a selected
            // lower-keyboard control so the transforming toolbar reads as its own control strip.
            containerColor = ComposerControlPressedColor,
            contentColor = Color.White,
            disabledContainerColor = ComposerControlDisabledColor,
            disabledContentColor = Color(0xFFAAA5B0),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Selection/action control used inside a transforming-toolbar submenu.
 * Unselected controls reuse the old light toolbar gray; selected controls use the exact same
 * dark latched color as Step and the duration palette beneath the piano keyboard.
 */
@Composable
internal fun ComposerSubmenuButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(32.dp),
        shape = ChamferedControlShape,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (enabled) ComposerControlOutlineColor else ComposerControlDisabledOutline,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) ComposerControlPressedColor else ComposerControlButtonColor,
            contentColor = Color.White,
            disabledContainerColor = ComposerControlDisabledColor,
            disabledContentColor = Color(0xFFAAA5B0),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (selected) 0.dp else 2.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 0.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Compact toolbar command: keep the already-small angular command geometry but use submenu fill. */
@Composable
internal fun ComposerSubmenuCommandButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    feedback: UiCommandFeedback = UiCommandFeedback.NEUTRAL,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val physicallyPressed by interactionSource.collectIsPressedAsState()
    val latchedPressed = remember { androidx.compose.runtime.mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val visuallyPressed = physicallyPressed || latchedPressed.value

    Button(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            ScoreForgeUiFeedback.play(feedback)
            latchedPressed.value = true
            scope.launch {
                delay(110L)
                latchedPressed.value = false
            }
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .height(30.dp)
            .offset(y = if (visuallyPressed) 2.dp else 0.dp),
        shape = CompactCommandShape,
        border = BorderStroke(
            1.dp,
            if (enabled) ComposerControlOutlineColor else ComposerControlDisabledOutline,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (visuallyPressed) ComposerControlPressedColor else ComposerControlButtonColor,
            contentColor = Color.White,
            disabledContainerColor = ComposerControlDisabledColor,
            disabledContentColor = Color(0xFFAAA5B0),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (visuallyPressed) 0.dp else 2.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
'''

if old_toolbar not in control:
    raise SystemExit("ComposerToolbarButton block not found")
control = control.replace(old_toolbar, new_toolbar, 1)
control_path.write_text(control)

toolbar_path = Path("app/src/main/java/com/scoreforge/app/ui/ComposerTransformToolbar.kt")
toolbar = toolbar_path.read_text()

toolbar = toolbar.replace("import androidx.compose.material3.Button\n", "")
toolbar = toolbar.replace("import androidx.compose.material3.OutlinedButton\n", "")
toolbar = toolbar.replace(".height(54.dp)", ".height(44.dp)", 1)
toolbar = toolbar.replace(".padding(horizontal = 12.dp, vertical = 5.dp)", ".padding(horizontal = 12.dp, vertical = 3.dp)", 1)
toolbar = toolbar.replace("horizontalArrangement = Arrangement.spacedBy(7.dp)", "horizontalArrangement = Arrangement.spacedBy(6.dp)", 1)

toolbar = toolbar.replace("CompactCommandButton(", "ComposerSubmenuCommandButton(")

replacements = {
'''                        OutlinedButton(onClick = onCancelComfortTempo) { Text("Cancel") }''': '''                        ComposerSubmenuButton(label = "Cancel", onClick = onCancelComfortTempo)''',
'''                        Button(onClick = onApplyComfortTempo) { Text("Apply") }''': '''                        ComposerSubmenuButton(label = "Apply", onClick = onApplyComfortTempo)''',
'''                        OutlinedButton(onClick = onTryComfortTempoAgain) { Text("Try Again") }''': '''                        ComposerSubmenuButton(label = "Try Again", onClick = onTryComfortTempoAgain)''',
'''                        OutlinedButton(onClick = onStartComfortTempo) { Text("Measure Tempo") }''': '''                        ComposerSubmenuButton(label = "Measure Tempo", onClick = onStartComfortTempo)''',
'''                ScoreClefMode.entries.forEach { option ->
                    if (option == clefMode) {
                        Button(onClick = { onClefModeChanged(option) }) { Text(option.displayName) }
                    } else {
                        OutlinedButton(onClick = { onClefModeChanged(option) }) { Text(option.displayName) }
                    }
                }''': '''                ScoreClefMode.entries.forEach { option ->
                    ComposerSubmenuButton(
                        label = option.displayName,
                        onClick = { onClefModeChanged(option) },
                        selected = option == clefMode,
                    )
                }''',
'''                NoteDuration.entries.forEach { duration ->
                    if (duration == selectedDuration) {
                        Button(onClick = { onDurationSelected(duration) }) { Text(duration.displayName) }
                    } else {
                        OutlinedButton(onClick = { onDurationSelected(duration) }) { Text(duration.displayName) }
                    }
                }
                if (dotted) Button(onClick = onToggleDotted) { Text("Dot •") }
                else OutlinedButton(onClick = onToggleDotted) { Text("Dot") }

                OutlinedButton(onClick = onInsertRest) {
                    Text(if (dotted) "Dotted Rest" else "Rest")
                }

                if (tieActive) Button(onClick = onToggleTie, enabled = tieEnabled) { Text("Tie →") }
                else OutlinedButton(onClick = onToggleTie, enabled = tieEnabled) { Text("Tie →") }

                if (sharpInput) Button(onClick = onToggleSharpInput) { Text("Staff ♯") }
                else OutlinedButton(onClick = onToggleSharpInput) { Text("Staff ♯") }''': '''                NoteDuration.entries.forEach { duration ->
                    ComposerSubmenuButton(
                        label = duration.displayName,
                        onClick = { onDurationSelected(duration) },
                        selected = duration == selectedDuration,
                    )
                }
                ComposerSubmenuButton(
                    label = if (dotted) "Dot •" else "Dot",
                    onClick = onToggleDotted,
                    selected = dotted,
                )
                ComposerSubmenuButton(
                    label = if (dotted) "Dotted Rest" else "Rest",
                    onClick = onInsertRest,
                )
                ComposerSubmenuButton(
                    label = "Tie →",
                    onClick = onToggleTie,
                    selected = tieActive,
                    enabled = tieEnabled,
                )
                ComposerSubmenuButton(
                    label = "Staff ♯",
                    onClick = onToggleSharpInput,
                    selected = sharpInput,
                )''',
'''                if (editorMode == ScoreEditorMode.STAFF) {
                    Button(onClick = { onEditorModeChanged(ScoreEditorMode.STAFF) }) { Text("Staff") }
                } else {
                    OutlinedButton(onClick = { onEditorModeChanged(ScoreEditorMode.STAFF) }) { Text("Staff") }
                }
                if (editorMode == ScoreEditorMode.PIANO_ROLL) {
                    Button(onClick = { onEditorModeChanged(ScoreEditorMode.PIANO_ROLL) }) { Text("Piano Roll") }
                } else {
                    OutlinedButton(onClick = { onEditorModeChanged(ScoreEditorMode.PIANO_ROLL) }) { Text("Piano Roll") }
                }
                OutlinedButton(onClick = onTogglePianoKeyboard) {
                    Text(if (showPianoKeyboard) "Hide Piano" else "Show Piano")
                }''': '''                ComposerSubmenuButton(
                    label = "Staff",
                    onClick = { onEditorModeChanged(ScoreEditorMode.STAFF) },
                    selected = editorMode == ScoreEditorMode.STAFF,
                )
                ComposerSubmenuButton(
                    label = "Piano Roll",
                    onClick = { onEditorModeChanged(ScoreEditorMode.PIANO_ROLL) },
                    selected = editorMode == ScoreEditorMode.PIANO_ROLL,
                )
                ComposerSubmenuButton(
                    label = if (showPianoKeyboard) "Hide Piano" else "Show Piano",
                    onClick = onTogglePianoKeyboard,
                )''',
}

for old, new in replacements.items():
    if old not in toolbar:
        raise SystemExit(f"Expected toolbar block not found:\n{old}")
    toolbar = toolbar.replace(old, new, 1)

toolbar_path.write_text(toolbar)
