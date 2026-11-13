package com.mtwabpin.kolahal.terminal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.notify.Notifier
import com.mtwabpin.kolahal.productivity.Agenda
import com.mtwabpin.kolahal.productivity.NoteStore
import com.mtwabpin.kolahal.productivity.Reminder
import com.mtwabpin.kolahal.productivity.ReminderScheduler
import com.mtwabpin.kolahal.productivity.ReminderStore
import com.mtwabpin.kolahal.productivity.Repeat
import com.mtwabpin.kolahal.productivity.TimeSpec
import com.mtwabpin.kolahal.productivity.Todo
import com.mtwabpin.kolahal.productivity.TodoStore
import com.mtwabpin.kolahal.productivity.pickRef
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The commands todo, note / notes, remind / reminders, timer, alarm / alarms, stopwatch and agenda.
 * They live outside [CommandProcessor], so the dispatcher needs only one line per command.
 */
class ProductivityCommands(context: Context, private val ui: TerminalUi) : CommandGroup(context) {
    private val conf = Config.get(context)
    private val todos = TodoStore.get(context)
    private val notes = NoteStore.get(context)
    private val reminders = ReminderStore.get(context)

    /** Runs [name] if this class handles it, and returns true. Otherwise returns false. [rest] is the raw argument text. */
    fun run(name: String, rest: String): Boolean {
        when (name) {
            "todo" -> todo(rest)
            "note" -> if (rest.isBlank()) listNotes(null) else addNote(rest)
            "notes" -> notes(rest)
            "remind" -> remind(rest)
            "reminders" -> listReminders()
            "timer" -> timer(rest)
            "alarm" -> alarm(rest)
            "alarms" -> startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            "stopwatch" -> {
                // Android has no stopwatch intent. The clock app's stopwatch is one tab away.
                startActivity(Intent(AlarmClock.ACTION_SHOW_TIMERS))
                info("Opened the clock app (Android can't open its stopwatch directly)")
            }
            "agenda" -> agenda(rest)
            else -> return false
        }
        return true
    }


    private fun words(s: String) = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    // ---- todo ----------------------------------------------------------------------------------

    private fun todo(rest: String) {
        val w = words(rest)
        val sub = w.firstOrNull()?.lowercase()
        val all = todos.all()
        fun pick(): Todo? = pickRef(w.getOrNull(1), all) { it.id }.also {
            if (it == null) error("No todo '${w.getOrNull(1).orEmpty()}' — 'todo' lists them with numbers")
        }
        // A word alone is a subcommand. With more words after it, it is the todo text (`todo clear out the garage`).
        when (sub) {
            null -> listTodos(all, includeDone = false)
            "ls", "list" -> if (w.size == 1) listTodos(all, includeDone = false) else addTodo(rest.trim())
            "all" -> if (w.size == 1) listTodos(all, includeDone = true) else addTodo(rest.trim())
            "add" -> addTodo(rest.trim().substringAfter(' ', "").trim())
            "done", "undo", "undone", "toggle" -> {
                if (w.size < 2) return error("Usage: todo $sub <n>")
                val t = pick() ?: return
                val done = when (sub) {
                    "done" -> true
                    "toggle" -> !t.done
                    else -> false
                }
                todos.setDone(t.id, done)
                todoLine(t.copy(done = done), null, info = true)
            }
            "rm", "remove", "delete" -> {
                if (w.size < 2) return error("Usage: todo rm <n>")
                val t = pick() ?: return
                todos.remove(t.id)
                info("Removed todo: ${t.text}")
            }
            "edit" -> {
                if (w.size < 3) return error("Usage: todo edit <n> <new text>")
                val t = pick() ?: return
                val text = rest.trim().substringAfter(' ').trim().substringAfter(' ').trim()
                todos.edit(t.id, text)
                todoLine(t.copy(text = text), null, info = true)
            }
            "clear" -> if (w.size > 1) addTodo(rest.trim()) else {
                val n = todos.clearDone()
                info(if (n == 0) "No finished todos to clear" else "Cleared $n finished todo${if (n == 1) "" else "s"}")
            }
            else -> addTodo(rest.trim())
        }
    }

    private fun addTodo(text: String) {
        if (text.isBlank()) return error("Usage: todo add <text>")
        val id = todos.add(text)
        val open = todos.open()
        val pos = open.indexOfFirst { it.id == id } + 1
        todoLine(Todo(id, text, false), pos, info = true)
        info("${open.size} open")
    }

