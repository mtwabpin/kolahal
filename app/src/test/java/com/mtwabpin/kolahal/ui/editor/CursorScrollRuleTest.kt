package com.mtwabpin.kolahal.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The code field shows its cursor after typing and with a new focus, never after a change of colors alone. */
class CursorScrollRuleTest {
    private val code = "local a = 1\nlocal b = 2"

    /** A rule for a field that has the focus and has laid out [code] once. */
    private fun focusedOn(text: String = code) = CursorScrollRule().apply {
        focusChanged(true)
        laidOut(text)
    }

    @Test
    fun theFirstLayoutShowsTheCursor() {
        val rule = CursorScrollRule()
        rule.focusChanged(true)
        rule.laidOut(code)
        assertTrue(rule.allows)
    }

    @Test
    fun typingShowsTheCursor() {
        val rule = focusedOn()
        rule.laidOut("$code!")
        assertTrue("a key typed", rule.allows)
        rule.laidOut(code)
        assertTrue("a key deleted", rule.allows)
    }

    /** A replace by text as long changes the characters, not the length. */
    @Test
    fun aReplaceOfTheSameLengthShowsTheCursor() {
        val rule = focusedOn()
        rule.laidOut(code.replace("a = 1", "x = 9"))
        assertTrue(rule.allows)
    }

    /** The colored window moving, or the theme: the same characters in a new String, laid out again. */
    @Test
    fun aChangeOfColorsAloneLeavesTheView() {
        val rule = focusedOn()
        rule.laidOut(StringBuilder(code).toString())
        assertFalse(rule.allows)
        rule.laidOut(StringBuilder(code).toString())
        assertFalse("and again", rule.allows)
    }

    @Test
    fun typingAfterAChangeOfColorsShowsTheCursor() {
        val rule = focusedOn()
        rule.laidOut(StringBuilder(code).toString())
        assertFalse(rule.allows)
        rule.laidOut("x$code")
        assertTrue(rule.allows)
    }

    /** Without the focus the field asks for nothing on its own; what comes with a new focus goes through. */
    @Test
    fun aNewFocusShowsTheCursor() {
        val rule = focusedOn()
        rule.laidOut(StringBuilder(code).toString())
        assertFalse(rule.allows)
        rule.focusChanged(false)
        assertTrue("on losing the focus", rule.allows)
        rule.laidOut(StringBuilder(code).toString())
        assertTrue("colors changed while it had no focus", rule.allows)
        rule.focusChanged(true)
        assertTrue("on gaining the focus", rule.allows)
    }
}
