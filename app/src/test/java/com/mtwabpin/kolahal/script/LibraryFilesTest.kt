package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.commands.CommandCatalog
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The scripts bundled in assets/library, read as the editor reads them. */
class LibraryFilesTest {
    private val files = sourceFile("src/main/assets/library").listFiles().orEmpty()
        .mapNotNull { f -> ScriptLang.of(f.extension)?.let { f to it } }.sortedBy { it.first.name }

    @Test fun theyAreAllThere() {
        assertEquals(36, files.size)
    }

    /**
     * No script has the name of a built-in command or of one of its synonyms. Such a script can't
     * be installed ([ScriptLibrary.install]). So the Unlocks widget's script is `unlock-chart`,
     * not `unlocks`.
     */
    @Test fun noneHasABuiltInCommandsName() {
        val taken = files.map { it.first.nameWithoutExtension }.filter { it in CommandCatalog.RESERVED_NAMES }
        assertEquals(emptyList<String>(), taken)
    }

    /**
     * unlock-chart asks for nothing: the unlock counts need no access. A word such as `url` or
     * `status` anywhere in its text, even in a comment, would give it a switch
     * ([ScriptMeta.usesInternet], [ScriptMeta.setsStatus]).
     */
    @Test fun unlockChartAsksForNoAccess() {
        val code = files.single { it.first.name == "unlock-chart.lua" }.first.readText()
        assertEquals(emptyList<String>(), ScriptAccess.asksFor(code))
        assertEquals(listOf(ScriptAccess.WIDGET, "unlock"), ScriptAccess.items(code))
        assertFalse(ScriptMeta.postsNotifications(code))
    }

    /** headlines and webnumber go online, and ask for nothing else. */
    @Test fun headlinesAndWebnumberAskOnlyForTheInternet() {
        for (name in listOf("headlines.js", "webnumber.js")) {
            val code = files.single { it.first.name == name }.first.readText()
            assertEquals(name, listOf(ScriptAccess.INTERNET), ScriptAccess.asksFor(code))
            assertEquals(name, listOf(ScriptAccess.INTERNET, ScriptAccess.WIDGET), ScriptAccess.items(code))
            assertFalse(name, ScriptMeta.postsNotifications(code))
        }
    }

    /** Nothing the editor would point out on saving one. */
    @Test fun nothingToPointOut() {
        for ((f, lang) in files) assertEquals(f.name, emptyList<ScriptCheck.Issue>(), ScriptCheck.check(f.readText(), lang))
    }

    /** Each `@option` is read, and each `options.<name>` read is declared: a typo would be nil. */
    @Test fun optionsDeclaredAreTheOnesRead() {
        val read = Regex("""\boptions\.(\w+)""")
        for ((f, _) in files) {
            val code = f.readText()
            val declared = ScriptMeta.options(code).map { it.name }.toSet()
            val body = code.lines().filterNot { it.trimStart().startsWith("--") || it.trimStart().startsWith("//") }.joinToString("\n")
            assertEquals(f.name, declared, read.findAll(body).map { it.groupValues[1] }.toSet())
        }
        val withOptions = files.filter { (f, _) -> ScriptMeta.options(f.readText()).isNotEmpty() }.map { it.first.nameWithoutExtension }
        assertEquals(listOf("aqi", "charge-reminder", "cricket", "crypto", "exchange", "football", "forecast", "headlines", "hn", "lobsters", "night-dnd", "reddit", "template-timer-js", "template-timer", "weather", "webnumber", "wikipedia", "wiktionary"), withOptions)
    }

    @Test fun choicesAndQuotedDefaults() {
        fun options(name: String) = ScriptMeta.options(files.single { it.first.name == name }.first.readText()).associateBy { it.name }
        assertEquals(listOf("priority", "alarms", "none"), options("night-dnd.lua").getValue("mode").choices)
        assertEquals("bitcoin ethereum", options("crypto.js").getValue("coins").default)
        assertEquals("EUR GBP INR JPY", options("exchange.js").getValue("to").default)
        assertEquals("", options("weather.lua").getValue("city").default)
        // A default with a colon and slashes is read whole. webnumber starts with nothing set.
        assertEquals("https://hnrss.org/frontpage", options("headlines.js").getValue("url").default)
        assertEquals(listOf("", "", ""), listOf("url", "path", "label").map { options("webnumber.js").getValue(it).default })
    }
}
