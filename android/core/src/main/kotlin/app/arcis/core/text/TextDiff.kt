package app.arcis.core.text

/**
 * Turns a change in a "relay" text field into keystrokes for a target that has no text
 * field of its own (Bluetooth keyboard, host PC): how many Backspaces, then what to type.
 * Assumes the caret sits at the end, which is how the relay field is used.
 */
data class TextEdit(val backspaces: Int, val insert: String) {
    val isEmpty: Boolean get() = backspaces == 0 && insert.isEmpty()

    companion object {
        fun between(old: String, new: String): TextEdit {
            var common = 0
            val max = minOf(old.length, new.length)
            while (common < max && old[common] == new[common]) common++
            return TextEdit(old.length - common, new.substring(common))
        }
    }
}
