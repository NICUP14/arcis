package app.arcis.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import app.arcis.a11y.ArcisAccessibilityService
import app.arcis.host.HostTarget
import app.arcis.host.Moonlight

/**
 * Debug-only: logs the top app on every display accessibility lists, and for Moonlight
 * whether its stream is showing (checks [Moonlight.isStream] on displays you can reach).
 *
 *   adb shell am broadcast -n app.arcis/.debug.DebugScreenProbeReceiver
 *   adb logcat -s ArcisProbe
 */
class DebugScreenProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val windows = ArcisAccessibilityService.instance?.windowsOnAllDisplays ?: return
        for (i in 0 until windows.size()) {
            val top = windows.valueAt(i)
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .maxByOrNull { it.layer }
            val root = top?.root
            val pkg = root?.packageName?.toString()
            val stream = if (root != null && HostTarget.isMoonlight(pkg)) Moonlight.isStream(root) else null
            Log.i("ArcisProbe", "display ${windows.keyAt(i)}: $pkg moonlightStream=$stream")
        }
    }
}
