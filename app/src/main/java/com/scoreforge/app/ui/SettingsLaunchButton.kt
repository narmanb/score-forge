package com.scoreforge.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * App-level Settings launcher. Deliberately more prominent than ordinary command buttons:
 * Variant A from the phone mockups — lavender gear on a compact dark rounded tile.
 */
@Composable
internal fun SettingsLaunchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var activated by remember { mutableStateOf(false) }

    val gearRotation by animateFloatAsState(
        targetValue = if (activated) 18f else 0f,
        animationSpec = tween(durationMillis = 105),
        label = "settingsGearRotation",
    )
    val gearScale by animateFloatAsState(
        targetValue = if (activated) 0.84f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "settingsGearScale",
    )
    val tileColor by animateColorAsState(
        targetValue = if (activated) Color(0xFF494253) else Color(0xFF35323B),
        animationSpec = tween(durationMillis = 90),
        label = "settingsTileColor",
    )

    Surface(
        modifier = modifier
            .size(52.dp)
            .clickable(enabled = enabled) {
                if (activated) return@clickable
                view.performScoreForgeHaptic(UiHapticFeedback.TICK)
                ScoreForgeUiFeedback.play(UiCommandFeedback.SETTINGS, view.context)
                activated = true
                scope.launch {
                    // Let the gear visibly react before replacing the composer with Settings.
                    delay(120L)
                    activated = false
                    delay(35L)
                    onClick()
                }
            },
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) tileColor else Color(0xFF302E35),
        border = BorderStroke(1.dp, if (enabled) Color(0xFF756D82) else Color(0xFF56515E)),
        tonalElevation = 2.dp,
        shadowElevation = if (activated) 0.dp else 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "⚙",
                color = if (enabled) Color(0xFFD0B8FF) else Color(0xFF8A8197),
                fontSize = 37.sp,
                modifier = Modifier.graphicsLayer {
                    rotationZ = gearRotation
                    scaleX = gearScale
                    scaleY = gearScale
                },
            )
        }
    }
}
