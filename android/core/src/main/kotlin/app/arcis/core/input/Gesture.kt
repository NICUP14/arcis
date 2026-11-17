package app.arcis.core.input

/** High-level intents produced by the gesture engine. Targets decide how to perform them. */
sealed interface Gesture {
    /** Relative pointer movement (already accelerated), in target pixels. */
    data class PointerMove(val dx: Float, val dy: Float) : Gesture

    /** The finger that was moving the pointer lifted (ends a swipe, e.g. seek in media mode). */
    data object MoveEnd : Gesture

    data class Click(val button: Button) : Gesture

    /** Left button held down for a drag (double-tap-hold), released with [DragEnd]. */
    data object DragStart : Gesture
    data object DragEnd : Gesture

    /** Scroll in target pixels; positive dy scrolls content down (finger moved up). */
    data class Scroll(val dx: Float, val dy: Float) : Gesture

    /** Two-finger tap. */
    data object Back : Gesture

    /** Bottom-edge horizontal swipe. */
    data class SwitchApp(val direction: SwitchDirection) : Gesture

    /** Two-finger pinch: +1 per step apart (zoom in), -1 per step together (zoom out). */
    data class Zoom(val steps: Int) : Gesture

    /** Bottom-edge quick swipe up. */
    data object Home : Gesture

    /** Bottom-edge swipe up and hold: card switcher interaction. */
    data object RecentsShow : Gesture
    /** Selection moved by [steps] cards (negative = left). */
    data class RecentsMove(val steps: Int) : Gesture
    data object RecentsOpenSelected : Gesture
    data object RecentsCloseSelected : Gesture

    /** Finger rested still long enough: the next movement draws a shape (tick feedback). */
    data object ShapeArmed : Gesture

    /** A drawn shape finished; [command] is null when it wasn't recognized. */
    data class ShapeDrawn(val command: ShapeCommand?) : Gesture
}

enum class Button { LEFT, RIGHT }

enum class SwitchDirection { PREVIOUS, NEXT }
