package app.arcis.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot of the external screen Arcis drives (AR glasses, monitor, TV...).
 * Only standard Android display APIs are used, so any screen the phone exposes
 * as a presentation display works, regardless of brand.
 */
data class ExternalScreen(
    val displayId: Int,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val refreshRate: Float,
)

/** Tracks the first non-default presentation display (the glasses / external screen). */
class ExternalDisplayTracker(context: Context) {

    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val _screen = MutableStateFlow(findScreen())

    /** Currently connected external screen, or null. */
    val screen: StateFlow<ExternalScreen?> = _screen.asStateFlow()

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = refresh()
    }

    init {
        displayManager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
    }

    fun refresh() {
        _screen.value = findScreen()
    }

    /** The live [Display] for the current external screen, if any. */
    fun display(): Display? = _screen.value?.let { displayManager.getDisplay(it.displayId) }

    private fun findScreen(): ExternalScreen? {
        val display = displayManager
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.isValid }
            ?: return null
        val mode = display.mode
        return ExternalScreen(
            displayId = display.displayId,
            name = display.name,
            widthPx = mode.physicalWidth,
            heightPx = mode.physicalHeight,
            refreshRate = mode.refreshRate,
        )
    }
}
