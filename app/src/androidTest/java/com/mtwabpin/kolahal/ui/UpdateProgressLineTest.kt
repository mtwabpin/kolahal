package com.mtwabpin.kolahal.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.terminal.UpdateCommands
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The bar `update install` puts above the prompt while it downloads. */
@RunWith(AndroidJUnit4::class)
class UpdateProgressLineTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun itSaysHowMuchHasCome() {
        compose.setContent {
            KolahalTheme { UpdateProgressLine(UpdateCommands.Progress(4L * 1024 * 1024, 10L * 1024 * 1024)) }
        }
        compose.onNodeWithText("Downloading update").assertIsDisplayed()
        compose.onNodeWithText("4 MB / 10 MB  40%").assertIsDisplayed()
    }

    /** Nothing downloading: the log keeps the row. */
    @Test fun itsGoneWithoutADownload() {
        compose.setContent { KolahalTheme { UpdateProgressLine() } }
        compose.onNodeWithTag("update-progress").assertDoesNotExist()
    }
}
