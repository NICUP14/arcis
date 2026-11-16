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
        "question mark" to "? ",
        "exclamation mark" to "! ",
        "exclamation point" to "! ",
        "full stop" to ". ",
        "period" to ". ",
        "comma" to ", ",
        "colon" to ": ",
        "semicolon" to "; ",
    ).map { (words, symbol) ->
        // Swallow the spaces around the spoken word; the symbol brings its own spacing.
        Regex("[ \\t]*\\b${words.replace(" ", "\\s+")}\\b[ \\t]*", RegexOption.IGNORE_CASE) to symbol
    }

    fun apply(text: String): String {
        var out = text
        for ((regex, symbol) in phrases) out = regex.replace(out, Regex.escapeReplacement(symbol))
        // Only spacing produced above is tidied, so "example.com" or "127.0.0.1" stay intact.
        out = out
            .replace(Regex(" +\n"), "\n")
            .replace(Regex(" {2,}"), " ")
        return capitalizeSentences(out.trim(' ', '\t'))
    }

    /** Capitalizes the first letter after ". ", "? ", "! " and after line breaks. */
    private fun capitalizeSentences(s: String): String {
        val sb = StringBuilder(s)
        var capNext = false
        for (i in sb.indices) {
            val c = sb[i]
            when {
                c == '\n' -> capNext = true
                // Only a real sentence end ("done. Next"), not "example.com" or "3.50".
                c == '.' || c == '?' || c == '!' -> capNext = i + 1 < sb.length && sb[i + 1].isWhitespace()
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
