package com.mtwabpin.kolahal.shader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The words that play a one-off shader on a notification ([ShaderWords]). */
class ShaderWordsTest {
    private fun has(text: String, words: String) = ShaderWords.has(text, words)

    @Test
    fun wholeWordsInAnyCase() {
        assertTrue(has("alex\nRunning late", "Alex"))
        assertTrue(has("See you at the PICNIC!", "Alex, picnic"))
        assertTrue(has("Hi (Alex), hello", "alex"))
        assertTrue(has("Alex's here", "alex"))
        assertFalse(has("A picnics day", "picnic"))
        assertFalse(has("Alexander", "Alex"))
        // A later place counts when the first is part of a longer word.
        assertTrue(has("Alexander and Alex", "Alex"))
        assertTrue(has("good night, all", "good night"))
    }

    @Test
    fun otherScripts() {
        assertTrue(has("नमस्ते माया", "माया"))
        assertFalse(has("मायावी", "माया"))
        assertFalse(has("मायाँ", "माया"))
        assertTrue(has("Élodie arrive", "élodie"))
        assertFalse(has("Zoés", "Zoé"))
    }

    @Test
    fun noWordsMatchNothing() {
        for (none in listOf("", " ", ",", " , ,")) assertFalse(has("anything", none))
    }

    /** A shader that was removed keeps its words (Undo puts it back): they don't keep another one-off with the word from playing. */
    @Test
    fun aShaderThatIsGoneDoesntPlay() {
        val words = linkedMapOf("gone" to "alex", "hearts" to "Alex, picnic", "confetti" to "alex")
        val said = "Alex\nRunning late"
        assertEquals("hearts", ShaderWords.pick(words, said) { it != "gone" })
        assertEquals("gone", ShaderWords.pick(words, said) { true })
        assertEquals("confetti", ShaderWords.pick(words, said) { it == "confetti" })
        assertNull(ShaderWords.pick(words, said) { false })
        assertNull(ShaderWords.pick(words, "Bob\nhello") { true })
    }
}
