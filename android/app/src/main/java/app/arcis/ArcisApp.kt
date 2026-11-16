package app.arcis

import android.app.Application
import app.arcis.apps.AppPrefs
import app.arcis.apps.PhoneApps
import app.arcis.display.ExternalDisplayTracker
import app.arcis.switcher.AppSwitcher

/** Application singleton holding the app-wide runtime state. */
class ArcisApp : Application() {

    lateinit var displays: ExternalDisplayTracker
        private set
    lateinit var phoneApps: PhoneApps
        private set
    lateinit var appPrefs: AppPrefs
        private set
    lateinit var switcher: AppSwitcher
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        displays = ExternalDisplayTracker(this)
        phoneApps = PhoneApps(this)
        appPrefs = AppPrefs(this)
        switcher = AppSwitcher(this).also { it.start() }
        phoneApps.onOpened = switcher::noteOpened
    }

    companion object {
        lateinit var instance: ArcisApp
            private set
    }
}
