package com.mtwabpin.kolahal.ui.wallpaper

import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.shader.WallpaperChoice
import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    /** Kolahal's values from the phone's use go over with the scripts' ones, as home works them out. */
    @Test
    fun thePhonesUseGoesOver() {
        val use = state.copy(given = state.given + mapOf("unlocks" to "37", "screen.usual" to "1.25", "screen.h3" to "0.33", "away" to "412.5"))
        assertEquals(use, WallpaperState.decode(use.encode()))
    }

    /**
     * A cell is a character wide and a line tall. `log.line-height` is a line's height (22 at the default text
     * size of 14), not a part of the text's size: taken as one, a cell was 14 times too tall, and a shader
     * that sizes its picture by the cell drew it 14 times too large as a wallpaper.
     */
    @Test
    fun aCellIsALineTall() {
        // The default sizes on a screen with 2.8125 pixels to one.
        val (wide, tall) = WallpaperState.cell(textPx = 14f * 2.8125f, linePx = 22f * 2.8125f)
        assertEquals(23.6f, wide, 0.1f)
        assertEquals(61.9f, tall, 0.1f)
        // Large text on a low line: a cell is never shorter than its text.
        assertEquals(57.6f, WallpaperState.cell(textPx = 48f, linePx = 20f).second, 0.1f)
        // At every size the settings allow, it is less than ten characters tall.
        for (text in 10..24) for (line in 10..56) {
            val (w, h) = WallpaperState.cell(text * 3f, line * 3f)
            assertTrue("$text, $line", h > w && h < w * 10f)
        }
    }

    /** What home writes reads back the same, so a new process sees that the file holds it already. */
    @Test
    fun theFileHoldsWhatWasWritten() {
        val odd = state.copy(cellW = 14f * 0.6f * 2.625f, cellH = 14f * 1.15f * 2.625f)
        assertEquals(odd, WallpaperState.decode(odd.encode()))
        assertNotEquals(odd, WallpaperState.decode(odd.copy(cellW = Math.nextUp(odd.cellW)).encode()))
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
        assertEquals(
            setOf("mountains", "pond", "dunes", "lanterns", "kintsugi", "record", "candle", "hourglass", "windows", "seismograph"),
            metas.filterValues { it.uses(ShaderMeta.WALLPAPER) }.keys,
        )
    }
}
