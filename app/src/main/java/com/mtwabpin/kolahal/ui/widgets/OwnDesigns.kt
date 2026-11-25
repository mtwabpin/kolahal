/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.data.TimeFormats
import com.mtwabpin.kolahal.productivity.Note
import com.mtwabpin.kolahal.productivity.Reminder
import com.mtwabpin.kolahal.productivity.Repeat
import com.mtwabpin.kolahal.productivity.Todo
import com.mtwabpin.kolahal.terminal.OwnTaps
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetTap
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * What a built-in card draws in a look of its own, in place of its kind's body
 * ([WidgetCard]'s `body`): the box it gets, and what a tap inside it sends.
 */
internal typealias OwnBody = @Composable (box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) -> Unit

/** The room around a look of its own inside its card: a Plain card's, and a little more at the sides of a small one. */
internal fun ownRoom(box: WidgetSizes.Box): PaddingValues = when (box) {
    WidgetSizes.Box.LARGE -> PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    WidgetSizes.Box.WIDE -> PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    WidgetSizes.Box.SMALL -> PaddingValues(horizontal = 12.dp, vertical = 8.dp)
}

/**
 * The pure parts of the looks that only Kolahal's own cards have: Next for Todos and Reminders,
 * Stack for Notes ([com.mtwabpin.kolahal.widgets.WidgetLook.NEXT],
 * [com.mtwabpin.kolahal.widgets.WidgetLook.STACK]). The card's words for a screen reader and its
 * taps are the ones [OwnCards] gives, except where a look says more.
 */
internal object OwnDesigns {
    // ---- todos: the next one first ----------------------------------------------------------

    /**
     * The Todos card in the Next look.
     * - [done] of [total] are ticked: the ring. [open] are the open ones in the order they were
     *   added, at most [OwnCards.MAX_TODOS]: the first is [next], the large one with its check.
     * - [undo] is the todo ticked last on this card, while it is still done: a tap on Undo opens it
     *   again, as a second tap on a ticked row does in the other looks.
     */
    @Immutable
    data class NextTodos(val done: Int, val total: Int, val open: List<Todo>, val undo: Todo?) {
        /** How many are open, also those past [OwnCards.MAX_TODOS]. */
        val left: Int get() = total - done

        val next: Todo? get() = open.firstOrNull()

        /** How full the ring is. */
        val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total

        /** What the ring says: "3/7". */
        val count: String get() = "$done/$total"
    }

    /** The Next look of [list], all todos as the store gives them. [last] is the todo ticked last on the card, or null. */
    fun nextTodos(list: List<Todo>, last: Long? = null): NextTodos = NextTodos(
        done = list.count { it.done },
        total = list.size,
        open = list.filter { !it.done }.sortedBy { it.id }.take(OwnCards.MAX_TODOS),
        undo = last?.let { id -> list.firstOrNull { it.id == id && it.done } },
    )

    /** "4 left", or "All done" when none is open. */
    fun leftWords(t: NextTodos): String = if (t.left > 0) "${t.left} left" else "All done"

    /**
     * What a card's top row says under the next todo: the one after it, and with [rest] how many come
     * after that: "then Renew the passport · +2". Null when the next is the last one open. A tall card
     * lists the rest as rows ([followRows]) and counts what is left out under them, so its line has no count.
     */
    fun thenWords(t: NextTodos, rest: Boolean = true): String? {
        val after = t.open.getOrNull(1) ?: return null
        val more = t.left - 2
        return "then ${after.text}" + if (rest && more > 0) " · +$more" else ""
    }

    /**
     * The todos a tall card lists as rows under its top row: those after the two that row names, the
     * next one and the "then" one. So no todo is on the card twice.
     */
    fun followRows(t: NextTodos): List<Todo> = t.open.drop(2)

    /** How many open todos a tall card leaves out with [shown] rows under its top row: its "+3 more". */
    fun followMore(t: NextTodos, shown: Int): Int = (t.left - minOf(2, t.open.size) - shown).coerceAtLeast(0)

    /** The height of the line with Undo and "+3 more" under the todos, in dp. */
    const val FOOT_DP = 20

    /**
     * The height of a todo's row under the next one, in dp: a row to tap, as tall as a list's
     * ([TAP_ROW_DP]), or as its line of [lineDp] where a larger font makes that taller.
     */
    fun followRowDp(lineDp: Int): Int = WidgetWords.textRowDp(TAP_ROW_DP, lineDp)

    /**
     * How many of the [count] rows of a tall card ([followRows]) it shows in the [roomDp] under its top
     * row, each [followRowDp] tall with [ROW_GAP_DP] between two: as many as fit. The foot line
     * ([FOOT_DP]) is kept for "+3 more" only when rows are left out. Undo is on the top row's line.
     */
    fun followShown(count: Int, roomDp: Int, lineDp: Int): Int =
        WidgetWords.rowsShown(count, roomDp, followRowDp(lineDp), FOOT_DP, ROW_GAP_DP)

    /**
     * The height of each of the [shown] rows of a tall card, in dp, in the [roomDp] under its top row:
     * [followRowDp], or more where the rows have room to share, up to a list's tallest row
     * ([WidgetWords.ROW_MAX_DP]). With [more] left out, the foot line and its gap are kept first.
     */
    fun followHeightDp(shown: Int, roomDp: Int, lineDp: Int, more: Boolean): Float {
        val least = followRowDp(lineDp)
        if (shown <= 0) return least.toFloat()
        val room = roomDp - if (more) FOOT_DP + ROW_GAP_DP else 0
        val share = (room + ROW_GAP_DP).toFloat() / shown - ROW_GAP_DP
        return share.coerceIn(least.toFloat(), maxOf(least, WidgetWords.ROW_MAX_DP).toFloat())
    }

    /**
     * The card for a screen reader: "Todos: 4 open, 3 of 7 done. Next: Book the dentist". The
     * check and Undo are items of their own.
     */
    fun nextSaid(t: NextTodos): String {
        val open = if (t.left > 0) "${t.left} open" else "nothing open"
        val next = t.next?.let { ". Next: ${it.text}" }.orEmpty()
        return "Todos: $open, ${t.done} of ${t.total} done$next"
    }

    /** The check's tap, and a row's: mark [todo] done. The words are the Plain card's ([OwnCards.todos]). */
    fun doneTap(todo: Todo): WidgetTap =
        WidgetTap(args = OwnTaps.done(todo.id), label = "mark as done")

    /** Undo's tap: open [todo] again. */
    fun undoTap(todo: Todo): WidgetTap =
        WidgetTap(args = OwnTaps.undo(todo.id), label = "mark as not done")

    // ---- reminders: a timeline --------------------------------------------------------------

    /** The pill empties over this last stretch before a reminder: full an hour before, empty at its time. */
    const val PILL_MS = 60 * 60_000L

    /**
     * The next reminder in the Next look: its [text], the time to it in words ([until]: "in 42
     * min"), when it is due with how it repeats ([time]: "19:30 · every Mon"), and how full its pill
     * is ([left], 1 an hour or more before, 0 at its time).
     */
    @Immutable
    data class Due(val text: String, val until: String, val time: String, val left: Float)

    /** A later reminder on the line: its [text] and when it is due, short ([OwnCards.shortWhen]). */
    @Immutable
    data class Later(val text: String, val time: String)

    /** The Reminders card in the Next look: the [next] one and the [later] ones, at most [OwnCards.MAX_REMINDERS]. */
    @Immutable
    data class Timeline(val next: Due, val later: List<Later>)

    /**
     * The time from [now] to [at] in words, from [OwnCards.untilParts]: "in 42 min", "in 13 h",
     * "in 2 days", or "now" when the time has come.
     */
    fun untilWords(at: Long, now: Long): String {
        val (value, unit) = OwnCards.untilParts(at, now)
        return if (unit == null) value else "in $value $unit"
    }

    /** How full the pill is at [now] for a reminder at [at]: the part of the last hour that is left ([PILL_MS]). */
    fun pillLeft(at: Long, now: Long): Float = ((at - now).toFloat() / PILL_MS).coerceIn(0f, 1f)

    /**
     * The Next look of [list], which is soonest first, at [now] (milliseconds). Null with no
     * reminders: the card then says so. It is made again each minute, so the pill empties and the
     * time counts down with no run and no read.
     */
    fun timeline(
        list: List<Reminder>,
        now: Long,
        zone: ZoneId,
        clock24: Boolean,
        locale: Locale = Locale.getDefault()
    ): Timeline? {
        val r = list.firstOrNull() ?: return null
        // A rule that can't be read has no words. It must not end the card: such a reminder fires once.
        val repeat = r.repeat?.let { rule -> runCatching { " · ${Repeat.label(rule)}" }.getOrNull() }.orEmpty()
        val time = TimeFormats.whenLabel(r.at, now, zone, clock24, locale, capital = true) + repeat
        return Timeline(
            Due(r.text.trim(), untilWords(r.at, now), time, pillLeft(r.at, now)),
            list.drop(1).take(OwnCards.MAX_REMINDERS).map {
                Later(it.text.trim(), OwnCards.shortWhen(it.at, now, zone, clock24, locale))
            },
        )
    }

    /** What a one-row card says beside the pill: "19:30 · then Water plants, Wed". */
    fun pillLine(t: Timeline): String =
        t.later.firstOrNull()?.let { "${t.next.time} · then ${it.text}, ${it.time}" } ?: t.next.time

    // ---- notes: a stack of paper ------------------------------------------------------------

    /**
     * The Notes card in the Stack look: the newest note's [text], when it was written ([at], with
     * its date always written out, as the Plain card's caption), and how many notes there are.
     */
    @Immutable
    data class NoteStack(val text: String, val at: String, val count: Int) {
        /** The sheets behind the top one: one for each other note, at most two. */
        val behind: Int get() = (count - 1).coerceIn(0, 2)
    }

    /** The Stack look of [list], which is newest first. Null with no notes: the card then says so. */
    fun stack(
        list: List<Note>,
        zone: ZoneId = ZoneId.systemDefault(),
        clock24: Boolean = true,
        locale: Locale = Locale.getDefault()
    ): NoteStack? {
        val newest = OwnCards.notes(list, 1, zone, clock24, locale) ?: return null
        val written = Instant.ofEpochMilli(list[0].created).atZone(zone)
        val at = written.format(TimeFormats.dayFormat(locale)) + " " +
                TimeFormats.clockFormat(clock24, locale).format(written)
        return NoteStack(newest.text, at, list.size)
    }

    /** "3 notes", "1 note". */
    fun countWords(count: Int): String = if (count == 1) "1 note" else "$count notes"

    /** The tap of the + that starts a new note ([OwnTaps.NEW]). */
    val NEW_NOTE = WidgetTap(args = OwnTaps.NEW, label = "new note")
}
