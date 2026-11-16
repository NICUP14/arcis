package app.arcis.core.switcher

import app.arcis.core.input.SwitchDirection

/** Something that can be switched to: a phone app, a host session, a Bluetooth device. */
data class RecentItem(val id: String, val label: String)

/**
 * Most-recently-used list behind the app switcher, with Android-style "quick switch":
 * consecutive switches within [quickSwitchWindowMs] walk through the list without
 * reordering it; after a pause, the app you landed on becomes the most recent.
 * Index 0 is the app currently shown.
 */
class Recents(private val quickSwitchWindowMs: Long = 2_500, private val capacity: Int = 20) {

    private val items = mutableListOf<RecentItem>()
    private var walkIndex = 0
    private var lastWalkAt = Long.MIN_VALUE / 2

    /** Items in display order (most recent first), reflecting the quick-switch position. */
    val list: List<RecentItem> get() = items.toList()

    /** Index of the item currently shown (non-zero only during a quick switch). */
    val currentIndex: Int get() = walkIndex

    val current: RecentItem? get() = items.getOrNull(walkIndex)

    /** An item came to the front (user opened it). */
    fun onForeground(item: RecentItem, nowMs: Long) {
        val walking = nowMs - lastWalkAt <= quickSwitchWindowMs
        val existing = items.indexOfFirst { it.id == item.id }
        if (walking && existing >= 0) {
            // Landing on the item we just switched to: keep the order while still walking.
            items[existing] = item
            walkIndex = existing
            return
        }
        commit()
        if (existing >= 0) items.removeAt(existing)
        items.add(0, item)
        walkIndex = 0
        while (items.size > capacity) items.removeAt(items.lastIndex)
    }

    /**
     * Previous = further back in history (like swiping right on Android's gesture bar),
     * Next = back towards the most recent. Returns the item to show, or null at the end.
     */
    fun step(direction: SwitchDirection, nowMs: Long): RecentItem? {
        if (nowMs - lastWalkAt > quickSwitchWindowMs) commit()
        val target = walkIndex + if (direction == SwitchDirection.PREVIOUS) 1 else -1
        if (target !in items.indices) return null
        walkIndex = target
        lastWalkAt = nowMs
        return items[target]
    }

    /** Selects an item explicitly (card switcher) and makes it the most recent. */
    fun select(id: String): RecentItem? {
        val idx = items.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val item = items.removeAt(idx)
        items.add(0, item)
        walkIndex = 0
        lastWalkAt = Long.MIN_VALUE / 2
        return item
    }

    fun remove(id: String) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx < 0) return
        items.removeAt(idx)
        if (walkIndex > idx || walkIndex >= items.size) walkIndex = (walkIndex - 1).coerceAtLeast(0)
    }

    /** End of a quick switch: the item walked to becomes the most recent. */
    private fun commit() {
        if (walkIndex > 0 && walkIndex < items.size) {
            val item = items.removeAt(walkIndex)
            items.add(0, item)
        }
        walkIndex = 0
    }
}
