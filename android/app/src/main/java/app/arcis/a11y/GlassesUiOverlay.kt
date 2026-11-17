package app.arcis.a11y

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Display
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.arcis.glasses.GlassesColors
import app.arcis.ArcisApp
import app.arcis.status.StatusInfo
import app.arcis.notify.Banner
import android.text.format.DateFormat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** A card in the switcher. */
data class SwitcherCard(val id: String, val label: String, val icon: ImageBitmap?)

/**
 * Non-touchable Compose overlay on the external screen, drawn above every app:
 * the card switcher (Recents) and a short "you are now in X" label.
 */
class GlassesUiOverlay(service: AccessibilityService, display: Display) {

    private val context = service
        .createDisplayContext(display)
        .createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val owner = OverlayLifecycleOwner()

    private var cards by mutableStateOf<List<SwitcherCard>>(emptyList())
    private var selected by mutableStateOf(0)
    private var switcherVisible by mutableStateOf(false)
    private var flashText by mutableStateOf<String?>(null)
    private var dictationText by mutableStateOf<String?>(null)
    private var flashToken = 0
    private var banner by mutableStateOf<Banner?>(null)

    /** A message banner at the top (see NotificationBanners). */
    fun showBanner(b: Banner) {
        banner = b
    }

    fun hideBanner() {
        banner = null
    }

    private val view = ComposeView(context).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setContent { OverlayContent() }
    }
    private var attached = false

    fun show() {
        if (attached) return
        owner.start()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        windowManager.addView(view, params)
        attached = true
    }

    fun remove() {
        if (!attached) return
        runCatching { windowManager.removeView(view) }
        owner.destroy()
        attached = false
    }

    fun showSwitcher(newCards: List<SwitcherCard>, selectedIndex: Int) {
        cards = newCards
        selected = selectedIndex.coerceIn(0, (newCards.size - 1).coerceAtLeast(0))
        switcherVisible = true
    }

    /** Moves the selection; returns the newly selected index. */
    fun moveSelection(steps: Int): Int {
        selected = (selected + steps).coerceIn(0, (cards.size - 1).coerceAtLeast(0))
        return selected
    }

    fun selectedCard(): SwitcherCard? = cards.getOrNull(selected)

    fun hideSwitcher() {
        switcherVisible = false
    }

    /** Live speech-to-text strip at the bottom of the screen. */
    fun showDictation(text: String) {
        dictationText = text
    }

    fun hideDictation() {
        dictationText = null
    }

    /** Shows [text] centred for a moment (e.g. the name of the app you switched to). */
    fun flash(text: String, durationMs: Long = FLASH_MS) {
        val token = ++flashToken
        flashText = text
        view.postDelayed({ if (token == flashToken) flashText = null }, durationMs)
    }

    @Composable
    private fun OverlayContent() {
        Box(Modifier.fillMaxSize()) {
            val status by ArcisApp.instance.status.info.collectAsState()
            AnimatedVisibility(
                status.visible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 18.dp),
            ) {
                StatusBar(status, micLive = dictationText != null)
            }
            val shown = banner
            AnimatedVisibility(
                shown != null && !switcherVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
            ) {
                shown?.let { BannerCard(it) }
            }
            AnimatedVisibility(switcherVisible, enter = fadeIn(), exit = fadeOut()) {
                Switcher()
            }
            val spoken = dictationText
            AnimatedVisibility(
                spoken != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0xF010121A), RoundedCornerShape(24.dp))
                        .border(1.dp, GlassesColors.Accent, RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                ) {
                    Box(Modifier.size(12.dp).background(Color(0xFFFF5A5A), RoundedCornerShape(6.dp)))
                    Spacer(Modifier.width(14.dp))
                    Text(
                        spoken ?: "",
                        color = GlassesColors.Text,
                        fontSize = 22.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 900.dp),
                    )
                }
            }
            val text = flashText
            AnimatedVisibility(
                text != null && !switcherVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Text(
                    text ?: "",
                    color = GlassesColors.Text,
                    fontSize = 28.sp,
                    modifier = Modifier
                        .background(Color(0xE610121A), RoundedCornerShape(20.dp))
                        .padding(horizontal = 28.dp, vertical = 14.dp),
                )
            }
        }
    }

    @Composable
    private fun BannerCard(b: Banner) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .background(Color(0xF2161922), RoundedCornerShape(18.dp))
                .border(1.dp, GlassesColors.CardBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            b.icon?.let {
                Image(it, contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(14.dp))
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.title, color = GlassesColors.Text, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(10.dp))
                    Text(b.app, color = GlassesColors.Dim, fontSize = 13.sp, maxLines = 1)
                }
                if (b.text.isNotBlank()) {
                    Text(b.text, color = GlassesColors.Dim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("Two-finger tap: open · swipe up: dismiss", color = Color(0xFF5A6070), fontSize = 11.sp)
            }
        }
    }

    /** Time, phone battery, connected computer (green dot), mic live (red dot), media mode. */
    @Composable
    private fun StatusBar(status: StatusInfo, micLive: Boolean) {
        val is24h = DateFormat.is24HourFormat(context)
        var now by remember { mutableStateOf(LocalTime.now()) }
        LaunchedEffect(Unit) {
            while (true) {
                now = LocalTime.now()
                delay((60 - now.second) * 1000L + 50) // next minute
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .background(Color(0xB310121A), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            if (status.media) StatusText("▶ Media", GlassesColors.Accent)
            if (micLive) Dot(Color(0xFFFF5A5A))
            status.computer?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(Color(0xFF4CD98A))
                    Spacer(Modifier.width(6.dp))
                    StatusText(it, GlassesColors.Text)
                }
            }
            status.batteryPct?.let { pct ->
                val color = if (pct <= 15 && !status.charging) Color(0xFFFF5A5A) else GlassesColors.Text
                StatusText((if (status.charging) "⚡" else "") + "$pct%", color)
            }
            StatusText(now.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm")), GlassesColors.Text)
        }
    }

    @Composable
    private fun StatusText(text: String, color: Color) = Text(text, color = color, fontSize = 15.sp, maxLines = 1)

    @Composable
    private fun Dot(color: Color) = Box(Modifier.size(9.dp).background(color, RoundedCornerShape(5.dp)))

    @Composable
    private fun Switcher() {
        Box(
            Modifier.fillMaxSize().background(Color(0xD9000000)),
            contentAlignment = Alignment.Center,
        ) {
            if (cards.isEmpty()) {
                Text("No recent apps", color = GlassesColors.Dim, fontSize = 22.sp)
                return@Box
            }
            // Keep the selected card centred: show a window of cards around it.
            val first = (selected - 2).coerceAtLeast(0)
            val visible = cards.subList(first, (first + 5).coerceAtMost(cards.size))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                visible.forEachIndexed { i, card -> Card(card, first + i == selected) }
            }
        }
    }

    @Composable
    private fun Card(card: SwitcherCard, isSelected: Boolean) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(if (isSelected) 1.12f else 0.92f)
                .width(220.dp)
                .height(200.dp)
                .background(GlassesColors.Card, RoundedCornerShape(22.dp))
                .border(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) GlassesColors.Accent else GlassesColors.CardBorder,
                    RoundedCornerShape(22.dp),
                )
                .padding(16.dp),
        ) {
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                card.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(84.dp)) }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                card.label,
                color = if (isSelected) GlassesColors.Text else GlassesColors.Dim,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    private companion object {
        const val FLASH_MS = 1_200L
    }
}
