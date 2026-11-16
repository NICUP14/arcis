package app.arcis.core.hid

import app.arcis.core.text.TextEdit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HidKeysTest {

    @Test
    fun lettersAndShift() {
        assertEquals(HidKeys.Stroke(0x04), HidKeys.strokeFor('a'))
        assertEquals(HidKeys.Stroke(0x1D, HidKeys.MOD_SHIFT), HidKeys.strokeFor('Z'))
    }

    @Test
    fun digitsAndSymbols() {
        assertEquals(HidKeys.Stroke(0x1E), HidKeys.strokeFor('1'))
        assertEquals(HidKeys.Stroke(0x27), HidKeys.strokeFor('0'))
        assertEquals(HidKeys.Stroke(0x1E, HidKeys.MOD_SHIFT), HidKeys.strokeFor('!'))
        assertEquals(HidKeys.Stroke(0x38, HidKeys.MOD_SHIFT), HidKeys.strokeFor('?'))
        assertEquals(HidKeys.Stroke(0x37), HidKeys.strokeFor('.'))
        assertEquals(HidKeys.Stroke(0x36), HidKeys.strokeFor(','))
        assertEquals(HidKeys.Stroke(HidKeys.ENTER), HidKeys.strokeFor('\n'))
        assertNull(HidKeys.strokeFor('é'))
    }

    @Test
    fun keyboardReportLayout() {
        assertArrayEquals(byteArrayOf(2, 0, 0x04, 0, 0, 0, 0, 0), HidKeys.keyboardReport(HidKeys.MOD_SHIFT, 0x04))
    }

    @Test
    fun bigMouseMovesAreSplit() {
        val reports = HidKeys.mouseReports(buttons = 1, dx = 300, dy = -10)
        assertEquals(3, reports.size)
        assertEquals(300, reports.sumOf { it[1].toInt() })
        assertEquals(-10, reports.sumOf { it[2].toInt() })
        assertTrue(reports.all { it[0] == 1.toByte() })
    }

    @Test
    fun zeroMoveStillSendsOneReport() {
        assertEquals(1, HidKeys.mouseReports(buttons = 1, dx = 0, dy = 0).size)
    }

    @Test
    fun textDiffForRelayField() {
        assertEquals(TextEdit(0, "lo"), TextEdit.between("hel", "hello"))
        assertEquals(TextEdit(2, ""), TextEdit.between("hello", "hel"))
        assertEquals(TextEdit(2, "p!"), TextEdit.between("hello", "help!"))
        assertTrue(TextEdit.between("same", "same").isEmpty)
    }
}
