package app.arcis.host

import android.content.ComponentName
import android.content.Intent

/** Starting a Moonlight stream directly, without going through Moonlight's menus. */
object Moonlight {

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
