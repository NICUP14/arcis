package app.arcis.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

/** apply() keeps the first letter as spoken; join() capitalizes based on the field contents. */
class SpokenPunctuationTest {

    @Test
    fun replacesWordsWithSymbols() {
        assertEquals("hello, world.", SpokenPunctuation.apply("hello comma world period"))
    }

    @Test
    fun questionAndNewLine() {
        assertEquals("are you there?\nYes", SpokenPunctuation.apply("are you there question mark new line yes"))
    }

    @Test
    fun capitalizesAfterSentenceEnd() {
        assertEquals("done. Next one!", SpokenPunctuation.apply("done full stop next one exclamation mark"))
    }

    @Test
    fun leavesNormalTextAlone() {
        assertEquals("running ten minutes late", SpokenPunctuation.apply("running ten minutes late"))
    }

    @Test
    fun doesNotTouchWordsContainingPunctuationNames() {
        assertEquals("periodic commands", SpokenPunctuation.apply("periodic commands"))
    }

    @Test
    fun keepsAddressesAndNumbersIntact() {
        assertEquals("go to example.com", SpokenPunctuation.apply("go to example.com"))
        assertEquals("127.0.0.1", SpokenPunctuation.apply("127.0.0.1"))
        assertEquals("it costs 3.50, ok", SpokenPunctuation.apply("it costs 3.50 comma ok"))
    }

    @Test
    fun joinAddsSpaceAndKeepsCase() {
        assertEquals(" and more", SpokenPunctuation.join("Hello", "and more"))
        assertEquals("And more", SpokenPunctuation.join("", "and more"))
        assertEquals(" And more", SpokenPunctuation.join("Hello.", "and more"))
        assertEquals("and more", SpokenPunctuation.join("Hello ", "and more"))
        assertEquals(", ok", SpokenPunctuation.join("Hello", ", ok"))
    }
}
