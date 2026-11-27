package com.mtwabpin.kolahal

import com.mtwabpin.kolahal.data.HomeOrder
import com.mtwabpin.kolahal.widgets.WidgetPage
import org.junit.Assert.assertEquals
import org.junit.Test

/** The page home's pager opens on when it is laid out again ([homeStartPlace]): never a place outside the pager. */
class HomeStartPlaceTest {
    private val three = listOf(WidgetPage("panel"), WidgetPage("panel:2", "Travel"), WidgetPage("panel:4"))

    // The default order: the Assistant, widgets pages 3, 2 and 1, then the log.
    private val all = HomeOrder.of(emptyList(), three, assistant = true)

    @Test
    fun theKeptPageWhereverItIsNow() {
        assertEquals(listOf("assistant", "widgets:4", "widgets:2", "widgets", "log"), all.ids)
        assertEquals(0, homeStartPlace("assistant", all, homePage = 4))
        assertEquals(2, homeStartPlace("widgets:2", all, homePage = 4))
        assertEquals(4, homeStartPlace("log", all, homePage = 3))
        // A new order moves it: it opens where the page is now.
        val moved = HomeOrder.of(listOf("log", "widgets:2", "assistant"), three, assistant = true)
        assertEquals(moved.placeOf("widgets:2"), homeStartPlace("widgets:2", moved, homePage = moved.logPlace))
        assertEquals(moved.placeOf("assistant"), homeStartPlace("assistant", moved, homePage = moved.logPlace))
    }

    @Test
    fun nothingKeptOpensHomesFirstPage() {
        assertEquals(4, homeStartPlace(null, all, homePage = 4))
        assertEquals(3, homeStartPlace(null, all, homePage = 3))
    }

    @Test
    fun aPageGoneOpensTheLog() {
        // Widgets pages turned off while a screen was open: only the Assistant and the log are left.
        val noWidgets = HomeOrder.of(emptyList(), emptyList(), assistant = true)
        assertEquals(1, noWidgets.logPlace)
        assertEquals(1, homeStartPlace("widgets", noWidgets, homePage = 1))
        assertEquals(1, homeStartPlace("widgets:4", noWidgets, homePage = 1))
        // The Assistant's page stays: home opens on it again.
        assertEquals(0, homeStartPlace("assistant", noWidgets, homePage = 1))
        // A widgets page removed, the others kept.
        val two = HomeOrder.of(emptyList(), three.take(2), assistant = false)
        assertEquals(two.logPlace, homeStartPlace("widgets:4", two, homePage = two.logPlace))
        assertEquals(two.logPlace, homeStartPlace("assistant", two, homePage = two.logPlace))
    }

    @Test
    fun noPagesButTheLog() {
        val log = HomeOrder.of(emptyList(), emptyList(), assistant = false)
        assertEquals(0, homeStartPlace("assistant", log, homePage = 0))
        assertEquals(0, homeStartPlace("widgets", log, homePage = 0))
        assertEquals(0, homeStartPlace(null, log, homePage = 0))
        // Home's first page asked for as widgets page 1 before there is one: the log, never -1.
        assertEquals(0, homeStartPlace(null, log, homePage = -1))
        // No page at all: place 0, never negative.
        assertEquals(0, homeStartPlace("log", HomeOrder(emptyList()), homePage = -1))
    }
}
