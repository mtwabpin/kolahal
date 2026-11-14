package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.control.MediaRepository
import com.mtwabpin.kolahal.productivity.AgendaEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The words of a tap inside a built-in card ([OwnTaps]), and when the Agenda reads the calendar ([AgendaTurns]). */
class OwnTapsTest {
    @Test fun aShortcutsButtonNamesItsPlace() {
        assertEquals(OwnTap.RunShortcut(0), OwnTaps.parse("shortcuts", "run 0"))
        assertEquals(OwnTap.RunShortcut(7), OwnTaps.parse("shortcuts", OwnTaps.run(7)))
        assertEquals(OwnTap.EditShortcuts, OwnTaps.parse("shortcuts", "edit"))
        // The list has 8 places at most.
        assertNull(OwnTaps.parse("shortcuts", "run 8"))
        assertNull(OwnTaps.parse("shortcuts", "run -1"))
        assertNull(OwnTaps.parse("shortcuts", "run"))
        assertNull(OwnTaps.parse("shortcuts", "run two"))
    }

    /** The words never hold a command. Whatever is written after them, nothing runs. */
    @Test fun wordsCanNotCarryACommand() {
        assertNull(OwnTaps.parse("shortcuts", "run 0 torch"))
        assertNull(OwnTaps.parse("shortcuts", "run torch"))
        assertNull(OwnTaps.parse("shortcuts", "torch"))
        assertNull(OwnTaps.parse("shortcuts", "edit torch"))
        assertNull(OwnTaps.parse("todos", "open torch"))
        assertNull(OwnTaps.parse("todos", "torch"))
        assertNull(OwnTaps.parse("notes", "open ; torch"))
        assertNull(OwnTaps.parse("agenda", "event 5 6 torch"))
        // What `open` runs is fixed for each widget.
        val typed = listOf("todos", "reminders", "notes", "agenda").map { (OwnTaps.parse(it, "open") as OwnTap.Typed).command }
        assertEquals(listOf("todo", "reminders", "notes", "agenda"), typed)
    }

    @Test fun aTodosRowNamesItsId() {
        assertEquals(OwnTap.SetTodo(12, done = true), OwnTaps.parse("todos", "done #12"))
        assertEquals(OwnTap.SetTodo(12, done = false), OwnTaps.parse("todos", "undo #12"))
        assertEquals(OwnTap.SetTodo(3, done = true), OwnTaps.parse("todos", OwnTaps.done(3)))
        assertEquals(OwnTap.SetTodo(3, done = false), OwnTaps.parse("todos", OwnTaps.undo(3)))
        assertEquals(OwnTap.Typed("todo"), OwnTaps.parse("todos", "open"))
        // An id, not a place in the list: the list may have changed since the card was drawn.
        assertNull(OwnTaps.parse("todos", "done 12"))
        assertNull(OwnTaps.parse("todos", "done #x"))
        assertNull(OwnTaps.parse("todos", "done #-4"))
        assertNull(OwnTaps.parse("todos", "done"))
        assertNull(OwnTaps.parse("todos", "done #1 #2"))
        assertNull(OwnTaps.parse("todos", "rm #12"))
    }

    @Test fun mediaHasItsButtons() {
        assertEquals(OwnTap.Media(MediaRepository.Action.PREV), OwnTaps.parse("media", "prev"))
        assertEquals(OwnTap.Media(MediaRepository.Action.TOGGLE), OwnTaps.parse("media", "toggle"))
        assertEquals(OwnTap.Media(MediaRepository.Action.NEXT), OwnTaps.parse("media", "next"))
        assertEquals(OwnTap.OpenPlayer, OwnTaps.parse("media", "open"))
        assertEquals(OwnTap.AllowNotifications, OwnTaps.parse("media", "allow"))
        assertNull(OwnTaps.parse("media", "stop"))
        assertNull(OwnTaps.parse("media", "next 2"))
    }

    @Test fun theAgendaOpensAnEvent() {
        assertEquals(OwnTap.OpenEvent(41), OwnTaps.parse("agenda", "event 41"))
        assertEquals(OwnTap.OpenEvent(41, 1_800_000_000_000L), OwnTaps.parse("agenda", "event 41 1800000000000"))
        assertEquals(OwnTap.OpenEvent(41, 1_800_000_000_000L), OwnTaps.parse("agenda", OwnTaps.event(41, 1_800_000_000_000L)))
        assertEquals(OwnTap.Typed("agenda"), OwnTaps.parse("agenda", "open"))
        assertEquals(OwnTap.AllowCalendar, OwnTaps.parse("agenda", "allow"))
        assertNull(OwnTaps.parse("agenda", "event"))
        assertNull(OwnTaps.parse("agenda", "event x"))
        assertNull(OwnTaps.parse("agenda", "event 41 soon"))
    }

    /** A widget's words work for that widget only, and an unknown widget has none. */
    @Test fun wordsBelongToTheirWidget() {
        assertNull(OwnTaps.parse("notes", "done #1"))
        assertNull(OwnTaps.parse("todos", "next"))
        assertNull(OwnTaps.parse("reminders", "allow"))
        assertNull(OwnTaps.parse("media", "run 0"))
        assertNull(OwnTaps.parse("battery", "open"))
        assertNull(OwnTaps.parse("comic", "open"))
        assertNull(OwnTaps.parse("todos", ""))
        assertNull(OwnTaps.parse("", "open"))
        // Spaces around the words don't matter.
        assertEquals(OwnTap.SetTodo(5, done = true), OwnTaps.parse("todos", "  done   #5 "))
    }

    private val events = listOf(AgendaEvent(1, "Standup", 1_000L, 2_000L, allDay = false))

    @Test fun theCalendarIsReadAtMostOnceAMinute() {
        val read = AgendaRead(allowed = true, events = events, at = 100_000L)
        assertFalse(AgendaTurns.due(read, allowed = true, now = 100_000L))
        assertFalse(AgendaTurns.due(read, allowed = true, now = 159_999L))
        assertTrue(AgendaTurns.due(read, allowed = true, now = 160_000L))
        assertTrue(AgendaTurns.due(read, allowed = true, now = 900_000L))
        // The clock was set back: the read counts as old.
        assertTrue(AgendaTurns.due(read, allowed = true, now = 50_000L))
    }

    @Test fun theFirstQuestionAndAChangedPermissionReadAtOnce() {
        assertTrue(AgendaTurns.due(AgendaRead.NOT_READ, allowed = false, now = 5L))
        assertTrue(AgendaTurns.due(AgendaRead.NOT_READ, allowed = true, now = 5L))
        val refused = AgendaRead(allowed = false, events = emptyList(), at = 100_000L)
        // Just allowed: the card must not say "allow" for another minute.
        assertTrue(AgendaTurns.due(refused, allowed = true, now = 100_001L))
        // Still not allowed: there is nothing to read, however long ago that was.
        assertFalse(AgendaTurns.due(refused, allowed = false, now = 100_001L))
        assertFalse(AgendaTurns.due(refused, allowed = false, now = 9_000_000L))
        // The permission was taken away: the events go at once.
        assertTrue(AgendaTurns.due(AgendaRead(allowed = true, events = events, at = 100_000L), allowed = false, now = 100_001L))
    }
}
