package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.waitForNode
import com.mtwabpin.kolahal.waitForText
import com.mtwabpin.kolahal.waitUntil
import com.mtwabpin.kolahal.widgets.TestScriptWidgets
import com.mtwabpin.kolahal.widgets.WidgetRef
import com.mtwabpin.kolahal.widgets.WidgetState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A script's own widget on the widget screens: its row in the picker's Kolahal section, which says
 * "Added" once it is placed and then places another, and its card on the Widgets screen, where a
 * tap selects it.
 */
@RunWith(AndroidJUnit4::class)
class WidgetPickerScriptsTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val logLines = LogLinesSince()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val t = TestScriptWidgets("wpick")

    @After fun tearDown() = t.close()

    private val title = "Wpick Weather"
    private val code = "-- @title $title\n-- @widget stat 2x1\nwidget.show{ title = 'Weather', value = 24, label = 'Sunny' }"

    /** A view model on [screen], reached from the Widgets screen, that has read [scripts]. */
    private fun model(screen: Screen, vararg scripts: Script): TerminalViewModel {
        lateinit var vm: TerminalViewModel
        compose.runOnUiThread {
            vm = vms.terminal(app)
            vm.openScreen(Screen.WIDGETS)
            if (screen != Screen.WIDGETS) vm.openScreen(screen)
        }
        waitUntil("the view model hasn't read the scripts") { vm.scriptList.value.map { it.name }.containsAll(scripts.map { it.name }) }
        return vm
    }

    private fun hasClickLabel(label: String) =
        SemanticsMatcher("click label \"$label\"") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    /** Scrolls the picker's list to [matcher]. */
    private fun scrollTo(matcher: SemanticsMatcher) {
        compose.waitUntil(10_000) { runCatching { compose.onNode(hasScrollToNodeAction()).performScrollToNode(matcher) }.isSuccess }
    }

    /** Types [text] in the search box at the top of the picker's list. */
    private fun search(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToIndex(0)
        compose.onNode(hasSetTextAction()).performTextReplacement(text)
    }

    /**
     * The picker's first section has a row for a script with a `@widget` line, and a Status card
     * for one that sets a status line. A tap places the widget at once and hands over to the
     * Widgets screen. From then on the row says "Added". A second tap on the script's own widget
     * places another one; a Status card is placed once.
     */
    @Test fun thePickerListsAScriptsWidgetAndSaysWhenItIsAdded() {
        val s = t.create("-weather", code)
        val line = t.create("-line", "-- @title Wpick Line\nstatus('3 new')")
        val plain = t.create("-plain", "-- @title Wpick Plain\nreturn 1")
        val vm = model(Screen.WIDGET_PICKER, s, line, plain)
        compose.setContent { WidgetPickerScreen(vm) { } }

        // Its title, its kind and the size of its `@widget` line.
        val row = hasText(title) and hasText("Stat") and hasText("2×1") and hasClickAction()
        val status = hasText("Wpick Line") and hasText("Status card") and hasClickAction()
        scrollTo(row)
        compose.onNodeWithText("Kolahal").assertExists()
        compose.onAllNodes(row and hasText("Added", substring = true)).assertCountEquals(0)
        scrollTo(status)
        compose.onAllNodesWithText("Wpick Plain").assertCountEquals(0)

        // The search finds it by its script's name too, and leaves the others out.
        search(s.name)
        compose.waitForNode(row)
        compose.onAllNodes(status).assertCountEquals(0)
        compose.onAllNodesWithText("Nothing matches “${s.name}”.").assertCountEquals(0)
        search("")
        scrollTo(row)

        compose.onNode(row).performClick()
        compose.runOnIdle { assertEquals(Screen.WIDGETS, vm.screen.value) }
        val placed = t.placed(s)
        assertEquals(6, t.entry(s.name)!!.span)
        assertTrue(placed.grants.allows(ScriptAccess.WIDGET))
        assertEquals("24", (t.stored(s.name).spec as com.mtwabpin.kolahal.widgets.WidgetSpec.Stat).value)
        // The message waits for a widget screen. Here the picker is still up, and shows it.
        compose.waitForText("Added $title")
        scrollTo(row)
        // A script's own widget can be placed again, and the row says so.
        compose.waitForNode(row and hasText("Added · tap to add another"))
        // The Status card is a widget of its own, and is not placed.
        compose.onAllNodes(status and hasText("Added", substring = true)).assertCountEquals(0)

        // A second tap places a further widget of the script, with a run of its own.
        compose.onNode(row).performClick()
        val second = WidgetRef(s.name, "2")
        t.placed(s)
        assertEquals(listOf(WidgetRef(s.name), second), t.host.placedOf(s.name))
        assertEquals("24", (t.stored(second).spec as com.mtwabpin.kolahal.widgets.WidgetSpec.Stat).value)
        compose.waitForText("Added $title 2", ms = 15_000)
        // This script declares no option, so there is nothing to open on the Widgets screen.
        compose.runOnIdle { assertNull(vm.widgetOptionsAsked.value) }

        // A Status card is placed once: its row says "Added", and a second tap places nothing.
        scrollTo(status)
        compose.onNode(status).performClick()
        compose.waitForNode(status and hasText("Added"))
        compose.onNode(status).performClick()
        compose.waitForText("Wpick Line status is already on the widgets page", ms = 20_000)
        assertEquals(1, t.host.widgets.value.count { it.source == "status:${line.name}" })
    }

    /**
     * A tap on the row of a script whose widget is on the page places a further widget, and hands
     * over to the Widgets screen. That screen opens the new widget's options, so you can set what
     * differs from the first one.
     */
    @Test fun aTapOnAnAddedScriptAddsAnotherAndOpensItsOptions() {
        val s = t.place(t.create("-city", "-- @title Wpick City\n-- @widget stat 2x1\n-- @option city London The city to show\nwidget.show{ value = options.city }"))
        val vm = model(Screen.WIDGET_PICKER, s)
        compose.setContent {
            // As home does: the picker, and the Widgets screen once the picker has closed.
            val screen = vm.screen.collectAsState().value
            if (screen == Screen.WIDGET_PICKER) WidgetPickerScreen(vm) { } else WidgetsScreen(vm) { _, _ -> }
        }
        val row = hasText("Wpick City") and hasText("Stat") and hasClickAction()
        scrollTo(row)
        compose.waitForNode(row and hasText("Added · tap to add another"))
        compose.onNode(row).performClick()

        val second = WidgetRef(s.name, "2")
        compose.runOnIdle { assertEquals(Screen.WIDGETS, vm.screen.value) }
        t.placed(s)
        assertEquals(listOf(WidgetRef(s.name), second), t.host.placedOf(s.name))
        // It follows the script's options until one is set for it.
        assertEquals("London", (t.stored(second).spec as com.mtwabpin.kolahal.widgets.WidgetSpec.Stat).value)
        // The Widgets screen shows its options, and says that they are this widget's own.
        compose.waitForText("Options for Wpick City 2")
        compose.onNodeWithText(WidgetOptionWords.WIDGET).assertExists()
        compose.runOnIdle { assertNull(vm.widgetOptionsAsked.value) }
    }

    /** At six widgets of one script the row counts them, and a tap places no more. */
    @Test fun aScriptWithSixWidgetsGetsNoMore() {
        val s = t.place(t.create("-weather", code))
        repeat(WidgetRef.MAX_PER_SCRIPT - 1) { assertTrue(t.host.placeAnother(s, com.mtwabpin.kolahal.widgets.WidgetSize.S2X1) != null) }
        t.placed(s)
        val vm = model(Screen.WIDGET_PICKER, s)
        compose.setContent { WidgetPickerScreen(vm) { } }
        val row = hasText(title) and hasText("Stat") and hasClickAction()
        scrollTo(row)
        compose.waitForNode(row and hasText("Added · 6 of 6"))
        compose.onNode(row).performClick()
        compose.waitForText("$title has 6 widgets already, the most a script may have")
        assertEquals(6, t.host.placedOf(s.name).size)
        compose.runOnIdle { assertEquals(Screen.WIDGET_PICKER, vm.screen.value) }
    }

    /**
     * The Widgets screen draws the script's card as the widgets page does, with what the script
     * showed. The card takes no taps of its own there: a tap selects it, to resize, move or remove it.
     */
    @Test fun theWidgetsScreenDrawsTheCardAndATapSelectsIt() {
        val s = t.place(t.create("-weather", code))
        t.run(s)
        val height = t.entry(s.name)!!.heightDp
        val vm = model(Screen.WIDGETS, s)
        compose.setContent { WidgetsScreen(vm) { _, _ -> } }

        val inCard = hasAnyAncestor(hasTestTag("widget:${s.name}"))
        fun drawn() = compose.onAllNodes(inCard and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
        compose.waitFor("the card doesn't draw what the script showed") { drawn().containsAll(listOf("Weather", "24", "Sunny")) }
        compose.onNode(inCard and hasContentDescription("Weather: 24, Sunny")).assertExists()
        compose.onAllNodes(inCard and hasClickAction()).assertCountEquals(0)
        compose.onAllNodes(isSelected()).assertCountEquals(0)

        // It is named by its script's title.
        compose.onNode(hasClickLabel("change $title")).performClick()
        compose.onNode(isSelected()).assertExists()
        compose.onNodeWithText(title).assertExists()
        compose.onNodeWithText("1/2 wide · ${height}dp tall").assertExists()

        // A widget Kolahal draws is at least one cell wide: a quarter of the page.
        repeat(3) { compose.onNodeWithContentDescription("Decrease width").assertIsEnabled().performClick() }
        compose.onNodeWithText("1/4 wide · ${height}dp tall").assertExists()
        compose.onNodeWithContentDescription("Decrease width").assertIsNotEnabled()
        assertEquals(3, t.entry(s.name)!!.span)
        compose.waitFor("the narrow card doesn't draw the value") { drawn().containsAll(listOf("24", "Sunny")) }

        // Remove asks first. Then the widget is off the page, and its script keeps nothing for it.
        compose.onNodeWithContentDescription("Remove").performClick()
        compose.onNodeWithText("Remove $title?").assertExists()
        compose.onNodeWithText("Remove").performClick()
        compose.waitForIdle()
        assertNull(t.entry(s.name))
        t.host.settle()
        assertFalse(t.scripts.byId(s.id)!!.grants.allows(ScriptAccess.WIDGET))
        assertEquals(WidgetState(null, false), t.stored(s.name))
        compose.onAllNodes(hasTestTag("widget:${s.name}")).assertCountEquals(0)
        compose.onAllNodes(isSelected()).assertCountEquals(0)
    }
}
