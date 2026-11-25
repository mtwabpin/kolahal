package com.mtwabpin.kolahal.shader

import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The six hearts shaders that draw hearts share one choice of style and the one part that draws it: a style looks
 * the same in each. Fireworks draws sparks, not hearts, and has neither.
 */
class HeartStylesTest {
    private val hearts = listOf("hearts", "hearts-fountain", "hearts-falling", "hearts-stream", "hearts-poppers", "hearts-bounce")

    private fun source(name: String) = sourceFile("src/main/assets/shaders/$name.agsl").readText()

    /** The shared part: from its first comment to the end of heartLook. */
    private fun shared(name: String): String {
        val code = source(name)
        val start = code.indexOf("// The heart, in the style chosen.")
        assertTrue("$name has the shared part", start >= 0)
        val end = code.indexOf("\n}\n", code.indexOf("half4 heartLook(", start))
        return code.substring(start, end + 3)
    }

    @Test
    fun theSameEightStylesInTheSameOrder() {
        for (name in hearts) {
            val style = ShaderMeta.parse(source(name)).params.single { it.name == "style" }
            assertEquals(name, listOf("glossy", "flat", "gradient", "outline", "double", "neon", "cartoon", "halftone"), style.choices)
            assertEquals(name, 0f, style.default)
            assertEquals(name, "Heart", style.title)
        }
        assertTrue(ShaderMeta.parse(source("hearts-fireworks")).params.none { it.name == "style" })
    }

    @Test
    fun theSharedPartIsTheSameText() {
        val first = shared(hearts.first())
        for (name in hearts.drop(1)) assertEquals("$name's shared part differs from hearts'", first, shared(name))
        // Each style has a line in it that says what it is.
        for (word in listOf("Glossy", "Flat", "Gradient", "Outline", "Double", "Neon", "Cartoon", "Halftone")) {
            assertTrue(word, "// $word: " in first)
        }
    }
}
