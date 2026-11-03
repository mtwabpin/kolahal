package com.mtwabpin.kolahal.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Cutting a text keeps its characters whole. */
class TakeWholeTest {
    @Test fun anEmojiIsKeptWholeOrLeftOut() {
        assertEquals("ab", "ab😀c".takeWhole(2))
        // The cut falls inside the emoji: it's left out, not halved.
        assertEquals("ab", "ab😀c".takeWhole(3))
        assertEquals("ab😀", "ab😀c".takeWhole(4))
        assertEquals("", "😀".takeWhole(1))
        assertEquals("", "abc".takeWhole(0))
        assertEquals("abc", "abc".takeWhole(10))
    }
}
