package app.arcis.switcher

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import app.arcis.ArcisApp
import app.arcis.a11y.ArcisAccessibilityService
import app.arcis.a11y.SwitcherCard
import app.arcis.core.input.Gesture
import app.arcis.core.input.SwitchDirection
import app.arcis.core.switcher.LastTarget
import app.arcis.core.switcher.RecentItem
import app.arcis.core.switcher.Recents
import app.arcis.core.switcher.ResumeAction
import app.arcis.core.switcher.ResumeEvent
import app.arcis.core.switcher.ResumePolicy
import app.arcis.core.switcher.Target
import app.arcis.glasses.GlassesLauncher
import app.arcis.host.HostConnection
import app.arcis.host.HostTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Phone-style app switching on the external screen: previous/next app from the
 * bottom edge, and the card switcher (swipe up + hold). Recents are learned from
 * whatever comes to the front on the external screen. A computer's Moonlight stream is
 * its own entry ("MSI ⟳"): Moonlight ends its stream when another app covers it, so
 * switching back starts the stream again instead of showing Moonlight's menus.
 */
class AppSwitcher(private val appContext: Context) {

    private val recents = Recents()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = ArcisApp.instance
    private val store = LastTargetStore(appContext)
    private var homeInFront = true
    private var bluetoothInFront = false

    /** The last target in front (not Arcis Home), to tell how Moonlight's menus came up. */
    private var previousFront: Target? = null

    /** A package Arcis itself just opened, and when (that's not "landing" on it via Back). */
    private var openedPkg: String? = null
    private var openedAt = 0L

    fun start() {
        scope.launch {
            combine(
                ArcisAccessibilityService.foregroundPackage,
                ArcisAccessibilityService.foregroundActivity,
                app.hosts.connection,
            ) { pkg, activity, conn ->
                val connected = conn as? HostConnection.Connected
                when {
                    pkg == null || pkg in IGNORED -> null
                    pkg == appContext.packageName -> HOME
                    connected != null && HostTarget.isMoonlightStream(pkg, activity) ->
                        Target.Pc(connected.host.fingerprint, connected.host.name)
                    else -> Target.PhoneApp(pkg)
                }
            }.distinctUntilChanged().collect { target -> if (target != null) onFront(target) }
        }
        // Glasses unplugged: what was in front counts as seen until now (for resuming).
        scope.launch {
            app.displays.screen.collect { if (it == null) store.touch(System.currentTimeMillis()) }
        }
    }

    /** What was last in front, for [ResumePolicy]. */
    val lastTarget: LastTarget? get() = store.last

    /** The Bluetooth screen (part of Arcis) came to the front / left. */
    fun noteBluetoothInFront(inFront: Boolean) {
        bluetoothInFront = inFront
        if (inFront) onFront(Target.Bluetooth("")) else homeInFront = true // another app's event follows
    }

    /** Arcis just opened [pkg] on the external screen (don't wait for the window event). */
    fun noteOpened(pkg: String) {
        openedPkg = pkg
        openedAt = SystemClock.uptimeMillis()
    }

    private fun onFront(target: Target) {
        if (target == HOME) {
            if (!bluetoothInFront) homeInFront = true
            return
        }
        homeInFront = false
        val now = System.currentTimeMillis()
        val item = when (target) {
            is Target.PhoneApp -> RecentItem(target.pkg, app.phoneApps.find(target.pkg)?.label ?: target.pkg)
            is Target.Pc -> {
                // The stream replaces Moonlight's own entry: switching back resumes the stream.
                (previousFront as? Target.PhoneApp)?.pkg?.takeIf(HostTarget::isMoonlight)?.let(recents::remove)
                store.lastStream = LastTarget(target, now)
                RecentItem(PC_PREFIX + target.hostId, "${target.name} ⟳")
            }
            is Target.Bluetooth -> RecentItem(BLUETOOTH_ID, "Bluetooth")
        }
        recents.onForeground(item, SystemClock.uptimeMillis())

        val before = previousFront
        previousFront = target
        store.last = LastTarget(target, now)
        if (target is Target.PhoneApp && HostTarget.isMoonlight(target.pkg)) maybeResumeStream(target.pkg, before)
    }

