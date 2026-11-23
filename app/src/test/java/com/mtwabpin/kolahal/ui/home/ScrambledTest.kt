package com.mtwabpin.kolahal.ui.home

import com.mtwabpin.kolahal.terminal.Scramble
import com.mtwabpin.kolahal.terminal.Suggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The suggestions while home is scrambled ([scrambledSuggestion]). */
class ScrambledTest {
    private val scramble = Scramble(42L)

    /** An earlier line keeps its command readable and none of its words; a tap still puts the real line in the prompt. */
    @Test
    fun anEarlierLineShowsOnlyItsCommand() {
        val row = Suggestion.Completion("note buy ring for Anna", "note buy ring for Anna", ownFrom = 5)
        val shown = scrambledSuggestion(row, 0, scramble) as Suggestion.Completion
        assertTrue(shown.label, shown.label.startsWith("note "))
        assertEquals(row.label.length, shown.label.length)
        for ((a, b) in row.label.split(' ').drop(1).zip(shown.label.split(' ').drop(1))) assertNotEquals(shown.label, a, b)
        assertEquals(row.replacement, shown.replacement)
        assertEquals(row.key, shown.key)
        // A sum: the `=` stays, the numbers don't.
        val sum = Suggestion.Completion("= 1234*5678", "= 1234*5678", ownFrom = 1)
        val label = scrambledSuggestion(sum, 1, scramble).label
        assertTrue(label, label.startsWith("= ") && '*' in label)
        assertNotEquals(sum.label, label)
    }

    /** A source of the log (`/@whatsapp`, `log only WhatsApp`) is scrambled whole; a kind and a command stay as they are. */
    @Test
    fun aSourceIsScrambledAndAKindIsNot() {
        val source = Suggestion.Completion("@whatsapp", "/@whatsapp ", ownFrom = 0)
        val shown = scrambledSuggestion(source, 1, scramble)
        assertTrue(shown.label, shown.label.startsWith("@"))
        assertEquals(source.label.length, shown.label.length)
        assertNotEquals(source.label, shown.label)
        assertEquals(source.key, shown.key)
        val kind = Suggestion.Completion("@notifications", "/@notifications ")
        assertSame(kind, scrambledSuggestion(kind, 2, scramble))
        val command = Suggestion.Completion("torch", "torch ")
        assertSame(command, scrambledSuggestion(command, 3, scramble))
    }
}
