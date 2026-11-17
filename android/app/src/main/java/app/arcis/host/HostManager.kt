package app.arcis.host

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import app.arcis.core.net.AddressOrder
import app.arcis.host.BridgeConnection.Companion.msg
import app.arcis.host.BridgeConnection.Companion.request
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import app.arcis.core.net.PairLink
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A bridge seen on the local network. */
data class FoundHost(val name: String, val address: String, val port: Int, val fingerprintPrefix: String)

sealed interface HostConnection {
    data object Idle : HostConnection
    data class Connecting(val name: String) : HostConnection
    data class Connected(val host: SavedHost, val address: String, val os: String, val latencyMs: Long?) : HostConnection
    data class Failed(val name: String, val message: String) : HostConnection
}

/**
 * Finds Arcis Bridges (mDNS), pairs with them, and keeps one connection to the
 * computer currently in use. Input methods are fire-and-forget.
 */
class HostManager(context: Context) {

    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(NsdManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val store = HostStore(appContext)

    private val _found = MutableStateFlow<List<FoundHost>>(emptyList())
    val found: StateFlow<List<FoundHost>> = _found.asStateFlow()

    private val _connection = MutableStateFlow<HostConnection>(HostConnection.Idle)
    val connection: StateFlow<HostConnection> = _connection.asStateFlow()

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    @Volatile
    private var conn: BridgeConnection? = null
    private var pingJob: Job? = null
    /** One listener per discovery run: NsdManager keeps a stopped listener busy until it reports back. */
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // ------------------------------------------------------------------ discovery

    fun startDiscovery() {
        if (discoveryListener != null) return
        val listener = newDiscoveryListener()
        discoveryListener = listener
        runCatching { nsd.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { discoveryListener = null; Log.w(TAG, "discovery failed", it) }
    }

    fun stopDiscovery() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        runCatching { nsd.stopServiceDiscovery(listener) }
    }

    private fun newDiscoveryListener() = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit
        override fun onDiscoveryStopped(serviceType: String) = Unit
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            if (discoveryListener === this) discoveryListener = null
        }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        override fun onServiceLost(info: NsdServiceInfo) {
            _found.value = _found.value.filterNot { it.name == info.serviceName }
        }

