package app.arcis.input

import app.arcis.a11y.ArcisAccessibilityService
import app.arcis.host.HostManager
import app.arcis.voice.MicStreamer

/**
 * Talk into the connected computer's virtual microphone ("headset" mode for Discord,
 * Teams, Zoom...): [start] while talking, [stop] when done. No phone control triggers it
 * at the moment (the volume keys are plain volume); debug builds can via DebugMicReceiver.
 */
class PushToTalk(private val hosts: HostManager, private val mic: MicStreamer) {

    private var talking = false

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
