package com.mtwabpin.kolahal.ui.editor

import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.terminal.commands.ShaderCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the shader editor says after a save ([shaderSavedMessage]): what `shader check` found, at its line. */
class ShaderSavedMessageTest {
    private fun check(problem: String? = null, warnings: List<String> = emptyList(), line: Int? = null) =
        Shaders.Check(problem, listOf("time"), emptyList(), animated = false, warnings = warnings, line = line)

    /** Nothing wrong: saved, and the first word of `shader check`. It goes away by itself. */
    @Test
    fun aGoodShaderIsSaved() {
        val ok = check()
        val m = shaderSavedMessage(ok)
        assertEquals("Saved · ${ShaderCommands.checkLines(ok).first()}", m.text)
        assertEquals(EditorMessage.Kind.OK, m.kind)
        assertNull(m.line)
    }

    /** The compiler's error is saved too, says it can't be used and why, in red, and goes to its line. */
    @Test
    fun aCompileErrorHasItsLine() {
        val m = shaderSavedMessage(check("line 3: unknown identifier 'x'", line = 3))
        assertEquals("Saved · can't be used: line 3: unknown identifier 'x'", m.text)
        assertEquals(EditorMessage.Kind.ERROR, m.kind)
        assertEquals(3, m.line)
    }

    /** A problem found before compiling has no line; what else is wrong is counted. */
    @Test
    fun aProblemWithoutALineCountsTheRest() {
        val m = shaderSavedMessage(
            check("it needs 'uniform shader content;', the surface it draws over", listOf("'@fps 99': write a whole number from 1 to 30")),
        )
        assertEquals("Saved · can't be used: it needs 'uniform shader content;', the surface it draws over (and 1 more)", m.text)
        assertEquals(EditorMessage.Kind.ERROR, m.kind)
        assertNull(m.line)
    }

    /** A header line that is left out doesn't keep it from being used: it compiles, and the line says what to write. */
    @Test
    fun aWarningIsShownAfterWhatItFound() {
        val warned = check(warnings = listOf("'@fps 99': write a whole number from 1 to 30"))
        val m = shaderSavedMessage(warned)
        assertEquals(
            "Saved · ${ShaderCommands.checkLines(warned).first()} · '@fps 99': write a whole number from 1 to 30",
            m.text,
        )
        assertEquals(EditorMessage.Kind.ERROR, m.kind)
    }

    @Test
    fun aFileThatCantBeReadSaysSo() {
        val m = shaderSavedMessage(null)
        assertEquals("Saved · it can't be read", m.text)
        assertEquals(EditorMessage.Kind.ERROR, m.kind)
    }
}
