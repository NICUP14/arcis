package app.arcis.core.input

/** One finger on the controller surface, in surface pixels. */
data class TouchPoint(val id: Int, val x: Float, val y: Float)

/** Platform-independent touch event fed into the gesture engine. */
data class TouchEvent(
    val action: TouchAction,
    /** All fingers currently on the surface (for UP: still including the lifted one). */
    val points: List<TouchPoint>,
    /** Id of the finger that went down/up for DOWN/UP actions. */
    val actionPointerId: Int,
    val timeMs: Long,
)

enum class TouchAction { DOWN, MOVE, UP, CANCEL }
