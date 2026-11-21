package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.terminal.WidgetScript
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a card says when its script can't fill it ([scriptNote]). Such a card asks for no runs. */
class ScriptNotesTest {
    private val stat = ScriptMeta.Widget(WidgetKind.STAT, WidgetSize.S2X1)
    private val weather = WidgetScript("weather", "Weather", enabled = true, widget = stat)

    @Test
    fun aScriptThatIsThereAndOnHasNoNote() {
        assertNull(scriptNote(read = true, script = weather))
    }

    @Test
    fun aScriptThatIsGoneSaysRemoved() {
        assertEquals(CardNote.REMOVED, scriptNote(read = true, script = null))
    }

    @Test
    fun aScriptThatIsOffSaysOff() {
        assertEquals(CardNote.OFF, scriptNote(read = true, script = weather.copy(enabled = false)))
        // Off is said first: switching it on comes before its `@widget` line matters.
        assertEquals(
            CardNote.OFF,
            scriptNote(read = true, script = weather.copy(enabled = false, widget = null))
        )
    }

    /** A widget needs its script's `@widget` line. */
    @Test
    fun aScriptThatLostItsWidgetLineSaysSo() {
        assertEquals(
            CardNote.NO_WIDGET,
            scriptNote(read = true, script = weather.copy(widget = null))
        )
    }

    /** At the start the script list is empty for a moment. That must not read as "removed". */
    @Test
    fun nothingIsSaidBeforeTheScriptsAreRead() {
        assertNull(scriptNote(read = false, script = null))
        assertNull(scriptNote(read = false, script = weather.copy(enabled = false)))
        assertNull(scriptNote(read = false, script = weather.copy(widget = null)))
    }
}
