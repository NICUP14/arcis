package app.arcis.core.switcher

import app.arcis.core.input.SwitchDirection.NEXT
import app.arcis.core.input.SwitchDirection.PREVIOUS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentsTest {

    private val a = RecentItem("a", "A")
    private val b = RecentItem("b", "B")
    private val c = RecentItem("c", "C")

    private fun ids(r: Recents) = r.list.map { it.id }

    private fun recentsWith(vararg opened: RecentItem): Recents {
        val r = Recents(quickSwitchWindowMs = 1_000)
        var t = 0L
        opened.forEach { r.onForeground(it, t); t += 10_000 }
        return r
    }

    @Test
    fun mostRecentFirst() {
        val r = recentsWith(a, b, c)
        assertEquals(listOf("c", "b", "a"), ids(r))
        r.onForeground(a, 100_000)
        assertEquals(listOf("a", "c", "b"), ids(r))
    }

    @Test
    fun previousGoesToLastUsedApp() {
        val r = recentsWith(a, b, c)
        assertEquals(b, r.step(PREVIOUS, 100_000))
    }

    @Test
    fun quickSwitchesWalkWithoutReordering() {
        val r = recentsWith(a, b, c)
        assertEquals(b, r.step(PREVIOUS, 100_000))
        r.onForeground(b, 100_200) // app comes to front during the walk
        assertEquals(a, r.step(PREVIOUS, 100_500))
        assertEquals(listOf("c", "b", "a"), ids(r)) // order untouched while walking
        assertEquals(b, r.step(NEXT, 100_800))
    }

    @Test
    fun pauseCommitsTheAppYouLandedOn() {
        val r = recentsWith(a, b, c)
        r.step(PREVIOUS, 100_000)
        r.step(PREVIOUS, 100_300) // now on a
        // After a pause, the next switch first commits: a becomes most recent.
        assertEquals(c, r.step(PREVIOUS, 200_000))
        assertEquals(listOf("a", "c", "b"), ids(r))
    }

    @Test
    fun endsOfListReturnNull() {
        val r = recentsWith(a, b)
        assertNull(r.step(NEXT, 100_000))
        assertEquals(a, r.step(PREVIOUS, 100_100))
        assertNull(r.step(PREVIOUS, 100_200))
    }

    @Test
    fun selectMovesToFront() {
        val r = recentsWith(a, b, c)
        assertEquals(a, r.select("a"))
        assertEquals(listOf("a", "c", "b"), ids(r))
        assertNull(r.select("missing"))
    }

    @Test
    fun removeKeepsPositionValid() {
        val r = recentsWith(a, b, c)
        r.step(PREVIOUS, 100_000) // on b (index 1)
        r.remove("c")
        assertEquals(listOf("b", "a"), ids(r))
        assertEquals(b, r.current)
        r.remove("b")
        r.remove("a")
        assertNull(r.current)
    }

    @Test
    fun capacityDropsOldest() {
        val r = Recents(capacity = 2)
        r.onForeground(a, 0); r.onForeground(b, 10_000); r.onForeground(c, 20_000)
        assertEquals(listOf("c", "b"), ids(r))
    }
}
