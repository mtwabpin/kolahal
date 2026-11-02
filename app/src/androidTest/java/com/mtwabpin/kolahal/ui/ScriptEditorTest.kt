package com.mtwabpin.kolahal.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The editor's save message, a run's lines under the code, and Run with. */
@RunWith(AndroidJUnit4::class)
class ScriptEditorTest {
    @get:Rule val compose = createComposeRule()

    private val script = Script(1, "edtest", ScriptLang.LUA, "log('hi')", 0, true, false, null, null)
    private var lastRun by mutableStateOf<Long?>(null)
    private var ran: Pair<List<String>, Map<String, Any?>?>? = null

    private fun show(code: String = script.code, changed: String? = null, saveFails: Throwable? = null) {
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
                    runLines = { listOf(line(LogKind.SCRIPT, "first"), line(LogKind.ERROR, "edtest: oops")) },
                    onClose = {},
                )
            }
        }
    }

    private fun line(kind: LogKind, text: String) = LogEntry(1, System.currentTimeMillis(), kind, "edtest", text, LogContext.EMPTY)

    /** The colours the editor drew its code in. */
    private fun codeColours(): Set<androidx.compose.ui.graphics.Color> {
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("editor").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single().layoutInput.text.spanStyles.map { it.item.color }.toSet()
    }

    /** The phone turning dark with the editor open: the code changes colour along with everything else. */
    @Test fun codeFollowsTheThemeWhileOpen() {
        val before = Term.palette
        try {
            Term.palette = Palettes.green
            show()
            compose.waitForIdle()
            assertEquals(setOf(Palettes.green.script), codeColours())
            compose.runOnIdle { Term.palette = Palettes.dracula }
            compose.waitForIdle()
            assertEquals(setOf(Palettes.dracula.script), codeColours())
        } finally {
            Term.palette = before
        }
    }

    /** Saving says what it changed beyond the code, and the first thing worth a look, with its line. */
    @Test fun savingSaysWhatItFound() {
        show(changed = "runs every 1h")
        compose.onNodeWithTag("editor").performTextReplacement("-- @every 1h\nlocal r = http.gte('x')")
        compose.onNodeWithText("[save]").performClick()
        compose.waitForIdle()
        val shown = compose.onNodeWithTag("message").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
        assertEquals("Saved · runs every 1h · line 2: http.gte isn't in the API: http.get?", shown)
    }

    @Test fun aRunsLinesShowUnderTheCode() {
        show()
        compose.onNodeWithText("[run]").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("output · 2 lines").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("first").assertExists()
        compose.onNodeWithText("edtest: oops").assertExists()
        compose.onNodeWithText("Ran in 12ms · 2 lines below").assertExists()
        compose.onNodeWithText("✕").performClick()
        compose.onNodeWithTag("run-output").assertDoesNotExist()
    }

    /** Run whose save failed: the script didn't run, so its next run (its timer's, old code) isn't shown as this one's. */
    @Test fun aRunWhoseSaveFailedTakesNoLaterRun() {
        show(saveFails = java.io.IOException("disk full"))
        fun shown() = compose.onNodeWithTag("message").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
        compose.onNodeWithText("[run]").performClick()
        compose.waitForIdle()
        assertEquals("Couldn't save: disk full", shown())
        compose.runOnIdle { lastRun = System.currentTimeMillis() + 1_000 }
        compose.waitForIdle()
        assertEquals("Couldn't save: disk full", shown())
        compose.onNodeWithTag("run-output").assertDoesNotExist()
    }

    /** A long press on run: words for args, and one of its events with made-up values. */
    @Test fun runWithArgsAndAnEvent() {
        show(code = "-- @on charger\nlog(argline)")
        compose.onNodeWithText("[run]").performTouchInput { longClick() }
        compose.onNodeWithTag("run-args").performTextInput("one \"two three\"")
        compose.onNodeWithText("○ charger").performClick()
        compose.onNodeWithText("run").performClick()
        compose.waitUntil(3_000) { ran != null }
        assertEquals(listOf("one", "two three"), ran!!.first)
        assertEquals("charger", ran!!.second?.get("name"))
        assertEquals(64, ran!!.second?.get("level"))
    }
}
