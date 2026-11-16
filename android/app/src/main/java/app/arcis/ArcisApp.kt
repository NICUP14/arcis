package app.arcis

import android.app.Application
import app.arcis.apps.AppPrefs
import app.arcis.apps.PhoneApps
import app.arcis.display.ExternalDisplayTracker

/** Application singleton holding the app-wide runtime state. */
class ArcisApp : Application() {

    lateinit var displays: ExternalDisplayTracker
        private set
    lateinit var phoneApps: PhoneApps
        private set
    lateinit var appPrefs: AppPrefs
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        displays = ExternalDisplayTracker(this)
        phoneApps = PhoneApps(this)
        appPrefs = AppPrefs(this)
    }

    companion object {
        lateinit var instance: ArcisApp
            private set
    }
}
