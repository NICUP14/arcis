package app.arcis.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import app.arcis.ArcisApp

/**
 * Debug-only: opens a phone app on a display through Arcis's own launch code.
 *
 *   adb shell am start -n app.arcis/.debug.DebugOpenAppActivity --es pkg com.android.settings --ei display 30
 */
class DebugOpenAppActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg")
        val displayId = intent.getIntExtra("display", -1)
        val apps = ArcisApp.instance.phoneApps
        val app = apps.list().firstOrNull { it.packageName == pkg }
        val ok = app != null && apps.open(this, app, displayId)
        Log.i("ArcisDebug", "open $pkg on display $displayId -> $ok")
        finish()
    }
}
