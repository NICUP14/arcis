package app.arcis.apps

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.drawable.toBitmap

/** An installed app that can be opened on the external screen. */
data class PhoneApp(
    val packageName: String,
    val label: String,
    val component: ComponentName,
)

/** Lists launchable apps and opens them on a given display. */
class PhoneApps(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val iconCache = HashMap<String, Bitmap>()

    @Volatile
    private var cached: List<PhoneApp>? = null

    /** Called after an app was opened on an external display. */
    var onOpened: ((String) -> Unit)? = null

    /** Looks up an installed app by package (uses the last loaded list). */
    fun find(packageName: String): PhoneApp? =
        (cached ?: list()).firstOrNull { it.packageName == packageName }

    /** All launcher apps except Arcis itself, sorted by name. */
    fun list(): List<PhoneApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .map { info ->
                PhoneApp(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    component = ComponentName(info.packageName, info.name),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
            .also { cached = it }
    }

    fun icon(app: PhoneApp, sizePx: Int): Bitmap? = synchronized(iconCache) {
        iconCache.getOrPut(app.packageName) {
            runCatching { pm.getActivityIcon(app.component).toBitmap(sizePx, sizePx) }.getOrNull()
                ?: return null
        }
    }

    /**
     * Opens [app] full screen on [displayId] (the glasses). If the app is already running on
     * the phone, Android moves its task over. Returns false if Android refused.
     */
    fun open(caller: Context, app: PhoneApp, displayId: Int): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return launch(caller, intent, displayId)
    }

    /** Starts [intent] (an activity of an installed app) full screen on [displayId]. */
    fun launch(caller: Context, intent: Intent, displayId: Int): Boolean {
        val pkg = intent.component?.packageName ?: intent.`package` ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
        val ok = runCatching { caller.startActivity(intent, options.toBundle()) }
            .onFailure { Log.w(TAG, "cannot open $pkg on display $displayId", it) }
            .isSuccess
        if (ok) onOpened?.invoke(pkg)
        return ok
    }

    private companion object {
        const val TAG = "ArcisApps"
    }
}
