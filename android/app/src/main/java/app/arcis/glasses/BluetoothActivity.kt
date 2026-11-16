package app.arcis.glasses

import android.app.ActivityOptions
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.arcis.ArcisApp
import app.arcis.MainActivity
import app.arcis.a11y.ArcisAccessibilityService
import app.arcis.bt.HidState
import app.arcis.controller.ControllerMode
import app.arcis.core.hid.HidKeys
import app.arcis.core.text.TextEdit
import kotlinx.coroutines.delay

/**
 * Bluetooth mouse + keyboard on the external screen: pick a paired device, then the phone
 * controls it. While "controlling", trackpad gestures go to the device (the glasses cursor
 * hides); the phone's keyboard button and dictation type on it as keystrokes.
 */
@SuppressLint("MissingPermission")
class BluetoothActivity : ComponentActivity() {

    private val app get() = ArcisApp.instance
    private var controlling by mutableStateOf(false)
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        closeWhenScreenLeaves()
        setContent {
            val state by app.hid.state.collectAsState()
            LaunchedEffect(state.connected, controlling) { updateActive(state) }
            BluetoothScreen(state)
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        app.hid.start()
        app.switcher.noteBluetoothInFront(true)
        display?.displayId?.let(ArcisAccessibilityService::noteArcisScreenInFront)
        updateActive(app.hid.state.value)
    }

    override fun onPause() {
        resumed = false
        controlling = false
        app.switcher.noteBluetoothInFront(false)
        updateActive(app.hid.state.value)
        super.onPause()
    }

    private fun updateActive(state: HidState) {
        val active = resumed && controlling && state.connected != null
        app.bluetoothTarget.active = active
        ArcisAccessibilityService.instance?.setCursorVisible(!active)
    }

    /**
     * System prompts (permission, turn on, discoverable) appear on the phone, where they always
     * work, rather than on the glasses.
     */
    private fun onPhone(step: String, message: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_BLUETOOTH_SETUP)
                .putExtra(MainActivity.EXTRA_STEP, step)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle(),
        )
        ArcisAccessibilityService.instance?.uiOverlay?.flash(message)
    }

    @Composable
    private fun BluetoothScreen(state: HidState) {
        Column(
            Modifier.fillMaxSize().background(GlassesColors.Background).padding(horizontal = 64.dp, vertical = 36.dp),
        ) {
            val connected = state.connected
            val controllingNow = controlling && connected != null
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                // While controlling, the pointer belongs to the device, so no button here:
                // the Controlling screen says how to leave instead.
                if (!controllingNow) HomeButton()
                Text("Bluetooth mouse & keyboard", color = GlassesColors.Text, fontSize = 26.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(20.dp))
            when {
                !state.permitted -> Action("Allow Bluetooth for Arcis", "Needed to act as a mouse and keyboard.") {
                    onPhone(MainActivity.STEP_PERMISSION, "Allow Bluetooth on your phone")
                }
                !state.bluetoothOn -> Action("Turn on Bluetooth", "Confirm on your phone") {
                    onPhone(MainActivity.STEP_ENABLE, "Confirm on your phone")
                }
                controllingNow -> Controlling(connected!!)
                else -> DeviceList(state)
            }
        }
    }

    @Composable
    private fun DeviceList(state: HidState) {
        var devices by remember { mutableStateOf(app.hid.pairedDevices()) }
        LaunchedEffect(state.registered) { devices = app.hid.pairedDevices() }
        Text(
            if (state.registered) "Choose the device to control. It must have paired with this phone once."
            else "Starting Bluetooth…",
            color = GlassesColors.Dim,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Action("Pair a new device", "Makes this phone visible for 2 minutes") {
                onPhone(MainActivity.STEP_DISCOVERABLE, "Confirm on your phone, then pair from the other device")
            }
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(devices, key = { it.address }) { device ->
                val isConnected = state.connected == device
                val isConnecting = state.connecting == device
                DeviceRow(
                    name = device.name ?: device.address,
                    status = when {
                        isConnected -> "Connected — click to control"
                        isConnecting -> "Connecting…"
                        else -> "Click to connect"
                    },
                ) {
                    controlling = true
                    if (!isConnected) app.hid.connect(device)
                }
            }
        }
    }

    @Composable
    private fun Controlling(device: BluetoothDevice) {
        Text("Controlling ${device.name ?: device.address}", color = GlassesColors.Accent, fontSize = 22.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "The trackpad moves its pointer. Type with the keyboard button on the phone, or hold the mic button to speak.",
            color = GlassesColors.Dim,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "To leave: swipe up from the bottom edge of the trackpad.",
            color = GlassesColors.Text,
            fontSize = 16.sp,
        )
    }

    /** Back to Arcis Home on this screen. */
    @Composable
    private fun HomeButton() {
        Box(
            Modifier
                .background(GlassesColors.Card, RoundedCornerShape(14.dp))
                .border(1.dp, GlassesColors.CardBorder, RoundedCornerShape(14.dp))
                .clickable {
                    GlassesLauncher.showHome(this@BluetoothActivity, display?.displayId ?: Display.DEFAULT_DISPLAY)
                    finish()
                }
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) { Text("‹  Home", color = GlassesColors.Text, fontSize = 18.sp) }
    }

    @Composable
    private fun DeviceRow(name: String, status: String, onClick: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .background(GlassesColors.Card, RoundedCornerShape(16.dp))
                .border(1.dp, GlassesColors.CardBorder, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(name, color = GlassesColors.Text, fontSize = 20.sp)
            Spacer(Modifier.width(24.dp))
            Text(status, color = GlassesColors.Dim, fontSize = 15.sp)
        }
    }

    @Composable
    private fun Action(title: String, subtitle: String, onClick: () -> Unit) {
        Box(
            Modifier
                .background(GlassesColors.Card, RoundedCornerShape(16.dp))
                .border(1.dp, GlassesColors.Accent, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Column {
                Text(title, color = GlassesColors.Text, fontSize = 18.sp)
                if (subtitle.isNotEmpty()) Text(subtitle, color = GlassesColors.Dim, fontSize = 14.sp)
            }
        }
    }
}
