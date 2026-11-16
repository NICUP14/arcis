package app.arcis.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.arcis.ArcisApp

/**
 * Debug-only: route trackpad input to the connected computer without Moonlight in front.
 *
 *   adb shell am broadcast -n app.arcis/.debug.DebugHostTargetReceiver --ez on true
 */
class DebugHostTargetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ArcisApp.instance.hostTarget.forceActiveForTesting = intent.getBooleanExtra("on", false)
    }
}
