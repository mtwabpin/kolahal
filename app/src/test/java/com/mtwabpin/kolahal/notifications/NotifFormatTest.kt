package com.mtwabpin.kolahal.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertFalse

class NotifFormatTest {
    @Test fun titleAndText() = assertEquals("Alice — see you at 6", NotifFormat.line("Alice", "see you at 6"))
    @Test fun titleOnly() = assertEquals("Update ready", NotifFormat.line("Update ready", null))
    @Test fun textOnly() = assertEquals("3 new messages", NotifFormat.line("  ", "3 new messages"))
    @Test fun bothBlank() = assertEquals("", NotifFormat.line(null, ""))
    @Test fun collapsesWhitespace() = assertEquals("Bob — line one line two", NotifFormat.line("Bob\n", "line one\n\n  line two"))
    @Test fun sameTitleAndText() = assertEquals("Battery low", NotifFormat.line("Battery low", "Battery low"))
    @Test fun textRepeatsTitle() = assertEquals("Bob — hi there", NotifFormat.line("Bob", "Bob: hi there"))
    @Test fun truncates() {
        val out = NotifFormat.line("T", "x".repeat(500))
        assertEquals(200, out.length)
        assertTrue(out.endsWith("…"))
    }
    @Test fun titlePrefixOfWordKept() = assertEquals("Bob — Bobby is here", NotifFormat.line("Bob", "Bobby is here"))
    @Test fun shortStringUntouched() = assertEquals("abc", NotifFormat.truncate("abc", 3))

    /** Cut short, a line ends on a whole character: never half an emoji, which shows as a box. */
    @Test fun aCutKeepsEmojiWhole() {
        for (n in 140..160) {
            val line = NotifFormat.line("T", "😀".repeat(n))
            assertTrue(line, line.endsWith("…"))
            val cut = line.dropLast(1)
            assertFalse(line, cut.last().isHighSurrogate())
        }
    }
}
