package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.logEntry
import com.mtwabpin.kolahal.withDefaultZone
import org.junit.Assert.assertEquals
import org.junit.Test

class LogRowsTest {
    private val t0 = System.currentTimeMillis()

    private fun line(id: Long, at: Long, kind: LogKind = LogKind.INFO, ctx: LogContext = LogContext.EMPTY, source: String? = null) =
        logEntry(kind, "line $id", source, ctx, id, t0 + at)

    private fun starts(vararg entries: LogEntry): List<Boolean> =
        buildRows(entries.toList()).filterIsInstance<LogRow.Entry>().map { it.blockStart }

    @Test
    fun commandAndItsOutputAreOneGroup() {
        assertEquals(
            listOf(true, false, false),
            starts(line(1, 0, LogKind.COMMAND), line(2, 100, LogKind.OUTPUT), line(3, 900, LogKind.OUTPUT)),
        )
    }

    @Test
    fun lineArrivingLaterOnItsOwnStartsAGroup() {
        // e.g. an app launched by tapping, or an install event, well after the last command.
        assertEquals(
            listOf(true, false, true, true),
            starts(
                line(1, 0, LogKind.COMMAND),
                line(2, 200, LogKind.OUTPUT),
                line(3, 60_000),
                line(4, 120_000),
            ),
        )
    }

    @Test
    fun linesArrivingTogetherStayTogether() {
        assertEquals(listOf(false, false), starts(line(1, 0), line(2, GROUP_GAP_MS)))
    }

    /** Two apps launched by a tap, a second apart: two events, each its own group. */
    @Test
    fun eachTapLaunchIsItsOwnGroup() {
        val launched = LogContext.of(LogContext.APART to "1")
        assertEquals(
            listOf(true, false, true, true),
            starts(line(1, 0, LogKind.COMMAND), line(2, 100, LogKind.OUTPUT), line(3, 900, ctx = launched), line(4, 1_900, ctx = launched)),
        )
    }

    /** A burst of notifications from one app: each one its own group, named again. A script run stays one group. */
    @Test
    fun eachNotificationIsItsOwnGroup() {
        fun notif(id: Long, at: Long) = line(id, at, LogKind.SCRIPT, LogContext.of("notif" to "k$id"), source = "Outlook")
        val rows = entries(notif(1, 0), notif(2, 300), notif(3, 600))
        assertEquals(listOf(true, true, true), rows.map { it.blockStart })
        assertEquals(listOf(true, true, true), rows.map { it.showSource })
        assertEquals(
            listOf(true, false, false),
            starts(line(1, 0, LogKind.SCRIPT, source = "hn"), line(2, 300, LogKind.SCRIPT, source = "hn"), line(3, 600, LogKind.SCRIPT, source = "hn")),
        )
    }

    @Test
    fun timestampsGoingBackNeverRepeatADayDivider() {
        // id order with days running D-2, D-1, D-2, D-1 (clock set back, or a buffered script line
        // written just after midnight): each day divider must appear once, or the list key repeats.
        val day = 86_400_000L
        val rows = buildRows(
            listOf(line(1, -2 * day), line(2, -day), line(3, -2 * day + 1), line(4, -day + 1)),
        )
        val keys = rows.filterIsInstance<LogRow.Day>().map { it.key }
        assertEquals(keys.distinct(), keys)
        assertEquals(rows.map { it.key }.distinct().size, rows.size)
    }

    private fun entries(vararg entries: LogEntry) = buildRows(entries.toList()).filterIsInstance<LogRow.Entry>()

    /** A run names its source once, on its first line; another source in between names itself again. */
    @Test
    fun aRunNamesItsSourceOnce() {
        val rows = entries(
            line(1, 0, LogKind.SCRIPT, source = "hn"),
            line(2, 10, LogKind.SCRIPT, source = "hn"),
            line(3, 20, LogKind.SCRIPT, source = "WhatsApp"),
            line(4, 30, LogKind.SCRIPT, source = "hn"),
            line(5, 40, LogKind.SCRIPT, source = "hn"),
        )
        assertEquals(listOf(true, false, true, true, false), rows.map { it.showSource })
    }

