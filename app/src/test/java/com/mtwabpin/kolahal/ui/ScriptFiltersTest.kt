package com.mtwabpin.kolahal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Scripts tab's filters, search and sort ([ScriptFilters]), on the 11 scripts of the design
 * draft. Its frame A2 has Command, Library and Shared on, shows 4 scripts and gives every chip's count.
 */
class ScriptFiltersTest {
    private fun script(
        name: String,
        lang: String = "lua",
        on: Boolean = true,
        failed: Boolean = false,
        needsKey: Boolean = false,
        command: Boolean = false,
        timer: Boolean = false,
        event: Boolean = false,
        pinned: Boolean = false,
        source: ScriptSource = ScriptSource.LIBRARY,
        copy: CopyState? = CopyState.UP_TO_DATE,
        uses: Set<String> = emptySet(),
        text: String = name,
        lastRun: Long? = null,
        changedAt: Long? = null,
    ) = ScriptFacts(name, lang, on, failed, needsKey, command, timer, event, pinned, source, copy, uses, text, lastRun, changedAt)

    private val sample = listOf(
        script("weather", timer = true, uses = setOf("notifications")),
        script("hn", timer = true, copy = CopyState.NEW_VERSION),
        script("lobsters", timer = true, copy = CopyState.EDITED),
        script("countdown", command = true, timer = true),
        script("cricket", lang = "js", timer = true, pinned = true),
        script("charge-reminder", event = true, uses = setOf("notifications")),
        script("aqi", lang = "js", timer = true, needsKey = true),
        script("quote", lang = "js", command = true, timer = true, failed = true, uses = setOf("notifications")),
        script("my-notes", on = false, command = true, source = ScriptSource.YOURS, copy = null, uses = setOf("clipboard", "apps")),
        script("define", lang = "js", command = true, source = ScriptSource.SHARED, copy = CopyState.NEW_VERSION, uses = setOf("clipboard")),
        script("units", on = false, command = true, source = ScriptSource.SHARED, uses = setOf("clipboard")),
    )

    /** A chip saved by another version of Kolahal, which this one doesn't have, is left out. The order stays. */
    @Test fun onlyChipsThisVersionHasAreKept() {
        assertEquals(listOf("shared", "command", ScriptFilters.EDITED), ScriptFilters.known(listOf("shared", "gone", "command", "uses:nothing", ScriptFilters.EDITED)).toList())
        assertEquals(emptySet<String>(), ScriptFilters.known(emptyList()))
    }

    /** A newer version comes before edits: an edited copy with a newer version is "New version". */
    @Test fun aCopysStatePutsANewVersionFirst() {
        assertEquals(CopyState.UP_TO_DATE, CopyState.of(newer = false, edited = false))
        assertEquals(CopyState.EDITED, CopyState.of(newer = false, edited = true))
        assertEquals(CopyState.NEW_VERSION, CopyState.of(newer = true, edited = false))
        assertEquals(CopyState.NEW_VERSION, CopyState.of(newer = true, edited = true))
    }

    private val a2 = setOf("command", "library", "shared")

    private fun names(list: List<ScriptFacts>) = list.map { it.name }

    @Test fun inAGroupAnyChipAcrossGroupsAll() {
        assertEquals(listOf("countdown", "define", "quote", "units"), names(ScriptFilters.shown(sample, a2, "", ScriptSort.NAME)))
        // Library alone, then Library or Shared: a second chip in a group adds.
        assertEquals(2, ScriptFilters.shown(sample, setOf("command", "library"), "", ScriptSort.NAME).size)
        // No chip on: all of them.
        assertEquals(11, ScriptFilters.shown(sample, emptySet(), "", ScriptSort.NAME).size)
        // Two groups that nothing has together.
        assertEquals(emptyList<String>(), names(ScriptFilters.shown(sample, setOf("event", "js"), "", ScriptSort.NAME)))
    }

    /** Each count is what the chip keeps with the other groups' chips: the draft's frame A2. */
    @Test fun countsAreTheDraftsA2() {
        val counts = ScriptFilters.counts(sample, a2)
        val expected = mapOf(
            "on" to 3, "off" to 1, "failed" to 1, "needs-key" to 0,
            "command" to 4, "timer" to 7, "event" to 1, "pinned" to 1,
            "library" to 2, "shared" to 2, "yours" to 1,
            "up-to-date" to 3, "new-version" to 1, "edited" to 0,
            "uses:notifications" to 1, "uses:clipboard" to 2, "uses:apps" to 0,
            "lua" to 2, "js" to 2,
        )
        assertEquals(expected, counts.filterKeys { it in expected })
    }

