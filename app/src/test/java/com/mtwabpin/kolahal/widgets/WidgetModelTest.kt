package com.mtwabpin.kolahal.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetModelTest {
    private fun w(id: Int?, order: Int, location: String = WidgetLocation.PANEL, provider: String = "com.a/.W$order", height: Int = 120) =
        PlacedWidget(id, provider, 0L, height, order, location)

    @Test fun codecRoundTrips() {
        val all = listOf(
            w(5, 0),
            w(7, 1, WidgetLocation.OLD_HOME, "com.example.clock/com.example.clock.ClockWidget\$Big"),
            w(null, 2, WidgetLocation.OLD_HOME),
        ).map { it.copy(profileSerial = 10L) }
        assertEquals(all, WidgetCodec.decode(WidgetCodec.encode(all)))
    }

    @Test fun decodeSkipsBrokenLinesAndClamps() {
        val text = "garbage\n3|com.a/.W|0|5|0|panel\n|nocomponent|0|100|1|home\n4|com.b/.W|x|99999|y|weird"
        val got = WidgetCodec.decode(text)
        assertEquals(2, got.size)
        assertEquals(WidgetLayout.MIN_DP, got[0].heightDp)
        assertEquals(WidgetLayout.MAX_DP, got[1].heightDp)
        assertEquals(0L, got[1].profileSerial)
        assertEquals(WidgetLocation.PANEL, got[1].location)
        assertTrue(WidgetCodec.decode(null).isEmpty())
    }

    @Test fun exportDropsIdsAndImportMakesPending() {
        val exported = WidgetCodec.export(listOf(w(9, 1, WidgetLocation.OLD_HOME, height = 200), w(3, 0)))
        assertEquals(listOf("com.a/.W0", "com.a/.W1"), exported.map { it["provider"] })
        assertTrue(exported.none { it.containsKey("id") })
        // JSON round trips may turn numbers into Double / String.
        val imported = WidgetCodec.fromExport(
            listOf(
                mapOf("provider" to "com.a/.W1", "profile" to 0.0, "height" to "200", "location" to "home", "order" to 1),
                mapOf("provider" to "not a component"),
            )
        )
        assertEquals(1, imported.size)
        val p = imported[0]
        assertNull(p.id)
        assertTrue(p.pending)
        assertEquals(200, p.heightDp)
        assertEquals(WidgetLocation.OLD_HOME, p.location)
    }

    @Test fun importTwiceDoesNotDuplicate() {
        val list = listOf(mapOf<String, Any?>("provider" to "com.a/.W", "order" to 0))
        val once = WidgetCodec.mergePending(emptyList(), WidgetCodec.fromExport(list))
        val twice = WidgetCodec.mergePending(once, WidgetCodec.fromExport(list))
        assertEquals(1, twice.size)
    }

    @Test fun moveStaysWithinLocation() {
        val all = listOf(w(1, 0), w(2, 1), w(3, 2), w(4, 0, WidgetLocation.OLD_HOME))
        val moved = WidgetOrder.move(all, "w3", -1)
        assertEquals(listOf(1, 3, 2), WidgetOrder.inLocation(moved, WidgetLocation.PANEL).map { it.id })
        assertEquals(0, moved.first { it.id == 4 }.order)
        // Edges are no-ops.
        assertEquals(all, WidgetOrder.move(all, "w1", -1))
        assertEquals(all, WidgetOrder.move(all, "w4", 1))
    }

    /** Widgets placed on home before 0.3.1 join the widgets page after its own, keeping their order. */
    @Test fun homeWidgetsMoveOntoThePage() {
        val all = listOf(w(1, 1), w(2, 0), w(4, 1, WidgetLocation.OLD_HOME), w(5, 0, WidgetLocation.OLD_HOME), w(null, 3, WidgetLocation.OLD_HOME))
        val page = WidgetOrder.onePage(all)
        assertTrue(page.all { it.location == WidgetLocation.PANEL })
        assertEquals(listOf(2, 1, 5, 4), WidgetOrder.inLocation(page, WidgetLocation.PANEL).map { it.id })
        assertEquals(listOf(0, 1, 2, 3), WidgetOrder.inLocation(page, WidgetLocation.PANEL).map { it.order })
        assertEquals(3, page.single { it.pending }.order)
        val onlyPage = listOf(w(1, 0), w(2, 1))
        assertEquals(onlyPage, WidgetOrder.onePage(onlyPage))
    }

    @Test fun resizeClamps() {
        assertEquals(168, WidgetLayout.resize(120, 48, 100))
        assertEquals(100, WidgetLayout.resize(120, -48, 100))
        assertEquals(WidgetLayout.MAX_DP, WidgetLayout.resize(WidgetLayout.MAX_DP, 48, 48))
    }

    @Test fun minAndDefaultHeights() {
        assertEquals(60, WidgetLayout.minHeight(110, 60, resizableVertically = true))
        assertEquals(110, WidgetLayout.minHeight(110, 60, resizableVertically = false))
        assertEquals(WidgetLayout.MIN_DP, WidgetLayout.minHeight(10, 0, true))
        assertEquals(110, WidgetLayout.defaultHeight(110))
        assertEquals(180, WidgetLayout.defaultHeight(40, targetCellHeight = 2))
        assertEquals(72, WidgetLayout.defaultHeight(20))
    }

    @Test fun sizeLabels() {
        assertEquals("4×2", WidgetLayout.sizeLabel(250, 110))
        assertEquals("1×1", WidgetLayout.sizeLabel(10, 10))
        assertEquals("3×1", WidgetLayout.sizeLabel(250, 110, targetCellWidth = 3, targetCellHeight = 1))
    }

    @Test fun widgetsSitSideBySideWhileTheyFit() {
        assertEquals(listOf(listOf(0, 1), listOf(2)), WidgetLayout.rows(listOf(6, 6, 12)))
        assertEquals(listOf(listOf(0, 1, 2), listOf(3)), WidgetLayout.rows(listOf(4, 4, 4, 3)))
        // The next one doesn't fit beside the others: a new row, even with room left.
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), WidgetLayout.rows(listOf(8, 6, 12)))
        assertEquals(emptyList<List<Int>>(), WidgetLayout.rows(emptyList()))
        // Out-of-range spans count as their nearest allowed width.
        assertEquals(listOf(listOf(0), listOf(1)), WidgetLayout.rows(listOf(40, 0)))
    }

    @Test fun widthsOfARowFillThePage() {
        val page = 395
        assertEquals(page, WidgetLayout.widthDp(page, 12))
        // Two halves and the gap between them make the page, give or take a dp of rounding.
        val half = WidgetLayout.widthDp(page, 6)
        assertTrue(2 * half + WidgetLayout.GAP_DP in page - 1..page)
        val thirds = 3 * WidgetLayout.widthDp(page, 4) + 2 * WidgetLayout.GAP_DP
        assertTrue(thirds in page - 2..page)
    }

    @Test fun widthLabelsAndSteps() {
        assertEquals("full", WidgetLayout.spanLabel(12))
        assertEquals("1/2", WidgetLayout.spanLabel(6))
        assertEquals("2/3", WidgetLayout.spanLabel(8))
        assertEquals("1/6", WidgetLayout.spanLabel(2))
        assertEquals("5/12", WidgetLayout.spanLabel(5))
        assertEquals(WidgetLayout.COLUMNS, WidgetLayout.resizeSpan(12, 1))
        assertEquals(WidgetLayout.MIN_SPAN, WidgetLayout.resizeSpan(2, -1))
        assertEquals(5, WidgetLayout.resizeSpan(6, -1))
    }

    @Test fun widthsAreSavedAndOlderLinesAreFullWidth() {
        val half = w(5, 0).copy(span = 6)
        assertEquals(listOf(half), WidgetCodec.decode(WidgetCodec.encode(listOf(half))))
        // Saved by 0.3.1: six fields, no width.
        assertEquals(WidgetLayout.COLUMNS, WidgetCodec.decode("3|com.a/.W|0|120|0|panel").single().span)
        assertEquals(WidgetLayout.MIN_SPAN, WidgetCodec.decode("3|com.a/.W|0|120|0|panel|0").single().span)
    }

    @Test fun backupsCarryTheWidth() {
        val exported = WidgetCodec.export(listOf(w(9, 0).copy(span = 4)))
        assertEquals(4, exported.single()["span"])
        val imported = WidgetCodec.fromExport(
            listOf(
                mapOf("provider" to "com.a/.W0", "span" to 4.0),
                mapOf("provider" to "com.a/.W1"),
                mapOf("provider" to "com.a/.W2", "span" to "99"),
            )
        )
        assertEquals(listOf(4, WidgetLayout.COLUMNS, WidgetLayout.COLUMNS), imported.map { it.span })
    }

    // ---- widgets Kolahal draws itself: the provider field holds a source ----

    private fun script(name: String, order: Int = 0, span: Int = 6) = PlacedWidget(null, WidgetSource.script(name), 0L, 96, order, span = span)

    @Test fun aSourceIsAPrefixAndAName() {
        assertTrue(WidgetSource.isSource("script:weather"))
        assertTrue(WidgetSource.isSource("status:cricket"))
        assertTrue(WidgetSource.isSource("builtin:battery"))
        // No name, no known prefix, or one of the stored line's separators.
        assertFalse(WidgetSource.isSource("script:"))
        assertFalse(WidgetSource.isSource("weather"))
        assertFalse(WidgetSource.isSource("other:weather"))
        assertFalse(WidgetSource.isSource("com.a/.W"))
        assertFalse(WidgetSource.isSource("script:a/b"))
        assertFalse(WidgetSource.isSource("script:a|b"))
        assertFalse(WidgetSource.isSource("script:a\nb"))
        assertEquals("script:weather", WidgetSource.script("weather"))
        assertEquals("weather", WidgetSource.scriptName("script:weather"))
        // A Status card is another widget: removing it must not touch the script's own.
        assertNull(WidgetSource.scriptName("status:weather"))
        assertNull(WidgetSource.scriptName("com.a/.W"))
        assertNull(WidgetSource.scriptName(null))
    }

    /** A widget with a source has no id and is still placed. Only an app's widget without an id waits to be added again. */
    @Test fun aSourceIsPlacedNotPending() {
        val s = script("weather")
        assertEquals("script:weather", s.source)
        assertFalse(s.pending)
        assertNull(w(5, 0).source)
        assertFalse(w(5, 0).pending)
        assertTrue(w(null, 0).pending)
        // It is on the page with the app widgets, in one order.
        val all = listOf(w(5, 1), script("weather", order = 0), w(null, 2))
        assertEquals(listOf("s:script:weather", "w5"), WidgetOrder.inLocation(all, WidgetLocation.PANEL).map { it.key })
    }

    @Test fun sourceLinesRoundTrip() {
        val all = listOf(w(5, 0), script("weather", order = 1), PlacedWidget(null, "status:cricket", 0L, 72, 2, span = 12), w(null, 3))
        val text = WidgetCodec.encode(all)
        assertTrue(text.lines().contains("-|script:weather|0|96|1|panel|6"))
        assertEquals(all, WidgetCodec.decode(text))
        // A source has no AppWidget id, whatever the line says.
        assertNull(WidgetCodec.decode("7|script:weather|0|96|1|panel|6").single().id)
        // Not a source and not a component: skipped, as before.
        assertTrue(WidgetCodec.decode("-|script:|0|96|1|panel|6\n-|other:x|0|96|1|panel|6\n-|weather|0|96|1|panel|6").isEmpty())
    }

    /** Lines saved before sources existed read as they did: an id, or a pending entry. */
    @Test fun oldLinesReadAsBefore() {
        val got = WidgetCodec.decode("3|com.a/.W|0|120|0|panel\n-|com.b/.W|10|200|1|panel|6")
        assertEquals(listOf(3, null), got.map { it.id })
        assertEquals(listOf(false, true), got.map { it.pending })
        assertTrue(got.all { it.source == null })
        assertEquals(listOf("w3", "p:com.b/.W:10:panel:1"), got.map { it.key })
    }

    /** Its key holds no order, so it is the same wherever the widget moves and after a restart. */
    @Test fun aSourcesKeyIsStable() {
        val all = listOf(w(1, 0), script("weather", order = 1), w(2, 2))
        val key = all[1].key
        assertEquals("s:script:weather", key)
        val moved = WidgetOrder.move(all, key, -1)
        assertEquals(0, moved.single { it.source != null }.order)
        assertEquals(key, moved.single { it.source != null }.key)
        assertEquals(listOf(key, "w1", "w2"), WidgetOrder.inLocation(moved, WidgetLocation.PANEL).map { it.key })
        assertEquals(key, WidgetCodec.decode(WidgetCodec.encode(moved)).single { it.source != null }.key)
        // The script's widget and its Status card are two entries.
        assertTrue(key != PlacedWidget(null, "status:weather", 0L, 96, 3).key)
    }

    /** A backup holds the source where an app widget has its provider, and a restore places it: it needs no "Add again". */
    @Test fun backupsCarrySources() {
        val exported = WidgetCodec.export(listOf(w(9, 0), script("weather", order = 1, span = 6)))
        assertEquals(listOf("com.a/.W0", "script:weather"), exported.map { it["provider"] })
        val imported = WidgetCodec.fromExport(
            listOf(
                mapOf("provider" to "script:weather", "height" to 96.0, "order" to 1.0, "span" to 6.0),
                mapOf("provider" to "script:bad|name"),
                mapOf("provider" to "script:"),
                mapOf("provider" to "com.a/.W0"),
            )
        )
        assertEquals(listOf("script:weather", "com.a/.W0"), imported.map { it.provider })
        assertFalse(imported[0].pending)
        assertEquals(6, imported[0].span)
        assertEquals(96, imported[0].heightDp)
        assertTrue(imported[1].pending)
    }

    /** A widget with a source is placed once: importing a backup again, or over the same widget, adds nothing. */
    @Test fun importSkipsASourceAlreadyPlaced() {
        val list = listOf(mapOf<String, Any?>("provider" to "script:weather", "order" to 4), mapOf("provider" to "script:news", "order" to 5))
        val existing = listOf(w(1, 0), script("weather", order = 1))
        val merged = WidgetCodec.mergePending(existing, WidgetCodec.fromExport(list))
        assertEquals(listOf("w1", "s:script:weather", "s:script:news"), merged.map { it.key })
        // The one already placed keeps its place.
        assertEquals(1, merged.single { it.source == "script:weather" }.order)
        assertEquals(merged, WidgetCodec.mergePending(merged, WidgetCodec.fromExport(list)))
    }

    /** A renamed script's widgets keep their place, size and order. Only the name in the source changes. */
    @Test fun aRenamedScriptsWidgetsFollowIt() {
        val status = PlacedWidget(null, "status:weather", 0L, 72, 2, span = 12)
        val all = listOf(w(1, 0), script("weather", order = 1, span = 3), status, script("news", order = 3))
        assertEquals(listOf("script:weather", "status:weather"), WidgetSource.ofScript("weather"))
        val renamed = WidgetSource.renamed(all, "weather", "sky")
        assertEquals(listOf("com.a/.W0", "script:sky", "status:sky", "script:news"), renamed.map { it.provider })
        assertEquals(all.map { listOf(it.order, it.span, it.heightDp) }, renamed.map { listOf(it.order, it.span, it.heightDp) })
        // An entry that an earlier script of the new name left behind gives way.
        val over = WidgetSource.renamed(all, "weather", "news")
        assertEquals(listOf("com.a/.W0", "script:news", "status:news"), over.map { it.provider })
        assertEquals(listOf(0, 1, 2), over.map { it.order })
        // Nothing to move, the same name, or a name that can't be stored: no change.
        assertEquals(all, WidgetSource.renamed(all, "other", "sky"))
        assertEquals(all, WidgetSource.renamed(all, "weather", "weather"))
        assertEquals(all, WidgetSource.renamed(all, "weather", "a|b"))
        assertEquals(all, WidgetSource.renamed(all, "weather", ""))
    }
}
