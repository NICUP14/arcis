package app.arcis.glasses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Trackpad = listOf(
    "1 finger" to "Move the pointer",
    "Tap" to "Click",
    "Long-press" to "Right-click",
    "Tap, hold & move" to "Drag",
    "2 fingers / right edge" to "Scroll",
    "Pinch" to "Zoom in / out",
    "2-finger tap" to "Back (Esc on a computer)",
)

private val Switching = listOf(
    "Bottom edge ← →" to "Previous / next app",
    "Bottom edge, up" to "Home",
    "Bottom edge, up & hold" to "All open apps",
    "Keyboard button" to "Type",
    "Mic button (hold)" to "Speak to type",
)

private val Keys = listOf(
    "<" to "Backspace",
    ">" to "Tab",
    "↵" to "Enter",
    "␣" to "Space",
    "D" to "Delete",
    "─ │" to "Arrow keys (straight line)",
)

private val Shortcuts = listOf(
    "C" to "Copy",
    "V" to "Paste",
    "X" to "Cut",
    "Z" to "Undo",
    "Y" to "Redo",
    "∧" to "Select all (A)",
    "S" to "Save",
    "F" to "Find",
    "T" to "Switch window (Alt+Tab)",
    "W" to "Start menu",
)

/** Every gesture on one card, shown inside Home on the glasses. */
@Composable
fun GesturesPanel(onDone: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .background(Color(0xFF111319), RoundedCornerShape(20.dp))
                .border(1.dp, GlassesColors.CardBorder, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 20.dp),
        ) {
            Text("Gestures", color = GlassesColors.Text, fontSize = 22.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.width(300.dp)) {
                    Group("On the trackpad", Trackpad)
                    Spacer(Modifier.height(12.dp))
                    Group("Switching & typing", Switching)
                }
                Column(Modifier.width(200.dp)) {
                    Group("Draw a key", Keys, symbols = true)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Hold your finger still a moment, then draw.",
                        color = GlassesColors.Dim,
                        fontSize = 12.sp,
                    )
                }
                Column(Modifier.width(220.dp)) {
                    Group("Draw a shortcut (Ctrl + letter)", Shortcuts, symbols = true)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button("Done", enabled = true, onClick = onDone)
        }
    }
}

@Composable
private fun Group(title: String, rows: List<Pair<String, String>>, symbols: Boolean = false) {
    Text(title, color = GlassesColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(6.dp))
    for ((gesture, action) in rows) {
        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                gesture,
                color = GlassesColors.Text,
                fontSize = if (symbols) 16.sp else 13.sp,
                fontWeight = if (symbols) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.width(if (symbols) 44.dp else 150.dp),
            )
            Text(action, color = GlassesColors.Dim, fontSize = 13.sp)
        }
    }
}
