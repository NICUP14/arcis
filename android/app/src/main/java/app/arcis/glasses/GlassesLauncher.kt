package app.arcis.glasses

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.arcis.ArcisApp
import kotlinx.coroutines.launch

/** Starts Arcis's own screens on the external display. */
object GlassesLauncher {

    /** Opens the Bluetooth mouse & keyboard screen on [displayId]. */
    fun showBluetooth(context: Context, displayId: Int): Boolean {
        val intent = Intent(context, BluetoothActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
        return runCatching { context.startActivity(intent, options.toBundle()) }
            .onFailure { Log.w("Arcis", "cannot show Bluetooth on display $displayId", it) }
            .isSuccess
    }

    /** Brings the glasses Home screen to the front of [displayId]. Returns false if Android refused. */
    fun showHome(context: Context, displayId: Int): Boolean {
        val intent = Intent(context, GlassesHomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
        return runCatching { context.startActivity(intent, options.toBundle()) }
            .onFailure { Log.w("Arcis", "cannot show Home on display $displayId", it) }
            .isSuccess
    }
}

/**
 * Arcis's glasses screens belong on the glasses: when the glasses are unplugged Android
 * moves them to the phone, so close them instead (the phone shows the controller), and
 * likewise if another external screen takes over.
 */
fun ComponentActivity.closeWhenScreenLeaves() {
    lifecycleScope.launch {
        ArcisApp.instance.displays.screen.collect { screen ->
            val mine = display?.displayId
            if (screen == null || mine == Display.DEFAULT_DISPLAY || screen.displayId != mine) finish()
        }
    }
}
