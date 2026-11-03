package com.mtwabpin.kolahal.terminal

import android.app.Application
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.notifications.KolahalNotificationListener
import com.mtwabpin.kolahal.notifications.NotifPrefs
import com.mtwabpin.kolahal.notifications.NotificationActions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fixes to typing, the log and its lines, as home runs them. */
@RunWith(AndroidJUnit4::class)
class TerminalFixesTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val logs = LogRepository.get(app)
    private val config = Config.get(app)
    private val scripts = ScriptRepository.get(app)
    private val muted = NotifPrefs.get(app)
    private val gone = "com.example.kolahal.gone"

    private val ui = object : TerminalUi {
        override fun pickScriptFolder() = Unit
        override fun requestFontImport() = Unit
        override fun openScreen(screen: Screen) = Unit
        override fun requestContactsPermission() = Unit
        override fun requestCalendarPermission() = Unit
        override fun openEditor(script: Script) = Unit
        override fun askSecret(script: Script, name: String) = Unit
        override fun requestNotificationPermission() = Unit
        override fun clearScreen() { logs.clearScreen() }
    }
    private val processor = CommandProcessor(app, ui)

    @After fun tearDown() {
        Keys.ALL.forEach(config::reset)
        listOf("text", "pins", "updates").forEach { AppPrefs.get(app).setAlias(it, null) }
        listOf("tfcat", "tftag").forEach { n -> scripts.byName(n)?.let { scripts.delete(it.id) } }
        AppPrefs.get(app).setAlias("tfhf", null)
        com.mtwabpin.kolahal.productivity.TodoStore.get(app).let { t -> t.all().filter { it.text.startsWith("tf ") || it.text.startsWith("clear out") || it.text.startsWith("all hands") }.forEach { t.remove(it.id) } }
        logs.recent(100).filter { it.text.startsWith("tf ") || it.text.startsWith("echo tf ") || it.text.startsWith("tftag") }.forEach { logs.delete(it.id) }
        muted.unmute(gone)
    }

    /** Runs [line] and returns the lines it printed, the command line left out. */
    private fun run(line: String): List<LogEntry> {
        val before = logs.maxId()
        processor.run(line)
        return logs.recent(200).filter { it.id > before && it.kind != LogKind.COMMAND }
    }

    private fun shell(command: String) =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }

    private fun viewModel(): TerminalViewModel {
        lateinit var vm: TerminalViewModel
        instrumentation.runOnMainSync { vm = vms.terminal(app) }
        return vm
    }

    private fun waitUntil(what: String, ms: Long = 5_000, check: () -> Boolean) {
        val until = System.currentTimeMillis() + ms
        while (!check() && System.currentTimeMillis() < until) Thread.sleep(50)
        assertTrue(what, check())
    }

    @Test fun didYouMeanFillsThePromptInsteadOfRunning() {
        // Even where a tap runs whole command lines.
        config.set(Keys.LAUNCH_INSTANT, InstantRun.ALL)
        val hint = run("echoo hi").single()
        assertTrue(hint.text, hint.text.contains("did you mean 'echo hi'"))
        val vm = viewModel()
        val before = logs.maxId()
        instrumentation.runOnMainSync { vm.onTap(hint) }
        assertEquals("echo hi ", vm.input.text)
        Thread.sleep(500)
        assertTrue(logs.recent(50).none { it.id > before })
    }

    /** `feed fetch` for every feed says which ones failed, as fetching one feed does. */
    @Test fun fetchingAllFeedsReportsTheOnesThatFail() {
        val feeds = com.mtwabpin.kolahal.feeds.FeedRepository.get(app)
        // Fetched a few at a time, they're still reported in the feeds' order: the first answers last,
        // from a server here that waits before it fails.
        val slow = java.net.ServerSocket(0, 5, java.net.InetAddress.getByName("127.0.0.1"))
        kotlin.concurrent.thread(isDaemon = true) {
            while (!slow.isClosed) {
                val client = runCatching { slow.accept() }.getOrNull() ?: break
                kotlin.concurrent.thread(isDaemon = true) {
                    client.use { c ->
                        val reader = c.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                        generateSequence { reader.readLine()?.takeIf { it.isNotEmpty() } }.toList()
                        Thread.sleep(1_500)
                        runCatching { c.getOutputStream().write("HTTP/1.1 500 Oops\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()) }
                    }
                }
            }
        }
        val added = listOf(feeds.add("http://127.0.0.1:${slow.localPort}/feed.xml", title = "tf-broken-a")) +
            listOf("b", "c").map { feeds.add("https://kolahal-test-$it.invalid/feed.xml", title = "tf-broken-$it") }
        try {
            val before = logs.maxId()
            processor.run("feed fetch")
            fun failed() = logs.recent(100).filter { it.id > before && it.kind == LogKind.ERROR && it.text.startsWith("tf-broken-") }
            waitUntil("the failures", ms = 20_000) { failed().size == 3 }
            assertEquals(listOf("a", "b", "c"), failed().map { it.text.substringAfter("tf-broken-").substringBefore(':') })
            // They are the command's output, though the fetch ran in the background.
            val command = logs.recent(100).single { it.id > before && it.kind == LogKind.COMMAND && it.text == "feed fetch" }
            assertTrue(failed().all { it.ctx.values[LogContext.FROM] == command.id.toString() })
        } finally {
            slow.close()
            added.forEach { feeds.remove(it.id) }
            // `feed fetch` queued the feeds' job: with these gone it goes too, unless other feeds keep it.
            com.mtwabpin.kolahal.feeds.FeedScheduler.sync(app)
        }
    }

    private fun TerminalViewModel.type(line: String) = instrumentation.runOnMainSync {
        onInputChange(androidx.compose.ui.text.input.TextFieldValue(line))
        submit()
    }

    /** ↑ once: what the prompt then holds. */
    private fun TerminalViewModel.up(): String {
        var text = ""
        instrumentation.runOnMainSync {
            historyUp()
            text = input.text
        }
        return text
    }

    private fun TerminalViewModel.shown(line: String) = entries.value.count { it.kind == LogKind.COMMAND && it.text == line }

    /** ↑ goes back through what you typed, newest first, each command once. */
    @Test fun upRecallsTypedCommandsNewestFirst() {
        val vm = viewModel()
        vm.type("echo tf one")
        waitUntil("the first") { vm.shown("echo tf one") == 1 }
        vm.type("echo tf two")
        waitUntil("the second") { vm.shown("echo tf two") == 1 }
        vm.type("echo tf one")
        waitUntil("the first again") { vm.shown("echo tf one") == 2 }
        assertEquals("echo tf one", vm.up())
        assertEquals("echo tf two", vm.up())
        // Typed twice, it's there once.
        assertTrue(vm.up() != "echo tf one")
    }

    /** A command's line deleted, ↑ no longer offers it: history is read again when a command goes. */
    @Test fun aDeletedCommandLeavesHistory() {
        val vm = viewModel()
        vm.type("echo tf gone")
        waitUntil("the first") { vm.shown("echo tf gone") == 1 }
        vm.type("echo tf stays")
        waitUntil("the second") { vm.shown("echo tf stays") == 1 }
        logs.delete(vm.entries.value.single { it.kind == LogKind.COMMAND && it.text == "echo tf gone" }.id)
        waitUntil("it leaves the page") { vm.shown("echo tf gone") == 0 }
        assertEquals("echo tf stays", vm.up())
        assertTrue(vm.up() != "echo tf gone")
    }

    /** The same message twice shows twice: each is a notice of its own. */
    @Test fun theSameMessageTwiceIsTwoNotices() {
        val vm = viewModel()
        instrumentation.runOnMainSync { vm.scriptActions.newScript("Not A Name", ScriptLang.LUA, asCommand = false) }
        val first = vm.notice.value!!
        instrumentation.runOnMainSync { vm.scriptActions.newScript("Not A Name", ScriptLang.LUA, asCommand = false) }
        val second = vm.notice.value!!
        assertEquals(first.text, second.text)
        assertTrue(first != second)
    }

    /** `tfhf|count` is the alias and a filter: no spaces needed, and the alias's own pipe still applies. */
    @Test fun anAliasTakesAPipeWithNoSpace() {
        run("alias tfhf = help | grep feed")
        val some = run("tfhf").size
        assertTrue(some > 0)
        assertEquals(listOf("$some"), run("tfhf|count").map { it.text })
    }

    /** `alias hf = help | grep feed`: the pipe is the alias's, for when hf runs. */
    @Test fun anAliasKeepsItsPipe() {
        run("alias tfhf = help | grep feed")
        assertEquals("help | grep feed", AppPrefs.get(app).aliases()["tfhf"])
        val all = run("help").size
        val some = run("tfhf").size
        assertTrue("$some of $all", some in 1 until all)
    }

    /** A built-in's name in another case can't be an alias either: typed, the built-in would win. */
    @Test fun anAliasCantBeABuiltInInAnotherCase() {
        assertTrue(run("alias Help = echo x").single().text.contains("built-in"))
        assertNull(AppPrefs.get(app).aliases()["help"])
    }

    /** `todo clear out the garage` is a todo; `todo clear` on its own clears the finished ones. */
    @Test fun aTodoStartingWithASubcommandWordIsATodo() {
        val todos = com.mtwabpin.kolahal.productivity.TodoStore.get(app)
        run("todo clear out the garage")
        run("todo all hands meeting")
        val texts = todos.all().map { it.text }
        assertTrue(texts.toString(), "clear out the garage" in texts && "all hands meeting" in texts)
    }

    @Test fun feedCommandsRefuseAnUnknownId() {
        assertEquals(LogKind.ERROR, run("feed rm 999999").single().kind)
        assertEquals(LogKind.ERROR, run("feed every 999999 2h").single().kind)
        assertEquals(LogKind.ERROR, run("feed notify 999999 on").single().kind)
    }

    @Test fun anEmptyNoteIsntSaved() {
        val notes = com.mtwabpin.kolahal.productivity.NoteStore.get(app)
        val before = notes.all().size
        assertEquals(LogKind.ERROR, run("notes add").single().kind)
        assertEquals(before, notes.all().size)
    }

    @Test fun aCommandsOtherNamesCantBeAliases() {
        assertTrue(run("alias text = echo x").single().text.contains("built-in command"))
        assertNull(AppPrefs.get(app).aliases()["text"])
    }

    /** `pins` and `updates` are built-ins too: not aliases, and `help` knows them. */
    @Test fun pinsAndUpdatesAreBuiltIns() {
        for (name in listOf("pins", "updates")) {
            assertTrue(run("alias $name = echo x").single().text.contains("built-in command"))
            assertNull(AppPrefs.get(app).aliases()[name])
        }
        assertEquals("pins", run("help pins").first().text)
        assertTrue(run("help updates").any { it.text == "Also known as updates" })
    }

    @Test fun unmuteTakesThePackageThatMutedLists() {
        val listener = "${app.packageName}/${KolahalNotificationListener::class.java.name}"
        val had = NotificationActions.isEnabled(app)
        shell("cmd notification allow_listener $listener")
        waitUntil("notification access") { NotificationActions.isEnabled(app) }
        try {
            muted.mute(gone)
            // `notifs muted` lists an app no longer installed by its package, to unmute by tapping.
            assertEquals("notifs unmute $gone", run("notifs muted").single { it.text == gone }.ctx.command)
            run("notifs unmute $gone")
            assertFalse(gone in muted.muted.value)
        } finally {
            if (!had) shell("cmd notification disallow_listener $listener")
        }
    }

    @Test fun scriptCatKeepsOneCopyOfTheCode() {
        val code = "-- a\nlocal x = 1\nreturn x"
        scripts.create("tfcat", ScriptLang.LUA, code)
        val lines = run("script cat tfcat")
        assertEquals(listOf("  1  -- a", "  2  local x = 1", "  3  return x"), lines.map { it.text })
        assertEquals(listOf(code, null, null), lines.map { it.ctx.copy })
    }

    /**
     * `script diff`: a library copy you edited against the library version, the line only in yours
     * marked +, and how to go back; an unedited copy is the library version; your own script has
     * nothing to compare with.
     */
    @Test fun scriptDiffShowsWhatYouChanged() {
        val library = com.mtwabpin.kolahal.script.ScriptLibrary(app)
        val entry = library.entries.single { it.name == "countdown" }
        val s = library.install(entry)
        fun plain(line: String) = run(line).map { com.mtwabpin.kolahal.data.StyledText.plain(it.text) }
        try {
            scripts.saveCode(s.id, library.code(entry) + "-- mine\n", byUser = true)
            val lines = plain("script diff countdown")
            assertTrue(lines.first(), lines.first().startsWith("countdown.lua against the library version"))
            assertTrue(lines.first(), "+ 1 line only in yours" in lines.first())
            assertTrue(lines.toString(), "+ -- mine" in lines)
            assertTrue(lines.last(), "script install countdown --replace" in lines.last())
            library.install(entry)
            assertEquals(listOf("countdown.lua matches the library version"), plain("script diff countdown"))
            scripts.create("tfcat", ScriptLang.LUA, "return 1")
            assertTrue(plain("script diff tfcat").single().contains("isn't from the library"))
        } finally {
            scripts.byName("countdown")?.let { scripts.delete(it.id) }
            library.forget("countdown")
        }
    }

    @Test fun aFreshScreenHideNeverBringsBackWhatFreshHid() {
        val first = logs.add(LogKind.INFO, "tf one")
        val second = logs.add(LogKind.INFO, "tf two")
        try {
            logs.hideScreen(upTo = second)
            // Leaving home later hides only what was seen then: less than `fresh` hid.
            logs.hideScreen(upTo = first)
            assertTrue(logs.isHidden(second))
        } finally {
            logs.unhide()
            logs.delete(first)
            logs.delete(second)
        }
    }

    /** What a typed command prints, now or from a script it starts, is marked as its output. */
    @Test fun outputIsMarkedWithItsCommand() {
        scripts.create("tftag", ScriptLang.LUA, "-- @command\nlog('tf tagged ' .. argline)")
        val before = logs.maxId()
        processor.run("echo tf echoed")
        processor.run("tftag x")
        val commands = logs.recent(50).filter { it.id > before && it.kind == LogKind.COMMAND }.associate { it.text to it.id.toString() }
        assertEquals(commands["echo tf echoed"], logs.recent(50).single { it.text == "tf echoed" }.ctx.values[LogContext.FROM])
        waitUntil("the script's line", ms = 20_000) { logs.recent(50).any { it.text == "tf tagged x" } }
        assertEquals(commands["tftag x"], logs.recent(50).single { it.text == "tf tagged x" }.ctx.values[LogContext.FROM])
    }

    /** `xkcd random`: its comic comes when the script is done, maybe under other lines; it still goes with it. */
    @Test fun deleteCommandAndOutputTakesOutputThatCameLater() {
        val command = logs.add(LogKind.COMMAND, "tf xkcd random")
        // The script's own timer run, right under the command: not its output.
        logs.add(LogKind.SCRIPT, "tf timer comic", "tfx")
        val other = logs.add(LogKind.COMMAND, "tf echo")
        logs.add(LogKind.OUTPUT, "tf echoed", ctx = LogContext.of(LogContext.FROM to other.toString()))
        logs.add(LogKind.SCRIPT, "tf random comic", "tfx", LogContext.of(LogContext.FROM to command.toString()))
        val vm = viewModel()
        waitUntil("the lines load") { vm.entries.value.any { it.text == "tf random comic" } }
        // Its line offers the command that asked for it, not the one just above it.
        val comic = vm.entries.value.single { it.text == "tf random comic" }
        assertTrue(vm.actionsFor(comic).any { it.label == "Run 'tf xkcd random' again" })
        val delete = vm.actionsFor(vm.entries.value.single { it.id == command }).single { it.label == "Delete command and output" }
        instrumentation.runOnMainSync { delete.run() }
        waitUntil("the output goes") { logs.recent(50).none { it.text == "tf random comic" } }
        assertEquals(listOf("tf timer comic", "tf echo", "tf echoed"), logs.recent(50).map { it.text }.filter { it.startsWith("tf ") })
    }

    /** A line that came on its own (a tap launch, a call) long after a command isn't that command's output. */
    @Test fun aLineOnItsOwnOffersNoCommandToRunAgain() {
        val now = System.currentTimeMillis()
        logs.addAll(
            listOf(
                LogRepository.Pending(LogKind.COMMAND, "tf battery", null, LogContext.EMPTY, now - 7_200_000),
                // Its output from before the marks: within the group.
                LogRepository.Pending(LogKind.OUTPUT, "tf 80%", null, LogContext.EMPTY, now - 7_199_000),
                LogRepository.Pending(LogKind.INFO, "tf Launched Maps", null, LogContext.of(LogContext.PKG to "com.example.maps"), now),
            ),
        )
        val vm = viewModel()
        waitUntil("the lines load") { vm.entries.value.any { it.text == "tf Launched Maps" } }
        val output = vm.entries.value.single { it.text == "tf 80%" }
        assertTrue(vm.actionsFor(output).any { it.label == "Run 'tf battery' again" })
        val launch = vm.entries.value.single { it.text == "tf Launched Maps" }
        assertTrue(vm.actionsFor(launch).none { it.label.startsWith("Run '") })
    }

    /** A pause records the newest line as seen, though `fresh` emptied the screen: those lines were seen live. */
    @Test fun aPauseAfterFreshStillCountsTheLinesAsSeen() {
        val vm = viewModel()
        val line = logs.add(LogKind.SCRIPT, "tf seen live", "tfs", LogContext.of(LogContext.SCRIPT to "tfs"))
        waitUntil("the line loads") { vm.entries.value.any { it.id == line } }
        instrumentation.runOnMainSync { vm.freshScreen() }
        waitUntil("the screen is empty") { vm.entries.value.none { it.id == line } }
        instrumentation.runOnMainSync { vm.onPause() }
        try {
            assertTrue(logs.seenUpTo >= line)
        } finally {
            logs.unhide()
        }
    }

    /** Lines from before 0.4.2 carry no mark: the lines after the command, as the log groups them. */
    @Test fun deleteCommandAndOutputStopsWhereTheGroupDoes() {
        val now = System.currentTimeMillis()
        logs.addAll(
            listOf(
                LogRepository.Pending(LogKind.COMMAND, "tf echo", null, LogContext.EMPTY, now),
                LogRepository.Pending(LogKind.OUTPUT, "tf output", null, LogContext.EMPTY, now + 100),
                // A timer script's line inside the block is its own.
                LogRepository.Pending(LogKind.SCRIPT, "tf script", "tfs", LogContext.EMPTY, now + 200),
                LogRepository.Pending(LogKind.OUTPUT, "tf more output", null, LogContext.EMPTY, now + 300),
                // Much later, with no command in between: a call or an install, not output.
                LogRepository.Pending(LogKind.INFO, "tf installed", null, LogContext.EMPTY, now + 60_000),
            ),
        )
        val vm = viewModel()
        waitUntil("the lines load") { vm.entries.value.any { it.text == "tf installed" } }
        val command = vm.entries.value.single { it.text == "tf echo" }
        val delete = vm.actionsFor(command).single { it.label == "Delete command and output" }
        instrumentation.runOnMainSync { delete.run() }
        fun left() = logs.recent(50).map { it.text }.filter { it.startsWith("tf ") }
        // They go one at a time.
        waitUntil("the block goes") { left().size <= 2 }
        assertEquals(listOf("tf script", "tf installed"), left())
        logs.recent(50).filter { it.text.startsWith("tf ") }.forEach { logs.delete(it.id) }
    }
}
