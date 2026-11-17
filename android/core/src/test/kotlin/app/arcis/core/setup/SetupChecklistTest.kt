package app.arcis.core.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupChecklistTest {

    private val required = setOf(SetupItem.ACCESSIBILITY, SetupItem.MOONLIGHT, SetupItem.BRIDGE)

    @Test
    fun freshPhoneStartsWithAccessibility() {
        val c = SetupChecklist(done = emptySet())
        assertFalse(c.ready)
        assertEquals(SetupItem.ACCESSIBILITY, c.next)
        assertEquals(0 to 7, c.progress) // the PiP tip doesn't count
    }

    @Test
    fun requiredItemsComeBeforeOptionalOnes() {
        val c = SetupChecklist(done = setOf(SetupItem.ACCESSIBILITY, SetupItem.MICROPHONE))
        assertEquals(SetupItem.MOONLIGHT, c.next)
        assertFalse(c.ready)
    }

    @Test
    fun readyOnceTheRequiredItemsAreDone() {
        val c = SetupChecklist(done = required)
        assertTrue(c.ready)
        assertEquals(SetupItem.NOTIFICATIONS, c.next) // optional ones are still offered
    }

    @Test
    fun everythingDone() {
        val c = SetupChecklist(done = SetupItem.entries.toSet())
        assertTrue(c.ready)
        assertNull(c.next)
        assertEquals(7 to 7, c.progress)
        assertEquals(SetupStatus.TIP, c.rows.single { it.item == SetupItem.MOONLIGHT_PIP }.status)
    }

    @Test
    fun hiddenItemsDontCount() {
        val c = SetupChecklist(done = required, shown = SetupItem.entries.toSet() - SetupItem.NOTIFICATIONS)
        assertTrue(c.rows.none { it.item == SetupItem.NOTIFICATIONS })
        assertEquals(SetupItem.MICROPHONE, c.next)
        assertEquals(3 to 6, c.progress)
    }
}
