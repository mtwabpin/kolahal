/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.widgets

/**
 * Where widgets sit on the widgets pages. Pure, so it can be unit tested. Each page ([WidgetPages])
 * is laid out on its own: widgets on two pages never meet, and each page has its own order.
 * - Each widget has a [PlacedWidget.column] (0 to [WidgetLayout.COLUMNS] − its span, from the start
 *   edge) and a [PlacedWidget.topDp] (dp from the top of the page). They can sit anywhere: gaps stay
 *   where the user leaves them.
 * - Widgets never overlap. Two widgets also keep [WidgetLayout.GAP_DP] between them, up and down.
 *   A widget moved or grown onto others pushes them down just enough, and they push what is under
 *   them ([settle]).
 * - [PlacedWidget.order] follows the reading order (top, then the start edge), so an older Kolahal
 *   and a screen reader go through the widgets in the same order ([renumber]).
 * - Only the pages' placed widgets take part ([onPage]). A pending entry keeps its numbers for when
 *   it is added again.
 */
object WidgetPlacement {
    /** A column or a top that is not set yet. */
    const val UNPLACED = -1

    /** The lowest top that is read back. Anything further down is a mistake, and the widget gets a place again. */
    const val MAX_TOP_DP = 100_000

    /** Tops move in these steps, as heights do. */
    const val STEP_DP = WidgetLayout.STEP_DP
    private const val GAP = WidgetLayout.GAP_DP
    private const val COLUMNS = WidgetLayout.COLUMNS

    /** Whether [w] takes part in the layout: placed on a widgets page, and not waiting to be added again. */
    fun onPage(w: PlacedWidget): Boolean = !w.pending && WidgetPages.isPage(w.location)

    /** The bottom edge of [w], in dp from the top of the page. */
    fun bottomDp(w: PlacedWidget): Int = w.topDp + w.heightDp

    /**
     * How tall a page's content is: the lowest bottom edge of the widgets of the page [location] in
     * [all]. Null for [location] counts every page widget in [all]: for a list of one page's widgets.
     */
    fun pageHeightDp(all: List<PlacedWidget>, location: String? = null): Int =
        all.filter { onPage(it) && (location == null || it.location == location) }.maxOfOrNull(::bottomDp) ?: 0

    /**
     * The lowest top a dragged widget may land at on its page, [page] being that page's widgets: the
     * widget [key], [heightDp] tall, on a page that shows [shownDp] of height without scrolling (as
     * home lays it out). The larger of:
     * - the foot of what shows: the last [STEP_DP] step where all of it still shows. So a lone widget,
     *   or a few, can sit at the bottom of the screen with empty space above them;
     * - one gap under the lowest other widget. So there is always room further down, and a full page
     *   grows by scrolling, one widget at a time, not for ever while it is held at the bottom.
     * Only a drag is bound by it: a top is stored in dp whatever the screen, and a page laid out on a
     * shorter screen scrolls.
     */
    fun lowestDrop(page: List<PlacedWidget>, key: String?, heightDp: Int, shownDp: Int): Int {
        val underOthers = pageHeightDp(page.filter { it.key != key }) + GAP
        val atFoot = (shownDp - heightDp).coerceAtLeast(0) / STEP_DP * STEP_DP
        return maxOf(underOthers, atFoot).coerceAtMost(MAX_TOP_DP)
    }

    private fun columnsMeet(a: PlacedWidget, b: PlacedWidget): Boolean =
        a.column < b.column + b.span && b.column < a.column + a.span

    /** Whether [a] and [b], on one page, overlap, or sit closer than the gap above or below each other. */
    fun overlap(a: PlacedWidget, b: PlacedWidget): Boolean =
        a.key != b.key && a.location == b.location && columnsMeet(
            a,
            b
        ) && a.topDp < bottomDp(b) + GAP && b.topDp < bottomDp(a) + GAP

    /** [list] in reading order: from the top, then from the start edge. */
    fun readingOrder(list: List<PlacedWidget>): List<PlacedWidget> =
        list.sortedWith(compareBy({ it.topDp }, { it.column }, { it.order }))

