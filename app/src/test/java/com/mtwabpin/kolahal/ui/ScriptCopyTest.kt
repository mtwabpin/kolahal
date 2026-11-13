package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ScriptLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A library copy as the Scripts tab, a script's sheet and the library page see it ([ScriptCopy.of]):
 * the three share one set of dialogs ([CopyDialogs]), so they must agree on which copies get them.
 */
class ScriptCopyTest {
    private val entry = ScriptLibrary.Entry("countdown.lua", "countdown", ScriptLang.LUA, "Countdown", "Counts down", command = true, suggestedEvery = 0)

    /**
     * A row as the pages build it: `newer` is what [ScriptLibrary.hasNewer] says for the state. An
     * unedited older copy always has a newer version; an edited one has it when [fromOlder].
     */
    private fun row(state: ScriptLibrary.State, fromOlder: Boolean = false) = LibRow(
        entry, state, emptyList(),
        newer = state == ScriptLibrary.State.UPDATE_AVAILABLE || (state == ScriptLibrary.State.MODIFIED && fromOlder),
    )

    /** The library page offered Compare for a copy that differs, the Scripts tab for one that can be restored: the same copies. */
    @Test fun aCopyThatDiffersIsOneThatCanBeRestored() {
        for (state in ScriptLibrary.State.entries) for (fromOlder in listOf(false, true)) {
            val r = row(state, fromOlder)
            assertEquals("$state, from an older version: $fromOlder", r.state.differs(), ScriptCopy.of(r).canRestore)
        }
    }

    /** A newer version comes before edits, and only an edited copy asks before Update or Reset. */
    @Test fun aLibraryCopysStateAndEdits() {
        fun copy(state: ScriptLibrary.State, fromOlder: Boolean = false) = ScriptCopy.of(row(state, fromOlder)).let { it.state to it.edited }
        assertEquals(CopyState.UP_TO_DATE to false, copy(ScriptLibrary.State.INSTALLED))
        assertEquals(CopyState.NEW_VERSION to false, copy(ScriptLibrary.State.UPDATE_AVAILABLE))
        assertEquals(CopyState.EDITED to true, copy(ScriptLibrary.State.MODIFIED))
        assertEquals(CopyState.NEW_VERSION to true, copy(ScriptLibrary.State.MODIFIED, fromOlder = true))
    }

    /** Update or Reset brings the library's version of that script, and a library copy can be compared. */
    @Test fun aLibraryCopyRestoresFromItsLibraryScript() {
        val r = row(ScriptLibrary.State.MODIFIED)
        val copy = ScriptCopy.of(r)
        val s = Script(7, "countdown", ScriptLang.LUA, "-- mine", 0, true, false, null, null)
        val restore = copy.restore(s)
        assertSame(s, restore.script)
        assertSame(entry, restore.library)
        assertNull(restore.shared)
        assertEquals(ScriptSource.LIBRARY, copy.source)
        assertSame(r, copy.lib)
    }

    /** A shared copy whose entry is no longer in the kept list can't be restored: there is nothing to bring. */
    @Test fun aCopyWithNoKnownSourceCantBeRestored() {
        assertFalse(ScriptCopy(ScriptSource.SHARED, CopyState.NEW_VERSION, edited = false).canRestore)
        assertFalse(ScriptCopy(ScriptSource.SHARED, CopyState.EDITED, edited = true).canRestore)
        assertTrue(ScriptCopy(ScriptSource.LIBRARY, CopyState.EDITED, edited = true, lib = row(ScriptLibrary.State.MODIFIED)).canRestore)
        assertFalse(ScriptCopy(ScriptSource.LIBRARY, CopyState.UP_TO_DATE, edited = false, lib = row(ScriptLibrary.State.INSTALLED)).canRestore)
    }
}
