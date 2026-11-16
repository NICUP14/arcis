package app.arcis.host

import android.content.ComponentName
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo

/** Starting a Moonlight stream directly, and telling its stream from its menus. */
object Moonlight {

    /** Moonlight's streaming screen; its forks keep the class name. */
    const val STREAM_ACTIVITY = "com.limelight.Game"

    /**
     * True if [root] (a Moonlight window) is the stream: only the stream screen has the
     * full-screen video view. Reads the window itself, so it doesn't depend on catching
     * the moment the stream opened.
     */
    fun isStream(root: AccessibilityNodeInfo): Boolean =
        root.findAccessibilityNodeInfosByViewId("${root.packageName}:id/surfaceView").isNotEmpty()

    /** The app every Sunshine/Apollo host offers by default: the whole desktop. */
    const val DESKTOP_APP = "Desktop"

    /**
     * Moonlight's shortcut entry point (the same one its "Create Shortcut" uses). It finds
     * the computer by the name Moonlight shows for it (Sunshine uses the computer's name by
     * default) and the app by name in Moonlight's own app list, then starts or resumes the
     * stream. Forks keep the class name.
     */
    fun desktopIntent(moonlightPackage: String, computerName: String): Intent =
        Intent(Intent.ACTION_VIEW)
            .setComponent(ComponentName(moonlightPackage, "com.limelight.ShortcutTrampoline"))
            .putExtra("Name", computerName)
            .putExtra("AppName", DESKTOP_APP)
}