    /** [all] with each page's widgets numbered 0..n−1 in reading order. Other entries keep theirs. */
    fun renumber(all: List<PlacedWidget>): List<PlacedWidget> {
        val order = all.filter(::onPage).groupBy { it.location }.values
            .flatMap { page -> readingOrder(page).mapIndexed { i, w -> w.key to i } }.toMap()
        return all.map { w ->
            order[w.key]?.takeIf { it != w.order }?.let { w.copy(order = it) } ?: w
        }
    }

    private fun snap(topDp: Int): Int =
        (topDp.coerceIn(0, MAX_TOP_DP) + STEP_DP / 2) / STEP_DP * STEP_DP

    private fun fitColumn(column: Int, span: Int): Int =
        column.coerceIn(0, (COLUMNS - span).coerceAtLeast(0))

    /**
     * Gives a place to each page widget that has none.
     * - When none has a place (a list from 0.5.14 or older), each gets the place the old page gave
     *   it: in order, side by side while they fit, a row as tall as its tallest widget. So the page
     *   looks the same.
     * - Otherwise each one without a place goes into the first free space ([firstFree]), in order.
     */
    fun placeMissing(all: List<PlacedWidget>): List<PlacedWidget> {
        if (all.filter(::onPage).all { it.positioned }) return all
        val placed = HashMap<String, PlacedWidget>()
        all.filter(::onPage).groupBy { it.location }.forEach { (location, page) ->
            if (page.all { it.positioned }) return@forEach
            if (page.none { it.positioned }) {
                fromFlow(WidgetOrder.sorted(page)).forEach { placed[it.key] = it }
            } else {
                val done = page.filter { it.positioned }.toMutableList()
                WidgetOrder.sorted(page.filter { !it.positioned }).forEach { w ->
                    val (column, top) = firstFree(done, w.span, w.heightDp, location)
                    done += w.copy(column = column, topDp = top)
                }
                done.forEach { placed[it.key] = it }
            }
        }
        return renumber(all.map { placed[it.key] ?: it })
    }

    /** [ordered] laid out as the old page did: in order, rows from [WidgetLayout.rows]. */
    private fun fromFlow(ordered: List<PlacedWidget>): List<PlacedWidget> {
        val out = ArrayList<PlacedWidget>(ordered.size)
        var top = 0
        WidgetLayout.rows(ordered.map { it.span }).forEach { row ->
            var column = 0
            row.forEach { i ->
                val w = ordered[i]
                out += w.copy(column = fitColumn(column, w.span), topDp = top)
                column += w.span
            }
            top += row.maxOf { ordered[it].heightDp } + GAP
        }
        return out
    }

    /**
     * The first free place for a widget [span] columns wide and [heightDp] tall among [placed] on the
     * page [location]: the highest top, then the column nearest the start edge. Tops tried are the
     * top of the page and the line under each widget; the last one always fits.
     */
    fun firstFree(
        placed: List<PlacedWidget>,
        span: Int,
        heightDp: Int,
        location: String = WidgetLocation.PANEL,
    ): Pair<Int, Int> {
        val others = placed.filter { it.positioned && it.location == location }
        val s = span.coerceIn(1, COLUMNS)
        val tops = (listOf(0) + others.map { bottomDp(it) + GAP }).distinct().sorted()
        for (top in tops) {
            for (column in 0..COLUMNS - s) {
                val probe = PlacedWidget(
                    null,
                    "probe:/",
                    0L,
                    heightDp,
                    0,
                    location = location,
                    span = s,
                    column = column,
                    topDp = top
                )
                if (others.none { overlap(probe, it) }) return column to top
            }
        }
        return 0 to (others.maxOfOrNull { bottomDp(it) + GAP } ?: 0)
    }

