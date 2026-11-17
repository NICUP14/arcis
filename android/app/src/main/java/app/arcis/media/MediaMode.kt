package app.arcis.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import app.arcis.a11y.ArcisAccessibilityService
import app.arcis.core.input.Gesture
import app.arcis.core.media.MediaAction
import app.arcis.core.media.MediaGestureMap
import app.arcis.host.HostManager
import app.arcis.host.HostTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/** Media mode as the phone shows it. */
data class MediaState(
    /** A video/player is in front (a phone app with a media session, or a computer stream). */
    val available: Boolean = false,
    /** Gestures control playback instead of the pointer. */
    val active: Boolean = false,
    /** The computer's stream is in front (media keys go to the computer). */
    val computer: Boolean = false,
)

/**
 * Media mode: while a phone app on the glasses is playing media (it has an active media
 * session) the trackpad controls playback without aiming, see [MediaGestureMap]. On by
 * itself for phone apps; long-press (or the chip) leaves it until another app comes to the
 * front. For a computer's stream it is off by default and turned on with the chip; the
 * bridge then gets media keys.
 */
class MediaMode(
    private val context: Context,
    private val hosts: HostManager,
    private val hostTarget: HostTarget,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val listener = ComponentName(context, ArcisNotificationListener::class.java)
    private val map = MediaGestureMap()

    private val sessions = MutableStateFlow<List<MediaController>>(emptyList())
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        sessions.value = list.orEmpty()
    }

    /** Package the user turned media mode off for (until another app is in front). */
    private val exitedFor = MutableStateFlow<String?>(null)
    private val computerOn = MutableStateFlow(false)

    private val _state = MutableStateFlow(MediaState())
    val state: StateFlow<MediaState> = _state.asStateFlow()

    fun start() {
        scope.launch {
            ArcisNotificationListener.connected.collect { on -> if (on) watchSessions() else sessions.value = emptyList() }
        }
        scope.launch {
            combine(
                ArcisAccessibilityService.foregroundPackage,
                ArcisAccessibilityService.foregroundActivity,
                sessions,
                exitedFor,
                combine(computerOn, hosts.connection) { on, conn -> on to (conn is app.arcis.host.HostConnection.Connected) },
            ) { pkg, activity, _, exited, (pcOn, connected) ->
                if (exited != null && exited != pkg) exitedFor.value = null
                val stream = connected && HostTarget.isMoonlightStream(pkg, activity)
                if (!stream && computerOn.value) computerOn.value = false
                when {
                    stream -> MediaState(available = true, active = pcOn, computer = true)
                    controllerFor(pkg) != null -> MediaState(available = true, active = exited != pkg)
                    else -> MediaState()
                }
            }.collect { s ->
                if (s != _state.value) {
                    Log.d(TAG, "media mode $s")
                    // No pointer to aim with while a phone app's video is controlled.
                    if (!s.computer) ArcisAccessibilityService.instance?.setCursorVisible(!s.active)
                }
                _state.value = s
            }
        }
    }

    private fun watchSessions() {
        runCatching {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listener)
            sessions.value = sessionManager.getActiveSessions(listener)
        }.onFailure { Log.w(TAG, "media sessions unavailable", it) }
    }

    /** The front app's session, if it has one that is playing or paused. */
    private fun controllerFor(pkg: String?): MediaController? = sessions.value.firstOrNull { c ->
        c.packageName == pkg && c.playbackState?.state.let { it != null && it != PlaybackState.STATE_NONE && it != PlaybackState.STATE_STOPPED }
    }

    /** The phone's "▶ Media" chip: turn media mode on or off for what's in front. */
    fun toggle() {
        val s = _state.value
        if (!s.available) return
        if (s.computer) {
            computerOn.value = !computerOn.value
        } else {
            exitedFor.value = if (s.active) ArcisAccessibilityService.foregroundPackage.value else null
        }
        flash(if (!s.active) "Media mode" else "Pointer")
    }

    /** Performs [g] as a media action if media mode is on; false lets it through. */
    fun handle(g: Gesture): Boolean {
        val s = _state.value
        if (!s.active) return false
        val action = map.map(g) ?: return false
        if (s.computer) toComputer(action) else toPhoneApp(action)
        return true
    }

    private fun toPhoneApp(action: MediaAction) {
        val pkg = ArcisAccessibilityService.foregroundPackage.value
        val c = controllerFor(pkg)
        when (action) {
            MediaAction.PlayPause -> {
                val playing = c?.playbackState?.state == PlaybackState.STATE_PLAYING
                when {
                    c == null -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    playing -> c.transportControls.pause()
                    else -> c.transportControls.play()
                }
                flash(if (playing) "❚❚" else "▶")
            }
            is MediaAction.Seek -> {
                val state = c?.playbackState
                if (c != null && state != null && state.actions and PlaybackState.ACTION_SEEK_TO != 0L) {
                    c.transportControls.seekTo(max(0L, position(state) + action.seconds * 1000L))
                } else if (action.seconds > 0) {
                    c?.transportControls?.fastForward() ?: mediaKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
                } else {
                    c?.transportControls?.rewind() ?: mediaKey(KeyEvent.KEYCODE_MEDIA_REWIND)
                }
                flash(seekLabel(action.seconds))
            }
            is MediaAction.Volume -> {
                val direction = if (action.steps > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                repeat(abs(action.steps)) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0) }
                val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                flash("Volume $now / ${audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}")
            }
            MediaAction.Back -> ArcisAccessibilityService.instance?.back()
            MediaAction.Exit -> toggle()
            MediaAction.Nothing -> Unit
        }
    }

    private fun toComputer(action: MediaAction) {
        when (action) {
            MediaAction.PlayPause -> {
                hosts.key("MediaPlayPause")
                flash("▶ ❚❚")
            }
            // Video players on computers seek with the arrow keys (5-10 s per press).
            is MediaAction.Seek -> {
                repeat(max(1, abs(action.seconds) / 10)) { hosts.key(if (action.seconds > 0) "Right" else "Left") }
                flash(seekLabel(action.seconds))
            }
            is MediaAction.Volume -> {
                repeat(abs(action.steps)) { hosts.key(if (action.steps > 0) "VolumeUp" else "VolumeDown") }
                flash(if (action.steps > 0) "Volume +" else "Volume −")
            }
            MediaAction.Back -> hostTarget.handle(Gesture.Back)
            MediaAction.Exit -> toggle()
            MediaAction.Nothing -> Unit
        }
    }

    /** Current position, extrapolated while playing. */
    private fun position(s: PlaybackState): Long {
        if (s.state != PlaybackState.STATE_PLAYING) return s.position
        val elapsed = SystemClock.elapsedRealtime() - s.lastPositionUpdateTime
        return s.position + (elapsed * s.playbackSpeed).toLong()
    }

    private fun mediaKey(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun seekLabel(seconds: Int) = if (seconds > 0) "+$seconds s ⏩" else "⏪ ${seconds} s"

    private fun flash(text: String) = ArcisAccessibilityService.instance?.uiOverlay?.flash(text, FLASH_MS)

    private companion object {
        const val TAG = "ArcisMedia"
        const val FLASH_MS = 900L
    }
}
