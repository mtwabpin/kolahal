package com.mtwabpin.kolahal.ui.widgets

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.productivity.Todo
import com.mtwabpin.kolahal.terminal.AgendaRead
import com.mtwabpin.kolahal.terminal.OwnFeed
import com.mtwabpin.kolahal.terminal.OwnTaps
import com.mtwabpin.kolahal.terminal.WidgetCardActions
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.waitForNode
import com.mtwabpin.kolahal.widgets.Shortcut
import com.mtwabpin.kolahal.widgets.WidgetSource
import com.mtwabpin.kolahal.widgets.WidgetTap
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A tap that misses on the Todos and Shortcuts cards: it stays on the widgets page. Only "+3 more"
 * opens the todo list; the space between shortcut buttons does nothing.
 */
@RunWith(AndroidJUnit4::class)
class OwnCardTapsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** What the cards hand their actions, in order: `todos open`, `arrange`. */
    private class Asked : WidgetCardActions {
        val all = CopyOnWriteArrayList<String>()
        override fun refresh(widget: String) {}
        override fun tap(widget: String, tap: WidgetTap) {}
        override fun openBattery() {}
        override fun openScripts() {}
        override fun longPress() {
            all += "arrange"
        }

        override fun own(id: String, what: String) {
            all += "$id $what"
        }
    }

    private val todos = MutableStateFlow<List<Todo>?>(null)
    private val shortcuts = MutableStateFlow<List<Shortcut>?>(null)

    private val feed = OwnFeed(
        todos = todos,
        notes = MutableStateFlow(null),
        reminders = MutableStateFlow(null),
        shortcuts = shortcuts,
        playing = MutableStateFlow(null),
        agenda = MutableStateFlow(AgendaRead.NOT_READ),
    )

    private fun show(id: String, asked: Asked) = compose.setContent {
        KolahalTheme {
            GuiTheme {
                CompositionLocalProvider(LocalWidgetsShown provides true) {
                    Box(Modifier.width(395.dp)) { OwnWidget(id, 192.dp, asked, feed) }
                }
            }
        }
    }

    private fun card(id: String) = hasTestTag("widget:${WidgetSource.builtin(id)}")
    private fun inCard(id: String) = card(id) or hasAnyAncestor(card(id))

    private fun hasClickLabel(label: String) =
        SemanticsMatcher("click label \"$label\"") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    private val longPressOnly =
        SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick) and SemanticsMatcher.keyNotDefined(
            SemanticsActions.OnClick
        )

    /**
     * The Todos card has no tap of its own: a tap between rows or on its edge does nothing. "+5 more"
     * opens the list, and the long-press still arranges the widgets.
     */
    @Test
    fun onlyMoreOpensTheTodoList() {
        val asked = Asked()
        todos.value = (1L..8L).map { Todo(it, "Todo $it", done = false) }
        show(WidgetSource.TODOS, asked)
        compose.waitForNode(inCard(WidgetSource.TODOS) and hasText("Todo 1"))
        compose.onAllNodes(inCard(WidgetSource.TODOS) and hasClickLabel("open todos"))
            .assertCountEquals(0)

        compose.onAllNodes(inCard(WidgetSource.TODOS) and longPressOnly).onFirst()
            .performSemanticsAction(SemanticsActions.OnLongClick)
        compose.runOnIdle { assertEquals(listOf("arrange"), asked.all.toList()) }

        compose.onNode(
            hasTestTag("list-open") and hasText("more", substring = true),
            useUnmergedTree = true
        ).performClick()
        compose.runOnIdle {
            assertEquals(
                listOf("arrange", "${WidgetSource.TODOS} ${OwnTaps.OPEN}"),
                asked.all.toList()
            )
        }
    }

    /** With buttons, the Shortcuts card takes no tap of its own; empty, a tap on it opens the editor. */
    @Test
    fun theSpaceBetweenShortcutsDoesNothing() {
        val asked = Asked()
        shortcuts.value =
            listOf(Shortcut("torch", "torch", "torch"), Shortcut("dnd", "dnd", "moon"))
        show(WidgetSource.SHORTCUTS, asked)
        compose.waitForNode(
            inCard(WidgetSource.SHORTCUTS) and (hasText("torch") or hasContentDescription(
                "torch"
            ))
        )
        compose.onAllNodes(inCard(WidgetSource.SHORTCUTS) and hasClickLabel("edit shortcuts"))
            .assertCountEquals(0)
        compose.onAllNodes(inCard(WidgetSource.SHORTCUTS) and longPressOnly).onFirst()
            .performSemanticsAction(SemanticsActions.OnLongClick)
        compose.runOnIdle { assertEquals(listOf("arrange"), asked.all.toList()) }

        compose.runOnIdle { shortcuts.value = emptyList() }
        compose.waitForNode(inCard(WidgetSource.SHORTCUTS) and hasClickLabel("edit shortcuts"))
        compose.onNode(inCard(WidgetSource.SHORTCUTS) and hasClickLabel("edit shortcuts"))
            .performClick()
        compose.runOnIdle {
            assertTrue(
                asked.all.toString(),
                "${WidgetSource.SHORTCUTS} ${OwnTaps.EDIT}" in asked.all
            )
        }
    }
}
