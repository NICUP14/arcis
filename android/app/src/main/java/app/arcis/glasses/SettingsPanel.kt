package app.arcis.glasses

import android.app.ActivityOptions
import android.content.Intent
import android.view.Display
import androidx.compose.ui.platform.LocalContext
import app.arcis.MainActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.arcis.ArcisApp
import app.arcis.controller.ControllerPrefs
import java.util.Locale

/** Languages offered for speech-to-text; null = the phone's own language. */
private val DictationLanguages: List<Pair<String?, String>> = listOf(
    null to "Phone language",
    "en-US" to "English (US)",
    "en-GB" to "English (UK)",
    "ro-RO" to "Română",
    "de-DE" to "Deutsch",
    "fr-FR" to "Français",
    "es-ES" to "Español",
    "it-IT" to "Italiano",
    "pt-BR" to "Português (Brasil)",
    "nl-NL" to "Nederlands",
    "pl-PL" to "Polski",
    "hu-HU" to "Magyar",
)

/**
 * Arcis settings, shown inside Home on the glasses. Changes apply right away, so you can
 * adjust a speed and feel it with the same trackpad. Big −/+ and ‹/› targets instead of
 * sliders and lists: easier to hit with the pointer.
 */
@Composable
fun SettingsPanel(onDone: () -> Unit) {
    val prefs = ArcisApp.instance.controllerPrefs
    val context = LocalContext.current
    val pointer by prefs.pointerSpeed.collectAsState()
    val scroll by prefs.scrollSpeed.collectAsState()
    val language by prefs.dictationLanguage.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .background(Color(0xFF111319), RoundedCornerShape(20.dp))
                .border(1.dp, GlassesColors.CardBorder, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 22.dp)
                .widthIn(max = 480.dp),
        ) {
            Text("Settings", color = GlassesColors.Text, fontSize = 22.sp)
            Spacer(Modifier.height(12.dp))
            SpeedSetting("Pointer speed", "How far the pointer moves for a swipe on the phone", pointer, prefs::setPointerSpeed)
            Spacer(Modifier.height(14.dp))
            SpeedSetting("Scroll speed", "How far pages scroll for a two-finger swipe", scroll, prefs::setScrollSpeed)
            Spacer(Modifier.height(14.dp))
            LanguageSetting(language, prefs::setDictationLanguage)
            Spacer(Modifier.height(14.dp))
            val statusBar by prefs.statusBar.collectAsState()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Status bar", color = GlassesColors.Text, fontSize = 16.sp)
                    Text("Time, battery, computer and mic in the corner", color = GlassesColors.Dim, fontSize = 12.sp)
                }
                Button(if (statusBar) "On" else "Off", enabled = true) { prefs.setStatusBar(!statusBar) }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button("Done", enabled = true, onClick = onDone)
                val isDefault = pointer == ControllerPrefs.DEFAULT_POINTER_SPEED &&
                    scroll == ControllerPrefs.DEFAULT_POINTER_SPEED && language == null
                Button("Defaults", enabled = !isDefault) {
                    prefs.setPointerSpeed(ControllerPrefs.DEFAULT_POINTER_SPEED)
                    prefs.setScrollSpeed(ControllerPrefs.DEFAULT_POINTER_SPEED)
                    prefs.setDictationLanguage(null)
                }
                // The checklist lives on the phone (permission screens open there).
                Button("Phone setup", enabled = true) {
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .setAction(MainActivity.ACTION_PHONE_SETUP)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedSetting(title: String, description: String, value: Float, onChange: (Float) -> Unit) {
    Text(title, color = GlassesColors.Text, fontSize = 17.sp)
    Text(description, color = GlassesColors.Dim, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StepButton("−", enabled = value > ControllerPrefs.MIN_POINTER_SPEED) {
            onChange(value - ControllerPrefs.POINTER_SPEED_STEP)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            SpeedBar(value)
            Spacer(Modifier.height(4.dp))
            Text(String.format(Locale.US, "%.2f×", value), color = GlassesColors.Dim, fontSize = 13.sp)
        }
        StepButton("+", enabled = value < ControllerPrefs.MAX_POINTER_SPEED) {
            onChange(value + ControllerPrefs.POINTER_SPEED_STEP)
        }
    }
}

@Composable
private fun LanguageSetting(tag: String?, onChange: (String?) -> Unit) {
    val index = DictationLanguages.indexOfFirst { it.first == tag }.coerceAtLeast(0)
    fun pick(i: Int) = onChange(DictationLanguages[(i + DictationLanguages.size) % DictationLanguages.size].first)
    Text("Dictation language", color = GlassesColors.Text, fontSize = 17.sp)
    Text("Speech-to-text (mic button)", color = GlassesColors.Dim, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StepButton("‹", enabled = true) { pick(index - 1) }
        Text(
            DictationLanguages[index].second,
            color = GlassesColors.Text,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        StepButton("›", enabled = true) { pick(index + 1) }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .background(if (enabled) Color(0xFF1E3A6E) else GlassesColors.Card, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) { Text(label, color = if (enabled) GlassesColors.Text else GlassesColors.Dim, fontSize = 26.sp) }
}

/** One segment per step, filled up to the current value. */
@Composable
private fun SpeedBar(value: Float) {
    val step = ControllerPrefs.POINTER_SPEED_STEP
    val steps = Math.round((ControllerPrefs.MAX_POINTER_SPEED - ControllerPrefs.MIN_POINTER_SPEED) / step) + 1
    val filled = Math.round((value - ControllerPrefs.MIN_POINTER_SPEED) / step) + 1
    Row(Modifier.fillMaxWidth().height(18.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(steps) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(18.dp)
                    .background(if (i < filled) Color(0xFF6EA8FF) else GlassesColors.Card, RoundedCornerShape(4.dp)),
            )
        }
    }
}
