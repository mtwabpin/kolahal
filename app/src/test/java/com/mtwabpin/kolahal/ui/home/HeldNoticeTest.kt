package com.mtwabpin.kolahal.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The notice over the log keeps its last words while it fades out. */
class HeldNoticeTest {
    @Test
    fun keepsTheLastWordsWhileItFades() {
        val held = HeldNotice()
        assertFalse(held.update(null, undo = false))
        assertTrue(held.update("Line deleted", undo = true))
        assertEquals("Line deleted", held.text)
        assertTrue(held.undo)
        // The offer ended: nothing shows, but the words stay for the fade.
        assertFalse(held.update(null, undo = false))
        assertEquals("Line deleted", held.text)
        assertTrue(held.undo)
        // A copy's note after it: its own words, with no Undo.
        assertTrue(held.update("Copied", undo = false))
        assertEquals("Copied", held.text)
        assertFalse(held.undo)
    }
}
