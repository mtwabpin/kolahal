package com.mtwabpin.kolahal.widgets

/**
 * Pure widget bookkeeping. It uses no Android types, so ordering, resizing and serialisation can be
 * unit tested. [WidgetHost] owns the Android side.
 */
object WidgetLocation {
    /** The widgets page, where every widget lives. */
    const val PANEL = "panel"
    /** Under the terminal header, used before 0.3.1. These entries move onto the page ([WidgetOrder.onePage]). */
    const val OLD_HOME = "home"

    fun of(raw: Any?): String = if (raw?.toString()?.lowercase() == OLD_HOME) OLD_HOME else PANEL
}

/**
 * A placed widget.
 * - [id] is the AppWidget id. It is null for an entry restored from a backup that must still be bound
 *   again ("pending re-add").
 * - [provider] is a flattened ComponentName.
 */
data class PlacedWidget(
    val id: Int?,
    val provider: String,
    val profileSerial: Long,
    val heightDp: Int,
    val order: Int,
    val location: String = WidgetLocation.PANEL,
    /** Its width, in twelfths of the page ([WidgetLayout.COLUMNS]). Narrower widgets sit side by side. */
    val span: Int = WidgetLayout.COLUMNS,
) {
    val pending: Boolean get() = id == null
    /** Stable key for lists: the widget id, or provider and order for pending entries. */
    val key: String get() = id?.let { "w$it" } ?: "p:$provider:$profileSerial:$location:$order"
    /** The package part of [provider]: "com.example/.Clock" -> "com.example". */
    val packageName: String get() = provider.substringBefore('/')
}

object WidgetLayout {
    /** Height step for the − / + buttons. Holding a button repeats it. */
    const val STEP_DP = 8
    const val MIN_DP = 48
    const val MAX_DP = 1200

    /** The page is twelve columns wide, so halves, thirds, quarters and their sums all fit. */
    const val COLUMNS = 12
    const val MIN_SPAN = 2
    /** Space between widgets (across and down) and on each side of the page. */
    const val GAP_DP = 8

    /** Width of a widget [span] columns wide on a page [pageDp] wide. Spans that add up to a full row fill it exactly. */
    fun widthDp(pageDp: Int, span: Int): Int = (pageDp + GAP_DP) * span / COLUMNS - GAP_DP

    fun resizeSpan(current: Int, delta: Int): Int = (current + delta).coerceIn(MIN_SPAN, COLUMNS)

    /** Label for a span: "1/2", "2/3", "5/12" or "full". */
    fun spanLabel(span: Int): String {
        if (span >= COLUMNS) return "full"
        val d = gcd(span, COLUMNS)
        return "${span / d}/${COLUMNS / d}"
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    /** Splits the page into rows. Widgets stay in order. A new row starts when the next widget doesn't fit beside the others. */
    fun rows(spans: List<Int>): List<List<Int>> {
        val rows = ArrayList<MutableList<Int>>()
        var used = COLUMNS
        spans.forEachIndexed { i, span ->
            val s = span.coerceIn(MIN_SPAN, COLUMNS)
            if (used + s > COLUMNS) {
                rows += ArrayList<Int>()
                used = 0
            }
            rows.last() += i
            used += s
        }
        return rows
    }

    /** Smallest height for a widget: its resize minimum if it can resize vertically, else its minimum height. */
    fun minHeight(minHeightDp: Int, minResizeHeightDp: Int, resizableVertically: Boolean): Int {
        val m = if (resizableVertically && minResizeHeightDp > 0) minResizeHeightDp else minHeightDp
        return m.coerceIn(MIN_DP, MAX_DP)
    }

    /**
     * Height for a newly added widget: the larger of the provider's minimum and its target cells at
     * 90 dp each. It is at least 72 dp.
     */
    fun defaultHeight(minHeightDp: Int, targetCellHeight: Int = 0): Int {
        val fromCells = if (targetCellHeight > 0) targetCellHeight * 90 else 0
        return maxOf(minHeightDp, fromCells, 72).coerceIn(MIN_DP, MAX_DP)
    }

    /** Adds [deltaDp] (for example ±[STEP_DP]) and keeps the result within [min]..[MAX_DP]. */
    fun resize(current: Int, deltaDp: Int, min: Int): Int = (current + deltaDp).coerceIn(min.coerceAtMost(MAX_DP), MAX_DP)

    /** Launcher grid cells for a size in dp (the classic 70n − 30 rule). */
    fun cells(dp: Int): Int = maxOf(1, (dp + 30) / 70)

    /** Size label such as "4×2" from provider sizes. Target cells (Android 12+) are used when the provider declares them. */
    fun sizeLabel(minWidthDp: Int, minHeightDp: Int, targetCellWidth: Int = 0, targetCellHeight: Int = 0): String {
        val w = if (targetCellWidth > 0) targetCellWidth else cells(minWidthDp)
        val h = if (targetCellHeight > 0) targetCellHeight else cells(minHeightDp)
        return "$w×$h"
    }
}

/** Ordering helpers. An order value has meaning only within one location. */
object WidgetOrder {
    fun sorted(all: List<PlacedWidget>): List<PlacedWidget> = all.sortedWith(compareBy({ it.order }, { it.id ?: Int.MAX_VALUE }))

