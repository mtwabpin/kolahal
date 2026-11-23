package com.mtwabpin.kolahal.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.status.Status
import com.mtwabpin.kolahal.terminal.HeaderTarget
import com.mtwabpin.kolahal.ui.theme.HomePage
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.ui.theme.LocalHomePage
import com.mtwabpin.kolahal.ui.theme.LocalTopCorners
import com.mtwabpin.kolahal.ui.theme.ScreenCorner
import com.mtwabpin.kolahal.ui.theme.Term
import com.mtwabpin.kolahal.ui.theme.TopCorners
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The header, one line: the parts you chose on each side (by default the time and date; the alarm, todos and battery), and what a tap does. */
@RunWith(AndroidJUnit4::class)
class TerminalHeaderTest {
    @get:Rule
    val compose = createComposeRule()
    private val config = Config.get(InstrumentationRegistry.getInstrumentation().targetContext)
    private val date = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date())
    private val taps = mutableListOf<HeaderTarget>()
    private var swipes = 0
    private var status by mutableStateOf(Status(batteryPct = 50))
    private var todos by mutableIntStateOf(2)
    private var version by mutableIntStateOf(0)

    /** The screen's corners the header sees: square, whatever the emulator's screen has, unless a test sets some. */
    private var corners by mutableStateOf<TopCorners?>(TopCorners.NONE)

    @get:Rule
    val configReset = ConfigReset()

    /** The header, drawn once; [status], [todos] and settings (with [version]) change it after. */
    private fun show(width: Dp = 400.dp) = compose.setContent {
        KolahalTheme {
            CompositionLocalProvider(LocalTopCorners provides corners) {
                Box(Modifier.width(width)) {
                    Header(
                        status = status,
                        unlocks = 7,
                        profiles = emptyList(),
                        todoCount = todos,
                        config = config,
                        version = version,
                        onTarget = { taps += it },
                        onProfileTap = {},
                        onSwipeDown = { swipes++ },
                    )
                }
            }
        }
    }

    private fun set(vararg values: Pair<com.mtwabpin.kolahal.data.ConfigKey<*>, String>) =
        compose.runOnIdle {
            values.forEach { (key, value) -> config.set(key, value) }
            version++
        }

    /** One line of the header's text (or its icon, if taller), without the padding. */
    private fun line() =
        with(compose.density) { maxOf(Term.small.lineHeight.toDp(), (Term.fontSize + 1).dp) }

    /** header.padding is the space above and below the text: the header is the line and twice it. */
    @Test
    fun thePaddingSetsTheHeight() {
        show()
        compose.onNodeWithTag("header").assertHeightIsEqualTo(line() + 12.dp)
        set(Keys.HEADER_PADDING to "2")
        compose.onNodeWithTag("header").assertHeightIsEqualTo(line() + 4.dp)
        set(Keys.HEADER_PADDING to "12")
        compose.onNodeWithTag("header").assertHeightIsEqualTo(line() + 24.dp)
        // The parts stay on the one line, as tall as the header.
        compose.onNodeWithTag("header-time").assertHeightIsEqualTo(line() + 24.dp)
    }

    /** By default: the time and date, the next alarm, the open todos and the battery, in one line. */
    @Test
    fun theDefaultsShowInOneLine() {
        status = Status(batteryPct = 50, nextAlarm = System.currentTimeMillis() + 3_600_000L)
        show()
        compose.onNodeWithText(date).assertExists()
        compose.onNodeWithTag("header-alarm").assertExists()
        compose.onNodeWithTag("header-todo").assertExists()
        compose.onNodeWithText("50%").assertExists()
        compose.onNodeWithTag("header").assertHeightIsEqualTo(line() + 12.dp)
        // All on the one line.
        val tops =
            listOf("header-time", "header-date", "header-alarm", "header-todo", "header-battery")
                .map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot().top }
        assertTrue(tops.toString(), tops.distinct().size == 1)
    }

    /** An alarm within a day is its time alone, without AM or PM; further off, with its day. */
    @Test
    fun theAlarmSaysItsDayOnlyWhenFar() {
        val time = SimpleDateFormat("h:mm", Locale.getDefault())
        val soon = System.currentTimeMillis() + 3_600_000L
        status = Status(batteryPct = 50, nextAlarm = soon)
        show()
        compose.onNodeWithText(time.format(Date(soon))).assertExists()
        val far = System.currentTimeMillis() + 3 * 86_400_000L
        compose.runOnIdle { status = Status(batteryPct = 50, nextAlarm = far) }
        compose.onNodeWithText(
            SimpleDateFormat(
                "EEE ",
                Locale.getDefault()
            ).format(Date(far)) + time.format(Date(far))
        ).assertExists()
    }

    /** The parts are yours, on either side: one left out isn't shown; no todos, no todo part. */
    @Test
    fun thePartsAreTheOnesChosen() {
        config.set(Keys.HEADER_RIGHT, "unlocks,settings,battery")
        show()
        compose.onNodeWithText("7").assertExists()
        compose.onNodeWithContentDescription("Settings").assertExists()
        compose.onNodeWithTag("header-todo").assertDoesNotExist()

        compose.runOnIdle { todos = 0 }
        set(Keys.HEADER_RIGHT to "todo,battery")
        compose.onNodeWithTag("header-todo").assertDoesNotExist()
        compose.onNodeWithTag("header-settings").assertDoesNotExist()
        compose.onNodeWithTag("header-battery").assertExists()

        // The battery on the left and the time on the right; the date left out.
        set(Keys.HEADER_LEFT to "battery", Keys.HEADER_RIGHT to "time")
        compose.onNodeWithTag("header-date").assertDoesNotExist()
        val battery = compose.onNodeWithTag("header-battery").getUnclippedBoundsInRoot()
        val time = compose.onNodeWithTag("header-time").getUnclippedBoundsInRoot()
        assertTrue(
            "the battery at ${battery.left}, the time at ${time.left}",
            battery.right < time.left
        )

        // An empty side is fine.
        set(Keys.HEADER_LEFT to "none")
        compose.onNodeWithTag("header-battery").assertDoesNotExist()
        compose.onNodeWithTag("header-time").assertExists()
    }

    /** Each part does what it says, and a swipe down pulls the shade. */
    @Test
    fun aTapGoesWhereItSays() {
        status = Status(batteryPct = 50, nextAlarm = System.currentTimeMillis() + 3_600_000L)
        show()
        compose.onNodeWithTag("header-time").performClick()
        compose.onNodeWithTag("header-alarm").performClick()
        compose.onNodeWithTag("header-todo").performClick()
        compose.onNodeWithTag("header-battery").performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(
                    HeaderTarget.CLOCK,
                    HeaderTarget.ALARM,
                    HeaderTarget.TODO,
                    HeaderTarget.BATTERY
                ), taps
            )
        }
        // From the line down onto the log, as a finger would.
        compose.onNodeWithTag("header")
            .performTouchInput { swipeDown(startY = top + 1f, endY = bottom + 200.dp.toPx()) }
        compose.runOnIdle { assertEquals(1, swipes) }
    }

    /** Little room: what doesn't fit is left out from the middle out, the battery last, and it stays one line. */
    @Test
    fun itStaysOneLineWithLittleRoom() {
        config.set(Keys.HEADER_RIGHT, "unlocks,todo,battery")
        show(width = 300.dp)
        compose.onNodeWithTag("header").assertHeightIsEqualTo(line() + 12.dp)
        compose.onNodeWithTag("header-battery").assertExists()
        // Each part is on the line or left out: none drawn past its end.
        val line = compose.onNodeWithTag("header").getUnclippedBoundsInRoot()
        for (tag in listOf(
            "header-time",
            "header-date",
            "header-unlocks",
            "header-todo",
            "header-battery"
        )) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().forEach { node ->
                val right = with(compose.density) { node.boundsInRoot.right.toDp() }
                assertTrue("$tag ends at $right, the line at ${line.right}", right <= line.right)
            }
        }
    }

    /**
     * Rounded screen corners: the line's ends start past what they hide over its text, so the time
     * moves in. The preview in Settings is not at the screen's corner, so it stays where it was.
     */
    @Test
    fun theCornersMoveTheEndsInButNotThePreview() {
        val lineWidth = 400.dp
        compose.setContent {
            KolahalTheme {
                CompositionLocalProvider(LocalTopCorners provides corners) {
                    Column(Modifier.width(lineWidth)) {
                        Header(
                            status = status,
                            unlocks = 7,
                            profiles = emptyList(),
                            todoCount = todos,
                            config = config,
                            version = version,
                            onTarget = {},
                            onProfileTap = {},
                            onSwipeDown = {},
                        )
                        Header(
                            status = status,
                            unlocks = 7,
                            profiles = emptyList(),
                            todoCount = todos,
                            config = config,
                            version = version,
                            onTarget = {},
                            onProfileTap = {},
                            onSwipeDown = {},
                            preview = true,
                        )
                    }
                }
            }
        }
        // The line's time first, the preview's second.
        fun timeLeft(i: Int) = compose.onAllNodesWithTag("header-time")[i].getUnclippedBoundsInRoot().left
        val line = timeLeft(0)
        val preview = timeLeft(1)
        // Corners of 400 px centered 400 px in from each side and from the top: they cut well into
        // both lines, wherever the test's window puts them under the status bar.
        val widthPx = with(compose.density) { lineWidth.roundToPx() }
        compose.runOnIdle {
            corners = TopCorners(ScreenCorner(400, 400, 400), ScreenCorner(400, widthPx - 400, 400))
        }
        val moved = timeLeft(0)
        assertTrue("the time at $moved, at $line without corners", moved >= line + 4.dp)
        assertEquals(preview, timeLeft(1))
    }

    /**
     * While home turns to or from the widgets page, the log's page slides. The corners belong to the
     * screen, so the line's parts keep where they stand on the page at rest and move with it
     * ([LocalHomePage]): none of them is dropped on the way.
     */
    @Test
    fun thePartsMoveWithTheirPage() {
        val lineWidth = 400.dp
        val widthPx = with(compose.density) { lineWidth.roundToPx() }
        corners = TopCorners(ScreenCorner(400, 400, 400), ScreenCorner(400, widthPx - 400, 400))
        val page = HomePage()
        var shift by mutableIntStateOf(0)
        compose.setContent {
            KolahalTheme {
                CompositionLocalProvider(LocalTopCorners provides corners, LocalHomePage provides page) {
                    Box(
                        Modifier
                            .width(lineWidth)
                            .offset { IntOffset(shift, 0) }
                            .onPlaced { page.coordinates = it },
                    ) {
                        Header(
                            status = status,
                            unlocks = 7,
                            profiles = emptyList(),
                            todoCount = todos,
                            config = config,
                            version = version,
                            onTarget = {},
                            onProfileTap = {},
                            onSwipeDown = {},
                        )
                    }
                }
            }
        }
        fun left(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().left
        val time = left("header-time")
        val battery = left("header-battery")
        // Halfway through a turn either way: the parts move with the page, by as much as it does.
        for (s in listOf(widthPx / 2, -widthPx / 2)) {
            compose.runOnIdle { shift = s }
            compose.waitForIdle()
            val by = with(compose.density) { s.toDp() }
            assertEquals((time + by).value, left("header-time").value, 1f)
            assertEquals((battery + by).value, left("header-battery").value, 1f)
        }
    }
}
