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
 * - Without `home.pages`, the default order ([of] with no ids): the log on the right, widgets page 1
 *   left of it and the others further out, the Assistant outermost.
 * - With `home.pages`, its ids in their order. The same function builds both: the default is the
 *   order of an empty list.
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

    /**
     * Whether the log is the first page, with pages to its right: while a line's swipes are the defaults, one
     * swiped toward them copies, never deletes ([com.mtwabpin.kolahal.terminal.LineSwipes]). From the order home
     * shows, not from `home.pages`' text: a page it lists may not be there (the Assistant off, a widgets page
     * removed), and one it doesn't list may be left of the log.
     */
    val pagesRight: Boolean get() = logPlace == 0 && count > 1

    companion object {
        /** A widgets page's id: `widgets` for page 1, `widgets:3` for the page at `panel:3`. */
        fun idOf(location: String): String = WIDGETS + location.removePrefix(WidgetLocation.PANEL)

        /** The location of the widgets page with [id], or null when [id] can't be a widgets page's. */
        private fun locationOf(id: String): String? =
            if (id.startsWith(WIDGETS)) (WidgetLocation.PANEL + id.removePrefix(WIDGETS)).takeIf(WidgetPages::isPage) else null

        private const val WIDGETS = "widgets"

        /**
         * The pages in order: the ids in [order] (`home.pages`) that exist, and each page that exists
         * but isn't listed where the default order puts it: next to its neighbor toward the log, on
         * the side away from the log (left of the log for page 1 or the Assistant alone; the log
         * itself, unlisted, on the right).
         * - [pages] are the widgets pages; empty when `widgets.show` is off.
         * - [assistant]: the Assistant's page is on (never in the Play build).
         */
        fun of(order: List<String>, pages: List<WidgetPage>, assistant: Boolean): HomeOrder {
            // From the log outward, as the default order lays them out.
            val outward = listOf(HomeEntry.Log) +
                    pages.mapIndexed { i, p -> HomeEntry.Widgets(i, p) } +
                    listOfNotNull(HomeEntry.Assistant.takeIf { assistant })
            val byId = outward.associateBy { it.id }
            val list = order.mapNotNull { byId[it] }.distinct().toMutableList()
            outward.forEachIndexed { i, entry ->
                if (entry in list) return@forEachIndexed
                if (entry == HomeEntry.Log) {
                    list.add(entry)
                    return@forEachIndexed
                }
                // Its neighbor toward the log is placed already: the pages go from the log outward.
                val inward = list.indexOf(outward[i - 1])
                val log = list.indexOf(HomeEntry.Log)
                list.add(if (inward <= log) inward else inward + 1, entry)
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
         * The order home shows with [c]'s settings: `home.pages`, the widgets [pages] while `widgets.show` is on,
         * and the Assistant's page while `assistant.page` is (full build only). [pages] are the widgets pages
         * there are (WidgetHost.pages), whether they show or not.
         */
        fun of(c: Config, pages: List<WidgetPage>): HomeOrder = of(
            listed(c[Keys.HOME_PAGES]),
            if (c[Keys.WIDGETS_SHOW]) pages else emptyList(),
            com.mtwabpin.kolahal.BuildConfig.ASSISTANT && c[Keys.ASSISTANT_PAGE],
        )

        /** [HomeOrder.pagesRight] for the order home shows with [c]'s settings and the widgets [pages] ([of]). */
        fun pagesRight(c: Config, pages: List<WidgetPage>): Boolean = of(c, pages).pagesRight
    }
}
