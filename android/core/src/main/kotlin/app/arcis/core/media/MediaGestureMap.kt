package app.arcis.core.media

import app.arcis.core.input.Button
import app.arcis.core.input.Gesture
import app.arcis.core.input.ShapeCommand
import kotlin.math.abs
import kotlin.math.min

/** What a trackpad gesture means while a video is in front (media mode). */
sealed interface MediaAction {
    data object PlayPause : MediaAction

    /** Jump [seconds] forward (negative = back). */
    data class Seek(val seconds: Int) : MediaAction

    /** Volume [steps] up (negative = down). */
    data class Volume(val steps: Int) : MediaAction

    data object Back : MediaAction

    /** Leave media mode: the pointer comes back. */
    data object Exit : MediaAction

    /** Taken by media mode, nothing to do (e.g. pointer movement mid-swipe). */
    data object Nothing : MediaAction
}

/**
 * Media mode's gestures, so a video can be controlled without aiming:
 * tap = play/pause, horizontal swipe = seek (±10 s, more for longer swipes), right edge
 * or two fingers up/down = volume, two-finger tap = Back, long-press = leave media mode.
 * A drawn straight line (rest, then swipe) seeks / changes volume the same way.
 * Stateful (adds up a swipe until the finger lifts); one instance per trackpad.
 */
class MediaGestureMap(
    /** Shortest swipe (target px) that seeks, and how much more it must be than vertical. */
    private val swipeMinPx: Float = 120f,
    /** Every this much further adds another [seekStepS]. */
    private val seekStepPx: Float = 400f,
    private val seekStepS: Int = 10,
    private val maxSeekS: Int = 60,
    /** Scroll (target px) per volume step. */
    private val volumeStepPx: Float = 120f,
) {
    private var swipeX = 0f
    private var swipeY = 0f
    private var volumeAcc = 0f

    /** The media meaning of [g], or null if media mode leaves it alone (switching, Home…). */
    fun map(g: Gesture): MediaAction? = when (g) {
        is Gesture.PointerMove -> {
            swipeX += g.dx
            swipeY += g.dy
            MediaAction.Nothing
        }
        Gesture.MoveEnd -> endSwipe()
        is Gesture.Click -> {
            reset()
            if (g.button == Button.LEFT) MediaAction.PlayPause else MediaAction.Exit
        }
        is Gesture.Scroll -> {
            // Positive dy = finger moved up = louder.
            volumeAcc += g.dy
            val steps = (volumeAcc / volumeStepPx).toInt()
            volumeAcc -= steps * volumeStepPx
            if (steps != 0) MediaAction.Volume(steps) else MediaAction.Nothing
        }
        Gesture.Back -> MediaAction.Back
        is Gesture.ShapeDrawn -> when (g.command) {
            ShapeCommand.RIGHT -> MediaAction.Seek(seekStepS)
            ShapeCommand.LEFT -> MediaAction.Seek(-seekStepS)
            ShapeCommand.UP -> MediaAction.Volume(1)
            ShapeCommand.DOWN -> MediaAction.Volume(-1)
            else -> MediaAction.Nothing
        }
        Gesture.ShapeArmed, Gesture.DragStart, Gesture.DragEnd, is Gesture.Zoom -> MediaAction.Nothing
        else -> null
    }

    private fun endSwipe(): MediaAction {
        val dx = swipeX
        val dy = swipeY
        reset()
        if (abs(dx) < swipeMinPx || abs(dx) < abs(dy) * 1.5f) return MediaAction.Nothing
        val steps = 1 + ((abs(dx) - swipeMinPx) / seekStepPx).toInt()
        val seconds = min(steps * seekStepS, maxSeekS)
        return MediaAction.Seek(if (dx > 0) seconds else -seconds)
    }

    private fun reset() {
        swipeX = 0f
        swipeY = 0f
    }
}
