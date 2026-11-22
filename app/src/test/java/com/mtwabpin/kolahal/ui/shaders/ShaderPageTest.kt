package com.mtwabpin.kolahal.ui.shaders

import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderSuits
import com.mtwabpin.kolahal.shader.SharedShader
import com.mtwabpin.kolahal.terminal.ShaderActions
import com.mtwabpin.kolahal.terminal.ShaderRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Shaders screen says of a shader ([ShaderPage]). */
class ShaderPageTest {
    private val code =
        "// @name Glow\n// @author ann\n// @version 1.0\nuniform shader content;\nhalf4 main(float2 p) { return content.eval(p); }"
    private val mine = ShaderRow("glow", bundled = false, yours = true, code = code)
    private val bundled = ShaderRow("crt", bundled = true, yours = false, code = "// @animated\n$code")
    private val shared = mine.copy(
        origin = ScriptCatalog.Copy(edited = false, newer = false),
        latest = SharedShader(
            "glow", "shaders/glow/glow.agsl", "Glow", "", "ann", "1.1", "MIT", emptyList(),
            animated = false, requires = null, sha256 = "a".repeat(64),
        ),
    )

    private fun notes(row: ShaderRow, inUse: String = "off") = ShaderPage.notes(row, inUse)

    @Test
    fun theOneInUseSaysSo() {
        assertEquals(listOf("In use", "Yours"), notes(mine, "glow"))
        assertEquals(listOf("Yours"), notes(mine, "crt"))
        assertEquals(listOf("Animated"), notes(bundled))
    }

    /** A shader that follows the phone's lean says so, after whether it moves. */
    @Test
    fun oneThatUsesTiltSaysSo() {
        val leaning = code.replace("uniform shader content;", "uniform shader content;\nuniform float2 tilt;")
        assertEquals(listOf("Yours", "Uses tilt"), notes(mine.copy(code = leaning)))
        assertEquals(listOf("Animated", "Uses tilt"), notes(bundled.copy(code = "// @animated\n$leaning")))
    }

    /** In Get more shaders the list says it, and once the code is here the code does, whatever the list left out. */
    @Test
    fun aSharedOneThatUsesTiltSaysSo() {
        val e = shared.latest!!
        assertEquals("glow.agsl", ShaderPage.fileLine(e))
        assertEquals("glow.agsl · animated", ShaderPage.fileLine(e.copy(animated = true)))
        assertEquals("glow.agsl · uses tilt", ShaderPage.fileLine(e.copy(tilt = true)))
        assertEquals("glow.agsl · animated · uses tilt", ShaderPage.fileLine(e.copy(animated = true, tilt = true)))
        assertFalse(ShaderPage.usesTilt(e, null))
        assertFalse(ShaderPage.usesTilt(e, code))
        assertTrue(ShaderPage.usesTilt(e.copy(tilt = true), null))
        assertTrue(ShaderPage.usesTilt(e, "uniform shader content; uniform float2 tilt;"))
    }

    /** The search finds every word of the query, and each chip keeps what it says. */
    @Test
    fun searchAndChips() {
        val night = ShaderRow(
            "stars", bundled = true, yours = false,
            code = "// @name Night sky\n// @description A meteor now and then\n// @tags space, calm\n// @suits dark good, light poor, wallpaper fair\n// @param amount 0.0..1.0 = 0.5\n// @animated\nuniform shader content;\nuniform float amount;",
        )
        fun has(query: String, vararg f: ShaderPage.Filter) = ShaderPage.matches(night, query, f.toSet())
        for (query in listOf("", "night", "STARS", "meteor", "sky calm", "space")) assertTrue(query, has(query))
        for (query in listOf("rain", "night rain")) assertFalse(query, has(query))
        assertTrue(has("", ShaderPage.Filter.DARK, ShaderPage.Filter.ANIMATED, ShaderPage.Filter.ADJUSTABLE))
        for (f in listOf(
            ShaderPage.Filter.LIGHT, ShaderPage.Filter.WALLPAPER, ShaderPage.Filter.STILL, ShaderPage.Filter.TILT,
            ShaderPage.Filter.ONCE, ShaderPage.Filter.YOURS,
        )) assertFalse(f.name, has("", f))
        // Every chip has to pass, and the words too.
        assertFalse(has("", ShaderPage.Filter.DARK, ShaderPage.Filter.LIGHT))
        assertFalse(has("rain", ShaderPage.Filter.DARK))
        assertTrue(ShaderPage.matches(mine, "glow", setOf(ShaderPage.Filter.YOURS, ShaderPage.Filter.STILL)))
    }

    /** A shader that is poor over a wallpaper is worth a warning once enough of one shows. */
    @Test
    fun theWallpaperWarning() {
        fun warning(suits: String, shows: Int) =
            ShaderActions.wallpaperWarning(ShaderMeta.parse("// @name Pond\n// @suits $suits\nuniform shader content;"), "pond", shows)
        assertEquals("Pond doesn't work well with a wallpaper, and yours shows at 40%", warning("wallpaper poor", 40))
        assertNull(warning("wallpaper poor", ShaderSuits.WALLPAPER_SHOWS))
        assertNull(warning("wallpaper poor", 0))
        assertNull(warning("wallpaper fair", 40))
        assertNull(warning("dark poor", 40))
    }

    /** One that was turned off isn't in use, even while it is the one set; the reason shows when it is known. */
    @Test
    fun oneThatWasTurnedOffSaysWhy() {
        val off = bundled.copy(turnedOff = true, why = "Kolahal stopped while it was on")
        assertEquals(listOf("Turned off: Kolahal stopped while it was on", "Animated"), notes(off, "crt"))
        assertEquals("Turned off", ShaderPage.turnedOff(off.copy(why = null)))
    }

    @Test
    fun itSaysWhereItIsFrom() {
        assertNull(ShaderPage.from(bundled))
        assertEquals("Yours", ShaderPage.from(mine))
        assertEquals(
            "Yours, used instead of the one that comes with Kolahal",
            ShaderPage.from(bundled.copy(yours = true))
        )
        assertEquals("From ${ScriptCatalog.SOURCE}", ShaderPage.from(shared))
        val other = shared.copy(origin = ScriptCatalog.Copy(edited = true, newer = false, source = "owner/shaders"))
        assertEquals(listOf("From owner/shaders", "Edited"), notes(other))
    }

    @Test
    fun aNewerVersionShows() {
        val newer = shared.copy(origin = ScriptCatalog.Copy(edited = false, newer = true))
        assertEquals(listOf("From ${ScriptCatalog.SOURCE}", "Update to 1.1"), notes(newer))
        assertEquals("Update available", ShaderPage.update(newer.copy(latest = null)))
        assertEquals("Update available", ShaderPage.update(newer.copy(latest = newer.latest!!.copy(version = ""))))
    }

    @Test
    fun itsFileItsVersionAndItsAuthor() {
        assertEquals("glow.agsl · v1.0 · by ann", ShaderPage.byLine(mine))
        assertEquals("glow.agsl", ShaderPage.byLine(mine.copy(code = "uniform shader content;")))
        assertEquals("Glow", mine.title)
        assertEquals("glow", mine.copy(code = "uniform shader content;").title)
    }
}