    /** The same source on its own, later: a new group, named again. */
    @Test
    fun aLaterLineFromTheSameSourceIsNamedAgain() {
        val rows = entries(line(1, 0, LogKind.SCRIPT, source = "WhatsApp"), line(2, 60_000, LogKind.SCRIPT, source = "WhatsApp"))
        assertEquals(listOf(true, true), rows.map { it.showSource })
    }

    /** Right under `$ countdown`, its lines don't say countdown: the command does. */
    @Test
    fun linesUnderTheirCommandAreNotNamed() {
        val from = LogContext.of(LogContext.FROM to "1")
        val rows = entries(
            line(1, 0, LogKind.COMMAND),
            line(2, 10, LogKind.SCRIPT, ctx = from, source = "countdown"),
            line(3, 20, LogKind.SCRIPT, ctx = from, source = "countdown"),
            // The same script's lines after something else came in between: named.
            line(4, 30, LogKind.SCRIPT, source = "hn"),
            line(5, 40, LogKind.SCRIPT, ctx = from, source = "countdown"),
        )
        assertEquals(listOf(true, false, false, true, true), rows.map { it.showSource })
    }

    /** A script's lines under the command that ran it are that command's output: one group, one time. */
    @Test
    fun aScriptsOutputJoinsItsCommandsGroup() {
        val from = LogContext.of(LogContext.FROM to "1")
        val rows = entries(
            line(1, 0, LogKind.COMMAND),
            line(2, 10, LogKind.SCRIPT, ctx = from, source = "countdown"),
            line(3, 20, LogKind.SCRIPT, ctx = from, source = "countdown"),
        )
        assertEquals(listOf(true, false, false), rows.map { it.blockStart })
        assertEquals(listOf(true, false, false), rows.map { it.showTime })
    }

    /** log.time groups: a group's first line, then only a new minute. */
    @Test
    fun timesShowOncePerGroupAndOnANewMinute() {
        val base = (t0 / 60_000 + 1) * 60_000 - t0 // the start of the next minute, relative to t0
        val rows = entries(
            line(1, base, LogKind.COMMAND),
            line(2, base + 100, LogKind.OUTPUT),
            line(3, base + 2_000, LogKind.OUTPUT),
            line(4, base + 60_000, LogKind.OUTPUT),
            line(5, base + 60_500, LogKind.OUTPUT),
        )
        // Lines 4 and 5 are a new group (more than 3 s after line 3) anyway; 4 is also a new minute.
        assertEquals(listOf(true, false, false, true, false), rows.map { it.showTime })
    }

    @Test
    fun aSlowCommandsOutputShowsTheNewMinute() {
        val base = (t0 / 60_000 + 1) * 60_000 - t0
        val rows = entries(
            line(1, base + 58_000, LogKind.COMMAND),
            line(2, base + 59_000, LogKind.OUTPUT),
            line(3, base + 60_500, LogKind.OUTPUT),
        )
        assertEquals(listOf(true, false, true), rows.map { it.showTime })
    }

    /** Search results are out of context: every line keeps its name and time. */
    @Test
    fun untrimmedRowsKeepEverything() {
        val rows = buildRows(
            listOf(line(1, 0, LogKind.SCRIPT, source = "hn"), line(2, 10, LogKind.SCRIPT, source = "hn")),
            trim = false,
        ).filterIsInstance<LogRow.Entry>()
        assertEquals(listOf(true, true), rows.map { it.showSource })
        assertEquals(listOf(true, true), rows.map { it.showTime })
    }

    /** The long-press sheet's detail: source and time to the second, the day in words when it's recent. */
    @Test
    fun lineDetailSaysWhereAndWhen() {
        val zone = java.time.ZoneOffset.UTC
        val now = java.time.LocalDateTime.of(2026, 10, 24, 18, 0).toInstant(zone).toEpochMilli()
        fun at(d: Int, h: Int, m: Int, s: Int) = java.time.LocalDateTime.of(2026, 10, d, h, m, s).toInstant(zone).toEpochMilli()
        val script = logEntry(LogKind.SCRIPT, source = "countdown", id = 1, ts = at(24, 6, 55, 12))
        assertEquals("countdown · today 06:55:12", lineDetail(script, clock24 = true, now = now, zone = zone, locale = java.util.Locale.UK))
        val output = logEntry(LogKind.OUTPUT, id = 2, ts = at(23, 18, 2, 11))
        assertEquals("yesterday 18:02:11", lineDetail(output, clock24 = true, now = now, zone = zone, locale = java.util.Locale.UK))
        val old = logEntry(LogKind.SCRIPT, source = "hn", id = 3, ts = at(20, 9, 5, 0))
        assertEquals("hn · Tue 20 Oct 09:05:00", lineDetail(old, clock24 = true, now = now, zone = zone, locale = java.util.Locale.UK))
    }

