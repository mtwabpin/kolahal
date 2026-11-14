package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.script.ScriptNames

/**
 * One widget of a script. A script can have several widgets on the page, each with its own options.
 * - [id] is "" for the script's first widget. A further widget has "2", "3", …
 * - [key] is the one text that names the widget everywhere: in its placed entry (`script:weather#2`),
 *   in what it shows ([WidgetStates]), and in its options scope.
 *
 * The first widget's key is the script's name alone. So a widget placed before a script could have
 * several keeps every key it had, and nothing stored is rewritten.
 */
data class WidgetRef(val script: String, val id: String = "") {
    /** The script's first widget: the one a timer's run fills, with the script's own options. */
    val first: Boolean get() = id.isEmpty()

    /** `weather` for the first widget, `weather#2` for a further one. */
    val key: String get() = if (first) script else further(script) + id

    /** [title] as this widget's name: "Weather" for the first widget, "Weather 2" for a further one. */
    fun titled(title: String): String = if (first) title else "$title $id"

    companion object {
        /** Between a script's name and a further widget's id. A script's name never has it ([ScriptNames.NAME]). */
        const val SEPARATOR = '#'

        /** The allowed form of a further widget's id. */
        val ID = Regex("^[a-z0-9]{1,8}$")

        /** The most widgets one script may have on the page. */
        const val MAX_PER_SCRIPT = 6

        /**
         * What every key of [script]'s further widgets starts with: `weather#`. Code that looks for
         * all keys of a script matches the name itself, or this at the start.
         */
        fun further(script: String): String = script + SEPARATOR

        /** Whether [key] names a widget of [script]: its first widget, or a further one. */
        fun isOf(key: String, script: String): Boolean = key == script || key.startsWith(further(script))

        /**
         * The widget that [key] names, or null when it names none.
         * - Without a [SEPARATOR] the whole text is a script's name, as it always was.
         * - With one, the name must be a script's name and the id must match [ID].
         */
        fun parse(key: String?): WidgetRef? {
            if (key.isNullOrEmpty()) return null
            val at = key.indexOf(SEPARATOR)
            if (at < 0) return WidgetRef(key)
            val script = key.substring(0, at)
            val id = key.substring(at + 1)
            return if (ScriptNames.NAME.matches(script) && ID.matches(id)) WidgetRef(script, id) else null
        }

        /** Whether a script whose placed widgets have the ids [taken] may get one more: it has fewer than [MAX_PER_SCRIPT]. */
        fun roomFor(taken: Collection<String>): Boolean = taken.size < MAX_PER_SCRIPT

        /**
         * The id for one more further widget of a script whose placed widgets have the ids [taken]:
         * the smallest free one of "2", "3", … Null when the script has no room for one ([roomFor]).
         */
        fun nextId(taken: Collection<String>): String? {
            if (!roomFor(taken)) return null
            return generateSequence(2) { it + 1 }.map { it.toString() }.first { it !in taken }
        }
    }
}

/**
 * Which widgets of each script are on the page: the ids, by script name. The placed list is the
 * truth. The database keeps a copy, one row for each name ([com.mtwabpin.kolahal.data.Stores.WIDGET_PLACED]),
 * because the `:script` process can't see the list. This is the row's form and its one rule.
 */
object PlacedIds {
    /** The ids of every script widget in [all], by script name. A name with no widget is not in it. */
    fun of(all: List<PlacedWidget>): Map<String, Set<String>> =
        all.mapNotNull { WidgetSource.scriptRef(it.source) }.groupBy({ it.script }, { it.id }).mapValues { it.value.toSet() }

    /** The row's text for [ids]: a JSON list, the first widget's "" first. */
    fun encode(ids: Set<String>): String = Json.stringify(ids.sortedWith(compareBy({ it.length }, { it })))

    /** The ids in a row's [text]. Null with no row, and for a text that is not a list of ids. */
    fun parse(text: String?): Set<String>? {
        val list = text?.let { runCatching { Json.parse(it) as? List<*> }.getOrNull() } ?: return null
        return list.map { (it as? String)?.takeIf { id -> id.isEmpty() || WidgetRef.ID.matches(id) } ?: return null }.toSet()
    }

    /**
     * Whether the widget [id] is on the page, by its script's row ([ids]; null when there is none).
     * A name with no row was placed before rows were kept. It had one widget then, the first.
     * The script's `widget` access must be on as well: the caller checks that.
     */
    fun holds(ids: Set<String>?, id: String): Boolean = ids?.contains(id) ?: id.isEmpty()
}
