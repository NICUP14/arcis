package app.arcis.glasses

import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.arcis.ArcisApp
import app.arcis.apps.PhoneApp
import app.arcis.a11y.ArcisAccessibilityService
import app.arcis.host.FoundHost
import app.arcis.host.HostTarget
import app.arcis.host.Moonlight
import app.arcis.host.SavedHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Arcis Home on the external screen (glasses). Black background (black is
 * transparent on optical see-through glasses), status bar and launcher.
 */
class GlassesHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            GlassesHome(
                onOpen = ::open,
                onBluetooth = { GlassesLauncher.showBluetooth(this, display?.displayId ?: Display.DEFAULT_DISPLAY) },
                onComputer = { tile ->
                    when (tile) {
                        is ComputerTile.Paired -> openComputer(tile.host)
                        is ComputerTile.Found -> pairing = PairRequest(tile.host)
                        ComputerTile.Add -> pairing = PairRequest(null)
                    }
                },
                onSettings = { settingsOpen = true },
                overlay = if (settingsOpen) {
                    { SettingsPanel { settingsOpen = false } }
                } else {
                    pairing?.let { req ->
                        {
                            PairPanel(req.found) { saved ->
                                pairing = null
                                saved?.let(::openComputer)
                            }
                        }
                    }
                },
            )
        }
    }

    private data class PairRequest(val found: FoundHost?)

    private var pairing by mutableStateOf<PairRequest?>(null)
    private var settingsOpen by mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        ArcisApp.instance.hosts.startDiscovery()
        display?.displayId?.let(ArcisAccessibilityService::noteArcisScreenInFront)
    }

    override fun onPause() {
        ArcisApp.instance.hosts.stopDiscovery()
        super.onPause()
    }

    /** Connects the bridge (controls) and opens Moonlight (picture and sound) on this screen. */
    private fun openComputer(host: SavedHost) {
        val app = ArcisApp.instance
        app.hosts.connect(host, phoneName(this))
        val moonlight = app.phoneApps.list().firstOrNull { HostTarget.isMoonlight(it.packageName) }
        if (moonlight == null) {
            ArcisAccessibilityService.instance?.uiOverlay?.flash("Install Moonlight to see ${host.name}'s screen")
            return
        }
        val displayId = display?.displayId ?: Display.DEFAULT_DISPLAY
        // Straight into the computer's desktop; Moonlight's own start screen if that's refused.
        if (!app.phoneApps.launch(this, Moonlight.desktopIntent(moonlight.packageName, host.name), displayId)) {
            app.phoneApps.open(this, moonlight, displayId)
        }
    }

    /** Opens a phone app full screen on this same display. */
    private fun open(app: PhoneApp) {
        val displayId = display?.displayId ?: Display.DEFAULT_DISPLAY
        if (!ArcisApp.instance.phoneApps.open(this, app, displayId)) {
            Toast.makeText(this, "${app.label} can't open on this screen", Toast.LENGTH_SHORT).show()
        }
    }
}