    /**
     * Moonlight's menus came up after another app (e.g. Back from it): its stream was ended
     * when that app covered it, so start it again.
     */
    private fun maybeResumeStream(pkg: String, before: Target?) {
        val openedByArcis = openedPkg == pkg && SystemClock.uptimeMillis() - openedAt < OPENED_GRACE_MS
        val fromOtherApp = before != null && before !is Target.Pc && !(before is Target.PhoneApp && HostTarget.isMoonlight(before.pkg))
        if (openedByArcis || !fromOtherApp) return
        val action = ResumePolicy.decide(store.last, System.currentTimeMillis(), ResumeEvent.MoonlightMenuAfterOtherApp(store.lastStream))
        if (action is ResumeAction.ResumePc) {
            Log.i(TAG, "Moonlight's menus after another app: resuming ${action.pc.name}")
            val screen = app.displays.screen.value ?: return
            app.resumeComputer(appContext, action.pc, screen.displayId)
        }
    }

    /** Handles switcher gestures; returns false for gestures it doesn't own. */
    fun handle(gesture: Gesture, caller: Context): Boolean {
        when (gesture) {
            is Gesture.SwitchApp -> switchApp(gesture.direction, caller)
            Gesture.RecentsShow -> showRecents()
            is Gesture.RecentsMove -> overlay()?.moveSelection(gesture.steps)
            Gesture.RecentsOpenSelected -> openSelected(caller)
            Gesture.RecentsCloseSelected -> closeSelected(caller)
            else -> return false
        }
        return true
    }

    private fun overlay() = ArcisAccessibilityService.instance?.uiOverlay

    private fun switchApp(direction: SwitchDirection, caller: Context) {
        val item = if (homeInFront) {
            // From Home, "previous" returns to the last app you used.
            if (direction == SwitchDirection.PREVIOUS) recents.list.firstOrNull() else null
        } else {
            recents.step(direction, SystemClock.uptimeMillis())
        }
        if (item == null) {
            if (!homeInFront && direction == SwitchDirection.NEXT) goHome(caller) // past the newest: Home
            return
        }
        open(item, caller)
    }

    private fun showRecents() {
        val items = recents.list
        val cards = items.map { item ->
            val phoneApp = app.phoneApps.find(item.id)
                ?: if (item.id.startsWith(PC_PREFIX)) app.phoneApps.list().firstOrNull { HostTarget.isMoonlight(it.packageName) } else null
            SwitcherCard(item.id, item.label, phoneApp?.let { app.phoneApps.icon(it, ICON_PX)?.asImageBitmap() })
        }
        // Start on the previous app, like Android's recents (or the newest one from Home).
        val start = if (homeInFront || items.size < 2) 0 else 1
        overlay()?.showSwitcher(cards, start)
    }

    private fun openSelected(caller: Context) {
        val overlay = overlay() ?: return
        val card = overlay.selectedCard()
        overlay.hideSwitcher()
        val item = card?.let { recents.select(it.id) } ?: return
        open(item, caller)
    }

    private fun closeSelected(caller: Context) {
        val overlay = overlay() ?: return
        val card = overlay.selectedCard()
        overlay.hideSwitcher()
        card ?: return
        val wasFront = !homeInFront && recents.list.firstOrNull()?.id == card.id
        recents.remove(card.id)
        overlay.flash("Closed ${card.label}")
        if (wasFront) goHome(caller)
    }

    private fun open(item: RecentItem, caller: Context) {
        val screen = app.displays.screen.value ?: return
        when {
            item.id == BLUETOOTH_ID -> {
                GlassesLauncher.showBluetooth(caller, screen.displayId)
                overlay()?.flash(item.label)
            }
            item.id.startsWith(PC_PREFIX) -> {
                val pc = Target.Pc(item.id.removePrefix(PC_PREFIX), item.label.removeSuffix(" ⟳"))
                if (app.resumeComputer(caller, pc, screen.displayId)) overlay()?.flash(item.label)
            }
            else -> {
                val phoneApp = app.phoneApps.find(item.id) ?: return
                if (app.phoneApps.open(caller, phoneApp, screen.displayId)) overlay()?.flash(item.label)
            }
        }
    }

    private fun goHome(caller: Context) {
        val screen = app.displays.screen.value ?: return
        GlassesLauncher.showHome(caller, screen.displayId)
        overlay()?.flash("Home")
    }

    private companion object {
        const val TAG = "ArcisSwitcher"
        const val ICON_PX = 168
        const val BLUETOOTH_ID = "arcis:bluetooth"
        const val PC_PREFIX = "arcis:pc:"
        const val OPENED_GRACE_MS = 3_000L

        /** Arcis's own screens (Home); not a target to resume. */
        val HOME = Target.PhoneApp("arcis:home")
        val IGNORED = setOf("com.android.systemui", "android")
    }
}
