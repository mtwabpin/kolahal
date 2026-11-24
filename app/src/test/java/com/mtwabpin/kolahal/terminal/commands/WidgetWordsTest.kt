package com.mtwabpin.kolahal.terminal.commands

import com.mtwabpin.kolahal.commands.CommandCatalog
import com.mtwabpin.kolahal.terminal.commands.WidgetWords.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** `widget` and `widgets` follow the rule of `script` and `scripts` ([WidgetWords]). */
class WidgetWordsTest {
    /** `widget add`, `widget ls` and `widget page`, and the same under `widgets`. */
    @Test
    fun bothWordsTakeTheThreeSubcommands() {
        for (name in listOf("widget", "widgets")) {
            assertEquals(name, Action.ADD, WidgetWords.of(name, listOf("add")))
            assertEquals(name, Action.LIST, WidgetWords.of(name, listOf("ls")))
            assertEquals(name, Action.LIST, WidgetWords.of(name, listOf("list")))
            assertEquals(name, Action.PAGE, WidgetWords.of(name, listOf("page")))
            assertNull(name, WidgetWords.of(name, listOf("bogus")))
        }
    }

    /** The plural alone opens Arrange widgets; the singular alone lists, as `script` and `feed` alone do. */
    @Test
    fun aloneThePluralOpensTheScreenAndTheSingularLists() {
        assertEquals(Action.ARRANGE, WidgetWords.of("widgets", emptyList()))
        assertEquals(Action.LIST, WidgetWords.of("widget", emptyList()))
    }

    /** Help teaches the singular's subcommands, and the plural as the screen alone, as for scripts. */
    @Test
    fun helpTeachesTheSingular() {
        val usages = CommandCatalog.COMMAND_USAGES
        assertEquals("widget [add|ls|page]", usages["widget"])
        assertEquals("widgets", usages["widgets"])
        assertEquals("scripts", usages["scripts"])
        val uses = CommandCatalog.COMMANDS.single { it.name == "widget" }.uses.map { it.first }
        assertEquals(listOf("widget add", "widget ls", "widget page"), uses)
        assertEquals(emptyList<Pair<String, String>>(), CommandCatalog.COMMANDS.single { it.name == "widgets" }.uses)
    }
}
