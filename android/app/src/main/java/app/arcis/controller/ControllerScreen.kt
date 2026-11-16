package app.arcis.controller

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.arcis.core.input.Gesture
import app.arcis.core.text.TextEdit
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.delay

private val Hint = Color(0xFF3A3F4A)
private val ButtonBorder = Color(0xFF2E323C)

/**
 * The phone side. A fixed corner button (top-left, easy to find by feel) switches mode.
 * - Landscape: the whole screen is the trackpad.
 * - Upright: trackpad on the top half; the bottom half is where the phone's own keyboard
 *   appears when a text field is selected on the external screen.
 */
@Composable
fun ControllerScreen(
    mode: ControllerMode,
    remoteTyping: Boolean,
    onRemoteText: (TextEdit) -> Unit,
    onRemoteEnter: () -> Unit,
    onToggleMode: () -> Unit,
    onGesture: (Gesture) -> Unit,
) {
    var last by remember { mutableStateOf("") }
    LaunchedEffect(last) {
        if (last.isNotEmpty()) {
            delay(1200)
            last = ""
        }
    }
    val forward: (Gesture) -> Unit = { g ->
        if (g !is Gesture.PointerMove && g !is Gesture.Scroll) last = g.toString()
        onGesture(g)
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (mode) {
            ControllerMode.LANDSCAPE -> Trackpad(rightEdgeScroll = true, onGesture = forward, modifier = Modifier.fillMaxSize())
            ControllerMode.UPRIGHT -> Column(Modifier.fillMaxSize()) {
                Trackpad(
                    rightEdgeScroll = false,
                    onGesture = forward,
                    modifier = Modifier.fillMaxWidth().weight(UPRIGHT_TRACKPAD_WEIGHT),
                )
                val area = Modifier.fillMaxWidth().weight(1f - UPRIGHT_TRACKPAD_WEIGHT)
                if (remoteTyping) RelayArea(area, onRemoteText, onRemoteEnter) else KeyboardArea(area)
            }
        }
        // Faint feedback label, for sighted checks only.
        Text(last, color = Hint, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopCenter).padding(16.dp))
        ModeButton(mode, onToggleMode, Modifier.align(Alignment.TopStart))
    }
}

@Composable
private fun Trackpad(rightEdgeScroll: Boolean, onGesture: (Gesture) -> Unit, modifier: Modifier) {
    // Recreate the view when the layout changes so edge zones match the new size.
    AndroidView(
        modifier = modifier,
        factory = { ctx -> TrackpadView(ctx, rightEdgeScroll, onGesture) },
    )
}

/**
 * Typing for a computer: a text field on the phone opens the phone keyboard; each change is
 * sent as Backspaces + new text (TextEdit). The field is cleared now and then.
 */
@Composable
private fun RelayArea(modifier: Modifier, onEdit: (TextEdit) -> Unit, onEnter: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(150)
        focus.requestFocus()
        keyboard?.show()
    }
    Box(modifier.background(Color(0xFF07080A)).padding(16.dp)) {
        BasicTextField(
            value = text,
            onValueChange = { new ->
                val edit = TextEdit.between(text, new)
                if (!edit.isEmpty) onEdit(edit)
                text = if (new.length > 80 && new.endsWith(" ")) "" else new
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onEnter() }),
            textStyle = TextStyle(color = Color(0xFFB8BECC), fontSize = 18.sp),
            cursorBrush = SolidColor(Color(0xFF6EA8FF)),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            decorationBox = { inner ->
                if (text.isEmpty()) Text("Typing goes to your computer", color = Hint, fontSize = 16.sp)
                inner()
            },
        )
    }
}

@Composable
private fun KeyboardArea(modifier: Modifier) {
    Box(modifier.background(Color(0xFF07080A)), contentAlignment = Alignment.Center) {
        Text(
            "Select a text field on your screen —\nyour keyboard opens here",
            color = Hint,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ModeButton(mode: ControllerMode, onToggle: () -> Unit, modifier: Modifier) {
    val view = LocalView.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(12.dp)
            .size(56.dp)
            .border(1.dp, ButtonBorder, RoundedCornerShape(16.dp))
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onToggle()
            },
    ) {
        Text(
            if (mode == ControllerMode.LANDSCAPE) "⌨" else "▭",
            color = Color(0xFF6B7180),
            fontSize = 22.sp,
        )
    }
}

/** Share of the upright screen used by the trackpad; the rest is the keyboard area. */
private const val UPRIGHT_TRACKPAD_WEIGHT = 0.55f
