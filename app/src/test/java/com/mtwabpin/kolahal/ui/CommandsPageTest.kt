package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.commands.CommandSpec
import com.mtwabpin.kolahal.commands.CommandsOff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings → Commands: its sections, and what its search field finds. */
class CommandsPageTest {
    /** A command as its row shows it: [name], [short] and its other names. [help] is not on the row. */
    private fun spec(name: String, short: String, vararg aka: String, help: String = short) =
        CommandSpec(name, usage = name, help = help, short = short, aka = aka.toList())

    private val sections = listOf(
        "Apps" to listOf(
            spec("open", "Open an app or shortcut", "launch"),
            spec("apps", "Your apps as a grid", "ls"),
            spec("info", "An app's info page"),
        ),
        "Web" to listOf(
            spec("search", "Search the web", "g"),
            spec("yt", "Search YouTube", "youtube", help = "Find a video"),
            spec("wiki", "Search Wikipedia"),
        ),
        "System" to listOf(spec("lock", "Lock the screen")),
    )

    /** What [query] finds, a line for each section: `Web: yt wiki`. */
    private fun found(query: String) = commandsFound(sections, query).map { (section, specs) -> "$section: " + specs.joinToString(" ") { it.name } }

    @Test fun aBlankSearchShowsEveryCommand() {
        assertSame(sections, commandsFound(sections, ""))
        assertSame(sections, commandsFound(sections, "  "))
    }

    @Test fun aCommandIsFoundByItsName() {
        assertEquals(listOf("Web: wiki"), found("wiki"))
        assertEquals(listOf("System: lock"), found("lock"))
        // Part of a name is enough.
        assertEquals(listOf("Apps: info"), found("inf"))
    }

    @Test fun aCommandIsFoundByWhatItDoes() {
        assertEquals(listOf("Apps: apps"), found("grid"))
        assertEquals(listOf("System: lock"), found("screen"))
        assertEquals(listOf("Web: search yt wiki"), found("search"))
    }

    @Test fun aCommandIsFoundByItsOtherNames() {
        assertEquals(listOf("Apps: open"), found("launch"))
        assertEquals(listOf("Apps: apps"), found("ls"))
        assertEquals(listOf("Web: yt"), found("youtube"))
    }

    @Test fun caseAndSpacesAroundDontMatter() {
        assertEquals(listOf("Web: yt"), found("  YouTube "))
        assertEquals(listOf("Web: wiki"), found("WIKIPEDIA"))
        assertEquals(listOf("Apps: open"), found("\tLaunch"))
    }

    /** Each word can be in another part of the row: `yt` is the name, `search` is in what it does. */
    @Test fun everyWordMustBeFound() {
        assertEquals(listOf("Web: search"), found("search web"))
        assertEquals(listOf("Web: yt"), found("yt  search"))
        assertEquals(emptyList<String>(), found("search grid"))
    }

    /** The row shows the short text, so the long help text is not searched. */
    @Test fun onlyWhatTheRowShowsIsSearched() {
        assertEquals(emptyList<String>(), found("video"))
    }

    @Test fun sectionsWithNothingFoundAreLeftOut() {
        assertEquals(listOf("Apps: open apps info"), found("app"))
        assertEquals(emptyList<String>(), found("zzzz"))
    }

    /**
     * A section holds only the commands found, so the page shapes each row by its new place:
     * `info` is now the second and last row of Apps, not the third.
     */
    @Test fun sectionsAndCommandsKeepTheirOrder() {
        assertEquals(listOf("Apps: open info", "Web: search yt wiki", "System: lock"), found("e"))
        assertEquals(listOf(sections[0].second[0], sections[0].second[2]), commandsFound(sections, "e").first().second)
    }

    @Test fun thePageHasEachCommandThatCanBeTurnedOffOnce() {
        val names = commandSections().flatMap { it.second }.map { it.name }
        assertEquals(CommandsOff.CHOICES.sorted(), names.sorted())
    }

    @Test fun everyCommandOnThePageIsFoundByEachOfItsNames() {
        val all = commandSections()
        for ((section, specs) in all) for (spec in specs) for (word in listOf(spec.name) + spec.aka) {
            val hit = commandsFound(all, word).firstOrNull { it.first == section }?.second.orEmpty()
            assertTrue("$word doesn't find ${spec.name}", spec in hit)
        }
    }
}
