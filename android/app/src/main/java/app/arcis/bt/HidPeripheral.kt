package app.arcis.bt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import app.arcis.core.hid.HidKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/** What the Bluetooth side is doing right now. */
data class HidState(
    val bluetoothOn: Boolean = false,
    val permitted: Boolean = false,
    val registered: Boolean = false,
    val connected: BluetoothDevice? = null,
    val connecting: BluetoothDevice? = null,
)

/**
 * The phone as a Bluetooth keyboard + mouse (standard BluetoothHidDevice API, Android 9+).
 * Works with any laptop, tablet, TV or console that accepts Bluetooth keyboards/mice;
 * nothing needs to be installed on the other device.
 */
@SuppressLint("MissingPermission") // every entry point checks hasPermission()
class HidPeripheral(private val context: Context) {

    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val sender = Executors.newSingleThreadExecutor()
    private var hid: BluetoothHidDevice? = null
    private var proxyRequested = false
    private var buttons = 0

    private val _state = MutableStateFlow(HidState())
    val state: StateFlow<HidState> = _state.asStateFlow()

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Connects to the HID profile and registers Arcis as a keyboard + mouse. Safe to call repeatedly. */
    fun start() {
        val permitted = hasPermission()
        _state.value = _state.value.copy(bluetoothOn = adapter?.isEnabled == true, permitted = permitted)
        if (!permitted || adapter?.isEnabled != true || hid != null || proxyRequested) return
        proxyRequested = adapter.getProfileProxy(context, proxyListener, BluetoothProfile.HID_DEVICE)
    }

    fun pairedDevices(): List<BluetoothDevice> =
        if (hasPermission()) adapter?.bondedDevices.orEmpty().sortedBy { it.name ?: it.address } else emptyList()

    fun connect(device: BluetoothDevice) {
        val h = hid ?: return
        _state.value = _state.value.copy(connecting = device)
        if (!h.connect(device)) _state.value = _state.value.copy(connecting = null)
    }

    fun disconnect() {
        val h = hid ?: return
        _state.value.connected?.let { h.disconnect(it) }
    }

    // ------------------------------------------------------------------ input

    fun move(dx: Int, dy: Int) = mouse(dx, dy, 0)

    fun scroll(wheel: Int) = mouse(0, 0, wheel)

    fun press(button: Int) {
        buttons = buttons or button
        mouse(0, 0, 0)
    }

    fun release(button: Int) {
        buttons = buttons and button.inv()
        mouse(0, 0, 0)
    }

    fun click(button: Int) {
        press(button)
        release(button)
    }

    private fun mouse(dx: Int, dy: Int, wheel: Int) {
        val b = buttons
        HidKeys.mouseReports(b, dx, dy, wheel).forEach { send(HidKeys.MOUSE_REPORT_ID, it) }
    }

    fun key(stroke: HidKeys.Stroke) {
        send(HidKeys.KEYBOARD_REPORT_ID, HidKeys.keyboardReport(stroke.modifiers, stroke.usage))
        send(HidKeys.KEYBOARD_REPORT_ID, HidKeys.keyboardRelease)
    }

    fun type(text: String) = text.forEach { c -> HidKeys.strokeFor(c)?.let(::key) }

    fun backspace(times: Int) = repeat(times) { key(HidKeys.Stroke(HidKeys.BACKSPACE)) }

    private fun send(reportId: Int, data: ByteArray) {
        val h = hid ?: return
        val device = _state.value.connected ?: return
        sender.execute {
            h.sendReport(device, reportId, data)
            Thread.sleep(REPORT_GAP_MS) // keep bursts (typing) from being dropped
        }
    }

    // ------------------------------------------------------------------ callbacks

    private val proxyListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            val h = proxy as BluetoothHidDevice
            hid = h
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "Arcis",
                "Arcis trackpad and keyboard",
                "Arcis",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                HidKeys.reportDescriptor,
            )
            val ok = h.registerApp(sdp, null, null, Executors.newSingleThreadExecutor(), callback)
            Log.i(TAG, "registerApp -> $ok")
        }

        override fun onServiceDisconnected(profile: Int) {
            hid = null
            proxyRequested = false
            _state.value = _state.value.copy(registered = false, connected = null, connecting = null)
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.i(TAG, "app registered=$registered")
            _state.value = _state.value.copy(registered = registered)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.i(TAG, "connection ${device.address} state=$state")
            _state.value = when (state) {
                BluetoothProfile.STATE_CONNECTED -> _state.value.copy(connected = device, connecting = null)
                BluetoothProfile.STATE_CONNECTING -> _state.value.copy(connecting = device)
                else -> _state.value.copy(
                    connected = _state.value.connected?.takeIf { it != device },
                    connecting = null,
                )
            }
        }
    }

    companion object {
        private const val TAG = "ArcisBt"
        private const val REPORT_GAP_MS = 4L
        const val BUTTON_LEFT = 1
        const val BUTTON_RIGHT = 2
    }
}
