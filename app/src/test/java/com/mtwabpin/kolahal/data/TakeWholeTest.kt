package com.mtwabpin.kolahal.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Cutting a text keeps its characters whole. */
class TakeWholeTest {
    @Test
    fun anEmojiIsKeptWholeOrLeftOut() {
        assertEquals("ab", "ab😀c".takeWhole(2))
        // The cut falls inside the emoji: it's left out, not halved.
        assertEquals("ab", "ab😀c".takeWhole(3))
        assertEquals("ab😀", "ab😀c".takeWhole(4))
        assertEquals("", "😀".takeWhole(1))
        assertEquals("", "abc".takeWhole(0))
        assertEquals("abc", "abc".takeWhole(10))
    }

    @Test
    fun charactersAsTheyAreSeenAreCountedAndKeptWhole() {
        val coder = "\uD83D\uDC69\u200D\uD83D\uDCBB" // 👩‍💻, 5 units
        assertEquals(coder, coder.takeSeen(1, 16))
        assertEquals(coder, (coder + "x").takeSeen(1, 16))
        val flag = "\uD83C\uDDEE\uD83C\uDDF3" // 🇮🇳
        assertEquals(flag, (flag + flag).takeSeen(1, 16))
        val tone = "\uD83E\uDDD1\uD83C\uDFFD\u200D\uD83D\uDCBB" // 🧑🏽‍💻, 7 units
        assertEquals(tone, (tone + tone).takeSeen(1, 16))
        assertEquals("a", "abc".takeSeen(1, 16))
        assertEquals("abcd", "abcdef".takeSeen(4, 16))
        // The unit limit leaves out what doesn't fit whole, and adds no "…".
        assertEquals(coder + coder, (coder + coder + coder).takeSeen(4, 12))
        assertEquals("", coder.takeSeen(1, 4))
        assertEquals("", "abc".takeSeen(0, 16))
        assertEquals("", "".takeSeen(4, 16))
    }
}
