package com.mtwabpin.kolahal.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The line of an import in Scripts that says the scripts are off ([ScriptActions.offUntilOn]), and what Copy gives there. */
class ScriptImportNoticeTest {
    @Test
    fun oneScriptIsNamed() {
        assertEquals(
            "Off until you turn it on with 'script on chess'",
            ScriptActions.offUntilOn(listOf("chess.lua")),
        )
        assertEquals("script on chess", ScriptActions.turnOnCommand(listOf("chess.js")))
    }

    @Test
    fun severalScriptsKeepThePlaceholder() {
        val files = listOf("chess.lua", "go.js")
        assertEquals(
            "Off until you turn them on with 'script on <name>'",
            ScriptActions.offUntilOn(files),
        )
        assertNull(ScriptActions.turnOnCommand(files))
    }

    @Test
    fun noneSaysNothing() {
        assertNull(ScriptActions.offUntilOn(emptyList()))
        assertNull(ScriptActions.turnOnCommand(emptyList()))
    }
}
