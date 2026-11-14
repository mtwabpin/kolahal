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
 * Where a widget that Kolahal draws itself gets its content. Such a widget has no AppWidget id. Its
 * placed entry holds the source where an app widget's entry holds its provider.
 */
object WidgetSource {
    /** `script:<name>`: the widget a script fills with `widget.show`. */
    const val SCRIPT = "script:"
    /** `status:<name>`: the Status card of a script's status line. */
    const val STATUS = "status:"
    /** `builtin:<id>`: a built-in widget, such as `builtin:battery`. */
    const val BUILTIN = "builtin:"

    private val PREFIXES = listOf(SCRIPT, STATUS, BUILTIN)

    /**
     * Whether [provider] names a source: one of the prefixes, then something. A flattened
     * ComponentName always has a `/` and a source never does, so the two can't be mixed up. The
     * stored line's separators (`|`, a line break) are refused too.
     */
    fun isSource(provider: String): Boolean {
        val prefix = PREFIXES.firstOrNull { provider.startsWith(it) } ?: return false
        return provider.length > prefix.length && provider.none { it == '/' || it == '|' || it == '\n' || it == '\r' }
    }

    /** The source of the script [name]'s own widget. */
    fun script(name: String): String = SCRIPT + name

    /** The script's name when [source] is a script's own widget. Null for anything else, a Status card included. */
    fun scriptName(source: String?): String? = source?.takeIf { it.startsWith(SCRIPT) && isSource(it) }?.removePrefix(SCRIPT)

    /** The source of the Status card of the script [name]. */
    fun status(name: String): String = STATUS + name

    /** The script's name when [source] is a Status card. Null for anything else, the script's own widget included. */
    fun statusName(source: String?): String? = source?.takeIf { it.startsWith(STATUS) && isSource(it) }?.removePrefix(STATUS)

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
    fun builtinId(source: String?): String? = source?.takeIf { it.startsWith(BUILTIN) && isSource(it) }?.removePrefix(BUILTIN)

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
     * in the source. That is the script's name, or a built-in id this version does not know.
     */
    fun label(source: String): String = builtinId(source)?.let(::builtinTitle) ?: source.substringAfter(':')

    /** The sources that belong to the script [name]: its own widget and its Status card. */
    fun ofScript(name: String): List<String> = listOf(SCRIPT + name, STATUS + name)

    /**
     * [all] after the script [from] was renamed to [to]: its widgets keep their place, size and order
     * under the new name. An entry that an earlier script named [to] left behind gives way to the
     * one that takes its place. One that nothing replaces stays: it is the renamed script's widget
     * now. Nothing changes when the new name can't be stored.
     */
    fun renamed(all: List<PlacedWidget>, from: String, to: String): List<PlacedWidget> {
        val names = ofScript(from).zip(ofScript(to)).toMap()
        if (from == to || names.values.any { !isSource(it) }) return all
        // Only the entries that are placed move. A Status card that moves must not remove a waiting `script:` widget.
        val moved = names.filterKeys { source -> all.any { it.provider == source } }
        if (moved.isEmpty()) return all
        return all.filterNot { it.provider in moved.values }.map { w -> moved[w.provider]?.let { w.copy(provider = it) } ?: w }
    }
}

