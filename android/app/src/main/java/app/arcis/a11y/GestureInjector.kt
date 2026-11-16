package app.arcis.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Performs touch gestures on one display through [AccessibilityService.dispatchGesture].
 *
 * Only one gesture can be in flight at a time, so discrete actions are queued
 * and continuous input (scroll, drag) is coalesced while the previous gesture runs.
 * Must be used from the main thread.
 */
class GestureInjector(
    private val service: AccessibilityService,
    private val displayId: Int,
    private val bounds: () -> PointF, // display width / height in px
) {
    private val queue = ArrayDeque<GestureDescription>()
    private var busy = false

    // Coalesced scroll (finger travel in px).
    private var scrollFrom: PointF? = null
    private var scrollDx = 0f
    private var scrollDy = 0f

    // Drag state.
    private var dragStroke: GestureDescription.StrokeDescription? = null
    private var dragLast = PointF()
    private var dragTarget = PointF()
    private var dragEnding = false
    private var dragActive = false

    fun tap(x: Float, y: Float) = enqueue(single(x, y, x, y, TAP_MS))

    fun longPress(x: Float, y: Float) = enqueue(single(x, y, x, y, LONG_PRESS_MS))

    /** Swipe the finger by (fingerDx, fingerDy) starting near (x, y). */
    fun scroll(x: Float, y: Float, fingerDx: Float, fingerDy: Float) {
        if (scrollFrom == null) scrollFrom = PointF(x, y)
        scrollDx += fingerDx
        scrollDy += fingerDy
        pump()
    }

    fun dragStart(x: Float, y: Float) {
        dragActive = true
        dragEnding = false
        dragLast = PointF(x, y)
        dragTarget = PointF(x, y)
        dragStroke = null
        pump()
    }

    fun dragMove(x: Float, y: Float) {
        if (!dragActive) return
        dragTarget = PointF(x, y)
        pump()
    }

    fun dragEnd() {
        if (!dragActive) return
        dragEnding = true
        pump()
    }

    fun cancelAll() {
        queue.clear()
        scrollFrom = null
        scrollDx = 0f
        scrollDy = 0f
        dragActive = false
        dragStroke = null
    }

    private fun enqueue(g: GestureDescription) {
        queue.addLast(g)
        pump()
    }

    private fun pump() {
        if (busy) return
        val next = when {
            dragActive -> nextDragSegment()
            queue.isNotEmpty() -> queue.removeFirst()
            scrollFrom != null -> nextScroll()
            else -> null
        } ?: return
        busy = true
        val ok = service.dispatchGesture(
            next,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = done()
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "gesture cancelled on display $displayId")
                    done()
                }
            },
            null,
        )
        if (!ok) {
            Log.w(TAG, "dispatchGesture rejected on display $displayId")
            busy = false
        }
    }

    private fun done() {
        busy = false
        pump()
    }

    private fun nextDragSegment(): GestureDescription? {
        val prev = dragStroke
        if (prev == null) {
            // Finger down at the start point, to be continued.
            val s = GestureDescription.StrokeDescription(pointPath(dragLast), 0, 1, true)
            dragStroke = s
            return build(s)
        }
        val moved = hypot(dragTarget.x - dragLast.x, dragTarget.y - dragLast.y) >= 1f
        if (!moved && !dragEnding) return null
        val path = Path().apply {
            moveTo(dragLast.x, dragLast.y)
            lineTo(dragTarget.x, dragTarget.y)
        }
        val duration = max(SEGMENT_MIN_MS, (hypot(dragTarget.x - dragLast.x, dragTarget.y - dragLast.y) / 2f).toLong())
        val s = prev.continueStroke(path, 0, duration, !dragEnding)
        dragLast = PointF(dragTarget.x, dragTarget.y)
        if (dragEnding) {
            dragActive = false
            dragStroke = null
        } else {
            dragStroke = s
        }
        return build(s)
    }

    private fun nextScroll(): GestureDescription? {
        val from = scrollFrom ?: return null
        val size = bounds()
        val margin = 8f
        // One swipe can travel at most ~80% of the screen; the rest waits for the next swipe.
        val maxX = (size.x - 2 * margin) * 0.8f
        val maxY = (size.y - 2 * margin) * 0.8f
        val dx = scrollDx.coerceIn(-maxX, maxX)
        val dy = scrollDy.coerceIn(-maxY, maxY)
        scrollDx -= dx
        scrollDy -= dy
        if (abs(scrollDx) < 2f && abs(scrollDy) < 2f) {
            scrollFrom = null
            scrollDx = 0f
            scrollDy = 0f
        }
        if (abs(dx) < 2f && abs(dy) < 2f) return null
        // Keep both ends of the swipe on screen: shift the start point if needed.
        val sx = (from.x).coerceIn(margin - minOf(0f, dx), size.x - margin - maxOf(0f, dx))
            .coerceIn(margin, size.x - margin)
        val sy = (from.y).coerceIn(margin - minOf(0f, dy), size.y - margin - maxOf(0f, dy))
            .coerceIn(margin, size.y - margin)
        val ex = (sx + dx).coerceIn(margin, size.x - margin)
        val ey = (sy + dy).coerceIn(margin, size.y - margin)
        // Slow enough not to fling much: ~1.2 px/ms, at least 60 ms.
        val duration = max(60L, (hypot(ex - sx, ey - sy) / 1.2f).toLong())
        return single(sx, sy, ex, ey, duration)
    }

    private fun single(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): GestureDescription {
        val path = Path().apply {
            moveTo(x1, y1)
            if (x1 != x2 || y1 != y2) lineTo(x2, y2)
        }
        return build(GestureDescription.StrokeDescription(path, 0, durationMs))
    }

    private fun pointPath(p: PointF) = Path().apply { moveTo(p.x, p.y) }

    private fun build(stroke: GestureDescription.StrokeDescription): GestureDescription =
        GestureDescription.Builder().setDisplayId(displayId).addStroke(stroke).build()

    private companion object {
        const val TAG = "ArcisInject"
        const val TAP_MS = 20L
        const val LONG_PRESS_MS = 650L
        const val SEGMENT_MIN_MS = 16L
    }
}
