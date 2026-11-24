package com.mtwabpin.kolahal.terminal.commands

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
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.waitUntil
import com.mtwabpin.kolahal.widgets.TestWidgets
import com.mtwabpin.kolahal.widgets.WidgetHost
import com.mtwabpin.kolahal.widgets.listing
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
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

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
    @get:Rule
    val crashReports = CrashReportsAside()

    @get:Rule
    val logLines = LogLinesSince()

    // The log `clear all` empties, put back after it.
    @get:Rule
    val logKept = LogKept(eachTest = false)

    @get:Rule
    val configReset = ConfigReset()

    private val screens = CopyOnWriteArrayList<Screen>()
    private val backupFolderAsked = CopyOnWriteArrayList<Boolean>()
    private var notificationsAsked = 0
    private var widgetsPageTurns = 0

    private val ui = object : TerminalUiStub() {
        override fun openScreen(screen: Screen) {
            screens += screen
        }

        override fun requestNotificationPermission() {
            notificationsAsked++
        }

        override fun askBackupFolder(thenBackUp: Boolean) {
            backupFolderAsked += thenBackUp
        }

        override fun showWidgetsPage() {
            widgetsPageTurns++
        }
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

    @Before
    fun setUp() = instrumentation.addMonitor(monitor)

    @After
    fun tearDown() {
        instrumentation.removeMonitor(monitor)
        prefs.aliases().keys.filter { it.startsWith("pca") }.forEach { prefs.setAlias(it, null) }
        scripts.all().filter { it.name.startsWith("pctest") }
            .forEach { ScriptScheduler.cancel(context, it.id); scripts.delete(it.id) }
        fx.set("USD", fxBefore)
    }

    /** Runs [line] and returns the non-command lines it printed. */
    private fun run(line: String) = commands.run(line)

    /** The newest line printed after [from] that [matches], once there is one. */
    private fun printed(
        from: Long,
        what: String,
        ms: Long = 10_000,
        matches: (String) -> Boolean
    ): LogEntry {
        waitUntil(what, ms) { logs.recent(100).any { it.id > from && matches(it.text) } }
        return logs.recent(100).last { it.id > from && matches(it.text) }
    }

    /** A filter that can't run keeps what the command printed and says why under it. */
    @Test
    fun aFilterThatFailsKeepsTheOutput() {
        assertEquals(listOf("pctest", "Usage: … | grep [-v] <pattern>"), run("echo pctest | grep"))
    }

    /** An alias may name another, five deep; the sixth alias in a chain isn't expanded. */
    @Test
    fun aliasesFollowFiveLevels() {
        run("alias pca5 = echo pctest deep")
        for (n in 4 downTo 0) run("alias pca$n = pca${n + 1}")
        assertEquals(listOf("pctest deep x"), run("pca1 x"))
        assertTrue(run("pca0 x").single().startsWith("Command not found: pca5"))
    }

    /** `search` uses the engine search.engine names, or search.url's address; yt, wiki, maps and play go to theirs. */
    @Test
    fun searchOpensTheChosenEngine() {
        fun opens(line: String): String? {
            opened.clear()
            run(line)
            return opened.singleOrNull()?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString
        }
        assertEquals(listOf("Search: pctest cats"), run("search pctest cats"))
        assertEquals("https://duckduckgo.com/?q=pctest%20cats", opened.single().dataString)
        assertEquals(
            "https://duckduckgo.com/?q=pctest%20cats",
            logs.recent(1).single().ctx.values[LogContext.URL]
        )
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

        assertEquals(
            "https://www.youtube.com/results?search_query=pctest%20song",
            opens("yt pctest song")
        )
        assertEquals("https://en.wikipedia.org/w/index.php?search=pctest", opens("wiki pctest"))
        assertEquals(listOf("Maps: pctest place"), run("maps pctest place"))
        assertEquals("geo:0,0?q=pctest%20place", opened.last().dataString)
        assertEquals(listOf("Play Store: pctest game"), run("play pctest game"))
        assertEquals("market://search?q=pctest%20game", opened.last().dataString)

        opened.clear()
        assertEquals(listOf("Usage: search <query>"), run("search"))
        assertEquals(emptyList<Intent>(), opened.toList())
    }

    /** `allow battery`, `system`, `home` and `allow usage` open their pages in Settings and print nothing. */
    @Test
    fun settingsPagesOpen() {
        for ((command, action) in listOf(
            "allow battery" to Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
            "system" to Settings.ACTION_SETTINGS,
            "home" to Settings.ACTION_HOME_SETTINGS,
            "allow usage" to Settings.ACTION_USAGE_ACCESS_SETTINGS,
        )) {
            opened.clear()
            assertEquals(command, emptyList<String>(), run(command))
            assertEquals(command, action, opened.single().action)
        }
    }

    /** Without its accessibility service `lock` says how to allow it; `allow lock` opens that page. */
    @Test
    fun lockWithoutTheServiceSaysHowToAllowIt() {
        assumeTrue("the Play build has no lock", BuildConfig.LOCK_SERVICE)
        assertEquals(emptyList<String>(), run("allow lock"))
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, opened.single().action)
        // With the service on, this would lock the emulator's screen.
        assumeFalse("the screen lock service is on", LockService.isEnabled(context))
        assertEquals(
            listOf("Locking needs an accessibility service\nTap to allow Kolahal screen lock"),
            run("lock")
        )
        assertEquals("allow lock", logs.recent(1).single().ctx.values[LogContext.RUN])
    }

    /**
     * `allow send-notifications` asks for the permission while it's missing; once Android said no, it opens Kolahal's
     * notification settings instead; then it says it's allowed.
     */
    @Test
    fun allowNotifyAsksThenSaysAllowed() {
        // Another test may have answered no already in this process.
        AllowCommands.answered(com.mtwabpin.kolahal.commands.Accesses.SEND_NOTIFICATIONS, granted = true)
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            assertEquals(emptyList<String>(), run("allow send-notifications"))
            assertEquals(1, notificationsAsked)
            assertEquals(emptyList<Intent>(), opened.toList())
            // Android said no: a tap on the line that says so opens the settings, as `allow send-notifications` does now.
            AllowCommands.answered(com.mtwabpin.kolahal.commands.Accesses.SEND_NOTIFICATIONS, granted = false)
            assertEquals(emptyList<String>(), run("allow send-notifications"))
            with(opened.single()) {
                assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, action)
                assertEquals(context.packageName, getStringExtra(Settings.EXTRA_APP_PACKAGE))
            }
            assertEquals(1, notificationsAsked)
            opened.clear()
            // As the app asks for it. Not taken back after: Android stops an app whose permission is revoked.
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
        assertEquals(listOf("Notifications are allowed"), run("allow send-notifications"))
        assertEquals(emptyList<Intent>(), opened.toList())
    }

    /**
     * `allow` lists every access this build has, each with whether it is allowed, and a tap on a line runs
     * `allow <word>`. A word the build leaves out says so; an unknown one says how to list them.
     */
    @Test
    fun allowListsTheAccesses() {
        val offered = com.mtwabpin.kolahal.terminal.SetupChecks.accesses(context)
        val printed = commands.lines("allow")
        assertEquals("Android permissions\nTap one to allow it", printed.first().text)
        val lines = printed.drop(1)
        assertEquals(offered.map { it.access.word }, lines.map { it.text })
        assertEquals(offered.map { it.run }, lines.map { it.ctx.values[LogContext.RUN] })
        for ((line, row) in lines.zip(offered)) {
            val desc = com.mtwabpin.kolahal.data.StyledText.plain(line.ctx.values.getValue(LogContext.DESC))
            assertEquals(desc, (if (row.allowed) "allowed " else "not allowed ") + row.access.title, desc)
            assertEquals(row.access.short, line.ctx.values[LogContext.ARGS])
        }
        assertEquals(emptyList<Intent>(), opened.toList())
        assertEquals(listOf("Unknown access: nope\n'allow' lists them"), run("allow nope"))
        assertEquals("allow", logs.recent(1).single().ctx.copy)
        if (!BuildConfig.CALL_LOG) assertEquals(listOf("This build can't read the call log"), run("allow calls"))
        if (!BuildConfig.LOCK_SERVICE) assertEquals(
            listOf("This build can't lock the screen\nIt has no accessibility service"),
            run("allow lock")
        )
    }

    /** `allow dnd` and `allow settings` open their pages and say so, as `dnd access` and `brightness access` did. */
    @Test
    fun allowDndAndSettingsOpenTheirPages() {
        assertEquals(listOf("Opened Do Not Disturb access"), run("allow dnd"))
        assertEquals(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, opened.single().action)
        opened.clear()
        assertEquals(listOf("Opened modify system settings"), run("allow settings"))
        with(opened.single()) {
            assertEquals(Settings.ACTION_MANAGE_WRITE_SETTINGS, action)
            assertEquals("package:${context.packageName}", dataString)
        }
    }

    /** `= 10 usd in eur` converts at the rates kept from a fetch in the last six hours; a currency they don't have says so. */
    @Test
    fun currencyFromTheKeptRates() {
        fx.set(
            "USD",
            Json.stringify(
                mapOf(
                    "at" to System.currentTimeMillis(),
                    "rates" to mapOf("EUR" to 0.5, "INR" to 88.25)
                )
            )
        )
        var from = logs.maxId()
        run("= 10 usd in eur")
        val line = printed(from, "the conversion") {
            com.mtwabpin.kolahal.data.StyledText.plain(it).startsWith("5 EUR")
        }
        // Big: the result, with the amount it converts under it.
        assertEquals(
            com.mtwabpin.kolahal.data.StyledText.stats(listOf("5 EUR" to "10 USD")),
            line.text
        )
        assertEquals("5", line.ctx.values[LogContext.COPY])
        // Converted off the command's thread, it is still the command's output.
        val command = logs.recent(100)
            .last { it.id > from && it.kind == LogKind.COMMAND && it.text == "= 10 usd in eur" }
        assertEquals(command.id.toString(), line.ctx.values[LogContext.FROM])
        from = logs.maxId()
        run("= 1,000 usd to inr")
        assertEquals(
            "88,250 INR 1,000 USD",
            com.mtwabpin.kolahal.data.StyledText.plain(
                printed(
                    from,
                    "the second conversion"
                ) { com.mtwabpin.kolahal.data.StyledText.plain(it).endsWith(" USD") }.text
            )
        )
        from = logs.maxId()
        run("= 10 usd in xyz")
        assertEquals(
            LogKind.ERROR,
            printed(from, "the unknown currency") { it == "convert: unknown currency XYZ" }.kind
        )
    }

    /** `lua` and `js` without code say how they're used, and code too long for a snippet is refused; `=` with something that isn't arithmetic goes to Lua. */
    @Test
    fun snippets() {
        assertEquals(listOf("Usage: lua <code>"), run("lua"))
        assertEquals(listOf("Usage: js <code>"), run("js"))
        assertEquals(
            listOf("Snippet too long\nUse 'script new' for a longer one"),
            run("lua log('${"x".repeat(8_001)}')")
        )
        val from = logs.maxId()
        run("= string.rep('pcq', 2)")
        printed(from, "Lua's answer", ms = 30_000) { it == "pcqpcq" }
    }

    /** Forgets the unlock counts Kolahal saved for past days ([com.mtwabpin.kolahal.unlock.UnlockCounter.countOrNull]): a test's days are its own. */
    private fun forgetSavedUnlocks() {
        val kept =
            com.mtwabpin.kolahal.data.KvStore(context, com.mtwabpin.kolahal.data.Stores.UNLOCKS)
        val today = LocalDate.now()
        (1L..45L).forEach { kept.set("h:${today.minusDays(it)}", null) }
    }

    /** `unlocks 3` is a line a day, oldest first, counts lined up; without usage access it says it counts only while it runs. */
    @Test
    fun unlocksPerDay() {
        val op =
            Regex("GET_USAGE_STATS: (\\w+)").find(shell("appops get ${context.packageName} GET_USAGE_STATS"))?.groupValues?.get(
                1
            ) ?: "default"
        val today = LocalDate.now()
        val days = listOf(
            DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
                .format(today.minusDays(2)), "Yesterday", "Today"
        )
        val row = Regex("^(\\S.*?)\\s+(\\d+) (unlocks?)$")
        fun check(rows: List<String>) {
            // A table pads its last column to "unlocks", so "1 unlock" ends in a space: no part of the row.
            val parsed =
                rows.map { row.find(it.trimEnd()) ?: throw AssertionError("not a row: '$it'") }
            assertEquals(days, parsed.map { it.groupValues[1] })
            parsed.forEach {
                assertEquals(
                    it.value,
                    it.groupValues[2] == "1",
                    it.groupValues[3] == "unlock"
                )
            }
            assertEquals(
                rows.toString(),
                1,
                rows.map { it.substringBeforeLast(' ').length }.distinct().size
            )
        }
        // Every day has data here: Android's history on the emulator is not the test's to pick. Without
        // usage access, a day has a count when Kolahal counted it while it ran.
        com.mtwabpin.kolahal.usage.UsageHistory.firstDayForTests = { today.minusDays(40) }
        val kept =
            com.mtwabpin.kolahal.data.KvStore(context, com.mtwabpin.kolahal.data.Stores.UNLOCKS)
        val counted = (1L..2L).map { "d:${today.minusDays(it)}" }.associateWith { kept.get(it) }
        counted.forEach { (key, was) -> if (was == null) kept.set(key, "0") }
        try {
            shell("appops set ${context.packageName} GET_USAGE_STATS ignore")
            val without = run("unlocks 3")
            assertEquals(
                listOf(
                    "Counting unlocks only while Kolahal runs",
                    "Tap to allow usage access for exact counts"
                ), without.take(2)
            )
            assertEquals(
                "allow usage",
                logs.recent(10).single { it.text == without[1] }.ctx.values[LogContext.RUN]
            )
            // Big numbers first: today, a day's average, the total.
            assertTrue(
                without[2],
                Regex("\\d+ Unlocks? today · \\d+ A day · \\d+ In 3 days").matches(
                    com.mtwabpin.kolahal.data.StyledText.plain(without[2])
                )
            )
            check(without.drop(3))

            shell("appops set ${context.packageName} GET_USAGE_STATS allow")
            check(run("unlocks 3").drop(1))
            assertEquals(31, run("unlocks 99").size)
            // One day: its count, large, and no bar.
            assertTrue(
                Regex("\\d+ Unlocks? today").matches(
                    com.mtwabpin.kolahal.data.StyledText.plain(
                        run("unlocks 0").single()
                    )
                )
            )
        } finally {
            shell("appops set ${context.packageName} GET_USAGE_STATS $op")
            com.mtwabpin.kolahal.usage.UsageHistory.firstDayForTests = null
            counted.forEach { (key, was) -> kept.set(key, was) }
            forgetSavedUnlocks()
        }
    }

    /**
     * `unlocks` against the daily limit (`usage.unlock-limit`): under it, the count as always; at it
     * and over it, the count in the warning color with a line that says so; from one and a half times
     * it, in the error color. Without usage access, today's count is the one Kolahal counted, set here
     * to 12.
     */
    @Test
    fun unlocksAgainstTheDailyLimit() {
        val op =
            Regex("GET_USAGE_STATS: (\\w+)").find(shell("appops get ${context.packageName} GET_USAGE_STATS"))?.groupValues?.get(
                1
            ) ?: "default"
        val kept =
            com.mtwabpin.kolahal.data.KvStore(context, com.mtwabpin.kolahal.data.Stores.UNLOCKS)
        val key = "d:${LocalDate.now()}"
        val was = kept.get(key)
        // The count's color, in the line of big numbers after the two notes about usage access.
        fun color(lines: List<String>) =
            com.mtwabpin.kolahal.data.StyledText.lines(lines[2]).single().stats!!.first().value.single().color
        try {
            shell("appops set ${context.packageName} GET_USAGE_STATS ignore")
            kept.set(key, "12")
            config.set(Keys.USAGE_UNLOCK_LIMIT, "13")
            val under = run("unlocks")
            assertEquals(under.toString(), 3, under.size)
            assertEquals(null, color(under))
            assertEquals(
                "12 Unlocks today",
                com.mtwabpin.kolahal.data.StyledText.plain(under[2])
            )

            config.set(Keys.USAGE_UNLOCK_LIMIT, "12")
            val at = run("unlocks")
            assertEquals(com.mtwabpin.kolahal.data.StyledText.ColorRole.WARN, color(at))
            assertEquals("At your limit of 12", at[3])

            config.set(Keys.USAGE_UNLOCK_LIMIT, "10")
            val over = run("unlocks")
            assertEquals(com.mtwabpin.kolahal.data.StyledText.ColorRole.WARN, color(over))
            assertEquals("2 over your limit of 10", over[3])

            config.set(Keys.USAGE_UNLOCK_LIMIT, "8")
            val farOver = run("unlocks")
            assertEquals(com.mtwabpin.kolahal.data.StyledText.ColorRole.BAD, color(farOver))
            assertEquals("4 over your limit of 8", farOver[3])

            // No limit: as it always is.
            config.set(Keys.USAGE_UNLOCK_LIMIT, "0")
            val none = run("unlocks")
            assertEquals(3, none.size)
            assertEquals(null, color(none))
        } finally {
            shell("appops set ${context.packageName} GET_USAGE_STATS $op")
            kept.set(key, was)
        }
    }

    /**
     * A day before Android's usage history begins is left out: not a row, not in the average. Here the
     * history begins yesterday, so `unlocks 7` has two days.
     */
    @Test
    fun unlocksLeavesOutDaysWithNoData() {
        val op =
            Regex("GET_USAGE_STATS: (\\w+)").find(shell("appops get ${context.packageName} GET_USAGE_STATS"))?.groupValues?.get(
                1
            ) ?: "default"
        val today = LocalDate.now()
        forgetSavedUnlocks()
        com.mtwabpin.kolahal.usage.UsageHistory.firstDayForTests = { today.minusDays(1) }
        try {
            shell("appops set ${context.packageName} GET_USAGE_STATS allow")
            val lines = run("unlocks 7")
            assertTrue(
                lines[0],
                Regex("\\d+ Unlocks? today · \\d+ A day · \\d+ In 2 days").matches(
                    com.mtwabpin.kolahal.data.StyledText.plain(lines[0])
                )
            )
            assertEquals(
                listOf("Yesterday", "Today"),
                lines.drop(1).dropLast(1).map { it.substringBefore("  ").trim() })
            // And why there are only two.
            assertEquals("2 of 7 days", lines.last())
            // Scripts get the same days.
            assertEquals(2, com.mtwabpin.kolahal.unlock.UnlockCounter.get(context).history(7).size)
        } finally {
            shell("appops set ${context.packageName} GET_USAGE_STATS $op")
            com.mtwabpin.kolahal.usage.UsageHistory.firstDayForTests = null
        }
    }

    /**
     * `unlocks month`: today, the average and the total, then a column a day and a line a week. With a
     * history of 9 days, only those: 9 columns, and a line that says why.
     */
    @Test
    fun unlocksMonthIsAChart() {
        val op =
            Regex("GET_USAGE_STATS: (\\w+)").find(shell("appops get ${context.packageName} GET_USAGE_STATS"))?.groupValues?.get(
                1
            ) ?: "default"
        val today = LocalDate.now()
        try {
            shell("appops set ${context.packageName} GET_USAGE_STATS allow")
            forgetSavedUnlocks()
            com.mtwabpin.kolahal.usage.UsageHistory.firstDayForTests = { today.minusDays(40) }
            val month = run("unlocks month")
            val plain = month.map { com.mtwabpin.kolahal.data.StyledText.plain(it) }
            assertTrue(
                plain[0],
                Regex("\\d+ Unlocks? today · \\d+ A day · [\\d,]+ In 30 days").matches(plain[0])
            )
            val chart = com.mtwabpin.kolahal.data.StyledText.lines(month.single {
                com.mtwabpin.kolahal.data.StyledText.lines(it).any { l -> l.columns != null }
            }).single().columns!!
            assertEquals(30, chart.heights.size)
            assertTrue(plain.toString(), "── by week ──" in plain)
            // This week has finished days, except on a Monday.
            if (today.dayOfWeek != java.time.DayOfWeek.MONDAY) assertTrue(
                plain.toString(),
                plain.any { it.startsWith("This week") })
            assertTrue(plain.toString(), plain.none { it.endsWith("from now on") })

            forgetSavedUnlocks()
            com.mtwabpin.kolahal.usage.UsageHistory.firstDayForTests = { today.minusDays(8) }
            val short = run("unlocks month").map { com.mtwabpin.kolahal.data.StyledText.plain(it) }
            assertTrue(short[0], short[0].endsWith("In 9 days"))
            assertEquals("9 of 30 days", short.last())
        } finally {
            shell("appops set ${context.packageName} GET_USAGE_STATS $op")
            com.mtwabpin.kolahal.usage.UsageHistory.firstDayForTests = null
            forgetSavedUnlocks()
        }
    }

    /** `crashes share` hands the newest report, or the one named, to the share sheet. */
    @Test
    fun crashesShareSendsTheReport() {
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
            "crashes share" to "pctest boom",
            "crashes share 1" to "pctest boom",
            "crashes share 20260101-120000" to "pctest boom",
            "crashes share 2" to "pctest older",
            "crashes share 20251231-120000" to "pctest older",
        )
        for ((line, sent) in shares) {
            opened.clear()
            assertEquals(line, emptyList<String>(), run(line))
            val chooser = opened.single()
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val send =
                IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)!!
            assertEquals(Intent.ACTION_SEND, send.action)
            assertTrue(
                line,
                send.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                    .contains("IllegalStateException: $sent")
            )
        }
    }

    /** A word these commands don't take says how they're used. */
    @Test
    fun wrongWordsSayTheUsage() {
        // The usage lines help shows: what a wrong word says can't drift from them.
        assertEquals(listOf("Usage: log [only <name|kind>|all]"), run("log bogus"))
        assertEquals(
            listOf("Usage: backup [now|every <daily|weekly|off>|dir]"),
            run("backup bogus")
        )
        assertEquals(listOf("Usage: restart [scripts]"), run("restart bogus"))
        assertEquals(listOf("Usage: clear [history|all]"), run("clear bogus"))
        assertEquals(listOf("Usage: widget [add|ls|page [<page>]]"), run("widget bogus"))
        assertEquals(listOf("Usage: widget [add|ls|page [<page>]]"), run("widgets bogus"))
        if (BuildConfig.SELF_UPDATE) assertEquals(
            listOf("Usage: update [install]"),
            run("update bogus")
        )
        assertEquals(emptyList<Screen>(), screens.toList())
    }

    /** With no backup folder yet, `backup` asks for one and backs up once it's picked; `backup dir` only picks it. */
    @Test
    fun backupWithoutAFolderAsksForOne() {
        assumeTrue("a backup folder is set", AutoBackup.folder(context) == null)
        assertEquals(
            listOf("Pick a folder for backups\nThe backup is written there"),
            run("backup")
        )
        assertEquals(listOf(true), backupFolderAsked.toList())
        assertEquals(emptyList<String>(), run("backup dir"))
        assertEquals(emptyList<String>(), run("backup folder"))
        assertEquals(listOf(true, false, false), backupFolderAsked.toList())
    }

    /** `backup` to a folder Kolahal can no longer write says so under the command, and doesn't ask for another. */
    @Test
    fun backupToAFolderThatCantBeWrittenSaysSo() {
        val autoBackup = context.getSharedPreferences("auto_backup", Context.MODE_PRIVATE)
        val folderWas = autoBackup.getString("folder", null)
        try {
            // A folder whose grant is gone.
            autoBackup.edit(commit = true) {
                putString(
                    "folder",
                    "content://com.android.externalstorage.documents/tree/primary%3AKolahalTestBackups"
                )
            }
            val from = logs.maxId()
            processor.run("backup")
            val failed = printed(from, "the failure") { it.startsWith("backup: ") }
            assertEquals(
                "backup: the backup folder can't be written: run 'backup dir'",
                failed.text
            )
            assertEquals(LogKind.ERROR, failed.kind)
            assertEquals(emptyList<Boolean>(), backupFolderAsked.toList())
        } finally {
            autoBackup.edit(commit = true) {
                if (folderWas != null) putString(
                    "folder",
                    folderWas
                ) else remove("folder")
            }
        }
    }

    /**
     * `widgets` alone opens Arrange widgets, and `widget add` (or `widgets add`) the picker. With none
     * placed, `widget` alone, `widget ls` and `widgets ls` say how to add one.
     */
    @Test
    fun widgetsOpenTheirPages() {
        assertEquals(emptyList<String>(), run("widgets"))
        assertEquals(emptyList<String>(), run("widget add"))
        assertEquals(emptyList<String>(), run("widgets add"))
        assertEquals(
            listOf(Screen.WIDGETS, Screen.WIDGET_PICKER, Screen.WIDGET_PICKER),
            screens.toList()
        )
        assumeTrue("widgets are placed", WidgetHost.get(context).listing(context).isEmpty())
        for (typed in listOf("widget", "widget ls", "widgets ls")) {
            assertEquals(typed, listOf("No widgets yet\nTap to open the widget picker"), run(typed))
            assertEquals(typed, "widget add", logs.recent(1).single().ctx.values[LogContext.RUN])
        }
        assertEquals(3, screens.size)
    }

    /**
     * `widget page` (and `widgets page`) turns home to the widgets page and opens no screen. With the
     * page off, it says so, and a tap on the line puts the page on the left.
     */
    @Test
    fun widgetsPageTurnsHomeToIt() {
        try {
            config.set(Keys.WIDGETS_PAGE, "right")
            assertEquals(emptyList<String>(), run("widget page"))
            assertEquals(1, widgetsPageTurns)
            assertEquals(emptyList<String>(), run("widgets page"))
            assertEquals(2, widgetsPageTurns)
            config.set(Keys.WIDGETS_PAGE, "off")
            assertEquals(listOf("The widgets page is off\nTap to turn it on"), run("widget page"))
            with(logs.recent(1).single()) {
                assertEquals(LogKind.ERROR, kind)
                assertEquals("config set widgets.page left", ctx.values[LogContext.RUN])
            }
            assertEquals(2, widgetsPageTurns)
            assertEquals(emptyList<Screen>(), screens.toList())
        } finally {
            config.reset(Keys.WIDGETS_PAGE)
        }
    }

    /** `widget ls` numbers each widget, with its state, size and app beside it; a tap opens Arrange widgets. `widgets ls` says the same. */
    @Test
    fun widgetsLsListsEachWidget() {
        val widgets = TestWidgets()
        try {
            assumeTrue("widgets are placed", widgets.host.listing(context).isEmpty())
            val serial = context.getSystemService(UserManager::class.java)
                .getSerialNumberForUser(Process.myUserHandle())
            // One from a backup, waiting to be added again: no binding needed.
            widgets.restore(
                "com.example.pctest/com.example.pctest.ClockWidget",
                serial,
                height = 120
            )
            assertEquals(listOf("1. ClockWidget"), run("widgets ls"))
            assertEquals(listOf("1. ClockWidget"), run("widget ls"))
            val line = logs.recent(1).single()
            assertEquals(
                "Pending re-add · Full × 120dp · com.example.pctest",
                line.ctx.values[LogContext.DESC]
            )
            assertEquals("widgets", line.ctx.values[LogContext.RUN])
        } finally {
            widgets.close()
        }
    }

    /** `clear all` deletes the whole log, and ↑ history with it. */
    @Test
    fun clearAllEmptiesTheLog() {
        logKept.keep()
        run("echo pctest-one")
        assertTrue(logs.commandHistory().contains("echo pctest-one"))
        val upTo = logs.maxId()
        assertEquals(emptyList<String>(), run("clear all"))
        // Ids are never reused: anything at or below it is a line from before. Deleted, not hidden.
        assertEquals(emptyList<LogEntry>(), logs.recent(400).filter { it.id <= upTo })
        assertTrue(
            Db.get(context).readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM logs WHERE id <= ?",
                arrayOf(upTo.toString())
            ).use { it.moveToFirst(); it.getInt(0) == 0 })
        assertEquals(emptyList<String>(), logs.commandHistory())
    }

    /** `restart scripts` stops the process scripts run in. */
    @Test
    fun restartScriptsStopsTheScriptProcess() {
        val am = context.getSystemService(ActivityManager::class.java)
        fun scriptPid() = am.runningAppProcesses.orEmpty()
            .firstOrNull { it.processName == "${context.packageName}:script" }?.pid

        val from = logs.maxId()
        run("lua log('pctest-up')")
        printed(from, "the snippet's line", ms = 30_000) { it == "pctest-up" }
        // Its run over, so stopping the process can't cut it short (and have it run again later).
        val work = WorkManager.getInstance(context)
        waitUntil("the snippet's run finished") {
            work.getWorkInfosByTag(ScriptWorker::class.java.name).get()
                .none { it.state == WorkInfo.State.RUNNING }
        }
        val pid = scriptPid()
        assertNotNull("the script process runs", pid)
        assertEquals(
            listOf("Script process stopped\nScheduled scripts will start it again"),
            run("restart scripts")
        )
        waitUntil("process $pid gone") { scriptPid() != pid }
    }

    /** `help` lists the scripts that are commands under a heading of their own, each with its usage and description; `help <name>` shows that one. */
    @Test
    fun helpListsScriptCommands() {
        scripts.create(
            "pctestcmd",
            ScriptLang.LUA,
            "-- @command\n-- @desc Says hello\n-- @usage pctestcmd <name>\nlog('hi ' .. argline)"
        )
        scripts.create("pctestbare", ScriptLang.LUA, "-- @command\nlog('hi')")
        run("help")
        val help = logs.recent(400).filter { it.id > logLines.start && it.kind != LogKind.COMMAND }
        val start = help.indexOfFirst {
            com.mtwabpin.kolahal.data.StyledText.plain(it.text)
                .startsWith("Your script commands · ")
        }
        assertTrue(help.map { it.text }.toString(), start >= 0)
        val mine = help.drop(start + 1)
            .filter { it.ctx.values[LogContext.SCRIPT]?.startsWith("pctest") == true }
        // The name on the left; @desc beside it and @usage (without the name) muted under it.
        assertEquals(listOf("pctestcmd", "pctestbare"), mine.map { it.text }.sortedDescending())
        assertEquals(
            "Says hello",
            mine.single { it.text == "pctestcmd" }.ctx.values[LogContext.DESC]
        )
        assertEquals("<name>", mine.single { it.text == "pctestcmd" }.ctx.values[LogContext.ARGS])
        assertEquals(
            "lua script",
            mine.single { it.text == "pctestbare" }.ctx.values[LogContext.DESC]
        )
        assertEquals(null, mine.single { it.text == "pctestbare" }.ctx.values[LogContext.ARGS])

        assertEquals(listOf("pctestcmd"), run("help pctestcmd"))
        assertEquals("Says hello", logs.recent(1).single().ctx.values[LogContext.DESC])
    }
}
