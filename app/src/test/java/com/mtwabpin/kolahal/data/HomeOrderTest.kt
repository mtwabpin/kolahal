package com.mtwabpin.kolahal.data

import com.mtwabpin.kolahal.widgets.WidgetPage
import com.mtwabpin.kolahal.widgets.WidgetPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's pages in one ordered list (`home.pages`): today's rule when it is empty, its order when it isn't. */
class HomeOrderTest {
    private val one = WidgetPages.ONE
    private val three = listOf(WidgetPage("panel"), WidgetPage("panel:2", "Travel"), WidgetPage("panel:4"))

    /**
     * Home's pager before `home.pages`, copied from MainActivity's HomePages as it was: the place of each
     * pager page as "log", "assistant" or the widgets page's index. [side] is `widgets.page`.
     */
    private fun formulas(pages: List<WidgetPage>, side: String, assistant: Boolean): List<String> {
        val shown = if (side == "off") emptyList() else pages
        val left = side == "left"
        val extra = if (assistant) 1 else 0
        val count = shown.size + 1 + extra
        val shift = if (left) extra else 0
        val assistantPlace = if (!assistant) -1 else if (left) 0 else count - 1
        val terminalPage = if (left) shown.size + shift else 0
        fun pageAt(place: Int): String = when (place) {
            terminalPage -> "log"
            assistantPlace -> "assistant"
            else -> "${if (left) shift + shown.size - 1 - place else place - 1}"
        }
        return (0 until count).map(::pageAt)
    }

    /** [order]'s pages in the same form as [formulas]. */
    private fun HomeOrder.places() = entries.map {
        when (it) {
            HomeEntry.Log -> "log"
            HomeEntry.Assistant -> "assistant"
            is HomeEntry.Widgets -> "${it.index}"
        }
    }


    private fun of(order: String, pages: List<WidgetPage>, widgets: Boolean = true, assistant: Boolean = false) =
        HomeOrder.of(HomeOrder.listed(order), if (widgets) pages else emptyList(), assistant)

    /** Home after an old `widgets.page` [side] is carried over ([Keys.migrate]), as the pager builds it. */
    private fun migrated(side: String, pages: List<WidgetPage>, assistant: Boolean): HomeOrder {
        val saved = mapOf("widgets.page" to side, "assistant.page" to if (assistant) "on" else "off")
        val out = Keys.migrate(saved)
        assertFalse(out.containsKey("widgets.page"))
        val widgets = out[Keys.WIDGETS_SHOW.key] != "off"
        return of(out[Keys.HOME_PAGES.key].orEmpty(), pages, widgets, assistant)
    }

    /** Every old side, with and without the Assistant, keeps home as it was: nobody's home flips. */
    @Test
    fun anOldSideCarriedOverKeepsThePagesWhereTheyWere() {
        for (side in listOf("left", "right", "off")) for (assistant in listOf(false, true)) for (pages in listOf(one, three)) {
            assertEquals("$side $assistant ${pages.size}", formulas(pages, side, assistant), migrated(side, pages, assistant).places())
        }
    }

    @Test
    fun anOldSideBecomesTheSwitchAndAnOrder() {
        // left was the default: nothing to keep.
        assertEquals(emptyMap<String, String>(), Keys.migrate(mapOf("widgets.page" to "left")))
        assertEquals(mapOf("home.pages" to "log,widgets"), Keys.migrate(mapOf("widgets.page" to "right")))
        assertEquals(mapOf("widgets.show" to "off"), Keys.migrate(mapOf("widgets.page" to "off")))
        assertEquals(
            mapOf("assistant.page" to "on", "widgets.show" to "off", "home.pages" to "log,assistant"),
            Keys.migrate(mapOf("widgets.page" to "off", "assistant.page" to "on")),
        )
        // An order already set stays.
        assertEquals(mapOf("home.pages" to "widgets,log"), Keys.migrate(mapOf("widgets.page" to "right", "home.pages" to "widgets,log")))
        assertTrue(HomeOrder.pagesRight(Keys.migrate(mapOf("widgets.page" to "right")).getValue("home.pages")))
    }

