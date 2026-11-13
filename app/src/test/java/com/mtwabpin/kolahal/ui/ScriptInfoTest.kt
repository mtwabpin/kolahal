package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptMeta
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A script card's access, from the code as read once, is what the script's own access says. This
 * only keeps the two ways of working it out from drifting apart: whether either is right for a
 * script is for the tests of [ScriptAccess] and the script's switches (ScriptPermissionsTest and others).
 * The same for what the script's sheet shows from it, instead of reading the code again.
 */
class ScriptInfoTest {
    private val codes = listOf(
        "print('hi')",
        "-- @uses clipboard, notifications\n-- @on unlock\nlocal r = http.get('https://example.com')\nheader.set('x')",
        "// @uses apps\n// @on charging, unlock\nnotify('done')",
        "-- @on notification\n-- @uses notifications\nreturn 1",
    )
    private val decisions = listOf(
        emptyMap(),
        mapOf("clipboard" to true),
        mapOf("notifications" to false, ScriptAccess.INTERNET to false),
        mapOf("unlock" to false, "apps" to true, ScriptAccess.HEADER to false),
    )

    @Test fun keptAccessIsTheScriptsAccess() {
        for (code in codes) for (access in decisions) {
            val s = Script(1, "s", if (code.startsWith("//")) ScriptLang.JS else ScriptLang.LUA, code, 0, true, false, null, null, access = access)
            val info = ScriptInfo(s, ScriptCode(code, s.name))
            assertEquals(code, ScriptAccess.items(code), info.accessItems)
            assertEquals("$code $access", s.grants.undecided, info.grants.undecided)
            for (item in info.accessItems + ScriptAccess.INTERNET + ScriptAccess.HEADER + "dnd") {
                assertEquals("$code $access $item", s.grants.allows(item), info.grants.allows(item))
            }
        }
    }

    /** The sheet's lines come from the info: the name line, what it is, and its secrets. */
    @Test fun keptFactsAreWhatTheCodeSays() {
        val sheet = "-- @command\n-- @title Sheet test\n-- @desc Says hello\n-- @version 1.2\n-- @author Tester\n" +
            "-- @secret SST_KEY example.com Your key\nnotify('done', secrets.get('SST_KEY'))"
        for (code in codes + sheet) {
            val s = Script(1, "s", if (code.startsWith("//")) ScriptLang.JS else ScriptLang.LUA, code, 0, true, false, null, null)
            val info = ScriptInfo(s, ScriptCode(code, s.name))
            assertEquals(code, ScriptMeta.title(code), info.title)
            assertEquals(code, ScriptMeta.description(code), info.desc)
            assertEquals(code, ScriptMeta.version(code), info.version)
            assertEquals(code, ScriptMeta.author(code), info.author)
            assertEquals(code, ScriptMeta.isCommand(code), info.isCommand)
            assertEquals(code, ScriptMeta.postsNotifications(code), info.notifies)
            assertEquals(code, ScriptMeta.secrets(code), info.secrets)
        }
        val info = ScriptInfo(Script(1, "s", ScriptLang.LUA, sheet, 0, true, false, null, null), ScriptCode(sheet, "s"))
        assertEquals(listOf("Sheet test", "Says hello", "1.2", "Tester"), listOf(info.title, info.desc, info.version, info.author))
        assertEquals(listOf(true, true), listOf(info.isCommand, info.notifies))
        assertEquals(listOf("SST_KEY"), info.secrets.map { it.name })
    }
}
