package app.arcis.core.input

/**
 * Tunables for [TrackpadGestureEngine]. Distances are in surface pixels; callers
 * convert from dp using the screen density.
 */
data class GestureConfig(
    /** Movement below this counts as "not moved" (tap / hold). */
    val touchSlop: Float = 24f,
    val tapTimeoutMs: Long = 250,
    val doubleTapTimeoutMs: Long = 300,
    val longPressMs: Long = 550,
    /** Resting still this long, then moving, draws a shortcut shape instead of moving the pointer. */
    val shapeHoldMs: Long = 350,
    val shapesEnabled: Boolean = true,
    /** Height of the bottom-edge strip that starts switch / home / recents gestures. */
    val bottomEdge: Float = 90f,
    /** Width of the right-edge scroll strip (0 disables it). */
    val rightEdge: Float = 0f,
    /** Horizontal distance on the bottom edge that switches app. */
    val switchDistance: Float = 160f,
    /** Upward distance for Home (quick release) / Recents (hold). */
    val homeDistance: Float = 140f,
    /** Holding still this long after swiping up shows Recents. */
    val recentsHoldMs: Long = 220,
    /** Horizontal finger travel per card while in Recents. */
    val recentsStep: Float = 140f,
    /** Further upward travel while in Recents that closes the selected card on release. */
    val recentsCloseDistance: Float = 220f,
    /** Screen density (surface pixels per dp); pointer speed is measured in dp so it feels the same on every phone. */
    val density: Float = 1f,
    /** Pointer pixels per dp of finger travel when moving slowly. */
    val pointerSensitivity: Float = 1.5f,
    /** Extra gain per dp/ms of finger speed (pointer acceleration). */
    val pointerAcceleration: Float = 1.5f,
    /** Pointer pixels per dp of finger travel at most (fast flicks). */
    val maxPointerGain: Float = 8f,
    val scrollSensitivity: Float = 1.2f,
    /** true = content follows the fingers (phone style). */
    val naturalScroll: Boolean = true,
)
