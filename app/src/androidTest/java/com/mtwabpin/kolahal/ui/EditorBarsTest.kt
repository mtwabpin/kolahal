package com.mtwabpin.kolahal.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.waitForText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The script editor's find and replace bar, Go to line, the API reference and the symbol bar. */
@RunWith(AndroidJUnit4::class)
class EditorBarsTest {
    @get:Rule val compose = createComposeRule()

    // "apple" at 0 and 17, "Apple" at 23.
    private val code = "apple pie\nbanana apple\nApple cider"
    private val script = Script(1, "edbars", ScriptLang.LUA, code, 0, true, false, null, null)

    private fun show() {
        compose.setContent {
            KolahalTheme {
                ScriptEditor(
                    script = script,
                    jumpToLine = null,
                    lastRun = null,
                    lastStatus = null,
                    onSave = { _, done -> done(null, null) },
                    onRun = { _, _, _, done -> done(null, null) },
                    runLines = { emptyList() },
                    onClose = {},
                )
            }
        }
    }

    private fun editor() = compose.onNodeWithTag("editor").fetchSemanticsNode().config
    private fun text() = editor()[SemanticsProperties.EditableText].text
    private fun selection() = editor()[SemanticsProperties.TextSelectionRange]

    /** Where the code is marked as found ([EditorMarks]): each match's start, and the current one's. */
    private fun marks(): Pair<List<Int>, Int?> =
        compose.onAllNodesWithTag("editor-marks", useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrNull(MarkedMatches)?.let { (starts, current) -> starts.sorted() to current } ?: (emptyList<Int>() to null)

    /** Opens find and types [query]; matches are found a beat after typing. */
    private fun find(query: String, count: String) {
        compose.onNodeWithContentDescription("Find").performClick()
        compose.onNodeWithTag("find").performTextInput(query)
        compose.waitForText(count)
    }

    private fun openReplace(with: String) {
        compose.onNodeWithContentDescription("Replace").performClick()
        compose.onNodeWithTag("replace").performTextInput(with)
    }

    /** Find marks every match and selects the first; ↓, Enter and ↑ step through them, wrapping at the ends. */
    @Test fun findMarksEveryMatchAndStepsThroughThem() {
        show()
        find("apple", "1/3")
        assertEquals(TextRange(0, 5), selection())
        assertEquals(listOf(0, 17, 23) to 0, marks())
        compose.onNodeWithContentDescription("Next match").performClick()
        compose.waitForText("2/3")
        assertEquals(TextRange(17, 22), selection())
        assertEquals(listOf(0, 17, 23) to 17, marks())
        compose.onNodeWithTag("find").performImeAction()
        compose.waitForText("3/3")
        assertEquals(TextRange(23, 28), selection())
        compose.onNodeWithContentDescription("Next match").performClick()
        compose.waitForText("1/3")
        compose.onNodeWithContentDescription("Previous match").performClick()
        compose.waitForText("3/3")
        assertEquals(TextRange(23, 28), selection())
    }

    /** Aa matches case: "Apple" drops out of "apple"'s matches until it's tapped again. */
    @Test fun matchCaseLeavesOnlyTheExactOnes() {
        show()
        find("apple", "1/3")
        compose.onNodeWithText("Aa").performClick()
        compose.waitForText("1/2")
        assertEquals(listOf(0, 17) to 0, marks())
        compose.onNodeWithText("Aa").performClick()
        compose.waitForText("1/3")
    }

    /**
     * Text that isn't there counts 0/0 and marks nothing. A word that is there is found first, so the
     * 0/0 waited for is the search's answer, not what shows before the search has run.
     */
    @Test fun aSearchWithNoMatchCountsZero() {
        show()
        find("apple", "1/3")
        compose.onNodeWithTag("find").performTextReplacement("plum")
        compose.waitForText("0/0")
        assertEquals(emptyList<Int>() to null, marks())
    }

    /** × closes the bar and the marks go with it. */
    @Test fun closingFindTakesTheMarksAway() {
        show()
        find("apple", "1/3")
        compose.onNodeWithContentDescription("Close find").performClick()
        compose.onNodeWithTag("findbar").assertDoesNotExist()
        assertEquals(emptyList<Int>() to null, marks())
    }

    /** All replaces every match at once and says how many. */
    @Test fun replaceAllReplacesEveryMatch() {
        show()
        find("apple", "1/3")
        openReplace("pear")
        compose.onNodeWithText("Replace all").performClick()
        assertEquals("pear pie\nbanana pear\npear cider", text())
        compose.waitForText("Replaced 3")
        compose.onNodeWithText("edbars.lua *").assertExists()
        compose.waitForText("0/0")
    }

    /** Replace changes the selected match and selects the next; Enter in the replace box does the same. */
    @Test fun replaceChangesOneMatchAtATime() {
        show()
        find("apple", "1/3")
        openReplace("pear")
        compose.onNodeWithText("Replace").performClick()
        assertEquals("pear pie\nbanana apple\nApple cider", text())
        assertEquals(TextRange(16, 21), selection())
        compose.onNodeWithText("Replace").performClick()
        assertEquals("pear pie\nbanana pear\nApple cider", text())
        assertEquals(TextRange(21, 26), selection())
        compose.onNodeWithTag("replace").performImeAction()
        assertEquals("pear pie\nbanana pear\npear cider", text())
    }

    /** All with nothing found says so and leaves the code alone. */
    @Test fun replaceAllWithNoMatchSaysSo() {
        show()
        find("plum", "0/0")
        openReplace("pear")
        compose.onNodeWithText("Replace all").performClick()
        compose.waitForText("No matches to replace")
        assertEquals(code, text())
        compose.onNodeWithText("edbars.lua").assertExists()
    }

    /**
     * Ln asks for a line, takes only digits, refuses one past the end (4 of 3 lines) and takes the
     * last, and puts the cursor at the start of the one given.
     */
    @Test fun goToLinePutsTheCursorOnTheLine() {
        show()
        // The symbol bar: the last list that scrolls sideways (suggestions, when shown, sit above it).
        compose.onAllNodes(hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .onLast().performScrollToNode(hasText("Ln"))
        compose.onNodeWithText("Ln").performClick()
        compose.onNodeWithText("Go to line").assertExists()
        compose.onNodeWithText("Line (1–3)", useUnmergedTree = true).assertExists()
        val field = compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
        field.performTextInput("4")
        compose.onNodeWithText("Go").assertIsNotEnabled()
        field.performTextReplacement("x3")
        compose.onNodeWithText("Go").assertIsEnabled().performClick()
        compose.onNodeWithText("Go to line").assertDoesNotExist()
        assertEquals(TextRange(23), selection())
    }

    /** Puts the cursor at [offset], away from the end where it starts, so what goes in at it can't be an append. */
    private fun cursorAt(offset: Int) {
        compose.onNodeWithTag("editor").performTextInputSelection(TextRange(offset))
        compose.waitUntil(3_000) { selection() == TextRange(offset) }
    }

    /** A function picked from the API reference goes in at the cursor, ready for its arguments. */
    @Test fun theReferenceInsertsAFunctionAtTheCursor() {
        show()
        cursorAt(10)
        compose.onNodeWithContentDescription("API reference").performClick()
        compose.waitForText("API reference · Lua")
        compose.onNodeWithTag("apiref").performScrollToNode(hasText("log(title, body?, ctx?) -> line"))
        compose.onNodeWithText("log(title, body?, ctx?) -> line").performClick()
        compose.onNodeWithText("API reference · Lua").assertDoesNotExist()
        assertEquals("apple pie\nlog(banana apple\nApple cider", text())
    }

    /** A directive picked from the reference goes on a line of its own above the cursor's. */
    @Test fun theReferencePutsADirectiveAboveTheLine() {
        show()
        // Inside the second line.
        cursorAt(13)
        compose.onNodeWithContentDescription("API reference").performClick()
        compose.waitForText("API reference · Lua")
        compose.onNodeWithTag("apiref").performScrollToNode(hasText("-- @command"))
        compose.onNodeWithText("-- @command").performClick()
        assertEquals("apple pie\n-- @command\nbanana apple\nApple cider", text())
    }

    /**
     * The reference shows each widget example as its card, over its code. A tap on the card, even on
     * one of its buttons, runs nothing and opens nothing: the example goes in, on lines of its own.
     */
    @Test fun aWidgetExampleShowsItsCardAndGoesInOnATap() {
        show()
        // The start of the second line.
        cursorAt(10)
        compose.onNodeWithContentDescription("API reference").performClick()
        compose.waitForText("API reference · Lua")
        compose.onNodeWithTag("apiref").performScrollToNode(hasTestTag("widget-example buttons"))
        fun inExample(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("widget-example buttons")), useUnmergedTree = true)
        // The card is drawn, with its buttons.
        inExample("Start").assertExists()
        inExample("Stop").performClick()
        compose.onNodeWithText("API reference · Lua").assertDoesNotExist()
        val code = ReferenceExamples.widgets.single { it.name == "buttons" }.code(ScriptLang.LUA)
        assertEquals("apple pie\n$code\nbanana apple\nApple cider", text())
    }

    /**
     * The reference's Look row draws the widget examples in another look. A kind that doesn't
     * offer the look stays Plain and says so: buttons have no Bold.
     */
    @Test fun theWidgetExamplesCanBeSeenInEachLook() {
        show()
        compose.onNodeWithContentDescription("API reference").performClick()
        compose.waitForText("API reference · Lua")
        compose.onNodeWithTag("apiref").performScrollToNode(hasTestTag("example look Plain"))
        compose.onNodeWithTag("example look Plain").assertIsOn()
        compose.onNodeWithTag("example look Bold").performClick()
        compose.onNodeWithTag("example look Bold").assertIsOn()
        compose.onNodeWithTag("apiref").performScrollToNode(hasTestTag("widget-example buttons"))
        compose.onNode(hasText("Bold changes nothing for buttons, so this card is Plain.") and hasAnyAncestor(hasTestTag("widget-example buttons")), useUnmergedTree = true).assertExists()
        // A kind that has Bold has no such note.
        compose.onNodeWithTag("apiref").performScrollToNode(hasTestTag("widget-example steps"))
        compose.onNode(hasText("changes nothing", substring = true) and hasAnyAncestor(hasTestTag("widget-example steps")), useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * The reference shows each log example as its lines. A tap on a line, even on one of its buttons
     * or links, runs nothing and opens nothing: the example goes in, on lines of its own.
     */
    @Test fun aLogExampleShowsItsLinesAndGoesInOnATap() {
        show()
        cursorAt(10)
        compose.onNodeWithContentDescription("API reference").performClick()
        compose.waitForText("API reference · Lua")
        compose.onNodeWithTag("apiref").performScrollToNode(hasTestTag("log-example buttons"))
        val button = compose.onNode(hasText("Snooze 30 min") and hasAnyAncestor(hasTestTag("log-example buttons")), useUnmergedTree = true)
        button.assertExists()
        button.performClick()
        compose.onNodeWithText("API reference · Lua").assertDoesNotExist()
        val code = ReferenceExamples.logs.single { it.name == "buttons" }.code(ScriptLang.LUA)
        assertEquals("apple pie\n$code\nbanana apple\nApple cider", text())
    }

    /**
     * Inside an example, only the layer over its card or lines can act. So nothing else can be
     * tapped, also with TalkBack: not a link in a line, not a button of a card.
     */
    @Test fun anExamplesCardAndLinesHaveNoActionsOfTheirOwn() {
        show()
        compose.onNodeWithContentDescription("API reference").performClick()
        compose.waitForText("API reference · Lua")
        for ((tag, cover) in listOf("widget-example buttons" to "widget-example-cover", "log-example links" to "log-example-cover", "log-example buttons" to "log-example-cover")) {
            compose.onNodeWithTag("apiref").performScrollToNode(hasTestTag(tag))
            // The tree that TalkBack reads.
            val acting = compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(tag))).fetchSemanticsNodes()
            assertEquals(tag, listOf(cover), acting.map { it.config.getOrNull(SemanticsProperties.TestTag) })
        }
    }

    /**
     * A fmt function's entry shows what it makes, as the log draws it. A tap on the sample, even on
     * its link, picks the function, as a tap on its entry does.
     */
    @Test fun aFmtSampleShowsWhatItMakesAndPicksTheFunction() {
        show()
        cursorAt(10)
        compose.onNodeWithContentDescription("API reference").performClick()
        compose.waitForText("API reference · Lua")
        compose.onNodeWithTag("apiref").performScrollToNode(hasTestTag("fmt-sample fmt.link"))
        // The line reads as one text: the link is part of it, not a button of its own.
        compose.onNode(hasContentDescription("Read the guide")).assertExists()
        compose.onNodeWithTag("fmt-sample fmt.link").performClick()
        compose.onNodeWithText("API reference · Lua").assertDoesNotExist()
        assertEquals("apple pie\nfmt.link(banana apple\nApple cider", text())
    }

    /** A symbol goes in at the cursor and marks the script changed; undo takes it out again. */
    @Test fun theSymbolBarInsertsAndUndoes() {
        show()
        cursorAt(5)
        compose.onNodeWithText("(").performClick()
        assertEquals("apple( pie\nbanana apple\nApple cider", text())
        compose.onNodeWithText("edbars.lua *").assertExists()
        compose.onNodeWithContentDescription("Undo").performClick()
        assertEquals(code, text())
        compose.onNodeWithText("edbars.lua").assertExists()
    }
}
