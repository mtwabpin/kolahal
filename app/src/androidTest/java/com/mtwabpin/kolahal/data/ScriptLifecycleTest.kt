package com.mtwabpin.kolahal.data

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.script.HostApi
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.SecretStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a script keeps through a rename, what turning it on allows, and what new code over it
 * resets: the script's sheet and `script mv|on|every`, imports and updates all go through these.
 */
@RunWith(AndroidJUnit4::class)
class ScriptLifecycleTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repo = ScriptRepository.get(context)
    private val secrets = SecretStore(context)
    private val status = KvStore(context, HostApi.STATUS_SCOPE)
    private val names = listOf("lc-a", "lc-b", "lc-c", "lc-d")

    @After fun tearDown() {
        for (name in names) {
            repo.byName(name)?.let { repo.delete(it.id) }
            secrets.clear(name)
            status.set(name, null)
        }
    }

    private val key = ScriptMeta.Secret("KEY", listOf("api.example.com"), "")

    @Test fun renameKeepsTheScriptAndMovesWhatIsKeptByName() {
        val s = repo.create("lc-a", ScriptLang.LUA, "-- @secret KEY api.example.com a key\nreturn 1")
        secrets.set("lc-a", "KEY", "value-1234", key.hosts)
        status.set("lc-a", "its status line")
        // Left behind under the new name by an earlier script: replaced, not merged.
        status.set("lc-b", "stale line")
        context.getSharedPreferences("script_catalog", android.content.Context.MODE_PRIVATE).edit { putString("installed:lc-a", "{}") }

        repo.rename(s.id, "lc-b")

        assertNull(repo.byName("lc-a"))
        assertEquals(s.id, repo.byName("lc-b")?.id)
        assertEquals("value-1234", secrets.get("lc-b", "KEY")?.value)
        assertNull(secrets.get("lc-a", "KEY"))
        assertEquals("its status line", status.get("lc-b"))
        assertNull(status.get("lc-a"))
        // Renamed, a shared script is yours: no updates for it under the old name.
        assertNull(context.getSharedPreferences("script_catalog", android.content.Context.MODE_PRIVATE).getString("installed:lc-a", null))
    }

    @Test fun aNewScriptStartsWithNothingAnEarlierOneLeft() {
        status.set("lc-c", "left by a deleted script")
        secrets.set("lc-c", "KEY", "old-value-1234", key.hosts)
        repo.create("lc-c", ScriptLang.LUA, "return 1")
        assertNull(status.get("lc-c"))
        assertNull(secrets.get("lc-c", "KEY"))
    }

    @Test fun aValueIsUsedOnlyForTheSitesItWasSetFor() {
        secrets.set("lc-d", "KEY", "value-1234", listOf("api.example.com"))
        assertTrue(secrets.isSet("lc-d", key))
        val wider = ScriptMeta.Secret("KEY", listOf("api.example.com", "other.example"), "")
        assertFalse(secrets.isSet("lc-d", wider))
        assertTrue(secrets.hostsChanged("lc-d", wider))
        assertEquals(mapOf("KEY" to "value-1234"), secrets.usable("lc-d", listOf(key)))
        assertEquals(emptyMap<String, String>(), secrets.usable("lc-d", listOf(wider)))
        // Setting it again, having seen the new sites, makes it usable there.
        secrets.set("lc-d", "KEY", "value-1234", wider.hosts)
        assertTrue(secrets.isSet("lc-d", wider))
    }

    @Test fun onlyAnExplicitGoAheadAllowsWhatItAsksFor() {
        val s = repo.create("lc-a", ScriptLang.LUA, "-- @uses clipboard\nreturn 1", enabled = false)
        repo.setEnabled(s.id, true)
        var now = repo.byId(s.id)!!
        assertTrue(now.enabled)
        assertFalse(now.grants.allows("clipboard"))
        assertEquals(listOf("clipboard"), now.grants.undecided)

        repo.setEnabled(s.id, true, grant = true)
        now = repo.byId(s.id)!!
        assertTrue(now.grants.allows("clipboard"))
    }

    @Test fun anEditAllowsOnlyWhatItAdds() {
        val s = repo.create("lc-a", ScriptLang.LUA, "-- @uses clipboard\nreturn 1", enabled = false)
        repo.setEnabled(s.id, true)
        repo.saveCode(s.id, "-- @uses clipboard, apps\nreturn 1", byUser = true)
        val now = repo.byId(s.id)!!
        assertTrue(now.grants.allows("apps"))
        assertFalse(now.grants.allows("clipboard"))
    }

    @Test fun anUpdateLeavesNewEventsOff() {
        val s = repo.create("lc-a", ScriptLang.LUA, "return 1")
        repo.saveCode(s.id, "-- @on unlock\nreturn 1")
        assertFalse(repo.byId(s.id)!!.grants.allows("unlock"))
        // Your own edit adding an event keeps it on.
        val mine = repo.create("lc-b", ScriptLang.LUA, "return 1")
        repo.saveCode(mine.id, "-- @on unlock\nreturn 1", byUser = true)
        assertTrue(repo.byId(mine.id)!!.grants.allows("unlock"))
    }

    @Test fun codeFromElsewhereSwitchesItOffAndAsksAgain() {
        val s = repo.create("lc-a", ScriptLang.LUA, "-- @uses clipboard\nreturn http.get(\"https://example.com\")")
        repo.setAccess(s.id, "internet", false)
        status.set("lc-a", "a line")
        assertTrue(repo.byId(s.id)!!.grants.allows("clipboard"))

        repo.replaceCode(s.id, "-- @uses clipboard\nreturn http.get(\"https://example.org\")")
        val now = repo.byId(s.id)!!
        assertFalse(now.enabled)
        assertEquals(listOf("clipboard"), now.grants.undecided)
        // What you switched off stays off.
        assertFalse(now.grants.allows("internet"))
        assertNull(status.get("lc-a"))
    }
}
