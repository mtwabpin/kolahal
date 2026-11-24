/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

import com.mtwabpin.kolahal.widgets.WidgetPage
import com.mtwabpin.kolahal.widgets.WidgetPages

/**
 * What `widget` and `widgets` do, as `script` and `scripts` work: the plural alone opens a screen
 * (Arrange widgets), the singular acts (`widget add`, `widget ls`, `widget page`), and either word
 * takes the subcommands. `widget` alone lists, as `script` and `feed` alone do.
 */
object WidgetWords {
    enum class Action {
        /** Arrange widgets: `widgets` alone. */
        ARRANGE,

        /** The picker: `widget add`. */
        ADD,

        /** The placed widgets as text: `widget ls`, or `widget` alone. */
        LIST,

        /** Home turns to the widgets page: `widget page`. */
        PAGE,
    }

    /** What [name] (`widget` or `widgets`) with [args] does, or null for a word neither takes. */
    fun of(name: String, args: List<String>): Action? = when (args.firstOrNull()) {
        null -> if (name == "widgets") Action.ARRANGE else Action.LIST
        "add" -> Action.ADD
        "ls", "list" -> Action.LIST
        "page" -> Action.PAGE
        else -> null
    }

    /** Where `widget page <words>` turns home ([page]). */
    sealed interface PageTarget {
        /** The page at [page], from 1. */
        data class Found(val page: Int) : PageTarget

        /** No page fits: the [message] to print, and what Copy gives on it ([copy]), if anything. */
        data class Missing(val message: String, val copy: String? = null) : PageTarget
    }

    /**
     * The page of [pages] that [words] address, by its place or its name ([WidgetPages.find]), or
     * what to say when none does: with one page, that there is one; otherwise which pages there are.
     */
    fun page(pages: List<WidgetPage>, words: String): PageTarget {
        WidgetPages.find(pages, words)?.let { return PageTarget.Found(it + 1) }
        val w = words.trim()
        if (pages.size <= 1) return PageTarget.Missing(
            "There is one widgets page\n'widget page' turns to it",
            "widget page"
        )
        val first = when {
            w.toIntOrNull() != null -> "No widgets page $w"
            pages.indices.count { WidgetPages.title(pages, it).startsWith(w, ignoreCase = true) } > 1 ->
                "'$w' names more than one page"

            else -> "No widgets page called '$w'"
        }
        val list = pages.indices.joinToString(", ") { "${it + 1} ${WidgetPages.title(pages, it)}" }
        return PageTarget.Missing("$first\nPages: $list")
    }
}
