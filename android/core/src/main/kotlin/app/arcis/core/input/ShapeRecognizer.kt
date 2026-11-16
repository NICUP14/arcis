package app.arcis.core.input

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Commands that can be drawn on the trackpad. Easy to guess:
 * - a key is drawn as the symbol on the key (< Backspace, > Tab, ↵ Enter, ␣ Space, arrows);
 * - a shortcut is drawn as the letter pressed with Ctrl (C copy, V paste, X cut...).
 */
enum class ShapeCommand(val shape: String) {
    // Shortcuts: the letter used with Ctrl (Cmd on a Mac).
    COPY("C"),
    PASTE("V"),
    CUT("X"),
    UNDO("Z"),
    REDO("Y"),
    SELECT_ALL("A"),
    SAVE("S"),
    FIND("F"),

    // Keys: the symbol on the key.
    ENTER("↵"),
    BACKSPACE("<"),
    TAB(">"),
    SPACE("␣"),
    DELETE("D"),
    LEFT("←"),
    RIGHT("→"),
    UP("↑"),
    DOWN("↓"),

    // Windows: Alt+Tab and the Windows key (Start menu).
    SWITCH_WINDOW("T"),
    START_MENU("W"),
}

data class Point(val x: Float, val y: Float)

/**
 * Small unistroke recognizer in the spirit of the "$1 recognizer": resample, scale to a
 * square, centre, then compare point-to-point with fixed templates. Deliberately NOT
 * rotation-invariant, so "V", "∧", "<" and ">" stay different. Straight strokes are
 * recognised separately as the arrow keys. Deterministic, no learning.
 */
class ShapeRecognizer(
    private val minScore: Float = 0.78f,
    /** The best match must beat the best other command by this much, or nothing is done. */
    private val minMargin: Float = 0.02f,
) {

    private val templates: Map<ShapeCommand, List<List<Point>>> = mapOf(
        // C: start top-right, sweep counter-clockwise round the left, end bottom-right.
        ShapeCommand.COPY to listOf(arc(startDeg = -45f, endDeg = -315f)),
        // V: top-left down to bottom-middle, up to top-right.
        ShapeCommand.PASTE to listOf(poly(0f to 0f, 0.5f to 1f, 1f to 0f)),
        // X in one stroke: diagonal, up the side, other diagonal (either way round).
        ShapeCommand.CUT to listOf(
            poly(0f to 0f, 1f to 1f, 1f to 0f, 0f to 1f),
            poly(1f to 0f, 0f to 1f, 0f to 0f, 1f to 1f),
        ),
        // Z: right along the top, diagonal down-left, right along the bottom.
        ShapeCommand.UNDO to listOf(poly(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)),
        // Y: left arm to the centre, up to the right arm's tip, back to the centre, down.
        ShapeCommand.REDO to listOf(poly(0f to 0f, 0.5f to 0.5f, 1f to 0f, 0.5f to 0.5f, 0.5f to 1f)),
        // A as a peak (∧): up from bottom-left to the top, down to bottom-right.
        ShapeCommand.SELECT_ALL to listOf(poly(0f to 1f, 0.5f to 0f, 1f to 1f)),
        // S: from the top-right, round to the left, across the middle, round to the bottom-left.
        ShapeCommand.SAVE to listOf(
            poly(
                0.9f to 0.12f, 0.6f to 0f, 0.25f to 0.03f, 0.08f to 0.2f, 0.25f to 0.42f,
                0.75f to 0.58f, 0.92f to 0.8f, 0.75f to 0.97f, 0.4f to 1f, 0.1f to 0.88f,
            ),
        ),
        // F: along the top from the right, then down the left side (or up, then along the top).
        ShapeCommand.FIND to listOf(
            poly(1f to 0f, 0f to 0f, 0f to 1f),
            poly(0f to 1f, 0f to 0f, 1f to 0f),
        ),
        // Enter: down, then left (like the ↵ key).
        ShapeCommand.ENTER to listOf(poly(1f to 0f, 1f to 1f, 0f to 1f)),
        // Backspace: "<" from top-right to the left point to bottom-right.
        ShapeCommand.BACKSPACE to listOf(poly(1f to 0f, 0f to 0.5f, 1f to 1f)),
        // Tab: ">" from top-left to the right point to bottom-left.
        ShapeCommand.TAB to listOf(poly(0f to 0f, 1f to 0.5f, 0f to 1f)),
        // Space: a flat cup (␣): down, across, up (a deeper cup counts too).
        ShapeCommand.SPACE to listOf(
            poly(0f to 0f, 0f to 0.45f, 1f to 0.45f, 1f to 0f),
            poly(0f to 0f, 0f to 1f, 1f to 1f, 1f to 0f),
        ),
        // D: down the left side, then the bow round to the right and back to the top.
        ShapeCommand.DELETE to listOf(
            poly(0f to 0f, 0f to 1f, 0.55f to 0.95f, 0.88f to 0.75f, 1f to 0.5f, 0.88f to 0.25f, 0.55f to 0.05f, 0f to 0f),
        ),
        // T: across the top, then down from the end like a 7 (or from the middle, like a T).
        ShapeCommand.SWITCH_WINDOW to listOf(
            poly(0f to 0f, 1f to 0f, 1f to 1f),
            poly(0f to 0f, 1f to 0f, 0.5f to 0f, 0.5f to 1f),
        ),
        // W: down, up, down, up.
        ShapeCommand.START_MENU to listOf(poly(0f to 0f, 0.25f to 1f, 0.5f to 0.35f, 0.75f to 1f, 1f to 0f)),
    ).mapValues { (_, list) -> list.map(::normalize) }

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

    /** A (nearly) straight stroke is an arrow key in its direction; diagonals are not. */
    private fun straightLine(stroke: List<Point>): Result? {
        val first = stroke.first()
        val last = stroke.last()
        val dx = last.x - first.x
        val dy = last.y - first.y
        val span = hypot(dx, dy)
        val straightness = span / pathLength(stroke)
        if (straightness < STRAIGHTNESS) return null
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) // y points down
        val command = when {
            abs(angle) <= AXIS_TOLERANCE -> ShapeCommand.RIGHT
            abs(angle) >= 180 - AXIS_TOLERANCE -> ShapeCommand.LEFT
            abs(angle - 90) <= AXIS_TOLERANCE -> ShapeCommand.DOWN
            abs(angle + 90) <= AXIS_TOLERANCE -> ShapeCommand.UP
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

    private companion object {
        const val N = 64
        const val SQUARE = 250f
        val HALF_DIAGONAL = 0.5f * sqrt(2f * SQUARE * SQUARE)

        /** Straight-line distance / drawn length above which a stroke counts as a line. */
        const val STRAIGHTNESS = 0.93f

        /** How far (degrees) a line may tilt from horizontal/vertical and still be an arrow. */
        const val AXIS_TOLERANCE = 25.0

        fun poly(vararg corners: Pair<Float, Float>): List<Point> {
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

        /** Circular arc, angles in degrees, y pointing down (screen coordinates). */
        fun arc(startDeg: Float, endDeg: Float): List<Point> = (0..40).map { s ->
            val a = Math.toRadians((startDeg + (endDeg - startDeg) * s / 40f).toDouble())
            Point((50 + 50 * kotlin.math.cos(a)).toFloat(), (50 + 50 * kotlin.math.sin(a)).toFloat())
        }
    }
}