    @Test
    fun theDefaultOrderPlacesTheLogAndTheAssistantAtTheEnds() {
        assertEquals(listOf("assistant", "widgets:4", "widgets:2", "widgets", "log"), of("", three, assistant = true).ids)
        assertEquals(formulas(three, "left", true), of("", three, assistant = true).places())
        assertEquals(listOf("assistant", "log"), of("", three, widgets = false, assistant = true).ids)
        assertEquals(listOf("log"), of("", three, widgets = false).ids)
    }

    @Test
    fun anOrderPutsThePagesInIt() {
        val order = of("widgets:2,log,assistant,widgets,widgets:4", three, assistant = true)
        assertEquals(listOf("widgets:2", "log", "assistant", "widgets", "widgets:4"), order.ids)
        assertEquals(1, order.logPlace)
        assertEquals(2, order.assistantPlace)
        assertEquals(3, order.placeOf(0))
        assertEquals(0, order.placeOf(1))
        assertEquals(4, order.placeOf(2))
        assertEquals(HomeEntry.Widgets(1, three[1]), order.at(0))
        assertNull(order.at(5))
        assertEquals(2, order.placeOf("assistant"))
    }

    @Test
    fun anIdOfNoPageIsSkippedAndAPageNotListedGoesBeyondItsNeighbor() {
        // widgets:3 is gone, and widgets:4 and the Assistant came after the order was set: each goes
        // beyond its neighbor toward the log, as the default order has them.
        assertEquals(
            listOf("widgets", "log", "widgets:2", "widgets:4", "assistant"),
            of("widgets:3,widgets,log,widgets:2", three, assistant = true).ids,
        )
        assertEquals(
            listOf("assistant", "widgets:4", "widgets:2", "widgets", "log"),
            of("widgets:2,widgets,log", three, assistant = true).ids,
        )
        // Page 1 left out goes left of the log; the log alone is the default order.
        assertEquals(listOf("widgets", "log", "assistant"), of("log,assistant", one, assistant = true).ids)
        assertEquals(of("", three, assistant = true).ids, of("log", three, assistant = true).ids)
        // A log left out is still there, once, on the right.
        assertEquals(listOf("assistant", "widgets", "log"), of("assistant,widgets", one, assistant = true).ids)
    }

    @Test
    fun pagesTurnedOffLeaveTheOrder() {
        // widgets.show off, and the Assistant off (or a Play build): only what is on stays.
        assertEquals(listOf("assistant", "log"), of("widgets,assistant,log", three, widgets = false, assistant = true).ids)
        assertEquals(listOf("widgets", "log"), of("widgets,assistant,log", one).ids)
        assertEquals(listOf("log"), of("widgets,assistant,log", three, widgets = false).ids)
        assertEquals(-1, of("widgets,log", one).assistantPlace)
    }

    @Test
    fun theNeighborsAreNamedInTheNewOrder() {
        val order = of("widgets:2,log,assistant,widgets", three, assistant = true)
        assertEquals(
            listOf("Page 3", "Travel", "Log", "Assistant", "Page 1"),
            order.entries.map { HomeOrder.name(it, three) },
        )
        assertEquals(listOf("Log", "Widgets", "Assistant"), of("log,widgets,assistant", one, assistant = true).entries.map { HomeOrder.name(it, one) })
    }

    @Test
    fun aTypedOrderTakesIdsOnly() {
        assertEquals("widgets:2,log,assistant", HomeOrder.parse("Widgets:2, log  assistant"))
        assertEquals("", HomeOrder.parse(""))
        assertEquals("", HomeOrder.parse(" , "))
        assertNull(HomeOrder.parse("log,log"))
        assertNull(HomeOrder.parse("log,panel"))
        assertNull(HomeOrder.parse("log,widgets:1"))
        assertNull(HomeOrder.parse("log,widgets:x"))
        assertNull(HomeOrder.parse("log,settings"))
        assertEquals("widgets", HomeOrder.idOf("panel"))
        assertEquals("widgets:3", HomeOrder.idOf("panel:3"))
    }

    @Test
    fun logLinesSwipeTowardThePagesToCopy() {
        assertFalse(HomeOrder.pagesRight(""))
        assertFalse(HomeOrder.pagesRight("log"))
        assertTrue(HomeOrder.pagesRight("log,widgets"))
        assertFalse(HomeOrder.pagesRight("widgets,log"))
        assertFalse(HomeOrder.pagesRight("widgets,log,assistant"))
    }
}
