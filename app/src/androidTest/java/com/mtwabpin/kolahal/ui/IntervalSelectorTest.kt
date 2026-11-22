package com.mtwabpin.kolahal.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A script's **Runs every** and a feed's **Refresh every**. */
@RunWith(AndroidJUnit4::class)
class IntervalSelectorTest {
    @get:Rule
    val compose = createComposeRule()
    private val choices = listOf(0 to "Off", 15 to "15 minutes", 60 to "1 hour", 180 to "3 hours")
    private var picked by mutableIntStateOf(0)
    private var picks = 0

    private fun show(selected: Int) {
        picked = selected
        compose.setContent {
            KolahalTheme {
                IntervalSelector("Runs every", picked, { picked = it; picks++ }, choices)
            }
        }
    }

    private fun open() = compose.onNodeWithText("Runs every").performClick()

    @Test
    fun itSaysWhatIsChosenAndOffersTheRestOnlyOnceOpened() {
        show(60)
        compose.onNodeWithText("1 hour").assertIsDisplayed()
        compose.onNodeWithText("3 hours").assertDoesNotExist()
        open()
        for (name in listOf("Off", "15 minutes", "3 hours")) compose.onNodeWithText(name)
            .assertIsDisplayed()
        compose.onNode(hasText("1 hour") and isSelected()).assertIsDisplayed()
    }

    @Test
    fun pickingOneSetsItAndCloses() {
        show(60)
        open()
        compose.onNodeWithText("3 hours").performClick()
        compose.runOnIdle { assertEquals(180, picked) }
        compose.onNodeWithText("3 hours").assertIsDisplayed()
        compose.onNodeWithText("Off").assertDoesNotExist()
    }

    /** Picking what's already chosen doesn't set the timer again, which would restart its wait. */
    @Test
    fun pickingTheChosenOneDoesNothing() {
        show(0)
        open()
        compose.onNode(hasText("Off") and isSelected()).performClick()
        compose.runOnIdle { assertEquals(0, picks) }
    }

    /** `script every weather 2h` isn't a preset: it still shows, and nothing in the list claims to be chosen. */
    @Test
    fun aTimeSetFromTheTerminalShowsAsItIs() {
        show(120)
        compose.onNodeWithText("2h").assertIsDisplayed()
        open()
        compose.onAllNodes(isSelected()).assertCountEquals(0)
    }
}
