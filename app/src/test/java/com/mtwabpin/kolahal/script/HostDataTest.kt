package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.feeds.FeedItem
import com.mtwabpin.kolahal.productivity.Agenda
import com.mtwabpin.kolahal.productivity.AgendaEvent
import com.mtwabpin.kolahal.productivity.Note
import com.mtwabpin.kolahal.productivity.Reminder
import com.mtwabpin.kolahal.productivity.ShownEvent
import com.mtwabpin.kolahal.productivity.Todo
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetParser
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The parts of a script's list calls that need no Android ([HostData]): how each call reads its
 * arguments, and what a row holds. The lists themselves are read on a device (ScriptListsTest).
 */
class HostDataTest {
    private fun bold(text: String) = StyledText.wrap(StyledText.Style.BOLD, text)

    // ---- arguments ----------------------------------------------------------------------

    /** No argument is the default. Lua passes every number as a double, JavaScript a whole one as an int or a long. */
    @Test fun aCountIsHeldBetweenOneAndTheMost() {
        fun notes(n: Any?) = listCount("notes.list", "n", n, HostData.MAX_NOTES, HostData.DEFAULT_NOTES)
        assertEquals(10, notes(null))
        assertEquals(5, notes(5))
        assertEquals(5, notes(5.0))
        assertEquals(5, notes(5L))
        assertEquals(5, notes(5.9))
        assertEquals(1, notes(0))
        assertEquals(1, notes(-4))
        assertEquals(1, notes(0.3))
        assertEquals(50, notes(51))
        assertEquals(50, notes(1e12))
        assertEquals(50, notes(Double.POSITIVE_INFINITY))
        assertEquals(1, notes(Double.NEGATIVE_INFINITY))
        assertEquals(1, notes(Double.NaN))
        // Each call has its own limits.
        assertEquals(3, listCount("calendar.events", "days", null, HostData.MAX_CALENDAR_DAYS, HostData.DEFAULT_CALENDAR_DAYS))
        assertEquals(30, listCount("calendar.events", "days", 365, HostData.MAX_CALENDAR_DAYS, HostData.DEFAULT_CALENDAR_DAYS))
        assertEquals(10, listCount("feeds.unread", "n", null, HostData.MAX_FEED_ITEMS, HostData.DEFAULT_FEED_ITEMS))
        assertEquals(50, listCount("feeds.unread", "n", 500, HostData.MAX_FEED_ITEMS, HostData.DEFAULT_FEED_ITEMS))
    }

    @Test fun aCountThatIsNoNumberIsAnErrorThatSaysWhatToPass() {
        for (wrong in listOf("5", "all", true, listOf(5), mapOf("n" to 5))) {
            val e = assertThrows(wrong.toString(), IllegalArgumentException::class.java) { listCount("notes.list", "n", wrong, 50, 10) }
            assertEquals("notes.list: n must be a number from 1 to 50", e.message)
        }
        val e = assertThrows(IllegalArgumentException::class.java) { listCount("calendar.events", "days", "week", 30, 3) }
        assertEquals("calendar.events: days must be a number from 1 to 30", e.message)
    }

    /** An id is a whole number above 0, as a number or as text of digits: a widget's tap gives its arguments back as text. */
    @Test fun aTodosIdIsANumberOrItsDigits() {
        assertEquals(12L, todoId(12))
        assertEquals(12L, todoId(12L))
        assertEquals(12L, todoId(12.0))
        assertEquals(12L, todoId("12"))
        assertEquals(12L, todoId(" 12 "))
        assertEquals(1L, todoId(1))
        assertEquals(123_456_789_012L, todoId(123_456_789_012.0))
    }

    /** Anything else is an error. No value is read as "some todo": a wrong call must never tick one. */
    @Test fun aWrongIdIsAnError() {
        val wrong = listOf(
            null, 0, -3, 1.5, Double.NaN, Double.POSITIVE_INFINITY, 1e300, true, listOf(12), mapOf("id" to 12),
            "", " ", "abc", "12.0", "1e3", "-12", "+12", "#12", "12 13", "٣", "9".repeat(16),
        )
        for (id in wrong) {
            val e = assertThrows(id.toString(), IllegalArgumentException::class.java) { todoId(id) }
            assertEquals("todos.setDone: id must be a todo's id, as todos.list() gives it", e.message)
        }
    }

    /** Only true and false say what to do. A missing value is not read as true. */
    @Test fun doneIsTrueOrFalse() {
        assertTrue(todoDone(true))
        assertFalse(todoDone(false))
        for (wrong in listOf(null, "true", "done", 1, 0, 1.0, listOf(true))) {
            val e = assertThrows(wrong.toString(), IllegalArgumentException::class.java) { todoDone(wrong) }
            assertEquals("todos.setDone: done must be true or false", e.message)
        }
    }

    // ---- rows ---------------------------------------------------------------------------

