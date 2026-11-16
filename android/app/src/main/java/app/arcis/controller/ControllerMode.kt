package app.arcis.controller

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The two ways to hold the phone. Chosen explicitly (corner button), never by sensing rotation. */
enum class ControllerMode {
    /** Phone sideways: the whole screen is the trackpad; text by speech-to-text. */
    LANDSCAPE,

    /** Phone upright: trackpad on the top half, the phone's own keyboard on the bottom half. */
    UPRIGHT,
}

/** Persists the last used mode (so Arcis reopens the way you left it) and the pointer speed. */
class ControllerPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("controller", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(
        runCatching { ControllerMode.valueOf(prefs.getString(KEY_MODE, null) ?: "") }
            .getOrDefault(ControllerMode.LANDSCAPE),
    )
    val mode: StateFlow<ControllerMode> = _mode.asStateFlow()

    private val _pointerSpeed = MutableStateFlow(prefs.getFloat(KEY_POINTER_SPEED, DEFAULT_POINTER_SPEED))

    /** Multiplier for trackpad pointer movement (1 = default), [MIN_POINTER_SPEED]..[MAX_POINTER_SPEED]. */
    val pointerSpeed: StateFlow<Float> = _pointerSpeed.asStateFlow()

    fun setPointerSpeed(speed: Float) {
        val s = (Math.round(speed / POINTER_SPEED_STEP) * POINTER_SPEED_STEP).coerceIn(MIN_POINTER_SPEED, MAX_POINTER_SPEED)
        _pointerSpeed.value = s
        prefs.edit().putFloat(KEY_POINTER_SPEED, s).apply()
    }

    fun toggle() = set(if (_mode.value == ControllerMode.LANDSCAPE) ControllerMode.UPRIGHT else ControllerMode.LANDSCAPE)

    fun set(mode: ControllerMode) {
        _mode.value = mode
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_POINTER_SPEED = "pointer_speed"
        const val DEFAULT_POINTER_SPEED = 1f
        const val MIN_POINTER_SPEED = 0.25f
        const val MAX_POINTER_SPEED = 3f
        const val POINTER_SPEED_STEP = 0.25f
    }
}
