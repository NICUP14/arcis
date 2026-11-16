package app.arcis.core.input

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Turns raw touches on the phone into trackpad [Gesture]s.
 *
 * Event-driven and clock-free: the host forwards touches via [onTouch] and
 * calls [onTimer] when [nextTimerAt] is reached (for long-press and the
 * Recents hold). Emitted gestures go to [sink].
 *
 * Surface zones (decided by where the first finger lands):
 * - bottom edge strip: switch app (←/→), Home (quick swipe up), Recents (swipe up + hold)
 * - right edge strip (optional): vertical scroll
 * - everything else: trackpad (move, tap, double-tap-drag, long-press, 2-finger scroll/tap)
 */
class TrackpadGestureEngine(
    private var width: Float,
    private var height: Float,
    var config: GestureConfig = GestureConfig(),
    private val sink: (Gesture) -> Unit,
) {
    private enum class Zone { TRACKPAD, BOTTOM_EDGE, RIGHT_EDGE }

    private enum class Mode {
        IDLE,
        PENDING,        // one finger down, not moved yet
        MOVING,         // one finger moving the pointer
        SHAPE,          // rested, then drawing a shortcut shape
        DRAGGING,       // double-tap-hold drag
        LONG_PRESSED,   // long-press fired; ignore until up
        TWO_PENDING,    // two fingers down, not moved yet
        TWO_SCROLLING,
        EDGE_SCROLL,
        BOTTOM_PENDING,
        BOTTOM_HORIZONTAL,
        BOTTOM_UP,      // moving up, may become Home or Recents
        RECENTS,
        IGNORE,         // finish gesture silently
    }

    private var mode = Mode.IDLE
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastTime = 0L
    private var lastCentroidX = 0f
    private var lastCentroidY = 0f
    private var twoDownTime = 0L
    private var maxPointers = 0

    private var lastTapUpTime = Long.MIN_VALUE / 2
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var armedForDrag = false
    private var shapeArmed = false
    private val shapePoints = mutableListOf<Point>()
    private val shapes = ShapeRecognizer()

    private var timerAt: Long? = null
    private var bottomStillSince = 0L
    private var recentsAnchorX = 0f
    private var recentsAnchorY = 0f
    private var recentsSteps = 0

    /** When the host should call [onTimer], or null. */
    fun nextTimerAt(): Long? = timerAt

    fun resize(width: Float, height: Float) {
        this.width = width
        this.height = height
    }

    fun onTouch(event: TouchEvent) {
        when (event.action) {
            TouchAction.DOWN -> onDown(event)
            TouchAction.MOVE -> onMove(event)
            TouchAction.UP -> onUp(event)
            TouchAction.CANCEL -> reset()
        }
    }

    fun onTimer(nowMs: Long) {
        val due = timerAt ?: return
        if (nowMs < due) return
        timerAt = null
        when (mode) {
            Mode.PENDING -> if (!shapeArmed && config.shapesEnabled && !armedForDrag && nowMs < downTime + config.longPressMs) {
                shapeArmed = true
                sink(Gesture.ShapeArmed)
                timerAt = downTime + config.longPressMs
            } else {
                mode = Mode.LONG_PRESSED
                armedForDrag = false
                sink(Gesture.Click(Button.RIGHT))
            }
            Mode.BOTTOM_UP -> enterRecents()
            else -> Unit
        }
    }

    // ---------------------------------------------------------------- down

    private fun onDown(e: TouchEvent) {
        val count = e.points.size
        maxPointers = maxOf(maxPointers, count)
        if (count == 1) {
            val p = e.points.first()
            downX = p.x; downY = p.y; downTime = e.timeMs
            lastX = p.x; lastY = p.y; lastTime = e.timeMs
            maxPointers = 1
            when (zoneOf(p.x, p.y)) {
                Zone.BOTTOM_EDGE -> {
                    mode = Mode.BOTTOM_PENDING
                    timerAt = null
                }
                Zone.RIGHT_EDGE -> {
                    mode = Mode.EDGE_SCROLL
                    timerAt = null
                }
                Zone.TRACKPAD -> {
                    armedForDrag = e.timeMs - lastTapUpTime <= config.doubleTapTimeoutMs &&
                        hypot(p.x - lastTapX, p.y - lastTapY) <= config.touchSlop * 3
                    mode = Mode.PENDING
                    shapeArmed = false
                    timerAt = e.timeMs + if (config.shapesEnabled && !armedForDrag) config.shapeHoldMs else config.longPressMs
                }
            }
            return
        }
        // Second (or more) finger.
        if (mode == Mode.SHAPE) {
            mode = Mode.IGNORE // a second finger cancels the shape
            return
        }
        // Fingers rarely land together: a second finger shortly after the first still means "two-finger".
        val lateSecondFinger = mode == Mode.MOVING && e.timeMs - downTime <= TWO_FINGER_GRACE_MS
        when {
            mode == Mode.PENDING || mode == Mode.TWO_PENDING || lateSecondFinger -> {
                if (count == 2) {
                    mode = Mode.TWO_PENDING
                    twoDownTime = e.timeMs
                    timerAt = null
                    centroid(e.points).let { (cx, cy) -> lastCentroidX = cx; lastCentroidY = cy }
                } else {
                    mode = Mode.IGNORE
                }
            }
            mode == Mode.TWO_SCROLLING -> if (count > 2) mode = Mode.IGNORE
            mode == Mode.DRAGGING -> Unit // extra finger while dragging: keep dragging
            else -> if (mode != Mode.IGNORE) {
                // Fingers added mid-gesture: finish what we were doing cleanly.
                finishActive()
                mode = Mode.IGNORE
            }
        }
    }

    // ---------------------------------------------------------------- move

    private fun onMove(e: TouchEvent) {
        if (e.points.isEmpty()) return
        when (mode) {
            Mode.PENDING -> {
                val p = e.points.first()
                if (hypot(p.x - downX, p.y - downY) > config.touchSlop) {
                    timerAt = null
                    if (shapeArmed) {
                        mode = Mode.SHAPE
                        shapePoints.clear()
                        shapePoints += Point(downX, downY)
                        shapePoints += Point(p.x, p.y)
                        return
                    }
                    mode = if (armedForDrag) {
                        sink(Gesture.DragStart)
                        Mode.DRAGGING
                    } else {
                        Mode.MOVING
                    }
                    emitPointer(p, e.timeMs)
                }
            }
            Mode.MOVING, Mode.DRAGGING -> emitPointer(e.points.first(), e.timeMs)
            Mode.SHAPE -> e.points.first().let { shapePoints += Point(it.x, it.y) }
            Mode.TWO_PENDING, Mode.TWO_SCROLLING -> {
                if (e.points.size < 2) return
                val (cx, cy) = centroid(e.points)
                val dx = cx - lastCentroidX
                val dy = cy - lastCentroidY
                if (mode == Mode.TWO_PENDING) {
                    // lastCentroid still holds the centroid from when the 2nd finger landed.
                    if (hypot(dx, dy) <= config.touchSlop) return
                    mode = Mode.TWO_SCROLLING
                }
                emitScroll(dx, dy)
                lastCentroidX = cx; lastCentroidY = cy
            }
            Mode.EDGE_SCROLL -> {
                val p = e.points.first()
                emitScroll(0f, p.y - lastY)
                lastX = p.x; lastY = p.y
            }
            Mode.BOTTOM_PENDING -> {
                val p = e.points.first()
                val dx = p.x - downX
                val dy = p.y - downY
                if (hypot(dx, dy) <= config.touchSlop) return
                mode = if (abs(dx) > abs(dy)) {
                    Mode.BOTTOM_HORIZONTAL
                } else if (dy < 0) {
                    bottomStillSince = e.timeMs
                    Mode.BOTTOM_UP
                } else {
                    Mode.IGNORE
                }
                lastX = p.x; lastY = p.y; lastTime = e.timeMs
                if (mode == Mode.BOTTOM_UP) armRecentsTimer(p)
            }
            Mode.BOTTOM_UP -> {
                val p = e.points.first()
                if (hypot(p.x - lastX, p.y - lastY) > config.touchSlop / 3) {
                    // Still moving: restart the hold timer.
                    bottomStillSince = e.timeMs
                    lastX = p.x; lastY = p.y
                }
                armRecentsTimer(p)
                val due = timerAt
                if (due != null && e.timeMs >= due) {
                    timerAt = null
                    enterRecents()
                }
            }
            Mode.RECENTS -> {
                val p = e.points.first()
                val steps = ((p.x - recentsAnchorX) / config.recentsStep).toInt()
                if (steps != recentsSteps) {
                    sink(Gesture.RecentsMove(steps - recentsSteps))
                    recentsSteps = steps
                }
                lastX = p.x; lastY = p.y
            }
            else -> Unit
        }
    }

    // ---------------------------------------------------------------- up

    private fun onUp(e: TouchEvent) {
        val remaining = e.points.size - 1
        if (remaining > 0) {
            // A finger lifted but others remain.
            when (mode) {
                Mode.TWO_PENDING, Mode.TWO_SCROLLING -> {
                    // Wait for all fingers; a two-finger tap is decided on the last UP.
                }
                Mode.DRAGGING -> Unit
                else -> Unit
            }
            return
        }
        val p = e.points.firstOrNull { it.id == e.actionPointerId } ?: e.points.first()
        when (mode) {
            Mode.PENDING -> {
                if (e.timeMs - downTime <= config.longPressMs) {
                    sink(Gesture.Click(Button.LEFT))
                    lastTapUpTime = e.timeMs
                    lastTapX = p.x; lastTapY = p.y
                }
            }
            Mode.DRAGGING -> Unit // reset() below ends the drag
            Mode.SHAPE -> {
                shapePoints += Point(p.x, p.y)
                sink(Gesture.ShapeDrawn(shapes.recognize(shapePoints)?.command))
            }
            Mode.TWO_PENDING -> {
                if (maxPointers == 2 && e.timeMs - twoDownTime <= config.tapTimeoutMs * 2) {
                    sink(Gesture.Back)
                }
            }
            Mode.BOTTOM_HORIZONTAL -> {
                val dx = p.x - downX
                if (abs(dx) >= config.switchDistance) {
                    sink(
                        Gesture.SwitchApp(
                            if (dx > 0) SwitchDirection.PREVIOUS else SwitchDirection.NEXT,
                        ),
                    )
                }
            }
            Mode.BOTTOM_UP -> {
                if (downY - p.y >= config.homeDistance) sink(Gesture.Home)
            }
            Mode.RECENTS -> {
                if (recentsAnchorY - p.y >= config.recentsCloseDistance) {
                    sink(Gesture.RecentsCloseSelected)
                } else {
                    sink(Gesture.RecentsOpenSelected)
                }
            }
            else -> Unit
        }
        reset()
    }

    // ---------------------------------------------------------------- helpers

    /** Recents opens after holding still for a moment once the finger has risen far enough. */
    private fun armRecentsTimer(p: TouchPoint) {
        timerAt = if (downY - p.y >= config.homeDistance) bottomStillSince + config.recentsHoldMs else null
    }

    private fun enterRecents() {
        mode = Mode.RECENTS
        recentsAnchorX = lastX
        recentsAnchorY = lastY
        recentsSteps = 0
        sink(Gesture.RecentsShow)
    }

    private fun finishActive() {
        when (mode) {
            Mode.DRAGGING -> sink(Gesture.DragEnd)
            Mode.RECENTS -> sink(Gesture.RecentsOpenSelected)
            else -> Unit
        }
        timerAt = null
    }

    private fun reset() {
        if (mode == Mode.DRAGGING) sink(Gesture.DragEnd)
        mode = Mode.IDLE
        timerAt = null
        maxPointers = 0
        armedForDrag = false
    }

    private fun zoneOf(x: Float, y: Float): Zone = when {
        y >= height - config.bottomEdge -> Zone.BOTTOM_EDGE
        config.rightEdge > 0f && x >= width - config.rightEdge -> Zone.RIGHT_EDGE
        else -> Zone.TRACKPAD
    }

    private fun emitPointer(p: TouchPoint, timeMs: Long) {
        val dx = p.x - lastX
        val dy = p.y - lastY
        val dt = (timeMs - lastTime).coerceAtLeast(1)
        lastX = p.x; lastY = p.y; lastTime = timeMs
        if (dx == 0f && dy == 0f) return
        val d = config.density
        val gain = pointerGain(hypot(dx, dy) / d / dt)
        sink(Gesture.PointerMove(dx / d * gain, dy / d * gain))
    }

    /** Linear acceleration: faster finger = proportionally larger pointer travel. */
    internal fun pointerGain(speedDpPerMs: Float): Float = config.pointerSpeed *
        min(config.pointerSensitivity * (1f + config.pointerAcceleration * speedDpPerMs), config.maxPointerGain)

    private fun emitScroll(dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        val s = config.scrollSensitivity * config.scrollSpeed * if (config.naturalScroll) -1f else 1f
        sink(Gesture.Scroll(dx * s, dy * s))
    }

    private fun centroid(points: List<TouchPoint>): Pair<Float, Float> {
        val n = points.size.toFloat()
        return points.sumOf { it.x.toDouble() }.toFloat() / n to points.sumOf { it.y.toDouble() }.toFloat() / n
    }

    private companion object {
        const val TWO_FINGER_GRACE_MS = 150L
    }
}
