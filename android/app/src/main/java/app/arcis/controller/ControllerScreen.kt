package app.arcis.controller

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.arcis.R
import app.arcis.core.input.Gesture
import app.arcis.core.text.TextEdit
import app.arcis.input.RemoteTarget
import kotlinx.coroutines.delay

private val Hint = Color(0xFF3A3F4A)
private val ButtonBorder = Color(0xFF2E323C)
private val Icons = Color(0xFF6B7180)
private val Active = Color(0xFF6EA8FF)

/**
 * The phone side: the trackpad fills the screen in both orientations.
 * - Bottom-right, under the thumb and just above the bottom-edge swipe zone: the keyboard
 *   button and the hold-to-talk mic button.
 * - Top-left (rarely needed): switch between upright and landscape.
 * When a keyboard is showing (keyboard button, or a text field selected on the glasses)
 * the trackpad shrinks to the space above it.
 */
@Composable
fun ControllerScreen(
    mode: ControllerMode,
    remoteTarget: RemoteTarget?,
    relayOpen: Boolean,
    keyboardShowing: Boolean,
    onKeyboard: () -> Unit,
    onRemoteText: (TextEdit) -> Unit,
    onRemoteEnter: () -> Unit,
    onToggleMode: () -> Unit,
    onDictationStart: () -> Unit,
    onDictationStop: () -> Unit,
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
    val landscape = mode == ControllerMode.LANDSCAPE
    val keyboardSpace = relayOpen || keyboardShowing
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                // New view per orientation so the edge zones match the new size.
                key(landscape) { Trackpad(rightEdgeScroll = true, onGesture = forward, modifier = Modifier.fillMaxSize()) }
                ThumbButtons(
                    keyboardOn = keyboardSpace,
                    onKeyboard = onKeyboard,
                    onDictationStart = onDictationStart,
                    onDictationStop = onDictationStop,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .then(if (keyboardSpace) Modifier else Modifier.navigationBarInsets())
                        // Clear of the bottom-edge swipe zone and the right-edge scroll strip.
                        .padding(end = EDGE_ZONE + 10.dp, bottom = EDGE_ZONE + 10.dp),
                )
            }
            if (keyboardSpace) {
                val share = if (landscape) LANDSCAPE_KEYBOARD_SHARE else UPRIGHT_KEYBOARD_SHARE
                val area = Modifier.fillMaxWidth().weight(share / (1f - share))
                if (relayOpen && remoteTarget != null) {
                    RelayArea(area, remoteTarget, onRemoteText, onRemoteEnter)
                } else {
                    KeyboardArea(area)
                }
            }
        }
        // Faint feedback label, for sighted checks only.
        Text(last, color = Hint, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopCenter).padding(16.dp))
        ModeButton(mode, onToggleMode, Modifier.align(Alignment.TopStart))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Modifier.navigationBarInsets(): Modifier =
    // The navigation bar is hidden but can slide in; keep the buttons clear of it.
    windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.End + WindowInsetsSides.Bottom))

@Composable
private fun Trackpad(rightEdgeScroll: Boolean, onGesture: (Gesture) -> Unit, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx -> TrackpadView(ctx, rightEdgeScroll, onGesture) },
    )
}

/** Keyboard + mic, side by side; the mic (held most) is nearest the corner. */
@Composable
private fun ThumbButtons(
    keyboardOn: Boolean,
    onKeyboard: () -> Unit,
    onDictationStart: () -> Unit,
    onDictationStop: () -> Unit,
    modifier: Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KeyboardButton(keyboardOn, onKeyboard)
        MicButton(onDictationStart, onDictationStop)
    }
}

@Composable
private fun KeyboardButton(on: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(BUTTON_SIZE)
            .background(if (on) Color(0xFF16324F) else Color.Transparent, RoundedCornerShape(16.dp))
            .border(1.dp, if (on) Active else ButtonBorder, RoundedCornerShape(16.dp))
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onClick()
            },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_keyboard),
            contentDescription = if (on) "Close keyboard" else "Open keyboard",
            tint = if (on) Active else Icons,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** Hold to speak; release to type what was said (into the app or device on the screen). */
@Composable
private fun MicButton(onStart: () -> Unit, onStop: () -> Unit) {
    val view = LocalView.current
    var listening by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(BUTTON_SIZE)
            .background(if (listening) Color(0xFF16324F) else Color.Transparent, RoundedCornerShape(16.dp))
            .border(1.dp, if (listening) Active else ButtonBorder, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    listening = true
                    onStart()
                    tryAwaitRelease()
                    listening = false
                    onStop()
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                })
            },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mic),
            contentDescription = "Hold to speak",
            tint = if (listening) Active else Icons,
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * Typing for a computer or Bluetooth device: a text field on the phone opens the phone
 * keyboard; each change is sent as Backspaces + new text (TextEdit). Cleared now and then.
 */
@Composable
private fun RelayArea(modifier: Modifier, target: RemoteTarget, onEdit: (TextEdit) -> Unit, onEnter: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(150)
        focus.requestFocus()
        keyboard?.show()
    }
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
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
            cursorBrush = SolidColor(Active),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            decorationBox = { inner ->
                if (text.isEmpty()) Text("Typing goes to ${target.label}", color = Hint, fontSize = 16.sp)
                inner()
            },
        )
    }
}

/** Space for the phone keyboard opened for a text field on the glasses. */
@Composable
private fun KeyboardArea(modifier: Modifier) {
    Box(modifier.background(Color(0xFF07080A)), contentAlignment = Alignment.Center) {
        Text("Typing into the selected field", color = Hint, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

/** Switches between holding the phone upright and sideways. */
@Composable
private fun ModeButton(mode: ControllerMode, onToggle: () -> Unit, modifier: Modifier) {
    val view = LocalView.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(12.dp)
            .size(48.dp)
            .border(1.dp, ButtonBorder, RoundedCornerShape(14.dp))
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onToggle()
            },
    ) {
        // Shows the orientation you switch to.
        Text(if (mode == ControllerMode.LANDSCAPE) "▯" else "▭", color = Icons, fontSize = 22.sp)
    }
}

private val BUTTON_SIZE = 60.dp

/** Must match the edge zones in TrackpadView (bottom-edge swipes, right-edge scroll). */
private val EDGE_ZONE = 44.dp

/** Share of the screen the phone keyboard takes when open. */
private const val UPRIGHT_KEYBOARD_SHARE = 0.42f
private const val LANDSCAPE_KEYBOARD_SHARE = 0.58f
