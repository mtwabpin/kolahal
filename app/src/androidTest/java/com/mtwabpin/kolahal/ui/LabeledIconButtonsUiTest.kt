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
import com.mtwabpin.kolahal.ui.editor.EditorIconButton
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.ui.widgets.PageEndAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** An icon button shows its label on a long-press, and a tap still does what it does. */
@RunWith(AndroidJUnit4::class)
class LabeledIconButtonsUiTest {
    @get:Rule
    val compose = createComposeRule()

    /** The widgets page's pencil: a long-press says what it does, and does nothing else; a tap opens Arrange. */
    @Test
    fun aLongPressShowsTheLabelAndATapActs() {
        var arranged by mutableIntStateOf(0)
        compose.setContent {
            KolahalTheme {
                PageEndAction(onArrange = { arranged++ })
            }
        }
        compose.onNodeWithText("Arrange widgets").assertDoesNotExist()
        compose.onNodeWithContentDescription("Arrange widgets").performTouchInput { longClick() }
        compose.waitUntil(3_000) { compose.onAllNodesWithTextCount("Arrange widgets") > 0 }
        assertEquals(0, arranged)
        compose.onNodeWithContentDescription("Arrange widgets").performClick()
        compose.runOnIdle { assertEquals(1, arranged) }
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
