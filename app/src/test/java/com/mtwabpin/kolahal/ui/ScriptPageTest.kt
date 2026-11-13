package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Scripts tab works out about its scripts before it filters and draws them ([ScriptPage]). */
class ScriptPageTest {
    private fun script(id: Long, name: String, code: String = "print('hi')", lang: ScriptLang = ScriptLang.LUA, enabled: Boolean = true, lastStatus: String? = null) =
        Script(id, name, lang, code, 0, enabled, false, null, lastStatus)

    private fun infos(vararg scripts: Script) = ScriptPage.infos(scripts.toList(), HashMap())

    private fun libRow(name: String, state: ScriptLibrary.State, lang: ScriptLang = ScriptLang.LUA, newer: Boolean = false) =
        LibRow(ScriptLibrary.Entry("$name.${lang.ext}", name, lang, name, "", command = false, suggestedEvery = 0), state, emptyList(), newer)

    private fun sharedEntry(name: String, tags: List<String> = emptyList()) =
        ScriptCatalog.Entry(name, ScriptLang.LUA, "$name.lua", name, "", "", "1.0", tags, emptyList(), emptyList(), false, null, emptyList(), null, "sha-$name")

    private fun sharedCopy(name: String, edited: Boolean = false, newer: Boolean = false, tags: List<String> = emptyList(), listed: Boolean = true) =
        SharedCopy(ScriptCatalog.Copy(edited = edited, newer = newer), if (listed) sharedEntry(name, tags) else null)

    /** A script's code is read once per version: an unchanged script keeps what was read, a changed or deleted one loses it. */
    @Test fun codeIsReadOncePerVersion() {
        val kept = HashMap<Pair<String, String>, ScriptCode>()
        val a = script(1, "a", "-- @title A\nreturn 1")
        val b = script(2, "b", "-- @title B\nreturn 2")
        val first = ScriptPage.infos(listOf(a, b), kept)
        assertEquals(listOf("A", "B"), first.map { it.title })
        assertEquals(listOf(a, b), first.map { it.script })
        val readOfA = kept.getValue("a" to a.code)
        val readOfB = kept.getValue("b" to b.code)

        // a ran (the same code), b was saved with new code, and c is new.
        val b2 = b.copy(code = "-- @title B two\nreturn 2")
        val second = ScriptPage.infos(listOf(a.copy(lastRun = 5), b2, script(3, "c")), kept)
        assertEquals(listOf("A", "B two", null), second.map { it.title })
        assertSame(readOfA, kept.getValue("a" to a.code))
        assertNotSame(readOfB, kept.getValue("b" to b2.code))
        assertEquals(setOf("a" to a.code, "b" to b2.code, "c" to "print('hi')"), kept.keys)

        // Deleted scripts are forgotten.
        ScriptPage.infos(listOf(b2), kept)
        assertEquals(setOf("b" to b2.code), kept.keys)
    }

    /** Only scripts installed from Get more scripts are shared copies. Each has its entry in the kept list, if it is still there. */
    @Test fun sharedCopiesAreTheScriptsInstalledFromGetMoreScripts() {
        val scripts = listOf(script(1, "define"), script(2, "units"), script(3, "mine"))
        val asked = ArrayList<Pair<String, String?>>()
        val copies = ScriptPage.sharedCopies(scripts, latest = listOf(sharedEntry("define", listOf("words")), sharedEntry("other"))) { s, entry ->
            asked += s.name to entry?.name
            if (s.name == "mine") null else ScriptCatalog.Copy(edited = s.name == "units", newer = entry != null)
        }
        // Each script is compared with its own entry of the list, or with none.
        assertEquals(listOf("define" to "define", "units" to null, "mine" to null), asked)
        assertEquals(setOf("define", "units"), copies.keys)
        assertEquals(listOf("words"), copies.getValue("define").entry?.tags)
        assertTrue(copies.getValue("define").copy.newer)
        assertNull(copies.getValue("units").entry)
        assertTrue(copies.getValue("units").copy.edited)
        assertEquals(emptyMap<String, SharedCopy>(), ScriptPage.sharedCopies(scripts, emptyList()) { _, _ -> null })
    }

