package app.arcis.a11y

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.view.Display
import android.view.View
import android.view.WindowManager

/**
 * Mouse cursor drawn on the external display above every app, using an
 * accessibility overlay window (non-touchable, non-focusable).
 */
class CursorOverlay(service: AccessibilityService, display: Display) {

    private val context: Context = service
        .createDisplayContext(display)
        .createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val view = CursorView(context)
    private var attached = false

    fun show() {
        if (attached) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        windowManager.addView(view, params)
        attached = true
    }

    fun moveTo(x: Float, y: Float) = view.moveTo(x, y)

    fun setVisible(visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun remove() {
        if (!attached) return
        runCatching { windowManager.removeView(view) }
        attached = false
    }

    @SuppressLint("ViewConstructor")
    private class CursorView(context: Context) : View(context) {
        private val scale = context.resources.displayMetrics.density.coerceAtLeast(1f)
        private var cx = 0f
        private var cy = 0f

        private val arrow = Path().apply {
            // Classic arrow pointer, tip at (0,0).
            moveTo(0f, 0f)
            lineTo(0f, 17f)
            lineTo(4.5f, 13f)
            lineTo(7.5f, 19.5f)
            lineTo(10f, 18.5f)
            lineTo(7.2f, 12.2f)
            lineTo(13f, 12.2f)
            close()
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF000000.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            strokeJoin = Paint.Join.ROUND
        }

        fun moveTo(x: Float, y: Float) {
            cx = x
            cy = y
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(scale * 1.4f, scale * 1.4f)
            canvas.drawPath(arrow, fill)
            canvas.drawPath(arrow, stroke)
            canvas.restore()
        }
    }
}
