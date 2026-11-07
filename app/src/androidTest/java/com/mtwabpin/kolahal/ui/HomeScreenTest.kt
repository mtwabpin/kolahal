package com.mtwabpin.kolahal.ui

import android.app.Application
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.shell
import com.mtwabpin.kolahal.terminal.LogRow
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.terminal.UiEvent
import com.mtwabpin.kolahal.terminal.lineTimeFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The home screen's layout rules, on a frame smaller than the phone. */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createComposeRule()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val config = Config.get(app)

    @get:Rule val configReset = ConfigReset()

    private fun show(width: Dp, height: Dp): TerminalViewModel {
        // On the main thread, as the activity makes it.
        val vm = vms.terminal(app)
        compose.setContent {
            // The frame is all the room there is: the keyboard (the emulator shows one for the
            // focused prompt) and the system bars are taken out of the window, not of the frame.
            KolahalTheme {
                Box(Modifier.size(width, height).testTag("frame").consumeWindowInsets(WindowInsets.safeDrawing)) {
                    TerminalScreen(vm, onPickFolder = {})
                }
            }
        }
        return vm
    }

    private fun type(vm: TerminalViewModel, text: String) {
        compose.runOnIdle { vm.onInputChange(TextFieldValue(text, TextRange(text.length))) }
        compose.waitForIdle()
    }

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single()
    }

    /**
     * The suggestion list is capped at 40% of the window, which can be more than the room left
     * above the key bar and prompt: the list must give way, not push them out (21 October 2026).
     */
    @Test fun longSuggestionListKeepsKeyBarAndPromptInside() {
        config.set(Keys.SUGGESTIONS, "30")
        config.set(Keys.KEYBAR_SHOW, "on")
        config.set(Keys.KEYBAR_AUTO, "off")
        val vm = show(360.dp, 320.dp)
        type(vm, "s")
        compose.onNodeWithTag("suggestions").assertExists()
        val frame = compose.onNodeWithTag("frame").getUnclippedBoundsInRoot()
        // Pushed out, or squeezed to nothing at the bottom: both lose the prompt.
        val prompt = compose.onNodeWithTag("prompt", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the prompt ends at ${prompt.bottom}, the frame at ${frame.bottom}", prompt.bottom <= frame.bottom)
        assertTrue("the prompt is ${prompt.bottom - prompt.top} tall", prompt.bottom - prompt.top >= 16.dp)
        val rows = compose.onAllNodesWithTag("keybar", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(rows.isNotEmpty())
        for (row in rows) {
            val bottom = with(compose.density) { row.boundsInRoot.bottom.toDp() }
            assertTrue("a key bar row ends at $bottom, the frame at ${frame.bottom}", bottom <= frame.bottom && row.size.height > 0)
        }
    }

    /** The suggestions follow each key: read where they show, not by all of home, they still change as you type. */
    @Test fun theSuggestionsFollowEachKey() {
        val vm = show(360.dp, 700.dp)
        fun suggested(text: String) = compose.onAllNodes(
            hasText(text, substring = true, ignoreCase = true) and androidx.compose.ui.test.hasAnyAncestor(hasTestTag("suggestions")),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()
        type(vm, "sett")
        compose.waitUntil(5_000) { suggested("settings") }
        type(vm, "tod")
        compose.waitUntil(5_000) { suggested("todo") }
        assertFalse("still suggests settings", suggested("settings"))
    }

    /** A script with a status line, pinned or not, for as long as [block] runs. */
    private fun withStatusLine(name: String, text: String, pinned: Boolean, block: () -> Unit) {
        val scripts = com.mtwabpin.kolahal.data.ScriptRepository.get(app)
        val s = scripts.create(name, com.mtwabpin.kolahal.data.ScriptLang.LUA, "status('x')")
        try {
            if (pinned) scripts.setAccess(s.id, com.mtwabpin.kolahal.script.ScriptAccess.HEADER, true)
            com.mtwabpin.kolahal.data.KvStore(app, com.mtwabpin.kolahal.script.HostApi.STATUS_SCOPE).set(name, text)
            block()
        } finally {
            scripts.delete(s.id)
            com.mtwabpin.kolahal.data.StatusLines.clear(app, name)
        }
    }

    /**
     * Home as Settings shows it, not typing whatever the emulator's keyboard does (up, it counts as
     * typing); text in the prompt still does.
     */
    private fun showResting(): TerminalViewModel {
        val vm = vms.terminal(app)
        compose.setContent {
            KolahalTheme {
                Box(Modifier.size(360.dp, 700.dp)) {
                    TerminalScreen(vm, onPickFolder = {}, preview = TerminalViewModel.Peek(null, typing = false))
                }
            }
        }
        return vm
    }

    /** The header stays one line while typing; the pinned header steps aside for the suggestions. */
    @Test fun whileTypingThePinnedHeaderStepsAside() = withStatusLine("pinned-home", "NZ 136/8", pinned = true) {
        config.set(Keys.HEADER_SHOW, "on")
        val vm = showResting()
        compose.onNodeWithTag("header").assertExists()
        compose.onNodeWithText("NZ 136/8").assertExists()
        type(vm, "s")
        compose.onNodeWithTag("header").assertExists()
        compose.onNodeWithTag("pinned-header").assertDoesNotExist()
        type(vm, "")
        compose.onNodeWithText("NZ 136/8").assertExists()
    }

    /** header.show off (the default): no header, and it comes back when turned on. */
    @Test fun headerCanBeTurnedOff() {
        assertEquals(false, Keys.HEADER_SHOW.default)
        val vm = show(360.dp, 700.dp)
        compose.onNodeWithTag("header").assertDoesNotExist()
        type(vm, "s")
        compose.onNodeWithTag("header").assertDoesNotExist()
        compose.runOnIdle { config.set(Keys.HEADER_SHOW, "on") }
        compose.onNodeWithTag("header").assertExists()
    }

    /** Only a script you pinned shows its line at the top, with the header on or off. */
    @Test fun onlyPinnedScriptsShowAtTheTop() = withStatusLine("pinned-home", "NZ 136/8", pinned = true) {
        withStatusLine("unpinned-home", "US AQI 131", pinned = false) {
            showResting()
            compose.onNodeWithText("NZ 136/8").assertExists()
            compose.onNodeWithText("US AQI 131").assertDoesNotExist()
            compose.runOnIdle { config.set(Keys.HEADER_SHOW, "off") }
            compose.onNodeWithText("NZ 136/8").assertExists()
        }
    }

    /** The settings item, when you choose it, opens Settings. */
    @Test fun theSettingsItemOpensSettings() {
        config.set(Keys.HEADER_SHOW, "on")
        config.set(Keys.HEADER_RIGHT, "settings,battery")
        val vm = show(360.dp, 700.dp)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntil(5_000) { vm.screen.value == com.mtwabpin.kolahal.terminal.Screen.SETTINGS }
    }

    /** log.taller-taps: a line you can tap is taller than plain output, unless it's turned off. */
    @Test fun tappableLinesAreTallerUnlessTurnedOff() {
        val logs = LogRepository.get(app)
        val command = logs.add(com.mtwabpin.kolahal.data.LogKind.COMMAND, "lh echo")
        val output = logs.add(com.mtwabpin.kolahal.data.LogKind.OUTPUT, "lh plain")
        try {
            Term.lineHeight = 25
            Term.tallerTaps = true
            show(360.dp, 700.dp)
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("line-$output").fetchSemanticsNodes().isNotEmpty() }
            fun height(id: Long) = compose.onNodeWithTag("line-$id").getUnclippedBoundsInRoot().let { it.bottom - it.top }
            // In sp: a larger system text size makes the lines taller with the text.
            val scale = app.resources.configuration.fontScale
            assertEquals(35f * scale, height(command).value, 1f)
            assertEquals(25f * scale, height(output).value, 1f)
            compose.runOnIdle { Term.tallerTaps = false }
            assertEquals(25f * scale, height(command).value, 1f)
            // Set shorter than a line of text with its padding: as short as the text allows, tappable or not.
            compose.runOnIdle { Term.lineHeight = 10 }
            assertTrue(height(output).value < 20f)
            assertEquals(height(output).value, height(command).value, 0.5f)
        } finally {
            Term.tallerTaps = Keys.LOG_TALLER_TAPS.default
            Term.lineHeight = Keys.LOG_LINE_HEIGHT.default
            logs.delete(command)
            logs.delete(output)
        }
    }

    /** Sets the phone's time zone; false if this device won't let a test do that. */
    private fun setZone(id: String): Boolean {
        shell("cmd alarm set-timezone $id")
        val until = SystemClock.uptimeMillis() + 5_000
        while (TimeZone.getDefault().id != id && SystemClock.uptimeMillis() < until) Thread.sleep(50)
        return TimeZone.getDefault().id == id
    }

    /** The phone changing time zone under a home that stays up: a line's time and day follow at once, as the header's do. */
    @Test fun theLogFollowsThePhonesTimeZone() {
        // It changes a real setting, so it never runs on a phone.
        assumeTrue("only on an emulator", Build.HARDWARE == "ranchu")
        val logs = LogRepository.get(app)
        val before = TimeZone.getDefault().id
        // More than a day apart: the hour and the date both differ.
        val east = "Pacific/Kiritimati"
        val west = "Pacific/Pago_Pago"
        // Only this line shows, and being days old it has a day divider.
        logs.hideScreen()
        val at = System.currentTimeMillis() - 3 * 86_400_000L
        logs.addAll(listOf(LogRepository.Pending(LogKind.COMMAND, "tz line", null, LogContext.EMPTY, at)))
        val line = logs.recent(limit = 10).single { it.text == "tz line" }.id
        try {
            assumeTrue("this device can't set its time zone from a test", setZone(east))
            val vm = show(360.dp, 700.dp)
            fun shows(zone: String) {
                val time = lineTimeFormat(config[Keys.CLOCK_24H]).apply { timeZone = TimeZone.getTimeZone(zone) }.format(Date(at))
                val day = Instant.ofEpochMilli(at).atZone(ZoneId.of(zone)).toLocalDate()
                compose.waitUntil(5_000) {
                    compose.onAllNodes(hasTestTag("line-$line") and hasText(time)).fetchSemanticsNodes().isNotEmpty() &&
                        vm.rows.value.any { it is LogRow.Day && it.day == day }
                }
            }
            shows(east)
            assertTrue(setZone(west))
            shows(west)
        } finally {
            shell("cmd alarm set-timezone $before")
            logs.unhide()
            logs.delete(line)
        }
    }

    /** Changed while another screen is up: the days are right on coming back, with no new line to bring that on. */
    @Test fun theDaysFollowAZoneThatChangedElsewhere() {
        assumeTrue("only on an emulator", Build.HARDWARE == "ranchu")
        val logs = LogRepository.get(app)
        val before = TimeZone.getDefault().id
        logs.hideScreen()
        val at = System.currentTimeMillis() - 3 * 86_400_000L
        logs.addAll(listOf(LogRepository.Pending(LogKind.COMMAND, "tz away", null, LogContext.EMPTY, at)))
        val line = logs.recent(limit = 10).single { it.text == "tz away" }.id
        try {
            assumeTrue("this device can't set its time zone from a test", setZone("Pacific/Kiritimati"))
            val vm = vms.terminal(app)
            val onHome = androidx.compose.runtime.mutableStateOf(true)
            compose.setContent { KolahalTheme { if (onHome.value) TerminalScreen(vm, onPickFolder = {}) } }
            fun shows(zone: String) {
                val day = Instant.ofEpochMilli(at).atZone(ZoneId.of(zone)).toLocalDate()
                compose.waitUntil(5_000) { vm.rows.value.any { it is LogRow.Day && it.day == day } }
            }
            shows("Pacific/Kiritimati")
            compose.runOnUiThread { onHome.value = false }
            compose.waitForIdle()
            assertTrue(setZone("Pacific/Pago_Pago"))
            compose.runOnUiThread { onHome.value = true }
            shows("Pacific/Pago_Pago")
        } finally {
            shell("cmd alarm set-timezone $before")
            logs.unhide()
            logs.delete(line)
        }
    }

    /** The header's ⚙ shows no word; a screen reader still has one to say. */
    @Test fun headerSettingsHasANameForScreenReaders() {
        config.set(Keys.HEADER_SHOW, "on")
        config.set(Keys.HEADER_LEFT, "time,settings")
        show(360.dp, 700.dp)
        compose.onNodeWithContentDescription("Settings").assertExists()
    }

    @Test fun dollarOpensQuickActions() {
        show(360.dp, 700.dp)
        compose.onNodeWithTag("prompt-quick").performClick()
        compose.onNodeWithText("Quick actions").assertIsDisplayed()
        compose.onNodeWithText("Unlocks this week").assertIsDisplayed()
    }

    /**
     * suggestions.wrap: a setting's description is cut to one line, or (on) shown whole over more
     * lines in its own column, its first line level with the setting's name.
     */
    @Test fun wrappedDescriptionsShowWhole() {
        val vm = show(360.dp, 700.dp)
        type(vm, "config set header.sh")
        val help = compose.onNodeWithText(Keys.HEADER_SHOW.help, substring = true, useUnmergedTree = true)
        assertEquals(1, help.layout().lineCount)

        compose.runOnIdle { config.set(Keys.SUGGESTIONS_WRAP, "on") }
        val wrapped = help.layout()
        assertTrue("${wrapped.lineCount} lines", wrapped.lineCount > 1)
        val name = compose.onNodeWithText(Keys.HEADER_SHOW.key, substring = true, useUnmergedTree = true)
        val nameBounds = name.getUnclippedBoundsInRoot()
        val helpBounds = help.getUnclippedBoundsInRoot()
        assertTrue("the description starts at ${helpBounds.left}, the name ends at ${nameBounds.right}", helpBounds.left >= nameBounds.right)
        with(compose.density) {
            val nameBaseline = nameBounds.top + name.layout().firstBaseline.toDp()
            val helpBaseline = helpBounds.top + wrapped.firstBaseline.toDp()
            assertEquals(nameBaseline.value, helpBaseline.value, 1f)
        }
    }

    /** keyboard.on-home: coming back to home (from an app, or unlocking) focuses the prompt, not only Home. */
    @Test fun keyboardOnHomeAlsoOnReturn() {
        val vm = show(360.dp, 700.dp)
        val asked = java.util.concurrent.atomic.AtomicInteger()
        val subscribed = java.util.concurrent.CountDownLatch(1)
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            vm.events.onSubscription { subscribed.countDown() }.collect { if (it == UiEvent.FocusPrompt) asked.incrementAndGet() }
        }
        try {
            subscribed.await()
            compose.runOnIdle { vm.onResume(); vm.onWindowFocused() }
            Thread.sleep(300)
            assertEquals("off: nothing asks for the keyboard", 0, asked.get())

            compose.runOnIdle { config.set(Keys.FOCUS_ON_HOME, "on"); vm.onResume(); vm.onWindowFocused() }
            compose.waitUntil(5_000) { asked.get() == 1 }
            // Once per return: focus coming back otherwise (the shade closing) leaves the keyboard be.
            compose.runOnIdle { vm.onWindowFocused() }
            Thread.sleep(300)
            assertEquals(1, asked.get())
            compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("prompt").assertIsFocused() }.isSuccess }
        } finally {
            job.cancel()
        }
    }

    /** `clear` (and Ctrl+L) offers Undo, which brings the lines back. */
    @Test fun aClearedScreenComesBackWithUndo() {
        val logs = LogRepository.get(app)
        val mark = "before the clear ${System.nanoTime()}"
        val id = logs.add(com.mtwabpin.kolahal.data.LogKind.OUTPUT, mark)
        val vm = show(360.dp, 700.dp)
        try {
            compose.waitUntil(5_000) { compose.onAllNodesWithText(mark).fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { vm.clearScreen() }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Screen cleared").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(5_000) { compose.onAllNodesWithText(mark).fetchSemanticsNodes().isEmpty() }
            // The empty log's hint: what to do on a line of its own, the other tips under it.
            compose.onNodeWithText("Type an app name or a command").assertExists()
            compose.onNodeWithText("help lists everything", substring = true).assertExists()
            compose.onNodeWithText("Undo").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText(mark).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Screen cleared").assertDoesNotExist()
        } finally {
            // Failed before the tap: the log stays cleared for every later test unless it's undone.
            compose.runOnIdle { vm.undo() }
            logs.delete(id)
        }
    }

    /** gesture.swipe-up is none by default, which turns swiping up off; an action turns it on. */
    @Test fun swipeUpRunsOnlyWhenGivenAnAction() {
        val logs = LogRepository.get(app)
        val mark = "swiped up ${System.nanoTime()}"
        show(360.dp, 700.dp)
        fun swipe() = compose.onNodeWithTag("prompt-quick").performTouchInput {
            swipeUp(startY = height - 1f, endY = height - 1f - 120.dp.toPx(), durationMillis = 200)
        }
        fun ran() = logs.recent(50).any { it.text == mark }

        // Nothing at all runs, not only the action set later.
        val before = logs.maxId()
        swipe()
        compose.waitForIdle()
        Thread.sleep(500)
        assertFalse(
            "swiping up ran something while it was none",
            logs.recent(20).any { it.id > before && (it.kind == com.mtwabpin.kolahal.data.LogKind.COMMAND || it.kind == com.mtwabpin.kolahal.data.LogKind.ERROR) },
        )

        compose.runOnIdle { config.set(Keys.GESTURE_SWIPE_UP, "echo $mark") }
        swipe()
        compose.waitUntil(5_000) { ran() }
    }

    /** Pinching the log sets the font size and keeps it (the toast saying so can't be seen from here). */
    @Test fun pinchingTheLogSetsAndSavesTheFontSize() {
        val before = Term.fontSize
        try {
            show(360.dp, 640.dp)
            compose.onNodeWithTag("log").performTouchInput {
                pinch(center - Offset(40f, 0f), center - Offset(160f, 0f), center + Offset(40f, 0f), center + Offset(160f, 0f), 400)
            }
            compose.waitForIdle()
            assertTrue(Term.fontSize > before)
            assertEquals(Term.fontSize, config[Keys.FONT_SIZE])
        } finally {
            Term.fontSize = before
        }
    }
}
