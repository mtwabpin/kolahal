/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.widgets

/**
 * Pure widget bookkeeping. It uses no Android types, so ordering, resizing and serialization can be
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
 * Where a widget that Kolahal draws itself gets its content. Such a widget has no AppWidget id. Its
 * placed entry holds the source where an app widget's entry holds its provider.
 */
object WidgetSource {
    /**
     * `script:<name>`: the widget a script fills with `widget.show`. A script's further widgets have
     * an id after the name: `script:<name>#2` ([WidgetRef]).
     */
    const val SCRIPT = "script:"

    /** `builtin:<id>`: a built-in widget, such as `builtin:battery`. */
    const val BUILTIN = "builtin:"

    /**
     * `status:<name>`: a Status card, a script's status line as a card, up to 0.5.8.
     * - Status cards are gone. The status line still shows at the top of home.
     * - So this is no source. A stored entry or a backup's entry with it is dropped
     *   ([WidgetCodec.start], [WidgetCodec.fromExport]).
     */
    const val OLD_STATUS = "status:"

    private val PREFIXES = listOf(SCRIPT, BUILTIN)

    /**
     * Whether [provider] names a source: one of the prefixes, then something. A flattened
     * ComponentName always has a `/` and a source never does, so the two can't be mixed up. The
     * stored line's separators (`|`, a line break) are refused too.
     */
    fun isSource(provider: String): Boolean {
        val prefix = PREFIXES.firstOrNull { provider.startsWith(it) } ?: return false
        return provider.length > prefix.length && provider.none { it == '/' || it == '|' || it == '\n' || it == '\r' }
    }

    /** The source of the script [name]'s first widget. */
    fun script(name: String): String = SCRIPT + name

    /** The source of the widget [ref]: `script:weather`, `script:weather#2`. */
    fun script(ref: WidgetRef): String = SCRIPT + ref.key

    /**
     * Which widget of which script [source] is, when it is a script's own widget. Null for anything
     * else, and for a source whose id can't be one ([WidgetRef.parse]).
     */
    fun scriptRef(source: String?): WidgetRef? =
        source?.takeIf { it.startsWith(SCRIPT) && isSource(it) }?.removePrefix(SCRIPT)
            ?.let(WidgetRef::parse)

    /** The id of the built-in Battery and network widget. Its source is `builtin:battery`. */
    const val BATTERY = "battery"

    /** The id of the built-in Shortcuts widget: buttons for commands you picked ([Shortcuts]). */
    const val SHORTCUTS = "shortcuts"

    /** The id of the built-in Now playing widget. */
    const val MEDIA = "media"

    /** The id of the built-in Todos widget. */
    const val TODOS = "todos"

    /** The id of the built-in Agenda widget: the phone's calendar. */
    const val AGENDA = "agenda"

    /** The id of the built-in Reminders widget. */
    const val REMINDERS = "reminders"

    /** The id of the built-in Notes widget. */
    const val NOTES = "notes"

    /** The source of the built-in widget [id]. */
    fun builtin(id: String): String = BUILTIN + id

    /**
     * The id when [source] is a built-in widget: `battery`. Null for anything else. An id this
     * version does not know (from a newer Kolahal's backup) still comes back: the card then says
     * that it has nothing to draw.
     */
    fun builtinId(source: String?): String? =
        source?.takeIf { it.startsWith(BUILTIN) && isSource(it) }?.removePrefix(BUILTIN)

    /** The kind of card the built-in widget [id] draws. Null for an id this version does not know. */
    fun builtinKind(id: String): WidgetKind? = when (id) {
        BATTERY -> WidgetKind.PROGRESS
        SHORTCUTS -> WidgetKind.BUTTONS
        MEDIA -> WidgetKind.MEDIA
        TODOS, REMINDERS -> WidgetKind.LIST
        AGENDA -> WidgetKind.AGENDA
        NOTES -> WidgetKind.TEXT
        else -> null
    }

    /**
     * The name of the built-in widget [id], as the picker, the Widgets screen and `widgets ls` say
     * it: "Battery and network". Null for an id this version does not know.
     */
    fun builtinTitle(id: String): String? = when (id) {
        BATTERY -> "Battery and network"
        SHORTCUTS -> "Shortcuts"
        MEDIA -> "Now playing"
        TODOS -> "Todos"
        AGENDA -> "Agenda"
        REMINDERS -> "Reminders"
        NOTES -> "Notes"
        else -> null
    }

