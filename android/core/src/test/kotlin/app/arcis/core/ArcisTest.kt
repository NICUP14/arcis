package app.arcis.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ArcisTest {
    @Test
    fun nameIsArcis() {
        assertEquals("Arcis", Arcis.NAME)
    }
}
