package app.arcis.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairLinkTest {

    private val fp = "a".repeat(64)

    @Test
    fun parsesTheBridgesLink() {
        // As Go's url.Values.Encode writes it (sorted keys, "+" for spaces, %2C for commas).
        val link = PairLink.parse("arcis://pair?a=192.168.1.20%2C100.64.1.2&fp=$fp&h=My+PC&p=41990&pin=012345")
        assertEquals(PairLink("My PC", 41990, fp, "012345", listOf("192.168.1.20", "100.64.1.2")), link)
    }

    @Test
    fun rejectsOtherCodes() {
        assertNull(PairLink.parse("https://example.com/pair?p=1"))
        assertNull(PairLink.parse("WIFI:S:home;T:WPA;P:secret;;"))
        assertNull(PairLink.parse("arcis://pair?a=1.2.3.4&fp=$fp&p=41990")) // no PIN
        assertNull(PairLink.parse("arcis://pair?a=1.2.3.4&fp=xyz&p=41990&pin=123456")) // bad fingerprint
        assertNull(PairLink.parse("arcis://pair?fp=$fp&p=41990&pin=123456")) // no address
    }

    @Test
    fun nameFallsBackToTheFirstAddress() {
        assertEquals("10.0.0.2", PairLink.parse("arcis://pair?a=10.0.0.2&fp=$fp&p=41990&pin=123456")?.host)
    }
}
