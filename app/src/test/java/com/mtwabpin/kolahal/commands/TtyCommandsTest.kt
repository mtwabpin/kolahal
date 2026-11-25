package com.mtwabpin.kolahal.commands

import com.mtwabpin.kolahal.commands.InstantRun.Target
import com.mtwabpin.kolahal.script.ScriptImport
import com.mtwabpin.kolahal.script.ScriptNames
import com.mtwabpin.kolahal.terminal.commands.Aliases
import com.mtwabpin.kolahal.ui.settings.commandSections
import com.mtwabpin.kolahal.ui.widgets.ShortcutPicks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The full-screen screens' commands belong to the tty home style: offered there as any other, and in
 * the gui style not offered at all, their short names plain words, `screen <name>` still running when
 * typed in full.
 */
class TtyCommandsTest {
    private val screens = TtyCommands.SCREENS

    @Test
    fun theTtyStyleOffersThemAndTheGuiStyleDoesnt() {
        val tty = TtyCommands.offeredNames(tty = true)
        val gui = TtyCommands.offeredNames(tty = false)
        assertEquals(CommandCatalog.OFFERED_NAMES, tty)
        assertTrue(TtyCommands.ONLY.all { it in tty })
        assertTrue(TtyCommands.ONLY.none { it in gui })
        assertEquals(tty - TtyCommands.ONLY, gui)
        assertEquals(gui.toList(), TtyCommands.offered(tty = false).map { it.name })
        // shader draws over home in both styles.
        assertTrue("shader" in gui)
    }

    /** `help` lists by these: the gui style has no Screens section at all, as none of its commands is offered there. */
    @Test
    fun helpInTheGuiStyleHasNoScreens() {
        val shown = TtyCommands.offered(tty = false).map { it.name }
        assertEquals(emptyList<String>(), CommandCatalog.HELP_SECTIONS.getValue("screens").filter { it in shown })
        // What is drawn over home and behind it is in Look, in both styles.
        assertEquals(listOf("theme", "font", "shader", "shaders", "wallpaper"), CommandCatalog.HELP_SECTIONS.getValue("look").filter { it in shown })
        assertEquals(
            CommandCatalog.HELP_SECTIONS.getValue("screens"),
            CommandCatalog.HELP_SECTIONS.getValue("screens").filter { it in TtyCommands.offeredNames(tty = true) },
        )
    }

    /** In the gui style `screen` is as `skill.md`: offered to nobody, and it runs when typed in full. */
    @Test
    fun screenRunsInBothStyles() {
        assertEquals(CommandCatalog.HIDDEN, TtyCommands.hidden(tty = true))
        assertTrue("screen" in TtyCommands.hidden(tty = false))
        assertTrue("skill.md" in TtyCommands.hidden(tty = false))
        assertTrue(TtyCommands.isCommand("screen", tty = false))
        assertTrue(TtyCommands.isCommand("screen", tty = true))
    }

    /** The short names are commands in the tty style, and plain words in the gui style; the rest are commands in both. */
    @Test
    fun theShortNamesAreCommandsOnlyInTheTtyStyle() {
        assertEquals(setOf("matrix", "pipes", "sl", "clock", "top", "decode", "boot"), screens)
        for (name in screens) {
            assertTrue(name, TtyCommands.isCommand(name, tty = true))
            assertFalse(name, TtyCommands.isCommand(name, tty = false))
        }
        for (name in listOf("help", "shader", "fetch", "neofetch")) assertTrue(name, TtyCommands.isCommand(name, tty = false))
        assertFalse(TtyCommands.isCommand("weather", tty = true))
    }

    /** CommandProcessor opens a screen for the short names only in the tty style; in the gui style they go on as a word no command has. */
    @Test
    fun theDispatchFollowsTheStyle() {
        val line = com.mtwabpin.kolahal.sourceFile("src/main/java/com/mtwabpin/kolahal/terminal/commands/CommandProcessor.kt")
            .readLines().single { it.trimStart().startsWith("\"matrix\",") }
        val names = Regex("\"([^\"]+)\"").findAll(line.substringBefore("->")).map { it.groupValues[1] }.toSet()
        assertEquals(screens, names)
        assertTrue(line, line.substringAfter("->").trim().startsWith("if (tty()) ui.showTtyScreen(name)"))
    }

