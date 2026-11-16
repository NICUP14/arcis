package app.arcis.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.arcis.ArcisApp

/**
 * Debug-only: press / release push-to-talk (hold volume down) without the real button.
 *
 *   adb shell am broadcast -n app.arcis/.debug.DebugMicReceiver -a start
 *   adb shell am broadcast -n app.arcis/.debug.DebugMicReceiver -a stop
 */
class DebugMicReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "start" -> ArcisApp.instance.pushToTalk.start()
            "stop" -> ArcisApp.instance.pushToTalk.stop()
        }
    }
}
