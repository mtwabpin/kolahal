package com.mtwabpin.kolahal.ui.reference

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A log example's view draws its lines in either home style: GUI or TTY, one tap apart. It starts
 * on home's style, a tap changes only the reference, and the choice holds from example to example.
 * Widget examples have no such choice.
 */
@RunWith(AndroidJUnit4::class)
class ReferenceStyleTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val configReset = ConfigReset()

    private val config =
        Config.get(InstrumentationRegistry.getInstrumentation().targetContext)

    private val page: @Composable () -> Unit = {
        KolahalTheme {
            ReferencePage(
                ScriptLang.LUA,
                onBack = {},
                onPick = {},
                onDirective = {},
                onIcon = {},
                onExample = {},
            )
        }
    }

    /** The reference page, in home's [style]. With [restoration], it can be torn down and restored as after a rotation. */
    private fun show(style: String, restoration: StateRestorationTester? = null) {
        config.set(Keys.HOME_STYLE, style)
        if (restoration != null) restoration.setContent(page) else compose.setContent(page)
    }

    private fun tab(label: String) = compose.onNodeWithTag("tab $label").performClick()

    /** Opens the Log lines tab and [name]'s view. */
    private fun openLog(name: String) {
        tab("Log lines")
        compose.onNodeWithTag("reference-list").performScrollToNode(hasTestTag("log-row $name"))
        compose.onNodeWithTag("log-row $name").performClick()
        compose.onNodeWithTag("log-view $name").assertExists()
    }

    private fun back() = compose.onNodeWithContentDescription("Back").performClick()

    /** [text] as a text node in the example's view: the GUI draws its words as text, the grid draws them itself. */
    private fun inPreview(text: String) = compose.onNode(
        hasText(text) and hasAnyAncestor(hasTestTag("example-preview")),
        useUnmergedTree = true
    )

    /** The lines on the grid, in the example's view. */
    private fun gridInPreview() = compose.onNode(
        hasTestTag("tty-lines") and hasAnyAncestor(hasTestTag("example-preview")),
        useUnmergedTree = true
    )

    /** The node reads [text] (TalkBack, and tests, find it by its words). */
    private fun reads(text: String) = SemanticsMatcher("reads “$text”") { node ->
        node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
            .any { it.contains(text) }
    }

    private val title = StyledText.plain(ReferenceExamples.logs.single { it.name == "buttons" }.lines.first().text)

    /** A log example has the choice, a widget example doesn't; it starts on gui when home is gui. */
    @Test
    fun aLogExampleHasTheChoiceAndAWidgetExampleDoesnt() {
        show("gui")
        openLog("buttons")
        compose.onNodeWithTag("style buttons GUI").assertIsOn()
        compose.onNodeWithTag("style buttons TTY").assertIsOff()
        inPreview("Snooze 30 min").assertExists()
        gridInPreview().assertDoesNotExist()
        back()
        tab("Widgets")
        compose.onNodeWithTag("reference-list").performScrollToNode(hasTestTag("widget-row buttons"))
        compose.onNodeWithTag("widget-row buttons").performClick()
        compose.onNodeWithTag("widget-view buttons").assertExists()
        compose.onNodeWithTag("style buttons GUI").assertDoesNotExist()
        compose.onNodeWithTag("style buttons TTY").assertDoesNotExist()
    }

    /** When home is on the grid, a log example starts on TTY, its lines drawn on the grid. */
    @Test
    fun itStartsOnHomesStyle() {
        show("tty")
        openLog("buttons")
        compose.onNodeWithTag("style buttons TTY").assertIsOn()
        gridInPreview().assert(reads(title))
        inPreview("Snooze 30 min").assertDoesNotExist()
    }

    /**
     * TTY draws the example's lines on the grid: no text nodes, the same words read once. The
     * setting stays as it was, and GUI brings the text back.
     */
    @Test
    fun ttyDrawsTheLinesOnTheGrid() {
        show("gui")
        openLog("buttons")
        compose.onNodeWithTag("style buttons TTY").performClick()
        compose.onNodeWithTag("style buttons TTY").assertIsOn()
        gridInPreview().assert(reads(title))
        // The grid draws the words and the buttons itself.
        inPreview("Snooze 30 min").assertDoesNotExist()
        inPreview(title).assertDoesNotExist()
        assertEquals("gui", config[Keys.HOME_STYLE])
        compose.onNodeWithTag("style buttons GUI").performClick()
        inPreview("Snooze 30 min").assertExists()
        gridInPreview().assertDoesNotExist()
    }

    /** The choice holds when another example opens, in the list's small previews, and after a rotation. */
    @Test
    fun theChoiceStaysForTheNextExample() {
        val restoration = StateRestorationTester(compose)
        show("gui", restoration)
        openLog("buttons")
        compose.onNodeWithTag("style buttons TTY").performClick()
        back()
        // The list's small previews follow it.
        compose.onNode(
            hasTestTag("tty-lines") and hasAnyAncestor(hasTestTag("log-row buttons")),
            useUnmergedTree = true
        ).assertExists()
        openLog("links")
        compose.onNodeWithTag("style links TTY").assertIsOn()
        gridInPreview().assertExists()
        restoration.emulateSavedInstanceStateRestore()
        openLog("links")
        compose.onNodeWithTag("style links TTY").assertIsOn()
        gridInPreview().assertExists()
    }
}
