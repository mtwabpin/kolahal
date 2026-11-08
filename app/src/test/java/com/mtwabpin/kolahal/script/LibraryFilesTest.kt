package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Test

/** The scripts bundled in assets/library, read as the editor reads them. */
class LibraryFilesTest {
    private val files = sourceFile("src/main/assets/library").listFiles().orEmpty()
        .mapNotNull { f -> ScriptLang.of(f.extension)?.let { f to it } }.sortedBy { it.first.name }

    @Test fun theyAreAllThere() {
        assertEquals(25, files.size)
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
        assertEquals(listOf("aqi", "charge-reminder", "cricket", "crypto", "exchange", "football", "forecast", "hn", "lobsters", "night-dnd", "reddit", "template-timer", "weather", "wikipedia"), withOptions)
    }

    @Test fun choicesAndQuotedDefaults() {
        fun options(name: String) = ScriptMeta.options(files.single { it.first.name == name }.first.readText()).associateBy { it.name }
        assertEquals(listOf("priority", "alarms", "none"), options("night-dnd.lua").getValue("mode").choices)
        assertEquals("bitcoin ethereum", options("crypto.js").getValue("coins").default)
        assertEquals("EUR GBP INR JPY", options("exchange.js").getValue("to").default)
        assertEquals("", options("weather.lua").getValue("city").default)
    }
}
