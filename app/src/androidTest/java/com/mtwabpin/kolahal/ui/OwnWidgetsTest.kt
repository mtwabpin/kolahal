package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.TestViewModels
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.productivity.TodoStore
import com.mtwabpin.kolahal.waitForNode
import com.mtwabpin.kolahal.waitUntil
import com.mtwabpin.kolahal.widgets.WidgetHost
import com.mtwabpin.kolahal.widgets.WidgetSize
import com.mtwabpin.kolahal.widgets.WidgetSource
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kolahal's own widgets, placed as the picker places them and drawn as the widgets page draws
 * them, with the real view model behind them. No script runs for these cards.
 */
@RunWith(AndroidJUnit4::class)
class OwnWidgetsTest {
    @get:Rule(order = 0) val vms = TestViewModels()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val host = WidgetHost.get(context)
    private val todos = TodoStore.get(context)

    /** The widgets that were on the page before the test. Any other is the test's, and goes after it. */
    private val hadKeys = host.widgets.value.map { it.key }.toSet()

    /** The todos that were open before the test. They are set aside while it runs, so the card has room for the test's own. */
    private val wereOpen = todos.open().map { it.id }
    private val mine = mutableListOf<Long>()

    @Before fun setUp() = wereOpen.forEach { todos.setDone(it, true) }

    @After fun tearDown() {
        mine.forEach { todos.remove(it) }
        wereOpen.forEach { todos.setDone(it, false) }
        host.widgets.value.filter { it.key !in hadKeys }.forEach { host.remove(it.key) }
        host.settle()
        // The app writes the placed list a moment later, and a test run's process ends before that.
        Stores.WIDGETS.open(context).edit().commit()
        while (host.takeMessage() != null) Unit
    }

    private fun todo(text: String): Long = todos.add(text).also { mine += it }

    private fun isDone(id: Long): Boolean = todos.all().first { it.id == id }.done

    private fun isCheckbox(ticked: Boolean) =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox) and
            SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, if (ticked) ToggleableState.On else ToggleableState.Off)

    /**
     * The Todos card lists the open todos, each with a checkbox, and follows the list. A tap on a
     * row marks its todo done. The row stays, ticked, and a second tap opens the todo again.
     */
    @Test fun aTapOnATodosRowMarksItDone() {
        val vm = vms.terminal(context.applicationContext as Application)
        assertTrue("Todos was on the widgets page before the test", host.placeBuiltin("todos", WidgetSize.S4X2))
        val source = WidgetSource.BUILTIN + "todos"
        val placed = host.widgets.value.single { it.source == source }
        // A text no other todo has.
        val stamp = System.nanoTime()
        val milk = "Buy milk $stamp"
        val milkId = todo(milk)
        compose.setContent {
            KolahalTheme {
                CompositionLocalProvider(LocalWidgetsShown provides true) {
                    Box(Modifier.width(360.dp)) { SourceWidget(placed, 320.dp, vm.widgetActions, vm.widgetFeed) }
                }
            }
        }
        val inCard = hasAnyAncestor(hasTestTag("widget:$source"))
        val row = inCard and hasText(milk)
        compose.waitForNode(row and isCheckbox(ticked = false))

        // A todo added while the card shows: the card follows the list, with no run and no tap.
        val mum = "Call mum $stamp"
        val mumId = todo(mum)
        compose.waitForNode(inCard and hasText(mum) and isCheckbox(ticked = false))

        compose.onNode(row and hasClickAction()).performClick()
        waitUntil("the tap on the row didn't mark the todo done") { isDone(milkId) }
        // The row stays where it is, ticked: a second tap can undo a slip of the finger.
        compose.waitForNode(row and isCheckbox(ticked = true))
        assertFalse(isDone(mumId))

        compose.onNode(row and hasClickAction()).performClick()
        waitUntil("the second tap didn't open the todo again") { !isDone(milkId) }
        compose.waitForNode(row and isCheckbox(ticked = false))
    }
}
