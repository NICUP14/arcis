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

/**
 * Persists the last used mode (so Arcis reopens the way you left it) and the settings:
 * pointer speed, scroll speed and dictation language.
 */
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

    private val _scrollSpeed = MutableStateFlow(prefs.getFloat(KEY_SCROLL_SPEED, DEFAULT_POINTER_SPEED))

    /** Multiplier for two-finger and edge scrolling (1 = default), same range and steps as the pointer. */
    val scrollSpeed: StateFlow<Float> = _scrollSpeed.asStateFlow()

    fun setScrollSpeed(speed: Float) {
        val s = (Math.round(speed / POINTER_SPEED_STEP) * POINTER_SPEED_STEP).coerceIn(MIN_POINTER_SPEED, MAX_POINTER_SPEED)
        _scrollSpeed.value = s
        prefs.edit().putFloat(KEY_SCROLL_SPEED, s).apply()
    }

    private val _dictationLanguage = MutableStateFlow(prefs.getString(KEY_DICTATION_LANGUAGE, null))

    /** BCP-47 tag for speech-to-text (e.g. "ro-RO"), or null for the phone's language. */
    val dictationLanguage: StateFlow<String?> = _dictationLanguage.asStateFlow()

    fun setDictationLanguage(tag: String?) {
        _dictationLanguage.value = tag
        prefs.edit().putString(KEY_DICTATION_LANGUAGE, tag).apply()
    }

    fun toggle() = set(if (_mode.value == ControllerMode.LANDSCAPE) ControllerMode.UPRIGHT else ControllerMode.LANDSCAPE)

    private val _statusBar = MutableStateFlow(prefs.getBoolean(KEY_STATUS_BAR, true))

    /** The status bar in the corner of the glasses (time, battery, computer, mic, media). */
    val statusBar: StateFlow<Boolean> = _statusBar.asStateFlow()

    fun setStatusBar(on: Boolean) {
        _statusBar.value = on
        prefs.edit().putBoolean(KEY_STATUS_BAR, on).apply()
    }

    private val _banners = MutableStateFlow(prefs.getBoolean(KEY_BANNERS, true))

    /** Message banners on the glasses. */
    val banners: StateFlow<Boolean> = _banners.asStateFlow()

    fun setBanners(on: Boolean) {
        _banners.value = on
        prefs.edit().putBoolean(KEY_BANNERS, on).apply()
    }

    private val _bannersOnlyStreaming = MutableStateFlow(prefs.getBoolean(KEY_BANNERS_STREAMING, false))

    /** Banners only while a computer's stream or a video is in front. */
    val bannersOnlyStreaming: StateFlow<Boolean> = _bannersOnlyStreaming.asStateFlow()

    fun setBannersOnlyStreaming(on: Boolean) {
        _bannersOnlyStreaming.value = on
        prefs.edit().putBoolean(KEY_BANNERS_STREAMING, on).apply()
    }

    fun set(mode: ControllerMode) {
        _mode.value = mode
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_POINTER_SPEED = "pointer_speed"
        private const val KEY_SCROLL_SPEED = "scroll_speed"
        private const val KEY_DICTATION_LANGUAGE = "dictation_language"
        private const val KEY_STATUS_BAR = "status_bar"
        private const val KEY_BANNERS = "banners"
        private const val KEY_BANNERS_STREAMING = "banners_only_streaming"
        const val DEFAULT_POINTER_SPEED = 1f
        const val MIN_POINTER_SPEED = 0.25f
        const val MAX_POINTER_SPEED = 3f
        const val POINTER_SPEED_STEP = 0.25f
    }
}
