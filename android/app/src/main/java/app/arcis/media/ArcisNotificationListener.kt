package app.arcis.media

import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notification access. Android only lets a notification listener read which apps are
 * playing media (MediaSessionManager.getActiveSessions), which media mode needs.
 */
class ArcisNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        _connected.value = true
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
