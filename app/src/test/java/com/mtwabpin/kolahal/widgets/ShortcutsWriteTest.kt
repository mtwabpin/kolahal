package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Only you write the Shortcuts widget's list ([Shortcuts]), in the Widgets screen. A tap on one of
 * its buttons runs a command as if you had typed it, also a sensitive one. So nothing a script can
 * reach may write the list: no script function, and no command (`launcher.run` runs commands).
 *
 * The tests read the sources. They name every file that may touch the list, so a new one fails
 * here and gets a look.
 */
class ShortcutsWriteTest {
    private val root = sourceFile("src/main/java/com/mtwabpin/kolahal")
    private val sources = root.walkTopDown().filter { it.extension == "kt" }.toList()

    private fun path(f: File) = f.relativeTo(root).invariantSeparatorsPath

    /** The lines of [f] that are code: no comment lines, and nothing after `//`. */
    private fun code(f: File): List<String> = f.readLines()
        .filterNot { line -> line.trimStart().let { it.startsWith("*") || it.startsWith("/*") } }
        .map { it.substringBefore("//") }

    /** The files whose code matches [what]. */
    private fun naming(what: Regex): Set<String> = sources.filter { f -> code(f).any(what::containsMatchIn) }.map(::path).toSet()

    @Test fun theSourcesAreFound() {
        assertTrue("no sources at ${root.absolutePath}", sources.size > 100)
        assertTrue(File(root, "widgets/Shortcuts.kt").exists())
    }

    /** The key is in one file, so the list can't be written past [Shortcuts]. */
    @Test fun onlyShortcutsNamesItsKey() {
        val named = sources.filter { "widget.shortcuts" in it.readText() }.map(::path)
        assertEquals(listOf("widgets/Shortcuts.kt"), named)
    }

    /** [Shortcuts.write] has one caller: the view model's own data for the built-in widgets. */
    @Test fun onlyTheViewModelsDataWritesTheList() {
        val writers = naming(Regex("""\bShortcuts\s*\.\s*write\b|\bShortcuts\s*::\s*write\b""")) - "widgets/Shortcuts.kt"
        assertEquals("Shortcuts.write is for the Widgets screen's editor only", setOf("terminal/OwnWidgets.kt"), writers)
    }

    /**
     * The save is named in three files and no other: the view model's data has it, the view model
     * hands it on, and the Widgets screen's editor calls it. No command handler is among them, and
     * nothing in script/.
     */
    @Test fun onlyTheEditorSavesTheList() {
        val named = setOf("terminal/OwnWidgets.kt", "terminal/TerminalViewModel.kt", "ui/ShortcutsEditor.kt")
        assertEquals("saveShortcuts is for the Widgets screen's editor only", named, naming(Regex("""\bsaveShortcuts\b""")))
    }

    /**
     * The view model reaches a command handler only as a TerminalUi, and a script only through
     * commands. The save is not a part of TerminalUi, so neither can ask the view model for it.
     */
    @Test fun theSaveIsNotSomethingACommandCanAskFor() {
        val ui = File(root, "terminal/TerminalUi.kt").readText()
        assertTrue(ui.contains("interface TerminalUi"))
        assertTrue("TerminalUi must not offer the shortcuts", !ui.contains("hortcut"))
        // The view model's own save overrides nothing: it is not on any interface.
        val vm = code(File(root, "terminal/TerminalViewModel.kt")).single { it.contains("fun saveShortcuts") }
        assertTrue(vm, !vm.contains("override"))
    }

    /**
     * What a script can reach never names the list: the script functions (script/), the commands'
     * facts (commands/), the command handlers, and what a handler may ask of the view model
     * (TerminalUi). `launcher.run` ends in a command handler, as a typed line does.
     */
    @Test fun noCommandAndNoScriptFunctionNamesTheList() {
        val list = Regex("""\bShortcuts\s*[.:]|\bsaveShortcuts\b|\bShortcut\(""")
        val handlers = sources.filter { f ->
            val p = path(f)
            p.startsWith("script/") || p.startsWith("commands/") || p.startsWith("terminal/") && (f.name.endsWith("Commands.kt") || f.name in setOf("CommandProcessor.kt", "CommandGroup.kt", "TerminalUi.kt"))
        }
        assertTrue("the command handlers are not where they were", handlers.count { path(it).startsWith("terminal/") } >= 8)
        assertEquals(emptyList<String>(), handlers.filter { f -> code(f).any(list::containsMatchIn) }.map(::path))
    }

    /**
     * The list is one key of home's own preferences file. Each file that opens it is named here,
     * and each writes keys with fixed names of its own. A new one must not write a key that a
     * script or a command chooses.
     */
    @Test fun theFilesThatOpenHomesPreferencesAreKnown() {
        val known = setOf("LauncherApp.kt", "data/StatusLines.kt", "script/ScriptAccess.kt", "terminal/TerminalViewModel.kt", "widgets/Shortcuts.kt")
        assertEquals(known, naming(Regex("""\bStores\.LAUNCHER\b""")) - "data/Stores.kt")
    }
}
