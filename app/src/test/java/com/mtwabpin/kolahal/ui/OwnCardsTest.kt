package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.productivity.Note
import com.mtwabpin.kolahal.productivity.Reminder
import com.mtwabpin.kolahal.productivity.Todo
import com.mtwabpin.kolahal.terminal.OwnTap
import com.mtwabpin.kolahal.terminal.OwnTaps
import com.mtwabpin.kolahal.widgets.WidgetSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
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
        assertTrue(listOf(WidgetSource.TODOS, WidgetSource.REMINDERS, WidgetSource.NOTES).all(OwnCards::draws))
        // Battery and network has its own card, and an id from a newer version has none.
        assertFalse(OwnCards.draws(WidgetSource.BATTERY))
        assertFalse(OwnCards.draws("comic"))
        assertFalse(OwnCards.draws(null))
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
    }

    /** A todo ticked on the card keeps its place until the page is turned away, and a tap opens it again. */
    @Test fun aTickedTodoStaysForAnUndo() {
        val ticked = todos.map { if (it.id == 4L) it.copy(done = true) else it }.sortedBy { it.done }
        val spec = OwnCards.todos(ticked, kept = setOf(4))
        assertEquals(listOf("Buy milk", "Call the bank", "Water the plants"), spec.items.map { it.title })
        assertEquals(listOf("done #1", "undo #4", "done #6"), spec.items.map { it.tap?.args })
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
