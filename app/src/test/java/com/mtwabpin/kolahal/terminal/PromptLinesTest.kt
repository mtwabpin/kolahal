package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.script.ScriptNames
import com.mtwabpin.kolahal.terminal.commands.ScriptCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lines typed at the prompt that are treated specially: secret values, log searches, new names. */
class PromptLinesTest {
    @Test
    fun aLineWithASecretValueIsNotLogged() {
        assertTrue(ScriptCommands.carriesSecret("script secret weather OWM_KEY abc123"))
        assertTrue(ScriptCommands.carriesSecret("  Scripts   SECRETS weather OWM_KEY abc123 "))
        assertTrue(ScriptCommands.carriesSecret("script secret weather OWM_KEY \"two words\""))
        // Any kind of space separates words, as when the command runs.
        assertTrue(ScriptCommands.carriesSecret("script secret weather OWM_KEY abc123"))
        assertTrue(ScriptCommands.carriesSecret("script secret w K v | grep x"))
    }

    @Test
    fun linesWithoutAValueAreLogged() {
        assertFalse(ScriptCommands.carriesSecret("script secret weather OWM_KEY"))
        assertFalse(ScriptCommands.carriesSecret("script secret weather OWM_KEY --clear"))
        assertFalse(ScriptCommands.carriesSecret("script secret weather"))
        assertFalse(ScriptCommands.carriesSecret("script run weather a b c"))
        assertFalse(ScriptCommands.carriesSecret("echo script secret w K v"))
    }

    @Test
    fun aSlashStartsALogSearch() {
        assertEquals(emptyList<String>(), searchWords("/"))
        assertEquals(listOf("battery", "low"), searchWords("/battery   low "))
        assertEquals(listOf("github.com/x"), searchWords("/github.com/x"))
        assertNull(searchWords("battery"))
        assertNull(searchWords(" /battery"))
        assertNull(searchWords(""))
    }

    @Test
    fun scriptNamesForNewAndRenamedScripts() {
        val taken = setOf("weather")
        assertNull(ScriptNames.invalid("my-script_2") { it in taken })
        assertTrue(ScriptNames.invalid("Weather") { it in taken }.orEmpty().contains("lowercase"))
        assertTrue(
            ScriptNames.invalid("a".repeat(41)) { it in taken }.orEmpty().contains("lowercase")
        )
        assertTrue(
            ScriptNames.invalid("help") { it in taken }.orEmpty().contains("built-in command")
        )
        // The other names a command answers to are taken too: a script named `text` would never run.
        assertTrue(
            ScriptNames.invalid("text") { it in taken }.orEmpty().contains("built-in command")
        )
        assertTrue(ScriptNames.invalid("weather") { it in taken }.orEmpty().contains("already"))
    }
}
