package com.mtwabpin.kolahal.terminal.commands

import com.mtwabpin.kolahal.commands.CommandCatalog
import com.mtwabpin.kolahal.terminal.commands.WidgetWords.Action
import com.mtwabpin.kolahal.terminal.commands.WidgetWords.PageTarget
import com.mtwabpin.kolahal.widgets.WidgetLocation
import com.mtwabpin.kolahal.widgets.WidgetPage
import com.mtwabpin.kolahal.widgets.WidgetPages
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
        assertEquals("widget [add|ls|page [<page>]]", usages["widget"])
        assertEquals("widgets", usages["widgets"])
        assertEquals("scripts", usages["scripts"])
        val uses = CommandCatalog.COMMANDS.single { it.name == "widget" }.uses.map { it.first }
        assertEquals(listOf("widget add", "widget ls", "widget page", "widget page 2"), uses)
        assertEquals(emptyList<Pair<String, String>>(), CommandCatalog.COMMANDS.single { it.name == "widgets" }.uses)
    }

    /** `widget page 2` and `widget page work` find the page; with one page, or none that fits, it says what there is. */
    @Test
    fun widgetPageFindsAPageOrSaysWhichThereAre() {
        val pages = listOf(
            WidgetPage(WidgetLocation.PANEL, "Daily"),
            WidgetPage(WidgetPages.locationOf(2), "Work"),
            WidgetPage(WidgetPages.locationOf(3)),
        )
        assertEquals(PageTarget.Found(2), WidgetWords.page(pages, "2"))
        assertEquals(PageTarget.Found(2), WidgetWords.page(pages, "work"))
        assertEquals(PageTarget.Found(3), WidgetWords.page(pages, "page 3"))
        assertEquals(
            PageTarget.Missing("No widgets page 4\nPages: 1 Daily, 2 Work, 3 Page 3"),
            WidgetWords.page(pages, "4")
        )
        assertEquals(
            PageTarget.Missing("No widgets page called 'travel'\nPages: 1 Daily, 2 Work, 3 Page 3"),
            WidgetWords.page(pages, "travel")
        )
        assertEquals(
            PageTarget.Missing("'pa' names more than one page\nPages: 1 Daily, 2 Work, 3 Page 3").message.lines().first(),
            (WidgetWords.page(listOf(pages[0], pages[1], pages[2], WidgetPage(WidgetPages.locationOf(4))), "pa") as PageTarget.Missing).message.lines().first()
        )
        assertEquals(
            PageTarget.Missing("There is one widgets page\n'widget page' turns to it", "widget page"),
            WidgetWords.page(WidgetPages.ONE, "2")
        )
        assertEquals(PageTarget.Found(1), WidgetWords.page(WidgetPages.ONE, "1"))
    }
}
