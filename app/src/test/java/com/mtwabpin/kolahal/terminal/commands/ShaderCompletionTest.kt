package com.mtwabpin.kolahal.terminal.commands

import com.mtwabpin.kolahal.commands.CommandCatalog
import com.mtwabpin.kolahal.commands.UsageLine
import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.ShaderParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What completes `shader …` ([ShaderCommands.completions]). */
class ShaderCompletionTest {
    private fun of(vararg parts: String, shared: List<String> = emptyList()) =
        ShaderCommands.completions(listOf("shader") + parts, shared)

    @Test
    fun theFirstWordIsASubcommandOrAShader() {
        val words = of("")
        assertTrue(words.toString(), words.containsAll(listOf("ls", "use", "off", "get", "save", "rm") + ShaderCatalog.BUNDLED))
    }

    /** `get` completes what the sources have, `use` what is on the phone. */
    @Test
    fun theSecondWordIsTheSubcommandsShader() {
        assertEquals(listOf("glow", "owner/shaders/glow"), of("get", "", shared = listOf("glow", "owner/shaders/glow")))
        assertTrue(of("get", "").isEmpty())
        assertTrue(of("use", "").containsAll(ShaderCatalog.BUNDLED))
        // None of the user's own on this JVM: nothing to remove.
        assertTrue(of("rm", "").isEmpty())
        assertTrue(of("delete", "").isEmpty())
        assertTrue(of("off", "").isEmpty())
        assertTrue(of("get", "glow", "", shared = listOf("glow")).isEmpty())
        // save offers the templates too, which make a shader of the user's; the others don't.
        assertTrue(of("save", "").containsAll(ShaderCatalog.BUNDLED + ShaderCatalog.TEMPLATES))
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

    /**
     * `shader` takes the words `script` and `feed` take: `ls`, `rm` (or `delete`), `save`, and `get --update`. The
     * old words are no subcommands, offer no shaders after them, and are not in the usage line; the live wallpaper
     * is `wallpaper shader`.
     */
    @Test
    fun theOldWordsAreGone() {
        val old = listOf("list", "remove", "copy", "update", "wallpaper")
        val first = of("")
        for (word in old) {
            assertFalse(word, word in ShaderCommands.SUBCOMMANDS)
            assertFalse(word, word in ShaderCommands.TAKE_NAME)
            assertFalse(word, word in first)
            assertTrue(word, of(word, "").isEmpty())
        }
        assertTrue(listOf("rm", "delete", "save").all { it in ShaderCommands.TAKE_NAME })
        assertTrue(listOf("ls", "rm", "delete", "save").all { it in ShaderCatalog.WORDS })
        val usage = CommandCatalog.COMMAND_USAGES.getValue("shader")
        val words = UsageLine.choices(UsageLine.tokens(usage)[1]).mapNotNull { it.word }
        assertTrue(words.toString(), words.containsAll(listOf("ls", "get", "save", "rm")))
        assertTrue(words.toString(), words.none { it in old })
        val uses = CommandCatalog.COMMANDS.first { it.name == "shader" }.uses.map { it.first }
        assertTrue(uses.toString(), "shader get --update" in uses)
        assertTrue(uses.toString(), uses.none { use -> old.any { use.startsWith("shader $it") } })
    }

    /** A word the command reads can't be a shader's name, or that shader couldn't be named. */
    @Test
    fun everySubcommandIsAWordNoShaderHas() {
        assertEquals(emptyList<String>(), ShaderCommands.SUBCOMMANDS - ShaderCatalog.WORDS.toSet())
    }
}
