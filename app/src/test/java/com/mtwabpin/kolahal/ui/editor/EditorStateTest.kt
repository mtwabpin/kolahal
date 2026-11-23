package com.mtwabpin.kolahal.ui.editor

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What an editor holds while a text is open: the text against its saved copy, typed and undone, and what comes back from the background. */
class EditorStateTest {
    private val scope = object : SaverScope {
        override fun canBeSaved(value: Any) = true
    }

    private fun state(text: String, cursor: Int = text.length) =
        EditorState(text, TextFieldValue(text, TextRange(cursor)))

    /** The text is changed once it differs from the saved copy, and no longer once that copy is the same again. */
    @Test
    fun dirtyFollowsTheSavedCopy() {
        val s = state("one")
        assertFalse(s.dirty)
        s.edit(TextFieldValue("one two", TextRange(7)))
        assertTrue(s.dirty)
        s.saved = "one two"
        assertFalse(s.dirty)
    }

    /** What is inserted takes the selection's place, or goes in at the cursor, and the cursor ends up after it. */
    @Test
    fun insertReplacesTheSelection() {
        val s = state("one two", cursor = 3)
        s.insert("(")
        assertEquals(TextFieldValue("one( two", TextRange(4)), s.value)
        s.value = s.value.copy(selection = TextRange(5, 8))
        s.insert("2")
        assertEquals(TextFieldValue("one( 2", TextRange(6)), s.value)
    }

    /** Undo and redo step through the changes; moving the cursor alone is not a step. */
    @Test
    fun undoAndRedoStepThroughTheChanges() {
        val s = state("one")
        assertNull(s.undo())
        s.insert(" two")
        s.value = s.value.copy(selection = TextRange(0))
        s.undo()
        assertEquals("one", s.value.text)
        assertFalse(s.dirty)
        s.redo()
        assertEquals("one two", s.value.text)
        assertNull(s.redo())
    }

    /** With a limit, a change that makes the text too long or gives it too many lines isn't made, and the message says why. */
    @Test
    fun aChangePastTheLimitIsRefused() {
        val s = EditorState(
            "ab\ncd",
            TextFieldValue("ab\ncd", TextRange(5)),
            limit = EditorLimit(maxChars = 8, maxLines = 3)
        )
        s.insert("efg")
        assertEquals("ab\ncdefg", s.value.text)
        s.insert("h")
        assertEquals("ab\ncdefg", s.value.text)
        assertEquals("The editor takes up to 8 characters", s.message?.text)
        // Shorter is always fine, also when the text is already past the limit.
        s.edit(TextFieldValue("ab\nc\n", TextRange(5)))
        assertEquals("ab\nc\n", s.value.text)
        s.insert("\n")
        assertEquals("ab\nc\n", s.value.text)
        assertEquals("The editor takes up to 3 lines", s.message?.text)
        // What was refused is not an undo step.
        val full = EditorState(
            "12345678",
            TextFieldValue("12345678", TextRange(8)),
            limit = EditorLimit(maxChars = 8, maxLines = 3)
        )
        full.insert("9")
        assertFalse(full.history.canUndo)
    }

    /** Back from the background: the text as typed, the saved copy, and the find bar as it was. */
    @Test
    fun whatWasTypedComesBack() {
        val s = state("return 1")
        s.edit(TextFieldValue("return 2", TextRange(7)))
        s.findOpen = true
        s.replaceOpen = true
        s.query = TextFieldValue("ret")
        s.replacement = TextFieldValue("x")
        s.ignoreCase = false
        val saver = EditorState.saver("return 1", flashLine = 3)
        val back = saver.restore(with(saver) { scope.save(s) }!!)!!
        assertEquals(TextFieldValue("return 2", TextRange(7)), back.value)
        assertEquals("return 1", back.saved)
        assertTrue(back.dirty)
        assertTrue(back.findOpen && back.replaceOpen && !back.ignoreCase)
        assertEquals("ret", back.query.text)
        assertEquals("x", back.replacement.text)
        // The line it was opened at flashes again, and the undo history starts again.
        assertEquals(3, back.flashLine)
        assertFalse(back.history.canUndo)
    }

    /** The limit comes back with the text: what was typed in the background is held to it still. */
    @Test
    fun theLimitComesBack() {
        val s = state("abc")
        val saver = EditorState.saver("abc", flashLine = null, limit = EditorLimit(maxChars = 4, maxLines = 3))
        val back = saver.restore(with(saver) { scope.save(s) }!!)!!
        back.insert("d")
        assertEquals("abcd", back.value.text)
        back.insert("e")
        assertEquals("abcd", back.value.text)
        assertEquals("The editor takes up to 4 characters", back.message?.text)
    }

    /** A very big text isn't kept: the stored one comes back, with the cursor where it was. */
    @Test
    fun aVeryBigTextComesBackAsStored() {
        val big = "x".repeat(KEPT_TEXT_MAX + 1)
        val s = EditorState(big, TextFieldValue(big, TextRange(500)))
        val saver = EditorState.saver(big, flashLine = null)
        val kept = with(saver) { scope.save(s) }!!
        assertEquals(listOf(false, 500, 500), (kept as List<*>)[0])
        assertEquals(false, kept[1])
        val back = saver.restore(kept)!!
        assertEquals(TextFieldValue(big, TextRange(500)), back.value)
        assertFalse(back.dirty)
    }
}
