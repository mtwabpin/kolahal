package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.script.ScriptMeta

/*
 * The filters, search and sort of the Scripts tab. Plain Kotlin with no Android types, so the JVM
 * tests check them (ScriptFiltersTest).
 */

/** Short names for what scripts use, shown in chips and in a card's pills. */
internal val USES_SHORT = mapOf("notifications" to "notifications", "clipboard" to "clipboard", "apps" to "apps", "commands" to "commands", "dnd" to "Do Not Disturb", "work" to "work profile")

/** Where a script came from. */
enum class ScriptSource { LIBRARY, SHARED, YOURS }

/** How a library or shared copy compares with where it came from. */
enum class CopyState {
    /** The same code as the library or the shared version. */
    UP_TO_DATE,

    /** A newer version exists. The copy may be edited too. */
    NEW_VERSION,

    /** You changed the code, and nothing newer exists. */
    EDITED,
}

/** What the filters, the search and the sort know about one script. */
data class ScriptFacts(
    val name: String,
    /** "lua" or "js". */
    val lang: String,
    val on: Boolean,
    val failed: Boolean,
    /** A `@secret` has no value yet. */
    val needsKey: Boolean,
    val command: Boolean,
    /** It has an interval or a daily time. */
    val timer: Boolean,
    /** It has `@on` events. */
    val event: Boolean,
    /** It may keep its line at the top of home. */
    val pinned: Boolean,
    val source: ScriptSource,
    /** Null for a script of your own. */
    val copy: CopyState?,
    /** What it declares with `@uses`. */
    val uses: Set<String>,
    /** The text the search looks in: name, title, description, author and tags. */
    val text: String,
    val lastRun: Long?,
    val changedAt: Long?,
    /** Kolahal updated it by itself, and the note about it is still shown. */
    val selfUpdated: Boolean = false,
)

/** The groups of the filter sheet. In a group, any chip may match. Across groups, all must. */
enum class FilterGroup(val title: String) {
    STATUS("Status"),
    RUNS("How it runs"),
    SOURCE("Source"),
    VERSION("Library version"),
    USES("Uses"),
    LANGUAGE("Language"),

    /** Only the "Show" of the note about scripts Kolahal updated. It is not in the sheet. */
    SELF_UPDATED(""),
}

/** One filter chip: its group, its name and a test for which scripts it keeps. */
class ScriptFilter(val id: String, val group: FilterGroup, val label: String, val keeps: (ScriptFacts) -> Boolean)

/** How the list is sorted. */
enum class ScriptSort(val label: String) {
    NAME("Name"),

    /** The latest run first. Scripts that never ran come last. */
    LAST_RUN("Last run"),

    /** The latest change of code or name first. Scripts with no recorded change come last. */
    CHANGED("Recently changed"),
}

/** The chips, and what they, the search and the sort do to a list of [ScriptFacts]. */
object ScriptFilters {
    /** The ids that the note and the banner turn on. */
    const val SELF_UPDATED = "updated"
    const val NEW_VERSION = "new-version"
    const val EDITED = "edited"