    private fun listTodos(all: List<Todo>, includeDone: Boolean) {
        val shown = if (includeDone) all else all.filter { !it.done }
        val doneCount = all.count { it.done }
        if (shown.isEmpty()) {
            return info(
                if (all.isEmpty()) "No todos — 'todo <text>' adds one"
                else "Nothing open · $doneCount done ('todo all' shows them, 'todo clear' removes them)"
            )
        }
        shown.forEach { t -> todoLine(t, all.indexOf(t) + 1) }
        info(
            "${all.size - doneCount} open" + (if (doneCount > 0) " · $doneCount done" else "") +
                " — tap to toggle · long-press to edit or delete" + if (doneCount > 0 && includeDone) " · 'todo clear' removes done" else ""
        )
    }

    /** Prints one todo. A tap toggles it. The tap uses the id, so the line stays correct after the list changes. */
    private fun todoLine(t: Todo, pos: Int?, info: Boolean = false) {
        val text = (if (pos != null) "${pos.toString().padStart(2)}. " else "") + (if (t.done) "[x] " else "[ ] ") + t.text
        val ctx = LogContext.of(
            LogContext.RUN to "todo toggle #${t.id}",
            LogContext.TODO to t.id.toString(),
            LogContext.TODO_DONE to if (t.done) "1" else "0",
            LogContext.COPY to t.text,
        ) + LogContext.ALIGNED
        if (info) info(text, ctx) else out(text, ctx)
    }

    // ---- notes ---------------------------------------------------------------------------------

    private fun addNote(text: String) {
        if (text.isBlank()) return usage("note")
        val id = notes.add(text.trim())
        info("Note saved", noteCtx(id, text.trim()))
    }

    private fun notes(rest: String) {
        val w = words(rest)
        when (w.firstOrNull()?.lowercase()) {
            null, "ls", "list" -> listNotes(null)
            "grep", "find", "search" -> {
                val q = rest.trim().substringAfter(' ', "").trim()
                if (q.isEmpty()) return error("Usage: notes grep <text>")
                listNotes(q)
            }
            "rm", "remove", "delete" -> {
                val all = notes.all()
                val n = pickRef(w.getOrNull(1), all) { it.id }
                    ?: return error("Usage: notes rm <n> — 'notes' lists them with numbers")
                notes.remove(n.id)
                info("Removed note: ${n.text}")
            }
            "add" -> addNote(rest.trim().substringAfter(' ', ""))
            else -> error("Usage: notes [grep <text>|rm <n>] · note <text> adds one")
        }
    }

    private fun listNotes(query: String?) {
        val all = notes.all()
        if (all.isEmpty()) return info("No notes — 'note <text>' saves one")
        val fmt = DateTimeFormatter.ofPattern(if (conf[Keys.CLOCK_24H]) "d MMM HH:mm" else "d MMM h:mm a", Locale.getDefault())
        val zone = ZoneId.systemDefault()
        var shown = 0
        all.forEachIndexed { i, n ->
            if (query != null && !n.text.contains(query, ignoreCase = true)) return@forEachIndexed
            shown++
            out(
                "${(i + 1).toString().padStart(2)}. ${n.text}",
                // An item each: a long note wraps over several lines.
                noteCtx(n.id, n.text) + mapOf(LogContext.DESC to fmt.format(Instant.ofEpochMilli(n.created).atZone(zone))) + LogContext.ALIGNED + LogContext.ITEM,
            )
        }
        info(
            if (query != null && shown == 0) "No note contains '$query'"
            else "$shown note${if (shown == 1) "" else "s"} — tap to copy · 'notes rm <n>'"
        )
    }

    private fun noteCtx(id: Long, text: String) = LogContext.of(LogContext.NOTE to id.toString(), LogContext.COPY to text)

    // ---- reminders -----------------------------------------------------------------------------

