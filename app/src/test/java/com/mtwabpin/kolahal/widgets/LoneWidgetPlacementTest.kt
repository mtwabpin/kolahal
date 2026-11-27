package com.mtwabpin.kolahal.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A widget dropped anywhere down to the foot of the page ([WidgetPlacement.lowestDrop]), with empty
 * space above it: the bound in Arrange widgets, and the gap kept by everything but Close gaps.
 */
class LoneWidgetPlacementTest {
    private fun script(
        name: String,
        span: Int = 6,
        height: Int = 88,
        column: Int = -1,
        top: Int = -1,
        location: String = WidgetLocation.PANEL,
    ) = PlacedWidget(
        null,
        WidgetSource.script(name),
        0L,
        height,
        0,
        location = location,
        span = span,
        column = column,
        topDp = top
    )

    private fun places(all: List<PlacedWidget>) =
        all.associate { it.source!!.removePrefix("script:") to (it.column to it.topDp) }

    /** How tall the page shows without scrolling, in these tests. */
    private val shown = 640

    /** A lone widget goes down to the last step where all of it shows: not only just under where it was. */
    @Test
    fun aLoneWidgetReachesTheFootOfThePage() {
        val a = script("a", height = 88, column = 0, top = 0)
        val lowest = WidgetPlacement.lowestDrop(listOf(a), a.key, a.heightDp, shown)
        assertEquals(552, lowest)
        assertTrue(lowest + a.heightDp <= shown)
        assertTrue(lowest + a.heightDp + WidgetPlacement.STEP_DP > shown)
        // A drag far below the page lands there, and the move keeps it there.
        val g = WidgetDrag(1080, 21, 2.625f)
        val dropped = g.dropTopDp(g.px(2000)).coerceAtMost(lowest)
        assertEquals(552, dropped)
        assertEquals(0 to 552, places(WidgetPlacement.moveTo(listOf(a), a.key, 0, dropped))["a"])
    }

    /** The foot is on an 8 dp step, inside what shows, whatever the screen's height. */
    @Test
    fun theFootIsOnAStep() {
        for (h in listOf(645, 701, 799, 1000)) {
            val lowest = WidgetPlacement.lowestDrop(emptyList(), "k", 100, h)
            assertEquals(0, lowest % WidgetPlacement.STEP_DP)
            assertTrue("$h: $lowest", lowest + 100 <= h && lowest + 100 + WidgetPlacement.STEP_DP > h)
        }
    }

    /** Rows under the last widget are offered: to the foot, and one gap under the lowest other when that is further down. */
    @Test
    fun rowsBelowTheLastWidgetAreOffered() {
        val a = script("a", span = 12, height = 200, column = 0, top = 0)
        val b = script("b", height = 88, column = 0, top = 208)
        // A few widgets: b goes down to the foot, well under a.
        assertEquals(552, WidgetPlacement.lowestDrop(listOf(a, b), b.key, 88, shown))
        // A full page: one gap under the lowest other widget, so the page grows by scrolling.
        val tall = a.copy(heightDp = 900)
        assertEquals(908, WidgetPlacement.lowestDrop(listOf(tall, b), b.key, 88, shown))
        // Before the page was ever laid out (nothing shown), as before: under the others.
        assertEquals(208, WidgetPlacement.lowestDrop(listOf(a, b), b.key, 88, 0))
        // Its own place doesn't hold it up.
        val low = b.copy(topDp = 2000)
        assertEquals(552, WidgetPlacement.lowestDrop(listOf(a, low), low.key, 88, shown))
    }

    /** A widget taller than what shows still has a place: the top of the page, or under the others. */
    @Test
    fun aWidgetTallerThanThePageGoesAtTheTop() {
        assertEquals(WidgetLayout.GAP_DP, WidgetPlacement.lowestDrop(emptyList(), "k", 900, shown))
    }

