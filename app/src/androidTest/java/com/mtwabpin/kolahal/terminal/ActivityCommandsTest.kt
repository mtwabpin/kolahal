package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.commands.LogFilter
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.CommandRunner
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.shell
import com.mtwabpin.kolahal.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** `activity`: the list of an app's activities, opening one by name, and what it says when it can't. */
@RunWith(AndroidJUnit4::class)
class ActivityCommandsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val apps = AppRepository.get(context)
    private val logs = LogRepository.get(context)
    @get:Rule val logLines = LogLinesSince()
    private val commands = CommandRunner(context)

    @Before fun setUp() {
        apps.refresh()
        waitUntil("the app list") { apps.apps.value.isNotEmpty() }
    }

    @After fun tearDown() {
        shell("am force-stop $SETTINGS")
    }

    /** The personal Settings app, which every test phone has. */
    private fun settings(): AppEntry {
        val s = apps.apps.value.firstOrNull { it.pkg == SETTINGS && it.profile.kind == ProfileKind.PERSONAL }
        assumeTrue("Settings is installed", s != null)
        assumeTrue("Settings is called Settings", s!!.label == "Settings")
        return s
    }

    /** Every line [line] printed; Settings has hundreds of activities, more than [CommandRunner] reads. */
    private fun run(line: String): List<LogEntry> {
        val from = logs.maxId()
        commands.processor.run(line)
        return logs.recent(5_000, afterId = from).filter { it.kind != LogKind.COMMAND }
    }

    @Test fun listsTheActivitiesOtherAppsMayOpen() {
        val app = settings()
        val lines = run("activity settings")
        val summary = lines.last()
        val items = lines.dropLast(1)
        assertEquals(LogKind.INFO, summary.kind)
        assertEquals("${items.size} activities in Settings · tap one to open it", summary.text)
        assertTrue("${items.size} activities", items.size > 10)

        // The same activities as Android reports: exported, enabled, no permission.
        @Suppress("DEPRECATION")
        val expected = context.packageManager.getPackageInfo(SETTINGS, PackageManager.GET_ACTIVITIES).activities.orEmpty()
            .filter { it.exported && it.isEnabled && it.permission == null }.map { it.name }.toSet()
        assertEquals(expected, items.map { it.ctx.values[LogContext.ACTIVITY] }.toSet())

        items.forEach { line ->
            // A name, then its class muted under it, and a tap that opens it.
            assertEquals(LogKind.OUTPUT, line.kind)
            val (label, cls) = line.text.split('\n')
            // The app's own label says nothing: then it is the class name.
            assertTrue(line.text, label.isNotBlank() && (label != "Settings" || cls == ".Settings"))
            assertEquals(ActivityMatch.shownClass(SETTINGS, line.ctx.values.getValue(LogContext.ACTIVITY)), cls)
            assertEquals(SETTINGS, line.ctx.pkg)
            assertEquals(app.profile.serial, line.ctx.userSerial)
        }
        // Sorted by name.
        val labels = items.map { it.text.substringBefore('\n') }
        assertEquals(labels.sortedWith(String.CASE_INSENSITIVE_ORDER), labels)
        // The launcher's own activity is one of them, under its class name: its label is the app's.
        assertTrue(items.any { it.text == "Settings\n.Settings" })
        assertTrue(items.any { it.ctx.values[LogContext.ACTIVITY] == WIFI })
    }

    @Test fun opensOneByName() {
        val app = settings()
        val items = ActivityCommands.activities(context, app)
        assumeTrue("Settings has $WIFI", items.any { it.className == WIFI })
        val lines = run("activity Settings WifiSettingsActivity")
        assertEquals(lines.toString(), 1, lines.size)
        val opened = lines.single()
        assertEquals(LogKind.INFO, opened.kind)
        assertTrue(opened.text, opened.text.startsWith("Opened ") && opened.text.endsWith(" · Settings"))
        assertEquals(WIFI, opened.ctx.values[LogContext.ACTIVITY])
        val resumed = Regex("ResumedActivity.{0,120}${Regex.escape("$SETTINGS/.Settings\$WifiSettingsActivity")}")
        waitUntil("Wi-Fi settings in front", ms = 10_000) { resumed.containsMatchIn(shell("dumpsys activity activities")) }

        // The copied command names it again, and resolves to the same activity.
        val command = ActivityCommands.commandFor(context, app, WIFI)
        assertEquals("activity Settings WifiSettingsActivity", command)
        // The label works too, in any case.
        val label = items.first { it.className == WIFI }.label
        val byLabel = run("activity settings ${label.uppercase()}")
        assertTrue(byLabel.toString(), byLabel.any { it.text.startsWith("Opened ") && it.ctx.values[LogContext.ACTIVITY] == WIFI } ||
            byLabel.count { it.ctx.values[LogContext.ACTIVITY] != null } > 1)
    }

    @Test fun severalMatchesAreListed() {
        settings()
        val lines = run("activity settings wifi")
        val summary = lines.last()
        assertTrue(summary.text, Regex("\\d+ activities in Settings match 'wifi' · tap one to open it").matches(summary.text))
        val items = lines.dropLast(1)
        assertTrue(items.size >= 2)
        assertTrue(items.all { it.ctx.values[LogContext.ACTIVITY] != null })
    }

    @Test fun saysWhatWentWrong() {
        settings()
        assertEquals(listOf("No app matches 'zzqqxx'"), run("activity zzqqxx").map { it.text })
        assertEquals(listOf("Settings has no activity matching 'zzqqxx' · 'activity Settings' lists them"), run("activity settings zzqqxx").map { it.text })
        assertEquals(listOf("activity works only with apps in the personal profile"), run("activity settings -w").map { it.text })
        assertEquals(listOf("Usage: activity <app> [<name>]"), run("activity").map { it.text })
        // An activity that is gone, and one that other apps may not open: a clear line, no crash.
        val gone = ActivityCommands.start(context, SETTINGS, "$SETTINGS.NoSuchActivity", "Nothing")
        assertEquals("Can't open Nothing: the app no longer has it", gone)
        @Suppress("DEPRECATION")
        val private = context.packageManager.getPackageInfo(SETTINGS, PackageManager.GET_ACTIVITIES).activities.orEmpty()
            .firstOrNull { !it.exported }
        assertNotNull(private)
        assertEquals("Can't open Hidden: the app doesn't let other apps open it", ActivityCommands.start(context, SETTINGS, private!!.name, "Hidden"))
    }

    /** A tap on a listed activity opens it; a long-press offers to open it and to copy its command. */
    @Test fun aLineOpensOnTapAndOffersItsCommand() {
        settings()
        val line = run("activity settings").first { it.ctx.values[LogContext.ACTIVITY] == WIFI }
        val host = object : LogLineActions.Host {
            override val entries get() = emptyList<LogEntry>()
            override val scripts get() = emptyList<com.mtwabpin.kolahal.data.Script>()
            override fun io(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }
            override fun setText(text: String) {}
            override fun submit() {}
            override fun emit(event: UiEvent) {}
            override fun runsOnTap(line: String) = false
            override fun runCommand(cmd: String) {}
            override fun openEditorAt(script: com.mtwabpin.kolahal.data.Script, line: Int?) {}
            override fun startActivity(intent: android.content.Intent) {}
            override fun filterLog(filter: LogFilter?) {}
        }
        val actions = LogLineActions(context, commands.processor, host)
        val labels = actions.actionsFor(line).map { it.label }
        assertEquals(listOf("Open activity", "Copy command"), labels.take(2))
        actions.onTap(line)
        val resumed = Regex("ResumedActivity.{0,120}${Regex.escape("$SETTINGS/.Settings\$WifiSettingsActivity")}")
        waitUntil("Wi-Fi settings in front", ms = 10_000) { resumed.containsMatchIn(shell("dumpsys activity activities")) }
    }

    /** `activity sett` offers apps; after the app and a space, its activities. */
    @Test fun completesAppsThenActivities() {
        settings()
        val completer = Completer(context, object : Completer.Data {
            override val history = emptyList<String>()
            override val scripts = emptyList<com.mtwabpin.kolahal.data.Script>()
            override val commandNames = emptyList<String>()
            override val scriptUsages = emptyMap<String, String>()
            override val statusNames = emptySet<String>()
            override val feeds = emptyList<com.mtwabpin.kolahal.feeds.FeedInfo>()
            override val todos = emptyList<com.mtwabpin.kolahal.productivity.Todo>()
            override val notes = emptyList<com.mtwabpin.kolahal.productivity.Note>()
            override val reminders = emptyList<com.mtwabpin.kolahal.productivity.Reminder>()
        })
        fun lines(text: String) = completer.suggestions(text).filterIsInstance<Suggestion.Completion>().map { it.replacement }
        assertTrue(lines("activity sett").toString(), "activity Settings " in lines("activity sett"))
        // The app's activities are read off the main thread: none at first, then they arrive.
        val loaded = completer.loaded.value
        assertEquals(emptyList<String>(), lines("activity settings "))
        waitUntil("the activities arrive") { completer.loaded.value > loaded }
        assertTrue(lines("activity settings ").size > 5)
        assertEquals(lines("activity settings wifisettingsact").toString(), "activity Settings WifiSettingsActivity", lines("activity settings wifisettingsact").first())
    }

    companion object {
        const val SETTINGS = "com.android.settings"
        const val WIFI = "com.android.settings.Settings\$WifiSettingsActivity"
    }
}
