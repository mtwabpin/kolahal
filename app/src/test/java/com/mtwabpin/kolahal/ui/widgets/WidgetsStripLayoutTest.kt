package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.data.HomeOrder
import com.mtwabpin.kolahal.ui.widgets.WidgetsStripLayout.Case
import com.mtwabpin.kolahal.ui.widgets.WidgetsStripLayout.Places
import com.mtwabpin.kolahal.ui.widgets.WidgetsStripLayout.Span
import com.mtwabpin.kolahal.widgets.WidgetPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The strip at the top of the widgets pages: where it goes, where its name and marks sit, and over which pages it shows. */
class WidgetsStripLayoutTest {
    /** 20 dp at 2.75 pixels a dp. */
    private val line = 55

    @Test
    fun whichCaseTheInsetsGive() {
        // Status bar shown, with or without a camera: under the bar.
        assertEquals(Case.BELOW_BAR, WidgetsStripLayout.case(barTop = 110, cutoutTop = 110))
        assertEquals(Case.BELOW_BAR, WidgetsStripLayout.case(barTop = 110, cutoutTop = 0))
        // Hidden, no camera: the top edge.
        assertEquals(Case.BELOW_BAR, WidgetsStripLayout.case(barTop = 0, cutoutTop = 0))
        // Hidden, with the camera's strip: beside the camera.
        assertEquals(Case.CAMERA, WidgetsStripLayout.case(barTop = 0, cutoutTop = 110))
    }

    /** With a 40 dp status bar (110 px) and the grid's 4 dp, the first card is 64, 24 and 44 dp down, as drafted. */
    @Test
    fun theStripsHeightInEachCase() {
        val barShown = WidgetsStripLayout.span(barTop = 110, cutoutTop = 110, line = line)
        assertEquals(Span(Case.BELOW_BAR, 110, 55), barShown)
        assertEquals(165, barShown.bottom) // 60 dp, then 4 dp of grid: 64
        val hiddenNoCamera = WidgetsStripLayout.span(barTop = 0, cutoutTop = 0, line = line)
        assertEquals(Span(Case.BELOW_BAR, 0, 55), hiddenNoCamera)
        assertEquals(55, hiddenNoCamera.bottom) // 20 dp, then 4: 24
        val hiddenCamera = WidgetsStripLayout.span(barTop = 0, cutoutTop = 110, line = line)
        assertEquals(Span(Case.CAMERA, 0, 110), hiddenCamera)
        assertEquals(110, hiddenCamera.bottom) // 40 dp, then 4: 44
        // A camera strip lower than the line: the line's height.
        assertEquals(Span(Case.CAMERA, 0, 55), WidgetsStripLayout.span(barTop = 0, cutoutTop = 40, line = line))
        // A cutout lower than the bar: under the cutout.
        assertEquals(Span(Case.BELOW_BAR, 130, 55), WidgetsStripLayout.span(barTop = 110, cutoutTop = 130, line = line))
    }

    @Test
    fun aCenteredCameraSplitsNameAndMarks() {
        val zones = listOf(10..474, 526..989)
        assertEquals(Places(192, 100, 728), WidgetsStripLayout.place(1000, zones, 100, 60, 20, rtl = false))
        // Right to left: the name on the right, the marks on the left.
        assertEquals(Places(708, 100, 213), WidgetsStripLayout.place(1000, zones, 100, 60, 20, rtl = true))
    }

    @Test
    fun aCornerCameraOrNoneKeepsThemTogether() {
        // A camera at the left corner: both, centered on the free side.
        assertEquals(Places(428, 100, 548), WidgetsStripLayout.place(1000, listOf(46..989), 100, 60, 20, rtl = false))
        // A camera at the right corner.
        assertEquals(Places(392, 100, 512), WidgetsStripLayout.place(1000, listOf(10..953), 100, 60, 20, rtl = false))
        // No camera: centered across the line.
        assertEquals(Places(410, 100, 530), WidgetsStripLayout.place(1000, listOf(10..989), 100, 60, 20, rtl = false))
        // Right to left: the marks before the name, from the left.
        assertEquals(Places(490, 100, 410), WidgetsStripLayout.place(1000, listOf(10..989), 100, 60, 20, rtl = true))
    }

    @Test
    fun aSideTooNarrowPutsBothOnTheOther() {
        // A camera left of the middle: the name doesn't fit before it.
        assertEquals(Places(455, 100, 575), WidgetsStripLayout.place(1000, listOf(10..59, 100..989), 100, 60, 20, rtl = false))
        // A name too long for the line is cut; the marks keep their width.
        assertEquals(Places(10, 120, 150), WidgetsStripLayout.place(1000, listOf(10..209), 300, 60, 20, rtl = false))
    }

    /** Default order with the Assistant and two pages: Assistant, page 2, page 1, log. */
    private val order = HomeOrder.of(emptyList(), listOf(WidgetPage("panel"), WidgetPage("panel:2")), assistant = true)

    @Test
    fun whichPagesShowIt() {
        assertTrue(WidgetsStripLayout.isWidgets(order, 1))
        assertTrue(WidgetsStripLayout.isWidgets(order, 2))
        assertFalse(WidgetsStripLayout.isWidgets(order, 0))
        assertFalse(WidgetsStripLayout.isWidgets(order, 3))
        // Resting on a page.
        assertEquals(2, WidgetsStripLayout.shownPlace(order, 2f))
        assertNull(WidgetsStripLayout.shownPlace(order, 3f))
        assertNull(WidgetsStripLayout.shownPlace(order, 0f))
        // Between two widgets pages, it switches at the halfway point.
        assertEquals(1, WidgetsStripLayout.shownPlace(order, 1.4f))
        assertEquals(2, WidgetsStripLayout.shownPlace(order, 1.6f))
        // Between a widgets page and the log or the Assistant, it names the widgets page.
        assertEquals(2, WidgetsStripLayout.shownPlace(order, 2.7f))
        assertEquals(1, WidgetsStripLayout.shownPlace(order, 0.3f))
    }

    @Test
    fun itFadesOverTheOtherPages() {
        assertEquals(1f, WidgetsStripLayout.alpha(order, 2f), 0.001f)
        assertEquals(1f, WidgetsStripLayout.alpha(order, 1.5f), 0.001f)
        assertEquals(0f, WidgetsStripLayout.alpha(order, 3f), 0.001f)
        assertEquals(0f, WidgetsStripLayout.alpha(order, 0f), 0.001f)
        assertEquals(0.75f, WidgetsStripLayout.alpha(order, 2.25f), 0.001f)
        assertEquals(0.5f, WidgetsStripLayout.alpha(order, 0.5f), 0.001f)
    }

    @Test
    fun aScreenReaderHearsTheNameAndThePlace() {
        assertEquals("Daily, page 3 of 4", WidgetsStripLayout.description("Daily", 2, 4))
    }
}
