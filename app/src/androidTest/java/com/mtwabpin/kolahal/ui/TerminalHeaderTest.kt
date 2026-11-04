package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.status.Status
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The header, one line: the time and date, the two items you chose and the battery, and what a tap does. */
@RunWith(AndroidJUnit4::class)
class TerminalHeaderTest {
    @get:Rule val compose = createComposeRule()
    private val config = Config.get(InstrumentationRegistry.getInstrumentation().targetContext)
    private val date = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date())
    private val taps = mutableListOf<HeaderTarget>()
    private var swipes = 0
    private var status by mutableStateOf(Status(batteryPct = 50))
    private var todos by mutableIntStateOf(2)
    private var version by mutableIntStateOf(0)

    @After fun tearDown() = Keys.ALL.forEach(config::reset)

    /** The header, drawn once; [status], [todos] and settings (with [version]) change it after. */
    private fun show(width: Dp = 400.dp) = compose.setContent {
        KolahalTheme {
            Box(Modifier.width(width)) {
                Header(
                    status = status, unlocks = 7, profiles = emptyList(), todoCount = todos, config = config, version = version,
                    onTarget = { taps += it }, onProfileTap = {}, onSwipeDown = { swipes++ },
                )
            }
        }
    }

    private fun set(vararg values: Pair<com.mtwabpin.kolahal.data.ConfigKey<*>, String>) = compose.runOnIdle {
        values.forEach { (key, value) -> config.set(key, value) }
        version++
    }

    /** By default: the time and date, the next alarm, the open todos and the battery, in one line. */
    @Test fun theDefaultsShowInOneLine() {
        status = Status(batteryPct = 50, nextAlarm = System.currentTimeMillis() + 3_600_000L)
        show()
        compose.onNodeWithText(date).assertExists()
        compose.onNodeWithTag("header-alarm").assertExists()
        compose.onNodeWithTag("header-todo").assertExists()
        compose.onNodeWithText("50%").assertExists()
        compose.onNodeWithTag("header").assertHeightIsEqualTo(32.dp)
        // All on the one line.
        val tops = listOf("header-time", "header-date", "header-alarm", "header-todo", "header-battery")
            .map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot().top }
        assertTrue(tops.toString(), tops.distinct().size == 1)
    }

    /** An alarm within a day is its time alone, without AM or PM; further off, with its day. */
    @Test fun theAlarmSaysItsDayOnlyWhenFar() {
        val time = SimpleDateFormat("h:mm", Locale.getDefault())
        val soon = System.currentTimeMillis() + 3_600_000L
        status = Status(batteryPct = 50, nextAlarm = soon)
        show()
        compose.onNodeWithText(time.format(Date(soon))).assertExists()
        val far = System.currentTimeMillis() + 3 * 86_400_000L
        compose.runOnIdle { status = Status(batteryPct = 50, nextAlarm = far) }
        compose.onNodeWithText(SimpleDateFormat("EEE ", Locale.getDefault()).format(Date(far)) + time.format(Date(far))).assertExists()
    }

    /** The two items are yours: none leaves the place empty; no todos, no item. */
    @Test fun theItemsAreTheOnesChosen() {
        config.set(Keys.HEADER_ITEM_1, "unlocks")
        config.set(Keys.HEADER_ITEM_2, "settings")
        show()
        compose.onNodeWithText("7").assertExists()
        compose.onNodeWithContentDescription("Settings").assertExists()
        compose.onNodeWithTag("header-todo").assertDoesNotExist()

        compose.runOnIdle { todos = 0 }
        set(Keys.HEADER_ITEM_1 to "todo", Keys.HEADER_ITEM_2 to "none")
        compose.onNodeWithTag("header-todo").assertDoesNotExist()
        compose.onNodeWithTag("header-settings").assertDoesNotExist()
        compose.onNodeWithTag("header-battery").assertExists()
    }

    /** Each part does what it says, and a swipe down pulls the shade. */
    @Test fun aTapGoesWhereItSays() {
        status = Status(batteryPct = 50, nextAlarm = System.currentTimeMillis() + 3_600_000L)
        show()
        compose.onNodeWithTag("header-time").performClick()
        compose.onNodeWithTag("header-alarm").performClick()
        compose.onNodeWithTag("header-todo").performClick()
        compose.onNodeWithTag("header-battery").performClick()
        compose.runOnIdle { assertEquals(listOf(HeaderTarget.CLOCK, HeaderTarget.ALARM, HeaderTarget.TODO, HeaderTarget.BATTERY), taps) }
        // From the line down onto the log, as a finger would.
        compose.onNodeWithTag("header").performTouchInput { swipeDown(startY = top + 1f, endY = bottom + 200.dp.toPx()) }
        compose.runOnIdle { assertEquals(1, swipes) }
    }

    /** Little room: what doesn't fit is left out, the second item first, the battery last, and it stays one line. */
    @Test fun itStaysOneLineWithLittleRoom() {
        config.set(Keys.HEADER_ITEM_1, "unlocks")
        show(width = 300.dp)
        compose.onNodeWithTag("header").assertHeightIsEqualTo(32.dp)
        compose.onNodeWithTag("header-battery").assertExists()
        // Each part is on the line or left out: none drawn past its end.
        val line = compose.onNodeWithTag("header").getUnclippedBoundsInRoot()
        for (tag in listOf("header-time", "header-date", "header-unlocks", "header-todo", "header-battery")) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().forEach { node ->
                val right = with(compose.density) { node.boundsInRoot.right.toDp() }
                assertTrue("$tag ends at $right, the line at ${line.right}", right <= line.right)
            }
        }
    }
}
