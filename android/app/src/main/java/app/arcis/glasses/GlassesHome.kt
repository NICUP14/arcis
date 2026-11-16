package app.arcis.glasses

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Colours tuned for optical see-through glasses: black = transparent. */
object GlassesColors {
    val Background = Color.Black
    val Text = Color(0xFFEDEFF5)
    val Dim = Color(0xFF8A90A0)
    val Accent = Color(0xFF6EA8FF)
    val Card = Color(0xFF15171C)
}

@Composable
fun GlassesHome() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassesColors.Background)
            .padding(horizontal = 48.dp, vertical = 24.dp),
    ) {
        StatusBar()
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Arcis", color = GlassesColors.Text, fontSize = 48.sp, fontWeight = FontWeight.Light)
                Text(
                    "Use your phone as the trackpad",
                    color = GlassesColors.Dim,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusBar() {
    val context = LocalContext.current
    var time by remember { mutableStateOf(now()) }
    var battery by remember { mutableStateOf(batteryPercent(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            time = now()
            battery = batteryPercent(context)
            delay(15_000)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, color = GlassesColors.Text, fontSize = 20.sp)
        Text(battery?.let { "Phone $it%" } ?: "", color = GlassesColors.Dim, fontSize = 16.sp)
    }
}

private fun now(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private fun batteryPercent(context: Context): Int? {
    val intent: Intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?: return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level >= 0 && scale > 0) level * 100 / scale else null
}
