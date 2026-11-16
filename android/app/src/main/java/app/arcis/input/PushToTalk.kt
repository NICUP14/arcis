package app.arcis.input

import android.view.KeyEvent
import app.arcis.a11y.ArcisAccessibilityService
import app.arcis.host.HostManager
import app.arcis.voice.MicStreamer

/**
 * Hold volume down to talk into the connected computer's virtual microphone ("headset"
 * mode for Discord, Teams, Zoom...). Without a connected computer the key changes the
 * volume as usual.
 */
class PushToTalk(private val hosts: HostManager, private val mic: MicStreamer) {

    private var talking = false

    fun onKey(event: KeyEvent): Boolean {
        if (!hosts.isConnected && !talking) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (!talking) start()
            KeyEvent.ACTION_UP -> stop()
        }
        return true
    }

    fun start() {
        val overlay = ArcisAccessibilityService.instance?.uiOverlay
        if (!mic.start()) {
            overlay?.flash("Allow the microphone for Arcis on your phone")
            return
        }
        talking = true
        overlay?.showDictation("Microphone on — talking to ${hosts.connectedName ?: "computer"}")
    }

    fun stop() {
        if (!talking) return
        talking = false
        mic.stop()
        ArcisAccessibilityService.instance?.uiOverlay?.hideDictation()
    }
}
