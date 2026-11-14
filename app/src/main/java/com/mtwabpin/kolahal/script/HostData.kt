package com.mtwabpin.kolahal.script

import android.content.Context
import android.content.Intent
import com.mtwabpin.kolahal.data.Singleton
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.takeWhole
import com.mtwabpin.kolahal.feeds.FeedItem
import com.mtwabpin.kolahal.feeds.FeedRepository
import com.mtwabpin.kolahal.productivity.Agenda
import com.mtwabpin.kolahal.productivity.Note
import com.mtwabpin.kolahal.productivity.NoteStore
import com.mtwabpin.kolahal.productivity.Reminder
import com.mtwabpin.kolahal.productivity.ReminderStore
import com.mtwabpin.kolahal.productivity.Repeat
import com.mtwabpin.kolahal.productivity.ShownEvent
import com.mtwabpin.kolahal.productivity.Todo
import com.mtwabpin.kolahal.productivity.TodoStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Instant
import java.time.ZoneId

/**
 * What a script reads of your own lists: `todos`, `notes`, `reminders`, `calendar` and `feeds`.
 *
 *   todos.list()               -> [{ id, text, done }]
 *   todos.setDone(id, done)    -> true when that todo changed
 *   notes.list(n?)             -> [{ id, text, created }]
 *   reminders.list()           -> [{ id, text, at, repeats }]
 *   calendar.events(days?)     -> [{ id, title, starts, ends, allDay }]
 *   feeds.unread(n?)           -> [{ id, feed, title, link, date, summary }]
 *
 * Each list is private data, behind the `@uses` item of its name ([ScriptMeta.USES]). [may] decides:
 * - The script does not declare the item: [may] throws an error that names the `@uses` line.
 * - You switched the item off, or did not allow it yet: [may] is false. The call gives an empty
 *   list, and `todos.setDone` gives false and changes nothing.
 * Nothing is read before [may] has said yes.
 *
 * What a row holds:
 * - Times are in seconds since 1970, as a widget takes them.
 * - Every text has its style marks removed ([StyledText.strip]) and is at most [MAX_TEXT] characters.
 * - Only text, numbers and true or false, so a row reaches Lua and JavaScript as a plain table or object.
 *
 * The engines make one for each run: `HostData(host.context, host::may, host::live, host::logError)`.
 * The argument checks and the rows are functions outside the class, below. They need no Android,
 * so JVM tests call them (HostDataTest).
 */
