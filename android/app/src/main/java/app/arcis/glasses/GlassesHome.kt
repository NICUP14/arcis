package app.arcis.glasses

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.arcis.ArcisApp
import app.arcis.apps.PhoneApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    val CardBorder = Color(0xFF262A33)
}

/**
 * Arcis Home on the external screen: status bar, search, favourites and all phone apps.
 * Click a tile to open the app full screen on this display; long-press (right click)
 * toggles it as a favourite.
 */
@Composable
fun GlassesHome(onOpen: (PhoneApp) -> Unit, onBluetooth: () -> Unit) {
    val app = ArcisApp.instance
    val apps by produceState(initialValue = emptyList<PhoneApp>()) {
        value = withContext(Dispatchers.IO) { app.phoneApps.list() }
    }
    val favorites by app.appPrefs.favorites.collectAsState()
    var query by remember { mutableStateOf("") }

    val favoriteApps = favorites.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
    val shown = if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassesColors.Background)
            .padding(horizontal = 48.dp, vertical = 24.dp),
    ) {
        StatusBar()
        Spacer(Modifier.height(16.dp))
        SearchField(query) { query = it }
        Spacer(Modifier.height(20.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(132.dp),
            contentPadding = PaddingValues(bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (query.isBlank() && favoriteApps.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Favourites") }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(favoriteApps, key = { it.packageName }) { a ->
                            AppTile(a, favorite = true, onOpen = { onOpen(a) }) {
                                app.appPrefs.toggleFavorite(a.packageName)
                            }
                        }
                    }
                }
            }
            if (query.isBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Control another device") }
                item { ActionTile("Bluetooth", "ᛒ", onBluetooth) }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(if (query.isBlank()) "Phone apps" else "Results")
            }
            items(shown, key = { it.packageName }) { a ->
                AppTile(a, favorite = a.packageName in favorites, onOpen = { onOpen(a) }) {
                    app.appPrefs.toggleFavorite(a.packageName)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = GlassesColors.Dim,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(app: PhoneApp, favorite: Boolean, onOpen: () -> Unit, onToggleFavorite: () -> Unit) {
    val sizePx = with(LocalDensity.current) { 56.dp.roundToPx() }
    val icon by produceState(initialValue = null as androidx.compose.ui.graphics.ImageBitmap?, app.packageName) {
        value = withContext(Dispatchers.IO) {
            ArcisApp.instance.phoneApps.icon(app, sizePx)?.asImageBitmap()
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(132.dp)
            .background(GlassesColors.Card, RoundedCornerShape(16.dp))
            .border(1.dp, if (favorite) GlassesColors.Accent else GlassesColors.CardBorder, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onToggleFavorite)
            .padding(vertical = 14.dp, horizontal = 8.dp),
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(56.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            app.label,
            color = GlassesColors.Text,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ActionTile(label: String, glyph: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(132.dp)
            .background(GlassesColors.Card, RoundedCornerShape(16.dp))
            .border(1.dp, GlassesColors.CardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
    ) {
        Box(
            Modifier.size(56.dp).background(Color(0xFF1E3A6E), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(glyph, color = GlassesColors.Text, fontSize = 28.sp) }
        Spacer(Modifier.height(8.dp))
        Text(label, color = GlassesColors.Text, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    // The first click on the screen moves window focus here; a keyboard request made in the
    // same instant can get lost, so ask again once the field is focused.
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) {
            delay(150)
            keyboard?.show()
        }
    }
    // The whole pill is the text field, so a click anywhere on it starts typing.
    BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = GlassesColors.Text, fontSize = 16.sp),
        cursorBrush = SolidColor(GlassesColors.Accent),
        modifier = Modifier
            .fillMaxWidth(0.5f)
            .onFocusChanged { focused = it.isFocused }
            .background(GlassesColors.Card, RoundedCornerShape(24.dp))
            .border(1.dp, GlassesColors.CardBorder, RoundedCornerShape(24.dp)),
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                if (query.isEmpty()) Text("Search apps", color = GlassesColors.Dim, fontSize = 16.sp)
                inner()
            }
        },
    )
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Arcis", color = GlassesColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(16.dp))
            Text(time, color = GlassesColors.Dim, fontSize = 18.sp)
        }
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
