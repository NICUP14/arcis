package app.arcis.controller

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import androidx.core.view.WindowInsetsCompat
import app.arcis.core.input.Gesture
import app.arcis.core.input.GestureConfig
import app.arcis.core.input.ShapeCommand
import app.arcis.core.input.TouchAction
import app.arcis.core.input.TouchEvent
import app.arcis.core.input.TouchPoint
import app.arcis.core.input.TrackpadGestureEngine

/**
 * Black, full-size touch surface that drives [TrackpadGestureEngine].
 * Draws only faint guides (bottom-edge switch strip, optional right-edge scroll
 * strip) so the OLED stays dark and the phone can be used without looking.
 */
@SuppressLint("ViewConstructor")
class TrackpadView(
    context: Context,
    private val rightEdgeScroll: Boolean,
    private val onGesture: (Gesture) -> Unit,
) : View(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val engine = TrackpadGestureEngine(1f, 1f, config(), ::dispatch)
    private val handler = Handler(Looper.getMainLooper())
    private val timer = Runnable {
        engine.onTimer(SystemClock.uptimeMillis())
        scheduleTimer()
    }

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2A2D35.toInt()
        strokeWidth = dp(2f)
    }

    init {
        setBackgroundColor(0xFF000000.toInt())
        isHapticFeedbackEnabled = true
        keepScreenOn = true
    }

    private fun config() = GestureConfig(
        touchSlop = dp(8f),
        bottomEdge = dp(44f),
        rightEdge = if (rightEdgeScroll) dp(44f) else 0f,
        switchDistance = dp(60f),
        homeDistance = dp(50f),
        recentsStep = dp(48f),
        recentsCloseDistance = dp(80f),
    )

    // Space the (hidden) system navigation bar can slide into. Edge zones are kept inside it
    // so a swipe meant for Arcis never lands on Back/Home/Recents when the bar reappears.
    private var insetRight = 0
    private var insetBottom = 0

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val compat = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
        val nav = compat.getInsetsIgnoringVisibility(
            WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        insetRight = nav.right
        insetBottom = nav.bottom
        resizeEngine()
        invalidate()
        return insets
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = resizeEngine()

    private fun resizeEngine() {
        engine.resize((width - insetRight).toFloat(), (height - insetBottom).toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width - insetRight
        val bottom = height - insetBottom - engine.config.bottomEdge
        canvas.drawLine(w * 0.3f, bottom, w * 0.7f, bottom, guidePaint)
        if (engine.config.rightEdge > 0f) {
            val x = w - engine.config.rightEdge
            canvas.drawLine(x, height * 0.2f, x, bottom - dp(16f), guidePaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> TouchAction.DOWN
            MotionEvent.ACTION_MOVE -> TouchAction.MOVE
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> TouchAction.UP
            MotionEvent.ACTION_CANCEL -> TouchAction.CANCEL
            else -> return true
        }
        val points = (0 until event.pointerCount).map {
            TouchPoint(event.getPointerId(it), event.getX(it), event.getY(it))
        }
        val actionId = event.getPointerId(event.actionIndex)
        engine.onTouch(TouchEvent(action, points, actionId, event.eventTime))
        scheduleTimer()
        return true
    }

    private fun scheduleTimer() {
        handler.removeCallbacks(timer)
        engine.nextTimerAt()?.let { handler.postAtTime(timer, it) }
    }

    private fun dispatch(gesture: Gesture) {
        haptic(gesture)?.let { performHapticFeedback(it) }
        onGesture(gesture)
    }

    private fun haptic(g: Gesture): Int? = when (g) {
        is Gesture.PointerMove, is Gesture.Scroll -> null
        is Gesture.Click -> HapticFeedbackConstants.VIRTUAL_KEY
        Gesture.DragStart -> HapticFeedbackConstants.LONG_PRESS
        Gesture.DragEnd -> HapticFeedbackConstants.VIRTUAL_KEY_RELEASE
        Gesture.Back, Gesture.Home -> HapticFeedbackConstants.CONFIRM
        is Gesture.SwitchApp -> HapticFeedbackConstants.CONFIRM
        Gesture.RecentsShow -> HapticFeedbackConstants.LONG_PRESS
        is Gesture.RecentsMove -> HapticFeedbackConstants.CLOCK_TICK
        Gesture.RecentsOpenSelected -> HapticFeedbackConstants.CONFIRM
        Gesture.RecentsCloseSelected -> HapticFeedbackConstants.REJECT
        Gesture.ShapeArmed -> HapticFeedbackConstants.CLOCK_TICK
        // Each shortcut feels different, so you know which one was recognized.
        is Gesture.ShapeDrawn -> when (g.command) {
            ShapeCommand.COPY -> HapticFeedbackConstants.CONTEXT_CLICK
            ShapeCommand.PASTE -> HapticFeedbackConstants.GESTURE_END
            ShapeCommand.UNDO -> HapticFeedbackConstants.LONG_PRESS
            ShapeCommand.ENTER -> HapticFeedbackConstants.CONFIRM
            ShapeCommand.BACKSPACE -> HapticFeedbackConstants.KEYBOARD_TAP
            null -> HapticFeedbackConstants.REJECT
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(timer)
        super.onDetachedFromWindow()
    }
}
