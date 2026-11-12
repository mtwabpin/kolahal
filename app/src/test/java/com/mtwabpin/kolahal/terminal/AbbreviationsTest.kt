package com.mtwabpin.kolahal.terminal

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** An alias typed as the first word, then a space, expands in the prompt, as fish's abbreviations do. */
class AbbreviationsTest {
    private val aliases = mapOf("a" to "Authy", "m" to "open maps", "hf" to "help | grep feed ")

    /** [old] becomes [new], the cursor at [cursor] (the end by default). */
    private fun typed(old: String, new: String, cursor: Int = new.length) =
        Abbreviations.onSpace(old, TextFieldValue(new, TextRange(cursor)), aliases::get)

    @Test fun aSpaceAfterTheFirstWordExpandsIt() {
        assertEquals(TextFieldValue("Authy ", TextRange(6)), typed("a", "a "))
        assertEquals(TextFieldValue("open maps ", TextRange(10)), typed("m", "m "))
        // Its own spaces at the end don't double the one typed.
        assertEquals(TextFieldValue("help | grep feed ", TextRange(17)), typed("hf", "hf "))
    }

    @Test fun aSpaceTypedInsideTheLineExpandsTheWordBeforeIt() {
        // `afoo`, the cursor after `a`: a space there makes `a` the first word.
        assertEquals(TextFieldValue("Authy foo", TextRange(6)), typed("afoo", "a foo", cursor = 2))
    }

    @Test fun onlyTheFirstWordFromTheStartOfTheLine() {
        assertNull("a later word", typed("echo a", "echo a "))
        assertNull("after a leading space", typed(" a", " a "))
        assertNull("not an alias", typed("ab", "ab "))
        assertNull("no word", typed("", " "))
    }

    @Test fun onlyWhenOneSpaceIsTyped() {
        assertNull("pasted with its space", typed("", "a "))
        assertNull("a letter, not a space", typed("", "a"))
        assertNull("a deletion", typed("a  ", "a "))
        assertNull("the cursor elsewhere", typed("afoo", "a foo", cursor = 5))
        assertNull("a selection", Abbreviations.onSpace("a", TextFieldValue("a ", TextRange(0, 2)), aliases::get))
    }

    @Test fun anEmptyExpansionLeavesTheWord() {
        assertNull(Abbreviations.onSpace("x", TextFieldValue("x ", TextRange(2))) { " " })
    }
}