    /**
     * What `widgets ls` calls the widget with this [source]: a built-in widget's name, else the name
     * in the source. That is the script's name, or a built-in id this version does not know. A
     * script's further widget has its id after the name: "weather 2".
     */
    fun label(source: String): String =
        builtinId(source)?.let(::builtinTitle) ?: scriptRef(source)?.let { it.titled(it.script) }
        ?: source.substringAfter(':')

    /**
     * [source] under the name [to], when it is one of the script [from]'s widgets: `script:weather#2`
     * becomes `script:sky#2`. Null for any other source.
     */
    private fun moved(source: String?, from: String, to: String): String? =
        scriptRef(source)?.takeIf { it.script == from }?.let { script(it.copy(script = to)) }

    /**
     * [all] after the script [from] was renamed to [to]: every one of its widgets keeps its place,
     * size and order under the new name. An entry that an earlier script named [to] left behind
     * gives way to the one that takes its place. One that nothing replaces stays: it is the renamed
     * script's widget now. Nothing changes when the new name can't be stored.
     */
    fun renamed(all: List<PlacedWidget>, from: String, to: String): List<PlacedWidget> {
        if (from == to || !isSource(script(to))) return all
        val moved =
            all.mapNotNull { w -> moved(w.source, from, to)?.let { w.provider to it } }.toMap()
        if (moved.isEmpty()) return all
        return all.filterNot { it.provider in moved.values }
            .map { w -> moved[w.provider]?.let { w.copy(provider = it) } ?: w }
    }
}