    /** A format keeps the zone it was made in; one made after the phone's zone changed shows the new zone. */
    @Test
    fun aLineTimeFormatKeepsTheZoneItWasMadeIn() {
        val at = java.util.Date(java.time.LocalDateTime.of(2026, 10, 24, 18, 5).toInstant(java.time.ZoneOffset.UTC).toEpochMilli())
        withDefaultZone("UTC") {
            val old = lineTimeFormat(clock24 = true, locale = java.util.Locale.UK)
            assertEquals("18:05", old.format(at))
            assertEquals("6:05", lineTimeFormat(clock24 = false, locale = java.util.Locale.UK).format(at))
            withDefaultZone("Asia/Tokyo") {
                assertEquals("18:05", old.format(at))
                assertEquals("03:05", lineTimeFormat(clock24 = true, locale = java.util.Locale.UK).format(at))
            }
        }
    }

    /** A line's day divider is its day in the zone the rows are built in. */
    @Test
    fun aLinesDayIsItsDayInTheZone() {
        val at = java.time.LocalDateTime.of(2020, 1, 15, 23, 30).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        val entry = logEntry(LogKind.INFO, ts = at)
        fun day(zone: String) = buildRows(listOf(entry), zone = java.time.ZoneId.of(zone)).filterIsInstance<LogRow.Day>().single().day
        assertEquals(java.time.LocalDate.of(2020, 1, 15), day("UTC"))
        assertEquals(java.time.LocalDate.of(2020, 1, 16), day("Asia/Tokyo"))
    }

    /** A script's or a feed's lines are a group of their own, even right after other lines. */
    @Test
    fun aRunStartsAGroupEvenRightAfterOtherLines() {
        assertEquals(
            listOf(true, true, false, true, false),
            starts(
                line(1, 0, LogKind.COMMAND),
                line(2, 10, LogKind.SCRIPT, source = "exchange"),
                line(3, 20, LogKind.SCRIPT, source = "exchange"),
                line(4, 30, LogKind.SCRIPT, source = "hn"),
                line(5, 40, LogKind.SCRIPT, source = "hn"),
            ),
        )
    }

    private val item = LogContext(mapOf(LogContext.GAP to "1"))

    @Test
    fun listItemsGetAGapButNotTheFirstUnderItsCommand() {
        // $ hn, then three stories that each ask for a gap: the first sits right under the command.
        val gaps = entries(
            line(1, 0, LogKind.COMMAND),
            line(2, 100, LogKind.SCRIPT, item, "hn"),
            line(3, 110, LogKind.SCRIPT, item, "hn"),
            line(4, 120, LogKind.SCRIPT, item, "hn"),
        ).map { it.itemGap }
        assertEquals(listOf(false, false, true, true), gaps)
    }

    @Test
    fun aGroupsFirstLineHasTheGroupGapNotAnItemGap() {
        // A background run: its first line starts a group, so it keeps the group gap only.
        val rows = entries(line(1, 0, LogKind.SCRIPT, item, "lobsters"), line(2, 10, LogKind.SCRIPT, item, "lobsters"))
        assertEquals(listOf(true, false), rows.map { it.blockStart })
        assertEquals(listOf(false, true), rows.map { it.itemGap })
    }

    @Test
    fun linesWithoutTheKeyHaveNoItemGap() {
        val rows = entries(line(1, 0, LogKind.COMMAND), line(2, 10, LogKind.OUTPUT), line(3, 20, LogKind.OUTPUT))
        assertEquals(listOf(false, false, false), rows.map { it.itemGap })
    }
}
