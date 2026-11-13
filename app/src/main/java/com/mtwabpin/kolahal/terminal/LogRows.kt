package com.mtwabpin.kolahal.terminal

import androidx.compose.runtime.Immutable
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.TimeFormats
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The coloured gutter a line gets. It shows command blocks and timer output as groups. */
enum class Gutter { COMMAND, SCRIPT, ERROR }

/** A row that the log list renders: a log entry, or an added day / unread separator. */
@Immutable
sealed interface LogRow {
    val key: Any

    @Immutable
    data class Entry(
        val entry: LogEntry,
        val dimmed: Boolean,
        val gutter: Gutter,
        /** True for script output that arrived since the user last left home. */
        val unread: Boolean = false,
        /** True when the line starts a block (a command, or the first line of a script run). It gets the block gap above it. */
        val blockStart: Boolean = false,
        /**
         * True when the line ends with its source's name (for a notification: its app's icon).
         * This is the first line of a run or a group. It is false when the command that ran it is
         * right above, because the command already names it.
         */
        val showSource: Boolean = true,
        /**
         * True when the line shows its time under `log.time groups`: the first line of a group,
         * the first line of a day, or the first line in a new minute.
         */
        val showTime: Boolean = true,
        /**
         * True when the line starts an item of a list inside its group ([LogContext.GAP]): it gets
         * the `log.item-spacing` gap above it. Never on a group's first line, which has the group
         * gap, nor right under its command, which the first item belongs with.
         */
        val itemGap: Boolean = false,
    ) : LogRow {
        override val key: Any get() = entry.id
    }

    @Immutable
    data class Day(val day: LocalDate) : LogRow {
        override val key: Any get() = "day:$day"
    }

    @Immutable
    data class Unread(val count: Int) : LogRow {
        override val key: Any get() = "unread"
    }
}

/** A line that comes more than this long after the line before it starts a new group. */
const val GROUP_GAP_MS = 3_000L

/** The context key a notification's line carries ([com.mtwabpin.kolahal.notifications.KolahalNotificationListener.CTX_KEY]). */
private const val NOTIFICATION = com.mtwabpin.kolahal.notifications.KolahalNotificationListener.CTX_KEY

/**
 * Builds the log rows:
 * - Output older than the most recent command is dimmed, so the latest result stands out.
 *   Timer-script lines that arrived after that command stay bright.
 * - A separator marks each new day.
 * - Script output newer than [seenUpTo] is marked unread, with a divider above the first such line.
 */
fun buildRows(
    entries: List<LogEntry>,
    seenUpTo: Long = Long.MAX_VALUE,
    unreadUntil: Long = Long.MAX_VALUE,
    zone: ZoneId = ZoneId.systemDefault(),
    /** Leaves out a name or time that the lines above already show. Turn it off for lines out of context (search results). */
    trim: Boolean = true,
): List<LogRow> {
    val latestCommand = entries.lastOrNull { it.kind == LogKind.COMMAND }?.id ?: Long.MIN_VALUE
    val today = LocalDate.now(zone)
    val isScriptLine = { e: LogEntry -> e.kind == LogKind.SCRIPT }
    // Use two comparisons, not a range. When seenUpTo is Long.MAX_VALUE, seenUpTo + 1 overflows and every line would be unread.
    val isUnread = { e: LogEntry -> e.id > seenUpTo && e.id <= unreadUntil }
    val unreadCount = entries.count { isUnread(it) && it.kind == LogKind.SCRIPT }
    val rows = ArrayList<LogRow>(entries.size + 4)
    var previousDay: LocalDate? = null
    var unreadShown = false
    var previous: LogEntry? = null
    for (e in entries) {
        val day = Instant.ofEpochMilli(e.ts).atZone(zone).toLocalDate()
        // Move the day only forward. Lines are in id order. A clock set back (or a script line buffered
        // across midnight) would otherwise add the same day twice, which gives a duplicate list key.
        val last = previousDay
        val newDay = if (last == null) day != today else day > last
        if (newDay) rows += LogRow.Day(day)
        if (last == null || day > last) previousDay = day
        val unread = isUnread(e) && isScriptLine(e)
        if (unread && !unreadShown && unreadCount > 0) {
            rows += LogRow.Unread(unreadCount)
            unreadShown = true
        }
        // The first line of a background run: a script or a feed starts logging after other lines.
        val runStart = isScriptLine(e) && (previous == null || !isScriptLine(previous) || previous.source != e.source)
        // The line is right under the command that ran it (or under that command's other lines).
        // Then it is that command's output, in its group, and `$ countdown` already names it.
        val from = e.ctx.values[LogContext.FROM]?.toLongOrNull()
        val underItsCommand = from != null && previous != null &&
            (previous.id == from || previous.ctx.values[LogContext.FROM]?.toLongOrNull() == from)
        // An event of its own: an app launched by a tap ([LogContext.APART]), or a notification, even in a
        // burst from one app. It starts a group even right after another event.
        val ownEvent = LogContext.APART in e.ctx.values || (e.kind == LogKind.SCRIPT && NOTIFICATION in e.ctx.values)
        // A group holds the lines of one action:
        // - a typed command and its output;
        // - a background run (a script's, a feed's), from its first line;
        // - one event: a notification, an app launched by a tap.
        // A line more than [GROUP_GAP_MS] after the one before also starts a group, even in a run.
        val blockStart = e.kind == LogKind.COMMAND || (runStart && !underItsCommand) || ownEvent ||
            (previous != null && e.ts - previous.ts > GROUP_GAP_MS)
        rows += LogRow.Entry(
            entry = e,
            dimmed = e.id < latestCommand,
            gutter = when {
                e.kind == LogKind.ERROR -> Gutter.ERROR
                isScriptLine(e) -> Gutter.SCRIPT
                else -> Gutter.COMMAND
            },
            unread = unread && e.kind == LogKind.SCRIPT,
            blockStart = blockStart,
            showSource = !trim || ((runStart || blockStart) && !underItsCommand),
            showTime = !trim || blockStart || newDay || previous == null || e.ts / 60_000 != previous.ts / 60_000,
            itemGap = !blockStart && previous != null && previous.kind != LogKind.COMMAND && e.ctx.values[LogContext.GAP] == "1",
        )
        previous = e
    }
    return rows
}

/**
 * The time format beside a line. A format keeps the time zone in which it was created,
 * so a new one is created when the phone's time zone changes.
 */
fun lineTimeFormat(clock24: Boolean, locale: Locale = Locale.getDefault()) = SimpleDateFormat(if (clock24) "HH:mm" else "h:mm", locale)

/**
 * Where a line came from, and when (to the second), for its long-press sheet: `countdown · today 6:55:12`.
 * The log may leave this information off the line itself.
 */
fun lineDetail(
    e: LogEntry,
    clock24: Boolean,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val at = Instant.ofEpochMilli(e.ts).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day = TimeFormats.dayLabel(at.toLocalDate(), today, locale)
    val time = at.format(DateTimeFormatter.ofPattern(if (clock24) "HH:mm:ss" else "h:mm:ss a", locale))
    return listOfNotNull(e.source?.takeIf { e.kind == LogKind.SCRIPT }, "$day $time").joinToString(" · ")
}
