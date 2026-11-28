package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.productivity.Note
import com.mtwabpin.kolahal.productivity.Reminder
import com.mtwabpin.kolahal.productivity.Todo
import com.mtwabpin.kolahal.terminal.OwnTap
import com.mtwabpin.kolahal.terminal.OwnTaps
import com.mtwabpin.kolahal.widgets.WidgetSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/** What the built-in cards draw in the looks of their own: Next and Stack ([OwnDesigns]). */
class OwnDesignsTest {
    private val zone = ZoneId.of("Asia/Tokyo")

    /** A moment in [zone], in milliseconds. */
    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    // The store's order: open ones oldest first, then done ones.
    private val todos = listOf(
        Todo(2, "Book the dentist", false),
        Todo(5, "Renew the passport", false),
        Todo(6, "Water the plants", false),
        Todo(7, "Call the plumber", false),
        Todo(1, "Pay electricity bill", true),
        Todo(3, "Book train tickets", true),
        Todo(4, "Post the letter", true),
    )

    /** The ring counts the ticked ones of all; the next is the oldest open one, and the others follow in order. */
    @Test
    fun theNextTodoComesFirst() {
        val t = OwnDesigns.nextTodos(todos)
        assertEquals(3, t.done)
        assertEquals(7, t.total)
        assertEquals(4, t.left)
        assertEquals("3/7", t.count)
        assertEquals(3f / 7, t.fraction, 0.0001f)
        assertEquals("Book the dentist", t.next?.text)
        assertEquals(listOf(2L, 5L, 6L, 7L), t.open.map { it.id })
        assertNull(t.undo)
        assertEquals("4 left", OwnDesigns.leftWords(t))
        assertEquals("then Renew the passport · +2", OwnDesigns.thenWords(t))
        assertEquals("Todos: 4 open, 3 of 7 done. Next: Book the dentist", OwnDesigns.nextSaid(t))
    }

    /** The check sends the words a row sends in the other looks, and Undo those of a second tap. */
    @Test
    fun theCheckAndUndoSendTheRowsWords() {
        val next = todos.first()
        assertEquals("done #2", OwnDesigns.doneTap(next).args)
        assertEquals("mark as done", OwnDesigns.doneTap(next).label)
        assertEquals(OwnTap.SetTodo(2, done = true), OwnTaps.parse(WidgetSource.TODOS, OwnDesigns.doneTap(next).args!!))
        assertEquals(OwnTap.SetTodo(2, done = false), OwnTaps.parse(WidgetSource.TODOS, OwnDesigns.undoTap(next).args!!))
        assertEquals("mark as not done", OwnDesigns.undoTap(next).label)
    }

    /**
     * The todos under the top row are rows to tap, as tall as a list's ([TAP_ROW_DP]), as many as fit.
     * The rooms are a 4×2's and a 4×4's on a phone 411 dp wide (192 and 392 dp, 28 of them its
     * margins, 64 its top row and the gap under it), with the text at its usual size (a line of 20 dp).
     */
    @Test
    fun theRowsUnderTheTopRowAreRowsToTap() {
        assertEquals(TAP_ROW_DP, OwnDesigns.followRowDp(20))
        assertEquals("a larger font", 50, OwnDesigns.followRowDp(50))
        // A 4×2: two when they are all there are, else one and "+3 more".
        assertEquals(2, OwnDesigns.followShown(2, 100, 20))
        assertEquals(1, OwnDesigns.followShown(3, 100, 20))
        assertEquals(1, OwnDesigns.followShown(1, 100, 20))
        assertEquals(0, OwnDesigns.followShown(0, 100, 20))
        // A 4×4: six when they are all there are, and six of eight with "+2 more".
        assertEquals(6, OwnDesigns.followShown(6, 300, 20))
        assertEquals(6, OwnDesigns.followShown(8, 300, 20))
        // Ten dp less, and the line for "+3 more" takes the sixth row's place.
        assertEquals(5, OwnDesigns.followShown(8, 290, 20))
    }

    /** A tall card names the next todo and the one after it in its top row, and its rows go on from the third: none is there twice. */
    @Test
    fun aTallCardShowsNoTodoTwice() {
        val t = OwnDesigns.nextTodos(todos)
        // The top row's line has no count: the rows and the line under them carry on.
        assertEquals("then Renew the passport", OwnDesigns.thenWords(t, rest = false))
        assertEquals(listOf("Water the plants", "Call the plumber"), OwnDesigns.followRows(t).map { it.text })
        val named = listOf(t.next!!.text, "Renew the passport") + OwnDesigns.followRows(t).map { it.text }
        assertEquals(named.distinct(), named)
        assertEquals(t.open.map { it.text }, named)
        // What is left out is counted: both rows shown, one, none.
        assertEquals(0, OwnDesigns.followMore(t, 2))
        assertEquals(1, OwnDesigns.followMore(t, 1))
        assertEquals(2, OwnDesigns.followMore(t, 0))
        // Two open or fewer: no rows, and nothing left out.
        val two = OwnDesigns.nextTodos(todos.map { if (it.id < 6L) it.copy(done = true) else it })
        assertEquals(emptyList<Todo>(), OwnDesigns.followRows(two))
        assertEquals(0, OwnDesigns.followMore(two, 0))
        val none = OwnDesigns.nextTodos(todos.map { it.copy(done = true) })
        assertEquals(0, OwnDesigns.followMore(none, 0))
        // A long list counts every open one, also those the card doesn't hold.
        val many = OwnDesigns.nextTodos((1..80L).map { Todo(it, "Todo $it", false) })
        assertEquals(80 - 2 - 5, OwnDesigns.followMore(many, 5))
    }

