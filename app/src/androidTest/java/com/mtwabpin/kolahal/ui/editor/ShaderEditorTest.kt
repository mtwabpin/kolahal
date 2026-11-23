package com.mtwabpin.kolahal.ui.editor

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.ui.theme.Term
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The shader editor: a shader's buttons only, AGSL's colors, what a save found at its line, and closing. */
@RunWith(AndroidJUnit4::class)
class ShaderEditorTest {
    @get:Rule
    val compose = createComposeRule()

    private val code = "// @name Glow\nuniform shader content;\nuniform float time;\nhalf4 main(float2 p) { return content.eval(p); }"
    private var saved: String? = null
    private var closed: Pair<Boolean, Shaders.Check?>? = null

    /** [fails]: the save throws with this message, as [Shaders.save] does for a file too large. */
    private fun show(bundled: Boolean = false, check: Shaders.Check? = null, fails: String? = null) {
        compose.setContent {
            KolahalTheme {
                ShaderEditor(
                    name = "glow",
                    code = code,
                    bundled = bundled,
                    onSave = { text ->
                        if (fails != null) throw IllegalArgumentException(fails)
                        saved = text
                        check
                    },
                    onClose = { done, found -> closed = done to found },
                )
            }
        }
    }

    private fun editor() = compose.onNodeWithTag("editor", useUnmergedTree = true).fetchSemanticsNode().config
    private fun shown() = runCatching {
        compose.onNodeWithTag("message").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
    }.getOrDefault("")

    /** Save and Close, with Find and the key bar of the shared editor; nothing of a script's. */
    @Test
    fun itHasAShadersButtons() {
        show()
        compose.onNodeWithContentDescription("Save").assertExists()
        compose.onNodeWithContentDescription("Close").assertExists()
        compose.onNodeWithContentDescription("Find").assertExists()
        compose.onNodeWithContentDescription("Undo").assertExists()
        compose.onNodeWithContentDescription("Run").assertDoesNotExist()
        compose.onNodeWithContentDescription("API reference").assertDoesNotExist()
        compose.onNodeWithTag("completions").assertDoesNotExist()
        compose.onNodeWithTag("editor").assertExists()
    }

    /** The code has AGSL's colors: its types, the header's word and the names Kolahal sets. */
    @Test
    fun theCodeHasAgslsColors() {
        show()
        compose.waitForIdle()
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        editor()[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        val spans = results.single().layoutInput.text.spanStyles
        fun colorOf(word: String) = spans.first { code.substring(it.start, it.end) == word }.item.color
        assertEquals(Term.prompt, colorOf("@name"))
        assertEquals(Term.prompt, colorOf("half4"))
        assertEquals(Term.info, colorOf("content"))
        assertEquals(Term.info, colorOf("time"))
    }

    /**
     * A save that the check fails is saved all the same, and says why in red; a tap on the message goes to the
     * compiler's line.
     */
    @Test
    fun aFailedCheckIsSavedAndGoesToItsLine() {
        show(check = Shaders.Check("line 4: unknown identifier 'nope'", emptyList(), emptyList(), false, line = 4))
        val broken = code.replace("content.eval(p)", "nope")
        compose.onNodeWithTag("editor").performTextReplacement(broken)
        compose.onNodeWithContentDescription("Save").performClick()
        compose.waitUntil(5_000) { "line 4" in shown() }
        assertEquals("Saved · can't be used: line 4: unknown identifier 'nope'", shown())
        assertEquals(broken, saved)
        compose.onNodeWithTag("message").performClick()
        compose.waitForIdle()
        assertEquals(TextRange(EditorLogic.lineStart(broken, 4)), editor()[SemanticsProperties.TextSelectionRange])
    }

    /** One that comes with Kolahal says, when it opens, that saving makes the user's copy. */
    @Test
    fun aBundledOneSaysSavingMakesYourCopy() {
        show(bundled = true)
        compose.waitUntil(5_000) { shown() == BUNDLED_NOTE }
    }

    /** Close saves the text that isn't saved first, then closes with what the check found; with nothing to save, it just closes. */
    @Test
    fun closeSavesFirst() {
        val check = Shaders.Check(null, listOf("time"), emptyList(), false)
        show(check = check)
        compose.onNodeWithContentDescription("Close").performClick()
        compose.waitForIdle()
        assertEquals(false to null, closed)
        assertNull(saved)
        closed = null
        compose.onNodeWithTag("editor").performTextReplacement("$code\n// more")
        compose.onNodeWithContentDescription("Close").performClick()
        compose.waitUntil(5_000) { closed != null }
        assertEquals("$code\n// more", saved)
        assertEquals(true, closed?.first)
        assertTrue(closed?.second === check)
    }

    /** A save that fails on the way out keeps the editor open with the text, and says why, as Save does. */
    @Test
    fun aFailedSaveKeepsTheEditorOpen() {
        show(fails = "larger than 64 KB")
        compose.onNodeWithTag("editor").performTextReplacement("$code\n// more")
        compose.onNodeWithContentDescription("Close").performClick()
        compose.waitUntil(5_000) { "Couldn't save" in shown() }
        assertEquals("Couldn't save: larger than 64 KB", shown())
        assertNull(closed)
        assertEquals("$code\n// more", editor()[SemanticsProperties.EditableText].text)
    }

    /** The editor takes no more than a shader's file holds: a longer text isn't taken, and the message says why. */
    @Test
    fun itTakesNoMoreThanAShadersFile() {
        show()
        compose.onNodeWithTag("editor").performTextReplacement("x".repeat(ShaderMeta.MAX_BYTES + 1))
        compose.waitUntil(5_000) { "The editor takes up to" in shown() }
        assertEquals("The editor takes up to 65,536 characters", shown())
        assertEquals(code, editor()[SemanticsProperties.EditableText].text)
    }
}
