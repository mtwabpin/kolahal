package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.script.ScriptMeta

/*
 * The filters, search and sort of the Scripts tab ([ScriptFilters]), and what the tab works out
 * about its scripts before it filters and draws them ([ScriptPage]). Plain Kotlin with no Android
 * types, so the JVM tests check them (ScriptFiltersTest, ScriptPageTest).
 */

/** Short names for what scripts use, shown in chips and in a card's pills. */
internal val USES_SHORT = mapOf(
    "notifications" to "notifications", "clipboard" to "clipboard", "apps" to "apps", "commands" to "commands", "dnd" to "Do Not Disturb", "work" to "work profile",
    "todos" to "to-dos", "notes" to "notes", "reminders" to "reminders", "calendar" to "calendar", "feeds" to "feeds",
)

/** Where a script came from. */
enum class ScriptSource { LIBRARY, SHARED, YOURS }

/** How a library or shared copy compares with where it came from. */
enum class CopyState {
    /** The same code as the library or the shared version. */
    UP_TO_DATE,

    /** A newer version exists. The copy may be edited too. */
    NEW_VERSION,

    /** You changed the code, and nothing newer exists. */
    EDITED;

    companion object {
        /** The state of a library or shared copy. A newer version comes first: the copy may be [edited] too. */
        fun of(newer: Boolean, edited: Boolean) = when {
            newer -> NEW_VERSION
            edited -> EDITED
            else -> UP_TO_DATE
        }
    }
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

    /** The chips of [ids] that this version has, in their order. A saved id of an older version is left out. */
    fun known(ids: List<String>): Set<String> = ids.filterTo(LinkedHashSet()) { byId(it) != null }

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

/** Whether the script's last run failed: an error, a stop, or the time limit. */
internal fun Script.failed() = lastStatus?.let { it.startsWith("error") || it.startsWith("stopped") || it == "timeout" } == true

/**
 * What the Scripts tab works out about its scripts before it filters and draws them.
 * - The page keeps each result (`remember`, `produceState`), and it reads the library, the shared
 *   list and the secrets itself.
 * - These are only the calculations, so the JVM tests check them (ScriptPageTest).
 */
internal object ScriptPage {
    /**
     * The [ScriptInfo] of each script. A script's code is read once per version: [kept] has what
     * was read before, by name and code, and loses what no script has any more.
     */
    fun infos(scripts: List<Script>, kept: MutableMap<Pair<String, String>, ScriptCode>): List<ScriptInfo> {
        kept.keys.retainAll(scripts.mapTo(HashSet()) { it.name to it.code })
        return scripts.map { s -> ScriptInfo(s, kept.getOrPut(s.name to s.code) { ScriptCode(s.code, s.name) }) }
    }

    /**
     * The copies from Get more scripts among [scripts], by name.
     * - [latest]: the list kept from Get more scripts. Empty when none is kept.
     * - [copyOf]: how a script compares with what was installed and with its entry in the list
     *   ([ScriptCatalog.copy]). Null for a script that wasn't installed from there.
     */
    fun sharedCopies(
        scripts: List<Script>,
        latest: List<ScriptCatalog.Entry>,
        copyOf: (Script, ScriptCatalog.Entry?) -> ScriptCatalog.Copy?,
    ): Map<String, SharedCopy> {
        val byName = latest.associateBy { it.name }
        return scripts.mapNotNull { s -> copyOf(s, byName[s.name])?.let { s.name to SharedCopy(it, byName[s.name]) } }.toMap()
    }

    /**
     * How each script compares with where it came from, by script id. Null for a script of your own.
     * - A library copy has the name and the language of a library script, and that one is installed.
     * - Otherwise, a copy from Get more scripts if it is one of [shared].
     */
    fun copies(infos: List<ScriptInfo>, libByName: Map<String, LibRow>, shared: Map<String, SharedCopy>): Map<Long, ScriptCopy?> =
        infos.associate { i ->
            val s = i.script
            val lib = libByName[s.name]?.takeIf { it.state != ScriptLibrary.State.NOT_INSTALLED && it.entry.lang == s.lang }
            s.id to (lib?.let { ScriptCopy.of(it) } ?: shared[s.name]?.let { ScriptCopy.of(it) })
        }

