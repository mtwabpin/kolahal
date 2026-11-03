package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.widgets.ProviderEntry
import com.mtwabpin.kolahal.widgets.TestWidgets
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings → Widgets → Add widget: each app's widgets with their names and sizes, a search, and a tap to pick one. */
@RunWith(AndroidJUnit4::class)
class WidgetPickerScreenTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val config = Config.get(app)
    private val t = TestWidgets()
    private val pm = app.packageManager
    private var picked: ProviderEntry? = null

    @After fun tearDown() {
        config.reset(Keys.WIDGETS_PAGE)
        t.close()
    }

    /** The picker, opened from the Widgets screen as Add widget does. */
    private fun show(): TerminalViewModel {
        lateinit var vm: TerminalViewModel
        compose.runOnUiThread {
            vm = vms.terminal(app)
            vm.openScreen(Screen.WIDGETS)
            vm.openScreen(Screen.WIDGET_PICKER)
        }
        compose.setContent { WidgetPickerScreen(vm) { picked = it } }
        return vm
    }

    private fun appName(p: ProviderEntry) = pm.getApplicationLabel(pm.getApplicationInfo(p.packageName, 0)).toString()

    /** [p]'s card: its name and its size in cells, a tap to pick it. */
    private fun card(p: ProviderEntry): SemanticsMatcher = hasText(t.label(p)) and hasText(t.host.sizeLabel(p.info)) and hasClickAction()

    /** Scrolls the list to [matcher] once the widgets are loaded. */
    private fun scrollTo(matcher: SemanticsMatcher) {
        compose.waitUntil(10_000) { runCatching { compose.onNode(hasScrollToNodeAction()).performScrollToNode(matcher) }.isSuccess }
    }

    /** Types [text] in the search box at the top of the list. */
    private fun search(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToIndex(0)
        compose.onNode(hasSetTextAction()).performTextReplacement(text)
    }

    /** Every clock widget is listed under the clock app, by name, with its size. */
    @Test fun eachWidgetShowsItsNameAndSize() {
        val clocks = t.clocks()
        show()
        compose.onNodeWithText("Added to the widgets page").assertExists()
        scrollTo(hasText(appName(clocks.first())))
        clocks.forEach { p ->
            scrollTo(card(p))
            assertTrue("no card for ${p.flat}", compose.onAllNodes(card(p)).fetchSemanticsNodes().isNotEmpty())
        }
    }

    /** Searching a widget's name leaves it and its app; the app's other widgets go. */
    @Test fun searchingAWidgetsNameShowsOnlyIt() {
        val clocks = t.clocks()
        assumeTrue("only one clock widget", clocks.size > 1)
        val mine = t.host.providers().filter { it.user == clocks.first().user }
        val apps = mine.map { appName(it).lowercase() }.toSet()
        // A name no other widget's or app's name holds.
        val p = clocks.firstOrNull { c ->
            val q = t.label(c).lowercase()
            mine.count { q in t.label(it).lowercase() } == 1 && apps.none { q in it }
        }
        assumeTrue("every clock widget's name is part of another's", p != null)
        show()
        scrollTo(card(clocks.first()))
        search(t.label(p!!))
        scrollTo(card(p))
        compose.onAllNodesWithText(appName(p)).onFirst().assertExists()
        (clocks - p).forEach { compose.onAllNodes(card(it)).assertCountEquals(0) }
    }

    /** Searching the app's name shows all of its widgets. */
    @Test fun searchingAnAppsNameShowsAllItsWidgets() {
        val clocks = t.clocks()
        show()
        scrollTo(card(clocks.first()))
        search(appName(clocks.first()))
        clocks.forEach { p -> scrollTo(card(p)) }
    }

    /** A search that finds nothing says so. */
    @Test fun aSearchWithNoMatchSaysSo() {
        val clock = t.clocks().first()
        show()
        scrollTo(card(clock))
        search("zzqx kolahal")
        compose.onNodeWithText("Nothing matches “zzqx kolahal”.").assertExists()
        compose.onAllNodes(card(clock)).assertCountEquals(0)
    }

    /** A tap on a card picks that widget. */
    @Test fun aTapPicksTheWidget() {
        val clock = t.clocks().last()
        show()
        scrollTo(card(clock))
        compose.onAllNodes(card(clock)).onFirst().performClick()
        compose.runOnIdle { assertEquals(clock.flat, picked?.flat) }
    }

    /** With the widgets page off, the picker says where the widget goes is off. */
    @Test fun itSaysWhenTheWidgetsPageIsOff() {
        config.set(Keys.WIDGETS_PAGE, "off")
        show()
        compose.onNodeWithText("Added to the widgets page, which is off").assertExists()
    }

    /** A message waiting for a widget screen ("Widget not added") shows on the picker. */
    @Test fun aWaitingMessageShowsHere() {
        t.host.message("Widget not added")
        show()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Widget not added").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(t.host.messages.value.isEmpty())
    }

    /** Back returns to the Widgets screen the picker was opened from. */
    @Test fun backReturnsToTheWidgetsScreen() {
        val vm = show()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(Screen.WIDGETS, vm.screen.value) }
    }
}
