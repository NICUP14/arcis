package app.arcis.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.arcis.a11y.ArcisAccessibilityService

/**
 * Debug-only: put the cursor at exact external-screen pixels, optionally click there.
 *
 *   adb shell am broadcast -n app.arcis/.debug.DebugCursorReceiver --ei x 934 --ei y 642 --ez click true
 */
class DebugCursorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val svc = ArcisAccessibilityService.instance ?: return
        svc.moveCursorTo(intent.getIntExtra("x", 0).toFloat(), intent.getIntExtra("y", 0).toFloat())
        if (intent.getBooleanExtra("click", false)) svc.click()
    }
}
