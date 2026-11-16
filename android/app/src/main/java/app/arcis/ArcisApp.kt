package app.arcis

import android.app.Application
import android.util.Log
import app.arcis.apps.AppPrefs
import app.arcis.apps.PhoneApps
import app.arcis.bt.BluetoothTarget
import app.arcis.bt.HidPeripheral
import app.arcis.controller.ControllerPrefs
import app.arcis.display.ExternalDisplayTracker
import app.arcis.a11y.ArcisAccessibilityService
import app.arcis.host.HostConnection
import app.arcis.host.HostManager
import app.arcis.host.HostTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import app.arcis.glasses.GlassesLauncher
import app.arcis.input.PushToTalk
import app.arcis.voice.MicStreamer
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
    lateinit var pushToTalk: PushToTalk
        private set
    lateinit var controllerPrefs: ControllerPrefs
        private set
    lateinit var hid: HidPeripheral
        private set
    lateinit var bluetoothTarget: BluetoothTarget
        private set
    lateinit var hosts: HostManager
        private set
    lateinit var hostTarget: HostTarget
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _remoteTyping = MutableStateFlow(false)

    /** True while typed text should go to a computer (Moonlight in front, bridge connected). */
    val remoteTyping: StateFlow<Boolean> = _remoteTyping.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this
        displays = ExternalDisplayTracker(this)
        phoneApps = PhoneApps(this)
        appPrefs = AppPrefs(this)
        controllerPrefs = ControllerPrefs(this)
        hid = HidPeripheral(this)
        bluetoothTarget = BluetoothTarget(hid)
        hosts = HostManager(this)
        hostTarget = HostTarget(hosts)
        watchHostTarget()
        watchHostProblems()
        switcher = AppSwitcher(this).also { it.start() }
        phoneApps.onOpened = switcher::noteOpened
        voice = VoiceInput(this)
        pushToTalk = PushToTalk(hosts, MicStreamer(this) { pcm -> hosts.sendAudio(pcm) })
    }

    /** Moonlight's stream in front + bridge connected = control the computer; its own cursor is in the video. */
    private fun watchHostTarget() {
        scope.launch {
            combine(
                ArcisAccessibilityService.foregroundPackage,
                ArcisAccessibilityService.foregroundActivity,
                hosts.connection,
            ) { pkg, activity, conn ->
                HostTarget.isMoonlightStream(pkg, activity) to (conn is HostConnection.Connected)
            }.collect { (stream, connected) ->
                hostTarget.streamInFront = stream
                val active = stream && connected
                if (_remoteTyping.value != active) {
                    Log.d("ArcisHost", "computer control ${if (active) "on" else "off"} (stream=$stream, connected=$connected)")
                    _remoteTyping.value = active
                    ArcisAccessibilityService.instance?.setCursorVisible(!active)
                }
            }
        }
    }

    /** Says so on the glasses when a computer can't be reached or drops, e.g. over Moonlight. */
    private fun watchHostProblems() {
        scope.launch {
            hosts.connection.collect { c ->
                if (c is HostConnection.Failed) {
                    ArcisAccessibilityService.instance?.uiOverlay?.flash("${c.name}: ${c.message}", PROBLEM_FLASH_MS)
                }
            }
        }
    }

    companion object {
        private const val PROBLEM_FLASH_MS = 5_000L

        lateinit var instance: ArcisApp
            private set
    }
}
