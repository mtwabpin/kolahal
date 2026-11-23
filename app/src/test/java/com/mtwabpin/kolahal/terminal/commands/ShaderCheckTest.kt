package com.mtwabpin.kolahal.terminal.commands

import com.mtwabpin.kolahal.shader.ShaderClock
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderValues
import com.mtwabpin.kolahal.shader.Shaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What `shader check` says of how often a shader that moves is drawn ([ShaderCommands.redrawn]), and of its uniforms; what `shader values` lists. */
class ShaderCheckTest {
    private fun redrawn(vararg lines: String, uniforms: String) = ShaderCommands.redrawn(
        ShaderClock.Pace.of(ShaderMeta.parse(lines.joinToString("\n") + "\nuniform shader content; $uniforms")),
    )

    @Test
    fun itSaysWhatTheHeaderAsks() {
        // Without the lines, as before.
        assertEquals("redrawn 30 times a second", ShaderCommands.redrawn(ShaderClock.Pace.DEFAULT))
        assertEquals("redrawn 30 times a second", redrawn("// @animated", uniforms = "uniform float time; uniform float2 touch;"))
        assertEquals("redrawn 10 times a second", redrawn("// @animated", "// @fps 10", uniforms = "uniform float time;"))
        assertEquals(
            "redrawn 10 times a second, 30 for 5 s after a touch",
            redrawn("// @animated", "// @fps 10", "// @settles 5", uniforms = "uniform float time; uniform float2 touch;"),
        )
        assertEquals(
            "redrawn 30 times a second for 0.5 s after a touch",
            redrawn("// @animated", "// @settles 0.5", uniforms = "uniform float touchAge;"),
        )
    }

    /** The uniforms Kolahal sets are listed as `shader check` finds them ([Shaders.check]): `corner` among them. */
    @Test
    fun itListsTheCorner() {
        val meta = ShaderMeta.parse("uniform shader content; uniform float2 resolution; uniform float corner, mine;")
        val check = Shaders.Check(null, meta.uniforms.map { it.name }.filter { it in ShaderMeta.KNOWN }, meta.unset(), meta.animated)
        val lines = ShaderCommands.checkLines(check)
        assertTrue(lines.toString(), "Kolahal sets: resolution, corner" in lines)
        assertTrue(lines.toString(), "Not set by Kolahal, so 0: mine" in lines)
    }

    /** `cutout` is listed among the uniforms Kolahal sets, as `corner` is. */
    @Test
    fun itListsTheCutout() {
        val meta = ShaderMeta.parse("uniform shader content; uniform float corner; uniform float4 cutout;")
        val check = Shaders.Check(null, meta.uniforms.map { it.name }.filter { it in ShaderMeta.KNOWN }, meta.unset(), meta.animated)
        val lines = ShaderCommands.checkLines(check)
        assertTrue(lines.toString(), "Kolahal sets: corner, cutout" in lines)
    }

    /** `shader values`: a value on each line, and a row of days or blocks on one. */
    @Test
    fun valuesAreListedWithTheirRows() {
        fun source(id: String, value: String?) = ShaderValues.Source(id, ShaderValues.label(id), value)
        val days = (1..7).map { source("unlocks.d$it", if (it == 7) "1" else "0.5") }
        val blocks = (1..8).map { source("screen.h$it", null) }
        val lines = ShaderCommands.valueLines(
            listOf(source("battery", "64.0"), source("unlocks", "12")) + days + blocks + source("away", null) + source("weather.rain", "0.25"),
        )
        assertEquals(
            listOf(
                "battery" to "64% · Battery",
                "unlocks" to "12 · Unlocks today",
                "unlocks.d1 … unlocks.d7" to "0.5 0.5 0.5 0.5 0.5 0.5 1 · Unlocks on each of the last 7 days, d7 today",
                "screen.h1 … screen.h8" to "no value yet · Screen time in each 3 hours of today, h1 from midnight",
                "away" to "no value yet · Time away before the last unlock",
                "weather.rain" to "0.25",
            ),
            lines,
        )
    }
}