    /** The rows share the room they have, up to a list's tallest row; the line for "+3 more" is kept first. */
    @Test
    fun theRowsShareTheirRoom() {
        // A 4×2 with one row and "+3 more": 100 less the line and its gap is more than a row may take.
        assertEquals(WidgetWords.ROW_MAX_DP.toFloat(), OwnDesigns.followHeightDp(1, 100, 20, more = true), 0f)
        // Two rows and nothing left out: they share 100 dp with the gap between them.
        assertEquals(49f, OwnDesigns.followHeightDp(2, 100, 20, more = false), 0f)
        // Never less than a row to tap, and none shown is that too.
        assertEquals(TAP_ROW_DP.toFloat(), OwnDesigns.followHeightDp(5, 250, 20, more = true), 0f)
        assertEquals(TAP_ROW_DP.toFloat(), OwnDesigns.followHeightDp(0, 100, 20, more = false), 0f)
        // What they take fits the room: rows, gaps and the line.
        for ((shown, room, more) in listOf(Triple(1, 100, true), Triple(2, 100, false), Triple(5, 300, true), Triple(6, 300, false))) {
            val used = shown * OwnDesigns.followHeightDp(shown, room, 20, more) + (shown - 1) * ROW_GAP_DP +
                if (more) OwnDesigns.FOOT_DP + ROW_GAP_DP else 0
            assertTrue("$shown rows in $room dp take $used", used <= room + 0.01f)
        }
    }

    /** A tick brings the one after; Undo stays for the ticked one while it is done. */
    @Test
    fun aTickBringsTheNextAndKeepsAnUndo() {
        val ticked = todos.map { if (it.id == 2L) it.copy(done = true) else it }
        val t = OwnDesigns.nextTodos(ticked, last = 2)
        assertEquals("Renew the passport", t.next?.text)
        assertEquals("4/7", t.count)
        assertEquals("Book the dentist", t.undo?.text)
        // Opened again elsewhere: nothing to undo.
        assertNull(OwnDesigns.nextTodos(todos, last = 2).undo)
        // A todo removed in the meantime: nothing to undo either.
        assertNull(OwnDesigns.nextTodos(ticked.filter { it.id != 2L }, last = 2).undo)
    }

    @Test
    fun theLastOpenOneAndNoneOpen() {
        val one = OwnDesigns.nextTodos(todos.map { if (it.id != 7L) it.copy(done = true) else it })
        assertEquals("Call the plumber", one.next?.text)
        assertNull(OwnDesigns.thenWords(one))
        assertEquals("1 left", OwnDesigns.leftWords(one))
        // Two open: "then" with no count after it.
        assertEquals(
            "then Call the plumber",
            OwnDesigns.thenWords(OwnDesigns.nextTodos(todos.map { if (it.id < 6L) it.copy(done = true) else it }))
        )
        val none = OwnDesigns.nextTodos(todos.map { it.copy(done = true) })
        assertNull(none.next)
        assertEquals("7/7", none.count)
        assertEquals(1f, none.fraction, 0f)
        assertEquals("All done", OwnDesigns.leftWords(none))
        assertEquals("Todos: nothing open, 7 of 7 done", OwnDesigns.nextSaid(none))
    }

    /** A long list draws as many as a card holds, and counts them all. */
    @Test
    fun aLongListIsCutButCounted() {
        val many = (1..80L).map { Todo(it, "Todo $it", false) }
        val t = OwnDesigns.nextTodos(many)
        assertEquals(OwnCards.MAX_TODOS, t.open.size)
        assertEquals(80, t.left)
        assertEquals("then Todo 2 · +78", OwnDesigns.thenWords(t))
    }

    private val reminders = listOf(
        Reminder(7, at(14, 16, 30), "Call the bank"),
        Reminder(2, at(15, 9), "Water plants"),
        Reminder(9, at(16, 19), "Bins", repeat = "mon", time = "19:00"),
        Reminder(4, at(20, 8), "Pay the rent"),
        Reminder(5, at(21, 8), "Later"),
    )