    /** A switch in Settings → Commands for a short name counts only in the tty style. */
    @Test
    fun turnedOffCountsOnlyWhereItIsACommand() {
        assertEquals(setOf("clock", "maps"), TtyCommands.off(setOf("clock", "maps"), tty = true))
        assertEquals(setOf("maps"), TtyCommands.off(setOf("clock", "maps"), tty = false))
        assertEquals(setOf("screen"), TtyCommands.off(setOf("screen"), tty = false))
        // The stored value keeps them: the switches come back with the tty style.
        assertTrue(screens.all { it in CommandsOff.CHOICES })
        assertEquals("clock,maps", CommandsOff.parse("maps clock")?.split(',')?.sorted()?.joinToString(","))
    }

    /** Settings → Commands and the Shortcuts card's editor list what the style offers. */
    @Test
    fun theListsToPickFromFollowTheStyle() {
        val guiPage = commandSections(tty = false).flatMap { it.second }.map { it.name }
        val ttyPage = commandSections(tty = true).flatMap { it.second }.map { it.name }
        assertTrue(TtyCommands.ONLY.none { it in guiPage })
        assertTrue(TtyCommands.ONLY.all { it in ttyPage })
        assertEquals(CommandsOff.CHOICES.filter { it !in TtyCommands.ONLY }.sorted(), guiPage.sorted())

        val scripts = listOf("weather", "bigclock")
        val gui = ShortcutPicks.commands(scripts, setOf("bigclock"), tty = false)
        val tty = ShortcutPicks.commands(scripts, setOf("bigclock"), tty = true)
        assertTrue(TtyCommands.ONLY.none { it in gui })
        assertTrue("weather" in gui && "bigclock" !in gui)
        assertTrue(TtyCommands.ONLY.all { it in tty })
        assertTrue("weather" in tty && "bigclock" in tty)
    }

    /** A script that draws a screen (`@uses screen`) is a tty thing: the gui style doesn't offer it. */
    @Test
    fun screenScriptsAreOfferedOnlyInTheTtyStyle() {
        val screen = "-- @command\n-- @uses screen\ntty.screen{}\n"
        val plain = "-- @command\nprint('hi')\n"
        assertTrue(TtyCommands.offersScript(screen, tty = true))
        assertFalse(TtyCommands.offersScript(screen, tty = false))
        assertTrue(TtyCommands.offersScript(plain, tty = false))
        assertTrue(TtyCommands.offersScript(plain, tty = true))
        val names = listOf("weather", "tty-clock", "bigclock")
        assertEquals(listOf("weather"), TtyCommands.offeredScripts(names, setOf("tty-clock", "bigclock"), tty = false))
        assertEquals(names, TtyCommands.offeredScripts(names, setOf("tty-clock", "bigclock"), tty = true))
    }

    /** The usage lines instant launch reads in the gui style: the view model leaves out the tty commands. */
    private val guiCommands = CommandCatalog.COMMAND_USAGES - TtyCommands.ONLY

    private fun target(typed: String, apps: List<String>, commands: Map<String, String>, aliases: Map<String, String> = emptyMap()) =
        InstantRun.target<String>(
            typed, InstantRun.ALL, 2,
            rankedApps = { q -> apps.filter { it.lowercase().startsWith(q) }.map { it to if (it.lowercase() == q) 0 else 1 } },
            appLabel = { it },
            commands = commands,
            aliases = aliases,
            scriptCommands = emptyList(),
        )

