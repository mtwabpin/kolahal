/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.widgets

/**
 * One widgets page: the [location] its widgets hold ([PlacedWidget.location]), and the [name] a person
 * gave it, or null until then.
 */
data class WidgetPage(val location: String, val name: String? = null)

/**
 * The widgets pages beside home, in their order from home outward. Pure, so it can be unit tested.
 * - Page 1 is always there, at [WidgetLocation.PANEL]: every widget placed before there were pages is
 *   on it, and so is every widget of an old backup.
 * - A later page has a location of its own, `panel:<id>` ([locationOf]). The id stays with the page
 *   when the pages before it are removed, so a widget never has to be rewritten for that.
 * - There are at most [MAX] pages.
 * - A page has a name a person gives it ([nameError] says which names may be given); until then it is
 *   "Page 2" ([title]). Numbers always address a page by its place ([find]).
 * - The pages are stored apart from the widgets, one line each: `location|name` ([encode]).
 */
object WidgetPages {
    /** The most pages there may be. */
    const val MAX = 5

    /** The longest a page's name may be, in characters. A tab and the title row show it whole at this length. */
    const val MAX_NAME = 16

    private const val PREFIX = WidgetLocation.PANEL + ":"

    /** Only page 1: the pages before any was added. */
    val ONE: List<WidgetPage> = listOf(WidgetPage(WidgetLocation.PANEL))

    /** The location of the later page with [id] (2 and up): `panel:2`. */
    fun locationOf(id: Int): String = PREFIX + id

    /** The id of a later page's [location]; null for page 1 and for anything that is no page. */
    fun idOf(location: String): Int? =
        location.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.toIntOrNull()?.takeIf { it >= 2 }

    /** Whether [location] can be a page's: page 1's, or a later page's. */
    fun isPage(location: String): Boolean = location == WidgetLocation.PANEL || idOf(location) != null

    /** The stored form: a line for each page, its location and its name (empty when it has none). */
    fun encode(pages: List<WidgetPage>): String =
        pages.joinToString("\n") { "${it.location}|${it.name.orEmpty()}" }

    /** The pages [encode] wrote. A line that names no page is skipped; page 1 comes first, as [of] gives it. */
    fun decode(text: String?): List<WidgetPage> = of(
        text.orEmpty().lineSequence().mapNotNull { line ->
            val location = line.substringBefore('|')
            if (!isPage(location)) return@mapNotNull null
            WidgetPage(location, clean(line.substringAfter('|', "")).ifEmpty { null })
        }.toList(),
        emptyList(),
    )

    /**
     * The pages there are, from what is stored ([stored]) and where the widgets are ([widgets]):
     * page 1 first, then the stored pages in their order, then any page that only a widget names (a
     * list written by a newer or an older version), each page once, at most [MAX]. A widget on a page
     * that isn't among them goes to page 1 ([homeless]).
     */
    fun of(stored: List<WidgetPage>, widgets: List<PlacedWidget>): List<WidgetPage> {
        val first = stored.firstOrNull { it.location == WidgetLocation.PANEL } ?: WidgetPage(WidgetLocation.PANEL)
        val out = arrayListOf(first)
        val seen = hashSetOf(WidgetLocation.PANEL)
        // A name another page has already gives way: two pages never share one.
        val names = hashSetOf<String>()
        first.name?.let { names += it.lowercase() }
        fun take(page: WidgetPage) {
            if (out.size >= MAX || !isPage(page.location) || !seen.add(page.location)) return
            val name = page.name?.takeIf { names.add(it.lowercase()) }
            out += page.copy(name = name)
        }
        stored.forEach(::take)
        widgets.map { it.location }.distinct().forEach { take(WidgetPage(it)) }
        return out
    }

    /** [all] with every widget whose page isn't among [pages] on page 1 instead, placed under what is there. */
    fun homeless(all: List<PlacedWidget>, pages: List<WidgetPage>): List<PlacedWidget> {
        val kept = pages.map { it.location }.toSet()
        val lost = all.map { it.location }.filter { WidgetPages.isPage(it) && it !in kept }.distinct()
        return lost.fold(all) { list, location -> WidgetPlacement.mergeInto(list, location) }
    }

    /** What the page at [index] of [pages] is called: its name, or "Page 2". */
    fun title(pages: List<WidgetPage>, index: Int): String =
        pages.getOrNull(index)?.name ?: "Page ${index + 1}"

    /** The place of the page at [location] among [pages], from 0; -1 when there is no such page. */
    fun indexOf(pages: List<WidgetPage>, location: String): Int =
        pages.indexOfFirst { it.location == location }

    /** [pages] with a new page at the end, or null when there are [MAX] already. Its id is one more than the largest. */
    fun added(pages: List<WidgetPage>): List<WidgetPage>? {
        if (pages.size >= MAX) return null
        val id = (pages.mapNotNull { idOf(it.location) }.maxOrNull() ?: 1) + 1
        return pages + WidgetPage(locationOf(id))
    }

    /** [pages] without the page at [index]. Page 1 stays: it is where the others' widgets go. */
    fun removed(pages: List<WidgetPage>, index: Int): List<WidgetPage> =
        if (index <= 0 || index >= pages.size) pages else pages.filterIndexed { i, _ -> i != index }

    /**
     * The pages and the widgets after the page at [location] is removed from [pages]. Its widgets in
     * [widgets] go to page 1, under what is there ([WidgetPlacement.mergeInto]); the pages after it
     * move up one place. Null for page 1, which stays, and for a page that isn't there.
     */
    fun withoutPage(
        pages: List<WidgetPage>,
        widgets: List<PlacedWidget>,
        location: String,
    ): Pair<List<WidgetPage>, List<PlacedWidget>>? {
        val index = indexOf(pages, location)
        if (index <= 0) return null
        return removed(pages, index) to WidgetPlacement.mergeInto(widgets, location)
    }