    /** The pill says the time to the next one, and empties over its last hour. */
    @Test
    fun theTimelineLeadsWithAPill() {
        val now = at(14, 15, 48)
        val t = OwnDesigns.timeline(reminders, now, zone, clock24 = true, locale = Locale.US)!!
        assertEquals("Call the bank", t.next.text)
        assertEquals("in 42 min", t.next.until)
        assertEquals("16:30", t.next.time)
        assertEquals(0.7f, t.next.left, 0.0001f)
        // The next three after it, with their day.
        assertEquals(listOf("Water plants", "Bins", "Pay the rent"), t.later.map { it.text })
        assertEquals(listOf("Sun", "Mon", "Fri"), t.later.map { it.time })
        assertEquals("16:30 · then Water plants, Sun", OwnDesigns.pillLine(t))
        // A minute later the pill has less in it.
        val later = OwnDesigns.timeline(reminders, now + 60_000L, zone, clock24 = true, locale = Locale.US)!!
        assertEquals("in 41 min", later.next.until)
        assertEquals(41f / 60, later.next.left, 0.0001f)
        assertNull(OwnDesigns.timeline(emptyList(), now, zone, clock24 = true))
    }

    @Test
    fun thePillIsFullAnHourBeforeAndEmptyAtItsTime() {
        val now = at(14, 10)
        assertEquals(1f, OwnDesigns.pillLeft(at(14, 13), now), 0f)
        assertEquals(1f, OwnDesigns.pillLeft(now + OwnDesigns.PILL_MS, now), 0f)
        assertEquals(0.5f, OwnDesigns.pillLeft(now + 30 * 60_000L, now), 0f)
        assertEquals(0f, OwnDesigns.pillLeft(now, now), 0f)
        assertEquals(0f, OwnDesigns.pillLeft(now - 60_000L, now), 0f)
        assertEquals("in 3 h", OwnDesigns.untilWords(at(14, 13), now))
        assertEquals("in 2 days", OwnDesigns.untilWords(at(16, 12), now))
        assertEquals("now", OwnDesigns.untilWords(now - 1, now))
    }

    /** A reminder that repeats says how; the last one alone has no "then". */
    @Test
    fun aRepeatingReminderSaysHowAndTheLastStandsAlone() {
        val now = at(14, 16, 5)
        val bins = OwnDesigns.timeline(reminders.drop(2), now, zone, clock24 = true, locale = Locale.US)!!
        assertEquals("Mon 16 Nov 19:00 · every Mon", bins.next.time)
        val alone = OwnDesigns.timeline(reminders.take(1), now, zone, clock24 = false, locale = Locale.US)!!
        assertEquals(emptyList<OwnDesigns.Later>(), alone.later)
        assertEquals("4:30 PM", OwnDesigns.pillLine(alone))
        // A stored rule that can't be read has no words, and the card is still drawn.
        val odd = OwnDesigns.timeline(
            listOf(Reminder(1, at(14, 18, 30), "Odd", repeat = "month:x")),
            now,
            zone,
            clock24 = true,
            locale = Locale.US
        )!!
        assertEquals("18:30", odd.next.time)
    }

    private val notes = listOf(
        Note(5, "Parcel comes Tuesday.\nThe gate code is with the guard.", at(24, 18, 48)),
        Note(4, "Wi-Fi: blue-heron-42", at(12, 9)),
        Note(3, "Book: The Dispossessed", at(10, 9)),
        Note(1, "Old", at(1, 9))
    )

    /** The newest note on top, with its date written out, and a sheet behind for each other note, two at most. */
    @Test
    fun theStackHasTheNewestOnTop() {
        val s = OwnDesigns.stack(notes, zone, clock24 = true, locale = Locale.US)!!
        assertEquals("Parcel comes Tuesday.\nThe gate code is with the guard.", s.text)
        assertEquals("Tue 24 Nov 18:48", s.at)
        assertEquals(4, s.count)
        assertEquals(2, s.behind)
        assertEquals("4 notes", OwnDesigns.countWords(s.count))
        assertEquals(1, OwnDesigns.stack(notes.take(2), zone, locale = Locale.US)!!.behind)
        val one = OwnDesigns.stack(notes.take(1), zone, clock24 = false, locale = Locale.US)!!
        assertEquals(0, one.behind)
        assertEquals("Tue 24 Nov 6:48 PM", one.at)
        assertEquals("1 note", OwnDesigns.countWords(one.count))
        assertNull(OwnDesigns.stack(emptyList()))
        // A long note is cut as the Plain card cuts it.
        assertEquals(500, OwnDesigns.stack(listOf(Note(1, "word ".repeat(400), 1)))!!.text.length)
    }

    /** + puts `note ` in the prompt: the words are the Notes card's, and nothing runs. */
    @Test
    fun plusStartsANewNote() {
        assertEquals("new", OwnDesigns.NEW_NOTE.args)
        assertEquals("new note", OwnDesigns.NEW_NOTE.label)
        assertEquals(OwnTap.Fill("note "), OwnTaps.parse(WidgetSource.NOTES, OwnDesigns.NEW_NOTE.args!!))
    }
}
