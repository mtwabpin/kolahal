package com.mtwabpin.kolahal.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** How long a line's context is once stored as JSON: what keeps it within one field. */
class LogContextSizeTest {
    private fun length(vararg pairs: Pair<String, String>) = LogRepository.jsonLength(mapOf(*pairs))

    @Test
    fun plainValues() {
        assertEquals("{}".length, length())
        assertEquals("""{"a":"b"}""".length, length("a" to "b"))
        assertEquals("""{"a":"b","cd":"ef"}""".length, length("a" to "b", "cd" to "ef"))
    }

    @Test
    fun escapesCountAsWritten() {
        // org.json writes \" \\ \/ \t \b \n \r \f as two characters, other control characters as \u00XX.
        assertEquals("""{"u":"a\/b"}""".length, length("u" to "a/b"))
        assertEquals("""{"t":"\"\\\t\b\n\r\f"}""".length, length("t" to "\"\\\t\b\n\r\u000c"))
        assertEquals("""{"c":"\u0001"}""".length, length("c" to "\u0001"))
        // The line and paragraph separators are written as six-character escapes too.
        assertEquals(
            "{\"s\":\"".length + 2 * 6 + "\"}".length,
            length("s" to String(charArrayOf(Char(0x2028), Char(0x2029))))
        )
        assertEquals("""{"k\/":"é"}""".length, length("k/" to "é"))
    }
}
