package com.mtwabpin.kolahal.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** An icon button shows its label on a long-press, and a tap still does what it does. */
@RunWith(AndroidJUnit4::class)
class LabeledIconButtonsUiTest {
    @get:Rule
    val compose = createComposeRule()

    /** The widgets page's + and pencil: a long-press says what each does, and does nothing else. */
    @Test
    fun aLongPressShowsTheLabelAndATapActs() {
        var added by mutableIntStateOf(0)
        var edited by mutableIntStateOf(0)
        compose.setContent {
            KolahalTheme {
                WidgetsTitleRow(
                    onAdd = { added++ },
                    onEdit = { edited++ })
            }
        }
        compose.onNodeWithText("Add a widget").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add a widget").performTouchInput { longClick() }
        compose.waitUntil(3_000) { compose.onAllNodesWithTextCount("Add a widget") > 0 }
        assertEquals(0, added)
        compose.onNodeWithContentDescription("Edit widgets").performClick()
        compose.runOnIdle { assertEquals(listOf(0, 1), listOf(added, edited)) }
    }

    /** The editor's Run keeps its own long-press (Run with…): no label covers it. Save shows its label. */
    @Test
    fun aButtonWithALongPressOfItsOwnKeepsIt() {
        var runWith by mutableIntStateOf(0)
        compose.setContent {
            KolahalTheme {
                androidx.compose.foundation.layout.Row {
                    EditorIconButton(
                        R.drawable.ic_play,
                        "Run",
                        onLongClick = { runWith++ },
                        longClickLabel = "Run with…"
                    ) {}
                    EditorIconButton(R.drawable.ic_save, "Save") {}
                }
            }
        }
        compose.onNodeWithContentDescription("Run").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, runWith) }
        compose.onNodeWithText("Run").assertDoesNotExist()
        compose.onNodeWithContentDescription("Save").performTouchInput { longClick() }
        compose.waitUntil(3_000) { compose.onAllNodesWithTextCount("Save") > 0 }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