/**
 * A placed widget.
 * - [id] is the AppWidget id. It is null for an entry restored from a backup that must still be bound
 *   again ("pending re-add"), and for a widget Kolahal draws itself ([source]).
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
) {
    /** The source, for a widget Kolahal draws itself: `script:weather`. Null for an app's widget. */
    val source: String? get() = provider.takeIf(WidgetSource::isSource)
    /** An app's widget that must be bound again. A widget with a [source] needs no id, so it is never pending. */
    val pending: Boolean get() = id == null && source == null
    /**
     * Stable key for lists: the source, or the widget id, or provider and order for pending entries.
     * A source's key does not hold its order, so it stays the same when the widget moves.
     */
    val key: String get() = source?.let { "s:$it" } ?: id?.let { "w$it" } ?: "p:$provider:$profileSerial:$location:$order"
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
     * widget at least [MIN_SPAN]. Every place that sets or reads a width uses this one rule.
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
 * - A pending entry has `-` as its id. So has a widget Kolahal draws itself: its provider field holds
 *   its source (`script:weather`, see [WidgetSource]).
 * - Lines saved before widths existed have no span. They are full width.
 * - A height or a width outside what the widget may have is brought inside it ([WidgetLayout.minDp],
 *   [WidgetLayout.minSpan]). A saved line and a backup's entry follow the same rule.
 * - Flattened ComponentNames never contain `|`, and a source with one is refused.
 * - A Kolahal from before sources skips a line whose provider has no `/`. So going back to it drops
 *   these widgets and breaks nothing.
 */
object WidgetCodec {
    fun encode(all: List<PlacedWidget>): String = all.joinToString("\n") {
        listOf(it.id?.toString() ?: "-", it.provider, it.profileSerial, it.heightDp, it.order, it.location, it.span).joinToString("|")
    }

    fun decode(text: String?): List<PlacedWidget> = text.orEmpty().lineSequence().mapNotNull { line ->
        val f = line.split('|')
        if (f.size < 6 || f[1].isBlank()) return@mapNotNull null
        val source = WidgetSource.isSource(f[1])
        if (!source && !f[1].contains('/')) return@mapNotNull null
        PlacedWidget(
            // A source has no AppWidget id, whatever the line says.
            id = if (source) null else f[0].toIntOrNull(),
            provider = f[1],
            profileSerial = f[2].toLongOrNull() ?: 0L,
            // A size below the widget's minimum (a list written by hand, or by another version) is raised to it.
            heightDp = (f[3].toIntOrNull() ?: 0).coerceIn(WidgetLayout.minDp(source), WidgetLayout.MAX_DP),
            order = f[4].toIntOrNull() ?: 0,
            location = WidgetLocation.of(f[5]),
            span = f.getOrNull(6)?.toIntOrNull()?.coerceIn(WidgetLayout.minSpan(source), WidgetLayout.COLUMNS) ?: WidgetLayout.COLUMNS,
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

    /**
     * Parses backup maps. An app's widget becomes a pending entry. A widget with a source
     * (`"provider": "script:weather"`) needs no id, so it is placed as it comes. Numbers may come as
     * Int, Long, Double or String.
     */
    fun fromExport(list: List<Map<String, Any?>>): List<PlacedWidget> = list.mapNotNull { m ->
        val provider = m["provider"]?.toString()
            ?.takeIf { WidgetSource.isSource(it) || (it.contains('/') && !it.contains('|') && !it.contains('\n')) }
            ?: return@mapNotNull null
        fun num(key: String): Long? = when (val v = m[key]) {
            is Number -> v.toLong()
            is String -> v.toDoubleOrNull()?.toLong()
            else -> null
        }
        val sourced = WidgetSource.isSource(provider)
        PlacedWidget(
            id = null,
            provider = provider,
            profileSerial = num("profile") ?: 0L,
            heightDp = (num("height")?.toInt() ?: 0).coerceIn(WidgetLayout.minDp(sourced), WidgetLayout.MAX_DP),
            order = num("order")?.toInt() ?: 0,
            location = WidgetLocation.of(m["location"]),
            span = num("span")?.toInt()?.coerceIn(WidgetLayout.minSpan(sourced), WidgetLayout.COLUMNS) ?: WidgetLayout.COLUMNS,
        )
    }

    /**
     * Adds imported entries. It skips a pending entry that duplicates an existing pending one, and a
     * source that is already placed: a widget with a source is placed once.
     */
    fun mergePending(existing: List<PlacedWidget>, imported: List<PlacedWidget>): List<PlacedWidget> {
        val seen = existing.filter { it.pending || it.source != null }.map { it.key }.toMutableSet()
        return existing + imported.filter { seen.add(it.key) }
    }
}
