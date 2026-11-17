package app.arcis.core.switcher

/** What was last in front on the glasses. */
sealed interface Target {
    /** A phone app, by package name. */
    data class PhoneApp(val pkg: String) : Target

    /** A computer's Moonlight stream; [hostId] is the bridge's certificate fingerprint. */
    data class Pc(val hostId: String, val name: String) : Target

    /** The "controlling a Bluetooth device" screen. */
    data class Bluetooth(val address: String) : Target
}

/** [target] was in front until [atMs] (wall clock). */
data class LastTarget(val target: Target, val atMs: Long)

sealed interface ResumeEvent {
    /** The glasses (external screen) were connected. */
    data object GlassesConnected : ResumeEvent

    /**
     * Moonlight's own menus came to the front right after another app was in front (Back
     * or the switcher), not after leaving the stream inside Moonlight. Moonlight ends its
     * stream when another app covers it, so this is where it lands afterwards.
     * [lastStream]: the PC whose stream was last in front, and until when.
     */
    data class MoonlightMenuAfterOtherApp(val lastStream: LastTarget?) : ResumeEvent
}

sealed interface ResumeAction {
    data object None : ResumeAction

    /** Show Arcis Home on the glasses. */
    data object OpenHome : ResumeAction

    /** Show Home, then reopen [target] over it (a PC resumes its stream). */
    data class Reopen(val target: Target) : ResumeAction

    /** Start the PC's stream again (Moonlight's shortcut entry; the host app kept running). */
    data class ResumePc(val pc: Target.Pc) : ResumeAction
}

/** Decides what to bring back, and when. Pure, so it is unit-tested. */
object ResumePolicy {

    /** Glasses back within this time reopen what was in front; later, Home. */
    const val RECONNECT_WINDOW_MS = 10 * 60_000L

    fun decide(last: LastTarget?, nowMs: Long, event: ResumeEvent): ResumeAction {
        fun recent(t: LastTarget?) = t != null && nowMs - t.atMs in 0..RECONNECT_WINDOW_MS
        return when (event) {
            ResumeEvent.GlassesConnected ->
                if (recent(last)) ResumeAction.Reopen(last!!.target) else ResumeAction.OpenHome
            is ResumeEvent.MoonlightMenuAfterOtherApp -> {
                val pc = event.lastStream?.target as? Target.Pc
                if (pc != null && recent(event.lastStream)) ResumeAction.ResumePc(pc) else ResumeAction.None
            }
        }
    }
}
