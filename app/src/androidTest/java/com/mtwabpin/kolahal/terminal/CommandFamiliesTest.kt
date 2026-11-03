package com.mtwabpin.kolahal.terminal

import android.app.WallpaperManager
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import java.io.File
import com.mtwabpin.kolahal.ui.WallpaperMatch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** One test per command family: what each prints and what it changes. */
@RunWith(AndroidJUnit4::class)
class CommandFamiliesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)
    private val config = Config.get(context)
    private val prefs = AppPrefs.get(context)
    private val apps = AppRepository.get(context)
    private var cleared = 0

    private val ui = object : TerminalUi {
        override fun pickScriptFolder() = Unit
        override fun requestFontImport() = Unit
        override fun openScreen(screen: Screen) = Unit
        override fun requestContactsPermission() = Unit
        override fun openEditor(script: Script) = Unit
        override fun askSecret(script: Script, name: String) = Unit
        override fun requestNotificationPermission() = Unit
        override fun clearScreen() { cleared++ }
    }
    private val processor = CommandProcessor(context, ui)
    private val crashDir = File(context.filesDir, "crash")

    @Before fun setUp() {
        clearMine()
        apps.refresh()
        val deadline = System.currentTimeMillis() + 5_000
        while (apps.apps.value.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
    }

    @After fun tearDown() {
        prefs.setAlias("tt", null)
        Keys.ALL.forEach(config::reset)
        crashDir.deleteRecursively()
        clearMine()
    }

    /** This class's todos, notes and reminders (each says cptest), and the reminders' alarms: a test that fails leaves none behind. */
    private fun clearMine() {
        val todos = com.mtwabpin.kolahal.productivity.TodoStore.get(context)
        todos.all().filter { "cptest" in it.text }.forEach { todos.remove(it.id) }
        val notes = com.mtwabpin.kolahal.productivity.NoteStore.get(context)
        notes.all().filter { "cptest" in it.text }.forEach { notes.remove(it.id) }
        val reminders = com.mtwabpin.kolahal.productivity.ReminderStore.get(context)
        reminders.all().filter { "cptest" in it.text }.forEach {
            com.mtwabpin.kolahal.productivity.ReminderScheduler.cancel(context, it.id)
            reminders.remove(it.id)
        }
    }

    /** Runs [line] and returns the non-command lines it printed. */
    private fun run(line: String): List<String> {
        val before = logs.recent(1).lastOrNull()?.id ?: 0
        processor.run(line)
        return logs.recent(400).filter { it.id > before && it.kind != LogKind.COMMAND }.map { it.text }
    }

    @Test fun helpListsEveryCommandAndPipesFilterIt() {
        val help = run("help")
        for (spec in CommandCatalog.COMMANDS) assertTrue("${spec.usage} missing from help", spec.usage in help)
        val grepped = run("help | grep crashes")
        assertTrue(grepped.toString(), grepped.isNotEmpty() && grepped.all { it.contains("crashes", ignoreCase = true) })
        assertEquals(2, run("help | grep -v zzz | head 2").size)
    }

    /** `help todo` is that command alone; a synonym finds its command, a near miss the nearest ones. */
    @Test fun helpForOneCommand() {
        val todo = CommandCatalog.COMMANDS.first { it.name == "todo" }
        val one = run("help todo")
        assertEquals(todo.usage, one.first())
        assertTrue(one.toString(), one.size <= 3)
        assertTrue(run("help cls").let { it.first() == CommandCatalog.COMMANDS.first { c -> c.name == "clear" }.usage && "Also known as cls" in it })
        assertTrue(run("help tod").toString(), todo.usage in run("help tod"))
        assertTrue(run("help zzzzzz").single().startsWith("No command called 'zzzzzz'"))
    }

    /** Arithmetic is answered at once, as a plain number; what has no answer says why. */
    @Test fun calculatorAnswersHere() {
        assertEquals(listOf("1024"), run("= 2^10"))
        assertEquals(listOf("12"), run("= 15% of 80"))
        assertEquals(listOf("1/0: can't divide by zero"), run("= 1/0"))
    }

    @Test fun unitConversion() {
        val out = run("= 5 km in mi")
        assertTrue(out.toString(), out.single().contains("3.1"))
    }

    @Test fun todoAddDoneClear() {
        assertTrue(run("todo cptest milk").any { it.endsWith("[ ] cptest milk") })
        assertTrue(run("todo").any { it.endsWith("[ ] cptest milk") })
        run("todo done 1")
        assertTrue(run("todo").single().startsWith("Nothing open"))
        assertEquals(listOf("Cleared 1 finished todo"), run("todo clear"))
        assertTrue(run("todo").single().startsWith("No todos"))
    }

    @Test fun notesAddFindRemove() {
        assertTrue(run("note cptest idea").single().startsWith("Note saved"))
        assertTrue(run("notes").any { it.contains("cptest idea") })
        assertTrue(run("notes grep cptest").any { it.contains("cptest idea") })
        assertTrue(run("notes rm 1").single().contains("cptest idea"))
        assertTrue(run("notes").single().startsWith("No notes"))
    }

    @Test fun remindListCancel() {
        assertTrue(run("remind 10m cptest tea").first().contains("cptest tea"))
        assertTrue(run("reminders").any { it.contains("cptest tea") })
        assertEquals("Cancelled reminder: cptest tea", run("remind rm 1").single())
        assertTrue(run("reminders").single().startsWith("No reminders"))
        assertTrue(run("remind someday x").single().startsWith("Usage: remind"))
    }

    /** A time typed for before 6 AM sets nothing until it's tapped, and says what else it could mean. */
    @Test fun aTimeBeforeSixAsksFirst() {
        val store = com.mtwabpin.kolahal.productivity.ReminderStore.get(context)
        fun mine() = store.all().filter { "cptest" in it.text }
        fun time(r: com.mtwabpin.kolahal.productivity.Reminder) =
            java.time.Instant.ofEpochMilli(r.at).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        fun cancelAll() = mine().forEach {
            com.mtwabpin.kolahal.productivity.ReminderScheduler.cancel(context, it.id)
            store.remove(it.id)
        }
        /** What tapping the newest line starting with [start] runs. */
        fun tap(start: String) = run(logs.recent(20).last { it.text.startsWith(start) }.ctx.values.getValue(com.mtwabpin.kolahal.data.LogContext.RUN))
        try {
            val asked = run("remind tomorrow 2 cptest tickets")
            assertEquals(asked.toString(), 3, asked.size)
            assertTrue(asked[0], asked[0].startsWith("Not set yet, as tomorrow ") && asked[0].endsWith(" is before 6 AM: cptest tickets"))
            assertTrue(asked[1], asked[1].startsWith("Tap to set it for tomorrow "))
            assertTrue(asked[2], asked[2].startsWith("Or tap to set it for tomorrow ") && asked[2].endsWith(": 2 cptest tickets"))
            assertEquals(emptyList<Any>(), mine())

            // Either reading, and tapped twice it's still one reminder.
            assertTrue(tap("Tap to set it").first().startsWith("Reminder set for tomorrow "))
            assertEquals("cptest tickets", mine().single().text)
            assertEquals(java.time.LocalTime.of(2, 0), time(mine().single()))
            assertEquals(listOf("Already set: cptest tickets"), tap("Tap to set it"))
            assertEquals(1, mine().size)
            cancelAll()
            assertTrue(tap("Or tap to set it").first().startsWith("Reminder set for tomorrow "))
            assertEquals("2 cptest tickets", mine().single().text)
            assertEquals(java.time.LocalTime.of(9, 0), time(mine().single()))
            cancelAll()

            // Any time typed for then, with nothing else to offer when it says what it means.
            for (typed in listOf("5:59", "at 2", "tomorrow at 2", "12am", "tomorrow 5am")) {
                assertEquals(typed, 2, run("remind $typed cptest x").size)
                assertEquals(typed, emptyList<Any>(), mine())
            }
            run("remind every day 2:30 cptest pills").let { assertTrue(it.toString(), it[1].endsWith(", then every day")) }
            assertEquals(emptyList<Any>(), mine())
            assertTrue(tap("Tap to set it").first().contains("then every day: cptest pills"))
            assertEquals("day", mine().single().repeat)
            assertEquals(listOf("Already set: cptest pills"), tap("Tap to set it"))
            cancelAll()

            // From 6 on, a duration, or --yes: it's set at once.
            for (typed in listOf("6:00", "tomorrow 9", "10m", "in 2h", "--yes 5:00", "--yes tomorrow 2")) {
                assertTrue(typed, run("remind $typed cptest x").first().startsWith("Reminder set for"))
                assertEquals(typed, 1, mine().size)
                cancelAll()
            }
        } finally {
            cancelAll()
        }
    }

    /** A repeating reminder is set again for its next time when it fires; Snooze adds a one-off copy. */
    @Test fun aRepeatingReminderComesBack() {
        val store = com.mtwabpin.kolahal.productivity.ReminderStore.get(context)
        fun mine() = store.all().filter { it.text == "cptest pills" }
        fun waitFor(what: String, ok: () -> Boolean) {
            val until = System.currentTimeMillis() + 10_000
            while (!ok() && System.currentTimeMillis() < until) Thread.sleep(100)
            assertTrue(what, ok())
        }
        try {
            assertTrue(run("remind every day 8:00 cptest pills").first().contains("then every day: cptest pills"))
            val r = mine().single()
            assertEquals("day", r.repeat)
            assertEquals("08:00", r.time)
            assertTrue(run("reminders").toString(), logs.recent(20).any { it.ctx.values[com.mtwabpin.kolahal.data.LogContext.DESC]?.endsWith("every day") == true })

            context.sendBroadcast(
                android.content.Intent(context, com.mtwabpin.kolahal.productivity.ReminderReceiver::class.java)
                    .putExtra(com.mtwabpin.kolahal.productivity.ReminderScheduler.EXTRA_ID, r.id),
            )
            waitFor("set for the next day") { mine().singleOrNull()?.at?.let { it > r.at } == true }
            val next = java.time.Instant.ofEpochMilli(mine().single().at).atZone(java.time.ZoneId.systemDefault())
            assertEquals(java.time.LocalTime.of(8, 0), next.toLocalTime())

            context.sendBroadcast(
                android.content.Intent(context, com.mtwabpin.kolahal.productivity.ReminderActionReceiver::class.java)
                    .setAction(com.mtwabpin.kolahal.productivity.ReminderActionReceiver.ACTION_SNOOZE)
                    .putExtra(com.mtwabpin.kolahal.productivity.ReminderScheduler.EXTRA_ID, r.id)
                    .putExtra(com.mtwabpin.kolahal.productivity.ReminderActionReceiver.EXTRA_TEXT, "cptest pills")
                    .putExtra(com.mtwabpin.kolahal.productivity.ReminderActionReceiver.EXTRA_MINUTES, 10),
            )
            waitFor("a snoozed copy") { mine().size == 2 }
            val snoozed = mine().single { it.repeat == null }
            assertTrue(kotlin.math.abs(snoozed.at - (System.currentTimeMillis() + 600_000)) < 60_000)
        } finally {
            mine().forEach { com.mtwabpin.kolahal.productivity.ReminderScheduler.cancel(context, it.id); store.remove(it.id) }
        }
    }

    /** `script set` lists a script's options, changes one, and puts its default back. */
    @Test fun scriptSetChangesAnOption() {
        val repo = com.mtwabpin.kolahal.data.ScriptRepository.get(context)
        val s = repo.create("cptestopt", com.mtwabpin.kolahal.data.ScriptLang.LUA, "-- @option city London The city\nreturn 1")
        try {
            assertTrue(run("script set cptestopt").toString(), run("script set cptestopt").any { it.startsWith("city") && it.endsWith("London (default)") })
            assertEquals(listOf("cptestopt: city = New York"), run("script set cptestopt city New York"))
            assertTrue(run("script set cptestopt").any { it.startsWith("city") && it.endsWith("New York") })
            assertEquals(listOf("cptestopt: city reset to default, London"), run("script set cptestopt city --reset"))
            assertTrue(run("script set cptestopt town x").single().startsWith("cptestopt has no @option town"))
        } finally {
            repo.delete(s.id)
        }
    }

    /** `backup every` sets automatic backups, which schedule themselves, and asks for a folder when there is none. */
    @Test fun backupEverySchedulesBackups() {
        val wm = androidx.work.WorkManager.getInstance(context)
        fun state() = wm.getWorkInfosForUniqueWork("auto-backup").get().lastOrNull()?.state
        fun waitFor(ms: Long = 5_000, check: () -> Boolean): Boolean {
            val until = System.currentTimeMillis() + ms
            while (!check() && System.currentTimeMillis() < until) Thread.sleep(50)
            return check()
        }
        // The first backup starts by itself, at any moment from here on. With no folder it fails, and says
        // so in the log: not a line of the command this test ran.
        val failed = "Automatic backup failed"
        fun said(line: String) = run(line).filterNot { it.startsWith(failed) }
        val before = logs.maxId()
        try {
            assertTrue(said("backup every weekly").first().startsWith("Backing up weekly to "))
            assertEquals("weekly", config[Keys.BACKUP_AUTO])
            val scheduled = setOf(androidx.work.WorkInfo.State.ENQUEUED, androidx.work.WorkInfo.State.RUNNING)
            assertTrue("scheduled: ${state()}", waitFor { state() in scheduled })
            // Over and done with, so its line can't turn up in the next test's output.
            waitFor(ms = 3_000) { logs.recent(50).any { it.id > before && it.text.startsWith(failed) } }
            assertEquals(listOf("Usage: backup every daily|weekly|off"), said("backup every hourly"))
            assertEquals(listOf("Automatic backups off"), said("backup every off"))
            assertTrue("cancelled: ${state()}", waitFor { state() == androidx.work.WorkInfo.State.CANCELLED })
        } finally {
            config.reset(Keys.BACKUP_AUTO)
        }
    }

    @Test fun sourceOpensGitHub() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val opened = mutableListOf<Intent>()
        val browser = object : android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): android.app.Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_VIEW) return null
                opened += intent
                return android.app.Instrumentation.ActivityResult(0, null)
            }
        }
        instrumentation.addMonitor(browser)
        try {
            assertEquals(listOf("Opened https://github.com/mtwabpin/kolahal"), run("source"))
            assertEquals("https://github.com/mtwabpin/kolahal", opened.single().dataString)
        } finally {
            instrumentation.removeMonitor(browser)
        }
        assertTrue(run("help").any { it.contains("source") })
    }

    /** It says whose the wallpaper is, asks before replacing one, and doesn't ask twice. */
    @Test fun wallpaperCommand() {
        val wallpapers = WallpaperManager.getInstance(context)
        val system = WallpaperManager.FLAG_SYSTEM
        WallpaperMatch.forget(context)
        try {
            assertTrue(run("wallpaper").single().startsWith("Your home screen wallpaper is your own"))
            assertEquals(listOf("Usage: wallpaper [match]"), run("wallpaper bogus"))

            val before = wallpapers.getWallpaperId(system)
            val asked = run("wallpaper match")
            assertTrue(asked.toString(), asked.first().contains("can't restore the old one"))
            assertEquals("Tap to replace it", asked.last())
            assertEquals("wallpaper match --yes", logs.recent(1).last().ctx.values[LogContext.RUN])
            assertEquals("only asked so far", before, wallpapers.getWallpaperId(system))

            assertTrue(run("wallpaper match --yes").single().startsWith("Home screen wallpaper set"))
            assertNotEquals(before, wallpapers.getWallpaperId(system))
            assertTrue(run("wallpaper").single().contains("follows the theme"))
            val ours = wallpapers.getWallpaperId(system)
            assertEquals(listOf("Your home screen wallpaper already follows the theme"), run("wallpaper match"))
            assertEquals(ours, wallpapers.getWallpaperId(system))
        } finally {
            WallpaperMatch.forget(context)
            wallpapers.clear(system or WallpaperManager.FLAG_LOCK)
        }
    }

    /**
     * `calls` prints call lines, as the ones logged by themselves are, so they stay out of backups;
     * the newest call first.
     */
    @Test fun callsPrintsCallLines() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        val older = "5550142"
        val newer = "5550143"
        shell("pm grant ${context.packageName} android.permission.READ_CALL_LOG")
        val now = System.currentTimeMillis()
        shell("content insert --uri content://call_log/calls --bind number:s:$older --bind type:i:1 --bind date:l:${now - 60_000} --bind duration:l:5")
        shell("content insert --uri content://call_log/calls --bind number:s:$newer --bind type:i:2 --bind date:l:$now --bind duration:l:7")
        val before = logs.maxId()
        try {
            processor.run("calls 5")
            val lines = logs.recent(50).filter { it.id > before && it.kind != LogKind.COMMAND }
            val numbers = lines.mapNotNull { it.ctx.values[LogContext.PHONE] }
            assertTrue(lines.toString(), older in numbers && newer in numbers)
            assertTrue(numbers.toString(), numbers.indexOf(newer) < numbers.indexOf(older))
            assertTrue(lines.toString(), lines.all { it.source == com.mtwabpin.kolahal.calls.CallEvents.SOURCE })
        } finally {
            logs.recent(50).filter { it.id > before }.forEach { logs.delete(it.id) }
            shell("content delete --uri content://call_log/calls --where \"number='$older' OR number='$newer'\"")
        }
    }

    @Test fun crashesListAndClear() {
        assertTrue(run("crashes").single().startsWith("No crash reports"))
        crashDir.mkdirs()
        File(crashDir, "20260101-120000-crash.txt").writeText(
            "Kolahal crash report\nKolahal 0.0 (1, debug)\nAndroid\nWhen: x\nWhat: crash in test, thread main\n\njava.lang.IllegalStateException: boom\n\tat a.b(c:1)\n",
        )
        val listed = run("crashes")
        assertTrue(listed.toString(), listed.first().startsWith("#1 crash") && listed.first().endsWith("IllegalStateException: boom"))
        assertTrue(run("crashes share 5").single().startsWith("No crash report 5"))
        assertTrue(run("crashes bogus").single().startsWith("Usage: crashes"))
        assertEquals(listOf("Deleted 1 crash report"), run("crashes clear"))
        assertFalse(crashDir.listFiles().orEmpty().any())
    }

    @Test fun configGetSetReset() {
        assertEquals(listOf("font.size = 14"), run("config get font.size"))
        run("config set clock.24h on")
        assertTrue(config[Keys.CLOCK_24H])
        run("config reset clock.24h")
        assertFalse(config[Keys.CLOCK_24H])
        assertTrue(run("config set no.such.key 1").single().startsWith("Unknown setting"))
    }

    @Test fun fontSansSetsTheMode() {
        assertEquals(listOf("font.sans = prompt"), run("font sans prompt"))
        assertEquals("prompt", config[Keys.FONT_SANS])
        assertTrue(run("font sans bogus").single().startsWith("Invalid value"))
        assertEquals("prompt", config[Keys.FONT_SANS])
        assertTrue(run("font sans").single().startsWith("font.sans = prompt"))
        assertTrue(run("font").any { it.startsWith("System font: prompt") })
    }

    @Test fun historyAndClear() {
        run("echo cptest-one")
        assertTrue(run("history").contains("echo cptest-one"))
        assertEquals(listOf("Command history cleared"), run("clear history"))
        assertFalse(run("history").contains("echo cptest-one"))
        val before = cleared
        run("clear")
        assertEquals(before + 1, cleared)
    }

    @Test fun aliasesAreListed() {
        run("alias tt = echo cptest")
        assertTrue(run("alias").any { it.contains("tt") && it.contains("echo cptest") })
    }

    @Test fun pinAndUnpinApp() {
        val app = apps.apps.value.first { it.profile.kind == ProfileKind.PERSONAL }
        try {
            run("pin ${app.label}")
            assertTrue(prefs.isPinned(app.key))
            run("unpin ${app.label}")
            assertFalse(prefs.isPinned(app.key))
        } finally {
            prefs.setPinned(app.key, false)
        }
    }

    @Test fun setupTextAndDeviceCommands() {
        val setup = run("setup text")
        assertTrue(setup.toString(), setup.first().startsWith("Setup") && setup.size > 1 && setup.drop(1).all { it.startsWith("[") })
        assertTrue(run("date").single().isNotBlank())
        // The time elsewhere: each place on a line of its own, the detail in its second column.
        assertEquals(listOf("Tokyo", "New York"), run("date tokyo new york"))
        assertTrue(run("date nowhere").single().startsWith("Unknown place"))
        // The title, the day names, and four to six weeks (a February starting on the week's first day has four).
        val cal = run("cal")
        assertTrue(cal.toString(), cal.size in 6..8 && cal[1].trim().split(" ").size == 7)
        assertTrue(run("cal nope").single().startsWith("Unknown month"))
    }
}