/**
 * A placed widget.
 * - [id] is the AppWidget id. It is null for an entry restored from a backup that must still be bound
 *   again ("Pending re-add"), and for a widget Kolahal draws itself ([source]).
 * - [provider] is a flattened ComponentName, or a source ([WidgetSource]).
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
    /** How Kolahal draws it ([WidgetLook]). An app's widget draws itself, so it is always [WidgetLook.PLAIN]. */
    val look: WidgetLook = WidgetLook.PLAIN,
    /**
     * Its first column on the page: 0 until [WidgetLayout.COLUMNS] − [span], from the start edge.
     * [WidgetPlacement.UNPLACED] until it has a place ([WidgetPlacement.placeMissing]).
     */
    val column: Int = WidgetPlacement.UNPLACED,
    /** Its top edge, in dp from the top of the page. [WidgetPlacement.UNPLACED] until it has a place. */
    val topDp: Int = WidgetPlacement.UNPLACED,
) {
    /** Whether it has a place on the page: a [column] and a [topDp]. */
    val positioned: Boolean get() = column >= 0 && topDp >= 0

    /** The source, for a widget Kolahal draws itself: `script:weather`. Null for an app's widget. */
    val source: String? get() = provider.takeIf(WidgetSource::isSource)

    /** An app's widget that must be bound again. A widget with a [source] needs no id, so it is never pending. */
    val pending: Boolean get() = id == null && source == null

    /**
     * Stable key for lists: the source, or the widget id, or provider and order for pending entries.
     * A source's key does not hold its order, so it stays the same when the widget moves.
     */
    val key: String
        get() = source?.let { "s:$it" } ?: id?.let { "w$it" }
        ?: "p:$provider:$profileSerial:$location:$order"

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

    /**
     * The narrowest a placed widget may be, in columns. A widget Kolahal draws itself ([sourced]: it
     * has a [PlacedWidget.source]) is at least one cell wide ([WidgetSizes.MIN_SPAN]); an app's
     * widget at least [MIN_SPAN]. Every place that sets or reads a width uses this one rule. An app's
     * widget whose details are known also has its own minimum ([minSpan] of its least width,
     * [WidgetHost.minSpanOf]).
     */
    fun minSpan(sourced: Boolean): Int = if (sourced) WidgetSizes.MIN_SPAN else MIN_SPAN

    /**
     * The lowest a placed widget may be, in dp: [WidgetSizes.MIN_HEIGHT_DP] for a widget Kolahal draws
     * itself ([sourced]), [MIN_DP] for an app's widget. An app's widget whose details are known also
     * has its own minimum ([minHeight]).
     */
    fun minDp(sourced: Boolean): Int = if (sourced) WidgetSizes.MIN_HEIGHT_DP else MIN_DP

    /** Width of a widget [span] columns wide on a page [pageDp] wide. Spans that add up to a full row fill it exactly. */
    fun widthDp(pageDp: Int, span: Int): Int = (pageDp + GAP_DP) * span / COLUMNS - GAP_DP

    fun resizeSpan(current: Int, delta: Int): Int = (current + delta).coerceIn(MIN_SPAN, COLUMNS)

    /** Label for a span: "1/2", "2/3", "5/12" or "Full". */
    fun spanLabel(span: Int): String {
        if (span >= COLUMNS) return "Full"
        val d = gcd(span, COLUMNS)
        return "${span / d}/${COLUMNS / d}"
    }

    /** A widget's size as the Widgets screen writes it: "1/2 wide · 184 dp tall", or "Full width · 184 dp tall". */
    fun sizeLine(span: Int, heightDp: Int): String =
        (if (span >= COLUMNS) "Full width" else "${spanLabel(span)} wide") + " · $heightDp dp tall"

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

    /**
     * The fewest columns of a page [pageDp] wide that make a widget at least [widthDp] wide
     * ([widthDp] of a span), from [MIN_SPAN] to [COLUMNS].
     */
    fun spanFor(widthDp: Int, pageDp: Int): Int {
        if (widthDp <= 0 || pageDp <= 0) return MIN_SPAN
        val cell = pageDp + GAP_DP
        return (((widthDp + GAP_DP) * COLUMNS + cell - 1) / cell).coerceIn(MIN_SPAN, COLUMNS)
    }

    /**
     * The narrowest an app's widget may be on a page [pageDp] wide, in columns: its resize minimum if
     * it can resize sideways, else its least width ([spanFor]).
     */
    fun minSpan(minWidthDp: Int, minResizeWidthDp: Int, resizableHorizontally: Boolean, pageDp: Int): Int {
        val m = if (resizableHorizontally && minResizeWidthDp > 0) minResizeWidthDp else minWidthDp
        return spanFor(m, pageDp)
    }

    /**
     * The width a newly added app's widget starts at, in columns: its target cells where the provider
     * gives them ([targetCellWidth]), else the cells of its least width ([cells]), a cell being three
     * columns ([WidgetSizes.MIN_SPAN]). At least [minSpan], at most the page.
     */
    fun defaultSpan(minWidthDp: Int, targetCellWidth: Int = 0, minSpan: Int = MIN_SPAN): Int {
        val wide = if (targetCellWidth > 0) targetCellWidth else cells(minWidthDp)
        return maxOf(wide * WidgetSizes.MIN_SPAN, minSpan).coerceIn(MIN_SPAN, COLUMNS)
    }

    /** [heightDp] kept within [min]..[MAX_DP]. A [min] over [MAX_DP] counts as [MAX_DP]. */
    fun clampHeight(heightDp: Int, min: Int): Int =
        heightDp.coerceIn(min.coerceAtMost(MAX_DP), MAX_DP)

    /** Launcher grid cells for a size in dp (the classic 70n − 30 rule). */
    fun cells(dp: Int): Int = maxOf(1, (dp + 30) / 70)

    /** Size label such as "4×2" from provider sizes. Target cells (Android 12+) are used when the provider declares them. */
    fun sizeLabel(
        minWidthDp: Int,
        minHeightDp: Int,
        targetCellWidth: Int = 0,
        targetCellHeight: Int = 0
    ): String {
        val w = if (targetCellWidth > 0) targetCellWidth else cells(minWidthDp)
        val h = if (targetCellHeight > 0) targetCellHeight else cells(minHeightDp)
        return "$w×$h"
    }
}

/** Ordering helpers. An order value has meaning only within one location. */
object WidgetOrder {
    fun sorted(all: List<PlacedWidget>): List<PlacedWidget> =
        all.sortedWith(compareBy({ it.order }, { it.id ?: Int.MAX_VALUE }))

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
        val placed =
            inLocation(all, WidgetLocation.PANEL) + inLocation(all, WidgetLocation.OLD_HOME)
        val order = placed.mapIndexed { i, w -> w.key to i }.toMap()
        return all.map { w ->
            w.copy(
                location = WidgetLocation.PANEL,
                order = order[w.key] ?: w.order
            )
        }
    }
}

