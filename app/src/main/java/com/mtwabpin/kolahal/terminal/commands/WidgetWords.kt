/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

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
}
