package com.mtwabpin.kolahal.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
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
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
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

    /** Where the code is marked as found: each match's start, and the current one's. */
    private fun marks(): Pair<List<Int>, Int?> {
        val results = mutableListOf<TextLayoutResult>()
        editor()[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        val found = results.single().layoutInput.text.spanStyles.filter { it.item.background != Color.Unspecified }
        return found.map { it.start }.sorted() to found.firstOrNull { it.item.color == Term.bg }?.start
    }

    private fun waitForText(text: String) =
        compose.waitUntil(3_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    /** Opens find and types [query]; matches are found a beat after typing. */
    private fun find(query: String, count: String) {
        compose.onNodeWithContentDescription("Find").performClick()
        compose.onNodeWithTag("find").performTextInput(query)
        waitForText(count)
    }

    private fun openReplace(with: String) {
        compose.onNodeWithText("⇄").performClick()
        compose.onNodeWithTag("replace").performTextInput(with)
    }

    /** Find marks every match and selects the first; ↓, Enter and ↑ step through them, wrapping at the ends. */
    @Test fun findMarksEveryMatchAndStepsThroughThem() {
        show()
        find("apple", "1/3")
        assertEquals(TextRange(0, 5), selection())
        assertEquals(listOf(0, 17, 23) to 0, marks())
        compose.onNodeWithText("↓").performClick()
        waitForText("2/3")
        assertEquals(TextRange(17, 22), selection())
        assertEquals(listOf(0, 17, 23) to 17, marks())
        compose.onNodeWithTag("find").performImeAction()
        waitForText("3/3")
        assertEquals(TextRange(23, 28), selection())
        compose.onNodeWithText("↓").performClick()
        waitForText("1/3")
        compose.onNodeWithText("↑").performClick()
        waitForText("3/3")
        assertEquals(TextRange(23, 28), selection())
    }

    /** Aa matches case: "Apple" drops out of "apple"'s matches until it's tapped again. */
    @Test fun matchCaseLeavesOnlyTheExactOnes() {
        show()
        find("apple", "1/3")
        compose.onNodeWithText("Aa").performClick()
        waitForText("1/2")
        assertEquals(listOf(0, 17) to 0, marks())
        compose.onNodeWithText("Aa").performClick()
        waitForText("1/3")
    }

    /** Text that isn't there counts 0/0 and marks nothing. */
    @Test fun aSearchWithNoMatchCountsZero() {
        show()
        find("plum", "0/0")
        assertEquals(emptyList<Int>() to null, marks())
    }

    /** × closes the bar and the marks go with it. */
    @Test fun closingFindTakesTheMarksAway() {
        show()
        find("apple", "1/3")
        compose.onNodeWithText("×").performClick()
        compose.onNodeWithTag("findbar").assertDoesNotExist()
        assertEquals(emptyList<Int>() to null, marks())
    }

    /** All replaces every match at once and says how many. */
    @Test fun replaceAllReplacesEveryMatch() {
        show()
        find("apple", "1/3")
        openReplace("pear")
        compose.onNodeWithText("[all]").performClick()
        assertEquals("pear pie\nbanana pear\npear cider", text())
        waitForText("Replaced 3")
        compose.onNodeWithText("edbars.lua *").assertExists()
        waitForText("0/0")
    }

    /** Replace changes the selected match and selects the next; Enter in the replace box does the same. */
    @Test fun replaceChangesOneMatchAtATime() {
        show()
        find("apple", "1/3")
        openReplace("pear")
        compose.onNodeWithText("[replace]").performClick()
        assertEquals("pear pie\nbanana apple\nApple cider", text())
        assertEquals(TextRange(16, 21), selection())
        compose.onNodeWithText("[replace]").performClick()
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
        compose.onNodeWithText("[all]").performClick()
        waitForText("No matches to replace")
        assertEquals(code, text())
        compose.onNodeWithText("edbars.lua").assertExists()
    }

    /** Ln asks for a line, takes only digits, refuses one past the end, and puts the cursor at the start of the one given. */
    @Test fun goToLinePutsTheCursorOnTheLine() {
        show()
        // The symbol bar: the last list that scrolls sideways (suggestions, when shown, sit above it).
        compose.onAllNodes(hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .onLast().performScrollToNode(hasText("Ln"))
        compose.onNodeWithText("Ln").performClick()
        compose.onNodeWithText("Go to line").assertExists()
        compose.onNodeWithText("Line (1–3)", useUnmergedTree = true).assertExists()
        val field = compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
        field.performTextInput("7")
        compose.onNodeWithText("Go").assertIsNotEnabled()
        field.performTextReplacement("x2")
        compose.onNodeWithText("Go").assertIsEnabled().performClick()
        compose.onNodeWithText("Go to line").assertDoesNotExist()
        assertEquals(TextRange(10), selection())
    }

    /** A function picked from the API reference goes in at the cursor, ready for its arguments. */
    @Test fun theReferenceInsertsAFunctionAtTheCursor() {
        show()
        compose.onNodeWithText("[?]").performClick()
        waitForText("API reference · Lua")
        compose.onNodeWithTag("apiref").performScrollToNode(hasText("log(title, body?, ctx?)"))
        compose.onNodeWithText("log(title, body?, ctx?)").performClick()
        compose.onNodeWithText("API reference · Lua").assertDoesNotExist()
        assertEquals("${code}log(", text())
    }

    /** A directive picked from the reference goes on a line of its own above the cursor's. */
    @Test fun theReferencePutsADirectiveAboveTheLine() {
        show()
        compose.onNodeWithText("[?]").performClick()
        waitForText("API reference · Lua")
        compose.onNodeWithTag("apiref").performScrollToNode(hasText("-- @command"))
        compose.onNodeWithText("-- @command").performClick()
        assertEquals("apple pie\nbanana apple\n-- @command\nApple cider", text())
    }

    /** A symbol goes in at the cursor and marks the script changed; ↶ takes it out again. */
    @Test fun theSymbolBarInsertsAndUndoes() {
        show()
        compose.onNodeWithText("(").performClick()
        assertEquals("$code(", text())
        compose.onNodeWithText("edbars.lua *").assertExists()
        compose.onNodeWithText("↶").performClick()
        assertEquals(code, text())
        compose.onNodeWithText("edbars.lua").assertExists()
    }
}