/**
 * Stores one record per line in SharedPreferences:
 * `id|provider|serial|height|order|location|span|look|column|top`.
 * - A pending entry has `-` as its id. So has a widget Kolahal draws itself: its provider field holds
 *   its source (`script:weather`, see [WidgetSource]).
 * - Lines saved before widths existed have no span. They are full width.
 * - Lines saved before looks and places (0.5.14 and older) have neither: the look is
 *   [WidgetLook.PLAIN], and [start] gives each widget the place it had ([WidgetPlacement.placeMissing]).
 *   An older Kolahal reads fields by position and ignores the last three.
 * - [PlacedWidget.order] is still written, in reading order, so an older Kolahal shows the widgets
 *   in a sensible order.
 * - A height or a width outside what the widget may have is brought inside it ([WidgetLayout.minDp],
 *   [WidgetLayout.minSpan]). A saved line and a backup's entry follow the same rule.
 * - Flattened ComponentNames never contain `|`, and a source with one is refused.
 * - A Kolahal from before sources skips a line whose provider has no `/`. So going back to it drops
 *   these widgets and breaks nothing.
 */
object WidgetCodec {
    fun encode(all: List<PlacedWidget>): String = all.joinToString("\n") {
        listOf(
            it.id?.toString() ?: "-",
            it.provider,
            it.profileSerial,
            it.heightDp,
            it.order,
            it.location,
            it.span,
            it.look.id,
            it.column,
            it.topDp
        ).joinToString("|")
    }

    /** The look stored for a widget: only one Kolahal draws ([sourced]) has one; an app's widget draws itself. */
    private fun lookOf(raw: Any?, sourced: Boolean): WidgetLook =
        if (sourced) WidgetLook.named(raw?.toString()) ?: WidgetLook.PLAIN else WidgetLook.PLAIN

    /** A stored place: a whole number from 0, else [WidgetPlacement.UNPLACED]. Too large a column is brought inside the page. */
    private fun columnOf(raw: Long?, span: Int): Int =
        raw?.takeIf { it >= 0 }?.toInt()?.coerceAtMost(WidgetLayout.COLUMNS - span)
            ?: WidgetPlacement.UNPLACED

    private fun topOf(raw: Long?): Int =
        raw?.takeIf { it in 0..WidgetPlacement.MAX_TOP_DP }?.toInt() ?: WidgetPlacement.UNPLACED

    fun decode(text: String?): List<PlacedWidget> =
        text.orEmpty().lineSequence().mapNotNull { line ->
            val f = line.split('|')
            if (f.size < 6 || f[1].isBlank()) return@mapNotNull null
            val source = WidgetSource.isSource(f[1])
            if (!source && !f[1].contains('/')) return@mapNotNull null
            val span = f.getOrNull(6)?.toIntOrNull()
                ?.coerceIn(WidgetLayout.minSpan(source), WidgetLayout.COLUMNS)
                ?: WidgetLayout.COLUMNS
            val column = columnOf(f.getOrNull(8)?.toLongOrNull(), span)
            val top = topOf(f.getOrNull(9)?.toLongOrNull())
            PlacedWidget(
                // A source has no AppWidget id, whatever the line says.
                id = if (source) null else f[0].toIntOrNull(),
                provider = f[1],
                profileSerial = f[2].toLongOrNull() ?: 0L,
                // A size below the widget's minimum (a list written by hand, or by another version) is raised to it.
                heightDp = (f[3].toIntOrNull() ?: 0).coerceIn(
                    WidgetLayout.minDp(source),
                    WidgetLayout.MAX_DP
                ),
                order = f[4].toIntOrNull() ?: 0,
                location = WidgetLocation.of(f[5]),
                span = span,
                look = lookOf(f.getOrNull(7), source),
                // A place needs both numbers: with one missing, the widget gets a place again at start.
                column = if (top >= 0) column else WidgetPlacement.UNPLACED,
                topDp = if (column >= 0) top else WidgetPlacement.UNPLACED,
            )
        }.toList()

    /**
     * The placed list as [WidgetHost] reads it at start, from the stored [text] ([decode]). It makes
     * the one-time changes for older versions:
     * - Widgets placed on home before 0.3.1 move onto the page ([WidgetOrder.onePage]).
     * - Status cards ([WidgetSource.OLD_STATUS], up to 0.5.8) are dropped: [decode] can't read them.
     * - Widgets saved without a place (up to 0.5.14) get the one they had: the order and the rows
     *   of the old page ([WidgetPlacement.placeMissing]). So the page looks the same after the update.
     *
     * When [Start.changed], the list is saved again at once. So each change happens once, and so
     * does its [Start.note].
     */
    fun start(text: String?): Start {
        val saved = decode(text)
        val widgets = WidgetPlacement.placeMissing(WidgetOrder.onePage(saved))
        val statusCards = text.orEmpty().lineSequence()
            .count { it.split('|').getOrNull(1)?.startsWith(WidgetSource.OLD_STATUS) == true }
        return Start(
            widgets,
            changed = widgets != saved || statusCards > 0,
            statusCards = statusCards
        )
    }