    /** A library copy has a library script's name and language. Then come the copies from Get more scripts. The rest are yours. */
    @Test fun copiesAreLibraryCopiesThenSharedOnes() {
        val list = infos(
            script(1, "weather"),
            script(2, "hn"),
            script(3, "cricket", lang = ScriptLang.JS),
            script(4, "define"),
            script(5, "units"),
            script(6, "mine"),
            script(7, "gone"),
        )
        val libByName = listOf(
            libRow("weather", ScriptLibrary.State.INSTALLED),
            libRow("hn", ScriptLibrary.State.MODIFIED, newer = true),
            // The library's cricket is a Lua script: your JavaScript one only has its name.
            libRow("cricket", ScriptLibrary.State.MODIFIED),
            // Both a library script and a shared one are named define: the library comes first.
            libRow("define", ScriptLibrary.State.UPDATE_AVAILABLE, newer = true),
            libRow("gone", ScriptLibrary.State.NOT_INSTALLED),
        ).associateBy { it.entry.name }
        val shared = mapOf(
            "define" to sharedCopy("define", newer = true),
            "units" to sharedCopy("units", edited = true),
            "cricket" to sharedCopy("cricket", edited = true, newer = true, listed = false),
        )
        val copies = ScriptPage.copies(list, libByName, shared)
        assertEquals((1L..7L).toSet(), copies.keys)
        fun what(id: Long) = copies[id]?.let { Triple(it.source, it.state, it.edited) }
        assertEquals(Triple(ScriptSource.LIBRARY, CopyState.UP_TO_DATE, false), what(1))
        assertEquals(Triple(ScriptSource.LIBRARY, CopyState.NEW_VERSION, true), what(2))
        assertEquals(Triple(ScriptSource.SHARED, CopyState.NEW_VERSION, true), what(3))
        assertEquals(Triple(ScriptSource.LIBRARY, CopyState.NEW_VERSION, false), what(4))
        assertEquals(Triple(ScriptSource.SHARED, CopyState.EDITED, true), what(5))
        assertNull(what(6))
        assertNull(what(7))
        // A library copy knows its library script, a shared one its entry in the list.
        assertSame(libByName.getValue("hn"), copies[2]?.lib)
        assertEquals("units", copies[5]?.shared?.name)
        // Update or Reset needs the source: a shared copy that left the list has none.
        assertEquals(listOf(false, true, false, true, true), (1L..5L).map { copies[it]!!.canRestore })
    }

    /** A script's secrets, in the order it declares them: those with no value yet, and those that are set. By script id. */
    @Test fun secretNamesAreSplitIntoMissingAndSet() {
        val keys = "-- @secret A_KEY example.com One\n-- @secret B_KEY example.com Two\n-- @secret C_KEY example.com Three\nreturn 1"
        val list = infos(script(1, "three", keys), script(2, "none"), script(3, "all", "-- @secret X_KEY example.com X\nreturn 1"))
        val asked = ArrayList<String>()
        val names = ScriptPage.secretNames(list) { s ->
            asked += s.name
            if (s.name == "three") setOf("C_KEY", "A_KEY", "NOT_DECLARED") else setOf("X_KEY")
        }
        // A script that declares no secret is not asked about, and has no entry.
        assertEquals(listOf("three", "all"), asked)
        assertEquals(setOf(1L, 3L), names.keys)
        assertEquals(listOf("B_KEY"), names.getValue(1).missing)
        assertEquals(listOf("A_KEY", "C_KEY"), names.getValue(1).set)
        assertEquals(emptyList<String>(), names.getValue(3).missing)
        assertEquals(listOf("X_KEY"), names.getValue(3).set)
    }

    /** The note about scripts Kolahal updated counts only those still here. */
    @Test fun selfUpdatedScriptsAreTheOnesStillHere() {
        val scripts = listOf(script(1, "weather"), script(2, "hn"))
        assertEquals(setOf("hn"), ScriptPage.selfUpdated(listOf("hn", "deleted"), scripts))
        assertEquals(emptySet<String>(), ScriptPage.selfUpdated(emptyList(), scripts))
        assertEquals(emptySet<String>(), ScriptPage.selfUpdated(listOf("hn"), emptyList()))
    }