    /**
     * Instant launch never opens a screen, at any prefix of its name or the whole of it: Enter does,
     * in the tty style. In the gui style `clock` is the Clock app's name, as before the screens.
     */
    @Test
    fun instantLaunchNeverOpensAScreen() {
        for (name in screens) for (end in 2..name.length) {
            val typed = name.take(end)
            assertNotEquals(typed, Target.Command(name), target(typed, emptyList(), CommandCatalog.COMMAND_USAGES))
            assertNotEquals(typed, Target.Command(name), target(typed, listOf("Clock", "Boost"), CommandCatalog.COMMAND_USAGES))
        }
        // The tty style: `clock` is the command, which waits for Enter, so nothing opens by itself.
        assertNull(target("clock", listOf("Clock"), CommandCatalog.COMMAND_USAGES))
        // The gui style: no command is called clock, so the app opens as soon as it is the only match.
        assertEquals(Target.App("Clock"), target("clock", listOf("Clock"), guiCommands))
        assertEquals(Target.App("Topaz"), target("top", listOf("Topaz"), guiCommands))
        // An alias named after a screen is the user's own word there.
        assertEquals(Target.Command("boot"), target("boot", emptyList(), guiCommands, aliases = mapOf("boot" to "fetch")))
        // `screen` is offered to nobody in the gui style: no prefix of it stands in an app's way.
        assertFalse("screen" in guiCommands)
    }

    /**
     * What a script may send with `launcher.run`: never a screen in the tty style, where its name opens
     * one. In the gui style the name is the user's app, alias or script, as before the screens, and
     * `screen <name>` stays refused in both.
     */
    @Test
    fun aScriptStartsNoScreenButMayUseItsNameInTheGuiStyle() {
        for (name in screens) {
            assertTrue(name, InstantRun.isSensitive(name, tty = true))
            assertFalse(name, InstantRun.isSensitive(name, tty = false))
            assertTrue(name, InstantRun.isSensitive("x", mapOf("x" to name), tty = true))
            assertFalse(name, InstantRun.isSensitive("x", mapOf("x" to name), tty = false))
            assertTrue(name, InstantRun.isSensitive("screen $name", tty = false))
        }
        // Every other sensitive command stays one in both styles, and the style is the tty one unless said.
        assertTrue(InstantRun.isSensitive("clear", tty = false))
        assertTrue(InstantRun.isSensitive("clock"))
    }

    /**
     * An alias of the user's named after a screen: it expands in the gui style, and in the tty style
     * the command wins, as for matrix. A new one can be made only where the name is no command.
     */
    @Test
    fun aliasesNamedAfterAScreen() {
        val aliases = screens.associateWith { "echo $it" }
        for (name in screens) {
            assertEquals("echo $name", Aliases.expand(name, TtyCommands.reserved(tty = false), aliases::get).trim())
            assertEquals(name, Aliases.expand(name, TtyCommands.reserved(tty = true), aliases::get).trim())
            assertEquals(Aliases.Request.Define(name, "open clock"), Aliases.parse("$name = open clock", TtyCommands.reserved(tty = false)))
            assertEquals(Aliases.Request.BuiltIn(name), Aliases.parse("$name = open clock", TtyCommands.reserved(tty = true)))
        }
        // `screen` and the other commands stay built-in in both styles.
        assertEquals(Aliases.Request.BuiltIn("screen"), Aliases.parse("screen = top", TtyCommands.reserved(tty = false)))
        assertEquals("screen", Aliases.expand("screen", TtyCommands.reserved(tty = false)) { "echo no" }.trim())
    }

    /**
     * A script or an alias named after a screen survives loading in either style: an import from the
     * folder or a backup checks [TtyCommands.ALWAYS_RESERVED]. A new or renamed script follows the style.
     */
    @Test
    fun scriptsAndAliasesNamedAfterAScreenSurviveLoading() {
        for (name in screens) {
            assertTrue(name, ScriptImport.named(name))
            assertFalse(name, name in TtyCommands.ALWAYS_RESERVED)
            assertNull(name, ScriptNames.invalid(name, TtyCommands.reserved(tty = false)) { false })
            assertTrue(name, ScriptNames.invalid(name, TtyCommands.reserved(tty = true)) { false }.orEmpty().contains("built-in command"))
        }
        assertFalse(ScriptImport.named("screen"))
        assertFalse(ScriptImport.named("shader"))
        assertEquals(CommandCatalog.RESERVED_NAMES, TtyCommands.ALWAYS_RESERVED + screens)
    }
}
