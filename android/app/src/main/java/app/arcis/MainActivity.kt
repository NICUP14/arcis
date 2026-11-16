package app.arcis

import android.app.ActivityOptions
import android.content.Intent
import android.view.Display
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import app.arcis.a11y.ArcisAccessibilityService
import app.arcis.controller.TrackpadView
import app.arcis.core.input.Gesture
import app.arcis.glasses.GlassesLauncher
import app.arcis.input.InputRouter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Phone-side controller: setup/status screens, or the trackpad when everything is ready. */
class MainActivity : ComponentActivity() {

    private val displays get() = ArcisApp.instance.displays
    private lateinit var router: InputRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The controller belongs on the phone. Android starts new tasks on the focused display,
        // which can be the glasses; if that happens, move to the phone's display.
        if (display?.displayId != Display.DEFAULT_DISPLAY) {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle(),
            )
            finish()
            return
        }
        router = InputRouter(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            val screen by displays.screen.collectAsState()
            val a11y by ArcisAccessibilityService.connected.collectAsState()
            val trackpad = screen != null && a11y
            LaunchedEffect(trackpad) { setTrackpadWindow(trackpad) }
            when {
                screen == null -> Message("Connect your glasses or an external screen over USB-C")
                !a11y -> AccessibilitySetup { openAccessibilitySettings() }
                else -> Controller(router::handle)
            }
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

    /**
     * In trackpad mode the controller is landscape and NOT focusable: touching the phone
     * then doesn't steal input focus from the external screen, so Back and typed keys
     * reach the app shown there.
     */
    private fun setTrackpadWindow(trackpad: Boolean) {
        requestedOrientation = if (trackpad) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (trackpad) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
private fun Message(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Arcis", color = Color.White, fontSize = 22.sp)
            Text(
                text,
                color = Color(0xFF8A90A0),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun AccessibilitySetup(onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("One-time setup", color = Color.White, fontSize = 22.sp)
            Text(
                "Turn on \"Arcis controller\" in Accessibility so your phone can control apps on the " +
                    "external screen.\n\nIf Android says the setting is restricted: open App info for " +
                    "Arcis, tap ⋮ → \"Allow restricted settings\", then try again.",
                color = Color(0xFF8A90A0),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            Button(onClick = onOpenSettings) { Text("Open Accessibility settings") }
        }
    }
}
