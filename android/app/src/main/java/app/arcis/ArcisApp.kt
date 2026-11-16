package app.arcis

import android.app.Application
import app.arcis.apps.AppPrefs
import app.arcis.apps.PhoneApps
import app.arcis.bt.BluetoothTarget
import app.arcis.bt.HidPeripheral
import app.arcis.controller.ControllerPrefs
import app.arcis.display.ExternalDisplayTracker
import app.arcis.glasses.GlassesLauncher
import app.arcis.input.HardwareKeys
import app.arcis.input.VoiceInput
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
    lateinit var voice: VoiceInput
        private set
    lateinit var hardwareKeys: HardwareKeys
        private set
    lateinit var controllerPrefs: ControllerPrefs
        private set
    lateinit var hid: HidPeripheral
        private set
    lateinit var bluetoothTarget: BluetoothTarget
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        displays = ExternalDisplayTracker(this)
        phoneApps = PhoneApps(this)
        appPrefs = AppPrefs(this)
        controllerPrefs = ControllerPrefs(this)
        hid = HidPeripheral(this)
        bluetoothTarget = BluetoothTarget(hid)
        switcher = AppSwitcher(this).also { it.start() }
        phoneApps.onOpened = switcher::noteOpened
        voice = VoiceInput(this)
        hardwareKeys = HardwareKeys(onHome = ::showHomeOnScreen, voice = voice)
    }

    private fun showHomeOnScreen() {
        val screen = displays.screen.value ?: return
        GlassesLauncher.showHome(this, screen.displayId)
    }

    companion object {
        lateinit var instance: ArcisApp
            private set
    }
}
