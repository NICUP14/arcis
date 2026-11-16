package app.arcis.glasses

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent

/** Starts Arcis's own screens on the external display. */
object GlassesLauncher {

    /** Brings the glasses Home screen to the front of [displayId]. */
    fun showHome(context: Context, displayId: Int) {
        val intent = Intent(context, GlassesHomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
        context.startActivity(intent, options.toBundle())
    }
}
