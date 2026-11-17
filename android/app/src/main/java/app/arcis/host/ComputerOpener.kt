package app.arcis.host

import android.content.Context
import android.util.Log
import app.arcis.a11y.ArcisAccessibilityService
import app.arcis.core.net.WakeAndConnect
import app.arcis.core.net.WakeEffect
import app.arcis.core.net.WakeEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Opens a computer with one tap, even when it's asleep: runs [WakeAndConnect] (connect;
 * if the bridge doesn't answer, Wake-on-LAN and retry every 2 s for a minute) and starts
 * the stream through [launchStream] once the bridge answers.
 */
class ComputerOpener(
    private val hosts: HostManager,
    private val phoneName: () -> String,
    private val launchStream: (caller: Context, host: SavedHost, displayId: Int) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val machine = WakeAndConnect()
    private var job: Job? = null
    private var request: Request? = null

    private data class Request(val caller: Context, val host: SavedHost, val displayId: Int)

    private val _waking = MutableStateFlow<String?>(null)

    /** Fingerprint of the computer being woken (its tile shows "Waking…"), or null. */
    val waking: StateFlow<String?> = _waking.asStateFlow()

    fun open(caller: Context, host: SavedHost, displayId: Int) {
        job?.cancel()
        machine.on(WakeEvent.Cancel)
        _waking.value = null
        request = Request(caller, host, displayId)
        run(machine.on(WakeEvent.Open))
    }

    private fun run(effects: List<WakeEffect>) {
        val r = request ?: return
        for (e in effects) {
            when (e) {
                WakeEffect.TryConnect -> tryConnect(r.host)
                WakeEffect.SendWake -> {
                    if (!hosts.wake(r.host)) {
                        // No MAC address known yet: can't wake it, say why.
                        machine.on(WakeEvent.Cancel)
                        flash("Can't reach ${r.host.name}. Connect to it once at home so Arcis learns how to wake it", 6_000)
                        return
                    }
                    Log.i(TAG, "waking ${r.host.name}")
                    _waking.value = r.host.fingerprint
                    flash("Waking ${r.host.name}…")
                }
                WakeEffect.LaunchStream -> {
                    _waking.value = null
                    launchStream(r.caller, r.host, r.displayId)
                }
                is WakeEffect.ScheduleRetry -> {
                    job = scope.launch {
                        delay(e.delayMs)
                        run(machine.on(WakeEvent.PollDue(System.currentTimeMillis())))
                    }
                }
                is WakeEffect.ShowFailure -> {
                    _waking.value = null
                    flash("${r.host.name} ${e.message}", 6_000)
                }
            }
        }
    }

    private fun tryConnect(host: SavedHost) {
        val now = hosts.connection.value
        if (now is HostConnection.Connected && now.host.fingerprint == host.fingerprint) {
            run(machine.on(WakeEvent.BridgeUp))
            return
        }
        hosts.connect(host, phoneName())
        job = scope.launch {
            val result = withTimeoutOrNull(CONNECT_WAIT_MS) {
                hosts.connection.first { c ->
                    (c is HostConnection.Connected && c.host.fingerprint == host.fingerprint) ||
                        (c is HostConnection.Failed && c.name == host.name)
                }
            }
            run(
                machine.on(
                    if (result is HostConnection.Connected) WakeEvent.BridgeUp else WakeEvent.BridgeDown(System.currentTimeMillis()),
                ),
            )
        }
    }

    private fun flash(text: String, ms: Long = 2_500) = ArcisAccessibilityService.instance?.uiOverlay?.flash(text, ms)

    private companion object {
        const val TAG = "ArcisWake"
        const val CONNECT_WAIT_MS = 20_000L
    }
}
