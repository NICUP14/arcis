package app.arcis.status

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import app.arcis.a11y.ArcisAccessibilityService
import app.arcis.controller.ControllerPrefs
import app.arcis.host.HostConnection
import app.arcis.host.HostManager
import app.arcis.host.HostTarget
import app.arcis.media.MediaMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the status bar on the glasses shows (the time and mic dot are added by the bar). */
data class StatusInfo(
    val visible: Boolean = false,
    val batteryPct: Int? = null,
    val charging: Boolean = false,
    /** Name of the computer whose bridge is connected, or null. */
    val computer: String? = null,
    val media: Boolean = false,
)

/**
 * Feeds the thin status bar in the corner of the glasses: phone battery, connected
 * computer, media mode. Hidden while a computer's stream fills the screen (it comes back
 * for a few seconds on a bottom-edge swipe, [peek]), on Arcis Home (which shows the same
 * in its header) or when turned off in Settings.
 */
class GlassesStatus(
    context: Context,
    hosts: HostManager,
    media: MediaMode,
    prefs: ControllerPrefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val battery = MutableStateFlow(Battery(null, false))
    private val peeking = MutableStateFlow(false)
    private var peekJob: Job? = null

    private data class Battery(val pct: Int?, val charging: Boolean)

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                battery.value = read(intent)
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { battery.value = read(it) }
    }

    private fun read(intent: Intent): Battery {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else null
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return Battery(pct, charging)
    }

    private val ownPackage = context.packageName

    /** Hidden over a computer's full-screen stream, and over Arcis Home (it has its own clock). */
    private val hiddenByFront = combine(
        ArcisAccessibilityService.foregroundPackage,
        ArcisAccessibilityService.foregroundActivity,
    ) { pkg, activity -> if (HostTarget.isMoonlightStream(pkg, activity)) HIDE_STREAM else if (pkg == ownPackage) HIDE_HOME else SHOW }

    val info: StateFlow<StatusInfo> = combine(
        battery,
        hosts.connection,
        media.state,
        prefs.statusBar,
        combine(hiddenByFront, peeking) { front, peek -> front == HIDE_HOME || (front == HIDE_STREAM && !peek) },
    ) { b, conn, m, enabled, hidden ->
        StatusInfo(
            visible = enabled && !hidden,
            batteryPct = b.pct,
            charging = b.charging,
            computer = (conn as? HostConnection.Connected)?.host?.name,
            media = m.active,
        )
    }.stateIn(scope, SharingStarted.Eagerly, StatusInfo())

    /** Shows the bar for a few seconds even over a computer's stream (bottom-edge swipe). */
    fun peek() {
        peekJob?.cancel()
        peeking.value = true
        peekJob = scope.launch {
            delay(PEEK_MS)
            peeking.value = false
        }
    }

    private companion object {
        const val PEEK_MS = 3_000L
        const val SHOW = 0
        const val HIDE_STREAM = 1
        const val HIDE_HOME = 2
    }
}
