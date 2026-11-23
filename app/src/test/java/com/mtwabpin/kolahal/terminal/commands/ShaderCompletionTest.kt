package com.mtwabpin.kolahal.terminal.commands

import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.ShaderParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What completes `shader …` ([ShaderCommands.completions]). */
class ShaderCompletionTest {
    private fun of(vararg parts: String, shared: List<String> = emptyList()) =
        ShaderCommands.completions(listOf("shader") + parts, shared)

    @Test
    fun theFirstWordIsASubcommandOrAShader() {
        val words = of("")
        assertTrue(words.toString(), words.containsAll(listOf("use", "off", "get", "update") + ShaderCatalog.BUNDLED))
    }

    /** `get` completes what the sources have, `use` what is on the phone. */
    @Test
    fun theSecondWordIsTheSubcommandsShader() {
        assertEquals(listOf("glow", "owner/shaders/glow"), of("get", "", shared = listOf("glow", "owner/shaders/glow")))
        assertTrue(of("get", "").isEmpty())
        assertTrue(of("use", "").containsAll(ShaderCatalog.BUNDLED))
        // None of the user's own on this JVM: nothing to update or remove.
        assertTrue(of("update", "").isEmpty())
        assertTrue(of("off", "").isEmpty())
        assertTrue(of("get", "glow", "", shared = listOf("glow")).isEmpty())
        // copy offers the templates too, which make a shader of the user's; the others don't.
        assertTrue(of("copy", "").containsAll(ShaderCatalog.BUNDLED + ShaderCatalog.TEMPLATES))
        assertTrue(of("use", "").none { it in ShaderCatalog.TEMPLATES })
    }

    /** `set` completes a shader's parameters, then what one can be. */
    @Test
    fun setCompletesParametersAndValues() {
        val params = listOf("amount 0.0..2.0 = 1.0", "shape round|star = star").mapNotNull(ShaderParam::read)
        fun of(vararg words: String) =
            ShaderCommands.completions(listOf("shader") + words, params = { if (it == "snow") params else emptyList() })
        assertTrue(of("set", "").containsAll(ShaderCatalog.BUNDLED))
        assertEquals(listOf("amount", "shape"), of("set", "snow", ""))
        assertEquals(listOf("amount", "shape"), of("reset", "snow", ""))
        assertEquals(listOf("0", "1", "2"), of("set", "snow", "amount", ""))
        assertEquals(listOf("round", "star"), of("set", "snow", "shape", ""))
        assertTrue(of("reset", "snow", "amount", "").isEmpty())
        assertTrue(of("set", "other", "").isEmpty())
        assertTrue(of("use", "snow", "").isEmpty())
        val follow = ShaderCommands.completions(listOf("shader", "follow", "snow", "amount", ""), values = { listOf("battery") }, params = { params })
        assertEquals(listOf("battery", "off"), follow)
    }

    /** `edit` takes a shader's name: it completes the shaders there are, and not the templates. */
    @Test
    fun editCompletesTheShaders() {
        assertTrue("edit" in of(""))
        assertTrue("edit" in ShaderCommands.TAKE_NAME)
        assertTrue(of("edit", "").containsAll(ShaderCatalog.BUNDLED))
        assertTrue(of("edit", "").none { it in ShaderCatalog.TEMPLATES })
        assertTrue(of("edit", "crt", "").isEmpty())
        // No shader can be called edit: `shader edit` is always the subcommand.
        assertTrue("edit" in ShaderCatalog.WORDS)
    }

    /** A word the command reads can't be a shader's name, or that shader couldn't be named. */
    @Test
    fun everySubcommandIsAWordNoShaderHas() {
        assertEquals(emptyList<String>(), ShaderCommands.SUBCOMMANDS - ShaderCatalog.WORDS.toSet())
    }
}
