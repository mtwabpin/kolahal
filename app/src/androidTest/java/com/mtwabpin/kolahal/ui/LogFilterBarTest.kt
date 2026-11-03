package com.mtwabpin.kolahal.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.terminal.LogFilter
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The bar `log only …` puts above the prompt, and the ways it goes. */
@RunWith(AndroidJUnit4::class)
class LogFilterBarTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @After fun tearDown() {
        LogRepository.get(compose.activity).filter = null
    }

    private fun show(): TerminalViewModel {
        lateinit var vm: TerminalViewModel
        compose.runOnUiThread { vm = vms.terminal(compose.activity.application) }
        compose.setContent {
            KolahalTheme {
                Box(Modifier.size(360.dp, 700.dp)) {
                    TerminalScreen(vm, onPickFolder = {})
                }
            }
        }
        return vm
    }

    @Test fun theBarSaysWhatShowsAndItsCrossShowsEverything() {
        val vm = show()
        compose.onNodeWithTag("log-filter").assertDoesNotExist()
        compose.runOnIdle { vm.filterLog(LogFilter.OfKind(LogFilter.Kind.FEEDS)) }
        compose.onNodeWithText("only feeds", substring = true).assertExists()
        compose.onNodeWithContentDescription("Show every line").performClick()
        compose.onNodeWithTag("log-filter").assertDoesNotExist()
        assertNull(vm.filter.value)
    }

    @Test fun backShowsEverything() {
        val vm = show()
        compose.runOnIdle { vm.filterLog(LogFilter.Named("xkcd")) }
        compose.onNodeWithTag("log-filter").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("log-filter").assertDoesNotExist()
        assertNull(vm.filter.value)
    }
}
