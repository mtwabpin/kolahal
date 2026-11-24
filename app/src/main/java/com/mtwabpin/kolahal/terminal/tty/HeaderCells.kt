/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.terminal.HeaderPart
import com.mtwabpin.kolahal.terminal.HeaderParts
import com.mtwabpin.kolahal.terminal.Ink
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The header on the grid: one row of its parts ([HeaderParts]), the left side from the left, the
 * right side ending at the right. No icons: each part is text, a few signs of the font, and a
 * battery the painter draws itself.
 * - time `14:02` bold, date `Tue 17 Nov` muted;
 * - alarm `◷ 07:00`, todos `✓ 3`, unlocks `unl 41` (the count in its part's ink: warning or error
 *   from the daily limit);
 * - network as its name (`wifi`, `cell`, red `offline`);
 * - battery as a battery the painter draws ([BoxShapes.battery]), filled to its level, with a bolt
 *   in it while charging, then `82%`: it reads as a battery at any level, 100 % too;
 * - a profile as its tag, inverse in the profile's color (` W `), muted while it is off;
 * - settings `⚙`.
 * - while home is scrambled, `scrambled` in the warning color, first on the left.
 * Parts that don't fit are dropped from the middle outward, as the GUI drops them: the left side
 * loses its last part first, the right side its first. A camera cutout across the row splits it
 * into zones ([zones]); the parts flow around it. Cells that the screen's rounded corners would
 * cut, at the row's ends, are left empty the same way.
 *
 * A part's spot id is its index in `left + right`.
 */
object HeaderCells {
    /** Cells between two parts. */
    const val GAP = 2

    private val BOLD = CellStyle.BOLD

    /** What [part] shows, piece by piece. */
    internal fun segs(part: HeaderPart): List<Seg> {
        val ink = CellStyle.of(part.ink, flags = BOLD)
        val muted = CellStyle.of(Ink.DIM)
        return when (part.kind) {
            HeaderPart.Kind.TIME -> listOf(Seg(part.text, ink))
            HeaderPart.Kind.DATE -> listOf(Seg(part.text, CellStyle.of(part.ink)))
            HeaderPart.Kind.ALARM -> listOf(Seg("◷ ", muted), Seg(part.text, ink))
            HeaderPart.Kind.TODO -> listOf(Seg(TODO_SIGN, muted), Seg(part.text, ink))
            HeaderPart.Kind.NETWORK -> listOf(Seg(part.text, ink))
            HeaderPart.Kind.UNLOCKS -> listOf(Seg("unl ", muted), Seg(part.text, ink))
            HeaderPart.Kind.PROFILE -> {
                val tag = part.profile?.kind?.tag ?: part.text.take(1).uppercase()
                listOf(Seg(" $tag ", CellStyle.of(part.ink, flags = CellStyle.INVERSE or BOLD)))
            }

            HeaderPart.Kind.SETTINGS -> listOf(Seg(SETTINGS, muted))
            HeaderPart.Kind.BATTERY -> listOf(Seg(part.text, ink))
            HeaderPart.Kind.SCRAMBLE -> listOf(Seg(part.text, ink))
        }
    }

    /** ⚙ as text, never as a color emoji. */
    const val SETTINGS = "⚙\uFE0E"

    /** The todos' sign: a check mark, as the GUI shows. Every bundled font has it. */
    const val TODO_SIGN = "✓ "

    /** The cells [part] takes. */
    fun width(part: HeaderPart): Int =
        segs(part).sumOf { CellWidth.total(it.text) } + if (part.kind == HeaderPart.Kind.BATTERY) BATTERY_WIDTH else 0

    /** The battery before its percentage, and a cell of room after it. */
    private const val BATTERY_WIDTH = BoxShapes.BATTERY_CELLS + 1

