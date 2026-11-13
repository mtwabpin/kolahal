package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.commands.CommandCatalog
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A command handler prints its usage line by the command's name (`usage("torch")`), and the line
 * comes from the catalog. A name the catalog doesn't have would fail only when that line is needed.
 */
class UsageNamesTest {
    /** `usage("widgets", "widget")`, but not `ScriptMeta.usage(…)` or a call with a variable. */
    private val call = Regex("""(?<![.\w])usage\(((?:"[^"]+",\s*)*"[^"]+")\)""")

    /** Each name a handler in terminal/ asks the usage line of, with its file, read from the source. */
    private val asked: List<Pair<String, String>> by lazy {
        sourceFile("src/main/java/com/mtwabpin/kolahal/terminal").listFiles()!!.filter { it.extension == "kt" }.flatMap { file ->
            call.findAll(file.readText()).flatMap { m -> Regex("\"([^\"]+)\"").findAll(m.groupValues[1]).map { file.name to it.groupValues[1] } }.toList()
        }
    }

    @Test fun theSourceIsRead() {
        assertTrue(asked.toString(), asked.size > 20)
        assertTrue(asked.toString(), ("ControlCommands.kt" to "torch") in asked && ("CommandProcessor.kt" to "widget") in asked)
    }

    @Test fun everyNameAskedHasAUsageLine() {
        // `lua` and `js` ask by the language's name (usage(lang.ext)).
        val names = asked.map { it.second } + ScriptLang.entries.map { it.ext }
        assertEquals(emptyList<String>(), names.distinct().filter { it !in CommandCatalog.COMMAND_USAGES })
    }

    /** The line starts with the command's own name, so "Usage: " and it read as one. */
    @Test fun aUsageLineStartsWithItsCommand() {
        for (name in asked.map { it.second }.distinct()) {
            val line = CommandCatalog.COMMAND_USAGES.getValue(name)
            assertTrue("$name: $line", line == name || line.startsWith("$name "))
        }
    }
}
