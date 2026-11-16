package app.arcis

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.arcis.controller.TrackpadView
import app.arcis.core.input.Gesture
import app.arcis.display.ExternalScreen
import app.arcis.glasses.GlassesLauncher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Phone-side controller: status when no screen is connected, trackpad otherwise. */
class MainActivity : ComponentActivity() {

    private val displays get() = ArcisApp.instance.displays

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            val screen by displays.screen.collectAsState()
            LaunchedEffect(screen != null) {
                requestedOrientation = if (screen != null) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
            if (screen == null) ControllerStatus(null) else Controller(::onGesture)
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

    private fun onGesture(gesture: Gesture) {
        if (gesture !is Gesture.PointerMove && gesture !is Gesture.Scroll) Log.d(TAG, "gesture $gesture")
    }

    private companion object {
        const val TAG = "Arcis"
    }
}

@Composable
private fun Controller(onGesture: (Gesture) -> Unit) {
    var last by remember { mutableStateOf("") }
    LaunchedEffect(last) {
        if (last.isNotEmpty()) {
            delay(1200)
            last = ""
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TrackpadView(ctx, rightEdgeScroll = true) { g ->
                    if (g !is Gesture.PointerMove && g !is Gesture.Scroll) last = g.toString()
                    onGesture(g)
                }
            },
        )
        // Faint feedback label (top-left), for sighted checks only.
        Text(last, color = Color(0xFF3A3F4A), fontSize = 12.sp, modifier = Modifier.padding(16.dp))
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
                    "Connected: ${screen.name}\n${screen.widthPx}×${screen.heightPx}"
                },
                color = Color(0xFF8A90A0),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
