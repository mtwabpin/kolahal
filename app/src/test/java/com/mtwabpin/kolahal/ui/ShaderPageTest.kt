package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.SharedShader
import com.mtwabpin.kolahal.terminal.ShaderRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
