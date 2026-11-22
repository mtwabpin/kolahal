/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.StyledText
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Takes a script's drawing calls (`g.put`, `g.fill`, `g.clear`, `g.box` and `g.bar`), for both
 * engines, and keeps them for the frame that goes to home ([take]). The engines pass the arguments as
 * Kotlin values (numbers, strings, maps). A wrong argument is an error for the script that says what
 * to pass. What is outside the grid is cut off at home, so a script can draw something partly off the
 * edge.
 *
 * One call may draw as much as [BUDGET_SCREENS] full screens. That keeps home's work for a frame
 * small, however much a script draws.
 *
 * [text] makes a text fit to show (secrets masked, log styles taken out).
 */
internal class ScreenDrawing(private val text: (String) -> String = { it }) {
    var cols = 0
        private set
    var rows = 0
        private set

    private var ops = IntArray(64)
    private var size = 0
    private val texts = ArrayList<String>()

    /** What the calls since the last [take] cost, in cells. */
    private var spent = 0

    private val budget: Int get() = maxOf(MIN_BUDGET, BUDGET_SCREENS * cols * rows)

    /** The grid is [cols] × [rows] cells now. */
    fun resize(cols: Int, rows: Int) {
        this.cols = cols.coerceAtLeast(1)
        this.rows = rows.coerceAtLeast(1)
    }

    /** Runs the drawing call [op] with the script's [args]. */
    fun call(op: String, args: List<Any?>) = when (op) {
        "put" -> put(Args(op, args))
        "fill" -> fill(Args(op, args))
        "clear" -> clear(Args(op, args))
        "box" -> box(Args(op, args))
        "bar" -> bar(Args(op, args))
        else -> throw IllegalArgumentException("$op isn't a drawing call; there are $CALLS")
    }

    /** What was drawn since the last call, and forgets it. [tick] marks the answer to a tick. */
    fun take(tick: Boolean): ScreenFrame {
        val frame = ScreenFrame(cols, rows, ops.copyOf(size), texts.toList(), tick)
        size = 0
        texts.clear()
        spent = 0
        return frame
    }

    private fun put(a: Args) {
        val col = a.int(0, "col")
        val row = a.int(1, "row")
        val raw = a.any(2)
        val style = a.style(3)
        // The styles of fmt go first, so that none can split a secret and hide it from the mask.
        val shown = text(StyledText.plain(render(raw)))
        // Every line of it starts below the grid, or the whole text is right of it: nothing to draw.
        if (row >= rows || col >= cols || (row < 0 && '\n' !in shown)) return spend(1)
        spend(shown.length)
        texts += shown
        record(ScreenFrame.PUT, col, row, texts.size - 1, style)
    }

    private fun fill(a: Args) {
        val col = a.int(0, "col")
        val row = a.int(1, "row")
        val w = a.int(2, "width", MAX_SIDE)
        val h = a.int(3, "height", MAX_SIDE)
        val char = a.any(4)
        val style = a.style(5)
        val cp = when (char) {
            null -> ' '.code
            is String -> {
                if (char.codePointCount(0, char.length) != 1) throw a.error("char is one character, or nothing for blank")
                char.codePointAt(0)
            }
            else -> throw a.error("char is one character, or nothing for blank")
        }
        // Only what is on the grid is sent: a rectangle that is partly off it is cut to the part that shows.
        val c0 = maxOf(col, 0)
        val r0 = maxOf(row, 0)
        val c1 = minOf(col + w, cols)
        val r1 = minOf(row + h, rows)
        spend(cells(col, row, w, h))
        if (c1 > c0 && r1 > r0) record(ScreenFrame.FILL, c0, r0, c1 - c0, r1 - r0, cp, style)
    }

    private fun clear(a: Args) {
        val style = a.style(0)
        spend(cols * rows)
        record(ScreenFrame.CLEAR, style)
    }

    private fun box(a: Args) {
        val col = a.int(0, "col")
        val row = a.int(1, "row")
        val w = a.int(2, "width", MAX_SIDE).coerceIn(0, MAX_SIDE)
        val h = a.int(3, "height", MAX_SIDE).coerceIn(0, MAX_SIDE)
        val style = a.style(4)
        spend(2 * (minOf(w, cols) + minOf(h, rows)))
        record(ScreenFrame.BOX, col, row, w, h, style)
    }