    /**
     * The header's row: [parts] in [cols] columns. [zones] are the column ranges free of a camera
     * cutout, left to right; the whole row when there is none.
     */
    fun row(parts: HeaderParts, cols: Int, zones: List<IntRange> = listOf(0 until cols)): HomeCells {
        val w = HomeWriter(cols)
        val all = parts.left + parts.right
        val widths = all.map(::width)
        val places = place(zones, widths.take(parts.left.size), widths.drop(parts.left.size), GAP)
        val xs = places.first + places.second
        for ((i, part) in all.withIndex()) {
            val x = xs[i] ?: continue
            var col = x
            if (part.kind == HeaderPart.Kind.BATTERY) col = batteryBar(w, part, col)
            for (s in segs(part)) col = w.put(col, 0, s.text, s.style)
            // A cell of room on each side takes the tap too: a part can be one cell wide.
            w.spot(i, x - 1, 0, widths[i] + 2)
        }
        return w.build()
    }

    /** The battery's cells ([BoxShapes.battery]) from [from], in the part's ink; returns the column after the room that follows. */
    private fun batteryBar(w: HomeWriter, part: HeaderPart, from: Int): Int {
        val cells = StringBuilder()
        for (i in 0 until BoxShapes.BATTERY_CELLS) cells.append(BoxShapes.battery(i, part.battery ?: 0, part.charging).toChar())
        return w.put(from, 0, cells.toString(), CellStyle.of(part.ink)) + 1
    }

    /**
     * The columns of a row [cols] wide that are free of the cutouts, as pixel ranges [cutouts]
     * across a row whose first cell starts at [originX] and whose cells are [cellW] pixels wide.
     * Each cutout keeps [gap] pixels clear on both sides; a cell that touches it is not free.
     * [cornerLeft] and [cornerRight]: the pixels the screen's rounded corners hide at the row's left
     * end and at its right end, [widthPx] from the left. They are kept clear as a cutout is, so a
     * cell that a corner would cut stays empty.
     */
    fun zones(
        cols: Int,
        originX: Int,
        cellW: Int,
        cutouts: List<IntRange>,
        gap: Int,
        cornerLeft: Int = 0,
        cornerRight: Int = 0,
        widthPx: Int = 2 * originX + cols * cellW,
    ): List<IntRange> {
        var zones = listOf(0 until cols)
        // A corner is a cutout at the row's end.
        val all = cutouts + listOfNotNull(
            if (cornerLeft > 0) 0 until cornerLeft else null,
            if (cornerRight > 0) (widthPx - cornerRight) until widthPx else null,
        )
        for (c in all) {
            val from = floor((c.first - gap - originX).toDouble() / cellW).toInt()
            val to = ceil((c.last + 1 + gap - originX).toDouble() / cellW).toInt() - 1
            zones = zones.flatMap { z ->
                if (to < z.first || from > z.last) listOf(z)
                else listOf(z.first until from, (to + 1)..z.last).filterNot { it.isEmpty() }
            }
        }
        return zones
    }

    /**
     * Where each part goes, by column, as the GUI's header places them: the left side's parts
     * ([left], widths in order) start at the first zone with room for them; the right side's
     * ([right]) end at the end of the last zone. Parts that don't fit are dropped from the middle
     * outward (null), and the left side's first part always shows.
     */
    fun place(zones: List<IntRange>, left: List<Int>, right: List<Int>, spacing: Int): Pair<List<Int?>, List<Int?>> {
        if (zones.isEmpty()) return left.map { null } to right.map { null }
        fun room(z: IntRange) = z.last - z.first + 1
        fun width(ws: List<Int>) = ws.sum() + spacing * (ws.size - 1).coerceAtLeast(0)
        var keep = left.size
        var lz = -1
        while (keep > 0) {
            lz = zones.indexOfFirst { room(it) >= width(left.take(keep)) }
            if (lz >= 0) break
            keep--
        }
        if (keep == 0 && left.isNotEmpty()) {
            keep = 1
            lz = zones.indices.maxBy { room(zones[it]) }
        }
        val leftXs = MutableList<Int?>(left.size) { null }
        var x = if (keep > 0) zones[lz].first else 0
        for (i in 0 until keep) {
            leftXs[i] = x
            x += left[i] + spacing
        }
        val last = zones.lastIndex
        val start = if (keep > 0 && lz == last) x else zones[last].first
        val space = zones[last].last + 1 - start
        var from = 0
        while (from < right.size && width(right.drop(from)) > space) from++
        val rightXs = MutableList<Int?>(right.size) { null }
        x = zones[last].last + 1
        for (i in right.indices.reversed()) {
            if (i < from) break
            x -= right[i]
            rightXs[i] = x
            x -= spacing
        }
        return leftXs to rightXs
    }
}

