package app.arcis.core.net

/**
 * Finds the 4-digit PIN in Moonlight's pairing dialog ("Please enter the following PIN
 * on the target PC: 1234"), so Arcis can have the Arcis Bridge enter it into Sunshine.
 */
object MoonlightPin {

    private val pinAfterWord = Regex("""\bPIN\b\D{0,80}?(?<!\d)(\d{4})(?!\d)""", RegexOption.IGNORE_CASE)

    /** The PIN, or null if [text] isn't a pairing message. */
    fun find(text: String): String? = pinAfterWord.find(text)?.groupValues?.get(1)
}
