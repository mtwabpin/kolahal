package com.mtwabpin.kolahal.terminal.commands

import com.mtwabpin.kolahal.shader.ShaderClock
import com.mtwabpin.kolahal.shader.ShaderMeta
import org.junit.Assert.assertEquals
import org.junit.Test

/** What `shader check` says of how often a shader that moves is drawn ([ShaderCommands.redrawn]). */
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
}
