package app.arcis.glasses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.arcis.ArcisApp
import app.arcis.controller.ControllerPrefs
import java.util.Locale

/**
 * Arcis settings, shown inside Home on the glasses. Changes apply right away, so you can
 * adjust the pointer speed and feel it with the same pointer. Big −/+ targets instead of a
 * slider: easier to hit with the trackpad.
 */
@Composable
fun SettingsPanel(onDone: () -> Unit) {
    val prefs = ArcisApp.instance.controllerPrefs
    val speed by prefs.pointerSpeed.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .background(Color(0xFF111319), RoundedCornerShape(20.dp))
                .border(1.dp, GlassesColors.CardBorder, RoundedCornerShape(20.dp))
                .padding(28.dp)
                .width(460.dp),
        ) {
            Text("Settings", color = GlassesColors.Text, fontSize = 22.sp)
            Spacer(Modifier.height(20.dp))
            Text("Pointer speed", color = GlassesColors.Text, fontSize = 17.sp)
            Text("How far the pointer moves for a swipe on the phone", color = GlassesColors.Dim, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StepButton("−", enabled = speed > ControllerPrefs.MIN_POINTER_SPEED) {
                    prefs.setPointerSpeed(speed - ControllerPrefs.POINTER_SPEED_STEP)
                }
                SpeedBar(speed, Modifier.weight(1f))
                StepButton("+", enabled = speed < ControllerPrefs.MAX_POINTER_SPEED) {
                    prefs.setPointerSpeed(speed + ControllerPrefs.POINTER_SPEED_STEP)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                String.format(Locale.US, "%.2f×", speed),
                color = GlassesColors.Dim,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button("Done", enabled = true, onClick = onDone)
                Button("Default", enabled = speed != ControllerPrefs.DEFAULT_POINTER_SPEED) {
                    prefs.setPointerSpeed(ControllerPrefs.DEFAULT_POINTER_SPEED)
                }
            }
        }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .background(if (enabled) Color(0xFF1E3A6E) else GlassesColors.Card, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) { Text(label, color = if (enabled) GlassesColors.Text else GlassesColors.Dim, fontSize = 26.sp) }
}

/** One segment per step, filled up to the current speed. */
@Composable
private fun SpeedBar(speed: Float, modifier: Modifier) {
    val step = ControllerPrefs.POINTER_SPEED_STEP
    val steps = Math.round((ControllerPrefs.MAX_POINTER_SPEED - ControllerPrefs.MIN_POINTER_SPEED) / step) + 1
    val filled = Math.round((speed - ControllerPrefs.MIN_POINTER_SPEED) / step) + 1
    Row(modifier.height(20.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(steps) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(20.dp)
                    .background(if (i < filled) Color(0xFF6EA8FF) else GlassesColors.Card, RoundedCornerShape(4.dp)),
            )
        }
    }
}
