package com.mtwabpin.kolahal.ui.wallpaper

import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.shader.WallpaperChoice
import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What home hands the live wallpaper ([WallpaperState]), and which shaders can be one. */
class WallpaperStateTest {
    private val state = WallpaperState(
        name = "dunes", code = "// @name Dunes\n\"quoted\" \\ and ☾\nuniform shader content;",
        set = mapOf("wind" to "1.5"), follows = mapOf("camels" to "battery"), formulas = mapOf("camels" to "battery / 20"),
        given = mapOf("weather.rain" to "0.25", "weather.sky" to "clear"),
        dark = mapOf("bg" to 0xFF0B0D0B.toInt(), "fg" to -1), light = mapOf("bg" to 0xFFF3F7F1.toInt()),
        alwaysDark = true, cellW = 22.5f, cellH = 48f,
    )

    @Test
    fun itComesBackAsItWas() {
        assertEquals(state, WallpaperState.decode(state.encode()))
        val bare = state.copy(set = emptyMap(), follows = emptyMap(), formulas = emptyMap(), given = emptyMap(), alwaysDark = false)
        assertEquals(bare, WallpaperState.decode(bare.encode()))
    }

    @Test
    fun whatIsNoneIsNone() {
        for (bad in listOf("", "{", "[]", "{}", "{\"name\":\"a\"}", state.encode().replace("\"cellW\"", "\"other\""))) {
            assertNull(bad.take(30), WallpaperState.decode(bad))
        }
    }

    /** One that works on text is none, and neither is a one-off. (Shaders aren't supported on this JVM: that is asked first.) */
    @Test
    fun shadersThatWorkOnText() {
        val metas = ShaderCatalog.BUNDLED.associateWith { ShaderMeta.parse(sourceFile("src/main/assets/shaders/$it.agsl").readText()) }
        assertEquals(setOf("crt", "lcd", "eink", "foil", "riso"), metas.filterValues { it.text }.keys)
        assertTrue(metas.getValue(WallpaperChoice.DEFAULT).let { !it.text && it.once == null })
        assertNotNull(WallpaperChoice.problem("crt", metas.getValue("crt")))
        if (!Shaders.supported) assertEquals(Shaders.NEEDS, WallpaperChoice.problem("pond", metas.getValue("pond")))
        // The scenes know that they are one.
        assertEquals(setOf("mountains", "pond", "dunes"), metas.filterValues { it.uses(ShaderMeta.WALLPAPER) }.keys)
    }
}