    /** With nothing on, a chip's count is what the list shows after a tap on it. */
    @Test fun withNothingOnACountIsTheListAfterATap() {
        val counts = ScriptFilters.counts(sample, emptySet())
        for (chip in ScriptFilters.ALL) {
            assertEquals(chip.id, ScriptFilters.shown(sample, setOf(chip.id), "", ScriptSort.NAME).size, counts[chip.id])
        }
        assertEquals(9, counts["on"])
        assertEquals(7, counts["lua"])
        assertEquals(4, counts["js"])
    }

    /** Counts follow the search, so they agree with the list. */
    @Test fun countsFollowTheSearch() {
        val found = sample.filter { ScriptFilters.found(it, "o") }
        val counts = ScriptFilters.counts(found, emptySet())
        assertEquals(found.count { it.on }, counts["on"])
        assertTrue(found.size < sample.size)
    }

    /** The sheet leaves out a chip no script has, unless it is on, so it can be turned off. */
    @Test fun theSheetShowsChipsSomeScriptHas() {
        val groups = ScriptFilters.sheetGroups(sample, emptySet()).toMap()
        assertEquals(listOf(FilterGroup.STATUS, FilterGroup.RUNS, FilterGroup.SOURCE, FilterGroup.VERSION, FilterGroup.USES, FilterGroup.LANGUAGE), groups.keys.toList())
        assertEquals(listOf("uses:notifications", "uses:clipboard", "uses:apps"), groups.getValue(FilterGroup.USES).map { it.id })
        val withWork = ScriptFilters.sheetGroups(sample, setOf("uses:work")).toMap()
        assertEquals(listOf("uses:notifications", "uses:clipboard", "uses:apps", "uses:work"), withWork.getValue(FilterGroup.USES).map { it.id })
        // Only your own scripts: no Library version group at all.
        val yours = ScriptFilters.sheetGroups(sample.filter { it.source == ScriptSource.YOURS }, emptySet()).toMap()
        assertFalse(FilterGroup.VERSION in yours)
        // The note's Show is never in the sheet.
        assertFalse(ScriptFilters.sheetGroups(sample.map { it.copy(selfUpdated = true) }, emptySet()).toMap().containsKey(FilterGroup.SELF_UPDATED))
    }

    /** Every word must be in the name, title, description, author or tags; case doesn't matter. */
    @Test fun theSearchFindsEveryWord() {
        val s = script("hn", text = "hn hacker news the top stories ann news reader")
        assertTrue(ScriptFilters.found(s, ""))
        assertTrue(ScriptFilters.found(s, "Hacker"))
        assertTrue(ScriptFilters.found(s, "news  ann"))
        assertFalse(ScriptFilters.found(s, "news bob"))
    }

    @Test fun sortByNameLastRunOrChange() {
        val list = listOf(
            script("b", lastRun = 100, changedAt = null),
            script("a", lastRun = null, changedAt = 50),
            script("d", lastRun = 300, changedAt = 10),
            script("c", lastRun = null, changedAt = null),
        )
        assertEquals(listOf("a", "b", "c", "d"), names(ScriptFilters.sorted(list, ScriptSort.NAME)))
        // The latest first; never run (or never changed) last, by name.
        assertEquals(listOf("d", "b", "a", "c"), names(ScriptFilters.sorted(list, ScriptSort.LAST_RUN)))
        assertEquals(listOf("a", "d", "b", "c"), names(ScriptFilters.sorted(list, ScriptSort.CHANGED)))
    }

    /** Review sets Library version to New version and Edited, and keeps the other groups. */
    @Test fun reviewShowsTheCopiesThatDiffer() {
        val picked = ScriptFilters.review(setOf("up-to-date", "command", "lua"))
        assertEquals(setOf("command", "lua", ScriptFilters.NEW_VERSION, ScriptFilters.EDITED), picked)
        assertEquals(listOf("define", "hn", "lobsters"), names(ScriptFilters.shown(sample, ScriptFilters.review(emptySet()), "", ScriptSort.NAME)))
    }

    /** Show in the note about scripts Kolahal updated: only those, and joined with the rest as a group of its own. */
    @Test fun theNotesShowKeepsTheUpdatedScripts() {
        val updated = setOf("weather", "countdown", "cricket")
        val list = sample.map { it.copy(selfUpdated = it.name in updated) }
        assertEquals(listOf("countdown", "cricket", "weather"), names(ScriptFilters.shown(list, setOf(ScriptFilters.SELF_UPDATED), "", ScriptSort.NAME)))
        assertEquals(listOf("countdown"), names(ScriptFilters.shown(list, setOf(ScriptFilters.SELF_UPDATED, "command"), "", ScriptSort.NAME)))
    }

    /** An id no chip has (one saved by an older version) is ignored. */
    @Test fun anUnknownIdIsIgnored() {
        assertEquals(11, ScriptFilters.shown(sample, setOf("differs"), "", ScriptSort.NAME).size)
    }
}
