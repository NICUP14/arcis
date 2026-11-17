package app.arcis

import app.arcis.glasses.phoneName
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
import app.arcis.host.SavedHost
import app.arcis.media.MediaMode
import app.arcis.status.GlassesStatus
import app.arcis.host.Moonlight
import app.arcis.core.switcher.ResumeAction
import app.arcis.core.switcher.ResumeEvent
import app.arcis.core.switcher.ResumePolicy
import app.arcis.core.switcher.Target
import android.content.Context
import app.arcis.core.net.AddressOrder
import android.content.ClipData
import android.content.ClipboardManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import app.arcis.core.hid.HidKeys
import app.arcis.core.text.TextEdit
import app.arcis.input.RemoteTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    lateinit var media: MediaMode
        private set
    lateinit var status: GlassesStatus
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _computerControl = MutableStateFlow(false)

    /**
     * Where the phone's typing field sends text: the computer (Moonlight's stream in front,
     * bridge connected) or the Bluetooth device being controlled. Null = an app on the
     * glasses, which gets the phone keyboard directly.
     */
    lateinit var remoteTyping: StateFlow<RemoteTarget?>
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
        hosts = HostManager(this)
        hostTarget = HostTarget(hosts)
        media = MediaMode(this, hosts, hostTarget).also { it.start() }
        status = GlassesStatus(this, hosts, media, controllerPrefs)
        remoteTyping = combine(_computerControl, bluetoothTarget.activeState) { computer, bluetooth ->
            when {
                computer -> RemoteTarget.COMPUTER
                bluetooth -> RemoteTarget.BLUETOOTH
                else -> null
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)
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
                if (_computerControl.value != active) {
                    Log.d("ArcisHost", "computer control ${if (active) "on" else "off"} (stream=$stream, connected=$connected)")
                    _computerControl.value = active
                    ArcisAccessibilityService.instance?.setCursorVisible(!active)
                }
            }
        }
    }

    /** Sends a change made in the phone's typing field to the [remoteTyping] target. */
    fun typeRemote(edit: TextEdit) {
        when (remoteTyping.value) {
            RemoteTarget.COMPUTER -> {
                repeat(edit.backspaces) { hosts.key("Backspace") }
                if (edit.insert.isNotEmpty()) hosts.text(edit.insert)
            }
            RemoteTarget.BLUETOOTH -> {
                hid.backspace(edit.backspaces)
                if (edit.insert.isNotEmpty()) hid.type(edit.insert)
            }
            null -> Unit
        }
    }

    /** Enter / Send on the phone keyboard, for the [remoteTyping] target. */
    fun enterRemote() {
        when (remoteTyping.value) {
            RemoteTarget.COMPUTER -> hosts.key("Enter")
            RemoteTarget.BLUETOOTH -> hid.key(HidKeys.Stroke(HidKeys.ENTER))
            null -> Unit
        }
    }

    /**
     * Moonlight shows a pairing PIN: have the connected computer's bridge enter it into
     * Sunshine, so nothing has to be typed on the computer.
     */
    fun onMoonlightPin(pin: String) {
        val overlay = ArcisAccessibilityService.instance?.uiOverlay
        val connected = hosts.connection.value as? HostConnection.Connected
        if (connected != null && hosts.sunshinePin(pin, phoneName(this))) {
            Log.i("ArcisHost", "Moonlight pairing PIN sent to ${connected.host.name}")
            overlay?.flash("Pairing Moonlight with ${connected.host.name}…")
        } else {
            overlay?.flash("Moonlight PIN $pin: enter it in Sunshine, or open the computer from Arcis Home first", PROBLEM_FLASH_MS)
        }
    }

    /**
     * Opens a computer: connects its bridge and starts (or resumes) its Desktop stream in
     * Moonlight on [displayId], skipping Moonlight's menus when it can.
     */
    fun openComputer(caller: Context, host: SavedHost, displayId: Int) {
        hosts.connect(host, phoneName(this))
        val moonlight = phoneApps.list().firstOrNull { HostTarget.isMoonlight(it.packageName) }
        if (moonlight == null) {
            ArcisAccessibilityService.instance?.uiOverlay?.flash("Install Moonlight to see ${host.name}'s screen")
            return
        }
        // Straight into the computer's desktop; Moonlight's own start screen if that's refused.
        if (!phoneApps.launch(caller, Moonlight.desktopIntent(moonlight.packageName, host.name), displayId)) {
            phoneApps.open(caller, moonlight, displayId)
        }
        hintIfMoonlightCantReach(host)
    }

    /**
     * Starts a computer's stream again (Moonlight ends it when another app covers it; the
     * computer's Desktop keeps running, so this reconnects in a second or two).
     */
    fun resumeComputer(caller: Context, pc: Target.Pc, displayId: Int): Boolean {
        val host = hosts.store.hosts.value.firstOrNull { it.fingerprint == pc.hostId } ?: return false
        openComputer(caller, host, displayId)
        return true
    }

    /**
     * The glasses were connected: show Home, and if they were away only briefly, reopen
     * what was in front over it (see [ResumePolicy]).
     */
    fun onGlassesConnected(caller: Context, displayId: Int) {
        GlassesLauncher.showHome(caller, displayId)
        val action = ResumePolicy.decide(switcher.lastTarget, System.currentTimeMillis(), ResumeEvent.GlassesConnected)
        if (action !is ResumeAction.Reopen) return
        Log.i("ArcisResume", "glasses back: reopening ${action.target}")
        when (val t = action.target) {
            is Target.PhoneApp -> phoneApps.find(t.pkg)?.let { phoneApps.open(caller, it, displayId) }
            is Target.Pc -> resumeComputer(caller, t, displayId)
            is Target.Bluetooth -> GlassesLauncher.showBluetooth(caller, displayId)
        }
    }

    private var moonlightHintJob: Job? = null

    /**
     * Away from home, Moonlight may only know a computer's home address, so its stream never
     * starts. Arcis can't add addresses to Moonlight, so when the bridge connected over a
     * remote address (e.g. Tailscale) and no stream shows within a few seconds, say which
     * address to add in Moonlight and copy it. Not shown again for a computer once its
     * stream worked over a remote address.
     */
    fun hintIfMoonlightCantReach(host: SavedHost) {
        moonlightHintJob?.cancel()
        moonlightHintJob = scope.launch {
            val c = withTimeoutOrNull(BRIDGE_WAIT_MS) {
                hosts.connection.first { it is HostConnection.Connected || it is HostConnection.Failed }
            } as? HostConnection.Connected ?: return@launch
            if (!AddressOrder.isRemote(c.address)) return@launch
            val hints = getSharedPreferences("hints", MODE_PRIVATE)
            val key = "moonlight_remote_ok_${host.fingerprint}"
            if (hints.getBoolean(key, false)) return@launch
            val streamed = withTimeoutOrNull(MOONLIGHT_GRACE_MS) {
                ArcisAccessibilityService.foregroundActivity.first { it != null && HostTarget.isMoonlightStream(it.packageName, it) }
            } != null
            if (streamed) {
                hints.edit().putBoolean(key, true).apply()
                return@launch
            }
            runCatching {
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("${host.name} address", c.address))
            }
            ArcisAccessibilityService.instance?.uiOverlay?.flash(
                "Moonlight can't find ${host.name} away from home? In Moonlight tap +, paste ${c.address} (copied)",
                HINT_FLASH_MS,
            )
        }
    }

    /** Says so on the glasses when a computer can't be reached or drops, e.g. over Moonlight. */
    private fun watchHostProblems() {
        scope.launch {
            hosts.notices.collect { ArcisAccessibilityService.instance?.uiOverlay?.flash(it, PROBLEM_FLASH_MS) }
        }
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
        private const val HINT_FLASH_MS = 10_000L
        private const val BRIDGE_WAIT_MS = 15_000L
        private const val MOONLIGHT_GRACE_MS = 12_000L

        lateinit var instance: ArcisApp
            private set
    }
}
