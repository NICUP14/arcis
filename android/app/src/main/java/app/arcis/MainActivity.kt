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
import kotlinx.coroutines.launch

/** Phone-side controller: setup/status screens, or the trackpad when everything is ready. */
class MainActivity : ComponentActivity() {

    private val displays get() = ArcisApp.instance.displays
    private lateinit var router: InputRouter
    private lateinit var controllerPrefs: ControllerPrefs
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val bluetoothPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { ArcisApp.instance.hid.start() }
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
        setContent {
            val screen by displays.screen.collectAsState()
            val a11y by ArcisAccessibilityService.connected.collectAsState()
            val mode by controllerPrefs.mode.collectAsState()
            val remoteTyping by ArcisApp.instance.remoteTyping.collectAsState()
            val trackpad = screen != null && a11y
            // Typing for a computer needs this window to take the keyboard (upright only).
            val focusable = !trackpad || (mode == ControllerMode.UPRIGHT && remoteTyping)
            LaunchedEffect(trackpad, mode, focusable) { setControllerWindow(trackpad, mode, focusable) }
            LaunchedEffect(trackpad) { if (trackpad) askForMicrophone() }
            when {
                screen == null -> Message("Connect your glasses or an external screen over USB-C")
                !a11y -> AccessibilitySetup { openAccessibilitySettings() }
                else -> ControllerScreen(
                    mode = mode,
                    remoteTyping = remoteTyping,
                    onRemoteText = { edit ->
                        val hosts = ArcisApp.instance.hosts
                        repeat(edit.backspaces) { hosts.key("Backspace") }
                        if (edit.insert.isNotEmpty()) hosts.text(edit.insert)
                    },
                    onRemoteEnter = { ArcisApp.instance.hosts.key("Enter") },
                    onToggleMode = controllerPrefs::toggle,
                    onGesture = router::handle,
                )
            }
        }
        handleIntent(intent)
        // Whenever an external screen appears, put Arcis Home on it.
        lifecycleScope.launch {
            var lastShownOn: Int? = null
            displays.screen.collect { screen ->
                if (screen != null && screen.displayId != lastShownOn) {
                    GlassesLauncher.showHome(this@MainActivity, screen.displayId)
                }
                lastShownOn = screen?.displayId
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
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

    /** Speech-to-text (hold volume up) needs the microphone; ask once when controlling starts. */
    private fun askForMicrophone() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    companion object {
        /** Asked by the glasses' Bluetooth screen: show a Bluetooth system prompt on the phone. */
        const val ACTION_BLUETOOTH_SETUP = "app.arcis.BLUETOOTH_SETUP"
        const val EXTRA_STEP = "step"
        const val STEP_PERMISSION = "permission"
        const val STEP_ENABLE = "enable"
        const val STEP_DISCOVERABLE = "discoverable"
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

@Composable
private fun AccessibilitySetup(onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("One-time setup", color = Color.White, fontSize = 22.sp)
            Text(
                "Turn on \"Arcis controller\" in Accessibility so your phone can control apps on the " +
                    "external screen.\n\nIf Android says the setting is restricted: open App info for " +
                    "Arcis, tap ⋮ → \"Allow restricted settings\", then try again.",
                color = Color(0xFF8A90A0),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            Button(onClick = onOpenSettings) { Text("Open Accessibility settings") }
        }
    }
}
