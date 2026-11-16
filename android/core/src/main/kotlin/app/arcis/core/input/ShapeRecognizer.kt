package app.arcis.core.input

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Commands that can be drawn on the trackpad, each a single easy stroke with one finger.
 * Mostly the letter used with Ctrl (C copy, V paste, Z undo, S save) or the symbol on the
 * key (< Backspace, > Tab, ↵ Enter, ⊔ Space, straight lines = arrows); where a letter needs
 * two strokes, a one-stroke stand-in ("\" cut, backwards Z redo, magnifier find, 7 Alt+Tab).
 * [shape] is a short label; [ShapeRecognizer.guide] gives the stroke to draw.
 */
enum class ShapeCommand(val shape: String) {
    COPY("C"),
    PASTE("V"),
    CUT("\\"),
    UNDO("Z"),
    REDO("Z backwards"),
    SELECT_ALL("∧"),
    SAVE("S"),
    FIND("magnifier"),
    ENTER("↵"),
    BACKSPACE("<"),
    TAB(">"),
    SPACE("⊔"),
    DELETE("D"),
    LEFT("←"),
    RIGHT("→"),
    UP("↑"),
    DOWN("↓"),
    SWITCH_WINDOW("7"),
    START_MENU("W"),
}

data class Point(val x: Float, val y: Float)

/**
 * Small unistroke recognizer in the spirit of the "$1 recognizer": resample, scale to a
 * square, centre, then compare point-to-point with fixed templates. Deliberately NOT
 * rotation-invariant, so "V", "∧", "<" and ">" stay different. Straight strokes are
 * recognised separately: horizontal/vertical = arrow keys, "\" = cut. Deterministic.
 */
class ShapeRecognizer(
    private val minScore: Float = 0.78f,
    /** The best match must beat the best other command by this much, or nothing is done. */
    private val minMargin: Float = 0.02f,
) {

    private val templates: Map<ShapeCommand, List<List<Point>>> =
        RAW_TEMPLATES.mapValues { (_, list) -> list.map(::normalize) }

    data class Result(val command: ShapeCommand, val score: Float)

    /** Best match, or null if the stroke is too short, ambiguous or nothing is close enough. */
    fun recognize(stroke: List<Point>): Result? {
        if (stroke.size < 4 || pathLength(stroke) < 1f) return null
        straightLine(stroke)?.let { return it }
        val candidate = normalize(stroke)
        val best = HashMap<ShapeCommand, Float>()
        for ((command, variants) in templates) {
            for (t in variants) {
                val score = 1f - pathDistance(candidate, t) / HALF_DIAGONAL
                if (score > (best[command] ?: -1f)) best[command] = score
            }
        }
        val ranked = best.entries.sortedByDescending { it.value }
        val top = ranked.firstOrNull() ?: return null
        val runnerUp = ranked.getOrNull(1)?.value ?: -1f
        if (top.value < minScore || top.value - runnerUp < minMargin) return null
        return Result(top.key, top.value)
    }

    /**
     * A (nearly) straight stroke: horizontal/vertical = arrow key in its direction, the
     * "\" diagonal (either way) = cut; the "/" diagonal is not used.
     */
    private fun straightLine(stroke: List<Point>): Result? {
        val first = stroke.first()
        val last = stroke.last()
        val dx = last.x - first.x
        val dy = last.y - first.y
        val straightness = hypot(dx, dy) / pathLength(stroke)
        if (straightness < STRAIGHTNESS) return null
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) // y points down
        fun near(target: Double) = abs(((angle - target + 540) % 360) - 180) <= AXIS_TOLERANCE
        val command = when {
            near(0.0) -> ShapeCommand.RIGHT
            near(180.0) -> ShapeCommand.LEFT
            near(90.0) -> ShapeCommand.DOWN
            near(-90.0) -> ShapeCommand.UP
            near(45.0) || near(-135.0) -> ShapeCommand.CUT
            else -> return null
        }
        return Result(command, straightness)
    }

    private fun normalize(points: List<Point>): List<Point> {
        val resampled = resample(points, N)
        val minX = resampled.minOf { it.x }
        val maxX = resampled.maxOf { it.x }
        val minY = resampled.minOf { it.y }
        val maxY = resampled.maxOf { it.y }
        // Uniform scale keeps a flat "<" or "↵" from being stretched into something else.
        val size = max(maxX - minX, maxY - minY).coerceAtLeast(1e-3f)
        val scaled = resampled.map { Point((it.x - minX) / size * SQUARE, (it.y - minY) / size * SQUARE) }
        val cx = scaled.map { it.x }.average().toFloat()
        val cy = scaled.map { it.y }.average().toFloat()
        return scaled.map { Point(it.x - cx, it.y - cy) }
    }

    private fun resample(points: List<Point>, n: Int): List<Point> {
        val interval = pathLength(points) / (n - 1)
        val src = points.toMutableList()
        val out = mutableListOf(src.first())
        var acc = 0f
        var i = 1
        while (i < src.size) {
            val a = src[i - 1]
            val b = src[i]
            val d = hypot(b.x - a.x, b.y - a.y)
            if (acc + d >= interval && d > 0f) {
                val t = (interval - acc) / d
                val q = Point(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
                out += q
                src.add(i, q)
                acc = 0f
            } else {
                acc += d
            }
            i++
        }
        while (out.size < n) out += src.last()
        return out.take(n)
    }

    private fun pathLength(p: List<Point>): Float =
        (1 until p.size).sumOf { hypot(p[it].x - p[it - 1].x, p[it].y - p[it - 1].y).toDouble() }.toFloat()

    private fun pathDistance(a: List<Point>, b: List<Point>): Float =
        a.indices.sumOf { hypot(a[it].x - b[it].x, a[it].y - b[it].y).toDouble() }.toFloat() / a.size

    companion object {
        private const val N = 64
        private const val SQUARE = 250f
        private val HALF_DIAGONAL = 0.5f * sqrt(2f * SQUARE * SQUARE)

        /** Straight-line distance / drawn length above which a stroke counts as a line. */
        private const val STRAIGHTNESS = 0.93f

        /** How far (degrees) a line may tilt from its direction and still count. */
        private const val AXIS_TOLERANCE = 22.0

        /**
         * The stroke to draw for [command], in a unit box (x right, y down), starting at the
         * first point: the main template, or the line for straight-stroke commands. For the
         * Gestures card on the glasses.
         */
        fun guide(command: ShapeCommand): List<Point> = when (command) {
            ShapeCommand.LEFT -> listOf(Point(1f, 0.5f), Point(0f, 0.5f))
            ShapeCommand.RIGHT -> listOf(Point(0f, 0.5f), Point(1f, 0.5f))
            ShapeCommand.UP -> listOf(Point(0.5f, 1f), Point(0.5f, 0f))
            ShapeCommand.DOWN -> listOf(Point(0.5f, 0f), Point(0.5f, 1f))
            ShapeCommand.CUT -> listOf(Point(0f, 0f), Point(1f, 1f))
            else -> RAW_TEMPLATES.getValue(command).first().map { Point(it.x / 100f, it.y / 100f) }
        }

        /** Templates in a 100×100 box (y down), first variant = how the card shows it. */
        private val RAW_TEMPLATES: Map<ShapeCommand, List<List<Point>>> = mapOf(
            // C: start top-right, sweep counter-clockwise round the left, end bottom-right.
            ShapeCommand.COPY to listOf(arc(50f, 50f, 50f, startDeg = -45f, endDeg = -315f)),
            // V: top-left down to bottom-middle, up to top-right.
            ShapeCommand.PASTE to listOf(poly(0f to 0f, 0.5f to 1f, 1f to 0f)),
            // Z: right along the top, diagonal down-left, right along the bottom.
            ShapeCommand.UNDO to listOf(poly(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)),
            // Redo: the Z drawn backwards (from its end): left along the bottom, up, left along the top.
            ShapeCommand.REDO to listOf(poly(1f to 1f, 0f to 1f, 1f to 0f, 0f to 0f)),
            // Select all: a peak (∧): up from bottom-left to the top, down to bottom-right.
            ShapeCommand.SELECT_ALL to listOf(poly(0f to 1f, 0.5f to 0f, 1f to 1f)),
            // S: from the top-right, round to the left, across the middle, round to the bottom-left.
            ShapeCommand.SAVE to listOf(
                poly(
                    0.9f to 0.12f, 0.6f to 0f, 0.25f to 0.03f, 0.08f to 0.2f, 0.25f to 0.42f,
                    0.75f to 0.58f, 0.92f to 0.8f, 0.75f to 0.97f, 0.4f to 1f, 0.1f to 0.88f,
                ),
            ),
            // Find: a magnifying glass, a circle (either way round) and then its handle down-right.
            ShapeCommand.FIND to listOf(
                magnifier(clockwise = false),
                magnifier(clockwise = true),
            ),
            // Enter: down, then left (like the ↵ key).
            ShapeCommand.ENTER to listOf(poly(1f to 0f, 1f to 1f, 0f to 1f)),
            // Backspace: "<" from top-right to the left point to bottom-right.
            ShapeCommand.BACKSPACE to listOf(poly(1f to 0f, 0f to 0.5f, 1f to 1f)),
            // Tab: ">" from top-left to the right point to bottom-left.
            ShapeCommand.TAB to listOf(poly(0f to 0f, 1f to 0.5f, 0f to 1f)),
            // Space: a cup (the ␣ symbol): down, across, up; deep, flat or rounded.
            ShapeCommand.SPACE to listOf(
                poly(0f to 0f, 0f to 0.55f, 1f to 0.55f, 1f to 0f),
                poly(0f to 0f, 0f to 1f, 1f to 1f, 1f to 0f),
                poly(0f to 0f, 0f to 0.3f, 1f to 0.3f, 1f to 0f),
            ),
            // D: down the left side, then the bow round to the right and back to the top.
            ShapeCommand.DELETE to listOf(
                poly(0f to 0f, 0f to 1f, 0.55f to 0.95f, 0.88f to 0.75f, 1f to 0.5f, 0.88f to 0.25f, 0.55f to 0.05f, 0f to 0f),
            ),
            // Alt+Tab: a 7, across the top then down (straight or slanted).
            ShapeCommand.SWITCH_WINDOW to listOf(
                poly(0f to 0f, 1f to 0f, 0.75f to 1f),
                poly(0f to 0f, 1f to 0f, 1f to 1f),
            ),
            // W: down, up, down, up.
            ShapeCommand.START_MENU to listOf(poly(0f to 0f, 0.25f to 1f, 0.5f to 0.35f, 0.75f to 1f, 1f to 0f)),
        )

        private fun poly(vararg corners: Pair<Float, Float>): List<Point> {
            val pts = mutableListOf<Point>()
            for (i in 0 until corners.size - 1) {
                val (x1, y1) = corners[i]
                val (x2, y2) = corners[i + 1]
                for (s in 0 until 20) {
                    val t = s / 20f
                    pts += Point((x1 + t * (x2 - x1)) * 100, (y1 + t * (y2 - y1)) * 100)
                }
            }
            val (lx, ly) = corners.last()
            pts += Point(lx * 100, ly * 100)
            return pts
        }

        /** Circular arc around (cx, cy), angles in degrees, y pointing down (screen coordinates). */
        private fun arc(cx: Float, cy: Float, r: Float, startDeg: Float, endDeg: Float): List<Point> = (0..40).map { s ->
            val a = Math.toRadians((startDeg + (endDeg - startDeg) * s / 40f).toDouble())
            Point((cx + r * cos(a)).toFloat(), (cy + r * sin(a)).toFloat())
        }

        /** A full circle starting and ending at its lower-right point, then the handle out to the corner. */
        private fun magnifier(clockwise: Boolean): List<Point> {
            val circle = arc(38f, 38f, 38f, startDeg = 45f, endDeg = if (clockwise) 405f else -315f)
            val handle = (1..12).map { s -> val t = s / 12f; Point(65f + t * 35f, 65f + t * 35f) }
            return circle + handle
        }
    }
}