    /** A placement with empty rows above survives the stored line, a restart and the backup. */
    @Test
    fun aGapSurvivesTheStoredLineAndTheBackup() {
        val all = listOf(
            script("a", column = 6, top = 552),
            script("b", column = 0, top = 1600, location = "panel:2"),
        )
        val text = WidgetCodec.encode(all)
        assertTrue(text.lines().contains("-|script:a|0|88|0|panel|6|plain|6|552"))
        assertEquals(all, WidgetCodec.decode(text))
        val start = WidgetCodec.start(text)
        assertEquals(all, start.widgets)
        assertFalse("nothing to save again", start.changed)
        assertEquals(all, WidgetCodec.fromExport(WidgetCodec.export(all)))
        // Imported onto an empty phone, each keeps its place.
        val (_, imported) = WidgetPages.imported(
            listOf(WidgetPage(WidgetLocation.PANEL)),
            emptyList(),
            listOf(WidgetPage(WidgetLocation.PANEL), WidgetPage("panel:2")),
            WidgetCodec.fromExport(WidgetCodec.export(all)),
        )
        assertEquals(mapOf("a" to (6 to 552), "b" to (0 to 1600)), places(imported))
    }

    /**
     * A top further down than this version reads ([WidgetPlacement.MAX_TOP_DP]) is not lost: the
     * widget gets a place again at start, as a new one does.
     */
    @Test
    fun aTopTooFarDownGetsAPlaceAgain() {
        val start = WidgetCodec.start("-|script:a|0|88|0|panel|6|plain|0|${WidgetPlacement.MAX_TOP_DP + 8}")
        val a = start.widgets.single()
        assertTrue(a.positioned)
        assertEquals(0 to 0, a.column to a.topDp)
        assertTrue(start.changed)
    }

    /** Close gaps still closes them. */
    @Test
    fun closeGapsClosesTheGap() {
        val all = listOf(script("a", column = 6, top = 552))
        assertEquals(6 to 0, places(WidgetPlacement.closeGaps(all))["a"])
    }

    /** Adding, removing, resizing or moving other widgets never pulls a widget up. */
    @Test
    fun nothingElsePullsItUp() {
        val a = script("a", span = 12, column = 0, top = 552)
        val b = script("b", span = 6, column = 0, top = 0)
        var all = listOf(a, b)
        // Added: the new one goes into the space above.
        all = WidgetPlacement.add(all, script("c", span = 6))
        assertEquals(0 to 552, places(all)["a"])
        assertEquals(6 to 0, places(all)["c"])
        // Removed.
        all = all.filterNot { it.key == b.key }
        assertEquals(0 to 552, places(WidgetPlacement.renumber(all))["a"])
        // Another resized, short of it, and moved: it stays.
        all = WidgetPlacement.resize(all, script("c").key, 6, 200)
        all = WidgetPlacement.moveTo(all, script("c").key, 0, 96)
        assertEquals(0 to 552, places(all)["a"])
        // One coming from another page goes into the space above.
        all = WidgetPlacement.moveToPage(all + script("d", column = 0, top = 0, location = "panel:2"), script("d").key, WidgetLocation.PANEL)
        assertEquals(0 to 552, places(all)["a"])
        // A backup's widget with no place of its own goes into the first free space, not over it.
        all = WidgetPlacement.placeImported(all, WidgetCodec.mergePending(all, listOf(script("e", span = 6))))
        assertEquals(0 to 552, places(all)["a"])
        for (x in all) for (y in all) assertFalse("${x.provider} and ${y.provider}", WidgetPlacement.overlap(x, y))
        // Moved further down, then Undo: back at its place, the gap above it kept.
        val before = all
        val up = WidgetPlacement.moveTo(all, a.key, 0, 800)
        assertEquals(0 to 552, places(ArrangeUndo.restored(up, before))["a"])
    }

    /** A screen reader's Move down goes into the empty rows under the last widget too. */
    @Test
    fun moveDownGoesIntoEmptyRows() {
        val all = listOf(script("a", column = 0, top = 0))
        assertEquals(0 to 3 * 96, places(WidgetPlacement.nudge(all, all[0].key, 0, 3, 88))["a"])
    }
}