    @Test fun aTodosRow() {
        assertEquals(mapOf("id" to 7L, "text" to "Buy milk", "done" to false), todoRow(Todo(7, "Buy milk", false)))
        assertEquals(mapOf("id" to 8L, "text" to "Call home", "done" to true), todoRow(Todo(8, "Call home", true)))
    }

    /** A note keeps its line breaks. Its time is in seconds. */
    @Test fun aNotesRow() {
        assertEquals(
            mapOf("id" to 3L, "text" to "Wifi\nguest / 1234", "created" to 1_800_000_000L),
            noteRow(Note(3, "Wifi\nguest / 1234", 1_800_000_000_999)),
        )
    }

    /** `repeats` is there only for a reminder that repeats: how, in words. A rule that can't be read has none. */
    @Test fun aRemindersRow() {
        assertEquals(mapOf("id" to 4L, "text" to "Tea", "at" to 1_800_000_000L), reminderRow(Reminder(4, 1_800_000_000_500, "Tea")))
        assertEquals(
            mapOf("id" to 5L, "text" to "Pills", "at" to 1_800_000_060L, "repeats" to "every day"),
            reminderRow(Reminder(5, 1_800_000_060_000, "Pills", repeat = "day", time = "08:00")),
        )
        assertEquals("every Mon and Thu", reminderRow(Reminder(6, 0, "Gym", repeat = "mon,thu"))["repeats"])
        assertEquals("on the 15th of every month", reminderRow(Reminder(6, 0, "Rent", repeat = "month:15"))["repeats"])
        // From a backup of a newer version, or damaged: the row is still there, as one that fires once.
        for (bad in listOf("month:x", "year:13", "")) {
            val row = reminderRow(Reminder(9, 60_000, "Odd", repeat = bad))
            assertEquals(bad, mapOf("id" to 9L, "text" to "Odd", "at" to 60L), row)
        }
    }

    @Test fun anEventsRow() {
        assertEquals(
            mapOf("id" to 41L, "title" to "Standup", "starts" to 1_800_000_000L, "ends" to 1_800_001_800L, "allDay" to false),
            eventRow(ShownEvent(41, "Standup", 1_800_000_000, 1_800_001_800, false, 1_800_000_000_000)),
        )
        // The calendar's own start is for Kolahal's cards. A script gets the times of the row only.
        assertFalse("begin" in eventRow(ShownEvent(41, "Standup", 1, 2, true, 3)))
    }

    private val item = FeedItem(90, 2, "https://example.com/a?id=1", "A title", "https://example.com/a", "Sat, 14 Nov 2026 10:00:00 GMT", "A summary", 1_800_000_000_000, false)

    /** A feed item has the fields of an item of `rss.fetch`, and the name of its feed. What the feed left out is nil. */
    @Test fun aFeedItemsRow() {
        assertEquals(
            mapOf(
                "id" to "https://example.com/a?id=1", "feed" to "Example", "title" to "A title",
                "link" to "https://example.com/a", "date" to "Sat, 14 Nov 2026 10:00:00 GMT", "summary" to "A summary",
            ),
            feedRow(item, "Example"),
        )
        val bare = feedRow(item.copy(link = null, publishedText = null, summary = null), "")
        assertEquals(listOf("id", "feed", "title", "link", "date", "summary"), bare.keys.toList())
        assertEquals(listOf(null, null, null), listOf(bare["link"], bare["date"], bare["summary"]))
        // The same names as an item of rss.fetch, and `feed`.
        assertEquals(RssParser.Item("t", "l", "i", "d", "s").toMap().keys + "feed", bare.keys)
        // Kolahal's own row number, the feed's number, the fetch time and the read flag are not for a script.
        assertEquals(emptyList<Any?>(), feedRow(item, "Example").values.filter { it !is String })
    }

    /** A feed's name is its title, or its host. Never its address, which can hold a name and a password. */
    @Test fun aFeedsNameIsNeverItsAddress() {
        assertEquals("Hacker News: Front Page", feedName(" Hacker News: Front Page ", "https://news.ycombinator.com/rss"))
        assertEquals("news.ycombinator.com", feedName(null, "https://news.ycombinator.com/rss"))
        assertEquals("example.com", feedName("  ", "https://www.example.com/feed.xml"))
        assertEquals("example.com", feedName(null, "https://me:hunter2@www.example.com/private/feed?key=abc123"))
        assertEquals("", feedName(null, "not an address"))
        assertEquals("", feedName(null, ""))
    }

    // ---- what every row holds -------------------------------------------------------------

    private val longText = "x".repeat(5000)

    /** A row of every kind, with [text] as each of its texts. */
    private fun rows(text: String): List<Map<String, Any?>> = listOf(
        todoRow(Todo(1, text, false)),
        noteRow(Note(2, text, 5_000)),
        reminderRow(Reminder(3, 5_000, text, repeat = "weekdays")),
        eventRow(ShownEvent(4, text, 5, 6, true, 5_000)),
        feedRow(FeedItem(5, 1, text, text, text, text, text, 5_000, false), text),
    )

