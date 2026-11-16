package app.arcis.core.input

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Every shortcut drawn many times with human-like sloppiness (proportions, tilt, wobble,
 * misplaced corners): each must be recognised as itself and almost never as another.
 */
class ShapeVocabularyTest {

    private val recognizer = ShapeRecognizer()

    /** How people draw each command (unit box, y down). */
    private val drawings: Map<ShapeCommand, List<Pair<Float, Float>>> = mapOf(
        ShapeCommand.PASTE to listOf(0f to 0f, 0.5f to 1f, 1f to 0f),
        ShapeCommand.CUT to listOf(0f to 0f, 1f to 1f, 1f to 0f, 0f to 1f),
        ShapeCommand.UNDO to listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f),
        ShapeCommand.REDO to listOf(0f to 0f, 0.5f to 0.5f, 1f to 0f, 0.5f to 0.5f, 0.5f to 1f),
        ShapeCommand.SELECT_ALL to listOf(0f to 1f, 0.5f to 0f, 1f to 1f),
        ShapeCommand.SAVE to listOf(
            0.9f to 0.12f, 0.6f to 0f, 0.25f to 0.03f, 0.08f to 0.2f, 0.25f to 0.42f,
            0.75f to 0.58f, 0.92f to 0.8f, 0.75f to 0.97f, 0.4f to 1f, 0.1f to 0.88f,
        ),
        ShapeCommand.FIND to listOf(1f to 0f, 0f to 0f, 0f to 1f),
        ShapeCommand.ENTER to listOf(1f to 0f, 1f to 1f, 0f to 1f),
        ShapeCommand.BACKSPACE to listOf(1f to 0f, 0f to 0.5f, 1f to 1f),
        ShapeCommand.TAB to listOf(0f to 0f, 1f to 0.5f, 0f to 1f),
        ShapeCommand.SPACE to listOf(0f to 0f, 0f to 0.5f, 1f to 0.5f, 1f to 0f),
        ShapeCommand.DELETE to listOf(0f to 0f, 0f to 1f, 0.6f to 0.92f, 1f to 0.5f, 0.6f to 0.08f, 0f to 0f),
        ShapeCommand.LEFT to listOf(1f to 0.5f, 0f to 0.5f),
        ShapeCommand.RIGHT to listOf(0f to 0.5f, 1f to 0.5f),
        ShapeCommand.UP to listOf(0.5f to 1f, 0.5f to 0f),
        ShapeCommand.DOWN to listOf(0.5f to 0f, 0.5f to 1f),
        ShapeCommand.SWITCH_WINDOW to listOf(0f to 0f, 1f to 0f, 1f to 1f),
        ShapeCommand.START_MENU to listOf(0f to 0f, 0.25f to 1f, 0.5f to 0.35f, 0.75f to 1f, 1f to 0f),
    )

    private fun sloppy(corners: List<Pair<Float, Float>>, rnd: Random, line: Boolean): List<Point> {
        val size = 150f + rnd.nextFloat() * 250f
        val sx = 0.75f + rnd.nextFloat() * 0.5f // squashed or stretched
        val tilt = Math.toRadians((rnd.nextDouble() - 0.5) * 16.0) // ±8°
        val jitter = if (line) 0f else 0.07f
        val pts = mutableListOf<Point>()
        val c = corners.map { (x, y) ->
            (x + (rnd.nextFloat() - 0.5f) * 2 * jitter) to (y + (rnd.nextFloat() - 0.5f) * 2 * jitter)
        }
        var k = rnd.nextInt(100)
        for (i in 0 until c.size - 1) {
            val (x1, y1) = c[i]
            val (x2, y2) = c[i + 1]
            for (s in 0 until 12) {
                val t = s / 12f
                val wobble = sin(k++ * 1.3f) * 0.015f
                pts += Point((x1 + t * (x2 - x1) + wobble) * sx, y1 + t * (y2 - y1) - wobble)
            }
        }
        pts += Point(c.last().first * sx, c.last().second)
        return pts.map { p ->
            val x = p.x * cos(tilt) - p.y * sin(tilt)
            val y = p.x * sin(tilt) + p.y * cos(tilt)
            Point((x * size).toFloat() + 400f, (y * size).toFloat() + 300f)
        }
    }

    private fun sloppyC(rnd: Random): List<Point> {
        val size = 150f + rnd.nextFloat() * 250f
        val start = -35.0 - rnd.nextDouble() * 20.0
        val sweep = 250.0 + rnd.nextDouble() * 50.0
        return (0..30).map { s ->
            val a = Math.toRadians(start - sweep * s / 30.0)
            Point((400 + size / 2 + size / 2 * cos(a)).toFloat(), (300 + size / 2 + size / 2 * sin(a)).toFloat())
        }
    }

    @Test
    fun everyShapeIsRecognisedAndNotConfused() {
        val rnd = Random(42)
        val tries = 40
        val report = StringBuilder()
        var failures = 0
        val all = drawings.keys + ShapeCommand.COPY
        for (command in all) {
            val wrong = mutableMapOf<String, Int>()
            var right = 0
            repeat(tries) {
                val stroke = if (command == ShapeCommand.COPY) {
                    sloppyC(rnd)
                } else {
                    val corners = drawings.getValue(command)
                    sloppy(corners, rnd, line = corners.size == 2)
                }
                val got = recognizer.recognize(stroke)?.command
                if (got == command) right++ else wrong.merge(got?.name ?: "none", 1, Int::plus)
            }
            val misread = wrong.filterKeys { it != "none" }.values.sum()
            val missed = wrong["none"] ?: 0
            report.append("$command: $right/$tries ok, $missed not recognised, wrong=$wrong\n")
            // Recognised nearly always; mistaken for another command almost never.
            if (right < tries * 0.85 || misread > tries * 0.05) failures++
        }
        println(report)
        assertTrue("Shapes not reliable enough:\n$report", failures == 0)
    }

    @Test
    fun diagonalLinesAndScribblesDoNothing() {
        val diagonal = (0..20).map { Point(100f + it * 10f, 100f + it * 10f) }
        assertTrue(recognizer.recognize(diagonal) == null)
    }
}
