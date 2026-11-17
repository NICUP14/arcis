package app.arcis.core.notify

/** What matters about a posted notification for deciding whether to show a banner. */
data class NotificationFacts(
    val ongoing: Boolean = false,
    val groupSummary: Boolean = false,
    /** Below Android's default importance (silent / minimized). */
    val silent: Boolean = false,
    /** Android's category, e.g. "msg", "call" (Notification.CATEGORY_*). */
    val category: String? = null,
    /** Uses the messaging style (chat apps: WhatsApp, Signal, Messages…). */
    val messaging: Boolean = false,
    /** High importance (would pop up as heads-up on the phone). */
    val highImportance: Boolean = false,
    val hasTitleOrText: Boolean = true,
    /** Posted by Arcis itself. */
    val own: Boolean = false,
)

/** The banner settings and the situation on the glasses. */
data class BannerContext(
    val enabled: Boolean = true,
    val onlyWhileStreaming: Boolean = false,
    val glassesConnected: Boolean = true,
    /** A computer's stream or a video is in front. */
    val streaming: Boolean = false,
)

/**
 * Which notifications pop up as a banner on the glasses: messages and calls (and anything
 * the app marks urgent), never ongoing ones (music, downloads), silent ones, group
 * summaries or Arcis's own.
 */
object BannerFilter {

    private val categories = setOf("msg", "call", "email", "alarm", "reminder", "event")

    fun show(n: NotificationFacts, c: BannerContext): Boolean {
        if (!c.enabled || !c.glassesConnected) return false
        if (c.onlyWhileStreaming && !c.streaming) return false
        if (n.own || n.ongoing || n.groupSummary || n.silent || !n.hasTitleOrText) return false
        return n.messaging || n.category in categories || n.highImportance
    }
}
