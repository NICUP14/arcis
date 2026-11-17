package app.arcis.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeAndConnectTest {

    private val m = WakeAndConnect(pollMs = 2_000, timeoutMs = 60_000)

    @Test
    fun awakeComputerStreamsRightAway() {
        assertEquals(listOf(WakeEffect.TryConnect), m.on(WakeEvent.Open))
        assertEquals(WakeState.Connecting, m.state)
        assertEquals(listOf(WakeEffect.LaunchStream), m.on(WakeEvent.BridgeUp))
        assertEquals(WakeState.Streaming, m.state)
    }

    @Test
    fun sleepingComputerIsWokenThenStreams() {
        m.on(WakeEvent.Open)
        assertEquals(listOf(WakeEffect.SendWake, WakeEffect.ScheduleRetry(2_000)), m.on(WakeEvent.BridgeDown(1_000)))
        assertEquals(WakeState.Waking(1_000), m.state)
        // Poll, still down, poll again...
        assertEquals(listOf(WakeEffect.TryConnect), m.on(WakeEvent.PollDue(3_000)))
        assertEquals(listOf(WakeEffect.ScheduleRetry(2_000)), m.on(WakeEvent.BridgeDown(3_100)))
        assertEquals(listOf(WakeEffect.TryConnect), m.on(WakeEvent.PollDue(5_100)))
        // ...it's up: stream.
        assertEquals(listOf(WakeEffect.LaunchStream), m.on(WakeEvent.BridgeUp))
        assertEquals(WakeState.Streaming, m.state)
    }

    @Test
    fun wakeIsSentOnlyOnce() {
        m.on(WakeEvent.Open)
        m.on(WakeEvent.BridgeDown(0))
        repeat(5) { i ->
            val effects = m.on(WakeEvent.PollDue(2_000L * (i + 1))) + m.on(WakeEvent.BridgeDown(2_000L * (i + 1)))
            assertTrue(WakeEffect.SendWake !in effects)
        }
    }

    @Test
    fun givesUpAfterAMinute() {
        m.on(WakeEvent.Open)
        m.on(WakeEvent.BridgeDown(0))
        val effects = m.on(WakeEvent.PollDue(60_000))
        assertEquals(listOf(WakeEffect.ShowFailure(WakeAndConnect.FAILED)), effects)
        assertTrue(m.state is WakeState.Failed)
        // A late answer doesn't start a stream nobody waits for any more.
        assertEquals(emptyList<WakeEffect>(), m.on(WakeEvent.BridgeUp))
    }

    @Test
    fun openingAgainStartsOverAndCancelStops() {
        m.on(WakeEvent.Open)
        m.on(WakeEvent.BridgeDown(0))
        m.on(WakeEvent.PollDue(60_000))
        assertEquals(listOf(WakeEffect.TryConnect), m.on(WakeEvent.Open))
        assertEquals(WakeState.Connecting, m.state)
        m.on(WakeEvent.Cancel)
        assertEquals(WakeState.Idle, m.state)
        assertEquals(emptyList<WakeEffect>(), m.on(WakeEvent.PollDue(1)))
    }
}
