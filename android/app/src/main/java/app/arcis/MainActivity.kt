package app.arcis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.arcis.display.ExternalScreen
import app.arcis.glasses.GlassesLauncher
import kotlinx.coroutines.launch

/** Phone-side controller entry point. */
class MainActivity : ComponentActivity() {

    private val displays get() = ArcisApp.instance.displays

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val screen by displays.screen.collectAsState()
            ControllerStatus(screen)
        }
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

    override fun onResume() {
        super.onResume()
        displays.refresh()
    }
}

@Composable
private fun ControllerStatus(screen: ExternalScreen?) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Arcis", color = Color.White)
            Text(
                text = if (screen == null) {
                    "Connect your glasses or an external screen over USB-C"
                } else {
                    "Connected: ${screen.name}\n${screen.widthPx}×${screen.heightPx} @ ${"%.0f".format(screen.refreshRate)} Hz"
                },
                color = Color(0xFF8A90A0),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
