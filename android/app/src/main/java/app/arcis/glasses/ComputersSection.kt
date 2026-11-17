package app.arcis.glasses

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import app.arcis.a11y.ArcisAccessibilityService
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import app.arcis.ArcisApp
import app.arcis.host.FoundHost
import app.arcis.host.HostConnection
import app.arcis.host.PairScanActivity
import app.arcis.host.SavedHost
import app.arcis.core.Arcis
import app.arcis.core.net.AddressOrder
import kotlinx.coroutines.launch

/** What a computer tile represents. */
sealed interface ComputerTile {
    data class Paired(val host: SavedHost) : ComputerTile
    data class Found(val host: FoundHost) : ComputerTile
    data object Add : ComputerTile
}

fun computerTiles(saved: List<SavedHost>, found: List<FoundHost>): List<ComputerTile> {
    val unpaired = found.filter { f -> saved.none { f.fingerprintPrefix.isNotEmpty() && it.fingerprint.startsWith(f.fingerprintPrefix) } }
    return saved.map { ComputerTile.Paired(it) } + unpaired.map { ComputerTile.Found(it) } + ComputerTile.Add
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComputerTileView(tile: ComputerTile, connection: HostConnection, onClick: () -> Unit) {
    val (title, subtitle) = when (tile) {
        is ComputerTile.Paired -> tile.host.name to when (val c = connection) {
            is HostConnection.Connected -> if (c.host.fingerprint == tile.host.fingerprint) {
                "Connected" + (if (AddressOrder.isRemote(c.address)) " · Remote" else "") +
                    (c.latencyMs?.let { " · $it ms" } ?: "")
            } else {
                "Paired"
            }
            is HostConnection.Connecting -> if (c.name == tile.host.name) "Connecting…" else "Paired"
            is HostConnection.Failed -> if (c.name == tile.host.name) c.message else "Paired"
            HostConnection.Idle -> "Paired"
        }
        is ComputerTile.Found -> tile.host.name to "Click to pair"
        ComputerTile.Add -> "Add computer" to "by address"
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(132.dp)
            .background(GlassesColors.Card, RoundedCornerShape(16.dp))
            .border(1.dp, GlassesColors.CardBorder, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                // Right click / long-press: wake a sleeping computer.
                onLongClick = { (tile as? ComputerTile.Paired)?.host?.let(::wakeComputer) },
            )
            .padding(vertical = 14.dp, horizontal = 8.dp),
    ) {
        Box(
            Modifier.size(56.dp).background(Color(0xFF243048), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(if (tile == ComputerTile.Add) "+" else "▣", color = GlassesColors.Text, fontSize = 26.sp) }
        Spacer(Modifier.height(8.dp))
        Text(title, color = GlassesColors.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = GlassesColors.Dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun wakeComputer(host: SavedHost) {
    val sent = ArcisApp.instance.hosts.wake(host)
    ArcisAccessibilityService.instance?.uiOverlay?.flash(
        if (sent) "Waking ${host.name}…" else "Connect to ${host.name} once so Arcis learns how to wake it",
    )
}

/**
 * Pair with a bridge: address (prefilled for found computers) and the PIN it shows.
 * Shown inside Home's own window (no dialog window), which keeps focus handling simple.
 */
@Composable
fun PairPanel(prefill: FoundHost?, onDone: (SavedHost?) -> Unit) {
    val app = ArcisApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(prefill?.address.orEmpty()) }
    var pin by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // Paired by scanning the code on the phone: close and open it like a typed PIN would.
    LaunchedEffect(Unit) { app.hosts.paired.collect { if (!busy) onDone(it) } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .background(Color(0xFF111319), RoundedCornerShape(20.dp))
                .border(1.dp, GlassesColors.CardBorder, RoundedCornerShape(20.dp))
                .padding(28.dp)
                .width(460.dp),
        ) {
            Text(prefill?.let { "Pair with ${it.name}" } ?: "Add a computer", color = GlassesColors.Text, fontSize = 22.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "On the computer, click the Arcis Bridge icon by the clock › Pair a phone…, then scan its code " +
                    "with the phone. Or type the PIN shown there (also at http://127.0.0.1:${Arcis.BRIDGE_STATUS_PORT}).",
                color = GlassesColors.Dim,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(14.dp))
            Button("Scan code with the phone", enabled = !busy) {
                status = "Point the phone at the code on the computer"
                PairScanActivity.start(context)
            }
            Spacer(Modifier.height(16.dp))
            if (prefill == null) {
                Field("Address (IP, name or Tailscale name)", address, KeyboardType.Uri) { address = it }
                Spacer(Modifier.height(10.dp))
            }
            Field("PIN", pin, KeyboardType.NumberPassword) { pin = it.filter(Char::isDigit).take(6) }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button("Pair", enabled = !busy && pin.length == 6 && address.isNotBlank()) {
                    busy = true
                    status = "Pairing…"
                    scope.launch {
                        val (host, port) = parseAddress(address.trim(), prefill?.port ?: Arcis.BRIDGE_PORT)
                        app.hosts.pair(host, port, pin, phoneName(context))
                            .onSuccess { onDone(it) }
                            .onFailure { status = it.message ?: "Pairing failed"; busy = false }
                    }
                }
                Button("Cancel", enabled = true) { onDone(null) }
                Text(status, color = GlassesColors.Dim, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType, onChange: (String) -> Unit) {
    Column {
        Text(label, color = GlassesColors.Dim, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = type),
            textStyle = TextStyle(color = GlassesColors.Text, fontSize = 18.sp),
            cursorBrush = SolidColor(GlassesColors.Accent),
            modifier = Modifier
                .background(GlassesColors.Card, RoundedCornerShape(12.dp))
                .border(1.dp, GlassesColors.CardBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .width(400.dp),
        )
    }
}

@Composable
internal fun Button(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (enabled) Color(0xFF1E3A6E) else GlassesColors.Card, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) { Text(label, color = if (enabled) GlassesColors.Text else GlassesColors.Dim, fontSize = 16.sp) }
}

/** "host", "host:port" or "[v6]:port". */
fun parseAddress(input: String, defaultPort: Int): Pair<String, Int> {
    val v6 = Regex("""^\[(.+)]:(\d+)$""").find(input)
    if (v6 != null) return v6.groupValues[1] to v6.groupValues[2].toInt()
    val parts = input.split(":")
    return if (parts.size == 2 && parts[1].toIntOrNull() != null) parts[0] to parts[1].toInt() else input to defaultPort
}

fun phoneName(context: android.content.Context): String =
    Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL
