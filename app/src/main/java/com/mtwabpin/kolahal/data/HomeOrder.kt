/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.data

import com.mtwabpin.kolahal.widgets.WidgetLocation
import com.mtwabpin.kolahal.widgets.WidgetPage
import com.mtwabpin.kolahal.widgets.WidgetPages

/**
 * One page of home's pager, with the [id] `home.pages` names it by.
 * A new kind of page is one more case here: its id, its name in [HomeOrder.name], and where
 * [HomeOrder.of] puts it while the setting doesn't list it.
 */
sealed interface HomeEntry {
    val id: String

    /** The terminal: always there, once. */
    data object Log : HomeEntry {
        override val id = "log"
    }

    /** The widgets page at [index] of the pages (from 0). Its id comes from its location, so it survives a rename and the removal of a page before it. */
    data class Widgets(val index: Int, val page: WidgetPage) : HomeEntry {
        override val id get() = HomeOrder.idOf(page.location)
    }

    /** The Assistant's page (`assistant.page`, full build only). */
    data object Assistant : HomeEntry {
        override val id = "assistant"
    }
}

/**
 * Home's pages from left to right ([entries]), and the places on the pager that everything else asks
 * for. Pure, so it can be unit tested.
 * - Without `home.pages`, the order is today's rule ([of] with no ids): the log at one end, widgets
 *   page 1 next to it and the others further out on `widgets.page`'s side, the Assistant outermost.
 * - With `home.pages`, its ids in their order. The same function builds both: the rule is the order
 *   of an empty list.
 */
class HomeOrder(val entries: List<HomeEntry>) {
    val count get() = entries.size

    /** The log's place. */
    val logPlace get() = entries.indexOf(HomeEntry.Log)

    /** The Assistant's place, or -1 when it has none. */
    val assistantPlace get() = entries.indexOf(HomeEntry.Assistant)

    /** The place of the widgets page at [index] (from 0), or -1. */
    fun placeOf(index: Int): Int = entries.indexOfFirst { it is HomeEntry.Widgets && it.index == index }

    /** The page at [place], or null past the ends. */
    fun at(place: Int): HomeEntry? = entries.getOrNull(place)

    /** The place of the page with [id], or -1. */
    fun placeOf(id: String): Int = entries.indexOfFirst { it.id == id }

    val ids get() = entries.map { it.id }

    companion object {
        /** A widgets page's id: `widgets` for page 1, `widgets:3` for the page at `panel:3`. */
        fun idOf(location: String): String = WIDGETS + location.removePrefix(WidgetLocation.PANEL)

        /** The location of the widgets page with [id], or null when [id] can't be a widgets page's. */
        private fun locationOf(id: String): String? =
            if (id.startsWith(WIDGETS)) (WidgetLocation.PANEL + id.removePrefix(WIDGETS)).takeIf(WidgetPages::isPage) else null

        private const val WIDGETS = "widgets"

        /**
         * The pages in order: the ids in [order] (`home.pages`) that exist, then each page that exists
         * but isn't listed, at the end where today's rule puts it ([side]), outward from the log.
         * - [pages] are the widgets pages; empty when `widgets.page` is off.
         * - [side]: `left` puts them left of the log; anything else, right.
         * - [assistant]: the Assistant's page is on (never in the Play build).
         */
        fun of(order: List<String>, pages: List<WidgetPage>, side: String, assistant: Boolean): HomeOrder {
            // From the log outward, as today's rule lays them out.
            val outward = listOf(HomeEntry.Log) +
                    pages.mapIndexed { i, p -> HomeEntry.Widgets(i, p) } +
                    listOfNotNull(HomeEntry.Assistant.takeIf { assistant })
            val byId = outward.associateBy { it.id }
            val list = order.mapNotNull { byId[it] }.distinct().toMutableList()
            for (entry in outward) if (entry !in list) {
                if (side == "left") list.add(0, entry) else list.add(entry)
            }
            return HomeOrder(list)
        }

        /** The ids in `home.pages`' value, from the left. */
        fun listed(value: String): List<String> = value.split(',').filter { it.isNotEmpty() }

        /**
         * A typed value as `home.pages` stores it: ids separated by commas or spaces, each once.
         * Empty is the default (today's order); null when an id has no form of one.
         */
        fun parse(raw: String): String? {
            val ids = raw.lowercase().split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
            if (ids.distinct().size != ids.size) return null
            if (ids.any { it != HomeEntry.Log.id && it != HomeEntry.Assistant.id && locationOf(it) == null }) return null
            return ids.joinToString(",")
        }

        /** What a page is called: "Log", "Assistant", "Widgets" while there is one widgets page, else the page's name. */
        fun name(entry: HomeEntry, pages: List<WidgetPage>): String = when (entry) {
            HomeEntry.Log -> "Log"
            HomeEntry.Assistant -> "Assistant"
            is HomeEntry.Widgets -> if (pages.size == 1) "Widgets" else WidgetPages.title(pages, entry.index)
        }

        /**
         * Whether the pages are to the right of the log, and none to its left: a log line swiped toward
         * them copies, never deletes. Without an order, `widgets.page right`; with one, the log listed first.
         */
        fun pagesRight(c: Config): Boolean = pagesRight(c[Keys.HOME_PAGES], c[Keys.WIDGETS_PAGE])

        /** [pagesRight] for `home.pages`' value [order] and `widgets.page`'s [side]. */
        fun pagesRight(order: String, side: String): Boolean {
            val ids = listed(order)
            return if (ids.isEmpty()) side == "right" else ids.first() == HomeEntry.Log.id && ids.size > 1
        }
    }
}
