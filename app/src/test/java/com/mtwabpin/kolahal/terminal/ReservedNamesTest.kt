package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.commands.CommandCatalog
import com.mtwabpin.kolahal.commands.InstantRun
import com.mtwabpin.kolahal.commands.ControlSpecs
import org.junit.Assert.assertTrue
import org.junit.Test

/** A built-in always wins, so every name it answers to is kept from aliases and scripts. */
class ReservedNamesTest {
    @Test fun everyNameACommandAnswersToIsReserved() {
        for (name in AppCommands.NAMES + ControlSpecs.NAMES + CommandCatalog.PRODUCTIVITY) {
            assertTrue("$name isn't reserved", name in CommandCatalog.RESERVED_NAMES)
        }
    }

    /** `help favs` shows the command it's another name for, so that has to be a command. */
    @Test fun everyOtherNameIsForACommand() {
        for ((other, name) in InstantRun.SYNONYMS) assertTrue("$other names $name", name in CommandCatalog.COMMAND_NAMES)
    }
}

/**
 * The names `CommandProcessor.execute` dispatches on (its `when`) and the command catalog must agree.
 * The safety checks (`InstantRun.isSensitive`, `commands.off`) look a word up through
 * [InstantRun.canonical] and the catalog, while dispatch matches the word as typed. So a name in the
 * `when` but in neither list would pass both checks unseen: a script could run it, and an alias
 * could hide it.
 */
class DispatchNamesTest {
    /** The string labels of the `when (name)` in `execute`, read from the source. */
    private val dispatched: Set<String> by lazy {
        val lines = com.mtwabpin.kolahal.sourceFile("src/main/java/com/mtwabpin/kolahal/terminal/CommandProcessor.kt").readLines()
        val start = lines.indexOfFirst { it.trim() == "when (name) {" }
        assertTrue("no `when (name) {` in CommandProcessor.kt", start >= 0)
        val end = (start + 1 until lines.size).first { lines[it].trim().startsWith("else ->") }
        val label = Regex("^\\s*((?:\"[^\"]+\",\\s*)*\"[^\"]+\")\\s*->")
        (start + 1 until end).mapNotNull { label.find(lines[it])?.groupValues?.get(1) }
            .flatMap { Regex("\"([^\"]+)\"").findAll(it).map { m -> m.groupValues[1] }.toList() }
            .toSet()
    }

    @Test fun theSourceIsRead() = assertTrue(dispatched.toString(), dispatched.size > 50 && "alias" in dispatched && "maps" in dispatched)

    @Test fun everyNameDispatchedIsACommandOrAnotherNameForOne() {
        for (name in dispatched) {
            assertTrue("$name is dispatched but isn't a command or a synonym (CommandCatalog.COMMANDS, InstantRun.SYNONYMS)", name in CommandCatalog.RESERVED_NAMES)
            assertTrue("$name stands for ${InstantRun.canonical(name)}, which isn't a command", InstantRun.canonical(name) in CommandCatalog.COMMAND_NAMES)
        }
    }

    @Test fun everyCommandIsDispatched() {
        val groups = AppCommands.NAMES + com.mtwabpin.kolahal.commands.ControlSpecs.NAMES + CommandCatalog.PRODUCTIVITY
        // `=` is the calculator, before the `when`.
        val missing = CommandCatalog.COMMAND_NAMES - dispatched - groups - "="
        assertTrue("in the catalog but not dispatched: $missing", missing.isEmpty())
    }

    @Test fun everySensitiveNameIsACommand() {
        val unknown = InstantRun.SENSITIVE - CommandCatalog.RESERVED_NAMES - "="
        assertTrue("sensitive but not a command: $unknown", unknown.isEmpty())
    }
}
