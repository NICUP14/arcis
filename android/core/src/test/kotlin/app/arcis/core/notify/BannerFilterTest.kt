package app.arcis.core.notify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerFilterTest {

    private val whatsApp = NotificationFacts(messaging = true, category = "msg")
    private val ctx = BannerContext()

    @Test
    fun messagesAndCallsShow() {
        assertTrue(BannerFilter.show(whatsApp, ctx))
        assertTrue(BannerFilter.show(NotificationFacts(category = "call"), ctx))
        assertTrue(BannerFilter.show(NotificationFacts(highImportance = true), ctx))
    }

    @Test
    fun noiseDoesNotShow() {
        assertFalse(BannerFilter.show(whatsApp.copy(ongoing = true), ctx)) // music, downloads, calls in progress
        assertFalse(BannerFilter.show(whatsApp.copy(groupSummary = true), ctx))
        assertFalse(BannerFilter.show(whatsApp.copy(silent = true), ctx))
        assertFalse(BannerFilter.show(whatsApp.copy(own = true), ctx))
        assertFalse(BannerFilter.show(whatsApp.copy(hasTitleOrText = false), ctx))
        assertFalse(BannerFilter.show(NotificationFacts(category = "promo"), ctx)) // not a message
    }

    @Test
    fun settingsAndGlasses() {
        assertFalse(BannerFilter.show(whatsApp, ctx.copy(enabled = false)))
        assertFalse(BannerFilter.show(whatsApp, ctx.copy(glassesConnected = false)))
        assertFalse(BannerFilter.show(whatsApp, ctx.copy(onlyWhileStreaming = true, streaming = false)))
        assertTrue(BannerFilter.show(whatsApp, ctx.copy(onlyWhileStreaming = true, streaming = true)))
    }
}
