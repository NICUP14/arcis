package app.arcis.core.net

/** Where opening a computer stands. */
sealed interface WakeState {
    data object Idle : WakeState

    /** Trying to reach its bridge. */
    data object Connecting : WakeState

    /** The bridge didn't answer: Wake-on-LAN sent at [sinceMs], polling until it does. */
    data class Waking(val sinceMs: Long) : WakeState

    /** The bridge answered; the stream was started. */
    data object Streaming : WakeState

    data class Failed(val message: String) : WakeState
}

sealed interface WakeEvent {
    /** The computer's tile was opened. */
    data object Open : WakeEvent

    /** The bridge answered. */
    data object BridgeUp : WakeEvent

    /** The bridge didn't answer (at [nowMs]). */
    data class BridgeDown(val nowMs: Long) : WakeEvent

    /** Time to try again (at [nowMs]). */
    data class PollDue(val nowMs: Long) : WakeEvent

    data object Cancel : WakeEvent
}

sealed interface WakeEffect {
    data object TryConnect : WakeEffect
    data object SendWake : WakeEffect
    data object LaunchStream : WakeEffect
    data class ScheduleRetry(val delayMs: Long) : WakeEffect
    data class ShowFailure(val message: String) : WakeEffect
}

/**
 * One tap from a sleeping computer to its desktop: try the bridge; if it doesn't answer,
 * send Wake-on-LAN and keep trying every [pollMs] for up to [timeoutMs]; start the stream
 * as soon as it answers. Pure: it returns what to do, the app does it.
 */
class WakeAndConnect(
    private val pollMs: Long = 2_000,
    private val timeoutMs: Long = 60_000,
) {
    var state: WakeState = WakeState.Idle
        private set

    fun on(event: WakeEvent): List<WakeEffect> {
        val s = state
        return when (event) {
            WakeEvent.Open -> {
                state = WakeState.Connecting
                listOf(WakeEffect.TryConnect)
            }
            WakeEvent.Cancel -> {
                state = WakeState.Idle
                emptyList()
            }
            WakeEvent.BridgeUp -> when (s) {
                WakeState.Connecting, is WakeState.Waking -> {
                    state = WakeState.Streaming
                    listOf(WakeEffect.LaunchStream)
                }
                else -> emptyList()
            }
            is WakeEvent.BridgeDown -> when (s) {
                WakeState.Connecting -> {
                    state = WakeState.Waking(event.nowMs)
                    listOf(WakeEffect.SendWake, WakeEffect.ScheduleRetry(pollMs))
                }
                is WakeState.Waking -> retryOrFail(s, event.nowMs)
                else -> emptyList()
            }
            is WakeEvent.PollDue -> when (s) {
                is WakeState.Waking ->
                    if (event.nowMs - s.sinceMs >= timeoutMs) fail() else listOf(WakeEffect.TryConnect)
                else -> emptyList()
            }
        }
    }

    private fun retryOrFail(s: WakeState.Waking, nowMs: Long): List<WakeEffect> =
        if (nowMs - s.sinceMs >= timeoutMs) fail() else listOf(WakeEffect.ScheduleRetry(pollMs))

    private fun fail(): List<WakeEffect> {
        state = WakeState.Failed(FAILED)
        return listOf(WakeEffect.ShowFailure(FAILED))
    }

    companion object {
        const val FAILED = "didn't wake: check it's plugged in and Wake-on-LAN is on"
    }
}