    /**
     * What [start] read: the [widgets], and whether they must be saved again ([changed]).
     * [statusCards] is how many Status cards it dropped.
     */
    data class Start(val widgets: List<PlacedWidget>, val changed: Boolean, val statusCards: Int) {
        /** What the log says once about the Status cards that were dropped. Null when there were none. */
        val note: String?
            get() = if (statusCards > 0) "Status cards are gone: $statusCards removed from the widgets page. Status lines still show at the top of home." else null
    }

    /** The backup form. Ids are device-specific, so they are left out. */
    fun export(all: List<PlacedWidget>): List<Map<String, Any?>> = WidgetOrder.sorted(all).map {
        mapOf(
            "provider" to it.provider,
            "profile" to it.profileSerial,
            "height" to it.heightDp,
            "location" to it.location,
            "order" to it.order,
            "span" to it.span,
            "look" to it.look.id,
            "column" to it.column,
            "top" to it.topDp,
        )
    }

    /**
     * Parses backup maps. An app's widget becomes a pending entry. A widget with a source
     * (`"provider": "script:weather"`) needs no id, so it is placed as it comes. Numbers may come as
     * Int, Long, Double or String. A Status card from an older backup is dropped ([WidgetSource.OLD_STATUS]).
     */
    fun fromExport(list: List<Map<String, Any?>>): List<PlacedWidget> = list.mapNotNull { m ->
        val provider = m["provider"]?.toString()
            ?.takeIf {
                WidgetSource.isSource(it) || (it.contains('/') && !it.contains('|') && !it.contains(
                    '\n'
                ))
            }
            ?: return@mapNotNull null

        fun num(key: String): Long? = when (val v = m[key]) {
            is Number -> v.toLong()
            is String -> v.toDoubleOrNull()?.toLong()
            else -> null
        }

        val sourced = WidgetSource.isSource(provider)
        val span =
            num("span")?.toInt()?.coerceIn(WidgetLayout.minSpan(sourced), WidgetLayout.COLUMNS)
                ?: WidgetLayout.COLUMNS
        val column = columnOf(num("column"), span)
        val top = topOf(num("top"))
        PlacedWidget(
            id = null,
            provider = provider,
            profileSerial = num("profile") ?: 0L,
            heightDp = (num("height")?.toInt() ?: 0).coerceIn(
                WidgetLayout.minDp(sourced),
                WidgetLayout.MAX_DP
            ),
            order = num("order")?.toInt() ?: 0,
            location = WidgetLocation.of(m["location"]),
            span = span,
            look = lookOf(m["look"], sourced),
            column = if (top >= 0) column else WidgetPlacement.UNPLACED,
            topDp = if (column >= 0) top else WidgetPlacement.UNPLACED,
        )
    }

    /**
     * Adds imported entries. It skips:
     * - a pending entry that duplicates an existing pending one;
     * - a source that is already placed: a widget with a source is placed once;
     * - an app's widget that is placed already (a backup imported on the phone it came from), by
     *   count: for each provider and profile, as many imported entries as are placed. So a second
     *   widget of the same app still comes back.
     */
    fun mergePending(
        existing: List<PlacedWidget>,
        imported: List<PlacedWidget>
    ): List<PlacedWidget> {
        val seen = existing.filter { it.pending || it.source != null }.map { it.key }.toMutableSet()
        // App widgets placed now, counted by provider and profile.
        val placed = existing.filter { it.id != null && it.source == null }
            .groupingBy { it.provider to it.profileSerial }.eachCount().toMutableMap()
        return existing + imported.filter { w ->
            val app = w.provider to w.profileSerial
            val left = if (w.source == null) placed[app] ?: 0 else 0
            if (left > 0) {
                placed[app] = left - 1
                false
            } else seen.add(w.key)
        }
    }

    /**
     * A list another install wrote (a restore to a new phone): app widgets' ids belong to the old
     * phone, so each app widget becomes [PlacedWidget.pending], keeping its place, for Add again.
     */
    fun fromElsewhere(list: List<PlacedWidget>): List<PlacedWidget> =
        list.map { if (it.source == null) it.copy(id = null) else it }
}
