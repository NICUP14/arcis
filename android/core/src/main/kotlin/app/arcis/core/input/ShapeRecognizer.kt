package app.arcis.core.input

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/** Commands that can be drawn on the trackpad. */
enum class ShapeCommand(val shape: String) {
    COPY("C"),
    PASTE("V"),
    UNDO("Z"),
    ENTER("↵"),
    BACKSPACE("<"),
}

data class Point(val x: Float, val y: Float)

/**
 * Small unistroke recognizer in the spirit of the "$1 recognizer": resample, scale to a
 * square, centre, then compare point-to-point with fixed templates. Deliberately NOT
 * rotation-invariant, so "V" and "<" stay different. Deterministic, no learning.
 */
class ShapeRecognizer(private val minScore: Float = 0.78f) {

    private val templates: Map<ShapeCommand, List<List<Point>>> = mapOf(
        // C: start top-right, sweep counter-clockwise round the left, end bottom-right.
        ShapeCommand.COPY to listOf(arc(startDeg = -45f, endDeg = -315f)),
        // V: top-left down to bottom-middle, up to top-right.
        ShapeCommand.PASTE to listOf(poly(0f to 0f, 0.5f to 1f, 1f to 0f)),
        // Z: right along the top, diagonal down-left, right along the bottom.
        ShapeCommand.UNDO to listOf(poly(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)),
        // Enter: down, then left (like the ↵ key).
        ShapeCommand.ENTER to listOf(poly(1f to 0f, 1f to 1f, 0f to 1f)),
        // Backspace: "<" from top-right to the left point to bottom-right.
        ShapeCommand.BACKSPACE to listOf(poly(1f to 0f, 0f to 0.5f, 1f to 1f)),
    ).mapValues { (_, list) -> list.map(::normalize) }

    data class Result(val command: ShapeCommand, val score: Float)

    /** Best match, or null if the stroke is too short or nothing is close enough. */
    fun recognize(stroke: List<Point>): Result? {
        if (stroke.size < 4 || pathLength(stroke) < 1f) return null
        val candidate = normalize(stroke)
        var best: Result? = null
        for ((command, variants) in templates) {
            for (t in variants) {
                val d = pathDistance(candidate, t)
                val score = 1f - d / HALF_DIAGONAL
                if (best == null || score > best.score) best = Result(command, score)
            }
        }
        return best?.takeIf { it.score >= minScore }
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
