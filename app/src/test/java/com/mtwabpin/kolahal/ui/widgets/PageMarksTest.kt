package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.data.HomeOrder
import com.mtwabpin.kolahal.widgets.WidgetPage
import org.junit.Assert.assertEquals
import org.junit.Test

/** The page dots on a widgets page: one mark per page home shows, in the pager's order ([pageMarks]). */
class PageMarksTest {
    private val two = listOf(WidgetPage("panel"), WidgetPage("panel:2"))
    private val three = listOf(WidgetPage("panel"), WidgetPage("panel:2", "Travel"), WidgetPage("panel:4"))

    private fun order(ids: String, pages: List<WidgetPage>, assistant: Boolean) =
        HomeOrder.of(HomeOrder.listed(ids), pages, assistant)

    /** The marks as "L", "A" or "W", the current one in brackets. */
    private fun marks(order: HomeOrder, place: Int) = pageMarks(order, place).joinToString(" ") {
        val k = when (it.kind) {
            PageKind.LOG -> "L"
            PageKind.ASSISTANT -> "A"
            PageKind.WIDGETS -> "W"
        }
        if (it.current) "[$k]" else k
    }

    /** The default order: the Assistant outermost, page 2 then page 1 left of the log on the right. */
    @Test
    fun theDefaultOrder() {
        val o = order("", two, assistant = true)
        assertEquals("A W [W] L", marks(o, o.placeOf(0)))
        assertEquals("A [W] W L", marks(o, o.placeOf(1)))
    }

    /** A changed order: page 1, the Assistant, the log, page 2 (as HomePageOrderTest sets it). */
    @Test
    fun aChangedOrder() {
        val o = order("widgets,assistant,log,widgets:2", two, assistant = true)
        assertEquals("[W] A L W", marks(o, o.placeOf(0)))
        assertEquals("W A L [W]", marks(o, o.placeOf(1)))
    }

    /** The log in the middle of the widgets pages. */
    @Test
    fun theLogInTheMiddle() {
        val o = order("widgets:2,log,widgets", two, assistant = false)
        assertEquals("W L [W]", marks(o, o.placeOf(0)))
        assertEquals("[W] L W", marks(o, o.placeOf(1)))
    }

    /** Without the Assistant (the Play build, or assistant.page off) it never shows, even when listed. */
    @Test
    fun noAssistant() {
        val o = order("assistant,log,widgets,widgets:2", two, assistant = false)
        assertEquals("L [W] W", marks(o, o.placeOf(0)))
        assertEquals(3, pageMarks(o, 1).size)
    }

    /** Several widgets pages, the log first: one dot each, the current one alone marked. */
    @Test
    fun severalWidgetsPages() {
        val o = order("log,widgets,widgets:2,widgets:4", three, assistant = false)
        assertEquals(listOf(PageKind.LOG, PageKind.WIDGETS, PageKind.WIDGETS, PageKind.WIDGETS), pageMarks(o, 0).map { it.kind })
        assertEquals("L W W [W]", marks(o, o.placeOf(2)))
        assertEquals(1, pageMarks(o, o.placeOf(1)).count { it.current })
    }

    /** The log alone: one mark, current. */
    @Test
    fun theLogAlone() {
        val o = order("", emptyList(), assistant = false)
        assertEquals("[L]", marks(o, 0))
    }
}
