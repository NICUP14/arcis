package app.arcis.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.arcis.ArcisApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notification access: message banners on the glasses, and media mode (Android only lets
 * a notification listener read which apps are playing media). Notification content stays
 * on the phone; it is never sent to a computer.
 */
class ArcisNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        _connected.value = true
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val ranking = rankingMap?.let { map -> Ranking().takeIf { map.getRanking(sbn.key, it) } }
        runCatching { ArcisApp.instance.banners.onPosted(sbn, ranking) }
    }

    override fun onListenerDisconnected() {
        _connected.value = false
    }

    companion object {
        private val _connected = MutableStateFlow(false)

        /** True while Android has the listener bound (notification access is on). */
        val connected: StateFlow<Boolean> = _connected.asStateFlow()
    }
}
