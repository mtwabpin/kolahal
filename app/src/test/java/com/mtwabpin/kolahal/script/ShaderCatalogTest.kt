package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.Keys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shaders `tty.shader` can name ([ShaderCatalog]): their names, their files and the list. */
class ShaderCatalogTest {
    @Test
    fun aFileGivesItsShadersName() {
        assertEquals("glow", ShaderCatalog.nameOf("glow.agsl"))
        assertEquals("my-glow", ShaderCatalog.nameOf("My Glow.AGSL"))
        assertEquals("a-b", ShaderCatalog.nameOf("a(b).agsl"))
        assertNull(ShaderCatalog.nameOf("glow.txt"))
        assertNull(ShaderCatalog.nameOf(".agsl"))
        assertNull(ShaderCatalog.nameOf("...agsl"))
    }

    /** A name the `shader` command reads as its own word, or `off`, can't be a shader's. */
    @Test
    fun theCommandsWordsAreTaken() {
        for (word in ShaderCatalog.WORDS) {
            assertFalse(word, ShaderCatalog.valid(word))
            assertNull(word, ShaderCatalog.nameOf("$word.agsl"))
        }
        assertTrue(ShaderCatalog.valid("crt"))
        assertTrue(ShaderCatalog.valid("my-glow_2"))
        assertFalse(ShaderCatalog.valid("My"))
        assertFalse(ShaderCatalog.valid("a b"))
        assertFalse(ShaderCatalog.valid("-a"))
        assertFalse(ShaderCatalog.valid("a".repeat(41)))
    }

    @Test
    fun yourFileTakesTheBundledOnesPlace() {
        val list = ShaderCatalog.list(listOf("zoom", "crt", "aurora"))
        assertEquals(listOf("crt", "bloom", "rain", "aurora", "zoom"), list.map { it.name })
        val crt = list.first { it.name == "crt" }
        assertTrue(crt.bundled && crt.yours)
        val bloom = list.first { it.name == "bloom" }
        assertTrue(bloom.bundled && !bloom.yours)
        val aurora = list.first { it.name == "aurora" }
        assertTrue(!aurora.bundled && aurora.yours)
    }

    @Test
    fun theSettingTakesOffAndTheBundledOnes() {
        assertEquals("off", Keys.TTY_SHADER.default)
        assertTrue(Keys.TTY_SHADER.choices.containsAll(listOf("off") + ShaderCatalog.BUNDLED))
        assertEquals(Keys.TTY_SHADER.choices, Keys.TTY_SHADER.choices.distinct())
        assertEquals("rain", Keys.TTY_SHADER.parse("Rain"))
        assertNull(Keys.TTY_SHADER.parse("no-such-shader"))
        assertTrue(Keys.TTY_SHADER in Keys.ALL)
        assertTrue(Keys.TTY_SHADER in Keys.CONFIG_ONLY)
    }
}
