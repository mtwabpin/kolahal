package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.commands.CommandCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What `help` shows, as the command catalog holds it. HelpTest checks the lines on a phone. */
class HelpCatalogTest {
    /** No command is left without a section in `help`. */
    @Test fun everyCommandHasASection() {
        val sectioned = CommandCatalog.HELP_SECTIONS.values.flatten().toSet()
        assertEquals(emptyList<String>(), CommandCatalog.COMMANDS.map { it.name }.filter { it !in sectioned })
    }

    /** Every command with several subcommands lists its uses, and each use starts with the command's name. */
    @Test fun usesStartWithTheirCommand() {
        val needed = listOf(
            "todo", "notes", "feed", "notifs", "script", "media", "dnd", "volume", "brightness", "config", "backup",
            "clear", "work", "private", "alias", "font", "crashes", "status", "log", "apps",
        )
        for (name in needed) {
            val spec = CommandCatalog.COMMANDS.first { it.name == name }
            assertTrue("$name has no uses", spec.uses.size >= 2)
            // alias lists unalias, the command that undoes it.
            assertTrue(name, spec.uses.all { it.first == name || it.first.startsWith("$name ") || (name == "alias" && it.first.startsWith("unalias ")) })
        }
    }

    /** A tap on a use puts its words in the prompt, up to the first one to fill in or choose. */
    @Test fun aUseFillsThePromptUpToItsFirstBlank() {
        assertEquals("script ", useCommand("script on|off <name>"))
        assertEquals("feed every ", useCommand("feed every <id> <2h>"))
        assertEquals("todo done ", useCommand("todo done <n>"))
        assertEquals("mp ", useCommand("mp"))
    }
}
