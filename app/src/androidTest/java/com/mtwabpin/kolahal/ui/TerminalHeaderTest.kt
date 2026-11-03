package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.status.Status
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What each stage of the header shows: drawn on their own, as home starts in one or the other by the keyboard. */
@RunWith(AndroidJUnit4::class)
class TerminalHeaderTest {
    @get:Rule val compose = createComposeRule()
    private val config = Config.get(InstrumentationRegistry.getInstrumentation().targetContext)
    private val date = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date())
    private val status = Status(batteryPct = 50)

    @After fun tearDown() = Keys.ALL.forEach(config::reset)

    private fun full() = compose.setContent {
        KolahalTheme {
            Header(
                unlocks = 0, profiles = emptyList(), status = status, statuses = emptyMap(), config = config, version = 0,
                onStatusTap = {}, onSwipeDown = {}, onTarget = {}, onProfileTap = {},
            )
        }
    }

    private fun oneLine() = compose.setContent {
        KolahalTheme { Box(Modifier.width(200.dp)) { CompactHeader(status, 0, config, 0, onExpand = {}) } }
    }

    /** By default, the clock (the gap before the date is for it), the date and the settings button. */
    @Test fun theFullHeaderHasTheClockDateAndSettings() {
        full()
        compose.onNodeWithContentDescription("Settings").assertExists()
        compose.onNodeWithText("  $date").assertExists()
    }

    /** Without the clock, the date has no gap before it. */
    @Test fun theClockAndSettingsButtonCanGo() {
        config.set(Keys.HEADER_SETTINGS, "off")
        config.set(Keys.HEADER_CLOCK, "off")
        full()
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
        compose.onNodeWithText(date).assertExists()
    }

    @Test fun aFullHeaderWithNothingChosenTakesNoRoom() {
        HEADER_ITEMS.forEach { config.set(it.full, "off") }
        full()
        compose.onNodeWithTag("header").assertDoesNotExist()
    }

    @Test fun aOneLineHeaderWithNothingChosenTakesNoRoom() {
        HEADER_ITEMS.forEach { config.set(it.oneLine, "off") }
        oneLine()
        compose.onNodeWithTag("header-compact").assertDoesNotExist()
    }

    /** The full header's setting doesn't reach it. */
    @Test fun theOneLineHeaderKeepsTheBatteryByItsOwnChoice() {
        config.set(Keys.HEADER_BATTERY, "off")
        oneLine()
        compose.onNodeWithText(" 50%").assertExists()
    }

    @Test fun theOneLineHeaderDropsTheBatteryByItsOwnChoice() {
        config.set(Keys.COMPACT_BATTERY, "off")
        oneLine()
        compose.onNodeWithTag("header-compact").assertExists()
        compose.onNodeWithText(" 50%").assertDoesNotExist()
    }

    /** With everything on and little room, it stays one line: what doesn't fit is left out. */
    @Test fun theOneLineHeaderStaysOneLine() {
        var version by mutableStateOf(0)
        compose.setContent {
            KolahalTheme {
                Box(Modifier.width(200.dp)) {
                    CompactHeader(status, 2, config, version, onExpand = {}, mediaPlaying = true, unlocks = 3, todoCount = 4)
                }
            }
        }
        compose.waitForIdle()
        val before = compose.onNodeWithTag("header-compact").getUnclippedBoundsInRoot().height
        compose.runOnIdle {
            HEADER_ITEMS.forEach { config.set(it.oneLine, "on") }
            version++
        }
        compose.waitForIdle()
        val after = compose.onNodeWithTag("header-compact").getUnclippedBoundsInRoot().height
        assertTrue("$before, then $after", after <= before + 1.dp)
    }
}
