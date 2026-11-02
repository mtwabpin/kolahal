package com.mtwabpin.kolahal.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

/** What Android keeps of the editor's text while Kolahal is in the background, and what comes back. */
class EditorKeptTextTest {
    private val scope = object : SaverScope {
        override fun canBeSaved(value: Any) = true
    }

    private fun <T : Any> Saver<T, Any>.roundTrip(value: T): Pair<Any?, T?> {
        val kept = with(this) { scope.save(value) }
        return kept to kept?.let(::restore)
    }

    /** A script of a usual size comes back as typed, unsaved edits and all. */
    @Test fun aUsualScriptComesBackAsTyped() {
        val typed = TextFieldValue("return 2", TextRange(7))
        assertEquals(typed, keptText("return 1").roundTrip(typed).second)
        assertEquals("return 2", keptSaved("return 1").roundTrip("return 2").second)
    }

    /** A very big one isn't kept, as all of it would stop the app: the script as saved comes back, the cursor where it was. */
    @Test fun aVeryBigScriptKeepsOnlyTheCursor() {
        val big = "x".repeat(KEPT_TEXT_MAX + 1)
        val (kept, back) = keptText(big).roundTrip(TextFieldValue(big, TextRange(500)))
        assertEquals(listOf(false, 500, 500), kept)
        assertEquals(TextFieldValue(big, TextRange(500)), back)
        assertEquals(false to big, keptSaved(big).roundTrip(big))
        // Saved shorter since: the cursor stays within it.
        assertEquals(TextRange(3), keptText("abc").restore(listOf(false, 500, 500))?.selection)
    }
}
