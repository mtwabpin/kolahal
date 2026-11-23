package com.mtwabpin.kolahal.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.ui.theme.Palettes
import com.mtwabpin.kolahal.ui.theme.Term
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The editor's save message, a run's lines under the code, and Run with. */
@RunWith(AndroidJUnit4::class)
class ScriptEditorTest {
    @get:Rule
    val compose = createComposeRule()

    private val script =
        Script(1, "edtest", ScriptLang.LUA, "log('hi')", 0, true, false, null, null)
    private var lastRun by mutableStateOf<Long?>(null)
    private var ran: Pair<List<String>, Map<String, Any?>?>? = null

    private fun show(
        code: String = script.code,
        changed: String? = null,
        saveFails: Throwable? = null
    ) {
        compose.setContent {
            KolahalTheme {
                ScriptEditor(
                    script = script.copy(code = code),
                    jumpToLine = null,
                    lastRun = lastRun,
                    lastStatus = "ok in 12ms",
                    onSave = { _, done -> done(null, changed) },
                    onRun = { _, args, event, done ->
                        ran = args to event
                        if (saveFails != null) {
                            done(saveFails, null)
                        } else {
                            done(null, null)
                            lastRun = System.currentTimeMillis() + 1_000
                        }
                    },
                    runLines = {
                        listOf(
                            line(LogKind.SCRIPT, "first"),
                            line(LogKind.ERROR, "edtest: oops")
                        )
                    },
                    onClose = {},
                )
            }
        }
    }

    private fun line(kind: LogKind, text: String) =
        LogEntry(1, System.currentTimeMillis(), kind, "edtest", text, LogContext.EMPTY)

    /** The editor's code as last laid out. */
    private fun codeLayout(): androidx.compose.ui.text.TextLayoutResult {
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("editor")
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(
            results
        )
        return results.single()
    }

    /** The colors the editor drew its code in. */
    private fun codeColors(): Set<androidx.compose.ui.graphics.Color> =
        codeLayout().layoutInput.text.spanStyles.map { it.item.color }.toSet()

    /** The lines (from 0) of [code] that have a color. */
    private fun coloredLines(code: String): Set<Int> {
        val starts = listOf(0) + code.indices.filter { code[it] == '\n' }.map { it + 1 }
        return codeLayout().layoutInput.text.spanStyles.map { r -> starts.indexOfLast { it <= r.start } }
            .toSet()
    }

    /** The phone turning dark with the editor open: the code changes color along with everything else. */
    @Test
    fun codeFollowsTheThemeWhileOpen() {
        val before = Term.palette
        try {
            Term.palette = Palettes.green
            show()
            compose.waitForIdle()
            assertEquals(setOf(Palettes.green.script), codeColors())
            compose.runOnIdle { Term.palette = Palettes.dracula }
            compose.waitForIdle()
            assertEquals(setOf(Palettes.dracula.script), codeColors())
        } finally {
            Term.palette = before
        }
    }

    /** Saving says what it changed beyond the code, and the first thing worth a look, with its line. */
    @Test
    fun savingSaysWhatItFound() {
        show(changed = "runs every 1h")
        compose.onNodeWithTag("editor")
            .performTextReplacement("-- @every 1h\nlocal r = http.gte('x')")
        compose.onNodeWithContentDescription("Save").performClick()
        fun shown() = runCatching {
            compose.onNodeWithTag("message")
                .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
        }.getOrDefault("")
        // The check runs off the main thread: waiting for idle doesn't wait for it.
        compose.waitUntil(5_000) { "line 2" in shown() }
        assertEquals(
            "Saved · runs every 1h · line 2: http.gte isn't in the API: http.get?",
            shown()
        )
    }

    @Test
    fun aRunsLinesShowUnderTheCode() {
        show()
        compose.onNodeWithContentDescription("Run").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("output · 2 lines").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("first").assertExists()
        compose.onNodeWithText("edtest: oops").assertExists()
        compose.onNodeWithText("Ran in 12ms · 2 lines below").assertExists()
        compose.onNodeWithContentDescription("Close output").performClick()
        compose.onNodeWithTag("run-output").assertDoesNotExist()
    }

    /** Run whose save failed: the script didn't run, so its next run (its timer's, old code) isn't shown as this one's. */
    @Test
    fun aRunWhoseSaveFailedTakesNoLaterRun() {
        show(saveFails = java.io.IOException("disk full"))
        fun shown() = compose.onNodeWithTag("message")
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
        compose.onNodeWithContentDescription("Run").performClick()
        compose.waitForIdle()
        assertEquals("Couldn't save: disk full", shown())
        compose.runOnIdle { lastRun = System.currentTimeMillis() + 1_000 }
        compose.waitForIdle()
        assertEquals("Couldn't save: disk full", shown())
        compose.onNodeWithTag("run-output").assertDoesNotExist()
    }

