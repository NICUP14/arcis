package app.arcis.input

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import app.arcis.ArcisApp

/**
 * Phone buttons while an external screen is connected (read through the accessibility
 * service, because the controller window never takes key focus):
 * - Volume up, short press: Home on the external screen
 * - Volume up, hold: speech-to-text until released
 * Without an external screen the keys behave normally.
 */
class HardwareKeys(private val onHome: () -> Unit, private val voice: VoiceInput) {

    private val handler = Handler(Looper.getMainLooper())
    private var volumeUpDown = false
    private var holdFired = false
    private val holdRunnable = Runnable {
        holdFired = true
        voice.startDictation()
    }

    fun onKey(event: KeyEvent): Boolean {
        if (ArcisApp.instance.displays.screen.value == null) return false
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (!volumeUpDown) {
                volumeUpDown = true
                holdFired = false
                handler.postDelayed(holdRunnable, HOLD_MS)
            }
            KeyEvent.ACTION_UP -> {
                volumeUpDown = false
                handler.removeCallbacks(holdRunnable)
                if (holdFired) voice.stopDictation() else onHome()
            }
        }
        return true // consumed: don't change the volume
    }

    private companion object {
        const val HOLD_MS = 350L
    }
}
