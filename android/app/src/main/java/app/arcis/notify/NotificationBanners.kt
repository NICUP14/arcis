package app.arcis.notify

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import app.arcis.ArcisApp
import app.arcis.a11y.ArcisAccessibilityService
import app.arcis.core.input.Gesture
import app.arcis.core.notify.BannerContext
import app.arcis.core.notify.BannerFilter
import app.arcis.core.notify.NotificationFacts
import kotlin.math.abs

/** A banner on the glasses: app icon, title and one line of text. */
data class Banner(
    val app: String,
    val title: String,
    val text: String,
    val icon: androidx.compose.ui.graphics.ImageBitmap?,
)

/**
 * Message banners on the glasses, so messages can be read without leaving a movie.
 * Shown for 4 s at the top; while one is up, a two-finger tap opens it on the glasses and
 * a swipe up dismisses it. Nothing is ever sent to the computer.
 */
class NotificationBanners(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var current: PendingIntent? = null
    private var shownAt = 0L
    private var swipeY = 0f
    private var swipeX = 0f
    private val hide = Runnable { dismiss() }

    /** True while a banner is up. */
    val visible: Boolean get() = current != null || bannerOnly

    /** A banner without an action is still dismissable. */
    private var bannerOnly = false

    /** Called by the notification listener for every posted notification. */
    fun onPosted(sbn: StatusBarNotification, ranking: NotificationListenerService.Ranking?) {
        val app = ArcisApp.instance
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            ?.toString().orEmpty()
        val facts = NotificationFacts(
            ongoing = sbn.isOngoing,
            groupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            silent = ranking != null && ranking.importance < NotificationManager.IMPORTANCE_DEFAULT,
            category = n.category,
            messaging = extras.getString(Notification.EXTRA_TEMPLATE) == Notification.MessagingStyle::class.java.name,
            highImportance = ranking != null && ranking.importance >= NotificationManager.IMPORTANCE_HIGH,
            hasTitleOrText = title.isNotBlank() || text.isNotBlank(),
            own = sbn.packageName == context.packageName,
        )
        val prefs = app.controllerPrefs
        val situation = BannerContext(
            enabled = prefs.banners.value,
            onlyWhileStreaming = prefs.bannersOnlyStreaming.value,
            glassesConnected = app.displays.screen.value != null,
            streaming = app.hostTarget.streamInFront || app.media.state.value.available,
        )
        if (!BannerFilter.show(facts, situation)) return
        val label = runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        val icon = runCatching {
            context.packageManager.getApplicationIcon(sbn.packageName).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
        }.getOrNull()
        handler.post { show(Banner(label, title.ifBlank { label }, text, icon), n.contentIntent) }
    }

    private fun show(banner: Banner, action: PendingIntent?) {
        val overlay = ArcisAccessibilityService.instance?.uiOverlay ?: return
        overlay.showBanner(banner)
        current = action
        bannerOnly = action == null
        shownAt = System.currentTimeMillis()
        swipeX = 0f
        swipeY = 0f
        handler.removeCallbacks(hide)
        handler.postDelayed(hide, SHOW_MS)
    }

    fun dismiss() {
        handler.removeCallbacks(hide)
        current = null
        bannerOnly = false
        ArcisAccessibilityService.instance?.uiOverlay?.hideBanner()
    }

    /**
     * While a banner is up: two-finger tap opens it, a swipe up dismisses it. Pointer
     * movement still goes through (only watched). Returns true if the gesture was used.
     */
    fun handle(g: Gesture): Boolean {
        if (!visible) return false
        when (g) {
            Gesture.Back -> {
                open()
                return true
            }
            is Gesture.PointerMove -> {
                swipeX += g.dx
                swipeY += g.dy
            }
            Gesture.MoveEnd -> {
                val up = -swipeY
                if (up > DISMISS_PX && up > 2 * abs(swipeX)) dismiss()
                swipeX = 0f
                swipeY = 0f
            }
            else -> Unit
        }
        return false
    }

    private fun open() {
        val action = current
        dismiss()
        val screen = ArcisApp.instance.displays.screen.value ?: return
        if (action == null) return
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(screen.displayId)
        runCatching { action.send(context, 0, null, null, null, null, options.toBundle()) }
            .onFailure { Log.w(TAG, "can't open the notification", it) }
    }

    private companion object {
        const val TAG = "ArcisBanners"
        const val SHOW_MS = 4_000L
        const val DISMISS_PX = 150f
        const val ICON_PX = 96
    }
}
