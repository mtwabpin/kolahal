package com.mtwabpin.kolahal.ui.widgets

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.waitForText
import com.mtwabpin.kolahal.widgets.KolahalWidgetView
import com.mtwabpin.kolahal.widgets.TestWidgets
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The widgets page beside the log ([WidgetPanel]): a hold on any widget opens Arrange widgets with it
 * chosen, on the line that stands for a widget that can't show too, and a screen reader has it. A page
 * with widgets ends with the pencil; one without shows the empty page and its Add a widget.
 */
@RunWith(AndroidJUnit4::class)
class WidgetPanelTest {
    @get:Rule
    val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val t = TestWidgets()
    private val widgets = t.host

    @After
    fun tearDown() {
        t.close()
        widgets.releaseViews(compose.activity)
    }

    /** The page, with nothing chosen yet. */
    private fun show(location: String = com.mtwabpin.kolahal.widgets.WidgetLocation.PANEL): TerminalViewModel {
        widgets.chosen()
        lateinit var vm: TerminalViewModel
        compose.runOnUiThread { vm = vms.terminal(app) }
        compose.setContent { WidgetPanel(vm, location) }
        return vm
    }

    /**
     * The line that stands for a widget whose app took its id back takes a hold, as a card does:
     * Arrange widgets opens with it chosen. A screen reader hears the hold as "arrange widgets".
     */
    @Test
    fun aPlaceholderTakesTheHold() {
        val clock = t.clocks().first()
        // An id given out and never bound, as one whose app took it back.
        val id = widgets.allocate()
        widgets.place(id, clock.info)
        val vm = show()
        // Named once the profiles are read: its profile is listed, and not a private space.
        compose.waitForText("[${WidgetWords.UNAVAILABLE}] ${clock.packageName}")
        val line = compose.onNodeWithText("[${WidgetWords.UNAVAILABLE}] ${clock.packageName}")
        line.performScrollTo()
        line.assert(SemanticsMatcher("long-click label \"arrange widgets\"") {
            it.config.getOrNull(SemanticsActions.OnLongClick)?.label == "arrange widgets"
        })
        line.performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(Screen.WIDGETS, vm.screen.value)
            assertEquals("w$id", widgets.toChoose.value)
        }
    }

    /** An app's widget has the hold for a screen reader, said as "arrange widgets", and it opens Arrange widgets. */
    @Test
    fun anAppWidgetHasTheHoldForAScreenReader() {
        val clock = t.clocks().first()
        t.allowBinding(clock)
        val w = t.place(clock)
        val vm = show()
        compose.waitForIdle()
        val view = compose.runOnUiThread {
            fun find(v: View): KolahalWidgetView? = when {
                v is KolahalWidgetView && v.appWidgetId == w.id -> v
                v is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }
                else -> null
            }
            find(compose.activity.window.decorView)
        }
        checkNotNull(view) { "the clock's view isn't shown" }
        val action = compose.runOnUiThread {
            view.createAccessibilityNodeInfo().actionList
                .single { it.id == AccessibilityNodeInfo.ACTION_LONG_CLICK }
        }
        assertEquals("arrange widgets", action.label?.toString())
        compose.runOnUiThread {
            view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null)
        }
        compose.runOnIdle {
            assertEquals(Screen.WIDGETS, vm.screen.value)
            assertEquals(w.key, widgets.toChoose.value)
        }
    }

    /** A page with no widgets: the empty page's title and Add a widget, which opens the picker; no pencil. */
    @Test
    fun anEmptyPageOffersToAddOne() {
        assumeTrue("widgets were placed before the test", widgets.widgets.value.isEmpty())
        val vm = show()
        compose.onNodeWithText("No widgets yet").assertExists()
        // Page 1 always stays.
        compose.onNodeWithText(REMOVE_PAGE).assertDoesNotExist()
        compose.onNodeWithContentDescription(ARRANGE_WIDGETS).assertDoesNotExist()
        compose.onNodeWithText(ADD_WIDGET).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Screen.WIDGET_PICKER, vm.screen.value) }
    }

    /** A page with widgets ends with the pencil, which opens Arrange widgets; no Add a widget there. */
    @Test
    fun aPageWithWidgetsEndsWithThePencil() {
        val clock = t.clocks().first()
        t.allowBinding(clock)
        t.place(clock)
        val vm = show()
        compose.onNodeWithText("No widgets yet").assertDoesNotExist()
        compose.onNodeWithText(ADD_WIDGET).assertDoesNotExist()
        if (!PAGE_END_PENCIL) {
            // Hidden for now: the page ends with its last card.
            compose.onNodeWithContentDescription(ARRANGE_WIDGETS).assertDoesNotExist()
            return
        }
        compose.onNodeWithContentDescription(ARRANGE_WIDGETS).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Screen.WIDGETS, vm.screen.value) }
    }

    /**
     * A page added and left empty stays, with the empty page: "No widgets yet", Add a widget for that
     * page, and Remove page, which removes it at once.
     */
    @Test
    fun anAddedPageLeftEmptyStaysAndCanBeRemoved() {
        val location = widgets.addPage()
        assumeTrue("there are as many pages as there may be", location != null)
        val vm = show(location!!)
        compose.onNodeWithText("No widgets yet").assertExists()
        compose.onNodeWithText(ADD_WIDGET).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(Screen.WIDGET_PICKER, vm.screen.value)
            assertEquals(location, widgets.addTo)
        }
        compose.onNodeWithText(REMOVE_PAGE).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(-1, com.mtwabpin.kolahal.widgets.WidgetPages.indexOf(widgets.pages.value, location)) }
    }
}
