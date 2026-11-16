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

/** Persists the last used mode so Arcis reopens the way you left it. */
class ControllerPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("controller", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(
        runCatching { ControllerMode.valueOf(prefs.getString(KEY_MODE, null) ?: "") }
            .getOrDefault(ControllerMode.LANDSCAPE),
    )
    val mode: StateFlow<ControllerMode> = _mode.asStateFlow()

    fun toggle() = set(if (_mode.value == ControllerMode.LANDSCAPE) ControllerMode.UPRIGHT else ControllerMode.LANDSCAPE)

    fun set(mode: ControllerMode) {
        _mode.value = mode
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    private companion object {
        const val KEY_MODE = "mode"
    }
}
