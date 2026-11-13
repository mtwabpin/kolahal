package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Suggestion
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The default key bar, with real touches on the home screen. */
@RunWith(AndroidJUnit4::class)
class KeyBarTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createComposeRule()
    @get:Rule val logLines = LogLinesSince()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val config = Config.get(app)
    private lateinit var vm: TerminalViewModel

    @Before fun show() {
        config.set(Keys.KEYBAR_SHOW, "on")
        config.set(Keys.KEYBAR_AUTO, "off")
        vm = vms.terminal(app)
        compose.setContent {
            KolahalTheme {
                Box(Modifier.size(360.dp, 700.dp).consumeWindowInsets(WindowInsets.safeDrawing)) {
                    TerminalScreen(vm)
                }
            }
        }
        compose.waitForIdle()
    }

    @get:Rule val configReset = ConfigReset()

    private fun put(text: String, cursor: Int = text.length) {
        compose.runOnIdle { vm.onInputChange(TextFieldValue(text, TextRange(cursor))) }
        compose.waitForIdle()
    }

    /** The arrows are drawn as icons described by their name; the symbols are text. */
    private fun press(key: String) {
        val node = when (key) {
            "left", "up", "down", "right" -> compose.onNodeWithContentDescription(key, useUnmergedTree = true)
            else -> compose.onAllNodesWithText(key, useUnmergedTree = true).onLast()
        }
        node.performTouchInput { click() }
        compose.waitForIdle()
    }

    /** Runs a line no earlier run has, and returns it once ↑ can reach it. */
    private fun ran(): String {
        val line = "echo ${System.nanoTime()}"
        put(line)
        compose.runOnIdle { vm.submit() }
        compose.waitUntil(10_000) { compose.runOnIdle { vm.entries.value.any { line in it.text } } }
        put("")
        return line
    }

    private val text get() = compose.runOnIdle { vm.input.text }
    private val cursor get() = compose.runOnIdle { vm.input.selection.end }

    /** One row by default, its 9 keys sharing the width of a 360 dp screen: none scrolled out of sight. */
    @Test fun oneRowByDefault() {
        compose.onAllNodesWithTag("keybar", useUnmergedTree = true).assertCountEquals(1)
        for (key in listOf("/", "@", "|", "=", "+", "-", "*")) compose.onAllNodesWithText(key, useUnmergedTree = true).onLast().assertIsDisplayed()
        for (key in listOf("up", "down")) compose.onNodeWithContentDescription(key, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun theSymbolsGoInAtTheCursor() {
        put("ab", 1)
        for (key in listOf("/", "@", "|", "=", "+", "-", "*")) press(key)
        assertEquals("a/@|=+-*b", text)
        assertEquals(8, cursor)
    }

    /**
     * A row too long to share the width (keys under 36 dp each) scrolls: its first keys show, its
     * last ones are out of sight until the row is scrolled.
     */
    @Test fun aLongRowScrolls() {
        config.set(Keys.KEYBAR, "a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p")
        compose.waitForIdle()
        compose.onAllNodesWithText("a", useUnmergedTree = true).onLast().assertIsDisplayed()
        compose.onAllNodesWithText("p", useUnmergedTree = true).assertCountEquals(0)
    }

    /** LEFT and RIGHT aren't in the default row any more; a row that has them moves the cursor. */
    @Test fun theArrowsMoveTheCursor() {
        config.set(Keys.KEYBAR, "LEFT,UP,DOWN,RIGHT")
        compose.waitForIdle()
        put("abc", 1)
        press("left")
        assertEquals(0, cursor)
        press("right")
        press("right")
        assertEquals(2, cursor)
    }

    @Test fun upAndDownWalkTheHistory() {
        val line = ran()
        put("draft")
        press("up")
        assertEquals(line, text)
        press("down")
        assertEquals("draft", text)
    }

    /** Found in review: ↓ went on walking the history and the edit was lost. */
    @Test fun downKeepsAnEditFromTheBar() {
        val line = ran()
        press("up")
        press("=")
        press("down")
        assertEquals("$line=", text)
    }

    @Test fun downKeepsALineASuggestionFilled() {
        ran()
        press("up")
        compose.runOnIdle { vm.onSuggestion(Suggestion.Completion("todo", "todo ")) }
        press("down")
        assertEquals("todo ", text)
    }

    /** A drag that starts on a symbol moves the cursor and types nothing. */
    @Test fun draggingAcrossTheBarMovesTheCursor() {
        put("abcdefgh", 0)
        compose.onNodeWithTag("keybar", useUnmergedTree = true).performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals("abcdefgh", text)
        assertTrue("the cursor is at $cursor", cursor > 0)
    }
}
