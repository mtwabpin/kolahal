package com.mtwabpin.kolahal.terminal

import android.Manifest
import android.app.ActivityManager
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserManager
import android.provider.Settings
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.CommandRunner
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.CrashReports
import com.mtwabpin.kolahal.CrashReportsAside
import com.mtwabpin.kolahal.LogKept
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.TerminalUiStub
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.data.AutoBackup
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Db
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.gesture.LockService
import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.script.ScriptWorker
import com.mtwabpin.kolahal.shell
import com.mtwabpin.kolahal.waitUntil
import com.mtwabpin.kolahal.widgets.TestWidgets
import com.mtwabpin.kolahal.widgets.WidgetHost
import com.mtwabpin.kolahal.widgets.listing
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The dispatcher's own commands: aliases, pipes, searches, settings pages, and what each says when it can't. */
@RunWith(AndroidJUnit4::class)
class BuiltInCommandsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val logs = LogRepository.get(context)
    private val config = Config.get(context)
    private val prefs = AppPrefs.get(context)
    private val scripts = ScriptRepository.get(context)
    private val fx = KvStore(context, "fx")
    private val fxBefore = fx.get("USD")
    private val crashDir = File(context.filesDir, "crash")
    private val crashReport = File(crashDir, "20260101-120000-crash.txt")
    private val olderReport = File(crashDir, "20251231-120000-crash.txt")
    /** Reports already there (one Android recorded earlier in the run, say), set aside while a test reads the newest. */
    @get:Rule val crashReports = CrashReportsAside()
    @get:Rule val logLines = LogLinesSince()
    // The log `clear all` empties, put back after it.
    @get:Rule val logKept = LogKept(eachTest = false)
    @get:Rule val configReset = ConfigReset()

    private val screens = CopyOnWriteArrayList<Screen>()
    private val backupFolderAsked = CopyOnWriteArrayList<Boolean>()
    private var notificationsAsked = 0

    private val ui = object : TerminalUiStub() {
        override fun openScreen(screen: Screen) { screens += screen }
        override fun requestNotificationPermission() { notificationsAsked++ }
        override fun askBackupFolder(thenBackUp: Boolean) { backupFolderAsked += thenBackUp }
    }
    private val commands = CommandRunner(context, ui)
    private val processor = commands.processor

    /** The pages the commands open, kept here instead of shown: a browser, Settings, the share sheet. */
    private val opened = CopyOnWriteArrayList<Intent>()
    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult {
            opened += intent
            return Instrumentation.ActivityResult(0, null)
        }
    }

    @Before fun setUp() = instrumentation.addMonitor(monitor)

    @After fun tearDown() {
        instrumentation.removeMonitor(monitor)
        prefs.aliases().keys.filter { it.startsWith("pca") }.forEach { prefs.setAlias(it, null) }
        scripts.all().filter { it.name.startsWith("pctest") }.forEach { ScriptScheduler.cancel(context, it.id); scripts.delete(it.id) }
        fx.set("USD", fxBefore)
    }

    /** Runs [line] and returns the non-command lines it printed. */
    private fun run(line: String) = commands.run(line)

    /** The newest line printed after [from] that [matches], once there is one. */
    private fun printed(from: Long, what: String, ms: Long = 10_000, matches: (String) -> Boolean): LogEntry {
        waitUntil(what, ms) { logs.recent(100).any { it.id > from && matches(it.text) } }
        return logs.recent(100).last { it.id > from && matches(it.text) }
    }

    /** A filter that can't run keeps what the command printed and says why under it. */
    @Test fun aFilterThatFailsKeepsTheOutput() {
        assertEquals(listOf("pctest", "Usage: … | grep [-v] <pattern>"), run("echo pctest | grep"))
    }

    /** An alias may name another, five deep; the sixth alias in a chain isn't expanded. */
    @Test fun aliasesFollowFiveLevels() {
        run("alias pca5 = echo pctest deep")
        for (n in 4 downTo 0) run("alias pca$n = pca${n + 1}")
        assertEquals(listOf("pctest deep x"), run("pca1 x"))
        assertTrue(run("pca0 x").single().startsWith("Command not found: pca5"))
    }

    /** `search` uses the engine search.engine names, or search.url's address; yt, wiki, maps and play go to theirs. */
    @Test fun searchOpensTheChosenEngine() {
        fun opens(line: String): String? {
            opened.clear()
            run(line)
            return opened.singleOrNull()?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString
        }
        assertEquals(listOf("Search: pctest cats"), run("search pctest cats"))
        assertEquals("https://duckduckgo.com/?q=pctest%20cats", opened.single().dataString)
        assertEquals("https://duckduckgo.com/?q=pctest%20cats", logs.recent(1).single().ctx.values[LogContext.URL])
        for ((engine, url) in listOf(
            "google" to "https://www.google.com/search?q=pctest",
            "bing" to "https://www.bing.com/search?q=pctest",
            "brave" to "https://search.brave.com/search?q=pctest",
            "startpage" to "https://www.startpage.com/do/search?q=pctest",
            "kagi" to "https://kagi.com/search?q=pctest",
        )) {
            config.set(Keys.SEARCH_ENGINE, engine)
            assertEquals(engine, url, opens("g pctest"))
        }
        config.set(Keys.SEARCH_ENGINE, "custom")
        config.set(Keys.SEARCH_URL, "https://search.example/find?words=%s")
        assertEquals("https://search.example/find?words=pctest", opens("search pctest"))
        // An address with no %s to put the words in: the default engine.
        config.set(Keys.SEARCH_URL, "https://search.example/")
        assertEquals("https://duckduckgo.com/?q=pctest", opens("search pctest"))

        assertEquals("https://www.youtube.com/results?search_query=pctest%20song", opens("yt pctest song"))
        assertEquals("https://en.wikipedia.org/w/index.php?search=pctest", opens("wiki pctest"))
        assertEquals(listOf("Maps: pctest place"), run("maps pctest place"))
        assertEquals("geo:0,0?q=pctest%20place", opened.last().dataString)
        assertEquals(listOf("Play Store: pctest game"), run("play pctest game"))
        assertEquals("market://search?q=pctest%20game", opened.last().dataString)

        opened.clear()
        assertEquals(listOf("Usage: search <query>"), run("search"))
        assertEquals(emptyList<Intent>(), opened.toList())
    }

    /** `battery`, `system`, `home` and `usage-access` open their pages in Settings and print nothing. */
    @Test fun settingsPagesOpen() {
        for ((command, action) in listOf(
            "battery" to Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
            "system" to Settings.ACTION_SETTINGS,
            "home" to Settings.ACTION_HOME_SETTINGS,
            "usage-access" to Settings.ACTION_USAGE_ACCESS_SETTINGS,
        )) {
            opened.clear()
            assertEquals(command, emptyList<String>(), run(command))
            assertEquals(command, action, opened.single().action)
        }
    }

    /** Without its accessibility service `lock` says how to allow it; `accessibility` opens that page. */
    @Test fun lockWithoutTheServiceSaysHowToAllowIt() {
        assumeTrue("the Play build has no lock", BuildConfig.LOCK_SERVICE)
        assertEquals(emptyList<String>(), run("accessibility"))
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, opened.single().action)
        // With the service on, this would lock the emulator's screen.
        assumeFalse("the screen lock service is on", LockService.isEnabled(context))
        assertEquals(listOf("Locking needs the Kolahal screen lock accessibility service — tap to allow it"), run("lock"))
        assertEquals("accessibility", logs.recent(1).single().ctx.values[LogContext.RUN])
    }

    /** `notifications` asks for the permission while it's missing and then says it's allowed; `notifications settings` opens Kolahal's page. */
    @Test fun notificationsAsksThenSaysAllowed() {
        assertEquals(emptyList<String>(), run("notifications settings"))
        with(opened.single()) {
            assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, action)
            assertEquals(context.packageName, getStringExtra(Settings.EXTRA_APP_PACKAGE))
        }
        opened.clear()
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            assertEquals(emptyList<String>(), run("notifications"))
            assertEquals(1, notificationsAsked)
            // As the app asks for it. Not taken back after: Android stops an app whose permission is revoked.
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        assertEquals(listOf("Notifications are allowed"), run("notifications"))
        assertEquals(emptyList<Intent>(), opened.toList())
    }

    /** `= 10 usd in eur` converts at the rates kept from a fetch in the last six hours; a currency they don't have says so. */
    @Test fun currencyFromTheKeptRates() {
        fx.set("USD", Json.stringify(mapOf("at" to System.currentTimeMillis(), "rates" to mapOf("EUR" to 0.5, "INR" to 88.25))))
        var from = logs.maxId()
        run("= 10 usd in eur")
        val line = printed(from, "the conversion") { com.mtwabpin.kolahal.data.StyledText.plain(it).startsWith("5 EUR") }
        // Big: the result, with the amount it converts under it.
        assertEquals(com.mtwabpin.kolahal.data.StyledText.stats(listOf("5 EUR" to "10 USD")), line.text)
        assertEquals("5", line.ctx.values[LogContext.COPY])
        // Converted off the command's thread, it is still the command's output.
        val command = logs.recent(100).last { it.id > from && it.kind == LogKind.COMMAND && it.text == "= 10 usd in eur" }
        assertEquals(command.id.toString(), line.ctx.values[LogContext.FROM])
        from = logs.maxId()
        run("= 1,000 usd to inr")
        assertEquals("88,250 INR 1,000 USD", com.mtwabpin.kolahal.data.StyledText.plain(printed(from, "the second conversion") { com.mtwabpin.kolahal.data.StyledText.plain(it).endsWith(" USD") }.text))
        from = logs.maxId()
        run("= 10 usd in xyz")
        assertEquals(LogKind.ERROR, printed(from, "the unknown currency") { it == "convert: unknown currency XYZ" }.kind)
    }

    /** `lua` and `js` without code say how they're used, and code too long for a snippet is refused; `=` with something that isn't arithmetic goes to Lua. */
    @Test fun snippets() {
        assertEquals(listOf("Usage: lua <code>"), run("lua"))
        assertEquals(listOf("Usage: js <code>"), run("js"))
        assertEquals(listOf("Snippet too long — use 'script new'"), run("lua log('${"x".repeat(8_001)}')"))
        val from = logs.maxId()
        run("= string.rep('pcq', 2)")
        printed(from, "Lua's answer", ms = 30_000) { it == "pcqpcq" }
    }

    /** `unlocks 3` is a line a day, oldest first, counts lined up; without usage access it says it counts only while it runs. */
    @Test fun unlocksPerDay() {
        val op = Regex("GET_USAGE_STATS: (\\w+)").find(shell("appops get ${context.packageName} GET_USAGE_STATS"))?.groupValues?.get(1) ?: "default"
        val today = LocalDate.now()
        val days = listOf(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(today.minusDays(2)), "Yesterday", "Today")
        val row = Regex("^(\\S.*?)\\s+(\\d+) (unlocks?)$")
        fun check(rows: List<String>) {
            val parsed = rows.map { row.find(it) ?: throw AssertionError("not a row: '$it'") }
            assertEquals(days, parsed.map { it.groupValues[1] })
            parsed.forEach { assertEquals(it.value, it.groupValues[2] == "1", it.groupValues[3] == "unlock") }
            assertEquals(rows.toString(), 1, rows.map { it.substringBeforeLast(' ').length }.distinct().size)
        }
        try {
            shell("appops set ${context.packageName} GET_USAGE_STATS ignore")
            val without = run("unlocks 3")
            assertEquals(listOf("Counting unlocks only while the launcher is running", "Tap here to allow usage access for accurate counts"), without.take(2))
            assertEquals("usage-access", logs.recent(10).single { it.text == without[1] }.ctx.values[LogContext.RUN])
            // Big numbers first: today, a day's average, the total.
            assertTrue(without[2], Regex("\\d+ unlocks? today · \\d+ a day on average · \\d+ in 3 days").matches(com.mtwabpin.kolahal.data.StyledText.plain(without[2])))
            check(without.drop(3))

            shell("appops set ${context.packageName} GET_USAGE_STATS allow")
            check(run("unlocks 3").drop(1))
            assertEquals(31, run("unlocks 99").size)
            // One day: its count, large, and no bar.
            assertTrue(Regex("\\d+ unlocks? today").matches(com.mtwabpin.kolahal.data.StyledText.plain(run("unlocks 0").single())))
        } finally {
            shell("appops set ${context.packageName} GET_USAGE_STATS $op")
        }
    }

    /** `crashes share` hands the newest report, or the one named, to the share sheet. */
    @Test fun crashesShareSendsTheReport() {
        // Only the test's own, the rest set aside: one written earlier in the run is newer than these, and would be sent.
        assertEquals(listOf("No crash reports"), run("crashes share"))
        crashDir.mkdirs()
        // The newest, as just written.
        crashReport.writeText(
            "Kolahal crash report\nKolahal 0.0 (1, debug)\nAndroid\nWhen: x\nWhat: crash in test, thread main\n\njava.lang.IllegalStateException: pctest boom\n\tat a.b(c:1)\n",
        )
        // And one from the day before, to tell the newest from the rest.
        olderReport.writeText(
            "Kolahal crash report\nKolahal 0.0 (1, debug)\nAndroid\nWhen: y\nWhat: crash in test, thread main\n\njava.lang.IllegalStateException: pctest older\n\tat a.b(c:1)\n",
        )
        val shares = listOf(
            "crashes share" to "pctest boom", "crashes share 1" to "pctest boom", "crashes share 20260101-120000" to "pctest boom",
            "crashes share 2" to "pctest older", "crashes share 20251231-120000" to "pctest older",
        )
        for ((line, sent) in shares) {
            opened.clear()
            assertEquals(line, emptyList<String>(), run(line))
            val chooser = opened.single()
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val send = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)!!
            assertEquals(Intent.ACTION_SEND, send.action)
            assertTrue(line, send.getStringExtra(Intent.EXTRA_TEXT).orEmpty().contains("IllegalStateException: $sent"))
        }
    }

    /** A word these commands don't take says how they're used. */
    @Test fun wrongWordsSayTheUsage() {
        // The usage lines help shows: what a wrong word says can't drift from them.
        assertEquals(listOf("Usage: log [only <name|kind>|all]"), run("log bogus"))
        assertEquals(listOf("Usage: backup [now|every <daily|weekly|off>|dir]"), run("backup bogus"))
        assertEquals(listOf("Usage: restart [scripts]"), run("restart bogus"))
        assertEquals(listOf("Usage: clear [history|all]"), run("clear bogus"))
        assertEquals(listOf("Usage: widgets [ls] · widget add"), run("widget bogus"))
        if (BuildConfig.SELF_UPDATE) assertEquals(listOf("Usage: update [install]"), run("update bogus"))
        assertEquals(emptyList<Screen>(), screens.toList())
    }

    /** With no backup folder yet, `backup` asks for one and backs up once it's picked; `backup dir` only picks it. */
    @Test fun backupWithoutAFolderAsksForOne() {
        assumeTrue("a backup folder is set", AutoBackup.folder(context) == null)
        assertEquals(listOf("Pick a folder for backups; the backup is written there"), run("backup"))
        assertEquals(listOf(true), backupFolderAsked.toList())
        assertEquals(emptyList<String>(), run("backup dir"))
        assertEquals(emptyList<String>(), run("backup folder"))
        assertEquals(listOf(true, false, false), backupFolderAsked.toList())
    }

    /** `backup` to a folder Kolahal can no longer write says so under the command, and doesn't ask for another. */
    @Test fun backupToAFolderThatCantBeWrittenSaysSo() {
        val autoBackup = context.getSharedPreferences("auto_backup", Context.MODE_PRIVATE)
        val folderWas = autoBackup.getString("folder", null)
        try {
            // A folder whose grant is gone.
            autoBackup.edit(commit = true) { putString("folder", "content://com.android.externalstorage.documents/tree/primary%3AKolahalTestBackups") }
            val from = logs.maxId()
            processor.run("backup")
            val failed = printed(from, "the failure") { it.startsWith("backup: ") }
            assertEquals("backup: the backup folder can't be written: run 'backup dir'", failed.text)
            assertEquals(LogKind.ERROR, failed.kind)
            assertEquals(emptyList<Boolean>(), backupFolderAsked.toList())
        } finally {
            autoBackup.edit(commit = true) { if (folderWas != null) putString("folder", folderWas) else remove("folder") }
        }
    }

    /** `widget` opens the widgets page and `widget add` the picker; with none placed, `widgets ls` says how to add one. */
    @Test fun widgetsOpenTheirPages() {
        assertEquals(emptyList<String>(), run("widget"))
        assertEquals(emptyList<String>(), run("widget add"))
        assertEquals(listOf(Screen.WIDGETS, Screen.WIDGET_PICKER), screens.toList())
        assumeTrue("widgets are placed", WidgetHost.get(context).listing(context).isEmpty())
        assertEquals(listOf("No widgets yet — widget add opens the picker"), run("widgets ls"))
        assertEquals("widget add", logs.recent(1).single().ctx.values[LogContext.RUN])
    }

    /** `widgets ls` numbers each widget, with its state, size and app beside it; a tap opens the widgets page. */
    @Test fun widgetsLsListsEachWidget() {
        val widgets = TestWidgets()
        try {
            assumeTrue("widgets are placed", widgets.host.listing(context).isEmpty())
            val serial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())
            // One from a backup, waiting to be added again: no binding needed.
            widgets.restore("com.example.pctest/com.example.pctest.ClockWidget", serial, height = 120)
            assertEquals(listOf("1. ClockWidget"), run("widgets ls"))
            val line = logs.recent(1).single()
            assertEquals("pending re-add · full × 120dp · com.example.pctest", line.ctx.values[LogContext.DESC])
            assertEquals("widgets", line.ctx.values[LogContext.RUN])
        } finally {
            widgets.close()
        }
    }

    /** `clear all` deletes the whole log, and ↑ history with it. */
    @Test fun clearAllEmptiesTheLog() {
        logKept.keep()
        run("echo pctest-one")
        assertTrue(logs.commandHistory().contains("echo pctest-one"))
        val upTo = logs.maxId()
        assertEquals(emptyList<String>(), run("clear all"))
        // Ids are never reused: anything at or below it is a line from before. Deleted, not hidden.
        assertEquals(emptyList<LogEntry>(), logs.recent(400).filter { it.id <= upTo })
        assertTrue(Db.get(context).readableDatabase.rawQuery("SELECT COUNT(*) FROM logs WHERE id <= ?", arrayOf(upTo.toString())).use { it.moveToFirst(); it.getInt(0) == 0 })
        assertEquals(emptyList<String>(), logs.commandHistory())
    }

    /** `restart scripts` stops the process scripts run in. */
    @Test fun restartScriptsStopsTheScriptProcess() {
        val am = context.getSystemService(ActivityManager::class.java)
        fun scriptPid() = am.runningAppProcesses.orEmpty().firstOrNull { it.processName == "${context.packageName}:script" }?.pid
        val from = logs.maxId()
        run("lua log('pctest-up')")
        printed(from, "the snippet's line", ms = 30_000) { it == "pctest-up" }
        // Its run over, so stopping the process can't cut it short (and have it run again later).
        val work = WorkManager.getInstance(context)
        waitUntil("the snippet's run finished") { work.getWorkInfosByTag(ScriptWorker::class.java.name).get().none { it.state == WorkInfo.State.RUNNING } }
        val pid = scriptPid()
        assertNotNull("the script process runs", pid)
        assertEquals(listOf("Script process stopped; scheduled scripts will start it again"), run("restart scripts"))
        waitUntil("process $pid gone") { scriptPid() != pid }
    }

    /** `help` lists the scripts that are commands under a heading of their own, each with its usage and description; `help <name>` shows that one. */
    @Test fun helpListsScriptCommands() {
        scripts.create("pctestcmd", ScriptLang.LUA, "-- @command\n-- @desc Says hello\n-- @usage pctestcmd <name>\nlog('hi ' .. argline)")
        scripts.create("pctestbare", ScriptLang.LUA, "-- @command\nlog('hi')")
        run("help")
        val help = logs.recent(400).filter { it.id > logLines.start && it.kind != LogKind.COMMAND }
        val start = help.indexOfFirst { it.text == "Your script commands" }
        assertTrue(help.map { it.text }.toString(), start >= 0)
        val mine = help.drop(start + 1).filter { it.ctx.values[LogContext.SCRIPT]?.startsWith("pctest") == true }
        assertEquals(listOf("pctestcmd <name>", "pctestbare"), mine.map { it.text }.sortedDescending())
        assertEquals("Says hello", mine.single { it.text == "pctestcmd <name>" }.ctx.values[LogContext.DESC])
        assertEquals("lua script", mine.single { it.text == "pctestbare" }.ctx.values[LogContext.DESC])

        assertEquals(listOf("pctestcmd <name>"), run("help pctestcmd"))
        assertEquals("Says hello", logs.recent(1).single().ctx.values[LogContext.DESC])
    }
}
