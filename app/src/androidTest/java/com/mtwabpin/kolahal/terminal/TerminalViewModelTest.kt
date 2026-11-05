package com.mtwabpin.kolahal.terminal

import android.app.Application
import android.content.Context
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.apps.Profile
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.ui.HeaderTarget
import com.mtwabpin.kolahal.ui.KeyBarKey
import com.mtwabpin.kolahal.waitUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** Home's view model: permission answers, the quick menu and header, gestures, the prompt's keys and suggestions, log line taps and swipes, script runs. */
@RunWith(AndroidJUnit4::class)
class TerminalViewModelTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val logLines = LogLinesSince()
    @get:Rule val configReset = ConfigReset()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val logs = LogRepository.get(app)
    private val config = Config.get(app)
    private val scripts = ScriptRepository.get(app)
    private val token = "tvm${System.nanoTime() % 1_000_000}"
    private var before = 0L

    /** Where home notes that its first run's tour was shown. */
    private val launcher = app.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val toured = launcher.contains("intro_shown")

    /** Collects a view model's events; cancelled after the test. */
    private val collectors = CoroutineScope(Dispatchers.Default + Job())

    @Before fun setUp() {
        // Not a first run: the tour and its welcome lines would land among the lines checked.
        launcher.edit().putBoolean("intro_shown", true).commit()
        // Typing at the prompt types; nothing launches on its own.
        config.set(Keys.LAUNCH_INSTANT, InstantRun.OFF)
        config.set(Keys.LOG_TIPS, "off")
        before = logs.maxId()
    }

    @After fun tearDown() {
        try {
            collectors.cancel()
        } finally {
            if (!toured) launcher.edit().remove("intro_shown").commit()
        }
    }

    private fun home() = vms.terminal(app)

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    /** [check] holds for about a second. */
    private fun staysTrue(what: String, check: () -> Boolean) {
        val until = System.currentTimeMillis() + 1_000
        while (System.currentTimeMillis() < until) {
            assertTrue(what, check())
            Thread.sleep(50)
        }
    }

    /** Lines logged since the test began. */
    private fun added(): List<LogEntry> = logs.recent(400).filter { it.id > before }

    /** Waits for the one line added with [text] and returns it. */
    private fun line(text: String): LogEntry {
        waitUntil("a line '$text'") { added().any { it.text == text } }
        return added().single { it.text == text }
    }

    private fun events(vm: TerminalViewModel): List<UiEvent> {
        val seen = CopyOnWriteArrayList<UiEvent>()
        collectors.launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { seen += it } }
        return seen
    }

    /** Puts [text] in the prompt, the cursor at its end. */
    private fun TerminalViewModel.typed(text: String) = main { onInputChange(TextFieldValue(text, TextRange(text.length))) }

    /** Turning down contacts, calendar, call log or notifications says so, each with a tap to where it's allowed. */
    @Test fun deniedPermissionsSayWhereToAllowThem() {
        val vm = home()
        main {
            vm.onContactsPermission(false)
            vm.onCalendarPermission(false)
            vm.onCallLogPermission(false)
            vm.onNotificationPermission(false)
        }
        assertEquals(LogKind.ERROR, line("Contacts permission denied").kind)
        val calendar = line("Calendar permission denied — tap to open app settings")
        assertEquals(LogKind.ERROR, calendar.kind)
        assertEquals("agenda --settings", calendar.ctx.values[LogContext.RUN])
        val calls = line("Call log permission denied — tap to open app settings")
        assertEquals(LogKind.ERROR, calls.kind)
        assertEquals("calls --settings", calls.ctx.values[LogContext.RUN])
        val notifications = line("Notifications denied — tap to open notification settings")
        assertEquals(LogKind.ERROR, notifications.kind)
        assertEquals("notifications settings", notifications.ctx.values[LogContext.RUN])
    }

    /** Allowing the calendar or notifications says so; the calendar's line opens the agenda. */
    @Test fun allowedPermissionsAreConfirmed() {
        val vm = home()
        main {
            vm.onCalendarPermission(true)
            vm.onNotificationPermission(true)
        }
        val calendar = line("Calendar allowed — tap to see your agenda")
        assertEquals(LogKind.INFO, calendar.kind)
        assertEquals("agenda", calendar.ctx.values[LogContext.RUN])
        assertEquals(LogKind.INFO, line("Notifications allowed").kind)
    }

    /** A phone with no contact picker gets an error line instead of nothing. */
    @Test fun aMissingContactPickerIsReported() {
        val vm = home()
        main { vm.onContactPickerMissing() }
        assertEquals(LogKind.ERROR, line("This phone has no contact picker").kind)
    }

    /** A font that can't be loaded is reported once per run, with a tap to import another. */
    @Test fun anUnloadableFontIsReportedOnce() {
        val vm = home()
        val text = "Font '$token' could not be loaded — using the system monospace · tap to import one"
        main {
            vm.onFontUnavailable(token)
            vm.onFontUnavailable(token)
        }
        val reported = line(text)
        assertEquals(LogKind.ERROR, reported.kind)
        assertEquals("font import", reported.ctx.values[LogContext.RUN])
        staysTrue("reported twice") { added().count { it.text == text } == 1 }
    }

    /** Not now on the notification-access question closes it and leaves a line to ask again. */
    @Test fun notNowForNotificationAccessLeavesALineToAskAgain() {
        val vm = home()
        main { vm.askNotificationAccess() }
        assertTrue(vm.notificationAccessAsk.value)
        main { vm.answerNotificationAccess(false) }
        assertFalse(vm.notificationAccessAsk.value)
        assertEquals("notifs enable", line("Notification access is off · tap to turn it on").ctx.values[LogContext.RUN])
    }

    /** The quick menu lists the header's places, and its entries open their pages or run their commands. */
    @Test fun quickActionsOpenTheirPlaces() {
        val vm = home()
        val actions = vm.quickActions()
        val labels = actions.map { it.label }
        assertEquals(
            listOf("Settings", "Notifications", "Alarms & clock", "Battery", "Network", "Unlocks this week", "Apps", "Widgets", "Feeds", "Scripts"),
            labels.filter { !it.endsWith(" profile") && it != "Lock screen" },
        )
        fun tap(label: String) = main { actions.single { it.label == label }.run() }
        tap("Settings")
        assertEquals(Screen.SETTINGS, vm.screen.value)
        tap("Widgets")
        assertEquals(Screen.WIDGETS, vm.screen.value)
        main { vm.openScreen(Screen.TERMINAL) }
        tap("Unlocks this week")
        tap("Apps")
        waitUntil("both commands run") {
            added().filter { it.kind == LogKind.COMMAND }.map { it.text }.containsAll(listOf("unlocks 7", "apps"))
        }
    }

    /** Header taps: the todo count runs `todo`, the gear opens Settings. */
    @Test fun headerTapsRunTodoAndOpenSettings() {
        val vm = home()
        main { vm.onHeaderTap(HeaderTarget.TODO) }
        waitUntil("todo run") { added().any { it.kind == LogKind.COMMAND && it.text == "todo" } }
        main { vm.onHeaderTap(HeaderTarget.SETTINGS) }
        assertEquals(Screen.SETTINGS, vm.screen.value)
    }

    /** Tapping a running work profile asks for a second tap, on a line that pauses it. */
    @Test fun pausingAProfileAsksForASecondTap() {
        val vm = home()
        val work = Profile(android.os.Process.myUserHandle(), 0L, ProfileKind.WORK, quiet = false)
        main { vm.onProfileTap(work) }
        val ask = line("Tap to pause the work profile")
        assertEquals(LogKind.INFO, ask.kind)
        assertEquals("work off", ask.ctx.values[LogContext.RUN])
    }

    /** Tapping a paused work profile resumes it at once: `work on` runs. */
    @Test fun resumingAProfileTakesOneTap() {
        val vm = home()
        val work = Profile(android.os.Process.myUserHandle(), 0L, ProfileKind.WORK, quiet = true)
        main { vm.onProfileTap(work) }
        waitUntil("work on run") { added().any { it.kind == LogKind.COMMAND && it.text == "work on" } }
    }

    /** A gesture set to `quick` opens the quick menu, as `$` does; one set to `none` does nothing; one set to a command runs it. */
    @Test fun gesturesOpenTheQuickMenuOrRunTheirCommand() {
        val vm = home()
        val seen = events(vm)
        main { vm.openQuick() }
        waitUntil("the quick menu from \$") { seen.count { it == UiEvent.ShowQuick } == 1 }
        config.set(Keys.GESTURE_SWIPE_UP, "quick")
        main { vm.onGesture(Keys.GESTURE_SWIPE_UP) }
        waitUntil("the quick menu from the gesture") { seen.count { it == UiEvent.ShowQuick } == 2 }
        config.set(Keys.GESTURE_SWIPE_UP, "none")
        main { vm.onGesture(Keys.GESTURE_SWIPE_UP) }
        config.set(Keys.GESTURE_SWIPE_UP, "echo $token")
        main { vm.onGesture(Keys.GESTURE_SWIPE_UP) }
        assertEquals(LogKind.OUTPUT, line(token).kind)
        // Commands run in order: had `none` run anything, its line would be here before the echo's.
        assertEquals(listOf("echo $token"), added().filter { it.kind == LogKind.COMMAND }.map { it.text })
        assertEquals(2, seen.count { it == UiEvent.ShowQuick })
    }

    /** In `launch.instant all`, typing a command's name runs it once no other command or app starts that way. */
    @Test fun aTypedCommandRunsOnceItIsTheOnlyMatch() {
        config.set(Keys.LAUNCH_INSTANT, InstantRun.ALL)
        val vm = home()
        // One letter at a time, as a keyboard types.
        for (n in 1.."his".length) main { vm.onInputChange(TextFieldValue("his".take(n), TextRange(n))) }
        assertEquals("", vm.input.text)
        waitUntil("history run") { added().any { it.kind == LogKind.COMMAND && it.text == "history" } }
    }

    /** `log only` suggests where the log's lines came from. */
    @Test fun logOnlySuggestsTheLogsSources() {
        logs.add(LogKind.OUTPUT, "$token from a source", source = token)
        val vm = home()
        waitUntil("the source suggested") { vm.suggestions("log only ${token.dropLast(1)}").any { it is Suggestion.Completion && it.replacement == "log only $token" } }
    }

    /** A script started by name (the script sheet's Run) runs and says so. */
    @Test fun aScriptRunsByName() {
        val name = "${token}named"
        val s = scripts.create(name, ScriptLang.LUA, "log(\"$token by name\")")
        try {
            val vm = home()
            main { vm.runScriptNamed(name) }
            assertEquals(name, line("Running $name…").ctx.values[LogContext.SCRIPT])
            waitUntil("its output", ms = 15_000) { added().any { it.text == "$token by name" && it.source == name } }
        } finally {
            ScriptScheduler.cancel(app, s.id)
            scripts.delete(s.id)
        }
    }

    /** Without the accessibility service, a lock gesture explains once, not on every double-tap. */
    @Test fun aLockGestureWithoutTheServiceExplainsOnce() {
        assumeFalse("the lock service is on: the gesture would lock the screen", com.mtwabpin.kolahal.gesture.LockService.isEnabled(app))
        val vm = home()
        config.set(Keys.GESTURE_DOUBLE_TAP, "lock")
        main {
            vm.onGesture(Keys.GESTURE_DOUBLE_TAP)
            vm.onGesture(Keys.GESTURE_DOUBLE_TAP)
        }
        fun errors() = added().count { it.kind == LogKind.ERROR }
        waitUntil("the explanation") { errors() == 1 }
        staysTrue("explained twice") { errors() == 1 }
    }

    /** The touch key bar's arrows, Home, End, space and backspace edit the prompt as a keyboard does. */
    @Test fun keyBarKeysEditThePrompt() {
        val vm = home()
        vm.typed("ac")
        main { vm.onKeyBar(KeyBarKey.LEFT) }
        assertEquals(1, vm.input.selection.end)
        main { vm.insert("b") }
        assertEquals(TextFieldValue("abc", TextRange(2)), vm.input)
        main { vm.onKeyBar(KeyBarKey.HOME) }
        assertEquals(0, vm.input.selection.end)
        // Backspace at the start has nothing to delete.
        main { vm.onKeyBar(KeyBarKey.BKSP) }
        assertEquals(TextFieldValue("abc", TextRange(0)), vm.input)
        main {
            vm.onKeyBar(KeyBarKey.END)
            vm.onKeyBar(KeyBarKey.RIGHT)
        }
        assertEquals(3, vm.input.selection.end)
        main {
            vm.onKeyBar(KeyBarKey.BKSP)
            vm.onKeyBar(KeyBarKey.SPACE)
        }
        assertEquals(TextFieldValue("ab ", TextRange(3)), vm.input)
        // A selection goes whole.
        main { vm.onInputChange(TextFieldValue("abcd", TextRange(1, 3))) }
        main { vm.deleteBackward() }
        assertEquals(TextFieldValue("ad", TextRange(1)), vm.input)
        main { vm.onKeyBar(KeyBarKey.ESC) }
        assertEquals("", vm.input.text)
    }

    /** The key bar's keyboard key opens the keyboard on the prompt. */
    @Test fun theKeyboardKeyFocusesThePrompt() {
        val vm = home()
        val seen = events(vm)
        main { vm.onKeyBar(KeyBarKey.KBD) }
        waitUntil("the prompt focused") { UiEvent.FocusPrompt in seen }
    }

    /** ↑ walks back through the commands run, newest first; ↓ walks forward again and ends on what was being typed. */
    @Test fun historyKeysWalkBackAndReturnToTheDraft() {
        val vm = home()
        logs.add(LogKind.COMMAND, "echo $token-1")
        val newest = logs.add(LogKind.COMMAND, "echo $token-2")
        waitUntil("the commands loaded") { vm.entries.value.any { it.id == newest } }
        vm.typed("dr")
        main { vm.onKeyBar(KeyBarKey.UP) }
        assertEquals("echo $token-2", vm.input.text)
        main { vm.onKeyBar(KeyBarKey.UP) }
        assertEquals("echo $token-1", vm.input.text)
        main { vm.onKeyBar(KeyBarKey.DOWN) }
        assertEquals("echo $token-2", vm.input.text)
        main { vm.onKeyBar(KeyBarKey.DOWN) }
        assertEquals("dr", vm.input.text)
        main { vm.onKeyBar(KeyBarKey.DOWN) }
        assertEquals("dr", vm.input.text)
    }

    /** Tab puts the first suggestion in the prompt: an app's name, or the line a completion makes. */
    @Test fun tabTakesTheFirstSuggestion() {
        val vm = home()
        for (text in listOf("ech", "config se")) {
            vm.typed(text)
            val expected = when (val first = vm.suggestions(text).first()) {
                is Suggestion.App -> first.app.label
                is Suggestion.Completion -> first.replacement
            }
            main { vm.onKeyBar(KeyBarKey.TAB) }
            assertEquals(TextFieldValue(expected, TextRange(expected.length)), vm.input)
        }
    }

    /** A font file that can't be read is reported, on the page and in the log, and the font stays as it was. */
    @Test fun aFontThatCantBeReadIsReported() {
        val vm = home()
        main { vm.importFont(android.net.Uri.fromFile(java.io.File(app.cacheDir, "$token-missing.ttf"))) }
        waitUntil("the notice") { vm.notice.value?.text?.startsWith("font import failed: ") == true }
        assertEquals(LogKind.ERROR, line(vm.notice.value!!.text).kind)
        assertNotEquals(com.mtwabpin.kolahal.ui.TermFonts.CUSTOM, config[Keys.FONT])
    }

    /** Enter in a `/` search hides the keyboard and keeps the search: nothing runs. */
    @Test fun enterInALogSearchKeepsTheSearch() {
        val vm = home()
        val seen = events(vm)
        vm.typed("/$token")
        main { vm.onKeyBar(KeyBarKey.ENTER) }
        assertEquals("/$token", vm.input.text)
        waitUntil("the keyboard hidden") { UiEvent.HideKeyboard in seen }
        staysTrue("a command ran") { added().none { it.kind == LogKind.COMMAND } }
    }

    /** A swiped-away line goes from the log and comes back with Undo. */
    @Test fun aSwipedLineComesBackWithUndo() {
        val vm = home()
        val id = logs.add(LogKind.OUTPUT, "$token swiped")
        waitUntil("the line shown") { vm.entries.value.any { it.id == id } }
        val entry = vm.entries.value.single { it.id == id }
        main { vm.swipeDelete(entry) }
        waitUntil("the line deleted") { added().none { it.id == id } }
        assertEquals("Line deleted", vm.undo.value?.text)
        main { vm.undo() }
        assertNull(vm.undo.value)
        waitUntil("the line back") { added().any { it.id == id && it.text == "$token swiped" } }
    }

    /** A fresh screen hides the lines above it; showing them again brings them back. */
    @Test fun hiddenLinesComeBackWithShowHidden() {
        // Showing them again shows every hidden line, so only from a log with none hidden.
        assumeTrue("lines were hidden already", logs.hiddenCount() == 0)
        val vm = home()
        val id = logs.add(LogKind.OUTPUT, "$token hidden")
        waitUntil("the line shown") { vm.entries.value.any { it.id == id } }
        try {
            main { vm.freshScreen() }
            waitUntil("the line hidden") { logs.isHidden(id) }
            waitUntil("home counts it") { vm.hiddenLines.value > 0 }
            vm.showHidden()
            waitUntil("the line back") { !logs.isHidden(id) }
        } finally {
            logs.unhide()
        }
    }

    /** A search result that's gone by the time it's shown doesn't stay a pending jump. */
    @Test fun aJumpToALineThatIsGoneIsDropped() {
        val vm = home()
        val id = logs.add(LogKind.OUTPUT, "$token gone")
        waitUntil("the line shown") { vm.entries.value.any { it.id == id } }
        val entry = vm.entries.value.single { it.id == id }
        logs.delete(id)
        main { vm.showInLog(entry) }
        waitUntil("the jump asked for") { vm.jumpTo.value == id }
        waitUntil("the jump dropped") { vm.jumpTo.value == null }
    }

    /** The editor's Run saves the code, runs it, says so, and its lines come back for the editor. */
    @Test fun runningFromTheEditorSavesAndRuns() {
        val name = "${token}run"
        val s = scripts.create(name, ScriptLang.LUA, "log(1)")
        try {
            val vm = home()
            val since = System.currentTimeMillis()
            val done = CompletableFuture<Throwable?>()
            main { vm.runScript(s, "log(\"$token ran\")") { failure, _ -> done.complete(failure) } }
            assertNull(done.get(10, TimeUnit.SECONDS))
            assertEquals("log(\"$token ran\")", scripts.byId(s.id)!!.code)
            assertEquals(name, line("Running $name…").ctx.values[LogContext.SCRIPT])
            waitUntil("its output", ms = 15_000) { runBlocking { vm.runLines(name, since) }.any { it.text == "$token ran" } }
        } finally {
            ScriptScheduler.cancel(app, s.id)
            scripts.delete(s.id)
        }
    }

    /** Settings, Feeds and Scripts are tabs: switching replaces the page, so Back goes home. */
    @Test fun switchingTabsReplacesThePage() {
        val vm = home()
        main {
            vm.openScreen(Screen.SETTINGS)
            vm.switchTab(Screen.FEEDS)
        }
        assertEquals(Screen.FEEDS, vm.screen.value)
        main { vm.closeScreen() }
        assertEquals(Screen.TERMINAL, vm.screen.value)
    }

    /** What commands ask of the screen (a permission, a picker) goes to it as the matching event. */
    @Test fun requestsGoToTheScreen() {
        val vm = home()
        val seen = events(vm)
        main {
            vm.requestContactsPermission()
            vm.requestCalendarPermission()
            vm.requestCallLogPermission()
            vm.requestNotificationPermission()
            vm.pickScriptFolder()
            vm.requestFontImport()
            vm.pickContact { }
        }
        val asked = listOf(
            UiEvent.RequestContactsPermission, UiEvent.RequestCalendarPermission, UiEvent.RequestCallLogPermission,
            UiEvent.RequestNotificationPermission, UiEvent.PickScriptFolder, UiEvent.PickFont, UiEvent.PickContact,
        )
        waitUntil("every request in $seen") { seen.toList() == asked }
    }

    /** Home from another page clears the prompt, lands on the terminal, and leaves one arrival for it to pick up. */
    @Test fun homeFromAnotherPageLeavesAnArrival() {
        val vm = home()
        vm.typed("half typed")
        main {
            vm.openScreen(Screen.SETTINGS)
            vm.onHomePressed()
        }
        assertEquals(Screen.TERMINAL, vm.screen.value)
        assertEquals("", vm.input.text)
        assertNotNull(vm.consumeArrival())
        assertNull(vm.consumeArrival())
    }

    /** An Undo not taken goes after a few seconds, and the swiped line stays deleted. */
    @Test fun anUndoNotTakenExpires() {
        val vm = home()
        val id = logs.add(LogKind.OUTPUT, "$token expires")
        waitUntil("the line shown") { vm.entries.value.any { it.id == id } }
        val entry = vm.entries.value.single { it.id == id }
        main { vm.swipeDelete(entry) }
        assertEquals("Line deleted", vm.undo.value?.text)
        waitUntil("the Undo gone", ms = 12_000) { vm.undo.value == null }
        assertTrue(added().none { it.id == id })
    }

    /**
     * In `launch.instant all` a tapped command line runs again, unless it's sensitive (`echo`): that
     * one goes to the prompt, and its long-press Run again runs it.
     */
    @Test fun aCommandLineRunsAgain() {
        config.set(Keys.LAUNCH_INSTANT, InstantRun.ALL)
        val vm = home()
        val unlocks = logs.add(LogKind.COMMAND, "unlocks 2")
        val echo = logs.add(LogKind.COMMAND, "echo $token again")
        waitUntil("the lines shown") { vm.entries.value.map { it.id }.containsAll(listOf(unlocks, echo)) }
        main { vm.onTap(vm.entries.value.single { it.id == unlocks }) }
        waitUntil("run by the tap") { added().count { it.kind == LogKind.COMMAND && it.text == "unlocks 2" } == 2 }
        val entry = vm.entries.value.single { it.id == echo }
        main { vm.onTap(entry) }
        assertEquals("echo $token again", vm.input.text)
        main { vm.actionsFor(entry).single { it.label == "Run again" }.run() }
        assertEquals(LogKind.OUTPUT, line("$token again").kind)
        assertEquals("", vm.input.text)
    }

    /** A script's error line opens the editor at that line; closing it with nothing unsaved just closes it. */
    @Test fun aScriptErrorOpensTheEditorAtItsLine() {
        val name = "${token}err"
        val s = scripts.create(name, ScriptLang.LUA, "log(1)\nlog(2)\nerror(3)")
        try {
            val vm = home()
            val id = logs.add(LogKind.ERROR, "$token failed", source = name, ctx = LogContext.of(LogContext.SCRIPT to name, LogContext.LINE to "3"))
            waitUntil("the line shown") { vm.entries.value.any { it.id == id } }
            main { vm.onTap(vm.entries.value.single { it.id == id }) }
            waitUntil("the editor open") { vm.editing.value?.id == s.id }
            assertEquals(3, vm.editLine.value)
            main { vm.closeEditor() }
            assertNull(vm.editing.value)
            assertEquals("log(1)\nlog(2)\nerror(3)", scripts.byId(s.id)!!.code)
        } finally {
            scripts.delete(s.id)
        }
    }

    /** A todo line's Mark done marks that todo done. */
    @Test fun aTodoLineMarksItsTodoDone() {
        val todos = com.mtwabpin.kolahal.productivity.TodoStore.get(app)
        val todo = todos.add("$token todo")
        try {
            val vm = home()
            val id = logs.add(LogKind.OUTPUT, "$token todo", ctx = LogContext.of(LogContext.TODO to todo.toString()))
            waitUntil("the line shown") { vm.entries.value.any { it.id == id } }
            main { vm.actionsFor(vm.entries.value.single { it.id == id }).single { it.label == "Mark done" }.run() }
            waitUntil("the todo done") { todos.all().single { it.id == todo }.done }
        } finally {
            todos.remove(todo)
        }
    }

    /** `todo rm`, `notes rm` and `remind rm` suggest the items there are, by number. */
    @Test fun removingSuggestsTheItemsThereAre() {
        val todos = com.mtwabpin.kolahal.productivity.TodoStore.get(app)
        val notes = com.mtwabpin.kolahal.productivity.NoteStore.get(app)
        val reminders = com.mtwabpin.kolahal.productivity.ReminderStore.get(app)
        val todo = todos.add("$token todo")
        val note = notes.add("$token note")
        val reminder = reminders.add(System.currentTimeMillis() + 3_600_000L, "$token reminder")
        try {
            val vm = home()
            for ((line, item) in listOf("todo rm $token" to "todo", "notes rm $token" to "note", "remind rm $token" to "reminder")) {
                waitUntil("'$line' suggests the $item") { vm.suggestions(line).any { it.label.endsWith(". $token $item") } }
            }
        } finally {
            todos.remove(todo)
            notes.remove(note)
            reminders.remove(reminder.id)
        }
    }
}
