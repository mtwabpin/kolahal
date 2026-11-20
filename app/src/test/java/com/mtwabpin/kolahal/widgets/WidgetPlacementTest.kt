package com.mtwabpin.kolahal.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPlacementTest {
    private fun script(name: String, span: Int = 6, height: Int = 88, column: Int = -1, top: Int = -1, order: Int = 0) =
        PlacedWidget(null, WidgetSource.script(name), 0L, height, order, span = span, column = column, topDp = top)

    private fun places(all: List<PlacedWidget>) = all.associate { it.source!!.removePrefix("script:") to (it.column to it.topDp) }

    @Test fun looksAndPlacesRoundTrip() {
        val all = listOf(script("a", column = 6, top = 96).copy(look = WidgetLook.SHAPE), script("b", column = 0, top = 0))
        val text = WidgetCodec.encode(all)
        assertTrue(text.lines().contains("-|script:a|0|88|0|panel|6|shape|6|96"))
        assertEquals(all, WidgetCodec.decode(text))
        assertEquals(all, WidgetCodec.fromExport(WidgetCodec.export(all)))
    }

    /** A line from 0.5.14 has neither: Plain, and no place yet. An unknown look, or half a place, reads as none. */
    @Test fun oldAndOddLinesReadAsPlainAndUnplaced() {
        val old = WidgetCodec.decode("-|script:a|0|88|0|panel|6").single()
        assertEquals(WidgetLook.PLAIN, old.look)
        assertFalse(old.positioned)
        val odd = WidgetCodec.decode("-|script:a|0|88|0|panel|6|glass|3|").single()
        assertEquals(WidgetLook.PLAIN, odd.look)
        assertFalse(odd.positioned)
        // A column past the end of the page comes back inside it.
        assertEquals(6, WidgetCodec.decode("-|script:a|0|88|0|panel|6|bold|11|40").single().column)
        assertEquals(WidgetLook.BOLD, WidgetCodec.decode("-|script:a|0|88|0|panel|6|BOLD|0|0").single().look)
    }

    /** An app's widget draws itself: a stored look is ignored. */
    @Test fun anAppsWidgetIsAlwaysPlain() {
        assertEquals(WidgetLook.PLAIN, WidgetCodec.decode("5|com.a/.W|0|88|0|panel|6|bold|0|0").single().look)
    }

    /** At the first start of this version, each widget gets the place the old page gave it. */
    @Test fun anOldPageKeepsItsLayout() {
        val text = listOf(
            "5|com.a/.W|0|120|0|panel|12",
            "-|script:a|0|96|1|panel|6",
            "-|script:b|0|88|2|panel|6",
            "-|script:c|0|72|3|panel|8",
        ).joinToString("\n")
        val start = WidgetCodec.start(text)
        assertTrue(start.changed)
        val got = start.widgets.associate { it.provider to (it.column to it.topDp) }
        assertEquals(0 to 0, got["com.a/.W"])
        assertEquals(0 to 128, got["script:a"])
        assertEquals(6 to 128, got["script:b"])
        // The row under is as far down as the tallest widget of the row above, and the gap.
        assertEquals(0 to 232, got["script:c"])
        // Saved again, it starts as it is.
        assertFalse(WidgetCodec.start(WidgetCodec.encode(start.widgets)).changed)
    }

    @Test fun firstFreeFindsAGap() {
        val placed = listOf(script("a", span = 6, column = 0, top = 0), script("b", span = 12, column = 0, top = 96))
        // Beside a, at the top.
        assertEquals(6 to 0, WidgetPlacement.firstFree(placed, 6, 88))
        // Too wide for the gap: under b.
        assertEquals(0 to 192, WidgetPlacement.firstFree(placed, 8, 88))
        assertEquals(0 to 0, WidgetPlacement.firstFree(emptyList(), 12, 200))
    }

    @Test fun aWidgetMovedOntoOthersPushesThemDown() {
        val all = listOf(
            script("a", span = 6, column = 0, top = 0),
            script("b", span = 6, column = 6, top = 0),
            script("c", span = 12, column = 0, top = 96),
        )
        // a goes to where c is: c moves under it; b, beside, stays.
        val moved = WidgetPlacement.moveTo(all, all[0].key, 0, 100)
        val got = places(moved)
        assertEquals(0 to 104, got["a"])
        assertEquals(6 to 0, got["b"])
        assertEquals(0 to 200, got["c"])
        // The order follows the page from the top.
        assertEquals(listOf("b", "a", "c"), WidgetPlacement.readingOrder(moved).map { it.source!!.removePrefix("script:") })
    }

    @Test fun moveToKeepsTheWidgetOnThePage() {
        val all = listOf(script("a", span = 6, column = 0, top = 0))
        val got = WidgetPlacement.moveTo(all, all[0].key, 9, -30).single()
        assertEquals(6, got.column)
        assertEquals(0, got.topDp)
    }

    @Test fun growingPushesDownAndStaysInsideThePage() {
        val all = listOf(script("a", span = 6, column = 6, top = 0), script("b", span = 6, column = 6, top = 96))
        val grown = places(WidgetPlacement.resize(all, all[0].key, 9, 184))
        // Wider than its place allows: it moves towards the start.
        assertEquals(3 to 0, grown["a"])
        assertEquals(6 to 192, grown["b"])
    }

    @Test fun closeGapsPacksThePage() {
        val all = listOf(
            script("a", span = 6, column = 0, top = 40),
            script("b", span = 6, column = 6, top = 300),
            script("c", span = 12, column = 0, top = 600),
        )
        val got = places(WidgetPlacement.closeGaps(all))
        assertEquals(0 to 0, got["a"])
        assertEquals(6 to 0, got["b"])
        assertEquals(0 to 96, got["c"])
    }

    @Test fun nudgeMovesByAColumnOrARow() {
        val all = listOf(script("a", span = 6, column = 0, top = 0))
        assertEquals(1 to 0, places(WidgetPlacement.nudge(all, all[0].key, 1, 0, 88))["a"])
        assertEquals(0 to 96, places(WidgetPlacement.nudge(all, all[0].key, 0, 1, 88))["a"])
        // Up from the top stays at the top.
        assertEquals(0 to 0, places(WidgetPlacement.nudge(all, all[0].key, 0, -1, 88))["a"])
    }

    @Test fun anAddedWidgetGoesIntoTheFirstFreeSpace() {
        val all = listOf(script("a", span = 6, column = 6, top = 0))
        val got = places(WidgetPlacement.add(all, script("b", span = 6)))
        assertEquals(0 to 0, got["b"])
    }

    /** A backup's widget keeps its place when it is free; else it goes into the first free space. */
    @Test fun importedWidgetsFindAPlace() {
        val before = listOf(script("a", span = 12, column = 0, top = 0))
        val imported = listOf(script("b", span = 6, column = 0, top = 0), script("c", span = 6, column = 6, top = 400), script("d", span = 6))
        val got = places(WidgetPlacement.placeImported(before, WidgetCodec.mergePending(before, imported)))
        assertEquals(0 to 0, got["a"])
        assertEquals(0 to 96, got["b"])
        assertEquals(6 to 400, got["c"])
        assertEquals(6 to 96, got["d"])
    }

    @Test fun widgetsNeverOverlapAfterAnyChange() {
        var all = listOf(
            script("a", span = 6, column = 0, top = 0, height = 184),
            script("b", span = 3, column = 6, top = 0),
            script("c", span = 3, column = 9, top = 0),
            script("d", span = 12, column = 0, top = 192),
        )
        all = WidgetPlacement.moveTo(all, all[3].key, 2, 40)
        all = WidgetPlacement.resize(all, all.first { it.provider == "script:b" }.key, 6, 300)
        all = WidgetPlacement.nudge(all, all.first { it.provider == "script:c" }.key, -9, 1, 88)
        for (x in all) for (y in all) assertFalse("${x.provider} and ${y.provider}", WidgetPlacement.overlap(x, y))
    }
}
