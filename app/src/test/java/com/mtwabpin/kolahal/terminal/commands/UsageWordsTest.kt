package com.mtwabpin.kolahal.terminal.commands

import com.mtwabpin.kolahal.commands.CommandCatalog
import com.mtwabpin.kolahal.commands.UsageLine
import com.mtwabpin.kolahal.terminal.commands.UsageWords.Asked
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What `usage` reads in its words: days, one of Android's levels, or an app ([UsageWords]). */
class UsageWordsTest {
    private fun of(line: String) = UsageWords.of(line.split(' ').filter { it.isNotEmpty() })

    @Test
    fun aloneOrANumberIsDays() {
        assertEquals(Asked.Days(1), of(""))
        assertEquals(Asked.Days(7), of("7"))
        // 1 to 30, as before.
        assertEquals(Asked.Days(1), of("0"))
        assertEquals(Asked.Days(30), of("90"))
    }

    @Test
    fun eachWordAloneIsItsView() {
        for (word in listOf("summary", "week", "month", "year", "full")) {
            assertEquals(Asked.Summary(word, listOf(word)), of(word))
        }
        // Typed in capitals, still the word.
        assertEquals(Asked.Summary("month", listOf("Month")), of("Month"))
    }

    @Test
    fun aLevelTakesAnAppAfterIt() {
        assertEquals(Asked.Summary("month", listOf("month", "youtube")), of("month youtube"))
        assertEquals(Asked.Summary("year", listOf("year", "google", "maps")), of("year google maps"))
        assertEquals(Asked.Summary("week", listOf("week", "chrome", "-w")), of("week chrome -w"))
    }

    @Test
    fun anythingElseIsAnApp() {
        assertEquals(Asked.App(listOf("youtube")), of("youtube"))
        assertEquals(Asked.App(listOf("google", "maps")), of("google maps"))
        assertEquals(Asked.App(listOf("chrome", "-w")), of("chrome -w"))
    }

    /** An app whose name starts like a word keeps its name: only the word itself, alone, is the view. */
    @Test
    fun anAppNamedLikeAWordIsTheApp() {
        assertEquals(Asked.App(listOf("weekly")), of("weekly"))
        assertEquals(Asked.App(listOf("Monthly")), of("Monthly"))
        assertEquals(Asked.App(listOf("yearbook")), of("yearbook"))
        assertEquals(Asked.App(listOf("fullscreen")), of("fullscreen"))
        // `summary` and `full` take no app, so after them comes an app's whole name.
        assertEquals(Asked.App(listOf("summary", "pro")), of("summary pro"))
        assertEquals(Asked.App(listOf("full", "screen")), of("full screen"))
        // `week` alone is Android's weeks; with an app after it, that app over the weeks.
        assertEquals(Asked.Summary("week", listOf("week")), of("week"))
        assertEquals(Asked.Summary("week", listOf("week", "weekly")), of("week weekly"))
    }

    /** The usage line `help usage` shows offers every word, so the first word of the prompt offers them too. */
    @Test
    fun theUsageLineHasEveryWord() {
        val usage = CommandCatalog.COMMAND_USAGES.getValue("usage")
        val first = UsageLine.choices(UsageLine.tokens(usage)[1]).mapNotNull { it.word }
        assertEquals(UsageWords.WORDS.keys.toList().sorted(), first.sorted())
        assertTrue(UsageWords.LEVELS.all { it in UsageWords.WORDS })
        val uses = CommandCatalog.COMMANDS.single { it.name == "usage" }.uses.map { it.first }
        for (word in UsageWords.WORDS.keys) assertTrue("$word: $uses", uses.any { it.startsWith("usage $word") })
    }

    /** `usage-summary` is gone: no command, no other name, no place in help; `usage` and `screentime` stay. */
    @Test
    fun usageSummaryIsNoCommand() {
        assertFalse("usage-summary" in CommandCatalog.RESERVED_NAMES)
        assertFalse(CommandCatalog.HELP_SECTIONS.values.flatten().contains("usage-summary"))
        assertTrue(CommandCatalog.COMMANDS.none { it.usage.contains("usage-summary") || it.uses.any { u -> u.first.contains("usage-summary") } })
        assertEquals("usage", CommandCatalog.SYNONYMS["screentime"])
        assertTrue("usage" in CommandCatalog.OFFERED_NAMES)
    }
}
