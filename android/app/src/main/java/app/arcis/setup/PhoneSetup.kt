package app.arcis.setup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.arcis.ArcisApp
import app.arcis.a11y.ArcisAccessibilityService
import app.arcis.core.setup.SetupChecklist
import app.arcis.core.setup.SetupItem
import app.arcis.core.setup.SetupStatus
import app.arcis.host.HostTarget

/** Reads what is set up on this phone, for [SetupChecklist]. */
object PhoneSetup {

    /** Items this build has. */
    var shown: Set<SetupItem> = SetupItem.entries.toSet()

    fun check(context: Context): SetupChecklist {
        val app = ArcisApp.instance
        val done = buildSet {
            if (ArcisAccessibilityService.connected.value) add(SetupItem.ACCESSIBILITY)
            if (app.phoneApps.list().any { HostTarget.isMoonlight(it.packageName) }) add(SetupItem.MOONLIGHT)
            if (app.hosts.store.hosts.value.isNotEmpty()) add(SetupItem.BRIDGE)
            if (granted(context, Manifest.permission.RECORD_AUDIO)) add(SetupItem.MICROPHONE)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                (granted(context, Manifest.permission.BLUETOOTH_CONNECT) && granted(context, Manifest.permission.BLUETOOTH_ADVERTISE))
            ) {
                add(SetupItem.BLUETOOTH)
            }
            if (context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)) {
                add(SetupItem.BATTERY)
            }
            if (NotificationAccess.enabled(context)) add(SetupItem.NOTIFICATIONS)
        }
        return SetupChecklist(done, shown)
    }

    private fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

/** Notification access (the listener service), for banners and media controls. */
object NotificationAccess {
    fun enabled(context: Context): Boolean =
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}

private data class Copy(val title: String, val why: String, val action: String)

private fun copy(item: SetupItem): Copy = when (item) {
    SetupItem.ACCESSIBILITY -> Copy(
        "Accessibility service",
        "Lets the phone control apps on the glasses. Turn on \"Arcis controller\". If Android says " +
            "it's restricted: App info › ⋮ › Allow restricted settings, then try again.",
        "Open",
    )
    SetupItem.MOONLIGHT -> Copy("Moonlight", "Shows your computer's screen on the glasses.", "Get it")
    SetupItem.BRIDGE -> Copy(
        "A computer with the Arcis Bridge",
        "On the computer: Arcis Bridge in the tray › Pair a phone…, then scan its code.",
        "Scan code",
    )
    SetupItem.NOTIFICATIONS -> Copy(
        "Notification access",
        "Message banners on the glasses, and media mode: tap to play / pause and swipe to seek videos.",
        "Open",
    )
    SetupItem.MICROPHONE -> Copy("Microphone", "Speech to text (hold the mic button), and the phone as a computer mic.", "Allow")
    SetupItem.BLUETOOTH -> Copy("Bluetooth", "Control another device as its mouse and keyboard.", "Allow")
    SetupItem.BATTERY -> Copy(
        "No battery limits",
        "So Android doesn't pause Arcis while you watch with the screen off.",
        "Allow",
    )
    SetupItem.MOONLIGHT_PIP -> Copy(
        "Tip: Moonlight picture-in-picture",
        "Moonlight › Settings › Picture-in-picture can keep the computer's stream alive while " +
            "another app is in front (otherwise Arcis restarts it when you come back).",
        "Moonlight",
    )
}

/**
 * The phone's setup checklist: one row per item with its status, why it's needed and a
 * button to the exact place to fix it. [onDone] (enabled once the required items are
 * done) and [onLater] close it.
 */
@Composable
fun PhoneSetupScreen(
    checklist: SetupChecklist,
    onAction: (SetupItem) -> Unit,
    onDone: () -> Unit,
    onLater: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text("Set up Arcis", color = Color.White, fontSize = 26.sp)
            val (doneCount, total) = checklist.progress
            Text("$doneCount of $total done · items marked Required are needed", color = Dim, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            for (row in checklist.rows) {
                val c = copy(row.item)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .background(Card, RoundedCornerShape(14.dp))
                        .border(1.dp, if (row.item == checklist.next) Accent else Border, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when (row.status) {
                            SetupStatus.DONE -> "✓"
                            SetupStatus.TODO -> "○"
                            SetupStatus.TIP -> "i"
                        },
                        color = when (row.status) {
                            SetupStatus.DONE -> Ok
                            SetupStatus.TODO -> if (row.item.required) Warn else Dim
                            SetupStatus.TIP -> Accent
                        },
                        fontSize = 20.sp,
                        modifier = Modifier.width(30.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(c.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        if (row.item.required && row.status == SetupStatus.TODO) {
                            Text("Required", color = Warn, fontSize = 12.sp)
                        }
                        Text(c.why, color = Dim, fontSize = 13.sp)
                    }
                    if (row.status != SetupStatus.DONE) {
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = { onAction(row.item) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A6E)),
                        ) { Text(c.action, color = Color.White) }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onDone,
                    enabled = checklist.ready,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A6E)),
                ) { Text("Done", color = Color.White) }
                TextButton(onClick = onLater) { Text("Later", color = Dim) }
            }
        }
    }
}

private val Dim = Color(0xFF8A90A0)
private val Card = Color(0xFF161922)
private val Border = Color(0xFF262A33)
private val Accent = Color(0xFF6EA8FF)
private val Ok = Color(0xFF4CD98A)
private val Warn = Color(0xFFFFB35A)