        @Suppress("DEPRECATION")
        override fun onServiceFound(info: NsdServiceInfo) {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                override fun onServiceResolved(r: NsdServiceInfo) {
                    val address = (if (Build.VERSION.SDK_INT >= 34) r.hostAddresses.firstOrNull() else r.host)
                        ?.hostAddress ?: return
                    val fp = r.attributes["fp"]?.let { String(it) }.orEmpty()
                    val host = FoundHost(r.serviceName, address, r.port, fp)
                    _found.value = _found.value.filterNot { it.name == host.name } + host
                    // A paired computer may have a new LAN address: remember it.
                    store.hosts.value.firstOrNull { fp.isNotEmpty() && it.fingerprint.startsWith(fp) }
                        ?.let { if (address !in it.addresses) store.addAddress(it.fingerprint, address) }
                }
            })
        }
    }

    // ------------------------------------------------------------------ pairing / connecting

    private val _paired = MutableSharedFlow<SavedHost>(extraBufferCapacity = 4)

    /** Each computer as it gets paired (typed PIN or scanned code). */
    val paired: SharedFlow<SavedHost> = _paired.asSharedFlow()

    /**
     * Pairs with the bridge at [address] using the PIN it shows. Returns the saved host.
     * [pinnedFingerprint] (from a scanned code) makes sure it is that computer.
     */
    suspend fun pair(
        address: String,
        port: Int,
        pin: String,
        phoneName: String,
        pinnedFingerprint: String? = null,
        knownAddresses: List<String> = emptyList(),
    ): Result<SavedHost> =
        withContext(Dispatchers.IO) {
            runCatching {
                val c = BridgeConnection.open(address, port, pinnedFingerprint, AddressOrder.connectTimeoutMs(address))
                try {
                    val reply = c.request(msg("pair", "pin" to pin, "name" to phoneName))
                    if (reply.optString("t") != "paired") throw PairRefused(reply.optString("msg", "Pairing failed"))
                    SavedHost(
                        name = reply.optString("host", address),
                        // The bridge tells us its other addresses (LAN, Tailscale): keep them all.
                        addresses = AddressOrder.order(listOf(address) + knownAddresses, reply.stringList("addresses")),
                        port = port,
                        fingerprint = c.fingerprint,
                        token = reply.getString("token"),
                    ).also(store::save)
                } finally {
                    c.close()
                }
            }.onSuccess { _paired.tryEmit(it) }
        }

    /**
     * Pairs using the bridge's QR code: tries each of its addresses (home network first,
     * then e.g. Tailscale) until one answers with the right certificate.
     */
    suspend fun pair(link: PairLink, phoneName: String): Result<SavedHost> {
        var failure: Throwable? = null
        for (address in AddressOrder.order(link.addresses)) {
            val r = pair(address, link.port, link.pin, phoneName, link.fingerprint, link.addresses)
            if (r.isSuccess) return r
            failure = r.exceptionOrNull()
            // A refused PIN won't do better on another address; a network error might.
            if (failure is PairRefused) break
        }
        val reason = failure?.takeIf { it is PairRefused }?.message
            ?: if (!phoneOnTailscale() && link.addresses.any { AddressOrder.kind(it) == AddressOrder.Kind.TAILSCALE }) {
                "Can't reach ${link.host}. Same Wi-Fi, or Tailscale on this phone?"
            } else {
                "Can't reach ${link.host}. Is it on the same network?"
            }
        return Result.failure(PairRefused(reason))
    }

    /** [pair] with a scanned code without tying it to a screen; [onResult] runs on the main thread. */
    fun pairInBackground(link: PairLink, phoneName: String, onResult: (Result<SavedHost>) -> Unit) {
        scope.launch {
            val r = pair(link, phoneName)
            mainHandler.post { onResult(r) }
        }
    }

    /** The bridge answered but refused (e.g. wrong PIN). */
    class PairRefused(message: String) : Exception(message)

    /** Connects to [host], trying its addresses in order; replaces any current connection. */
    fun connect(host: SavedHost, phoneName: String) {
        disconnect()
        _connection.value = HostConnection.Connecting(host.name)
        scope.launch {
            for (address in AddressOrder.order(host.addresses)) {
                try {
                    val c = BridgeConnection.open(address, host.port, host.fingerprint, AddressOrder.connectTimeoutMs(address))
                    val reply = c.request(msg("hello", "token" to host.token, "name" to phoneName))
                    if (reply.optString("t") != "welcome") {
                        c.close()
                        error(reply.optString("msg", "Refused"))
                    }
                    val os = reply.optString("os")
                    val updated = host.copy(
                        os = os,
                        addresses = AddressOrder.order(host.addresses, reply.stringList("addresses")),
                        macs = reply.stringList("macs").ifEmpty { host.macs },
                    )
                    if (updated != host) store.save(updated)
                    conn = c
                    _connection.value = HostConnection.Connected(updated, address, os, null)
                    c.start(onMessage = ::onMessage, onClosed = { onClosed(c, host) })
                    startPing()
                    return@launch
                } catch (e: Exception) {
                    Log.i(TAG, "connect ${host.name} via $address failed: ${e.message}")
                }
            }
            val hint = if (!phoneOnTailscale() && host.addresses.any { AddressOrder.kind(it) == AddressOrder.Kind.TAILSCALE }) {
                "Not reachable. Away from home? Turn on Tailscale on this phone"
            } else {
                "Can't reach its Arcis Bridge. Is it running?"
            }
            _connection.value = HostConnection.Failed(host.name, hint)
        }
    }

    fun disconnect() {
        pingJob?.cancel()
        conn?.close()
        conn = null
        _connection.value = HostConnection.Idle
    }

    private fun onClosed(c: BridgeConnection, host: SavedHost) {
        if (conn !== c) return
        conn = null
        pingJob?.cancel()
        _connection.value = HostConnection.Failed(host.name, "Connection lost")
    }

    private fun onMessage(m: org.json.JSONObject) {
        if (m.optString("t") == "clip") {
            // Copied on the computer: make it pasteable on the phone too.
            val text = m.optString("text")
            Log.i(TAG, "clipboard from computer (${text.length} chars)")
            mainHandler.post {
                runCatching {
                    appContext.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("From computer", text))
                }
            }
            return
        }
        if (m.optString("t") == "pong") {
            val rtt = SystemClock.elapsedRealtime() - m.optLong("time")
            (_connection.value as? HostConnection.Connected)?.let { _connection.value = it.copy(latencyMs = rtt) }
        }
    }

    private fun startPing() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                send(msg("ping", "time" to SystemClock.elapsedRealtime()))
                delay(2_000)
            }
        }
    }

    // ------------------------------------------------------------------ input

    val isConnected: Boolean get() = conn != null

    /** True when the connected computer is a Mac (Cmd instead of Ctrl for shortcuts). */
    val isMac: Boolean get() = (_connection.value as? HostConnection.Connected)?.os == "darwin"

    private fun send(m: org.json.JSONObject) = conn?.send(m)

    fun move(dx: Float, dy: Float) = send(msg("move", "dx" to dx.toDouble(), "dy" to dy.toDouble()))
    fun button(button: String, down: Boolean) = send(msg("button", "button" to button, "down" to down))
    fun click(button: String) = send(msg("click", "button" to button))
    fun scroll(dx: Float, dy: Float) = send(msg("scroll", "dx" to dx.toDouble(), "dy" to dy.toDouble()))
    fun key(key: String, mods: List<String> = emptyList()) = send(msg("key", "key" to key, "mods" to mods.ifEmpty { null }))
    fun text(text: String) = send(msg("text", "text" to text))

    /** A frame of microphone audio (16 kHz mono s16le) for the computer's virtual mic. */
    fun sendAudio(pcm: ByteArray) = send(msg("audio", "audio" to android.util.Base64.encodeToString(pcm, android.util.Base64.NO_WRAP)))

    /** Name of the connected computer, if any. */
    val connectedName: String? get() = (_connection.value as? HostConnection.Connected)?.host?.name

    /** Puts [text] on the connected computer's clipboard. */
    fun sendClipboard(text: String) = send(msg("clip", "text" to text))

    /**
     * Wakes [host]: a Wake-on-LAN broadcast from the phone (works at home) and, if another
     * computer's bridge is connected, through it as a relay (works from anywhere).
     * Returns false if the computer's MAC address isn't known yet.
     */
    fun wake(host: SavedHost): Boolean {
        if (host.macs.isEmpty()) return false
        scope.launch {
            for (mac in host.macs) {
                runCatching { sendMagicPacket(mac) }.onFailure { Log.i(TAG, "wake $mac: ${it.message}") }
                send(msg("wake", "mac" to mac)) // relay through the connected bridge, if any
            }
        }
        return true
    }

    private fun sendMagicPacket(mac: String) {
        val hw = mac.split(":", "-").map { it.toInt(16).toByte() }
        require(hw.size == 6) { "bad MAC $mac" }
        val packet = ByteArray(6) { 0xFF.toByte() } + List(16) { hw }.flatten()
        java.net.DatagramSocket().use { socket ->
            socket.broadcast = true
            for (port in listOf(9, 7)) {
                socket.send(
                    java.net.DatagramPacket(packet, packet.size, java.net.InetAddress.getByName("255.255.255.255"), port),
                )
            }
        }
    }

    /** True when this phone has a Tailscale address (100.64.0.0/10) right now. */
    fun phoneOnTailscale(): Boolean = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp }
            .flatMap { it.inetAddresses.toList() }
            .any { AddressOrder.kind(it.hostAddress.orEmpty()) == AddressOrder.Kind.TAILSCALE }
    }.getOrDefault(false)

    private fun org.json.JSONObject.stringList(key: String): List<String> =
        optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()

    private companion object {
        const val TAG = "ArcisHost"
        const val SERVICE = "_arcis._tcp"
    }
}