    /** A long press on run: words for args, and one of its events with made-up values. */
    @Test
    fun runWithArgsAndAnEvent() {
        show(code = "-- @on charger\nlog(argline)")
        compose.onNodeWithContentDescription("Run").performTouchInput { longClick() }
        compose.onNodeWithTag("run-args").performTextInput("one \"two three\"")
        compose.onNodeWithText("○ charger").performClick()
        compose.onNodeWithText("Run").performClick()
        compose.waitUntil(3_000) { ran != null }
        assertEquals(listOf("one", "two three"), ran!!.first)
        assertEquals("charger", ran!!.second?.get("name"))
        assertEquals(64, ran!!.second?.get("level"))
        // Run with… was used: its result doesn't point to it.
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Ran in 12ms · 2 lines below").fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    /**
     * A plain run of a command, or of a script with @on or @at, points to Run with… on the message
     * line. A script that takes neither isn't told (aRunsLinesShowUnderTheCode).
     */
    @Test
    fun aPlainRunOfACommandPointsToRunWith() {
        show(code = "-- @command\nlog(argline)")
        compose.onNodeWithContentDescription("Run").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Ran in 12ms · 2 lines below · Long-press Run for arguments or an event")
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(emptyList<String>() to null, ran)
    }

    /**
     * The field is as wide as its widest line, worked out by the editor: no line wraps, whether long,
     * in wide letters, with tabs, or in bold keywords (a small script keeps its bold).
     */
    @Test
    fun noLineWrapsInASmallScript() = noLineWraps("$keywords\n$long\nlog(s)")

    @Test
    fun noLineWrapsInABigScript() =
        noLineWraps(List(700) { "local x$it = $it -- some words" }.joinToString("\n") + "\n$long")

    private val long = "local s = '" + "日本語🙂é".repeat(60) + "\t\tend'"
    private val keywords = List(40) { "local function return end" }.joinToString(" ")

    private fun noLineWraps(code: String) {
        show(code)
        compose.waitForIdle()
        assertEquals(code.count { it == '\n' } + 1, codeLayout().lineCount)
    }

    /** A big script is colored around the view, and the colors follow once it's scrolled elsewhere. */
    @Test
    fun aBigScriptIsColoredAroundTheView() {
        val code = List(1_500) { "local x$it = $it -- line" }.joinToString("\n")
        show(code)
        compose.waitForIdle()
        val top = coloredLines(code)
        assertTrue("top: ${top.min()}..${top.max()}", 0 in top && top.max() < 400)
        // To the end of the script.
        val height = codeLayout().size.height.toFloat()
        compose.onAllNodes(
            androidx.compose.ui.test.hasScrollAction() and androidx.compose.ui.test.hasAnyDescendant(
                androidx.compose.ui.test.hasTestTag("editor")
            )
        )[0]
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) {
                it(
                    0f,
                    height
                )
            }
        compose.waitUntil(5_000) { 1_499 in coloredLines(code) }
        val below = coloredLines(code)
        assertTrue("after scrolling: ${below.min()}..${below.max()}", 0 !in below)
    }

    /** A script of 1,500 lines, colored around the view. */
    private val bigCode = List(1_500) { "local x$it = $it -- line" }.joinToString("\n")

    /** The code's own vertical scroll. */
    private fun codeScroll() = compose.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
                hasAnyDescendant(hasTestTag("editor"))
    )

    /** Where the code is scrolled to, and the farthest it goes, in pixels. */
    private fun scrolled(): Pair<Float, Float> =
        codeScroll().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            .let { it.value() to it.maxValue() }

    private fun scrollCodeBy(y: Float) {
        codeScroll().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, y) }
    }

    /**
     * Opens [code] with the focus in it and the cursor at its start, scrolled to the top: there
     * the colors are around the first lines, and none at the end.
     */
    private fun showAtTopWithTheCursor(code: String) {
        show(code)
        compose.waitForIdle()
        compose.onNodeWithTag("editor").performTextInputSelection(TextRange(0))
        compose.waitUntil(3_000) {
            compose.onAllNodes(hasTestTag("editor") and isFocused()).fetchSemanticsNodes().isNotEmpty() &&
                    compose.onNodeWithTag("editor").fetchSemanticsNode()
                        .config[SemanticsProperties.TextSelectionRange] == TextRange(0)
        }
        scrollCodeBy(-codeLayout().size.height.toFloat())
        compose.waitUntil(5_000) { 0 in coloredLines(code) }
        assertTrue("the end has no colors yet", 1_499 !in coloredLines(code))
    }

    /**
     * Scrolled far from the cursor, the view stays where the scroll ended: when the colors move to
     * the lines in view, and when the theme changes. The field asks to show its cursor after each
     * new layout of other text, and colors count as text there.
     */
    @Test
    fun aChangeOfColorsLeavesTheViewWhereItWasScrolled() {
        val before = Term.palette
        try {
            Term.palette = Palettes.green
            showAtTopWithTheCursor(bigCode)
            scrollCodeBy(codeLayout().size.height.toFloat())
            compose.waitForIdle()
            val (end, max) = scrolled()
            assertTrue("scrolled to the end: $end of $max", max > 0f)
            assertEquals("the view stays at the end, away from the cursor", max, end)
            // The colors follow the view.
            compose.waitUntil(5_000) { 1_499 in coloredLines(bigCode) }
            compose.waitForIdle()
            assertEquals("after the colors moved", max, scrolled().first)
            compose.runOnIdle { Term.palette = Palettes.dracula }
            compose.waitForIdle()
            // Comments take the theme's dim color.
            assertTrue(Palettes.dracula.dim in codeColors())
            assertEquals("after the theme changed", max, scrolled().first)
        } finally {
            Term.palette = before
        }
    }

    /** Typing at a cursor scrolled out of view brings it back into view, also after the colors moved. */
    @Test
    fun typingShowsTheCursorAgain() {
        showAtTopWithTheCursor(bigCode)
        scrollCodeBy(codeLayout().size.height.toFloat())
        compose.waitUntil(5_000) { 1_499 in coloredLines(bigCode) }
        compose.waitForIdle()
        val (away, max) = scrolled()
        assertTrue("scrolled away from the cursor: $away of $max", away > max / 2)
        compose.onNodeWithTag("editor").performTextInput("x")
        compose.waitForIdle()
        assertEquals(
            "x$bigCode",
            compose.onNodeWithTag("editor").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        )
        val back = scrolled().first
        assertTrue("the cursor is in view again: $back of $max", back < max / 2)
    }
}
