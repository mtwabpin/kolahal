package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.script.ShaderCatalog
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
    }

    /** A word the command reads can't be a shader's name, or that shader couldn't be named. */
    @Test
    fun everySubcommandIsAWordNoShaderHas() {
        assertEquals(emptyList<String>(), ShaderCommands.SUBCOMMANDS - ShaderCatalog.WORDS.toSet())
    }
}
