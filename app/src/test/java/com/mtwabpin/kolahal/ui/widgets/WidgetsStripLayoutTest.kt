package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.data.HomeOrder
import com.mtwabpin.kolahal.ui.home.HeaderLayout
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

    /*
     * A line 1000 px wide at 2.75 px a dp: the cards' 8 dp margin is 22 px, the gap before the marks
     * 22 px. The zones are what lineZones gives: 22 px in at each end, 4 dp (11 px) clear of a camera.
     * The name is 100 px wide, the marks 60.
     */
    private fun place(zones: List<IntRange>, name: Int = 100, rtl: Boolean = false, minName: Int = name) =
        WidgetsStripLayout.place(1000, zones, name, 60, 22, rtl, minName)

    @Test
    fun withNoCameraTheyGoToTheCorners() {
        val zones = listOf(22..977)
        // The name's start and the marks' end in line with the cards' sides.
        assertEquals(Places(22, 100, 918), place(zones))
        // Right to left: the name at the right corner, the marks at the left.
        assertEquals(Places(878, 100, 22), place(zones, rtl = true))
    }

    @Test
    fun aCenteredHoleChangesNothing() {
        val zones = listOf(22..474, 526..977)
        assertEquals(Places(22, 100, 918), place(zones))
        assertEquals(Places(878, 100, 22), place(zones, rtl = true))
    }

    @Test
    fun anOffCenterHoleChangesNothing() {
        val zones = listOf(22..299, 351..977)
        assertEquals(Places(22, 100, 918), place(zones))
        assertEquals(Places(878, 100, 22), place(zones, rtl = true))
    }

    @Test
    fun aCameraAtTheStartCornerHasTheNameJustInsideIt() {
        // A camera at the left, 0 to 48: the name 11 px past it, the marks at the right corner.
        assertEquals(Places(60, 100, 918), place(listOf(60..977)))
        // Right to left the start is the right: a camera there, 951 to 999.
        assertEquals(Places(840, 100, 22), place(listOf(22..939), rtl = true))
    }

    @Test
    fun aCameraAtTheEndCornerHasTheMarksJustInsideIt() {
        // A camera at the right, 951 to 999: the marks end 11 px before it.
        assertEquals(Places(22, 100, 880), place(listOf(22..939)))
        // Right to left the end is the left: a camera there, 0 to 48.
        assertEquals(Places(878, 100, 60), place(listOf(60..977), rtl = true))
    }

    @Test
    fun roundedCornersLargerThanTheCardsMarginPushThemIn() {
        // Corners that hide 40 px: the line keeps 11 px past them, more than the cards' 22.
        val zones = HeaderLayout.zones(1000, 22, emptyList(), 11, cornerLeft = 40, cornerRight = 40)
        assertEquals(listOf(51..948), zones)
        assertEquals(Places(51, 100, 889), place(zones))
        assertEquals(Places(849, 100, 51), place(zones, rtl = true))
        // Corners that hide 5 px: the cards' margin is the larger.
        assertEquals(listOf(22..977), HeaderLayout.zones(1000, 22, emptyList(), 11, cornerLeft = 5, cornerRight = 5))
    }

    @Test
    fun aNameTooLongIsShortened() {
        // One zone, 22 to 309: the marks end at 310, the name stops 22 px before them.
        assertEquals(Places(22, 206, 250), place(listOf(22..309), name = 400, minName = 50))
        assertEquals(Places(772, 206, 690), WidgetsStripLayout.place(1000, listOf(690..977), 400, 60, 22, rtl = true, minName = 50))
        // A centered hole: cut at the hole, in its own zone.
        assertEquals(Places(22, 453, 918), place(listOf(22..474, 526..977), name = 600, minName = 50))
    }

    @Test
    fun aNameThatCantFitIsLeftOut() {
        // 26 px left for a name that needs at least 50: no name, the marks still at the end corner.
        assertEquals(Places(22, 0, 70), place(listOf(22..129), name = 400, minName = 50))
        assertEquals(Places(978, 0, 870), place(listOf(870..977), name = 400, rtl = true, minName = 50))
        // A short name is whole or nothing.
        assertEquals(Places(22, 0, 70), place(listOf(22..129), name = 40))
    }

    @Test
    fun aNameWithNoRoomBeforeAHoleGoesPastIt() {
        // A hole near the start leaves 38 px before it: the name goes just past the hole, whole.
        assertEquals(Places(100, 100, 918), place(listOf(22..59, 100..977)))
        // Marks too wide for the zone past a hole at the end corner: they go before it.
        assertEquals(Places(22, 100, 415), place(listOf(22..474, 526..550)))
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