internal class HostData(
    private val context: Context,
    /** [HostApi.may]: whether the script may use an `@uses` item, for a call. It throws when the item is not declared. */
    private val may: (item: String, call: String) -> Boolean,
    /** [HostApi.live]: throws when the run has ended. A write must not happen after that. */
    private val live: () -> Unit,
    /** [HostApi.logError]: an error line in the run's log. */
    private val logError: (String) -> Unit = {},
) {
    /** `todos.list()`: the open todos, oldest first, then the done ones, as the `todo` command lists them. */
    fun todosList(): List<Map<String, Any?>> {
        if (!may(TODOS, "todos.list")) return emptyList()
        return TodoStore.get(context).all().take(MAX_TODOS).map(::todoRow)
    }

    /** `todos.setDone` calls in this run that were allowed and well formed. */
    private var todoChanges = 0

    /**
     * `todos.setDone(id, done)`: marks the todo [id] done, or open again. True when that todo changed.
     * False when no todo has the id, or when it is so already.
     * - It needs `@uses todos`, the same item as reading: who may read the list may tick it.
     * - It works in any run: a timer, an event, a tap on a widget.
     * - A run may call it [MAX_TODO_CHANGES] times. Later calls change nothing, and the first of them says so.
     * - It never adds, edits or deletes a todo.
     *
     * A script runs in the `:script` process, and home counts the todo store's writes in its own.
     * So a change made here is told to home with a message ([TodoStore.ACTION_CHANGED]). Home then
     * reads the list again, for the Todos widget and for the `todo` command's list.
     */
    fun todosSetDone(id: Any?, done: Any?): Boolean {
        live()
        if (!may(TODOS, "todos.setDone")) return false
        val todo = todoId(id)
        val to = todoDone(done)
        if (++todoChanges > MAX_TODO_CHANGES) {
            if (todoChanges == MAX_TODO_CHANGES + 1) logError("todos.setDone: more than $MAX_TODO_CHANGES calls in one run; the rest change nothing")
            return false
        }
        val changed = TodoStore.get(context).mark(todo, to)
        if (changed && Singleton.inScriptProcess) {
            runCatching { context.sendBroadcast(Intent(TodoStore.ACTION_CHANGED).setPackage(context.packageName)) }
        }
        return changed
    }

    /** `notes.list(n?)`: the newest [n] notes, the newest first ([listCount]). */
    fun notesList(n: Any?): List<Map<String, Any?>> {
        if (!may(NOTES, "notes.list")) return emptyList()
        val count = listCount("notes.list", "n", n, MAX_NOTES, DEFAULT_NOTES)
        return NoteStore.get(context).all().take(count).map(::noteRow)
    }

    /** `reminders.list()`: the reminders that are set, the soonest first. */
    fun remindersList(): List<Map<String, Any?>> {
        if (!may(REMINDERS, "reminders.list")) return emptyList()
        return ReminderStore.get(context).all().take(MAX_REMINDERS).map(::reminderRow)
    }

    /**
     * `calendar.events(days?)`: the calendar's events of [days] days from today ([listCount]), as the
     * Agenda widget shows them ([Agenda.shown]): an event that has ended is left out.
     * - Without Android's calendar permission the list is empty. A script can't ask for the
     *   permission. The `agenda` command and the Agenda widget ask for it.
     * - A calendar that can't be read gives an empty list too, never an error.
     */
    fun calendarEvents(days: Any?): List<Map<String, Any?>> {
        if (!may(CALENDAR, "calendar.events")) return emptyList()
        val count = listCount("calendar.events", "days", days, MAX_CALENDAR_DAYS, DEFAULT_CALENDAR_DAYS)
        if (!runCatching { Agenda.hasPermission(context) }.getOrDefault(false)) return emptyList()
        // One moment and one zone for the read and for what is shown, so the two agree at midnight.
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val events = runCatching { Agenda.query(context, today, count, zone) }.getOrDefault(emptyList())
        return Agenda.shown(events, zone, now, count).take(MAX_EVENTS).map(::eventRow)
    }

    /**
     * `feeds.unread(n?)`: the newest [n] unread items of the feeds you follow, the newest first
     * ([listCount]).
     * - It reads what Kolahal has fetched already. It makes no request, so it needs no internet access.
     * - It marks nothing as read, and changes nothing else.
     *
     * The feed database is read here in the `:script` process. Only reads are safe there:
     * [FeedRepository.add] reads the settings, which only the main process can see.
     */
    fun feedsUnread(n: Any?): List<Map<String, Any?>> {
        if (!may(FEEDS, "feeds.unread")) return emptyList()
        val count = listCount("feeds.unread", "n", n, MAX_FEED_ITEMS, DEFAULT_FEED_ITEMS)
        val repo = FeedRepository.get(context)
        val names = repo.feeds().associate { it.id to feedName(it.title, it.url) }
        return repo.items(unreadOnly = true, limit = count).map { feedRow(it, names[it.feedId].orEmpty()) }
    }

    companion object {
        // The `@uses` items. Each is also the name of its namespace.
        const val TODOS = "todos"
        const val NOTES = "notes"
        const val REMINDERS = "reminders"
        const val CALENDAR = "calendar"
        const val FEEDS = "feeds"

        /** The most todos `todos.list` gives. */
        const val MAX_TODOS = 200
        /** The most `todos.setDone` calls that count in one run. */
        const val MAX_TODO_CHANGES = 20
        /** `notes.list(n)`: n is from 1 to [MAX_NOTES]. Without it, [DEFAULT_NOTES]. */
        const val MAX_NOTES = 50
        const val DEFAULT_NOTES = 10
        /** The most reminders `reminders.list` gives. */
        const val MAX_REMINDERS = 200
        /** `calendar.events(days)`: days is from 1 to [MAX_CALENDAR_DAYS]. Without it, [DEFAULT_CALENDAR_DAYS], as the Agenda widget shows. */
        const val MAX_CALENDAR_DAYS = 30
        const val DEFAULT_CALENDAR_DAYS = 3
        /** The most events `calendar.events` gives. */
        const val MAX_EVENTS = 100
        /** `feeds.unread(n)`: n is from 1 to [MAX_FEED_ITEMS]. Without it, [DEFAULT_FEED_ITEMS]. */
        const val MAX_FEED_ITEMS = 50
        const val DEFAULT_FEED_ITEMS = 10
        /** The longest text in a row, in characters. A longer one is cut and ends in "…". */
        const val MAX_TEXT = 4000
    }
}

/**
 * How many rows a list call gives, for the argument [value] a script passed as [name]:
 * - nothing (`nil`, `null`, no argument): [default].
 * - a number: that many, from 1 to [max]. A fraction is dropped, and a number out of that range
 *   is held at its nearest end.
 * - anything else: an error that says what to pass.
 * The same rule as `device.unlocks(days)` ([unlockDays]).
 */
