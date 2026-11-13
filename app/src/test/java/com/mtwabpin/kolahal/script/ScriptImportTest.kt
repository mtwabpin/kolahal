package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ImportPlan.Place
import com.mtwabpin.kolahal.script.ImportPlan.Skip
import com.mtwabpin.kolahal.script.ImportPlan.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What an import (a picked file, the script folder, a backup) decides for one script, before it writes anything. */
class ScriptImportTest {
    private fun script(code: String = "return 1", lang: ScriptLang = ScriptLang.LUA, enabled: Boolean = true) =
        Script(1, "s", lang, code, intervalMin = 60, enabled = enabled, notify = false, lastRun = null, lastStatus = null, atTimes = listOf("08:00"))

    private fun plan(existing: Script?, code: String = "return 1", lang: ScriptLang = ScriptLang.LUA, changedEarlier: Boolean = false) =
        ImportPlan.plan(existing, lang, code, current = "0.5.8", changedEarlier = changedEarlier)

    /** The rule every import shares: a new script and replaced code are left switched off. */
    @Test fun newOrChangedCodeArrivesOff() {
        assertEquals(Place(Step.CREATE, off = true), plan(existing = null))
        assertEquals(Place(Step.REPLACE, off = true), plan(script("return 1"), code = "return 2"))
    }

    /** Code you already have follows the switch that came with it. */
    @Test fun theSameCodeMayFollowItsSwitch() {
        assertEquals(Place(Step.KEEP, off = false), plan(script()))
        assertEquals(Place(Step.KEEP, off = false), plan(script(enabled = false)))
    }

    /** A second entry with the same name finds the first one's code in place: it is not code you already had. */
    @Test fun codeThisImportPutThereStaysOff() {
        assertEquals(Place(Step.KEEP, off = true), plan(script(), changedEarlier = true))
    }

    @Test fun aScriptInAnotherLanguageIsLeftAlone() {
        assertEquals(Skip(ScriptImport.Why.OtherLanguage(ScriptLang.JS)), plan(script(lang = ScriptLang.JS)))
        // Whatever its code: the same text in the other language is still another script.
        assertEquals(Skip(ScriptImport.Why.OtherLanguage(ScriptLang.LUA)), plan(script("x"), code = "x", lang = ScriptLang.JS))
    }

    /** `@requires` a newer Kolahal: skipped, new or not, and before the language is looked at. */
    @Test fun codeForANewerKolahalIsSkipped() {
        val newer = "-- @requires 0.6.0\nreturn 1"
        assertEquals(Skip(ScriptImport.Why.NeedsNewer("0.6.0")), plan(existing = null, code = newer))
        assertEquals(Skip(ScriptImport.Why.NeedsNewer("0.6.0")), plan(script(), code = newer))
        assertEquals(Skip(ScriptImport.Why.NeedsNewer("0.6.0")), plan(script(lang = ScriptLang.JS), code = newer))
        // This version, an older one, or something that isn't a version: it comes in.
        for (line in listOf("-- @requires 0.5.8", "-- @requires 0.4.0", "-- @requires soon")) {
            assertEquals(line, Place(Step.CREATE, off = true), plan(existing = null, code = "$line\nreturn 1"))
        }
    }

    /** A timer from a file: none, WorkManager's shortest for anything under it, a week at most. */
    @Test fun aTimerFromAFileIsBroughtInRange() {
        assertEquals(0, ImportPlan.minutes(0))
        assertEquals(0, ImportPlan.minutes(-5))
        assertEquals(ScriptScheduler.MIN_INTERVAL_MIN, ImportPlan.minutes(1))
        assertEquals(ScriptScheduler.MIN_INTERVAL_MIN, ImportPlan.minutes(14))
        assertEquals(15, ImportPlan.minutes(15))
        assertEquals(90, ImportPlan.minutes(90))
        assertEquals(7 * 24 * 60, ImportPlan.minutes(7L * 24 * 60 + 1))
        assertEquals(7 * 24 * 60, ImportPlan.minutes(Long.MAX_VALUE))
    }

    /** Daily times from a file: read as clock times, each once, in order; what isn't a time is left out. */
    @Test fun dailyTimesFromAFile() {
        assertEquals(listOf("07:00", "18:30"), ImportPlan.times(listOf("18:30", "7:00", "07:00", "25:00", "soon", 9, null)))
        assertEquals(emptyList<String>(), ImportPlan.times(emptyList<Any?>()))
    }

    /** An update is a change to the code, a timer, notifications or the switch. A run's time or result is not. */
    @Test fun whatCountsAsUpdated() {
        val before = script()
        assertFalse(ImportPlan.changed(before, before.copy()))
        assertFalse(ImportPlan.changed(before, before.copy(lastRun = 5L, lastStatus = "ok", changedAt = 9L, access = mapOf("internet" to false))))
        assertTrue(ImportPlan.changed(before, before.copy(code = "return 2")))
        assertTrue(ImportPlan.changed(before, before.copy(intervalMin = 30)))
        assertTrue(ImportPlan.changed(before, before.copy(atTimes = emptyList())))
        assertTrue(ImportPlan.changed(before, before.copy(notify = true)))
        assertTrue(ImportPlan.changed(before, before.copy(enabled = false)))
    }

    /** A folder's and a backup's names: a script name's form, and no built-in command's name. */
    @Test fun namesAFolderOrABackupMayBring() {
        assertTrue(ScriptImport.named("hn"))
        assertTrue(ScriptImport.named("my_script-2"))
        assertFalse(ScriptImport.named("help"))
        assertFalse(ScriptImport.named("Has Space"))
        assertFalse(ScriptImport.named("UPPER"))
        assertFalse(ScriptImport.named(""))
        assertFalse(ScriptImport.named("x".repeat(41)))
    }
}
