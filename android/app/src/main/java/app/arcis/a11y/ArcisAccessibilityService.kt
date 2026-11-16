package app.arcis.a11y

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.graphics.PointF
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.accessibilityservice.InputMethod.AccessibilityInputConnection
import androidx.annotation.RequiresApi
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import app.arcis.ArcisApp
import app.arcis.core.input.ShapeCommand
import app.arcis.core.text.SpokenPunctuation
import app.arcis.display.ExternalScreen
import app.arcis.host.HostTarget
import app.arcis.host.Moonlight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Performs Arcis input on the external screen: draws the cursor there and
 * injects taps, long-presses, drags, scrolls and Back into whatever app is shown.
 * Uses public accessibility APIs only (no root, no Shizuku).
 */
class ArcisAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var screen: ExternalScreen? = null
    private var cursor: CursorOverlay? = null

    /** Switcher cards and status flashes on the external screen. */
    var uiOverlay: GlassesUiOverlay? = null
        private set
    private var injector: GestureInjector? = null
    private var x = 0f
    private var y = 0f
    private var dragging = false


    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "service connected")
        instance = this
        _connected.value = true
        scope.launch {
            ArcisApp.instance.displays.screen.collect { attachTo(it) }
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.i(TAG, "service unbound")
        detach()
        instance = null
        _connected.value = false
        scope.cancel()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        detach()
        if (instance === this) instance = null
        _connected.value = false
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            updateForegroundPackage()
            updateKeyboardOnPhone()
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) noteActivityEvent(event)
    }

    override fun onInterrupt() = Unit

    // ------------------------------------------------------------------ display

    private fun attachTo(newScreen: ExternalScreen?) {
        if (newScreen?.displayId == screen?.displayId) {
            screen = newScreen
            return
        }
        detach()
        screen = newScreen ?: return
        if (ArcisApp.instance.displays.display() == null) return
        x = newScreen.widthPx / 2f
        y = newScreen.heightPx / 2f
        injector = GestureInjector(this, newScreen.displayId) {
            val s = screen
            PointF((s?.widthPx ?: 1).toFloat(), (s?.heightPx ?: 1).toFloat())
        }
        addOverlays(newScreen.displayId, attempt = 1)
        updateForegroundPackage()
    }

    /**
     * Adds the switcher/flash overlay and the cursor. Right after a screen is plugged in,
     * the accessibility system may not know the display yet (BadTokenException), so retry.
     */
    private fun addOverlays(displayId: Int, attempt: Int) {
        if (screen?.displayId != displayId) return
        val display = ArcisApp.instance.displays.display() ?: return
        if (uiOverlay == null) {
            uiOverlay = runCatching { GlassesUiOverlay(this, display).also { it.show() } }.getOrNull()
        }
        if (cursor == null) {
            // Added after the UI overlay so the cursor stays on top.
            cursor = runCatching { CursorOverlay(this, display).also { it.show(); it.moveTo(x, y) } }.getOrNull()
        }
        if (uiOverlay != null && cursor != null) return
        if (attempt >= OVERLAY_ATTEMPTS) {
            Log.w(TAG, "overlays unavailable on display $displayId")
            return
        }
        mainHandler.postDelayed({ addOverlays(displayId, attempt + 1) }, OVERLAY_RETRY_MS)
    }

    private fun detach() {
        mainHandler.removeCallbacksAndMessages(null)
        injector?.cancelAll()
        injector = null
        cursor?.remove()
        cursor = null
        uiOverlay?.remove()
        uiOverlay = null
        screen = null
        dragging = false
    }

    // ------------------------------------------------------------------ input API (main thread)

    fun movePointer(dx: Float, dy: Float) {
        val s = screen ?: return
        x = (x + dx).coerceIn(0f, s.widthPx - 1f)
        y = (y + dy).coerceIn(0f, s.heightPx - 1f)
        cursor?.moveTo(x, y)
        if (dragging) injector?.dragMove(x, y)
    }

    /** Puts the cursor at an absolute position on the external screen (tests, recentering). */
    fun moveCursorTo(nx: Float, ny: Float) {
        val s = screen ?: return
        x = nx.coerceIn(0f, s.widthPx - 1f)
        y = ny.coerceIn(0f, s.heightPx - 1f)
        cursor?.moveTo(x, y)
    }

    fun click() = injector?.tap(x, y)

    fun longPress() = injector?.longPress(x, y)

    fun dragStart() {
        dragging = true
        injector?.dragStart(x, y)
    }

    fun dragEnd() {
        dragging = false
        injector?.dragEnd()
    }

    /** Scroll content: positive dy shows content further down (finger swipes up). */
    fun scroll(dx: Float, dy: Float) = injector?.scroll(x, y, -dx, -dy)

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)

    /**
     * Opens the phone keyboard for the text field selected on the external screen (a tap on
     * it). False if no field is selected there.
     */
    fun showKeyboardForField(): Boolean =
        focusedField()?.node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false

    /** Closes the phone keyboard of an app on the external screen: Back reaches the keyboard first. */
    fun hideKeyboard() = back()

    private fun updateKeyboardOnPhone() {
        val shown = windowsOnAllDisplays.get(Display.DEFAULT_DISPLAY).orEmpty()
            .any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        _keyboardOnPhone.value = shown
    }

    fun setCursorVisible(visible: Boolean) = cursor?.setVisible(visible)

    /**
     * Types [text] into the selected (focused) text field on the external screen, at the
     * caret, replacing any selection. Returns false if no editable field has focus there.
     */
    fun insertText(text: String): Boolean {
        val field = focusedField() ?: return false
        val ic = connection()
        if (ic != null) {
            // Android 13+: type through our own input connection, like a keyboard would.
            val before = ic.getSurroundingText(200, 0, 0)
                ?.let { it.text.subSequence(0, it.selectionStart.coerceIn(0, it.text.length)).toString() }
                ?: field.before
            ic.commitText(SpokenPunctuation.join(before, text), 1, null)
            return true
        }
        return field.replaceSelection(SpokenPunctuation.join(field.before, text))
    }

    /** Result of a drawn edit shortcut in a phone app. */
    enum class EditResult { DONE, NO_FIELD, UNSUPPORTED }

    /** Text of the last drawn copy (selection, or the whole field if nothing was selected). */
    var lastCopiedText: String? = null
        private set

    /** Copy / paste / undo / Enter / Backspace on the selected text field of the external screen. */
    fun performEdit(command: ShapeCommand): EditResult {
        val field = focusedField() ?: return EditResult.NO_FIELD
        if (command == ShapeCommand.COPY) {
            lastCopiedText = if (field.start == field.end) field.text else field.text.substring(field.start, field.end)
        }
        val ic = connection()
        if (ic != null) {
            // Android 13+: real key events and menu actions, so the keyboard stays in sync.
            when (command) {
                ShapeCommand.COPY -> {
                    if (field.start == field.end) ic.performContextMenuAction(android.R.id.selectAll)
                    ic.performContextMenuAction(android.R.id.copy)
                }
                ShapeCommand.PASTE -> ic.performContextMenuAction(android.R.id.paste)
                ShapeCommand.UNDO -> ic.key(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
                ShapeCommand.ENTER -> ic.key(KeyEvent.KEYCODE_ENTER)
                ShapeCommand.BACKSPACE -> ic.key(KeyEvent.KEYCODE_DEL)
            }
            return EditResult.DONE
        }
        // Android 11-12: node actions (no undo there).
        val node = field.node
        val ok = when (command) {
            ShapeCommand.COPY -> {
                if (field.start == field.end) field.select(0, field.text.length) // nothing selected: copy all
                node.performAction(AccessibilityNodeInfo.ACTION_COPY)
            }
            ShapeCommand.PASTE -> node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            ShapeCommand.ENTER -> node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            ShapeCommand.BACKSPACE -> when {
                field.start != field.end -> field.replaceSelection("")
                field.start > 0 -> field.copy(start = field.start - 1).replaceSelection("")
                else -> true
            }
            ShapeCommand.UNDO -> return EditResult.UNSUPPORTED
        }
        return if (ok) EditResult.DONE else EditResult.UNSUPPORTED
    }

    /** Our own input connection to the focused field (Android 13+, flagInputMethodEditor). */
    private fun connection(): AccessibilityInputConnection? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) inputMethod?.currentInputConnection else null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun AccessibilityInputConnection.key(code: Int, meta: Int = 0) {
        val now = SystemClock.uptimeMillis()
        sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
        sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
    }

    /** The focused text field on the external screen with its current text and selection. */
    private data class Field(val node: AccessibilityNodeInfo, val text: String, val start: Int, val end: Int) {
        val before get() = text.substring(0, start)

        fun select(from: Int, to: Int) = node.performAction(
            AccessibilityNodeInfo.ACTION_SET_SELECTION,
            Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, from)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, to)
            },
        )

        /** Replaces the selection (or inserts at the caret) and puts the caret after it. */
        fun replaceSelection(piece: String): Boolean {
            val newText = text.substring(0, start) + piece + text.substring(end)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            }
            if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false
            select(start + piece.length, start + piece.length)
            return true
        }
    }

    private fun focusedField(): Field? {
        val displayId = screen?.displayId ?: return null
        val node = windowsOnAllDisplays.get(displayId).orEmpty()
            .asSequence()
            .mapNotNull { it.root?.let(::focusedEditable) }
            .firstOrNull()
            ?: return null
        node.refresh() // make sure text and caret are current
        val text = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
        var start = node.textSelectionStart
        var end = node.textSelectionEnd
        if (start < 0 || end < 0 || start > text.length || end > text.length) {
            start = text.length
            end = text.length
        }
        return Field(node, text, minOf(start, end), maxOf(start, end))
    }

    fun cursorPosition(): PointF = PointF(x, y)

    /**
     * The text field with input focus under [root]. View-based apps answer findFocus directly;
     * Compose apps report only their host view, so fall back to searching the node tree.
     */
    private fun focusedEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }?.let { return it }
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val n = queue.removeFirst()
            visited++
            if (n.isEditable && n.isFocused) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let(queue::addLast)
        }
        return null
    }

    // ------------------------------------------------------------------ foreground app on the screen

    private fun updateForegroundPackage() {
        val displayId = screen?.displayId ?: run {
            _foregroundPackage.value = null
            return
        }
        val windows = windowsOnAllDisplays.get(displayId).orEmpty()
        val top = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .maxByOrNull { it.layer }
        val root = top?.root ?: return
        val pkg = root.packageName?.toString() ?: return
        setForeground(pkg)
        if (HostTarget.isMoonlight(pkg)) noteMoonlightScreen(pkg, root)
    }

    /**
     * Which Moonlight screen is showing, read from its window: the activity event can be
     * missed (e.g. other windows changing at the same moment when a stream starts).
     */
    private fun noteMoonlightScreen(pkg: String, root: AccessibilityNodeInfo) {
        val current = _foregroundActivity.value
        val wasStream = current?.packageName == pkg && current.className == Moonlight.STREAM_ACTIVITY
        val isStream = Moonlight.isStream(root)
        if (isStream && !wasStream) _foregroundActivity.value = ComponentName(pkg, Moonlight.STREAM_ACTIVITY)
        if (!isStream && wasStream) _foregroundActivity.value = null
    }

    /**
     * An activity coming to the front on the screen sends a window-state event tagged with
     * its display. Only real activities count, so dialogs, pop-ups and Arcis's own overlays
     * are ignored; Arcis's own screens report themselves through [noteArcisScreenInFront].
     * This tells which screen of an app is showing (Moonlight's menus vs. its stream) and is
     * the fallback for displays whose windows Android doesn't list for accessibility
     * (overlay displays, and possibly some vendor displays).
     */
    private fun noteActivityEvent(event: AccessibilityEvent) {
        val displayId = screen?.displayId ?: return
        if (event.displayId != displayId) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        if (pkg == packageName) return
        val activity = ComponentName(pkg, cls)
        if (runCatching { packageManager.getActivityInfo(activity, 0) }.isFailure) return
        _foregroundActivity.value = activity
        if (!windowsOnAllDisplays.contains(displayId)) setForeground(pkg)
    }

    private fun setForeground(pkg: String) {
        if (pkg == _foregroundPackage.value) return
        Log.d(TAG, "in front on the screen: $pkg")
        _foregroundPackage.value = pkg
    }

    companion object {
        private const val TAG = "ArcisA11y"
        private const val OVERLAY_ATTEMPTS = 10
        private const val OVERLAY_RETRY_MS = 500L
        private const val MAX_NODES = 2_000

        /** The running service, or null when accessibility is off. */
        @Volatile
        var instance: ArcisAccessibilityService? = null
            private set

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        private val _foregroundPackage = MutableStateFlow<String?>(null)
        private val _foregroundActivity = MutableStateFlow<ComponentName?>(null)
        private val _keyboardOnPhone = MutableStateFlow(false)

        /** True while a keyboard is showing on the phone's screen (for any app or field). */
        val keyboardOnPhone: StateFlow<Boolean> = _keyboardOnPhone.asStateFlow()

        /**
         * The last activity seen coming to the front on the screen (null for Arcis's own
         * screens or when unknown). Compare its package with [foregroundPackage].
         */
        val foregroundActivity: StateFlow<ComponentName?> = _foregroundActivity.asStateFlow()

        /** Called by Arcis's own activities on the external screen when they come to the front. */
        fun noteArcisScreenInFront(displayId: Int) {
            val service = instance ?: return
            if (service.screen?.displayId != displayId) return
            _foregroundActivity.value = null
            service.setForeground(service.packageName)
        }

        /** Package of the app currently in front on the external screen. */
        val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()
    }
}