/**
 * The pinned line on the grid: what's playing and the pinned scripts' status lines, one at a time.
 * Each item is two rows: its text, and under it a progress bar an eighth of a cell thin (`▔`)
 * when it has a progress, else nothing. The dots (`·●·`) are cells of their own at the row's end,
 * so they stay put while the items take turns.
 */
object PinnedCells {
    /** Spot ids: the item itself (a tap runs it, a long press opens its menu), and ⏭ on what's playing. */
    const val ITEM = 0
    const val NEXT = 1

    /** The cells the next-track button takes: ` ⏭ ` and a cell of room. */
    const val NEXT_CELLS = 4

    /** ⏭ and ‖ as text, never as color emoji. */
    private const val NEXT_GLYPH = "⏭\uFE0E"

    /** A script's status line in [ink] (the script color, or its own), and its progress line when it has one. [room]: cells kept free at the end for the dots. */
    fun script(text: String, ink: Ink, progress: Float?, cols: Int, room: Int): HomeCells {
        val w = HomeWriter(cols, 2)
        val right = (cols - room).coerceAtLeast(1)
        w.line(oneLine(text), CellStyle.of(ink), 0, 0, right)
        w.spot(ITEM, 0, 0, cols, 2)
        if (progress != null) w.thinBar(0, 1, cols, progress, CellStyle.of(ink))
        return w.build()
    }

    /**
     * What's playing: `♪` while it plays (`‖` paused), the title and artist, `⏭` at the end, and
     * the track's progress under it, [filled] cells of [cols] (see [filledCells]).
     */
    fun media(line: String, playing: Boolean, filled: Int, cols: Int, room: Int): HomeCells {
        val w = HomeWriter(cols, 2)
        val end = (cols - room).coerceAtLeast(NEXT_CELLS + 3)
        val mark = if (playing) Seg("♪ ", CellStyle.of(Ink.PROMPT, flags = CellStyle.BOLD)) else Seg("‖ ", CellStyle.of(Ink.DIM))
        w.line(listOf(mark, Seg(oneLine(line), CellStyle.of(Ink.FG))), 0, 0, end - NEXT_CELLS)
        w.put(end - NEXT_CELLS + 1, 0, NEXT_GLYPH, CellStyle.of(Ink.DIM, flags = CellStyle.BOLD))
        w.spot(ITEM, 0, 0, end - NEXT_CELLS, 2)
        w.spot(NEXT, end - NEXT_CELLS, 0, NEXT_CELLS, 2)
        w.thinBar(0, 1, cols, filled / cols.coerceAtLeast(1).toFloat(), CellStyle.of(Ink.PROMPT))
        return w.build()
    }

    /** The cells the dots take for [count] items: a dot and a space each. */
    fun dotsWidth(count: Int) = count * 2

    /** The dots: `·` for each item, `●` in the accent color for the one on screen. */
    fun dots(count: Int, index: Int): HomeCells {
        val w = HomeWriter(dotsWidth(count).coerceAtLeast(1))
        for (i in 0 until count) {
            w.put(i * 2 + 1, 0, if (i == index) "●" else "·", if (i == index) CellStyle.of(Ink.PROMPT, flags = CellStyle.BOLD) else CellStyle.of(Ink.DIM))
        }
        return w.build()
    }
}
