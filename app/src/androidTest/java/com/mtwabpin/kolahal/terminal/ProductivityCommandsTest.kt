package com.mtwabpin.kolahal.terminal

import android.app.Instrumentation
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.provider.AlarmClock
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.notify.Notifier
import com.mtwabpin.kolahal.productivity.Agenda
import com.mtwabpin.kolahal.productivity.NoteStore
import com.mtwabpin.kolahal.productivity.ReminderScheduler
import com.mtwabpin.kolahal.productivity.ReminderStore
import com.mtwabpin.kolahal.productivity.TodoStore
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** todo, notes, remind, timer, alarm and agenda: what each prints, what it changes, and what it opens. */
@RunWith(AndroidJUnit4::class)
class ProductivityCommandsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val logs = LogRepository.get(context)
    private val config = Config.get(context)
    private val todos = TodoStore.get(context)
    private val notes = NoteStore.get(context)
    private val reminders = ReminderStore.get(context)
    private val before = logs.maxId()
    private var calendarAsked = 0
    private var calendar: String? = null

    private val ui = object : TerminalUi {
        override fun pickScriptFolder() = Unit
        override fun requestFontImport() = Unit
        override fun openScreen(screen: Screen) = Unit
        override fun requestContactsPermission() = Unit
        override fun openEditor(script: Script) = Unit
        override fun askSecret(script: Script, name: String) = Unit
        override fun requestNotificationPermission() = Unit
        override fun clearScreen() = Unit
        override fun requestCalendarPermission() { calendarAsked++ }
    }
    private val processor = CommandProcessor(context, ui)

    /** The screens the commands open, kept here instead of shown: the clock app, Settings. */
    private val opened = CopyOnWriteArrayList<Intent>()
    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult {
            opened += intent
            return Instrumentation.ActivityResult(0, null)
        }
    }

    private fun shell(command: String) =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes().decodeToString() }

    @Before fun setUp() = instrumentation.addMonitor(monitor)

    @After fun tearDown() {
        instrumentation.removeMonitor(monitor)
        todos.all().filter { "pctest" in it.text }.forEach { todos.remove(it.id) }
        notes.all().filter { "pctest" in it.text }.forEach { notes.remove(it.id) }
        reminders.all().filter { "pctest" in it.text }.forEach { ReminderScheduler.cancel(context, it.id); reminders.remove(it.id) }
        // Its events go with it.
        // Every test calendar, one an aborted run left too.
        if (calendar != null) shell("content delete --uri $CALENDARS --where account_name='pctest'")
        Keys.ALL.forEach(config::reset)
        logs.recent(500).filter { it.id > before }.forEach { logs.delete(it.id) }
    }

    /** Runs [line] and returns the non-command lines it printed. */
    private fun run(line: String): List<String> {
        val from = logs.maxId()
        processor.run(line)
        return logs.recent(400).filter { it.id > from && it.kind != LogKind.COMMAND }.map { it.text }
    }

    private fun todo(text: String) = todos.all().single { it.text == text }

    /** A name that isn't one of these is left to the dispatcher: not taken, and nothing printed. */
    @Test fun otherNamesAreLeftToTheDispatcher() {
        val from = logs.maxId()
        assertFalse(ProductivityCommands(context, ui).run("echo", "pctest"))
        assertEquals(from, logs.maxId())
    }

    /** A todo is found by its number in the list or by its id; each change prints the todo as it is now. */
    @Test fun todoDoneUndoToggleEditRemove() {
        val added = run("todo add pctest milk")
        val t = todo("pctest milk")
        assertEquals(listOf("${(todos.open().indexOf(t) + 1).toString().padStart(2)}. [ ] pctest milk", "${todos.open().size} open"), added)

        val number = todos.all().indexOf(t) + 1
        assertEquals(listOf("[x] pctest milk"), run("todo done $number"))
        assertTrue(todo("pctest milk").done)
        assertEquals(listOf("[ ] pctest milk"), run("todo undo #${t.id}"))
        assertFalse(todo("pctest milk").done)
        assertEquals(listOf("[x] pctest milk"), run("todo toggle #${t.id}"))
        assertTrue(todo("pctest milk").done)
        assertEquals(listOf("[ ] pctest milk"), run("todo undone #${t.id}"))
        assertFalse(todo("pctest milk").done)
        assertEquals(listOf("[x] pctest milk"), run("todo toggle #${t.id}"))
        assertEquals(listOf("[ ] pctest milk"), run("todo toggle #${t.id}"))
        assertFalse(todo("pctest milk").done)

        assertEquals(listOf("[ ] pctest oat milk"), run("todo edit #${t.id} pctest oat milk"))
        assertEquals("pctest oat milk", todos.all().single { it.id == t.id }.text)
        assertEquals(listOf("Removed todo: pctest oat milk"), run("todo rm #${t.id}"))
        assertNull(todos.all().firstOrNull { it.id == t.id })
    }

    /** What's missing or wrong says how it's used, and changes nothing. */
    @Test fun todoUsageErrors() {
        run("todo add pctest bread")
        val t = todo("pctest bread")
        assertEquals(listOf("Usage: todo add <text>"), run("todo add"))
        assertEquals(listOf("Usage: todo done <n>"), run("todo done"))
        assertEquals(listOf("Usage: todo rm <n>"), run("todo rm"))
        assertEquals(listOf("Usage: todo edit <n> <new text>"), run("todo edit #${t.id}"))
        assertEquals(listOf("No todo '#999999' — 'todo' lists them with numbers"), run("todo done #999999"))
        assertEquals(listOf("No todo 'x' — 'todo' lists them with numbers"), run("todo remove x"))
        assertEquals(t, todo("pctest bread"))
    }

    /** A subcommand with more words after it is the todo's own text: `todo list pctest jobs` is a todo. */
    @Test fun aSubcommandWordWithMoreIsATodo() {
        assertTrue(run("todo list pctest jobs").first().endsWith("[ ] list pctest jobs"))
        assertEquals(1, todos.all().count { it.text == "list pctest jobs" })
        assertTrue(run("todo ls pctest tasks").first().endsWith("[ ] ls pctest tasks"))
        assertEquals(1, todos.all().count { it.text == "ls pctest tasks" })
    }

    /** `notes rm` takes a note by id and needs one that's there; a search that finds nothing says so; any other word says how `notes` is used. */
    @Test fun notesRemoveByIdAndUsage() {
        run("note pctest plan")
        val n = notes.all().single { it.text == "pctest plan" }
        assertEquals(listOf("Usage: notes rm <n> — 'notes' lists them with numbers"), run("notes rm"))
        assertEquals(listOf("Usage: notes rm <n> — 'notes' lists them with numbers"), run("notes rm #999999"))
        assertEquals(listOf("Usage: notes [grep <text>|rm <n>] · note <text> adds one"), run("notes bogus"))
        assertEquals(listOf("No note contains 'pctest-nowhere'"), run("notes grep pctest-nowhere"))
        assertEquals(listOf("Removed note: pctest plan"), run("notes rm #${n.id}"))
        assertTrue(notes.all().none { it.id == n.id })
    }

    /** `remind` alone lists the reminders as `reminders` does; a wrong number or rule says how it's used. */
    @Test fun remindListsAndRefusesWhatItCantRead() {
        run("remind 30m pctest tea")
        val listed = run("remind")
        assertEquals(run("reminders"), listed)
        assertEquals(listed, run("remind ls"))
        assertTrue(listed.toString(), listed.any { it.endsWith(". pctest tea") })
        assertEquals("Tap puts 'remind rm' in the prompt · long-press → Cancel reminder", listed.last())

        assertEquals(listOf("Usage: remind rm <n> — 'reminders' lists them with numbers"), run("remind rm #999999"))
        assertEquals(
            listOf("Usage: remind every <day|weekday|weekend|mon,thu|month 1st|year dec 3> [at] [time] <text>"),
            run("remind every sometimes pctest x"),
        )
        assertEquals(listOf("pctest tea"), reminders.all().filter { "pctest" in it.text }.map { it.text })
    }

    /** A reminder that isn't today or tomorrow says its date, and its year when it's another one; notifications that are off are said too. */
    @Test fun aReminderDaysAwaySaysItsDate() {
        config.set(Keys.CLOCK_24H, "on")
        val today = LocalDate.now()
        val canPost = Notifier.canPost(context, Notifier.CHANNEL_REMINDERS)
        for (day in listOf(today.plusDays(10), today.plusYears(1).plusDays(10))) {
            val pattern = if (day.year == today.year) "EEE d MMM" else "EEE d MMM yyyy"
            val label = DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(day) + " 10:00"
            val said = run("remind $day 10:00 pctest dentist")
            assertTrue(said.toString(), said.first().startsWith("Reminder set for $label (in ") && said.first().endsWith("): pctest dentist"))
            assertEquals(said.toString(), !canPost, said.any { it.startsWith("Notifications are off") })
            run("reminders")
            assertTrue(logs.recent(20).any { it.text.endsWith(". pctest dentist") && it.ctx.values[LogContext.DESC].orEmpty().startsWith("$label · in ") })
            reminders.all().filter { "pctest" in it.text }.forEach { ReminderScheduler.cancel(context, it.id); reminders.remove(it.id) }
        }
    }

    /** `remind exact` opens Android's page for exact alarms when Kolahal may not set them yet, and says so when it may. */
    @Test fun remindExactOpensTheAlarmsPage() {
        val said = run("remind exact")
        if (ReminderScheduler.canBeExact(context)) {
            assertEquals(listOf("Exact alarms are already allowed"), said)
            assertEquals(emptyList<Intent>(), opened.toList())
        } else {
            assertEquals(emptyList<String>(), said)
            val page = opened.single()
            assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, page.action)
            assertEquals("package:${context.packageName}", page.dataString)
        }
    }

    /** `timer 5m tea` sets the clock app's timer without showing it; a bare number is minutes; over a day is refused. */
    @Test fun timerSetsTheClockAppsTimer() {
        assertEquals(listOf("Timer 5m — pctest tea"), run("timer 5m pctest tea"))
        with(opened.single()) {
            assertEquals(AlarmClock.ACTION_SET_TIMER, action)
            assertEquals(300, getIntExtra(AlarmClock.EXTRA_LENGTH, 0))
            assertTrue(getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false))
            assertEquals("pctest tea", getStringExtra(AlarmClock.EXTRA_MESSAGE))
        }
        opened.clear()
        assertEquals(listOf("Timer 25m"), run("timer 25"))
        assertEquals(1_500, opened.single().getIntExtra(AlarmClock.EXTRA_LENGTH, 0))
        assertFalse(opened.single().hasExtra(AlarmClock.EXTRA_MESSAGE))
        opened.clear()
        assertEquals(listOf("Timer 1h 30m"), run("timer 1h30m"))
        assertEquals(5_400, opened.single().getIntExtra(AlarmClock.EXTRA_LENGTH, 0))
        opened.clear()

        assertEquals(listOf("Timers can be at most 24h"), run("timer 25h"))
        assertEquals(listOf("Usage: timer <5m|1h30m|90s|25> [label]"), run("timer soon"))
        assertEquals(emptyList<Intent>(), opened.toList())
        // Alone, it shows the clock app's timers.
        assertEquals(emptyList<String>(), run("timer"))
        assertEquals(AlarmClock.ACTION_SHOW_TIMERS, opened.single().action)
    }

    /** `alarm 7am wake` sets an alarm in the clock app; `alarm`, `alarms` and `stopwatch` open the clock app. */
    @Test fun alarmSetsTheClockAppsAlarm() {
        assertEquals(listOf("Alarm 07:00 — pctest wake"), run("alarm 7am pctest wake"))
        with(opened.single()) {
            assertEquals(AlarmClock.ACTION_SET_ALARM, action)
            assertEquals(7, getIntExtra(AlarmClock.EXTRA_HOUR, -1))
            assertEquals(0, getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
            assertEquals("pctest wake", getStringExtra(AlarmClock.EXTRA_MESSAGE))
        }
        opened.clear()
        assertEquals(listOf("Alarm 18:45"), run("alarm 18:45"))
        assertEquals(18, opened.single().getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(45, opened.single().getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
        assertFalse(opened.single().hasExtra(AlarmClock.EXTRA_MESSAGE))
        opened.clear()
        assertEquals(listOf("Usage: alarm <HH:MM|7am> [label]"), run("alarm 25:00"))
        assertEquals(emptyList<Intent>(), opened.toList())

        assertEquals(emptyList<String>(), run("alarm"))
        assertEquals(emptyList<String>(), run("alarms"))
        assertEquals(listOf("Opened the clock app (Android can't open its stopwatch directly)"), run("stopwatch"))
        assertEquals(listOf(AlarmClock.ACTION_SHOW_ALARMS, AlarmClock.ACTION_SHOW_ALARMS, AlarmClock.ACTION_SHOW_TIMERS), opened.map { it.action })
    }

    /**
     * `agenda` asks for the calendar first; then the next three days, by day: an event's times, an
     * all-day event, a day with nothing left out; `agenda 1` is today alone. (All-day events first
     * within a day: AgendaTest.)
     */
    @Test fun agendaListsTheNextDays() {
        if (!Agenda.hasPermission(context)) {
            assertEquals(listOf("Agenda needs calendar access — tap to allow"), run("agenda"))
            assertEquals("agenda --allow", logs.recent(1).single().ctx.values[LogContext.RUN])
            assertEquals(emptyList<String>(), run("agenda --allow"))
            assertEquals(1, calendarAsked)
            // As the app asks for it. Not taken back after: Android stops an app whose permission is revoked.
            shell("pm grant ${context.packageName} android.permission.READ_CALENDAR")
        }
        assertEquals(listOf("Calendar is allowed"), run("agenda --allow"))
        assertEquals(emptyList<String>(), run("agenda --settings"))
        with(opened.single()) {
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, action)
            assertEquals("package:${context.packageName}", dataString)
        }

        config.set(Keys.CLOCK_24H, "on")
        // A calendar an aborted run left would be found first, its events with it.
        shell("content delete --uri $CALENDARS --where account_name='pctest'")
        shell("content insert --uri $CALENDARS --bind account_name:s:pctest --bind account_type:s:LOCAL --bind name:s:pctest --bind calendar_displayName:s:pctest --bind calendar_access_level:i:700 --bind ownerAccount:s:pctest --bind visible:i:1 --bind sync_events:i:1")
        calendar = Regex("_id=(\\d+)").find(shell("content query --uri content://com.android.calendar/calendars --projection _id --where account_name='pctest'"))?.groupValues?.get(1)
        assertNotNull("the test calendar", calendar)
        assertEquals(listOf("Nothing on your calendar in the next 3 days"), run("agenda"))

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val begin = today.plusDays(1).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val end = begin + 3_600_000
        shell("content insert --uri content://com.android.calendar/events --bind calendar_id:i:$calendar --bind title:s:pctest-standup --bind dtstart:l:$begin --bind dtend:l:$end --bind eventTimezone:s:${zone.id}")
        // All day: from UTC midnight to the next.
        val offsite = today.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        shell("content insert --uri content://com.android.calendar/events --bind calendar_id:i:$calendar --bind title:s:pctest-offsite --bind dtstart:l:$offsite --bind dtend:l:${offsite + 86_400_000} --bind allDay:i:1 --bind eventTimezone:s:UTC")

        val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        val listed = run("agenda")
        assertEquals(
            listOf("Tomorrow · ${day.format(today.plusDays(1))}", "10:00–11:00  pctest-standup", day.format(today.plusDays(2)), "All day      pctest-offsite"),
            listed,
        )
        // The event's line opens that one event at its times.
        val line = logs.recent(10).single { it.text.endsWith("pctest-standup") }
        assertEquals(begin.toString(), line.ctx.values[LogContext.BEGIN])
        assertEquals(end.toString(), line.ctx.values[LogContext.END])
        assertNotNull(line.ctx.values[LogContext.EVENT]?.toLongOrNull())

        assertEquals(listOf("Nothing left on your calendar today"), run("agenda 1"))
        assertEquals(listed.take(2), run("agenda 2"))
    }

    companion object {
        /** The calendars a test may add and delete, as their sync adapter (a local account: nothing to sync). */
        const val CALENDARS = "content://com.android.calendar/calendars?caller_is_syncadapter=true&account_name=pctest&account_type=LOCAL"
    }
}
