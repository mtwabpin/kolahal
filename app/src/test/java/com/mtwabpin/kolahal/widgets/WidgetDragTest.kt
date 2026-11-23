package com.mtwabpin.kolahal.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetDragTest {
    // A page 344 px wide with 8 px gaps at density 1: each column and its gap is 352 / 12 px.
    private val page = WidgetDrag(pagePx = 344, gapPx = 8, density = 1f)
    private val rtl = WidgetDrag(pagePx = 344, gapPx = 8, density = 1f, rtl = true)

    private fun widget(column: Int, span: Int, top: Int, height: Int = 88) =
        PlacedWidget(
            null,
            WidgetSource.script("w$column$top"),
            0L,
            height,
            0,
            span = span,
            column = column,
            topDp = top
        )

    /** Widths and places as the widgets page has them: half the page is 168 px, the second half starts at 176. */
    @Test
    fun columnsAreTwelfthsOfThePage() {
        assertEquals(168, page.widthPx(6))
        assertEquals(344, page.widthPx(12))
        assertEquals(176, page.startPx(6))
        assertEquals(176, page.leftPx(6, 6))
        // In a right-to-left language, column 0 is on the right.
        assertEquals(176, rtl.leftPx(0, 6))
        assertEquals(0, rtl.leftPx(6, 6))
    }

    /** A dropped widget goes to the nearest column, inside the page. */
    @Test
    fun aDropTakesTheNearestColumn() {
        assertEquals(0, page.dropColumn(10f, 6))
        assertEquals(1, page.dropColumn(20f, 6))
        assertEquals(6, page.dropColumn(170f, 6))
        // Past either edge, it stays inside.
        assertEquals(6, page.dropColumn(300f, 6))
        assertEquals(0, page.dropColumn(-80f, 6))
        assertEquals(0, page.dropColumn(40f, 12))
        // Right to left: the left half is columns 6 and up.
        assertEquals(6, rtl.dropColumn(0f, 6))
        assertEquals(0, rtl.dropColumn(176f, 6))
    }

    /** Tops snap to 8 dp steps and stay on the page; density turns pixels into dp. */
    @Test
    fun aDropTakesTheNearest8DpStep() {
        assertEquals(96, page.dropTopDp(99f))
        assertEquals(104, page.dropTopDp(101f))
        assertEquals(0, page.dropTopDp(-30f))
        assertEquals(96, WidgetDrag(344, 8, density = 2.5f).dropTopDp(240f))
    }

    /**
     * Within 16 dp of another widget's top, or of its bottom and the gap, a drop goes onto that line,
     * off the 8 dp steps too; the nearest line wins. Further away, it goes to the nearest step.
     */
    @Test
    fun aDropNearANeighbourLandsOnItsLine() {
        val lines = setOf(93, 117)
        assertEquals(93, page.dropTopDp(82f, lines))
        assertEquals(93, page.dropTopDp(104f, lines))
        assertEquals(117, page.dropTopDp(106f, lines))
        assertEquals(117, page.dropTopDp(133f, lines))
        assertEquals(56, page.dropTopDp(58f, lines))
        assertEquals(144, page.dropTopDp(141f, lines))
        assertEquals(93, WidgetDrag(344, 8, density = 2.5f).dropTopDp(240f, lines))
    }

    /** Dragging the end edge changes the width by whole columns, at least the smallest, never past the end. */
    @Test
    fun anEdgeDragResizesByColumns() {
        assertEquals(6, page.resizeSpan(0, 6, 10f, 3))
        assertEquals(7, page.resizeSpan(0, 6, 20f, 3))
        assertEquals(3, page.resizeSpan(0, 6, -400f, 3))
        assertEquals(12, page.resizeSpan(0, 6, 1000f, 3))
        // At column 6, the page ends 6 columns on.
        assertEquals(6, page.resizeSpan(6, 3, 1000f, 3))
        // Right to left, the end edge is on the left: dragging left makes it wider.
        assertEquals(7, rtl.resizeSpan(0, 6, -20f, 3))
    }

    /** Dragging the bottom edge changes the height in 8 dp steps. */
    @Test
    fun aBottomDragResizesBy8DpSteps() {
        assertEquals(88, page.resizeHeightDp(88, 3f))
        assertEquals(96, page.resizeHeightDp(88, 5f))
        assertEquals(64, page.resizeHeightDp(88, -24f))
    }

    /** The widget under a finger, and the handles of a chosen one. */
    @Test
    fun findsWidgetsAndHandles() {
        val left = widget(0, 6, 0)
        val right = widget(6, 6, 0)
        val all = listOf(left, right)
        assertEquals(left, page.widgetAt(all, 10f, 10f))
        assertEquals(right, page.widgetAt(all, 200f, 80f))
        // The gap between them and below them is no widget.
        assertNull(page.widgetAt(all, 172f, 10f))
        assertNull(page.widgetAt(all, 10f, 90f))
        // Handles: the middle of the end edge, the middle of the bottom, and the corner.
        assertEquals(WidgetDrag.Handle.END, page.handleAt(left, 170f, 44f, 48f))
        assertEquals(WidgetDrag.Handle.BOTTOM, page.handleAt(left, 84f, 90f, 48f))
        assertEquals(WidgetDrag.Handle.CORNER, page.handleAt(left, 165f, 85f, 48f))
        assertNull(page.handleAt(left, 40f, 40f, 48f))
        // Right to left, the end edge is the left one.
        assertEquals(WidgetDrag.Handle.END, rtl.handleAt(left, 176f, 44f, 48f))
    }

    /** Held near the top or bottom, the page scrolls, faster nearer the edge. */
    @Test
    fun nearAnEdgeThePageScrolls() {
        assertEquals(0f, WidgetDrag.edgeScroll(500f, 100f, 1000f, 50f, 10f), 0f)
        assertEquals(-10f, WidgetDrag.edgeScroll(100f, 100f, 1000f, 50f, 10f), 0.01f)
        assertEquals(-5f, WidgetDrag.edgeScroll(125f, 100f, 1000f, 50f, 10f), 0.01f)
        assertEquals(5f, WidgetDrag.edgeScroll(975f, 100f, 1000f, 50f, 10f), 0.01f)
        assertEquals(10f, WidgetDrag.edgeScroll(1200f, 100f, 1000f, 50f, 10f), 0.01f)
        // A view too short for two edges doesn't scroll.
        assertEquals(0f, WidgetDrag.edgeScroll(100f, 100f, 150f, 50f, 10f), 0f)
    }
}