    /**
     * The declared secrets of each script that has any, by script id: those with no value yet, and
     * those that are set. [isSet] gives the names of a script's secrets that have a value.
     */
    fun secretNames(infos: List<ScriptInfo>, isSet: (Script) -> Set<String>): Map<Long, SecretNames> =
        infos.filter { it.secrets.isNotEmpty() }.associate { i ->
            val declared = i.secrets.map { it.name }
            val set = isSet(i.script)
            i.script.id to SecretNames(missing = declared.filter { it !in set }, set = declared.filter { it in set })
        }

    /** The names in [updated] (the library copies Kolahal updated by itself) that are still among [scripts]. */
    fun selfUpdated(updated: List<String>, scripts: List<Script>): Set<String> =
        updated.filterTo(HashSet()) { n -> scripts.any { it.name == n } }

    /** What the filters, the search and the sort know about each script, in the order of [infos]. */
    fun facts(
        infos: List<ScriptInfo>,
        copies: Map<Long, ScriptCopy?>,
        secretNames: Map<Long, SecretNames>,
        shared: Map<String, SharedCopy>,
        selfUpdated: Set<String>,
    ): List<ScriptFacts> = infos.map { i ->
        val name = i.script.name
        factsOf(i, copies[i.script.id], secretNames[i.script.id]?.missing.orEmpty().isNotEmpty(), shared[name]?.entry?.tags.orEmpty(), name in selfUpdated)
    }

    /** What the filters know about [info]: see [ScriptFacts]. */
    private fun factsOf(info: ScriptInfo, copy: ScriptCopy?, needsKey: Boolean, tags: List<String>, selfUpdated: Boolean): ScriptFacts {
        val s = info.script
        return ScriptFacts(
            name = s.name,
            lang = s.lang.ext,
            on = s.enabled,
            failed = s.failed(),
            needsKey = needsKey,
            command = info.isCommand,
            timer = info.timed,
            event = info.events.isNotEmpty(),
            pinned = info.grants.allows(ScriptAccess.HEADER),
            source = copy?.source ?: ScriptSource.YOURS,
            copy = copy?.state,
            uses = info.uses.toSet(),
            text = (listOfNotNull(s.name, info.title, info.desc, info.author) + tags).joinToString(" ").lowercase(),
            lastRun = s.lastRun,
            changedAt = s.changedAt,
            selfUpdated = selfUpdated,
        )
    }

    /** The copies that differ from where they came from, each with its script, in the order of [infos]. The banner is about these. */
    fun differing(infos: List<ScriptInfo>, copies: Map<Long, ScriptCopy?>): List<Pair<Script, ScriptCopy>> =
        infos.mapNotNull { i -> copies[i.script.id]?.takeIf { it.state != CopyState.UP_TO_DATE }?.let { i.script to it } }

    /** What the banner is about: each copy that differs, and how. */
    fun bannerSignature(differing: List<Pair<Script, ScriptCopy>>): Set<String> =
        differing.map { (s, c) -> "${s.name}:${c.state}" }.toSet()

    /**
     * Whether the banner shows: some copies differ, and the user didn't close it for all of them.
     * [hiddenFor] is what [hiddenText] gave when the user closed it. Null: it was never closed.
     */
    fun bannerShows(signature: Set<String>, hiddenFor: String?): Boolean =
        signature.isNotEmpty() && (hiddenFor == null || !hiddenFor.split(',').toSet().containsAll(signature))

    /** [signature] as it is kept when the user closes the banner. */
    fun hiddenText(signature: Set<String>): String = signature.sorted().joinToString(",")
}
