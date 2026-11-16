package app.arcis.a11y

import android.accessibilityservice.AccessibilityService
import android.graphics.PointF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import app.arcis.ArcisApp
import app.arcis.core.text.SpokenPunctuation
import app.arcis.display.ExternalScreen
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

    /** Hook for hardware keys (volume) used by later features; return true to consume. */
    var keyHandler: ((KeyEvent) -> Boolean)? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "service connected")
        instance = this
        keyHandler = ArcisApp.instance.hardwareKeys::onKey
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
        }
    }

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean = keyHandler?.invoke(event) ?: false

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

    fun setCursorVisible(visible: Boolean) = cursor?.setVisible(visible)

    /**
     * Types [text] into the selected (focused) text field on the external screen, at the
     * caret, replacing any selection. Returns false if no editable field has focus there.
     */
    fun insertText(text: String): Boolean {
        val displayId = screen?.displayId ?: return false
        val node = windowsOnAllDisplays.get(displayId).orEmpty()
            .asSequence()
            .mapNotNull { it.root?.let(::focusedEditable) }
            .firstOrNull()
            ?: return false
        node.refresh() // make sure text and caret are current
        val current = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
        var start = node.textSelectionStart
        var end = node.textSelectionEnd
        if (start < 0 || end < 0 || start > current.length || end > current.length) {
            start = current.length
            end = current.length
        }
        val before = current.substring(0, minOf(start, end))
        val after = current.substring(maxOf(start, end))
        val piece = SpokenPunctuation.join(before, text)
        val setText = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, before + piece + after)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setText)) return false
        val caret = before.length + piece.length
        node.performAction(
            AccessibilityNodeInfo.ACTION_SET_SELECTION,
            Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, caret)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, caret)
            },
        )
        return true
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
        val pkg = top?.root?.packageName?.toString()
        if (pkg != null) _foregroundPackage.value = pkg
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

        /** Package of the app currently in front on the external screen. */
        val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()
    }
}
