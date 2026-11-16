package app.arcis.core.text

/**
 * Turns spoken punctuation words in dictated text into symbols ("hello comma world"
 * -> "hello, world"). A small fixed list, no AI: English words plus the symbol names
 * many recognizers already emit. Unknown words are left untouched.
 */
object SpokenPunctuation {

    private val phrases: List<Pair<Regex, String>> = listOf(
        "new paragraph" to "\n\n",
        "new line" to "\n",
        "question mark" to "?",
        "exclamation mark" to "!",
        "exclamation point" to "!",
        "full stop" to ".",
        "period" to ".",
        "comma" to ",",
        "colon" to ":",
        "semicolon" to ";",
    ).map { (words, symbol) -> Regex("\\b${words.replace(" ", "\\s+")}\\b", RegexOption.IGNORE_CASE) to symbol }

    fun apply(text: String): String {
        var out = text
        for ((regex, symbol) in phrases) out = regex.replace(out, symbol)
        out = out
            .replace(Regex("[ \\t]+([.,:;?!])"), "$1")          // no space before punctuation
            .replace(Regex("([.,:;?!])(?=[^\\s.,:;?!])"), "$1 ") // one space after it
            .replace(Regex("[ \\t]*\\n[ \\t]*"), "\n")         // tidy line breaks
            .replace(Regex("[ \\t]{2,}"), " ")
        return capitalizeSentences(out.trim(' ', '\t'))
    }

    /** Capitalizes the first letter after ". ", "? ", "! " and after line breaks. */
    private fun capitalizeSentences(s: String): String {
        val sb = StringBuilder(s)
        var capNext = false
        for (i in sb.indices) {
            val c = sb[i]
            when {
                c == '.' || c == '?' || c == '!' || c == '\n' -> capNext = true
                c.isLetter() -> {
                    if (capNext) sb.setCharAt(i, c.uppercaseChar())
                    capNext = false
                }
                c.isDigit() -> capNext = false
            }
        }
        return sb.toString()
    }

    /**
     * Joins newly dictated text onto what is already in a field: adds a space when needed and
     * capitalizes after a sentence end or at the start of an empty field.
     */
    fun join(existingBefore: String, dictated: String): String {
        if (dictated.isEmpty()) return ""
        val trimmedBefore = existingBefore.trimEnd(' ')
        val startOfSentence = trimmedBefore.isEmpty() || trimmedBefore.last() in ".?!\n"
        var piece = if (startOfSentence) dictated.replaceFirstChar { it.uppercaseChar() } else dictated
        val needsSpace = existingBefore.isNotEmpty() && !existingBefore.last().isWhitespace() &&
            piece.first() !in ".,:;?!\n"
        if (needsSpace) piece = " $piece"
        return piece
    }
}
