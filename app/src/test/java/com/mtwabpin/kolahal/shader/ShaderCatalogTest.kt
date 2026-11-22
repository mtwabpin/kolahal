package com.mtwabpin.kolahal.shader

import com.mtwabpin.kolahal.data.Keys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shaders `screen.shader` can name ([ShaderCatalog]): their names, their files and the list. */
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
        val list = ShaderCatalog.list(listOf("zoom", "crt", "my-glow"))
        assertEquals(ShaderCatalog.BUNDLED + listOf("my-glow", "zoom"), list.map { it.name })
        val crt = list.first { it.name == "crt" }
        assertTrue(crt.bundled && crt.yours)
        val lcd = list.first { it.name == "lcd" }
        assertTrue(lcd.bundled && !lcd.yours)
        val glow = list.first { it.name == "my-glow" }
        assertTrue(!glow.bundled && glow.yours)
    }

    @Test
    fun theSettingTakesOffAndTheBundledOnes() {
        assertEquals("off", Keys.SCREEN_SHADER.default)
        assertTrue(Keys.SCREEN_SHADER.choices.containsAll(listOf("off") + ShaderCatalog.BUNDLED))
        assertEquals(Keys.SCREEN_SHADER.choices, Keys.SCREEN_SHADER.choices.distinct())
        assertEquals("rain", Keys.SCREEN_SHADER.parse("Rain"))
        assertNull(Keys.SCREEN_SHADER.parse("no-such-shader"))
        assertTrue(Keys.SCREEN_SHADER in Keys.ALL)
        assertTrue(Keys.SCREEN_SHADER in Keys.CONFIG_ONLY)
    }
}
