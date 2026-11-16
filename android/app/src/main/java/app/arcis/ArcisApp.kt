package app.arcis

import android.app.Application
import app.arcis.display.ExternalDisplayTracker

/** Application singleton holding the app-wide runtime state. */
class ArcisApp : Application() {

    lateinit var displays: ExternalDisplayTracker
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        displays = ExternalDisplayTracker(this)
    }

    companion object {
        lateinit var instance: ArcisApp
            private set
    }
}
