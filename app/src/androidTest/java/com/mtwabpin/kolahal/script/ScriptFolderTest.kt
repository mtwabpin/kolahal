package com.mtwabpin.kolahal.script

import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** What `script import` leaves of a script's timers and switches, from a plain folder of files. */
@RunWith(AndroidJUnit4::class)
class ScriptFolderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scripts = ScriptRepository.get(context)
    private val dir = File(context.cacheDir, "script-folder-test")

    /** What it suggests: an hour, two daily times, notifications. */
    private val lines = "-- @every 1h\n-- @at 07:00, 18:30\n-- @notify\n"

    private val settings = """{"version":1,"scripts":[{"name":"foldtest","lang":"lua","every":30,"at":["10:00"],"notify":true,"enabled":true}]}"""

    @Before fun setUp() {
        dir.deleteRecursively()
        dir.mkdirs()
    }

    /** What this phone's last export wrote, as Kolahal keeps it: put back after a test exports. */
    private val folderPrefs = context.getSharedPreferences("script_folder", android.content.Context.MODE_PRIVATE)
    private val exportedWas = folderPrefs.getStringSet("exported", null)

    @After fun tearDown() {
        scripts.byName("foldtest")?.let {
            ScriptScheduler.cancel(context, it.id)
            scripts.delete(it.id)
        }
        dir.deleteRecursively()
        folderPrefs.edit().putStringSet("exported", exportedWas).commit()
    }

    /** The script as you left it: switched on, with your timers and notifications off. */
    private fun yours(code: String, every: Int, at: List<String>) {
        val s = scripts.create("foldtest", ScriptLang.LUA, code)
        scripts.setInterval(s.id, every)
        scripts.setAtTimes(s.id, at)
        scripts.setNotify(s.id, false)
    }

    /** The script once [code] is imported, with [manifest] as the folder's kolahal.json. */
    private fun imported(code: String, manifest: String? = null): Script {
        File(dir, "foldtest.lua").writeText(code)
        manifest?.let { File(dir, "kolahal.json").writeText(it) }
        ScriptFolder(context).import(DocumentFile.fromFile(dir))
        return scripts.byName("foldtest")!!
    }

    @Test fun aNewScriptGetsWhatItsCodeSuggests() {
        val s = imported(lines + "return 1")
        assertEquals(60, s.intervalMin)
        assertEquals(listOf("07:00", "18:30"), s.atTimes)
        assertTrue(s.notify)
        assertFalse(s.enabled)
    }

    @Test fun aNewScriptWithSettingsGetsThoseInstead() {
        val s = imported(lines + "return 1", settings)
        assertEquals(30, s.intervalMin)
        assertEquals(listOf("10:00"), s.atTimes)
        assertFalse(s.enabled)
    }

    @Test fun aChangedScriptKeepsWhatYouSet() {
        yours(lines + "return 1", every = 120, at = listOf("09:00"))
        val s = imported(lines + "return 2")
        assertEquals(lines + "return 2", s.code)
        assertEquals(120, s.intervalMin)
        assertEquals(listOf("09:00"), s.atTimes)
        assertFalse(s.notify)
        // Still switched off, to be reviewed.
        assertFalse(s.enabled)
    }

    @Test fun timersYouSwitchedOffStayOff() {
        yours(lines + "return 1", every = 0, at = emptyList())
        val s = imported(lines + "return 2")
        assertEquals(0, s.intervalMin)
        assertEquals(emptyList<String>(), s.atTimes)
    }

    @Test fun aChangedLineSetsTheTimer() {
        yours(lines + "return 1", every = 120, at = listOf("09:00"))
        val s = imported("-- @every 30m\n-- @at 08:00\nreturn 1")
        assertEquals(30, s.intervalMin)
        assertEquals(listOf("08:00"), s.atTimes)
        assertFalse(s.enabled)
    }

    @Test fun aLineTakenOutTakesBackOnlyWhatItSet() {
        yours(lines + "return 1", every = 60, at = listOf("09:00"))
        val s = imported("return 1")
        assertEquals("what the line had set", 0, s.intervalMin)
        assertEquals("what you had set", listOf("09:00"), s.atTimes)
    }

    @Test fun settingsInTheFolderStillApplyToAChangedScript() {
        yours(lines + "return 1", every = 120, at = listOf("09:00"))
        val s = imported(lines + "return 2", settings)
        assertEquals(30, s.intervalMin)
        assertEquals(listOf("10:00"), s.atTimes)
        assertTrue(s.notify)
        // Its code still waits for a look, whatever they say.
        assertFalse(s.enabled)
    }

    /** The same code and settings again aren't an update; changed code is. */
    @Test fun onlyAChangedScriptCountsAsUpdated() {
        yours(lines + "return 1", every = 120, at = listOf("09:00"))
        File(dir, "foldtest.lua").writeText(lines + "return 1")
        assertEquals(0, ScriptFolder(context).import(DocumentFile.fromFile(dir)).updated)
        File(dir, "foldtest.lua").writeText(lines + "return 2")
        assertEquals(1, ScriptFolder(context).import(DocumentFile.fromFile(dir)).updated)
    }

    /** Another phone's script stays in the folder, and so do its settings; one whose file is gone doesn't. */
    @Test fun exportKeepsTheSettingsOfScriptsItKeeps() {
        yours("return 1", every = 120, at = emptyList())
        File(dir, "otherphone.lua").writeText("return 1")
        File(dir, "kolahal.json").writeText(
            """{"version":1,"scripts":[{"name":"otherphone","lang":"lua","every":30,"at":["10:00"],"notify":true,"enabled":true},""" +
                """{"name":"gone","lang":"lua","every":60,"at":[],"notify":false,"enabled":true}]}"""
        )
        ScriptFolder(context).export(DocumentFile.fromFile(dir))
        val entries = ((Json.parse(File(dir, "kolahal.json").readText()) as Map<*, *>)["scripts"] as List<*>).map { it as Map<*, *> }.associateBy { it["name"] }
        assertEquals(30, (entries.getValue("otherphone")["every"] as Number).toInt())
        assertEquals(listOf("10:00"), entries.getValue("otherphone")["at"])
        assertEquals(120, (entries.getValue("foldtest")["every"] as Number).toInt())
        assertFalse("gone" in entries)
    }

    @Test fun anUnchangedScriptIsLeftAsItIs() {
        yours(lines + "return 1", every = 120, at = listOf("09:00"))
        val s = imported(lines + "return 1")
        assertEquals(120, s.intervalMin)
        assertEquals(listOf("09:00"), s.atTimes)
        assertFalse(s.notify)
        assertTrue(s.enabled)
    }
}
