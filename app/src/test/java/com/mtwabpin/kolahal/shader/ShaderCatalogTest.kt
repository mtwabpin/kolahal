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

    /** A copy of a template is the user's `my-<idea>`, or the first of `my-<idea>-2` and on that is free. */
    @Test
    fun aTemplatesCopyGetsAFreeName() {
        assertEquals("my-tint", ShaderCatalog.yourName("template-tint") { false })
        assertEquals("my-tint-2", ShaderCatalog.yourName("template-tint") { it == "my-tint" })
        assertEquals("my-tint-4", ShaderCatalog.yourName("template-tint") { it in setOf("my-tint", "my-tint-2", "my-tint-3") })
        // A name that is free once the others are, as a file in the script folder can take one.
        assertEquals("my-scene-3", ShaderCatalog.yourName("template-scene") { it in setOf("my-scene", "my-scene-2", "my-scene-5") })
        for (template in ShaderCatalog.TEMPLATES) {
            val name = ShaderCatalog.yourName(template) { false }
            assertTrue(name, ShaderCatalog.valid(name) && name.startsWith("my-") && name !in ShaderCatalog.BUNDLED)
        }
        // The templates are the user's to start from, never listed as shaders.
        assertTrue(ShaderCatalog.list(emptyList()).none { it.name in ShaderCatalog.TEMPLATES })
        assertEquals(ShaderCatalog.TEMPLATES, ShaderCatalog.TEMPLATES.distinct())
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

    /**
     * Edit opens the user's own file; for one that comes with Kolahal and has none, its code, which a save makes the
     * user's file of the same name. A template, or a name that is no shader, has nothing to open.
     */
    @Test
    fun editOpensYoursOrACopyOfKolahals() {
        assertEquals(ShaderCatalog.Edit.COPY, ShaderCatalog.edit("crt", emptyList()))
        assertEquals(ShaderCatalog.Edit.COPY, ShaderCatalog.edit("crt", listOf("my-glow")))
        // Once saved, it is the user's: the next Edit opens their file.
        assertEquals(ShaderCatalog.Edit.YOURS, ShaderCatalog.edit("crt", listOf("crt")))
        assertEquals(ShaderCatalog.Edit.YOURS, ShaderCatalog.edit("my-glow", listOf("my-glow")))
        assertNull(ShaderCatalog.edit("my-glow", emptyList()))
        assertNull(ShaderCatalog.edit("template-tint", emptyList()))
        // The copy keeps the name, so it is the one used, as the list says.
        assertTrue(ShaderCatalog.list(listOf("crt")).single { it.name == "crt" }.yours)
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
