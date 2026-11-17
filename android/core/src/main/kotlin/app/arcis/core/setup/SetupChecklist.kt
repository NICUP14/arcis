package app.arcis.core.setup

/** One thing Arcis needs (or can use) on the phone, in the order they are shown. */
enum class SetupItem(val required: Boolean) {
    ACCESSIBILITY(required = true),
    MOONLIGHT(required = true),
    BRIDGE(required = true),
    NOTIFICATIONS(required = false),
    MICROPHONE(required = false),
    BLUETOOTH(required = false),
    BATTERY(required = false),
    /** Moonlight's picture-in-picture setting: a tip, Arcis can't read it. */
    MOONLIGHT_PIP(required = false),
}

enum class SetupStatus { DONE, TODO, TIP }

data class SetupRow(val item: SetupItem, val status: SetupStatus)

/**
 * The phone's setup checklist as a pure model: which items are done (from [done]),
 * which are left, and whether Arcis is ready ([ready]: every required item done).
 * [shown] limits the rows (e.g. items whose feature isn't in this build).
 */
class SetupChecklist(
    private val done: Set<SetupItem>,
    private val shown: Set<SetupItem> = SetupItem.entries.toSet(),
) {
    val rows: List<SetupRow> = SetupItem.entries.filter { it in shown }.map { item ->
        SetupRow(
            item,
            when {
                item == SetupItem.MOONLIGHT_PIP -> SetupStatus.TIP
                item in done -> SetupStatus.DONE
                else -> SetupStatus.TODO
            },
        )
    }

    /** Every required item is done. */
    val ready: Boolean = rows.none { it.item.required && it.status == SetupStatus.TODO }

    /** The first required item left, else the first optional one; null when nothing is left. */
    val next: SetupItem? = rows.firstOrNull { it.item.required && it.status == SetupStatus.TODO }?.item
        ?: rows.firstOrNull { it.status == SetupStatus.TODO }?.item

    /** Done / total, counting only real items (not tips). */
    val progress: Pair<Int, Int> = rows.filter { it.status != SetupStatus.TIP }.let { real ->
        real.count { it.status == SetupStatus.DONE } to real.size
    }
}