    /**
     * The pages and the widgets after a backup is imported onto [pages] and [widgets]:
     * - The backup's pages ([backupPages]) join these by place ([merge]).
     * - Its widgets ([backup], as [WidgetCodec.fromExport] reads them) are added
     *   ([WidgetCodec.mergePending]), each on the page its own page joined, or on page 1 when that
     *   page found no room. Each keeps its place when it is free ([WidgetPlacement.placeImported]).
     * - Every page of the backup comes, an empty one too: a page stays until the person removes it.
     */
    fun imported(
        pages: List<WidgetPage>,
        widgets: List<PlacedWidget>,
        backupPages: List<WidgetPage>,
        backup: List<PlacedWidget>,
    ): Pair<List<WidgetPage>, List<PlacedWidget>> {
        val (merged, into) = merge(pages, backupPages)
        val incoming = WidgetOrder.onePage(backup)
            .map { it.copy(location = into[it.location] ?: WidgetLocation.PANEL) }
        return merged to WidgetPlacement.placeImported(widgets, WidgetCodec.mergePending(widgets, incoming))
    }

    /** [pages] with the page at [index] called [name] ([clean]); an empty name takes its name away. */
    fun renamed(pages: List<WidgetPage>, index: Int, name: String): List<WidgetPage> =
        pages.mapIndexed { i, p -> if (i == index) p.copy(name = clean(name).ifEmpty { null }) else p }

    /** A name as it is kept: one line, without the stored line's `|`, no spaces at its ends, at most [MAX_NAME] characters. */
    fun clean(name: String): String =
        name.replace(Regex("[|\\r\\n\\t]"), " ").trim().take(MAX_NAME).trim()

    /**
     * Why [name] can't be the name of the page at [index], or null when it can. An empty name can:
     * the page is then "Page 2" again.
     * - Not longer than [MAX_NAME] characters.
     * - Not only digits: a number addresses a page by its place (`widget page 2`).
     * - Not what another page is called, by its name or as "Page 3", in any case.
     */
    fun nameError(pages: List<WidgetPage>, index: Int, name: String): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> null
            n.length > MAX_NAME -> "Up to $MAX_NAME characters"
            n.all { it.isDigit() } -> "A name can't be only digits"
            pages.indices.any { it != index && title(pages, it).equals(n, ignoreCase = true) } ->
                "Another page is called that"

            else -> null
        }
    }

    /** The backup form: each page's location, and its name when it has one. */
    fun export(pages: List<WidgetPage>): List<Map<String, Any?>> = pages.map { p ->
        mapOf<String, Any?>("page" to p.location) + listOfNotNull(p.name?.let { "name" to it })
    }

    /** The pages of a backup ([export]), as [of] gives them. A backup from before pages has none: page 1 only. */
    fun fromExport(list: List<Map<String, Any?>>): List<WidgetPage> = of(
        list.mapNotNull { m ->
            val location = m["page"]?.toString()?.lowercase()?.takeIf { isPage(it) } ?: return@mapNotNull null
            WidgetPage(location, (m["name"] as? String)?.let { clean(it) }?.ifEmpty { null })
        },
        emptyList(),
    )

    /**
     * [existing] pages after a backup's pages ([imported]) came in, and where each imported page's
     * widgets go, by location. A page of the backup joins the page in the same place here; it gives
     * that page its name when that page has none and no other page has the name. A page beyond the
     * ones here is added while there is room; past [MAX], its widgets go to page 1.
     */
    fun merge(
        existing: List<WidgetPage>,
        imported: List<WidgetPage>
    ): Pair<List<WidgetPage>, Map<String, String>> {
        var pages = existing
        val into = HashMap<String, String>()
        into[WidgetLocation.PANEL] = WidgetLocation.PANEL
        imported.forEachIndexed { i, page ->
            val at = if (i < pages.size) i else {
                pages = added(pages) ?: return@forEachIndexed
                pages.lastIndex
            }
            into[page.location] = pages[at].location
            val name = page.name
            if (name != null && pages[at].name == null && nameError(pages, at, name) == null) {
                pages = renamed(pages, at, name)
            }
        }
        return pages to into
    }

    /**
     * [widgets] by page, for `widget ls`: the place of each page of [pages] that has widgets, with its
     * placed widgets in page order. Widgets waiting to be added again are not among them.
     */
    fun byPage(pages: List<WidgetPage>, widgets: List<PlacedWidget>): List<Pair<Int, List<PlacedWidget>>> =
        pages.indices.map { i -> i to WidgetOrder.inLocation(widgets, pages[i].location) }
            .filter { it.second.isNotEmpty() }

    /**
     * The page [words] address, by its place among [pages] or its name, as `widget page` takes it:
     * - a number: that page, from 1;
     * - a name, in any case: the page called so, as [title] says it ("Page 2" too);
     * - the beginning of a name: the one page whose name starts so.
     * Null when no page or more than one fits.
     */
    fun find(pages: List<WidgetPage>, words: String): Int? {
        val w = words.trim()
        if (w.isEmpty()) return null
        w.toIntOrNull()?.let { n -> return (n - 1).takeIf { it in pages.indices } }
        val titles = pages.indices.map { title(pages, it) }
        titles.indexOfFirst { it.equals(w, ignoreCase = true) }.takeIf { it >= 0 }?.let { return it }
        return titles.withIndex().filter { it.value.startsWith(w, ignoreCase = true) }.singleOrNull()?.index
    }
}
