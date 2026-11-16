package app.arcis.glasses

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.arcis.core.input.Point
import app.arcis.core.input.ShapeCommand
import app.arcis.core.input.ShapeRecognizer

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
    ShapeCommand.BACKSPACE to "Backspace",
    ShapeCommand.TAB to "Tab",
    ShapeCommand.ENTER to "Enter",
    ShapeCommand.SPACE to "Space",
    ShapeCommand.DELETE to "Delete",
    ShapeCommand.RIGHT to "Arrow keys (straight line, any way)",
)

private val Shortcuts = listOf(
    ShapeCommand.COPY to "Copy",
    ShapeCommand.PASTE to "Paste",
    ShapeCommand.CUT to "Cut",
    ShapeCommand.UNDO to "Undo",
    ShapeCommand.REDO to "Redo (Z backwards)",
    ShapeCommand.SELECT_ALL to "Select all",
    ShapeCommand.SAVE to "Save",
    ShapeCommand.FIND to "Find",
    ShapeCommand.SWITCH_WINDOW to "Switch window (Alt+Tab)",
    ShapeCommand.START_MENU to "Start menu",
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
                Column(Modifier.width(230.dp)) {
                    ShapeGroup("Draw a key", Keys)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Rest your finger until it ticks, then draw in one stroke from the dot.",
                        color = GlassesColors.Dim,
                        fontSize = 12.sp,
                    )
                }
                Column(Modifier.width(230.dp)) {
                    ShapeGroup("Draw a shortcut", Shortcuts)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button("Done", enabled = true, onClick = onDone)
        }
    }
}

@Composable
private fun Group(title: String, rows: List<Pair<String, String>>) {
    Text(title, color = GlassesColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(6.dp))
    for ((gesture, action) in rows) {
        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(gesture, color = GlassesColors.Text, fontSize = 13.sp, modifier = Modifier.width(150.dp))
            Text(action, color = GlassesColors.Dim, fontSize = 13.sp)
        }
    }
}

/** Each shortcut as the stroke to draw (the recognizer's own template), dot = where to start. */
@Composable
private fun ShapeGroup(title: String, rows: List<Pair<ShapeCommand, String>>) {
    Text(title, color = GlassesColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(6.dp))
    for ((command, action) in rows) {
        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            StrokeGuide(command, Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Text(action, color = GlassesColors.Dim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StrokeGuide(command: ShapeCommand, modifier: Modifier) {
    val points = remember(command) { ShapeRecognizer.guide(command) }
    Canvas(modifier) {
        val inset = 3.dp.toPx()
        val w = size.width - 2 * inset
        val h = size.height - 2 * inset
        fun at(p: Point) = Offset(inset + p.x * w, inset + p.y * h)
        val path = Path().apply {
            moveTo(at(points.first()).x, at(points.first()).y)
            for (p in points.drop(1)) at(p).let { lineTo(it.x, it.y) }
        }
        drawPath(path, GlassesColors.Text, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(GlassesColors.Accent, radius = 3.5.dp.toPx(), center = at(points.first()))
    }
}