    /**
     * [all] with the widget [key] where it is, and every page widget that overlaps another pushed
     * down just enough. Widgets are taken in reading order, so they keep their order down the page,
     * and a widget pushed down pushes what is under it. Widgets that overlap nothing stay put.
     */
    fun settle(all: List<PlacedWidget>, key: String?): List<PlacedWidget> {
        val page = all.filter { onPage(it) && it.positioned }
        val fixed = page.firstOrNull { it.key == key }
        val done = ArrayList<PlacedWidget>(page.size).apply { if (fixed != null) add(fixed) }
        val moved = HashMap<String, PlacedWidget>()
        readingOrder(page.filter { it.key != key }).forEach { w ->
            var cur = w
            while (true) {
                val hit = done.filter { overlap(cur, it) }
                if (hit.isEmpty()) break
                cur = cur.copy(topDp = hit.maxOf { bottomDp(it) + GAP })
            }
            if (cur !== w) moved[w.key] = cur
            done += cur
        }
        return renumber(all.map { moved[it.key] ?: it })
    }

    /**
     * The lines a widget moved near them lines up with: each other widget's top on its page, and its
     * bottom and the gap under it. [key]'s own are left out.
     */
    fun lines(all: List<PlacedWidget>, key: String?): Set<Int> {
        val location = all.firstOrNull { it.key == key }?.location
        return all.filter { onPage(it) && it.positioned && it.key != key && (location == null || it.location == location) }
            .flatMapTo(HashSet<Int>()) { listOf(it.topDp, bottomDp(it) + GAP) }
    }

    /**
     * The widget [key] moved to [column] and [topDp]: the column brought inside the page, the top
     * to the nearest [STEP_DP] step, unless it is on another widget's line ([lines]): then it stays
     * there. Widgets in the way move down ([settle]).
     */
    fun moveTo(all: List<PlacedWidget>, key: String, column: Int, topDp: Int): List<PlacedWidget> {
        val w = all.firstOrNull { it.key == key } ?: return all
        val top = topDp.coerceIn(0, MAX_TOP_DP)
        val to = w.copy(
            column = fitColumn(column, w.span),
            topDp = if (top in lines(all, key)) top else snap(top)
        )
        if (to == w) return all
        return settle(all.map { if (it.key == key) to else it }, key)
    }

    /**
     * The widget [key] at [span] columns and [heightDp] (already within the widget's limits). A
     * widget that would pass the end edge moves back towards the start. Widgets in the way move down.
     */
    fun resize(all: List<PlacedWidget>, key: String, span: Int, heightDp: Int): List<PlacedWidget> {
        val w = all.firstOrNull { it.key == key } ?: return all
        val s = span.coerceIn(1, COLUMNS)
        val to = w.copy(
            span = s,
            heightDp = heightDp,
            column = if (w.positioned) fitColumn(w.column, s) else w.column
        )
        if (to == w) return all
        return settle(all.map { if (it.key == key) to else it }, key)
    }

    /**
     * The widget [key] moved by [columns] sideways and [rows] up or down, each row [rowDp] and the
     * gap: for a screen reader's Move up, Move down, Move left and Move right. Widgets in the way
     * move down.
     */
    fun nudge(
        all: List<PlacedWidget>,
        key: String,
        columns: Int,
        rows: Int,
        rowDp: Int
    ): List<PlacedWidget> {
        val w = all.firstOrNull { it.key == key }?.takeIf { it.positioned } ?: return all
        return moveTo(all, key, w.column + columns, w.topDp + rows * (rowDp + GAP))
    }

    /**
     * Every widget of the page [location] moved up as far as it goes in its own columns, in reading
     * order: the page packed, with no space left between widgets. The other pages stay as they are.
     */
    fun closeGaps(all: List<PlacedWidget>, location: String = WidgetLocation.PANEL): List<PlacedWidget> {
        val done = ArrayList<PlacedWidget>()
        val moved = HashMap<String, PlacedWidget>()
        readingOrder(all.filter { onPage(it) && it.positioned && it.location == location }).forEach { w ->
            val top = done.filter { columnsMeet(w, it) }.maxOfOrNull { bottomDp(it) + GAP } ?: 0
            val cur = if (top != w.topDp) w.copy(topDp = top) else w
            if (cur !== w) moved[w.key] = cur
            done += cur
        }
        return renumber(all.map { moved[it.key] ?: it })
    }

