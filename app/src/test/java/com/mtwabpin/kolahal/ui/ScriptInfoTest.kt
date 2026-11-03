package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ScriptAccess
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A script card's access, from the code as read once, is what the script's own access says. This
 * only keeps the two ways of working it out from drifting apart: whether either is right for a
 * script is for the tests of [ScriptAccess] and the script's switches (ScriptPermissionsTest and others).
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
}