    private fun remind(rest: String) {
        var w = words(rest)
        // `--yes` comes from a tap on the question below, or from a user who typed it on purpose.
        val confirmed = w.firstOrNull()?.lowercase() == "--yes"
        if (confirmed) w = w.drop(1)
        when (w.firstOrNull()?.lowercase()) {
            null, "ls", "list" -> return listReminders()
            "rm", "cancel", "remove" -> {
                val r = pickRef(w.getOrNull(1), reminders.all()) { it.id }
                    ?: return error("Usage: remind rm <n> — 'reminders' lists them with numbers")
                ReminderScheduler.cancel(context, r.id)
                reminders.remove(r.id)
                return info("Cancelled reminder: ${r.text}")
            }
            "exact" -> {
                if (ReminderScheduler.canBeExact(context)) return info("Exact alarms are already allowed")
                return startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null))
                )
            }
        }
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val repeating = TimeSpec.parseRepeat(w, now)
        if (w.firstOrNull()?.lowercase() == "every" && repeating == null) {
            return error("Usage: remind every <day|weekday|weekend|mon,thu|month 1st|year dec 3> [at] [time] <text>")
        }
        val parsed = repeating?.let { TimeSpec.Parsed(it.at, it.consumed) } ?: TimeSpec.parseWhen(w, now)
            ?: return error("Usage: remind <10m|2h|18:30|tomorrow 9:00|fri 8am|dec 3 10:00|every mon 19:00> <text>")
        val text = w.drop(parsed.consumed).joinToString(" ")
        if (text.isBlank()) return error("Reminder text missing: remind ${w.take(parsed.consumed).joinToString(" ")} <text>")
        val relative = if (repeating == null) TimeSpec.relativeSeconds(w) else null
        // For a time in the small hours, ask first. A duration (like 10m) is clearly meant, so don't ask.
        if (!confirmed && relative == null && TimeSpec.inTheSmallHours(parsed.at)) return askAboutTheSmallHours(w, parsed, repeating, text, now)
        val at = relative?.let { System.currentTimeMillis() + it * 1000 } ?: parsed.at.atZone(zone).toInstant().toEpochMilli()
        // The user can tap the question twice, so don't add the same reminder again.
        if (confirmed && reminders.all().any { it.at == at && it.text == text && it.repeat == repeating?.rule }) return info("Already set: $text")
        val r = reminders.add(at, text, repeating?.rule, repeating?.at?.toLocalTime()?.toString())
        ReminderScheduler.schedule(context, r)
        val then = repeating?.let { ", then ${Repeat.label(it.rule)}" }.orEmpty()
        info(
            "Reminder set for ${whenLabel(at)} (in ${TimeSpec.formatDuration((at - System.currentTimeMillis() + 999) / 1000)})$then: $text",
            reminderCtx(r),
        )
        if (!Notifier.canPost(context, Notifier.CHANNEL_REMINDERS)) {
            fix("Notifications are off — the reminder will only appear in the log (tap to allow)", "notifications")
        }
        if (!ReminderScheduler.canBeExact(context)) {
            info("Exact alarms are off, so it may be up to an hour late — tap to allow them", LogContext.of(LogContext.RUN to "remind exact"))
        }
    }

    /**
     * Says how the time was read, but sets nothing. The user sets the reminder with a tap: on this
     * reading, or on the other reading if there is one.
     */
    private fun askAboutTheSmallHours(w: List<String>, parsed: TimeSpec.Parsed, repeating: TimeSpec.Repeating?, text: String, now: LocalDateTime) {
        fun label(at: LocalDateTime) = whenLabel(at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        val then = repeating?.let { ", then ${Repeat.label(it.rule)}" }.orEmpty()
        info("Not set yet, as ${label(parsed.at)} is before 6 AM: $text")
        val words = if (repeating != null) w.joinToString(" ") else TimeSpec.exactWords(parsed.at, text)
        info("Tap to set it for ${label(parsed.at)}$then", LogContext.of(LogContext.RUN to "remind --yes $words"))
        TimeSpec.otherReading(w, parsed.consumed, now)?.let {
            info("Or tap to set it for ${label(it.at)}: ${it.text}", LogContext.of(LogContext.RUN to "remind --yes ${TimeSpec.exactWords(it.at, it.text)}"))
        }
    }

    private fun listReminders() {
        val all = reminders.all()
        if (all.isEmpty()) return info("No reminders — e.g. remind 10m tea, remind tomorrow 9:00 call mom, remind every mon 19:00 bins")
        val nowMs = System.currentTimeMillis()
        all.forEachIndexed { i, r ->
            out(
                "${(i + 1).toString().padStart(2)}. ${r.text}",
                reminderCtx(r) + mapOf(
                    LogContext.DESC to "${whenLabel(r.at)} · in ${TimeSpec.formatDuration(((r.at - nowMs) / 1000).coerceAtLeast(0))}" +
                        r.repeat?.let { " · ${Repeat.label(it)}" }.orEmpty(),
                ) + LogContext.ALIGNED + LogContext.ITEM,
            )
        }
        info("Tap puts 'remind rm' in the prompt · long-press → Cancel reminder")
    }

    private fun reminderCtx(r: Reminder) = LogContext.of(
        LogContext.REMINDER to r.id.toString(),
        LogContext.COPY to r.text,
    )

    /** Formats a time as "18:30", "tomorrow 09:00", "Fri 23 Oct 08:00", or "Thu 2 Dec 2027 08:00" in another year. */
    private fun whenLabel(at: Long): String {
        val zone = ZoneId.systemDefault()
        val t = Instant.ofEpochMilli(at).atZone(zone)
        val time = DateTimeFormatter.ofPattern(if (conf[Keys.CLOCK_24H]) "HH:mm" else "h:mm a", Locale.getDefault()).format(t)
        val today = LocalDate.now(zone)
        return when (t.toLocalDate()) {
            today -> time
            today.plusDays(1) -> "tomorrow $time"
            else -> DateTimeFormatter.ofPattern(if (t.year == today.year) "EEE d MMM" else "EEE d MMM yyyy", Locale.getDefault()).format(t) + " " + time
        }
    }

    // ---- clock app -----------------------------------------------------------------------------

    private fun timer(rest: String) {
        val w = words(rest)
        if (w.isEmpty()) return startActivity(Intent(AlarmClock.ACTION_SHOW_TIMERS))
        val seconds = TimeSpec.parseDuration(w[0], bareMinutes = true)
            ?: return error("Usage: timer <5m|1h30m|90s|25> [label]")
        if (seconds > 86_400) return error("Timers can be at most 24h")
        val label = w.drop(1).joinToString(" ")
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds.toInt())
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (label.isNotEmpty()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        startActivity(intent)
        info("Timer ${TimeSpec.formatDuration(seconds)}" + if (label.isNotEmpty()) " — $label" else "")
    }

    private fun alarm(rest: String) {
        val w = words(rest)
        if (w.isEmpty()) return startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        val time = TimeSpec.parseClock(w[0], allowBareHour = true) ?: return error("Usage: alarm <HH:MM|7am> [label]")
        val label = w.drop(1).joinToString(" ")
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, time.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
        if (label.isNotEmpty()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        startActivity(intent)
        info("Alarm ${String.format(Locale.ROOT, "%02d:%02d", time.hour, time.minute)}" + if (label.isNotEmpty()) " — $label" else "")
    }

    // ---- agenda --------------------------------------------------------------------------------

    private fun agenda(rest: String) {
        val arg = words(rest).firstOrNull()
        if (arg == "--settings") {
            return startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        }
        if (arg == "--allow") {
            return if (Agenda.hasPermission(context)) info("Calendar is allowed") else ui.requestCalendarPermission()
        }
        if (!Agenda.hasPermission(context)) {
            return fix("Agenda needs calendar access — tap to allow", "agenda --allow")
        }
        val days = arg?.toIntOrNull()?.coerceIn(1, 31) ?: DEFAULT_AGENDA_DAYS
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val grouped = Agenda.group(Agenda.query(context, today, days, zone), zone, today, days, System.currentTimeMillis())
        if (grouped.isEmpty()) {
            return info(if (days == 1) "Nothing left on your calendar today" else "Nothing on your calendar in the next $days days")
        }
        val is24h = conf[Keys.CLOCK_24H]
        val timeFmt = DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mma", Locale.getDefault())
        val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        fun span(e: com.mtwabpin.kolahal.productivity.AgendaEvent): String {
            val b = timeFmt.format(Instant.ofEpochMilli(e.begin).atZone(zone))
            val en = timeFmt.format(Instant.ofEpochMilli(e.end).atZone(zone))
            return if (e.end <= e.begin) b else "$b–$en"
        }
        val width = grouped.flatMap { it.second }.filter { !it.allDay }.maxOfOrNull { span(it).length } ?: 0
        grouped.forEach { (day, events) ->
            val label = when (day) {
                today -> "Today · "
                today.plusDays(1) -> "Tomorrow · "
                else -> ""
            } + dayFmt.format(day)
            // A gap above each day; its events are one line each, close together.
            info(label, LogContext(LogContext.ITEM))
            events.forEach { e ->
                val time = if (e.allDay) "All day" else span(e)
                out(
                    time.padEnd(maxOf(width, 7)) + "  " + e.title,
                    LogContext.of(
                        LogContext.EVENT to e.id.toString(),
                        LogContext.BEGIN to e.begin.toString(),
                        LogContext.END to e.end.toString(),
                        LogContext.COPY to e.title,
                    ) + LogContext.ALIGNED,
                )
            }
        }
    }

    companion object {
        const val DEFAULT_AGENDA_DAYS = 3

        /** Opens one calendar instance in the calendar app. */
        fun eventIntent(ctx: LogContext): Intent? {
            val id = ctx.values[LogContext.EVENT]?.toLongOrNull() ?: return null
            return Intent(Intent.ACTION_VIEW, Agenda.eventUri(id)).apply {
                ctx.values[LogContext.BEGIN]?.toLongOrNull()?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                ctx.values[LogContext.END]?.toLongOrNull()?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
            }
        }

        val TODO_SUBCOMMANDS = listOf("add", "done", "undo", "edit", "rm", "all", "clear")
        val NOTES_SUBCOMMANDS = listOf("grep", "rm")
    }
}