    private fun bar(a: Args) {
        val col = a.int(0, "col")
        val row = a.int(1, "row")
        val w = a.int(2, "width", MAX_SIDE).coerceIn(0, MAX_SIDE)
        val value = a.number(3, "value")
        val style = a.style(4)
        spend(cells(col, row, w, 1))
        record(ScreenFrame.BAR, col, row, w, (value.coerceIn(0.0, 1.0) * w * 8).roundToInt(), style)
    }

    /** The cells of a rectangle that are on the grid: no more than that is drawn. */
    private fun cells(col: Int, row: Int, w: Int, h: Int): Int =
        (minOf(col + w, cols) - maxOf(col, 0)).coerceAtLeast(0) * (minOf(row + h, rows) - maxOf(row, 0)).coerceAtLeast(0)

    private fun spend(cells: Int) {
        spent += maxOf(cells, 1)
        if (spent > budget) throw IllegalStateException("tty.screen: drew more than $BUDGET_SCREENS full screens in one call; draw less each time")
    }

    private fun record(vararg op: Int) {
        if (size + op.size > ops.size) ops = ops.copyOf(maxOf(ops.size * 2, size + op.size))
        op.copyInto(ops, size)
        size += op.size
    }

    /** [value] as the script would show it: text as it is, a whole number without ".0", nothing as no text. */
    private fun render(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        is Double -> Json.numberText(value)
        is Map<*, *>, is Collection<*> -> Json.stringify(value)
        else -> value.toString()
    }

    /** One call's arguments, read by place. [op] names the call in an error. */
    private class Args(private val op: String, private val args: List<Any?>) {
        fun any(i: Int): Any? = args.getOrNull(i)

        fun error(message: String) = IllegalArgumentException("g.$op: $message")

        fun number(i: Int, name: String): Double =
            (args.getOrNull(i) as? Number)?.toDouble()?.takeIf { it.isFinite() }
                ?: throw error("$name must be a number")

        /** A whole number: a fraction is dropped. Far off the grid is kept near it, so nothing overflows. */
        fun int(i: Int, name: String, far: Int = FAR): Int =
            floor(number(i, name)).coerceIn(-far.toDouble(), far.toDouble()).toInt()

        /** A color's name, or a table/object of the look: { color, bg, bold, … }. Nothing is the text color. */
        fun style(i: Int): Int = when (val value = args.getOrNull(i)) {
            null -> 0
            is String -> ScreenFrame.style(role(value), null, 0)
            is Map<*, *> -> options(value)
            else -> throw error("the style is a color ($ROLES), or a table with $STYLE_KEYS")
        }

        private fun options(map: Map<*, *>): Int {
            var fg: StyledText.ColorRole? = null
            var bg: StyledText.ColorRole? = null
            var flags = 0
            for ((k, v) in map) {
                when (val key = k.toString()) {
                    "color" -> fg = role(v?.toString() ?: continue)
                    "bg" -> bg = role(v?.toString() ?: continue)
                    in ScreenFrame.FLAGS -> if (v == true) flags = flags or ScreenFrame.FLAGS.getValue(key)
                    else -> throw error("no style '$key'; there are $STYLE_KEYS")
                }
            }
            return ScreenFrame.style(fg, bg, flags)
        }

        private fun role(name: String): StyledText.ColorRole = Fmt.color("g.$op", name)
    }

    companion object {
        /** The drawing calls, as a script names them. */
        val CALLS = listOf("put", "fill", "clear", "box", "bar")

        /** One call may draw as much as this many full screens. */
        const val BUDGET_SCREENS = 8

        /** A small grid (a narrow window) may still draw this many cells. */
        private const val MIN_BUDGET = 4_096

        /** A column or row is kept this close to the grid. */
        private const val FAR = 10_000

        /** A side of a box, or a bar, is kept this short. */
        private const val MAX_SIDE = 20_000

        private val ROLES = StyledText.ColorRole.entries.joinToString { it.role }
        private val STYLE_KEYS = (listOf("color", "bg") + ScreenFrame.FLAGS.keys).joinToString()
    }
}