    fun inLocation(all: List<PlacedWidget>, location: String): List<PlacedWidget> =
        sorted(all.filter { !it.pending && it.location == location })

    fun nextOrder(all: List<PlacedWidget>): Int = (all.maxOfOrNull { it.order } ?: -1) + 1

    /**
     * Moves the widget with [key] one place up (-1) or down (+1) among the placed widgets of its
     * location. Then renumbers that location 0..n-1. Other entries don't change.
     */
    fun move(all: List<PlacedWidget>, key: String, direction: Int): List<PlacedWidget> {
        val target = all.firstOrNull { it.key == key } ?: return all
        val group = inLocation(all, target.location).toMutableList()
        val from = group.indexOfFirst { it.key == key }
        val to = from + direction.coerceIn(-1, 1)
        if (from < 0 || to !in group.indices) return all
        group.add(to, group.removeAt(from))
        val renumbered = group.mapIndexed { i, w -> w.key to i }.toMap()
        return all.map { w -> renumbered[w.key]?.let { w.copy(order = it) } ?: w }
    }

    /**
     * Moves everything onto the widgets page. Widgets placed on home before 0.3.1 come after the
     * page's widgets, in their own order. Then the page is renumbered 0..n-1.
     */
    fun onePage(all: List<PlacedWidget>): List<PlacedWidget> {
        if (all.all { it.location == WidgetLocation.PANEL }) return all
        val placed = inLocation(all, WidgetLocation.PANEL) + inLocation(all, WidgetLocation.OLD_HOME)
        val order = placed.mapIndexed { i, w -> w.key to i }.toMap()
        return all.map { w -> w.copy(location = WidgetLocation.PANEL, order = order[w.key] ?: w.order) }
    }
}

/**
 * Stores one record per line in SharedPreferences: `id|provider|serial|height|order|location|span`.
 * - A pending entry has `-` as its id.
 * - Lines saved before widths existed have no span. They are full width.
 * - Flattened ComponentNames never contain `|`.
 */
object WidgetCodec {
    fun encode(all: List<PlacedWidget>): String = all.joinToString("\n") {
        listOf(it.id?.toString() ?: "-", it.provider, it.profileSerial, it.heightDp, it.order, it.location, it.span).joinToString("|")
    }

    fun decode(text: String?): List<PlacedWidget> = text.orEmpty().lineSequence().mapNotNull { line ->
        val f = line.split('|')
        if (f.size < 6 || f[1].isBlank() || !f[1].contains('/')) return@mapNotNull null
        PlacedWidget(
            id = f[0].toIntOrNull(),
            provider = f[1],
            profileSerial = f[2].toLongOrNull() ?: 0L,
            heightDp = (f[3].toIntOrNull() ?: WidgetLayout.MIN_DP).coerceIn(WidgetLayout.MIN_DP, WidgetLayout.MAX_DP),
            order = f[4].toIntOrNull() ?: 0,
            location = WidgetLocation.of(f[5]),
            span = f.getOrNull(6)?.toIntOrNull()?.coerceIn(WidgetLayout.MIN_SPAN, WidgetLayout.COLUMNS) ?: WidgetLayout.COLUMNS,
        )
    }.toList()

    /** The backup form. Ids are device-specific, so they are left out. */
    fun export(all: List<PlacedWidget>): List<Map<String, Any?>> = WidgetOrder.sorted(all).map {
        mapOf(
            "provider" to it.provider,
            "profile" to it.profileSerial,
            "height" to it.heightDp,
            "location" to it.location,
            "order" to it.order,
            "span" to it.span,
        )
    }

    /** Parses backup maps into pending entries. Numbers may come as Int, Long, Double or String. */
    fun fromExport(list: List<Map<String, Any?>>): List<PlacedWidget> = list.mapNotNull { m ->
        val provider = m["provider"]?.toString()?.takeIf { it.contains('/') && !it.contains('|') && !it.contains('\n') }
            ?: return@mapNotNull null
        fun num(key: String): Long? = when (val v = m[key]) {
            is Number -> v.toLong()
            is String -> v.toDoubleOrNull()?.toLong()
            else -> null
        }
        PlacedWidget(
            id = null,
            provider = provider,
            profileSerial = num("profile") ?: 0L,
            heightDp = (num("height")?.toInt() ?: WidgetLayout.MIN_DP).coerceIn(WidgetLayout.MIN_DP, WidgetLayout.MAX_DP),
            order = num("order")?.toInt() ?: 0,
            location = WidgetLocation.of(m["location"]),
            span = num("span")?.toInt()?.coerceIn(WidgetLayout.MIN_SPAN, WidgetLayout.COLUMNS) ?: WidgetLayout.COLUMNS,
        )
    }

    /** Adds imported pending entries, skipping ones that duplicate an existing pending entry. */
    fun mergePending(existing: List<PlacedWidget>, imported: List<PlacedWidget>): List<PlacedWidget> {
        val seen = existing.filter { it.pending }.map { it.key }.toMutableSet()
        return existing + imported.filter { seen.add(it.key) }
    }
}