    /**
     * [merged] after a backup's widgets were added to [before]. Each added page widget keeps its
     * place when it has one and the place is free; otherwise it goes into the first free space.
     */
    fun placeImported(before: List<PlacedWidget>, merged: List<PlacedWidget>): List<PlacedWidget> {
        val old = before.map { it.key }.toSet()
        val done = merged.filter { it.key in old && onPage(it) && it.positioned }.toMutableList()
        val placed = HashMap<String, PlacedWidget>()
        WidgetOrder.sorted(merged.filter { it.key !in old && onPage(it) }).forEach { w ->
            val fits = w.positioned && done.none { overlap(w, it) }
            val cur = if (fits) w else firstFree(
                done,
                w.span,
                w.heightDp,
                w.location
            ).let { (c, t) -> w.copy(column = c, topDp = t) }
            placed[w.key] = cur
            done += cur
        }
        return renumber(merged.map { placed[it.key] ?: it })
    }

    /** [all] with [w] added in the first free space of its page ([firstFree]). */
    fun add(all: List<PlacedWidget>, w: PlacedWidget): List<PlacedWidget> {
        val (column, top) = firstFree(all.filter(::onPage), w.span, w.heightDp, w.location)
        return renumber(all + w.copy(column = column, topDp = top))
    }

    /**
     * The widget [key] moved to the page [location]: into the first free space from its top, as a
     * new widget goes ([add]), at the size it has. Nothing changes when it is on that page already.
     */
    fun moveToPage(all: List<PlacedWidget>, key: String, location: String): List<PlacedWidget> {
        val w = all.firstOrNull { it.key == key } ?: return all
        if (w.location == location || !WidgetPages.isPage(location)) return all
        val moved = w.copy(location = location, column = UNPLACED, topDp = UNPLACED)
        val rest = all.filterNot { it.key == key }
        return if (w.pending) renumber(rest + moved.copy(order = pendingOrder(rest, location))) else add(rest, moved)
    }

    /**
     * The order for one more widget waiting to be added again on the page [location]: after those
     * waiting there in [all]. Its key holds its page and its order ([PlacedWidget.key]), so two must
     * never share both.
     */
    private fun pendingOrder(all: List<PlacedWidget>, location: String): Int =
        WidgetOrder.nextOrder(all.filter { it.pending && it.location == location })

    /**
     * Every widget of the page [from] moved onto the page [to], under what is there: they keep how
     * they sit among themselves, and start one gap under its lowest widget. A widget waiting to be
     * added again goes with them. For a page that is removed.
     */
    fun mergeInto(
        all: List<PlacedWidget>,
        from: String,
        to: String = WidgetLocation.PANEL
    ): List<PlacedWidget> {
        if (from == to || all.none { it.location == from }) return all
        val under = pageHeightDp(all, to).let { if (it > 0) it + GAP else 0 }
        val moving = all.filter { it.location == from && onPage(it) && it.positioned }
        val top = moving.minOfOrNull { it.topDp } ?: 0
        // Those waiting to be added again come after the ones waiting on [to] ([pendingOrder]).
        val first = pendingOrder(all, to)
        val orders = WidgetOrder.sorted(all.filter { it.pending && it.location == from })
            .mapIndexed { i, w -> w.key to first + i }.toMap()
        val merged = all.map { w ->
            when {
                w.location != from -> w
                w in moving -> w.copy(location = to, topDp = (w.topDp - top + under).coerceAtMost(MAX_TOP_DP))
                // A widget waiting to be added again keeps its place, as on its page.
                else -> w.copy(location = to, order = orders[w.key] ?: w.order)
            }
        }
        // One that had no place yet gets one under the rest.
        return placeMissing(renumber(merged))
    }
}
