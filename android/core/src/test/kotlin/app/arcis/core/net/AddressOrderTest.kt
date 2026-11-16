package app.arcis.core.net

import app.arcis.core.net.AddressOrder.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressOrderTest {

    @Test
    fun classifiesAddresses() {
        assertEquals(Kind.LAN, AddressOrder.kind("192.168.1.20"))
        assertEquals(Kind.LAN, AddressOrder.kind("10.0.0.7"))
        assertEquals(Kind.LAN, AddressOrder.kind("172.20.1.1"))
        assertEquals(Kind.OTHER, AddressOrder.kind("172.32.1.1"))
        assertEquals(Kind.TAILSCALE, AddressOrder.kind("100.64.1.2"))
        assertEquals(Kind.OTHER, AddressOrder.kind("100.20.1.1")) // not the CGNAT range
        assertEquals(Kind.TAILSCALE, AddressOrder.kind("desktop.tail1234.ts.net"))
        assertEquals(Kind.LOOPBACK, AddressOrder.kind("127.0.0.1"))
        assertEquals(Kind.LAN, AddressOrder.kind("msi.local"))
        assertEquals(Kind.OTHER, AddressOrder.kind("my-pc.example.org"))
    }

    @Test
    fun homeFirstTailscaleLast() {
        val ordered = AddressOrder.order(
            known = listOf("100.64.1.2"),
            reported = listOf("100.64.1.2", "192.168.1.20", "my.example.org"),
        )
        assertEquals(listOf("192.168.1.20", "my.example.org", "100.64.1.2"), ordered)
    }

    @Test
    fun keepsLoopbackFirstAndDeduplicates() {
        assertEquals(
            listOf("127.0.0.1", "192.168.1.20", "100.64.1.2"),
            AddressOrder.order(listOf("127.0.0.1", " 192.168.1.20"), listOf("192.168.1.20", "100.64.1.2", "")),
        )
    }

    @Test
    fun remoteAndTimeouts() {
        assertTrue(AddressOrder.isRemote("100.64.1.2"))
        assertFalse(AddressOrder.isRemote("192.168.1.20"))
        assertTrue(AddressOrder.connectTimeoutMs("192.168.1.20") < AddressOrder.connectTimeoutMs("100.64.1.2"))
    }
}
