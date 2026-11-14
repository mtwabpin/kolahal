package com.mtwabpin.kolahal.ui

import android.media.session.PlaybackState
import com.mtwabpin.kolahal.control.NowPlaying
import com.mtwabpin.kolahal.productivity.AgendaEvent
import com.mtwabpin.kolahal.productivity.Note
import com.mtwabpin.kolahal.productivity.Reminder
import com.mtwabpin.kolahal.productivity.Todo
import com.mtwabpin.kolahal.terminal.OwnTap
import com.mtwabpin.kolahal.terminal.OwnTaps
import com.mtwabpin.kolahal.widgets.Shortcut
import com.mtwabpin.kolahal.widgets.Shortcuts
import com.mtwabpin.kolahal.widgets.WidgetSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** What the built-in cards draw for Kolahal's own data ([OwnCards]). */
class OwnCardsTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    /** A moment in [zone], in milliseconds. */
    private fun at(day: Int, hour: Int, minute: Int = 0, second: Int = 0): Long =
        LocalDateTime.of(2026, 11, day, hour, minute, second).atZone(zone).toInstant().toEpochMilli()

    // The store's order: open ones oldest first, then done ones.
    private val todos = listOf(Todo(1, "Buy milk", false), Todo(4, "Call the bank", false), Todo(6, "Water the plants", false), Todo(3, "Pay rent", true), Todo(2, "Post letter", true))

    @Test fun theCardsOwnWidgetDraws() {
        assertTrue(listOf(WidgetSource.TODOS, WidgetSource.REMINDERS, WidgetSource.NOTES, WidgetSource.SHORTCUTS, WidgetSource.MEDIA, WidgetSource.AGENDA).all(OwnCards::draws))
        // Battery and network has its own card, and an id from a newer version has none.
        assertFalse(OwnCards.draws(WidgetSource.BATTERY))
        assertFalse(OwnCards.draws("comic"))
        assertFalse(OwnCards.draws(null))
    }

    private val shortcuts = listOf(Shortcut("Torch", "torch on", "🔦"), Shortcut("Add milk", "todo buy milk"), Shortcut("Maps", "open maps", "M"))

    @Test fun shortcutsHasAButtonForEach() {
        val spec = OwnCards.shortcuts(shortcuts)!!
        assertEquals("Shortcuts", spec.base.title)
        assertEquals(listOf("Torch", "Add milk", "Maps"), spec.items.map { it.label })
        assertEquals(listOf("🔦", null, "M"), spec.items.map { it.icon })
        // A button names its place in the list, and the view model knows these words.
        assertEquals(listOf("run 0", "run 1", "run 2"), spec.items.map { it.tap.args })
        assertEquals(
            listOf(OwnTap.RunShortcut(0), OwnTap.RunShortcut(1), OwnTap.RunShortcut(2)),
            spec.items.map { OwnTaps.parse(WidgetSource.SHORTCUTS, it.tap.args!!) },
        )
        assertTrue(spec.items.all { it.tap.url == null })
        // Never more buttons than the widget has.
        assertEquals(Shortcuts.MAX, OwnCards.shortcuts((1..12).map { Shortcut("S$it", "echo $it") })!!.items.size)
    }

    /** A command is never in a card's description: only its place in your list. So nothing that reads a description can run or change one. */
    @Test fun aShortcutsCardNeverHoldsACommand() {
        val text = OwnCards.shortcuts(shortcuts).toString()
        shortcuts.forEach { assertFalse(it.command, text.contains(it.command)) }
        // Even when the label is the command's first word, the whole command is not there.
        assertFalse(OwnCards.shortcuts(listOf(Shortcut("todo", "todo buy milk and eggs"))).toString().contains("buy milk"))
    }

    /** With no shortcuts there are no buttons: the card says how to add some. */
    @Test fun anEmptyShortcutsListIsAQuietCard() {
        assertNull(OwnCards.shortcuts(emptyList()))
        val quiet = OwnCards.quiet(WidgetSource.SHORTCUTS, OwnCards.NO_SHORTCUTS)
        assertEquals("Shortcuts", quiet.base.title)
        assertEquals("No shortcuts yet. Tap to add commands.", quiet.empty)
        // A tap on that card asks for the editor.
        assertEquals(OwnTap.EditShortcuts, OwnTaps.parse(WidgetSource.SHORTCUTS, OwnTaps.EDIT))
    }

    /** While a command is typed in the editor, the commands that start so are offered. */
    @Test fun theEditorOffersCommandsThatStartAsTyped() {
        val commands = listOf("agenda", "alarm", "alias", "apps", "todo", "torch", "weather")
        assertEquals(listOf("todo", "torch"), ShortcutPicks.matching("to", commands))
        assertEquals(listOf("torch"), ShortcutPicks.matching(" TOR", commands))
        assertEquals(listOf("agenda", "alarm", "alias", "apps"), ShortcutPicks.matching("a", commands))
        // Nothing to offer: no text, a whole command, a second word, or no match.
        assertEquals(emptyList<String>(), ShortcutPicks.matching("", commands))
        assertEquals(emptyList<String>(), ShortcutPicks.matching("torch", commands))
        assertEquals(emptyList<String>(), ShortcutPicks.matching("todo buy", commands))
        assertEquals(emptyList<String>(), ShortcutPicks.matching("zz", commands))
        assertEquals(ShortcutPicks.MAX, ShortcutPicks.matching("c", (1..20).map { "c$it" }).size)
    }

    private fun session(state: Int, title: String? = "So What", artist: String? = "Miles Davis", position: Long = 61_000, duration: Long = 562_000, speed: Float = 1f) =
        NowPlaying(pkg = "com.example.player", app = "Player", title = title, artist = artist, state = state, position = position, positionAt = 1_800_000_000_000L, speed = speed, duration = duration, idleSince = 1_799_999_000_000L)

    @Test fun nowPlayingShowsTheSession() {
        val spec = OwnCards.media(session(PlaybackState.STATE_PLAYING))
        assertEquals("Now playing", spec.base.title)
        assertEquals("So What", spec.title)
        assertEquals("Miles Davis", spec.artist)
        assertEquals("Player", spec.app)
        assertTrue(spec.playing)
        assertEquals(61_000L, spec.position)
        assertEquals(1_800_000_000_000L, spec.positionAt)
        assertEquals(1f, spec.speed)
        assertEquals(562_000L, spec.duration)
        // Paused: the same track, standing still.
        val paused = OwnCards.media(session(PlaybackState.STATE_PAUSED))
        assertFalse(paused.playing)
        assertEquals(0f, paused.speed)
        assertEquals(61_000L, paused.position)
    }

    /** While a track buffers, the button says "pause", and the position does not move by itself. */
    @Test fun aBufferingTrackStandsStill() {
        val spec = OwnCards.media(session(PlaybackState.STATE_BUFFERING, speed = 1.5f))
        assertTrue(spec.playing)
        assertEquals(0f, spec.speed)
        assertEquals(1.5f, OwnCards.media(session(PlaybackState.STATE_PLAYING, speed = 1.5f)).speed)
    }

    /** A player may give no title, no position and no length. */
    @Test fun whatAPlayerDoesNotSayIsLeftOut() {
        val spec = OwnCards.media(session(PlaybackState.STATE_PLAYING, title = " ", artist = null, position = -1, duration = -1))
        assertNull(spec.title)
        assertNull(spec.artist)
        assertEquals("Player", spec.app)
        assertEquals(0L, spec.position)
        assertEquals(0L, spec.duration)
    }

    /** The card keeps a paused session, however old. One with no state or in an error has nothing to control. */
    @Test fun nowPlayingShowsASessionThatCanBeControlled() {
        assertTrue(OwnCards.shows(session(PlaybackState.STATE_PLAYING)))
        assertTrue(OwnCards.shows(session(PlaybackState.STATE_PAUSED)))
        assertTrue(OwnCards.shows(session(PlaybackState.STATE_STOPPED)))
        assertTrue(OwnCards.shows(session(PlaybackState.STATE_BUFFERING)))
        assertFalse(OwnCards.shows(session(PlaybackState.STATE_NONE)))
        assertFalse(OwnCards.shows(session(PlaybackState.STATE_ERROR)))
        assertFalse(OwnCards.shows(null))
        assertEquals("Nothing playing", OwnCards.quiet(WidgetSource.MEDIA, OwnCards.NOTHING_PLAYING).empty)
        assertEquals("Allow notification access", OwnCards.quiet(WidgetSource.MEDIA, OwnCards.ALLOW_NOTIFICATIONS).empty)
        // The card's own taps: its three buttons, the player, and the access.
        val words = listOf(OwnTaps.PREV, OwnTaps.TOGGLE, OwnTaps.NEXT, OwnTaps.OPEN, OwnTaps.ALLOW)
        assertEquals(listOf("prev", "toggle", "next", "open", "allow"), words)
        assertTrue(words.all { OwnTaps.parse(WidgetSource.MEDIA, it) != null })
    }

    /** Midnight UTC on a day of November 2026, in milliseconds: where the calendar keeps an all-day event. */
    private fun utcDay(day: Int): Long = LocalDate.of(2026, 11, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun timed(id: Long, title: String, begin: Long, end: Long) = AgendaEvent(id, title, begin, end, allDay = false)
    private fun allDay(id: Long, title: String, first: Int, last: Int) = AgendaEvent(id, title, utcDay(first), utcDay(last + 1), allDay = true)

    @Test fun theAgendaShowsTheNextDaysEvents() {
        val now = at(14, 16, 5)
        val events = listOf(
            allDay(8, "Yesterday", 13, 13),
            allDay(5, "Trip", 13, 16),
            allDay(4, "Birthday", 14, 14),
            timed(2, "Breakfast", at(14, 8), at(14, 9)),
            timed(3, "Workshop", at(14, 15), at(14, 18)),
            timed(1, "Standup", at(14, 17), at(14, 17, 30)),
            timed(7, "Ping", at(15, 9), at(15, 9)),
            timed(6, "Later", at(17, 10), at(17, 11)),
        )
        val spec = OwnCards.agenda(events, zone, now, days = 3)
        assertEquals("Agenda", spec.base.title)
        assertEquals(3, spec.days)
        // What has ended and what is past the three days is left out. An event of several days is there once.
        assertEquals(listOf("Trip", "Birthday", "Workshop", "Standup", "Ping"), spec.events.map { it.title })
        assertEquals(listOf(true, true, false, false, false), spec.events.map { it.allDay })
        // Times are in seconds. An event with no length has no end.
        val standup = spec.events[3]
        assertEquals(at(14, 17) / 1000, standup.starts)
        assertEquals(at(14, 17, 30) / 1000, standup.ends)
        assertNull(spec.events[4].ends)
        // An event's tap names it and its start, in words the view model knows.
        assertEquals("event 1 ${at(14, 17)}", standup.tap?.args)
        assertEquals(OwnTap.OpenEvent(1, at(14, 17)), OwnTaps.parse(WidgetSource.AGENDA, standup.tap!!.args!!))
        assertTrue(spec.events.all { it.tap?.url == null && it.tap?.label == "open event" })
    }

    /** The calendar keeps an all-day event at midnight UTC. The card puts it at midnight where the phone is. */
    @Test fun anAllDayEventIsOnItsDayInAnyZone() {
        fun midnight(day: Int, z: ZoneId) = LocalDate.of(2026, 11, day).atStartOfDay(z).toEpochSecond()
        val birthday = OwnCards.agenda(listOf(allDay(4, "Birthday", 14, 14)), zone, at(14, 16, 5), days = 3).events.single()
        assertEquals(midnight(14, zone), birthday.starts)
        assertEquals(midnight(15, zone), birthday.ends)
        // West of UTC, midnight UTC on the 14th is still the 13th. The event is on the 14th all the same.
        val west = ZoneId.of("America/Los_Angeles")
        val there = LocalDateTime.of(2026, 11, 14, 10, 0).atZone(west).toInstant().toEpochMilli()
        val same = OwnCards.agenda(listOf(allDay(4, "Birthday", 14, 14)), west, there, days = 3).events.single()
        assertEquals(midnight(14, west), same.starts)
        assertEquals(midnight(15, west), same.ends)
        // One that started before today starts today, and ends after its last day.
        val trip = OwnCards.agenda(listOf(allDay(5, "Trip", 12, 16)), zone, at(14, 16, 5), days = 3).events.single()
        assertEquals(midnight(14, zone), trip.starts)
        assertEquals(midnight(17, zone), trip.ends)
        // The tap still names the event's own start, as the calendar has it.
        assertEquals("event 5 ${utcDay(12)}", trip.tap?.args)
    }

    @Test fun theAgendaSaysWhenThereIsNothing() {
        val none = OwnCards.agenda(emptyList(), zone, at(14, 16, 5), days = 3)
        assertTrue(none.events.isEmpty())
        assertEquals("No events in the next 3 days", none.empty)
        assertEquals("Nothing left today", OwnCards.agenda(emptyList(), zone, at(14, 16, 5), days = 1).empty)
        // Only events that have ended: the same.
        assertTrue(OwnCards.agenda(listOf(timed(2, "Breakfast", at(14, 8), at(14, 9))), zone, at(14, 16, 5), days = 3).events.isEmpty())
        assertEquals("Allow calendar access", OwnCards.quiet(WidgetSource.AGENDA, OwnCards.ALLOW_CALENDAR).empty)
    }

    @Test fun aFullCalendarIsCut() {
        val many = (1..30L).map { timed(it, "Meeting $it " + "x".repeat(100), at(15, 9) + it * 60_000, at(15, 10) + it * 60_000) }
        val spec = OwnCards.agenda(many, zone, at(14, 16, 5), days = 3)
        assertEquals(20, spec.events.size)
        assertTrue(spec.events.all { it.title.length == 80 && it.title.endsWith("…") })
        assertEquals("event 1 ${at(15, 9) + 60_000}", spec.events.first().tap?.args)
    }

    @Test fun todosShowsTheOpenOnes() {
        val spec = OwnCards.todos(todos)
        assertEquals("Todos", spec.base.title)
        assertEquals(listOf("Buy milk", "Call the bank", "Water the plants"), spec.items.map { it.title })
        // A row names its todo by id, and its words are ones the view model knows.
        assertEquals(listOf("done #1", "done #4", "done #6"), spec.items.map { it.tap?.args })
        assertEquals(
            listOf(OwnTap.SetTodo(1, true), OwnTap.SetTodo(4, true), OwnTap.SetTodo(6, true)),
            spec.items.map { OwnTaps.parse(WidgetSource.TODOS, it.tap!!.args!!) },
        )
        assertTrue(spec.items.all { it.tap?.url == null && it.tap?.label == "mark as done" })
        // Each row has a checkbox, not ticked.
        assertEquals(listOf(false, false, false), spec.items.map { it.done })
    }

    /** The picker lists a built-in widget only when its card is drawn, under the widget's one name. */
    @Test fun thePickerListsTheBuiltInWidgetsThatAreDrawn() {
        assertEquals(WidgetSource.BATTERY, pickerBuiltinRows.first().id)
        assertEquals(pickerBuiltinRows.size, pickerBuiltinRows.map { it.id }.toSet().size)
        pickerBuiltinRows.forEach { row ->
            assertTrue(row.id, row.id == WidgetSource.BATTERY || OwnCards.draws(row.id))
            assertEquals(WidgetSource.builtinTitle(row.id), row.title)
            assertEquals("Built-in", row.what)
            assertTrue(WidgetSource.isSource(row.source))
            // The search is in lowercase, as the query is, and finds the widget by its name.
            assertEquals(row.search.lowercase(), row.search)
            assertTrue(row.search.contains(row.title.lowercase()))
        }
        // Every card that is drawn can be added.
        val listed = pickerBuiltinRows.map { it.id }.toSet()
        val ids = listOf(WidgetSource.SHORTCUTS, WidgetSource.MEDIA, WidgetSource.TODOS, WidgetSource.AGENDA, WidgetSource.REMINDERS, WidgetSource.NOTES)
        assertEquals(ids.filter(OwnCards::draws).toSet(), listed - WidgetSource.BATTERY)
    }

    /** A todo ticked on the card keeps its place until the page is turned away, and a tap opens it again. */
    @Test fun aTickedTodoStaysForAnUndo() {
        val ticked = todos.map { if (it.id == 4L) it.copy(done = true) else it }.sortedBy { it.done }
        val spec = OwnCards.todos(ticked, kept = setOf(4))
        assertEquals(listOf("Buy milk", "Call the bank", "Water the plants"), spec.items.map { it.title })
        assertEquals(listOf("done #1", "undo #4", "done #6"), spec.items.map { it.tap?.args })
        assertEquals(listOf(false, true, false), spec.items.map { it.done })
        assertEquals("mark as not done", spec.items[1].tap?.label)
        // Todos that were done before are not on the card, and nor is this one after the page was left.
        assertEquals(listOf("Buy milk", "Water the plants"), OwnCards.todos(ticked).items.map { it.title })
        // Opened again while still kept: it is an open todo like the others.
        assertEquals(listOf("done #1", "done #4", "done #6"), OwnCards.todos(todos, kept = setOf(4)).items.map { it.tap?.args })
    }

    @Test fun todosSaysWhenThereIsNothing() {
        assertEquals("No todos yet", OwnCards.todos(emptyList()).empty)
        assertTrue(OwnCards.todos(emptyList()).items.isEmpty())
        val allDone = todos.filter { it.done }
        assertEquals("Nothing open", OwnCards.todos(allDone).empty)
        assertTrue(OwnCards.todos(allDone).items.isEmpty())
        assertEquals("Todos: no todos yet", OwnCards.todosSaid(emptyList()))
        assertEquals("Todos: nothing open", OwnCards.todosSaid(allDone))
        assertEquals("Todos: 3 open", OwnCards.todosSaid(todos))
    }

    @Test fun aLongTodoListIsCut() {
        val many = (1..80L).map { Todo(it, "Todo $it", false) }
        assertEquals(OwnCards.MAX_TODOS, OwnCards.todos(many).items.size)
        assertEquals("Todos: 80 open", OwnCards.todosSaid(many))
    }

    @Test fun aReminderSaysWhenItIsDue() {
        val now = at(14, 16, 5)
        assertEquals("18:30", OwnCards.whenLabel(at(14, 18, 30), now, zone, clock24 = true, locale = Locale.US))
        assertEquals("6:30 PM", OwnCards.whenLabel(at(14, 18, 30), now, zone, clock24 = false, locale = Locale.US))
        assertEquals("Tomorrow 09:00", OwnCards.whenLabel(at(15, 9), now, zone, clock24 = true, locale = Locale.US))
        assertEquals("Fri 20 Nov 08:00", OwnCards.whenLabel(at(20, 8), now, zone, clock24 = true, locale = Locale.US))
        // The day is the phone's day, not UTC's: 01:00 in India is still the day before in UTC.
        assertEquals("Tomorrow 01:00", OwnCards.whenLabel(at(15, 1), now, zone, clock24 = true, locale = Locale.US))
    }

    /** The time to a reminder is in whole minutes, rounded up, so "in 10m" stays until 9 minutes are left. */
    @Test fun theTimeToAReminderCountsDown() {
        val now = at(14, 16, 5)
        assertEquals("in 25m", OwnCards.until(at(14, 16, 30), now))
        assertEquals("in 2h 25m", OwnCards.until(at(14, 18, 30), now))
        assertEquals("in 1d 2h", OwnCards.until(at(15, 18, 30), now))
        assertEquals("in 10m", OwnCards.until(now + 9 * 60_000L + 1, now))
        assertEquals("in 9m", OwnCards.until(now + 9 * 60_000L, now))
        assertEquals("in 1m", OwnCards.until(now + 1, now))
        // Its time has come: the alarm fires any moment.
        assertEquals("now", OwnCards.until(now, now))
        assertEquals("now", OwnCards.until(now - 90_000L, now))
    }

    @Test fun remindersShowsTheNextThree() {
        val now = at(14, 16, 5)
        val list = listOf(
            Reminder(7, at(14, 16, 30), "Tea"),
            Reminder(2, at(15, 9), "Call mom"),
            Reminder(9, at(16, 19), "Bins", repeat = "mon", time = "19:00"),
            Reminder(4, at(20, 8), "Standup"),
        )
        val spec = OwnCards.reminders(list, now, zone, clock24 = true, locale = Locale.US)
        assertEquals("Reminders", spec.base.title)
        assertEquals(listOf("Tea", "Call mom", "Bins"), spec.items.map { it.title })
        assertEquals(listOf("in 25m", "in 16h 55m", "in 2d 2h"), spec.items.map { it.trailing })
        assertEquals("16:30", spec.items[0].detail)
        assertEquals("Tomorrow 09:00", spec.items[1].detail)
        // A reminder that repeats says how.
        assertEquals("Mon 16 Nov 19:00 · every Mon", spec.items[2].detail)
        // A stored rule that can't be read has no words, and the card is still drawn.
        val odd = OwnCards.reminders(listOf(Reminder(1, at(14, 18, 30), "Odd", repeat = "month:x")), now, zone, clock24 = true, locale = Locale.US)
        assertEquals("18:30", odd.items.single().detail)
        // The rows have no tap of their own: a tap anywhere opens the list.
        assertTrue(spec.items.all { it.tap == null })
        assertEquals("No reminders", OwnCards.reminders(emptyList(), now, zone, clock24 = true).empty)
        // A minute later the first row says so, and nothing else changed.
        val later = OwnCards.reminders(list, now + 60_000L, zone, clock24 = true, locale = Locale.US)
        assertEquals("in 24m", later.items[0].trailing)
        assertEquals(spec.items.map { it.detail }, later.items.map { it.detail })
    }

    private val notes = listOf(Note(5, "Buy stamps\nand envelopes", 5_000), Note(4, "Wi-Fi: blue-heron-42", 4_000), Note(3, "Book: The Dispossessed", 3_000), Note(1, "Old", 1_000))

    @Test fun notesShowsTheNewest() {
        val one = OwnCards.notes(notes)!!
        assertEquals("Notes", one.base.title)
        assertEquals("Buy stamps\nand envelopes", one.text)
        assertEquals("+3 more", one.caption)
        // A large card has the newest three, each on one line.
        val three = OwnCards.notes(notes, count = 3)!!
        assertEquals("• Buy stamps and envelopes\n• Wi-Fi: blue-heron-42\n• Book: The Dispossessed", three.text)
        assertEquals("+1 more", three.caption)
        // Never more than three, and no caption when all are shown.
        assertEquals(three, OwnCards.notes(notes, count = 9))
        assertNull(OwnCards.notes(notes.take(2), count = 3)!!.caption)
        // One note in a large card is shown whole, as in a small one.
        assertEquals("Buy stamps\nand envelopes", OwnCards.notes(notes.take(1), count = 3)!!.text)
    }

    @Test fun aLongNoteIsCut() {
        val long = Note(1, "word ".repeat(400), 1)
        val one = OwnCards.notes(listOf(long))!!
        assertEquals(500, one.text.length)
        assertTrue(one.text.endsWith("…"))
        val line = OwnCards.notes(listOf(long, long), count = 2)!!.text.lines()
        assertEquals(2, line.size)
        assertTrue(line.all { it.length <= 122 && it.endsWith("…") })
    }

    /** With no notes there is no text card: the card says "No notes yet" in the middle. */
    @Test fun noNotesIsAQuietCard() {
        assertNull(OwnCards.notes(emptyList()))
        val quiet = OwnCards.quiet(WidgetSource.NOTES, "No notes yet")
        assertEquals("Notes", quiet.base.title)
        assertTrue(quiet.items.isEmpty())
        assertEquals("No notes yet", quiet.empty)
    }
}