    /** Text from your lists and from the web may hold style marks. A row never does: it is plain text. */
    @Test fun everyTextLosesItsStyles() {
        val styled = "a " + bold("bold") + " and " + StyledText.colour(StyledText.Colour.entries.first(), "red") + " b"
        assertTrue(StyledText.has(styled))
        for (row in rows(styled)) {
            for ((key, value) in row) {
                if (value !is String) continue
                assertFalse("$key keeps a mark: $row", StyledText.has(value))
            }
        }
        assertEquals("a bold and red b", todoRow(Todo(1, styled, false))["text"])
        // A feed's name and an item's guid come from the web too.
        val fromTheWeb = feedRow(item.copy(guid = styled), styled)
        assertEquals(listOf("a bold and red b", "a bold and red b"), listOf(fromTheWeb["id"], fromTheWeb["feed"]))
    }

    /** A very long text is cut, so a list has a size a run can hold. */
    @Test fun aLongTextIsCut() {
        for (row in rows(longText)) {
            for ((key, value) in row) {
                if (value !is String || key == "repeats") continue
                assertEquals("$key: $row", HostData.MAX_TEXT, value.length)
                assertTrue(key, value.endsWith("…"))
            }
        }
        assertEquals("x".repeat(4000), rowText("x".repeat(4000)))
        // An emoji at the cut is not left in half.
        val cut = rowText("x".repeat(3998) + "😀😀")
        assertFalse(cut.dropLast(1).last().isSurrogate())
        assertTrue(cut.endsWith("…"))
    }

    /**
     * A row reaches Lua and JavaScript as a plain table or object only when it holds text, whole
     * numbers and true or false. Anything else would arrive as a Java object in Lua.
     */
    @Test fun aRowHoldsOnlyTextWholeNumbersAndTrueOrFalse() {
        // With the feed item whose feed gave no link, date or summary: those are nil.
        for (row in rows("text") + feedRow(item.copy(link = null, publishedText = null, summary = null), "")) {
            for ((key, value) in row) {
                assertTrue("$key is a ${value?.javaClass?.simpleName}: $row", value == null || value is String || value is Long || value is Boolean)
            }
        }
    }

    /**
     * A script writes `row.text` and `{ title = e.title }`. So no field is a Lua keyword (hence
     * `repeats` and `ends`), and none is `status` or `notify`: those words in a script's code switch
     * on access items ([ScriptMeta.setsStatus], [ScriptMeta.postsNotifications]).
     */
    @Test fun noFieldIsAKeywordOrAnAccessWord() {
        val fields = rows("text").flatMap { it.keys }.toSet()
        assertEquals(
            setOf("id", "text", "done", "created", "at", "repeats", "title", "starts", "ends", "allDay", "feed", "link", "date", "summary"),
            fields,
        )
        assertEquals(emptySet<String>(), fields intersect (ApiDocs.luaKeywords + ApiDocs.jsKeywords + listOf("status", "notify")).toSet())
        // Reading the fields in a script switches on no access item.
        val code = fields.joinToString("\n") { "log(row.$it)" }
        assertFalse(ScriptMeta.usesInternet(code))
        assertFalse(ScriptMeta.setsStatus(code))
        assertFalse(ScriptMeta.postsNotifications(code))
    }

    // ---- the calendar's rows in an agenda widget ------------------------------------------

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun local(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, d, h, m).atZone(zone).toInstant().toEpochMilli()
    private fun utcDay(d: Int) = LocalDate.of(2026, 10, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /**
     * The times and the title of a calendar row are what an `agenda` widget takes: the widget's
     * check reads each row, with the same title, times and all-day flag as the Agenda card has.
     * (The row's `id` is left out here. The agenda's check must know it too: see the lead's note.)
     */
    @Test fun aCalendarRowHasWhatAnAgendaWidgetTakes() {
        val now = local(17, 12)
        val events = listOf(
            AgendaEvent(1, "Standup", local(17, 13), local(17, 13, 30), false),
            AgendaEvent(2, "Holiday", utcDay(17), utcDay(18), true),
            AgendaEvent(3, "Ping", local(18, 9), local(18, 9), false),
        )
        val shown = Agenda.shown(events, zone, now, 3)
        val rows = shown.map { eventRow(it) - "id" }
        val spec = WidgetParser.parse(mapOf("events" to rows, "days" to 3), WidgetKind.AGENDA, text = { it }, link = { it }) as WidgetSpec.Agenda
        assertEquals(shown.map { it.title }, spec.events.map { it.title })
        assertEquals(shown.map { it.starts }, spec.events.map { it.starts })
        assertEquals(shown.map { it.ends }, spec.events.map { it.ends })
        assertEquals(shown.map { it.allDay }, spec.events.map { it.allDay })
        assertEquals(listOf("Holiday", "Standup", "Ping"), spec.events.map { it.title })
    }
}