internal fun listCount(call: String, name: String, value: Any?, max: Int, default: Int): Int = when (value) {
    null -> default
    is Number -> value.toDouble().let { if (it.isNaN()) 1 else it.coerceIn(1.0, max.toDouble()).toInt() }
    else -> throw IllegalArgumentException("$call: $name must be a number from 1 to $max")
}

/**
 * The todo that `todos.setDone(id, …)` names: the `id` of a row from `todos.list()`.
 * - A whole number above 0. Lua passes every number as a double.
 * - The same as text of digits (`"12"`). A tap on a widget gives its arguments back as text, so a
 *   script can put the id in a row's tap and pass it on as it comes.
 * - Anything else is an error that says what to pass. It is never read as "the first todo".
 */
internal fun todoId(id: Any?): Long {
    val n = when (id) {
        is Number -> id.toDouble().takeIf { it >= 1 && it <= MAX_ID && it == Math.floor(it) }?.toLong()
        is String -> id.trim().takeIf { it.length in 1..15 && it.all { c -> c in '0'..'9' } }?.toLong()?.takeIf { it >= 1 }
        else -> null
    }
    return n ?: throw IllegalArgumentException("todos.setDone: id must be a todo's id, as todos.list() gives it")
}

/** The largest whole number a double holds exactly (2^53). An id is far below it. */
private const val MAX_ID = 9_007_199_254_740_992.0

/**
 * The second argument of `todos.setDone`: true marks the todo done, false opens it again. Anything
 * else is an error. A missing value is not read as true: a mistyped variable would then tick a todo.
 */
internal fun todoDone(done: Any?): Boolean =
    done as? Boolean ?: throw IllegalArgumentException("todos.setDone: done must be true or false")

/** A text as a row holds it: no style marks, and at most [HostData.MAX_TEXT] characters. */
internal fun rowText(text: String): String {
    val plain = StyledText.strip(text)
    return if (plain.length <= HostData.MAX_TEXT) plain else plain.takeWhole(HostData.MAX_TEXT - 1) + "…"
}

/** A todo as `todos.list()` gives it. */
internal fun todoRow(t: Todo): Map<String, Any?> = mapOf("id" to t.id, "text" to rowText(t.text), "done" to t.done)

/** A note as `notes.list()` gives it. `created` is in seconds. */
internal fun noteRow(n: Note): Map<String, Any?> = mapOf("id" to n.id, "text" to rowText(n.text), "created" to n.created / 1000)

/**
 * A reminder as `reminders.list()` gives it. `at` is when it is next due, in seconds.
 * `repeats` is there only for one that repeats: how, in words ("every day", "every Mon and Thu"),
 * as the `reminders` command says it. A rule that can't be read has no words, as on the Reminders
 * card: such a reminder fires once.
 */
internal fun reminderRow(r: Reminder): Map<String, Any?> {
    val repeats = r.repeat?.let { rule -> runCatching { Repeat.label(rule) }.getOrNull() }
    return listOfNotNull("id" to r.id, "text" to rowText(r.text), "at" to r.at / 1000, repeats?.let { "repeats" to rowText(it) }).toMap()
}

/**
 * An event as `calendar.events()` gives it. `starts` and `ends` are in seconds, and an all-day
 * event starts and ends at midnights on the phone ([Agenda.shown]). So the list can be the `events`
 * of an `agenda` widget as it is.
 */
internal fun eventRow(e: ShownEvent): Map<String, Any?> =
    mapOf("id" to e.id, "title" to rowText(e.title), "starts" to e.starts, "ends" to e.ends, "allDay" to e.allDay)

/**
 * An unread feed item as `feeds.unread()` gives it, with the fields of an item of `rss.fetch`:
 * - `id` is the item's guid, and `date` is the date as the feed wrote it. A feed's dates have many
 *   forms, so Kolahal keeps no number for it.
 * - `feed` is the name of its feed ([feedName]).
 * - `link`, `date` and `summary` are nil when the feed gave none.
 * Kolahal's own row number and the item's read flag are left out.
 */
internal fun feedRow(item: FeedItem, feed: String): Map<String, Any?> = mapOf(
    "id" to rowText(item.guid),
    "feed" to rowText(feed),
    "title" to rowText(item.title),
    "link" to item.link?.let(::rowText),
    "date" to item.publishedText?.let(::rowText),
    "summary" to item.summary?.let(::rowText),
)

/**
 * A feed's name for a script: its title, or else its host without `www.`. Never its address: you
 * may have typed a name and a password into it (`https://me:secret@example.com/feed`).
 */
internal fun feedName(title: String?, url: String): String =
    title?.trim()?.takeIf { it.isNotEmpty() } ?: url.toHttpUrlOrNull()?.host?.removePrefix("www.").orEmpty()
