package app.arcis.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoonlightPinTest {

    @Test
    fun findsThePinInMoonlightsDialog() {
        assertEquals("4821", MoonlightPin.find("Pairing\nPlease enter the following PIN on the target PC: 4821"))
        assertEquals("0093", MoonlightPin.find("PIN: 0093\nThis dialog will close when pairing is completed."))
    }

    @Test
    fun ignoresOtherText() {
        assertNull(MoonlightPin.find("Pairing\nPlease wait…"))
        assertNull(MoonlightPin.find("Add PC 192.168.1.20"))
        assertNull(MoonlightPin.find("Bitrate 20000 Kbps")) // 4+ digits, but no PIN
        assertNull(MoonlightPin.find("PIN 123456")) // not 4 digits
    }
}
