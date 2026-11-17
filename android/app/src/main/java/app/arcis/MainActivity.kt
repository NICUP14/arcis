package app.arcis

import android.Manifest
import android.app.ActivityOptions
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.view.Display
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.arcis.a11y.ArcisAccessibilityService
import app.arcis.controller.ControllerMode
import app.arcis.controller.ControllerPrefs
import app.arcis.controller.ControllerScreen
import app.arcis.glasses.GlassesLauncher
import app.arcis.input.InputRouter
import app.arcis.core.setup.SetupItem
import app.arcis.host.HostTarget
import app.arcis.host.PairScanActivity
import app.arcis.setup.PhoneSetup
import app.arcis.setup.PhoneSetupScreen
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.launch

/** Phone-side controller: setup/status screens, or the trackpad when everything is ready. */
class MainActivity : ComponentActivity() {

    private val displays get() = ArcisApp.instance.displays
    private lateinit var router: InputRouter
    private lateinit var controllerPrefs: ControllerPrefs
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { setupTick++ }
    private val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ArcisApp.instance.hid.start()
        setupTick++
    }

    /** Bumped whenever setup items may have changed (back from Settings, permission answers). */
    private var setupTick by mutableIntStateOf(0)

    /** The phone setup checklist is showing (first run until done, or asked from Settings). */
    private var setupOpen by mutableStateOf(false)
    private val setupPrefs by lazy { getSharedPreferences("setup", MODE_PRIVATE) }
    private val bluetoothSystemPrompt =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { ArcisApp.instance.hid.start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The controller belongs on the phone. Android starts new tasks on the focused display,
        // which can be the glasses; if that happens, move to the phone's display.
        if (display?.displayId != Display.DEFAULT_DISPLAY) {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle(),
            )
            finish()
            return
        }
        router = InputRouter(this)
        controllerPrefs = ArcisApp.instance.controllerPrefs
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setupOpen = !setupPrefs.getBoolean(KEY_SETUP_DONE, false)
        setContent {
            val screen by displays.screen.collectAsState()
            val a11y by ArcisAccessibilityService.connected.collectAsState()
            val pairedHosts by ArcisApp.instance.hosts.store.hosts.collectAsState()
            val checklist = remember(setupTick, a11y, pairedHosts) { PhoneSetup.check(this) }
            val showSetup = setupOpen || !a11y
            val mode by controllerPrefs.mode.collectAsState()
            val remoteTarget by ArcisApp.instance.remoteTyping.collectAsState()
            val keyboardOnPhone by ArcisAccessibilityService.keyboardOnPhone.collectAsState()
            var relayRequested by remember { mutableStateOf(false) }
            LaunchedEffect(remoteTarget) { if (remoteTarget == null) relayRequested = false }
            val relayOpen = relayRequested && remoteTarget != null
            val trackpad = screen != null && a11y && !showSetup
            // Typing for a computer / Bluetooth device needs this window to take the keyboard.
            // Focusable as soon as such a device is in front: the tap on the keyboard button
            // then moves input focus from the glasses to the phone, so the keyboard can open.
            val focusable = !trackpad || remoteTarget != null
            LaunchedEffect(trackpad, mode, focusable) { setControllerWindow(trackpad, mode, focusable) }
            LaunchedEffect(trackpad) { if (trackpad) askForMicrophone() }
            when {
                showSetup -> PhoneSetupScreen(
                    checklist = checklist,
                    onAction = ::fixSetupItem,
                    onDone = {
                        setupPrefs.edit().putBoolean(KEY_SETUP_DONE, true).apply()
                        setupOpen = false
                    },
                    onLater = { setupOpen = false },
                )
                screen == null -> Message("Connect your glasses or an external screen over USB-C")
                else -> ControllerScreen(
                    mode = mode,
                    remoteTarget = remoteTarget,
                    relayOpen = relayOpen,
                    keyboardShowing = keyboardOnPhone,
                    onKeyboard = {
                        val svc = ArcisAccessibilityService.instance
                        when {
                            remoteTarget != null -> relayRequested = !relayRequested
                            keyboardOnPhone -> svc?.hideKeyboard()
                            svc?.showKeyboardForField() != true ->
                                svc?.uiOverlay?.flash("Select a text field on the screen first")
                        }
                    },
                    onRemoteText = ArcisApp.instance::typeRemote,
                    onRemoteEnter = ArcisApp.instance::enterRemote,
                    onToggleMode = controllerPrefs::toggle,
                    onDictationStart = ArcisApp.instance.voice::startDictation,
                    onDictationStop = ArcisApp.instance.voice::stopDictation,
                    onGesture = router::handle,
                )
            }
        }
        handleIntent(intent)
        // Whenever an external screen appears, put Arcis Home on it (and reopen what was in
        // front if the glasses were away only briefly). Remembered across controller
        // restarts, so reopening the controller doesn't cover what's playing.
        lifecycleScope.launch {
            displays.screen.collect { screen ->
                if (screen != null && screen.displayId != homeShownOn) {
                    ArcisApp.instance.onGlassesConnected(this@MainActivity, screen.displayId)
                }
                homeShownOn = screen?.displayId
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_PHONE_SETUP) {
            setupOpen = true
            return
        }
        if (intent?.action != ACTION_BLUETOOTH_SETUP) return
        when (intent.getStringExtra(EXTRA_STEP)) {
            STEP_PERMISSION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bluetoothPermission.launch(
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE),
                )
            }
            STEP_ENABLE -> bluetoothSystemPrompt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            STEP_DISCOVERABLE -> bluetoothSystemPrompt.launch(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120),
            )
        }
    }

    override fun onResume() {
        super.onResume()
        displays.refresh()
        setupTick++ // re-check the setup items (e.g. back from a Settings screen)
    }

    /** Opens the exact place that fixes [item]. */
    private fun fixSetupItem(item: SetupItem) {
        runCatching {
            when (item) {
                SetupItem.ACCESSIBILITY -> openAccessibilitySettings()
                SetupItem.MOONLIGHT -> {
                    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$MOONLIGHT_PACKAGE"))
                    if (market.resolveActivity(packageManager) != null) {
                        startActivity(market)
                    } else {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$MOONLIGHT_PACKAGE")))
                    }
                }
                SetupItem.BRIDGE -> PairScanActivity.start(this)
                SetupItem.NOTIFICATIONS -> startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                SetupItem.MICROPHONE -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                SetupItem.BLUETOOTH -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    bluetoothPermission.launch(
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE),
                    )
                }
                SetupItem.BATTERY -> startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
                )
                SetupItem.MOONLIGHT_PIP -> ArcisApp.instance.phoneApps.list()
                    .firstOrNull { HostTarget.isMoonlight(it.packageName) }
                    ?.let { packageManager.getLaunchIntentForPackage(it.packageName) }
                    ?.let(::startActivity)
            }
        }.onFailure { Log.w("ArcisSetup", "can't open the setting for $item", it) }
    }

    /**
     * While controlling, the orientation is locked to the chosen mode (no auto-rotation) and the
     * window is NOT focusable: touching the phone then doesn't steal input focus from the
     * external screen, so Back reaches the app shown there, and a text field selected there
     * opens the phone's own keyboard on the phone (Android's IME fallback display).
     */
    private fun setControllerWindow(trackpad: Boolean, mode: ControllerMode, focusable: Boolean) {
        requestedOrientation = when {
            !trackpad -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            mode == ControllerMode.UPRIGHT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        // ALT_FOCUSABLE_IM keeps the keyboard layered above this non-focusable window.
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        if (focusable) window.clearFlags(flags) else window.addFlags(flags)
    }

    /** Speech-to-text (mic button) needs the microphone; ask once when controlling starts. */
    private fun askForMicrophone() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    companion object {
        /** Asked by the glasses' Bluetooth screen: show a Bluetooth system prompt on the phone. */
        const val ACTION_BLUETOOTH_SETUP = "app.arcis.BLUETOOTH_SETUP"

        /** Opens the phone setup checklist (from Settings on the glasses). */
        const val ACTION_PHONE_SETUP = "app.arcis.PHONE_SETUP"
        private const val KEY_SETUP_DONE = "done"
        private const val MOONLIGHT_PACKAGE = "com.limelight"
        const val EXTRA_STEP = "step"
        const val STEP_PERMISSION = "permission"
        const val STEP_ENABLE = "enable"
        const val STEP_DISCOVERABLE = "discoverable"

        /** The external display Arcis Home was last put on (per app process). */
        private var homeShownOn: Int? = null
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun Message(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Arcis", color = Color.White, fontSize = 22.sp)
            Text(
                text,
                color = Color(0xFF8A90A0),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

