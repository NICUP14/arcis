package app.arcis.core.switcher

import app.arcis.core.switcher.ResumePolicy.RECONNECT_WINDOW_MS
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumePolicyTest {

    private val pc = Target.Pc("fp", "MSI")
    private val netflix = Target.PhoneApp("com.netflix.mediaclient")
    private val now = 1_000_000_000L

    @Test
    fun glassesBackSoonReopenWhatWasInFront() {
        assertEquals(ResumeAction.Reopen(pc), ResumePolicy.decide(LastTarget(pc, now - 60_000), now, ResumeEvent.GlassesConnected))
        assertEquals(
            ResumeAction.Reopen(netflix),
            ResumePolicy.decide(LastTarget(netflix, now - RECONNECT_WINDOW_MS), now, ResumeEvent.GlassesConnected),
        )
    }

    @Test
    fun glassesBackLaterOrFirstTimeGoHome() {
        assertEquals(
            ResumeAction.OpenHome,
            ResumePolicy.decide(LastTarget(pc, now - RECONNECT_WINDOW_MS - 1), now, ResumeEvent.GlassesConnected),
        )
        assertEquals(ResumeAction.OpenHome, ResumePolicy.decide(null, now, ResumeEvent.GlassesConnected))
        // A clock that went backwards isn't "recent".
        assertEquals(ResumeAction.OpenHome, ResumePolicy.decide(LastTarget(pc, now + 5_000), now, ResumeEvent.GlassesConnected))
    }

    @Test
    fun moonlightMenusAfterAnotherAppResumeTheStream() {
        // Netflix was in front last, but the PC stream was before it.
        val event = ResumeEvent.MoonlightMenuAfterOtherApp(LastTarget(pc, now - 30_000))
        assertEquals(ResumeAction.ResumePc(pc), ResumePolicy.decide(LastTarget(netflix, now - 1_000), now, event))
    }

    @Test
    fun moonlightMenusOtherwiseStay() {
        // No PC stream before.
        assertEquals(
            ResumeAction.None,
            ResumePolicy.decide(LastTarget(netflix, now - 1_000), now, ResumeEvent.MoonlightMenuAfterOtherApp(null)),
        )
        // The stream was too long ago.
        assertEquals(
            ResumeAction.None,
            ResumePolicy.decide(
                LastTarget(netflix, now - 1_000), now,
                ResumeEvent.MoonlightMenuAfterOtherApp(LastTarget(pc, now - RECONNECT_WINDOW_MS - 1)),
            ),
        )
    }
}
