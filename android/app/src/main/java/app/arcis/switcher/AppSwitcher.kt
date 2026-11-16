package app.arcis.switcher

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.graphics.asImageBitmap
import app.arcis.ArcisApp
import app.arcis.a11y.ArcisAccessibilityService
import app.arcis.a11y.SwitcherCard
import app.arcis.core.input.Gesture
import app.arcis.core.input.SwitchDirection
import app.arcis.core.switcher.RecentItem
import app.arcis.core.switcher.Recents
import app.arcis.glasses.GlassesLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Phone-style app switching on the external screen: previous/next app from the
 * bottom edge, and the card switcher (swipe up + hold). Recents are learned from
 * whatever comes to the front on the external screen.
 */
class AppSwitcher(private val appContext: Context) {

    private val recents = Recents()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = ArcisApp.instance
    private var homeInFront = true
    private var bluetoothInFront = false

    fun start() {
        scope.launch {
            ArcisAccessibilityService.foregroundPackage.collect { pkg -> onForeground(pkg) }
        }
    }

    /** The Bluetooth screen (part of Arcis) came to the front / left. */
    fun noteBluetoothInFront(inFront: Boolean) {
        bluetoothInFront = inFront
        if (inFront) {
            homeInFront = false
            recents.onForeground(RecentItem(BLUETOOTH_ID, "Bluetooth"), SystemClock.uptimeMillis())
        }
    }

    /** Arcis just opened [pkg] on the external screen (don't wait for the window event). */
    fun noteOpened(pkg: String) = onForeground(pkg)

    private fun onForeground(pkg: String?) {
        if (pkg == null || pkg in IGNORED) return
        if (pkg == appContext.packageName) {
            if (!bluetoothInFront) homeInFront = true
            return
        }
        homeInFront = false
        val label = app.phoneApps.find(pkg)?.label ?: pkg
        recents.onForeground(RecentItem(pkg, label), SystemClock.uptimeMillis())
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
        if (item.id == BLUETOOTH_ID) {
            GlassesLauncher.showBluetooth(caller, screen.displayId)
            overlay()?.flash(item.label)
            return
        }
        val phoneApp = app.phoneApps.find(item.id) ?: return
        if (app.phoneApps.open(caller, phoneApp, screen.displayId)) overlay()?.flash(item.label)
    }

    private fun goHome(caller: Context) {
        val screen = app.displays.screen.value ?: return
        GlassesLauncher.showHome(caller, screen.displayId)
        overlay()?.flash("Home")
    }

    private companion object {
        const val ICON_PX = 168
        const val BLUETOOTH_ID = "arcis:bluetooth"
        val IGNORED = setOf("com.android.systemui", "android")
    }
}
