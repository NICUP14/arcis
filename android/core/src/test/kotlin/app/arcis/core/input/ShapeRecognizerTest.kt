package app.arcis.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class ShapeRecognizerTest {

    private val recognizer = ShapeRecognizer()

    /** Polyline through [corners] in a box of [size] at ([ox],[oy]) with a little wobble. */
    private fun stroke(size: Float, ox: Float, oy: Float, vararg corners: Pair<Float, Float>): List<Point> {
        val pts = mutableListOf<Point>()
        var k = 0
        for (i in 0 until corners.size - 1) {
            val (x1, y1) = corners[i]
            val (x2, y2) = corners[i + 1]
            for (s in 0 until 15) {
                val t = s / 15f
                val wobble = sin(k++ * 1.7f) * size * 0.02f
                pts += Point(ox + (x1 + t * (x2 - x1)) * size + wobble, oy + (y1 + t * (y2 - y1)) * size - wobble)
            }
        }
        val (lx, ly) = corners.last()
        pts += Point(ox + lx * size, oy + ly * size)
        return pts
    }

    private fun cStroke(size: Float, ox: Float, oy: Float): List<Point> = (0..30).map { s ->
        val a = Math.toRadians(-40.0 - 280.0 * s / 30.0)
        Point((ox + size / 2 + size / 2 * cos(a)).toFloat(), (oy + size / 2 + size / 2 * sin(a)).toFloat())
    }

    private fun recognize(points: List<Point>) = recognizer.recognize(points)?.command

    @Test
    fun recognizesC() {
        assertEquals(ShapeCommand.COPY, recognize(cStroke(300f, 100f, 200f)))
        assertEquals(ShapeCommand.COPY, recognize(cStroke(120f, 900f, 50f)))
    }

    @Test
    fun recognizesV() {
        assertEquals(ShapeCommand.PASTE, recognize(stroke(260f, 50f, 50f, 0f to 0f, 0.5f to 1f, 1f to 0f)))
        // A narrower V still counts.
        assertEquals(ShapeCommand.PASTE, recognize(stroke(200f, 400f, 100f, 0.15f to 0f, 0.5f to 1f, 0.85f to 0f)))
    }

    @Test
    fun recognizesZ() {
        assertEquals(ShapeCommand.UNDO, recognize(stroke(240f, 10f, 10f, 0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)))
    }

    @Test
    fun recognizesEnter() {
        assertEquals(ShapeCommand.ENTER, recognize(stroke(220f, 300f, 300f, 1f to 0f, 1f to 1f, 0f to 1f)))
    }

    @Test
    fun recognizesBackspace() {
        assertEquals(ShapeCommand.BACKSPACE, recognize(stroke(200f, 0f, 0f, 1f to 0f, 0f to 0.5f, 1f to 1f)))
    }

    @Test
    fun vAndLessThanStayDifferent() {
        val v = recognize(stroke(200f, 0f, 0f, 0f to 0f, 0.5f to 1f, 1f to 0f))
        val lt = recognize(stroke(200f, 0f, 0f, 1f to 0f, 0f to 0.5f, 1f to 1f))
        assertNotEquals(v, lt)
    }

    @Test
    fun straightLinesAreArrowKeys() {
        assertEquals(ShapeCommand.RIGHT, recognize(stroke(300f, 0f, 0f, 0f to 0.5f, 1f to 0.5f)))
        assertEquals(ShapeCommand.LEFT, recognize(stroke(300f, 0f, 0f, 1f to 0.5f, 0f to 0.5f)))
        assertEquals(ShapeCommand.UP, recognize(stroke(300f, 0f, 0f, 0.5f to 1f, 0.5f to 0f)))
        assertEquals(ShapeCommand.DOWN, recognize(stroke(300f, 0f, 0f, 0.5f to 0f, 0.5f to 1f)))
    }

    @Test
    fun rejectsDots() {
        assertNull(recognize(listOf(Point(1f, 1f), Point(1f, 1f), Point(1f, 1f), Point(1f, 1f))))
    }

    @Test
    fun tabAndBackspaceAreMirrorImages() {
        assertEquals(ShapeCommand.TAB, recognize(stroke(200f, 0f, 0f, 0f to 0f, 1f to 0.5f, 0f to 1f)))
        assertEquals(ShapeCommand.SELECT_ALL, recognize(stroke(200f, 0f, 0f, 0f to 1f, 0.5f to 0f, 1f to 1f)))
    }
}
