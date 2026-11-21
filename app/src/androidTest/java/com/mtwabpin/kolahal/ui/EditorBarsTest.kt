package com.mtwabpin.kolahal.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The script editor's find and replace bar, Go to line, the reference page and the symbol bar. */
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

    // The unmerged tree: under the reference page, TalkBack (the merged tree) skips the code.
    private fun editor() = compose.onNodeWithTag("editor", useUnmergedTree = true).fetchSemanticsNode().config
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

    /** Opens the reference page. */
    private fun openReference() {
        compose.onNodeWithContentDescription("API reference").performClick()
        compose.onNodeWithTag("reference").assertExists()
    }

    private fun tab(label: String) = compose.onNodeWithTag("tab $label").performClick()

    /** The list of the tab in view. */
    private fun list() = compose.onNodeWithTag("reference-list")

    private fun referenceClosed() = compose.onNodeWithTag("reference").assertDoesNotExist()

    /** Opens the Widgets tab and [name]'s view. */
    private fun openWidget(name: String) {
        tab("Widgets")
        list().performScrollToNode(hasTestTag("widget-row $name"))
        compose.onNodeWithTag("widget-row $name").performClick()
        compose.onNodeWithTag("widget-view $name").assertExists()
    }

    /** [text] somewhere in the card or lines of the example's view, as drawn. */
    private fun inPreview(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("example-preview")), useUnmergedTree = true)

    private fun clipboard(): String? {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        var copied: String? = null
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            copied = context.getSystemService(android.content.ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()
        }
        return copied
    }

    private fun pressBack() {
        androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitForIdle()
    }

    /** A function picked from the reference goes in at the cursor, ready for its arguments, and the page closes. */
    @Test fun theReferenceInsertsAFunctionAtTheCursor() {
        show()
        cursorAt(10)
        openReference()
        compose.onNodeWithText("Lua").assertExists()
        list().performScrollToNode(hasText("log(title, body?, ctx?) -> line"))
        compose.onNodeWithText("log(title, body?, ctx?) -> line").performClick()
        referenceClosed()
        assertEquals("apple pie\nlog(banana apple\nApple cider", text())
    }

    /** A directive picked from the reference goes on a line of its own above the cursor's. */
    @Test fun theReferencePutsADirectiveAboveTheLine() {
        show()
        // Inside the second line.
        cursorAt(13)
        openReference()
        tab("Directives")
        list().performScrollToNode(hasText("-- @command"))
        compose.onNodeWithText("-- @command").performClick()
        assertEquals("apple pie\n-- @command\nbanana apple\nApple cider", text())
    }

    /** Each tab shows its own list; the chosen tab is marked. */
    @Test fun theTabsSwitch() {
        show()
        openReference()
        compose.onNodeWithTag("tab Functions").assertIsSelected()
        compose.onNodeWithTag("namespace All").assertExists()
        for ((label, tag) in listOf("Widgets" to "widget-row steps", "Log lines" to "log-row log", "Icons" to "icon sun")) {
            tab(label)
            compose.onNodeWithTag("tab $label").assertIsSelected()
            compose.onNodeWithTag("tab Functions").assertIsNotSelected()
            compose.onNodeWithTag(tag).assertExists()
            compose.onNodeWithTag("namespace All").assertDoesNotExist()
        }
        tab("Directives")
        compose.onNodeWithText("-- @command").assertExists()
    }

    /** After an insert closes the reference, it reopens on the tab it was on, and keeps its search. */
    @Test fun theReferenceReopensWhereItWas() {
        show()
        openReference()
        tab("Icons")
        compose.onNodeWithTag("icon sun").performClick()
        referenceClosed()
        openReference()
        compose.onNodeWithTag("tab Icons").assertIsSelected()
        compose.onNodeWithTag("icon sun").assertExists()

        compose.onNodeWithTag("reference-search").performTextInput("sun")
        compose.onNodeWithTag("icon sun").performClick()
        referenceClosed()
        openReference()
        compose.onNodeWithTag("reference-search").assert(hasText("sun"))
    }

    /** A namespace's chip shows only that group, from its top; All shows them all again. */
    @Test fun aNamespaceChipShowsItsGroup() {
        show()
        openReference()
        compose.onNodeWithText("log(title, body?, ctx?) -> line").assertExists()
        compose.onNodeWithTag("namespaces").performScrollToNode(hasTestTag("namespace store"))
        compose.onNodeWithTag("namespace store").performClick()
        compose.onNodeWithTag("namespace store").assertIsSelected()
        compose.onNodeWithText("Persistent", substring = true).assertExists()
        compose.onNodeWithText("store.get", substring = true).assertExists()
        compose.onNodeWithText("log(title, body?, ctx?) -> line").assertDoesNotExist()
        compose.onNodeWithText("http.get", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("namespaces").performScrollToNode(hasTestTag("namespace All"))
        compose.onNodeWithTag("namespace All").performClick()
        compose.onNodeWithText("log(title, body?, ctx?) -> line").assertExists()
    }

    /**
     * A widget example opens in a view of its own, with its card drawn. A tap on the card, even on
     * one of its buttons, runs nothing and puts nothing in; Insert puts the example in, on lines of
     * its own, and closes the page.
     */
    @Test fun aWidgetExampleShowsItsCardAndGoesInWithInsert() {
        show()
        // The start of the second line.
        cursorAt(10)
        openReference()
        openWidget("buttons")
        inPreview("Start").assertExists()
        inPreview("Stop").performClick()
        compose.onNodeWithTag("widget-view buttons").assertExists()
        assertEquals(code, text())
        compose.onNodeWithTag("example-insert").performClick()
        referenceClosed()
        val example = ReferenceExamples.widgets.single { it.name == "buttons" }.code(ScriptLang.LUA)
        assertEquals("apple pie\n$example\nbanana apple\nApple cider", text())
    }

    /** Only the sizes the kind is made for, its own chosen; another size redraws the card. Copy puts the code on the clipboard and nothing in the script. */
    @Test fun aWidgetExampleCopiesItsCodeAndChangesSize() {
        show()
        openReference()
        openWidget("toggles")
        listOf("2×1", "2×2", "4×1").forEach { compose.onNodeWithTag("size toggles $it").assertExists() }
        compose.onNodeWithTag("size toggles 4×2").assertDoesNotExist()
        compose.onNodeWithTag("size toggles 4×1").assertIsOn()
        fun cardWidth() = compose.onNode(hasTestTag("widget-card") and hasAnyAncestor(hasTestTag("example-preview")), useUnmergedTree = true)
            .fetchSemanticsNode().size.width
        val wide = cardWidth()
        compose.onNodeWithTag("size toggles 2×1").performClick()
        compose.onNodeWithTag("size toggles 2×1").assertIsOn()
        compose.waitForIdle()
        assertTrue("the card got narrower", cardWidth() < wide)
        compose.onNodeWithTag("example-copy").performClick()
        compose.waitForIdle()
        assertEquals(ReferenceExamples.widgets.single { it.name == "toggles" }.code(ScriptLang.LUA), clipboard())
        compose.onNodeWithTag("widget-view toggles").assertExists()
        assertEquals(code, text())
    }

    /**
     * An example's variants: a choice for each, the guide's example first. The chosen one is drawn,
     * and Copy and Insert take its code. Pictures of the view go to the app's files ("reference"),
     * to look at.
     */
    @Test fun aWidgetExampleShowsItsVariants() {
        show()
        cursorAt(10)
        openReference()
        val out = checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("reference"))
        fun picture(name: String) = java.io.File(out, "$name.png").outputStream().use {
            compose.onNodeWithTag("reference").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        openWidget("toggles")
        compose.onNodeWithTag("variant toggles As in the guide").assertIsOn()
        picture("view-toggles-guide")
        compose.onNodeWithTag("variant toggles 4 switches").performClick()
        compose.onNodeWithTag("variant toggles 4 switches").assertIsOn()
        compose.waitForIdle()
        picture("view-toggles-4")
        val example = ReferenceExamples.variantsOf("toggles").single { it.variant == "4 switches" }.code(ScriptLang.LUA)
        compose.onNodeWithTag("example-copy").performClick()
        compose.waitForIdle()
        assertEquals(example, clipboard())
        compose.onNodeWithTag("example-insert").performClick()
        assertEquals("apple pie\n$example\nbanana apple\nApple cider", text())
    }

    /** The Look row offers only the looks of the example's kind: switches have no Bold, a stat does. */
    @Test fun aWidgetExampleOffersItsKindsLooks() {
        show()
        openReference()
        openWidget("toggles")
        listOf("Plain", "Tonal", "Split", "Shape").forEach { compose.onNodeWithTag("look toggles $it").assertExists() }
        compose.onNodeWithTag("look toggles Bold").assertDoesNotExist()
        compose.onNodeWithTag("look toggles Plain").assertIsOn()
        compose.onNodeWithContentDescription("Back").performClick()
        openWidget("steps")
        compose.onNodeWithTag("look steps Bold").performClick()
        compose.onNodeWithTag("look steps Bold").assertIsOn()
    }

    /**
     * A log example opens in a view of its own, with its lines. A tap on a line, even on one of its
     * buttons, runs nothing; Insert puts the example in, on lines of its own.
     */
    @Test fun aLogExampleShowsItsLinesAndGoesInWithInsert() {
        show()
        cursorAt(10)
        openReference()
        tab("Log lines")
        list().performScrollToNode(hasTestTag("log-row buttons"))
        compose.onNodeWithTag("log-row buttons").performClick()
        inPreview("Snooze 30 min").assertExists()
        inPreview("Snooze 30 min").performClick()
        assertEquals(code, text())
        compose.onNodeWithTag("example-insert").performClick()
        referenceClosed()
        val example = ReferenceExamples.logs.single { it.name == "buttons" }.code(ScriptLang.LUA)
        assertEquals("apple pie\n$example\nbanana apple\nApple cider", text())
    }

    /**
     * An example's card or lines have nothing to tap, also with TalkBack: not a link in a line, not a
     * button of a card. TalkBack reads the card as its description.
     */
    @Test fun anExamplesCardAndLinesHaveNoActionsOfTheirOwn() {
        show()
        openReference()
        fun acting() = compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("example-preview"))).fetchSemanticsNodes()
        openWidget("buttons")
        assertEquals(0, acting().size)
        compose.onNode(hasTestTag("example-preview")).assertExists()
        assertTrue(
            "the card is read as its description",
            compose.onAllNodes(hasAnyAncestor(hasTestTag("example-preview"))).fetchSemanticsNodes()
                .any { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { d -> d.isNotBlank() } },
        )
        compose.onNodeWithContentDescription("Back").performClick()
        for (name in listOf("links", "buttons")) {
            tab("Log lines")
            list().performScrollToNode(hasTestTag("log-row $name"))
            compose.onNodeWithTag("log-row $name").performClick()
            assertEquals(name, 0, acting().size)
            compose.onNodeWithContentDescription("Back").performClick()
        }
    }

    /**
     * A fmt function's entry shows what it makes, as the log draws it. A tap on the sample, even on
     * its link, picks the function, as a tap on its entry does.
     */
    @Test fun aFmtSampleShowsWhatItMakesAndPicksTheFunction() {
        show()
        cursorAt(10)
        openReference()
        compose.onNodeWithTag("namespace fmt").performClick()
        // The line reads as one text, in its entry: the link is part of it, not a button of its own.
        list().performScrollToNode(hasContentDescription("Read the guide", substring = true))
        compose.onNodeWithTag("fmt-sample fmt.link", useUnmergedTree = true).performClick()
        referenceClosed()
        assertEquals("apple pie\nfmt.link(banana apple\nApple cider", text())
    }

    /** An icon goes in by its name, in quotes. */
    @Test fun anIconGoesInByName() {
        show()
        cursorAt(10)
        openReference()
        tab("Icons")
        list().performScrollToNode(hasTestTag("icon train"))
        compose.onNodeWithTag("icon train").performClick()
        referenceClosed()
        assertEquals("apple pie\n\"train\"banana apple\nApple cider", text())
    }

    /**
     * The search looks in every tab at once and lists what it finds by tab, with counts. A result
     * opens where it lives; Back returns to the results, and clearing the search to the tabs.
     */
    @Test fun theSearchFindsInEveryTabAndOpensAResult() {
        show()
        openReference()
        compose.onNodeWithTag("reference-search").performTextInput("store")
        compose.waitForText("FUNCTIONS · ", substring = true)
        compose.onNodeWithText("WIDGETS · ", substring = true).assertExists()
        compose.onNodeWithTag("tab Functions").assertDoesNotExist()
        compose.onNodeWithTag("reference-results").performScrollToNode(hasTestTag("widget-row toggles-2"))
        compose.onNodeWithTag("widget-row toggles-2").performClick()
        // The variant found is the one chosen.
        compose.onNodeWithTag("widget-view toggles").assertExists()
        compose.onNodeWithTag("variant toggles 2 switches").assertIsOn()
        pressBack()
        compose.onNodeWithTag("reference-results").assertExists()
        compose.onNodeWithTag("widget-row toggles-2").assertExists()
        compose.onNodeWithContentDescription("Clear the search").performClick()
        compose.onNodeWithTag("tab Functions").assertExists()
        compose.onNodeWithTag("reference-results").assertDoesNotExist()
    }

    /** A function found by the search goes in as from its tab. */
    @Test fun aFunctionFoundGoesIn() {
        show()
        cursorAt(10)
        openReference()
        compose.onNodeWithTag("reference-search").performTextInput("store.set")
        compose.waitForText("FUNCTIONS · 1")
        compose.onNodeWithText("store.set(key, value)").performClick()
        referenceClosed()
        assertEquals("apple pie\nstore.set(banana apple\nApple cider", text())
    }

    /** Back closes an example's view, then the page; the script is as it was, with the cursor where it was. */
    @Test fun backReturnsToTheScriptUnchanged() {
        show()
        cursorAt(13)
        openReference()
        openWidget("steps")
        pressBack()
        compose.onNodeWithTag("widget-view steps").assertDoesNotExist()
        compose.onNodeWithTag("reference-list").assertExists()
        pressBack()
        referenceClosed()
        assertEquals(code, text())
        assertEquals(TextRange(13), selection())
        // The page's own Back does the same.
        openReference()
        compose.onNodeWithContentDescription("Back").performClick()
        referenceClosed()
        assertEquals(code, text())
        assertEquals(TextRange(13), selection())
        compose.onNodeWithText("edbars.lua").assertExists()
    }

    /** Pictures of the page, to compare with its design: they go to the app's files ("reference"). */
    @Test fun picturesOfThePage() {
        show()
        openReference()
        val out = checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("reference"))
        fun picture(name: String) {
            compose.waitForIdle()
            java.io.File(out, "$name.png").outputStream().use {
                compose.onNodeWithTag("reference").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        picture("1-functions")
        compose.onNodeWithTag("namespace fmt").performClick()
        picture("1-functions-fmt")
        tab("Directives")
        picture("2-directives")
        tab("Widgets")
        picture("3-widgets")
        openWidget("toggles")
        picture("4-view-toggles")
        compose.onNodeWithContentDescription("Back").performClick()
        openWidget("list")
        picture("4-view-list")
        compose.onNodeWithContentDescription("Back").performClick()
        tab("Log lines")
        picture("5-logs")
        compose.onNodeWithTag("log-row log").performClick()
        picture("5-view-log")
        compose.onNodeWithContentDescription("Back").performClick()
        tab("Icons")
        picture("6-icons")
        compose.onNodeWithTag("reference-search").performTextInput("store")
        compose.waitForText("FUNCTIONS · ", substring = true)
        picture("7-search")
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