    /** What the filters know about each script: where it came from, what it needs, and the text the search looks in. */
    @Test fun factsAreWhatTheFiltersNeed() {
        val list = infos(
            script(1, "weather", "-- @title Weather now\n-- @desc Rain or sun\n-- @author Asha\n-- @secret W_KEY example.com Key\nreturn 1", lastStatus = "error: boom"),
            script(2, "define", "-- @command\nreturn 1", lang = ScriptLang.JS, enabled = false),
            script(3, "mine"),
        )
        val shared = mapOf("define" to sharedCopy("define", newer = true, tags = listOf("Words", "lookup")))
        val copies = ScriptPage.copies(list, mapOf("weather" to libRow("weather", ScriptLibrary.State.MODIFIED)), shared)
        val secrets = mapOf(1L to SecretNames(missing = listOf("W_KEY"), set = emptyList()))
        val facts = ScriptPage.facts(list, copies, secrets, shared, selfUpdated = setOf("weather"))
        assertEquals(listOf("weather", "define", "mine"), facts.map { it.name })

        val weather = facts[0]
        assertEquals(listOf(ScriptSource.LIBRARY, CopyState.EDITED), listOf(weather.source, weather.copy))
        assertEquals(listOf(true, true, true, true), listOf(weather.on, weather.failed, weather.needsKey, weather.selfUpdated))
        assertEquals("weather weather now rain or sun asha", weather.text)
        assertEquals("lua", weather.lang)

        val define = facts[1]
        assertEquals(listOf(ScriptSource.SHARED, CopyState.NEW_VERSION), listOf(define.source, define.copy))
        assertEquals(listOf(false, false, false, false, true), listOf(define.on, define.failed, define.needsKey, define.selfUpdated, define.command))
        // The tags of a shared script are searched too.
        assertEquals("define words lookup", define.text)
        assertEquals("js", define.lang)

        val mine = facts[2]
        assertEquals(listOf(ScriptSource.YOURS, null), listOf(mine.source, mine.copy))
        // A key that is set no longer counts as needed.
        val set = mapOf(1L to SecretNames(missing = emptyList(), set = listOf("W_KEY")))
        assertFalse(ScriptPage.facts(list, copies, set, shared, emptySet())[0].needsKey)
    }

    /** The banner is about the copies that differ. Closed, it stays closed until a copy differs that it wasn't closed for. */
    @Test fun theBannerShowsForCopiesItWasNotClosedFor() {
        val list = infos(script(1, "weather"), script(2, "hn"), script(3, "lobsters"), script(4, "mine"))
        fun differing(vararg rows: LibRow) = ScriptPage.differing(list, ScriptPage.copies(list, rows.associateBy { it.entry.name }, emptyMap()))

        val two = differing(
            libRow("weather", ScriptLibrary.State.INSTALLED),
            libRow("hn", ScriptLibrary.State.UPDATE_AVAILABLE, newer = true),
            libRow("lobsters", ScriptLibrary.State.MODIFIED),
        )
        // Up to date copies and your own scripts are left out.
        assertEquals(listOf("hn" to CopyState.NEW_VERSION, "lobsters" to CopyState.EDITED), two.map { (s, c) -> s.name to c.state })
        val signature = ScriptPage.bannerSignature(two)
        assertEquals(setOf("hn:NEW_VERSION", "lobsters:EDITED"), signature)
        assertTrue(ScriptPage.bannerShows(signature, hiddenFor = null))

        // Closed for these two: it stays closed for them, and for fewer of them.
        val hidden = ScriptPage.hiddenText(signature)
        assertEquals("hn:NEW_VERSION,lobsters:EDITED", hidden)
        assertFalse(ScriptPage.bannerShows(signature, hidden))
        assertFalse(ScriptPage.bannerShows(setOf("hn:NEW_VERSION"), hidden))

        // Another copy differs, or one differs in a new way: it shows again.
        assertTrue(ScriptPage.bannerShows(signature + "weather:EDITED", hidden))
        val newer = differing(libRow("hn", ScriptLibrary.State.UPDATE_AVAILABLE, newer = true), libRow("lobsters", ScriptLibrary.State.MODIFIED, newer = true))
        assertEquals(setOf("hn:NEW_VERSION", "lobsters:NEW_VERSION"), ScriptPage.bannerSignature(newer))
        assertTrue(ScriptPage.bannerShows(ScriptPage.bannerSignature(newer), hidden))

        // Nothing differs: no banner, closed before or not.
        val none = differing(libRow("weather", ScriptLibrary.State.INSTALLED))
        assertEquals(emptyList<Pair<Script, ScriptCopy>>(), none)
        assertFalse(ScriptPage.bannerShows(ScriptPage.bannerSignature(none), hiddenFor = null))
        assertFalse(ScriptPage.bannerShows(emptySet(), hidden))
    }

    /** A run that ended in an error, was stopped or ran out of time failed. */
    @Test fun aFailedRunIsAnErrorAStopOrATimeout() {
        assertEquals(
            listOf(true, true, true, false, false),
            listOf("error: boom (line 2)", "stopped: internet off", "timeout", "ok in 12ms", null).map { script(1, "s", lastStatus = it).failed() },
        )
    }
}