    /** Every chip, in the order the sheet shows them. */
    val ALL: List<ScriptFilter> = listOf(
        ScriptFilter("on", FilterGroup.STATUS, "On") { it.on },
        ScriptFilter("off", FilterGroup.STATUS, "Off") { !it.on },
        ScriptFilter("failed", FilterGroup.STATUS, "Failed") { it.failed },
        ScriptFilter("needs-key", FilterGroup.STATUS, "Needs a key") { it.needsKey },
        ScriptFilter("command", FilterGroup.RUNS, "Command") { it.command },
        ScriptFilter("timer", FilterGroup.RUNS, "Timer") { it.timer },
        ScriptFilter("event", FilterGroup.RUNS, "Event") { it.event },
        ScriptFilter("pinned", FilterGroup.RUNS, "Pinned") { it.pinned },
        ScriptFilter("library", FilterGroup.SOURCE, "Library") { it.source == ScriptSource.LIBRARY },
        ScriptFilter("shared", FilterGroup.SOURCE, "Shared") { it.source == ScriptSource.SHARED },
        ScriptFilter("yours", FilterGroup.SOURCE, "Yours") { it.source == ScriptSource.YOURS },
        ScriptFilter("up-to-date", FilterGroup.VERSION, "Up to date") { it.copy == CopyState.UP_TO_DATE },
        ScriptFilter(NEW_VERSION, FilterGroup.VERSION, "New version") { it.copy == CopyState.NEW_VERSION },
        ScriptFilter(EDITED, FilterGroup.VERSION, "Edited") { it.copy == CopyState.EDITED },
    ) + ScriptMeta.USES.map { what ->
        ScriptFilter("uses:$what", FilterGroup.USES, (USES_SHORT[what] ?: what).replaceFirstChar { it.uppercase() }) { what in it.uses }
    } + listOf(
        ScriptFilter("lua", FilterGroup.LANGUAGE, "Lua") { it.lang == "lua" },
        ScriptFilter("js", FilterGroup.LANGUAGE, "JavaScript") { it.lang == "js" },
        ScriptFilter(SELF_UPDATED, FilterGroup.SELF_UPDATED, "Updated by Kolahal") { it.selfUpdated },
    )

    private val byId = ALL.associateBy { it.id }

    fun byId(id: String): ScriptFilter? = byId[id]

    /** Whether [f] passes the chips in [selected]: any chip of a group, and every group that has one. */
    fun matches(f: ScriptFacts, selected: Set<String>): Boolean =
        selected.mapNotNull(byId::get).groupBy { it.group }.values.all { chips -> chips.any { it.keeps(f) } }

    /** Whether [f] has every word of [query] in its name, title, description, author or tags. */
    fun found(f: ScriptFacts, query: String): Boolean =
        query.lowercase().split(' ').filter { it.isNotBlank() }.all { it in f.text }

    /** The scripts to show: found by [query], passing [selected], in [sort] order. */
    fun shown(all: List<ScriptFacts>, selected: Set<String>, query: String, sort: ScriptSort): List<ScriptFacts> =
        sorted(all.filter { found(it, query) && matches(it, selected) }, sort)

    /**
     * The count on each chip: how many of [all] it keeps, together with the chips chosen in the other
     * groups. Chips of its own group are left out, because a chip there adds to them.
     * - In a group with no chip chosen, it is what the list shows after a tap on it.
     * - [all] should be the scripts the search found, so the counts agree with the list.
     */
    fun counts(all: List<ScriptFacts>, selected: Set<String>): Map<String, Int> {
        val chosen = selected.mapNotNull(byId::get)
        return ALL.associate { chip ->
            val others = chosen.filter { it.group != chip.group }.mapTo(HashSet()) { it.id }
            chip.id to all.count { chip.keeps(it) && matches(it, others) }
        }
    }

    /** The chips of the sheet, by group. A chip that no script has is left out, unless it is chosen. */
    fun sheetGroups(all: List<ScriptFacts>, selected: Set<String>): List<Pair<FilterGroup, List<ScriptFilter>>> =
        ALL.filter { it.group != FilterGroup.SELF_UPDATED && (it.id in selected || all.any(it.keeps)) }
            .groupBy { it.group }.toList()

    /** [list] in [sort] order. Ties go by name. */
    fun sorted(list: List<ScriptFacts>, sort: ScriptSort): List<ScriptFacts> = when (sort) {
        ScriptSort.NAME -> list.sortedBy { it.name }
        ScriptSort.LAST_RUN -> list.sortedWith(compareBy<ScriptFacts, Long?>(nullsLast(reverseOrder())) { it.lastRun }.thenBy { it.name })
        ScriptSort.CHANGED -> list.sortedWith(compareBy<ScriptFacts, Long?>(nullsLast(reverseOrder())) { it.changedAt }.thenBy { it.name })
    }

    /** Sets the Library version group to New version and Edited, and keeps the other groups. Review in the banner does this. */
    fun review(selected: Set<String>): Set<String> =
        selected.filterTo(LinkedHashSet()) { byId[it]?.group != FilterGroup.VERSION } + NEW_VERSION + EDITED
}
