package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.ProviderEntry
import com.mtwabpin.kolahal.widgets.TestWidgets
import com.mtwabpin.kolahal.widgets.WidgetLayout
import com.mtwabpin.kolahal.widgets.WidgetLocation
import com.mtwabpin.kolahal.widgets.WidgetOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings → Widgets → Arrange widgets: placed widgets live, their size and order, and the ones to add again. */
@RunWith(AndroidJUnit4::class)
class WidgetsScreenTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val config = Config.get(app)
    private val t = TestWidgets()
    private val widgets = t.host

    @After fun tearDown() {
        config.reset(Keys.WIDGETS_PAGE)
        t.close()
        widgets.releaseViews(compose.activity)
    }

    private fun show(onReAdd: (ProviderEntry, PlacedWidget) -> Unit = { _, _ -> }): TerminalViewModel {
        lateinit var vm: TerminalViewModel
        compose.runOnUiThread {
            vm = vms.terminal(app)
            vm.openScreen(Screen.WIDGETS)
        }
        compose.setContent { WidgetsScreen(vm, onReAdd) }
        return vm
    }

    private fun hasClickLabel(label: String) =
        SemanticsMatcher("click label \"$label\"") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    /** Edit, then a tap on the widget called [label]: its toolbar comes up. */
    private fun choose(label: String) {
        compose.onNodeWithContentDescription("Edit widgets").performClick()
        compose.onNodeWithText("Tap a widget to resize or move it").assertExists()
        compose.onNode(hasClickLabel("change $label")).performClick()
        compose.onNodeWithText("Tap a widget to resize or move it").assertDoesNotExist()
    }

    /** The chosen widget as drawn, in dp. */
    private fun chosenCard() = compose.onNode(isSelected()).getUnclippedBoundsInRoot()

    private fun panel() = WidgetOrder.inLocation(widgets.widgets.value, WidgetLocation.PANEL)

    /** A clock on the page, Kolahal allowed to bind it; and what the screen calls it. */
    private fun placeClock(index: Int = 0): Pair<PlacedWidget, String> {
        val clocks = t.clocks()
        assumeTrue("only ${clocks.size} clock widgets on this device", clocks.size > index)
        val clock = clocks[index]
        t.allowBinding(clock)
        return t.place(clock) to t.label(clock)
    }

    /** With the widgets page off, the screen says so, rather than "swipe sideways", and turns it on in a tap. */
    @Test fun withThePageOffItSaysSoAndTurnsItOn() {
        config.set(Keys.WIDGETS_PAGE, "off")
        show()
        compose.onNodeWithText("The widgets page is off").assertExists()
        compose.onNodeWithText("Turn on the widgets page").performClick()
        compose.waitForIdle()
        assertEquals("left", config[Keys.WIDGETS_PAGE])
        compose.onNodeWithText("Turn on the widgets page").assertDoesNotExist()
        compose.onNodeWithText("The widgets page is off").assertDoesNotExist()
    }

    /** No widgets yet: the screen says where they will show, and a tap opens the picker. */
    @Test fun anEmptyPageOffersToAddOne() {
        assumeTrue("widgets were placed before the test", widgets.widgets.value.isEmpty())
        val vm = show()
        compose.onNodeWithText("On the widgets page beside the terminal").assertExists()
        compose.onNodeWithText("No widgets yet. They appear on the widgets page beside the terminal: swipe sideways on the home screen to reach it.").assertExists()
        compose.onNodeWithContentDescription("Edit widgets").assertDoesNotExist()
        compose.onNodeWithText("Add a widget").performClick()
        compose.runOnIdle { assertEquals(Screen.WIDGET_PICKER, vm.screen.value) }
    }

    /** "Added Clock", said as the picker hands over to this screen, shows here once. */
    @Test fun aWaitingMessageShowsOnceTheScreenOpens() {
        widgets.message("Added Clock")
        show()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Added Clock").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(widgets.messages.value.isEmpty())
    }

    /** Each − or + makes the chosen widget 8dp shorter or taller, on the page as in the toolbar. */
    @Test fun heightStepsByEightWithMinusAndPlus() {
        val (w, label) = placeClock()
        show()
        compose.onNodeWithText("${panel().size} on the widgets page").assertExists()
        choose(label)
        val h = w.heightDp
        val drawn = chosenCard().height.value
        compose.onNodeWithText("full wide · ${h}dp tall").assertExists()
        compose.onNodeWithContentDescription("Increase height").performClick()
        compose.onNodeWithText("full wide · ${h + 8}dp tall").assertExists()
        assertEquals(h + 8, t.get(w.key)!!.heightDp)
        assertEquals(drawn + 8, chosenCard().height.value, 1f)
        compose.onNodeWithContentDescription("Decrease height").performClick()
        compose.onNodeWithContentDescription("Decrease height").performClick()
        compose.onNodeWithText("full wide · ${h - 8}dp tall").assertExists()
        assertEquals(h - 8, t.get(w.key)!!.heightDp)
        assertEquals(drawn - 8, chosenCard().height.value, 1f)
    }

    /** A full-width widget can't be wider; − takes off a twelfth of the page, drawn narrower. */
    @Test fun widthStepsByTwelfthsOfThePage() {
        val (w, label) = placeClock()
        show()
        choose(label)
        val page = chosenCard().width.value
        compose.onNodeWithContentDescription("Increase width").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Decrease width").performClick()
        compose.onNodeWithText("11/12 wide · ${w.heightDp}dp tall").assertExists()
        assertEquals(11, t.get(w.key)!!.span)
        val gap = WidgetLayout.GAP_DP.toFloat()
        assertEquals((page + gap) * 11 / 12 - gap, chosenCard().width.value, 1f)
        compose.onNodeWithContentDescription("Increase width").assertIsEnabled().performClick()
        compose.onNodeWithText("full wide · ${w.heightDp}dp tall").assertExists()
    }

    /** Square makes the widget as tall as the page is wide. */
    @Test fun squareMakesItAsTallAsItIsWide() {
        val (w, label) = placeClock()
        show()
        choose(label)
        compose.onNodeWithText("Square").performClick()
        compose.waitForIdle()
        val card = chosenCard()
        assertEquals(card.width.value, card.height.value, 1.5f)
        val h = t.get(w.key)!!.heightDp
        compose.onNodeWithText("full wide · ${h}dp tall").assertExists()
    }

    /** Move later swaps the chosen widget with the next, on the page too; the arrows stop at the ends. */
    @Test fun moveSwapsTwoWidgetsAndStopsAtTheEnds() {
        val (a, la) = placeClock(0)
        val (b, lb) = placeClock(1)
        show()
        fun mine() = panel().map { it.key }.filter { it == a.key || it == b.key }
        fun top(label: String) = compose.onNode(hasClickLabel("change $label")).getUnclippedBoundsInRoot().top
        choose(la)
        assertEquals(listOf(a.key, b.key), mine())
        if (panel().first().key == a.key) compose.onNodeWithContentDescription("Move earlier").assertIsNotEnabled()
        assertTrue(top(la) < top(lb))
        compose.onNodeWithContentDescription("Move later").performClick()
        compose.waitForIdle()
        assertEquals(listOf(b.key, a.key), mine())
        assertTrue("the moved widget is drawn after the other", top(la) > top(lb))
        if (panel().last().key == a.key) compose.onNodeWithContentDescription("Move later").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move earlier").assertIsEnabled()
    }

    /** Remove asks first; Cancel keeps the widget, Remove deletes it and frees its id. */
    @Test fun removeAsksFirst() {
        val (w, label) = placeClock()
        show()
        choose(label)
        compose.onNodeWithContentDescription("Remove").performClick()
        compose.onNodeWithText("Remove $label?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Remove $label?").assertDoesNotExist()
        assertNotNull(t.get(w.key))
        compose.onNodeWithContentDescription("Remove").performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.waitForIdle()
        assertNull(t.get(w.key))
        assertFalse(w.id in t.ids())
        compose.onNode(hasClickLabel("change $label")).assertDoesNotExist()
        compose.onNodeWithContentDescription("Remove").assertDoesNotExist()
    }

    /** Back puts the toolbar away and leaves editing before it leaves the screen. */
    @Test fun backLeavesEditingBeforeTheScreen() {
        val (_, label) = placeClock()
        val vm = show()
        choose(label)
        compose.onNodeWithContentDescription("Increase height").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("Increase height").assertDoesNotExist()
        compose.onNodeWithText("Tap a widget to resize or move it").assertDoesNotExist()
        compose.onNodeWithText("Add widget", useUnmergedTree = true).assertExists()
        assertEquals(Screen.WIDGETS, vm.screen.value)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(Screen.TERMINAL, vm.screen.value) }
    }

    /** A widget whose id Android no longer has says it's unavailable, and Edit still reaches its controls. */
    @Test fun anUnavailableWidgetSaysSoAndCanStillBeEdited() {
        val clock = t.clocks().first()
        // An id given out and never bound, as one whose app took it back.
        val id = widgets.allocate()
        widgets.place(id, clock.info)
        val fallback = clock.flat.substringAfterLast('.')
        show()
        compose.onNodeWithText("widget unavailable").assertExists()
        // Named by its provider's class, with no app to ask for a name.
        compose.onNodeWithText(fallback).assertExists()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("full wide · ${t.get("w$id")!!.heightDp}dp tall").assertExists()
        // On the widget and in the toolbar.
        compose.onAllNodesWithText(fallback).assertCountEquals(2)
    }

    /** A widget restored from a backup waits under "Restored from backup"; Add again hands it to the add flow. */
    @Test fun aRestoredWidgetIsAddedAgainInOneTap() {
        val clock = t.clocks().first()
        val pending = t.restore(clock.flat, clock.serial, height = 160)
        var readded: Pair<ProviderEntry, PlacedWidget>? = null
        show { e, w -> readded = e to w }
        compose.onNodeWithText("Restored from backup").performScrollTo()
        compose.onNodeWithText("panel · 160dp").assertExists()
        compose.onNodeWithText(t.label(clock)).assertExists()
        compose.onNodeWithText("Add again").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(clock.flat, readded?.first?.flat)
            assertEquals(pending.key, readded?.second?.key)
        }
    }

    /** One whose app isn't installed can't be added again, only forgotten. */
    @Test fun aRestoredWidgetWhoseAppIsGoneCanOnlyBeForgotten() {
        val serial = widgets.serialOf(android.os.Process.myUserHandle())
        val pending = t.restore("com.example.kolahal.gone/.ClockWidget", serial, height = 120)
        show()
        compose.onNodeWithText("app not installed").performScrollTo()
        compose.onNodeWithText("Add again").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Forget").performClick()
        compose.waitForIdle()
        assertNull(t.get(pending.key))
        compose.onNodeWithText("app not installed").assertDoesNotExist()
        compose.onNodeWithText("Restored from backup").assertDoesNotExist()
    }
}
