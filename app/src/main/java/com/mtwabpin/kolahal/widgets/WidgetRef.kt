/**
 * Manual Review - Pending
 */
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
            return if (ScriptNames.NAME.matches(script) && ID.matches(id)) WidgetRef(
                script,
                id
            ) else null
        }

        /** Whether a script whose placed widgets have the ids [taken] may get one more: it has fewer than [MAX_PER_SCRIPT]. */
        fun roomFor(taken: Collection<String>): Boolean = taken.size < MAX_PER_SCRIPT

        /**
         * The id for one more further widget of a script whose placed widgets have the ids [taken],
         * and that was given the further ids up to [given] before (0 when none, [GivenIds]): the
         * number after both, "2" at the least. An id is never given twice to one script, so a new
         * widget never finds what the script stored for a removed one (a habit's days, a match).
         * Null when the script has no room for one ([roomFor]), or when the number is too long for an [ID].
         */
        fun nextId(taken: Collection<String>, given: Int): String? {
            if (!roomFor(taken)) return null
            val highest = maxOf(given, taken.maxOfOrNull { it.toIntOrNull() ?: 0 } ?: 0, 1)
            return (highest.toLong() + 1).toString().takeIf { ID.matches(it) }
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
        all.mapNotNull { WidgetSource.scriptRef(it.source) }.groupBy({ it.script }, { it.id })
            .mapValues { it.value.toSet() }

    /** The row's text for [ids]: a JSON list, the first widget's "" first. */
    fun encode(ids: Set<String>): String =
        Json.stringify(ids.sortedWith(compareBy({ it.length }, { it })))

    /** The ids in a row's [text]. Null with no row, and for a text that is not a list of ids. */
    fun parse(text: String?): Set<String>? {
        val list =
            text?.let { runCatching { Json.parse(it) as? List<*> }.getOrNull() } ?: return null
        return list.map {
            (it as? String)?.takeIf { id -> id.isEmpty() || WidgetRef.ID.matches(id) }
                ?: return null
        }.toSet()
    }

    /**
     * Whether the widget [id] is on the page, by its script's row ([ids]; null when there is none).
     * A name with no row was placed before rows were kept. It had one widget then, the first.
     * The script's `widget` access must be on as well: the caller checks that.
     */
    fun holds(ids: Set<String>?, id: String): Boolean = ids?.contains(id) ?: id.isEmpty()
}

/**
 * The highest further id each script was given ([WidgetRef.nextId]), by script name. What a script
 * stores for one of its widgets (`days2`) stays when the widget is removed, so a removed widget's id
 * must not come back. Kept beside the placed list, and in a backup, which brings the scripts' stores
 * back with it. A widget that comes in another way (a backup, a rename) counts as given ([noted]).
 */
object GivenIds {
    /** The stored and backup form: a JSON object of names and numbers. */
    fun encode(given: Map<String, Int>): String = Json.stringify(given)

    /** What [encode] wrote. A name or a number that can't be one is skipped. */
    fun parse(text: String?): Map<String, Int> =
        of(text?.let { runCatching { Json.parse(it) as? Map<*, *> }.getOrNull() })

    /** The ids in a backup's object ([encode]'s form): whole numbers from 2, by a script's name. */
    fun of(raw: Map<*, *>?): Map<String, Int> = raw.orEmpty().mapNotNull { (k, v) ->
        val name = (k as? String)?.takeIf { ScriptNames.NAME.matches(it) } ?: return@mapNotNull null
        val n = (v as? Number)?.toDouble()?.takeIf { it >= 2 && it < 1e8 && it % 1.0 == 0.0 } ?: return@mapNotNull null
        name to n.toInt()
    }.toMap()

    /** [given] with each further widget in [all] counted as given. */
    fun noted(given: Map<String, Int>, all: List<PlacedWidget>): Map<String, Int> =
        merged(given, all.mapNotNull { WidgetSource.scriptRef(it.source) }
            .mapNotNull { ref -> ref.id.toIntOrNull()?.let { ref.script to it } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.max() })

    /** The higher of the two for each name. */
    fun merged(a: Map<String, Int>, b: Map<String, Int>): Map<String, Int> =
        b.entries.fold(a) { out, (name, n) -> if ((out[name] ?: 0) >= n) out else out + (name to n) }

    /** [given] after the script [from] was renamed to [to]: its widgets, and what it stored, go with it. */
    fun renamed(given: Map<String, Int>, from: String, to: String): Map<String, Int> {
        val n = given[from] ?: return given
        if (from == to) return given
        return merged(given - from, mapOf(to to n))
    }
}
