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
            )
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
