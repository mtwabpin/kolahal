/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty

/**
 * The shapes of the box-drawing (U+2500–257F) and block (U+2580–259F) characters, which the
 * painter draws as lines and rectangles instead of font glyphs. Font glyphs leave gaps when the
 * row height is not the font's own; drawn shapes join at every font, size and line height.
 *
 * A line character is four arms from the cell's middle: up, right, down and left, each [NONE],
 * [LIGHT], [HEAVY] or [DOUBLE] ([arms]). Some are dashed ([dashes]), rounded ([isArc]) or
 * diagonal ([diagonal]). A block is a rectangle in eighths of the cell ([eighths]), quadrants
 * ([quadrants]) or a shade ([shade]).
 *
 * The painter also draws one sign that fonts lack, kept in the Private Use Area: the header's
 * battery ([battery]).
 */
object BoxShapes {
    const val NONE = 0
    const val LIGHT = 1
    const val HEAVY = 2
    const val DOUBLE = 3

    /** The cells a battery takes: its body, with the terminal at the end of the last. */
    const val BATTERY_CELLS = 3

    /** The first of the battery's cells ([battery]). */
    private const val BATTERY = 0xF600

    /** Levels a battery shows: 0 to 100. */
    private const val LEVELS = 101

    /** After the last of the battery's cells: each cell at each level, charging or not. */
    private const val BATTERY_END = BATTERY + 2 * BATTERY_CELLS * LEVELS

    /** True when the painter draws [cp] itself. */
    fun isDrawn(cp: Int) = cp in 0x2500..0x259F || cp in BATTERY until BATTERY_END

    /**
     * Cell [index] (0 to [BATTERY_CELLS] - 1) of a battery filled to [percent] (0 to 100), as the
     * GUI's header draws it: an outline as tall as a digit with a terminal on its right, filled
     * from the left, and while [charging] a bolt in its middle. Each cell draws its share of the
     * whole battery, so the cells join into one shape at any level.
     */
    fun battery(index: Int, percent: Int, charging: Boolean): Int =
        BATTERY + ((if (charging) BATTERY_CELLS else 0) + index.coerceIn(0, BATTERY_CELLS - 1)) * LEVELS +
                percent.coerceIn(0, 100)

    /** Which cell of a battery [cp] is, or -1 when it is not one. */
    fun batteryCell(cp: Int): Int = if (cp in BATTERY until BATTERY_END) (cp - BATTERY) / LEVELS % BATTERY_CELLS else -1

    /** How full the battery that [cp] is a cell of is, from 0 to 100. */
    fun batteryPercent(cp: Int): Int = (cp - BATTERY) % LEVELS

    /** True when the battery that [cp] is a cell of is charging. */
    fun batteryCharging(cp: Int): Boolean = (cp - BATTERY) / LEVELS >= BATTERY_CELLS

    /** The arms of a line character, packed: up, right shl 2, down shl 4, left shl 6. 0 for others. */
    fun arms(cp: Int): Int = if (cp in 0x2500..0x257F) ARMS[cp - 0x2500] else 0

    fun up(arms: Int) = arms and 3
    fun right(arms: Int) = (arms shr 2) and 3
    fun down(arms: Int) = (arms shr 4) and 3
    fun left(arms: Int) = (arms shr 6) and 3

    /** How many dashes a dashed line has in its cell (2, 3 or 4); 0 when it is solid. */
    fun dashes(cp: Int): Int = when (cp) {
        in 0x2504..0x2507 -> 3
        in 0x2508..0x250B -> 4
        in 0x254C..0x254F -> 2
        else -> 0
    }

    /** ╭ ╮ ╯ ╰: a rounded corner. Its [arms] say which two sides it joins. */
    fun isArc(cp: Int) = cp in 0x256D..0x2570

    /** ╱ is 1, ╲ is 2, ╳ is 3 (both); 0 otherwise. */
    fun diagonal(cp: Int): Int = if (cp in 0x2571..0x2573) cp - 0x2570 else 0

    /**
     * A block that is one rectangle, in eighths of the cell, packed: left, top shl 4, right shl 8,
     * bottom shl 12 (each 0..8). -1 for others. █ is (0, 0, 8, 8), ▄ is (0, 4, 8, 8).
     */
    fun eighths(cp: Int): Int = when (cp) {
        0x2580 -> rect(0, 0, 8, 4)
        in 0x2581..0x2588 -> rect(0, 8 - (cp - 0x2580), 8, 8)
        in 0x2589..0x258F -> rect(0, 0, 8 - (cp - 0x2588), 8)
        0x2590 -> rect(4, 0, 8, 8)
        0x2594 -> rect(0, 0, 8, 1)
        0x2595 -> rect(7, 0, 8, 8)
        else -> -1
    }

    /** Quadrants filled, as bits: upper left 1, upper right 2, lower left 4, lower right 8. 0 for others. */
    fun quadrants(cp: Int): Int = when (cp) {
        0x2596 -> 4
        0x2597 -> 8
        0x2598 -> 1
        0x2599 -> 1 or 4 or 8
        0x259A -> 1 or 8
        0x259B -> 1 or 2 or 4
        0x259C -> 1 or 2 or 8
        0x259D -> 2
        0x259E -> 2 or 4
        0x259F -> 2 or 4 or 8
        else -> 0
    }

    /** ░ ▒ ▓: how much of the ink shows, in quarters (1, 2, 3). 0 for others. */
    fun shade(cp: Int): Int = if (cp in 0x2591..0x2593) cp - 0x2590 else 0

    /**
     * [cp] as seen in a mirror, for a frame laid out right to left: `┐` for `┌`, `▐` for `▌`,
     * `▏` for `▕`. [cp] itself when it looks the same both ways, and 0 for a block with no mirror
     * character (`▎`, `▋`…): the painter flips that one ([CellStyle.MIRROR]). A battery's cells
     * are flipped too, so it fills from the right as a bar does.
     */
    fun mirror(cp: Int): Int {
        if (!isDrawn(cp)) return cp
        if (cp <= 0x257F) return LINE_MIRRORS[cp - 0x2500]
        if (batteryCell(cp) >= 0) return 0
        return when (cp) {
            0x258C -> 0x2590
            0x2590 -> 0x258C
            0x258F -> 0x2595
            0x2595 -> 0x258F
            0x2589, 0x258A, 0x258B, 0x258D, 0x258E -> 0
            0x2596 -> 0x2597
            0x2597 -> 0x2596
            0x2598 -> 0x259D
            0x259D -> 0x2598
            0x2599 -> 0x259F
            0x259F -> 0x2599
            0x259A -> 0x259E
            0x259E -> 0x259A
            0x259B -> 0x259C
            0x259C -> 0x259B
            else -> cp
        }
    }

    /** The line characters' mirrors: the same arms with left and right swapped, the same dashes; arcs and diagonals by hand. */
    private val LINE_MIRRORS: IntArray by lazy {
        IntArray(0x80) { i ->
            val cp = 0x2500 + i
            when {
                isArc(cp) -> intArrayOf(0x256E, 0x256D, 0x2570, 0x256F)[cp - 0x256D]
                diagonal(cp) == 1 -> 0x2572
                diagonal(cp) == 2 -> 0x2571
                diagonal(cp) == 3 -> cp
                else -> {
                    val a = arms(cp)
                    val swapped = up(a) or (left(a) shl 2) or (down(a) shl 4) or (right(a) shl 6)
                    if (swapped == a) cp
                    else (0x2500..0x257F).firstOrNull {
                        arms(it) == swapped && dashes(it) == dashes(cp) && !isArc(it) && diagonal(it) == 0
                    } ?: 0
                }
            }
        }
    }

    private fun rect(left: Int, top: Int, right: Int, bottom: Int) =
        left or (top shl 4) or (right shl 8) or (bottom shl 12)

    /**
     * Up, right, down, left for U+2500 to U+257F, 1 light, 2 heavy, 3 double, from the characters'
     * Unicode names. The arcs have the arms they join; the diagonals have none.
     */
    private val ARMS: IntArray = (
            "0101 0202 1010 2020 0101 0202 1010 2020 0101 0202 1010 2020 0110 0210 0120 0220 " +
                    "0011 0012 0021 0022 1100 1200 2100 2200 1001 1002 2001 2002 1110 1210 2110 1120 " +
                    "2120 2210 1220 2220 1011 1012 2011 1021 2021 2012 1022 2022 0111 0112 0211 0212 " +
                    "0121 0122 0221 0222 1101 1102 1201 1202 2101 2102 2201 2202 1111 1112 1211 1212 " +
                    "2111 1121 2121 2112 2211 1122 1221 2212 1222 2122 2221 2222 0101 0202 1010 2020 " +
                    "0303 3030 0310 0130 0330 0013 0031 0033 1300 3100 3300 1003 3001 3003 1310 3130 " +
                    "3330 1013 3031 3033 0313 0131 0333 1303 3101 3303 1313 3131 3333 0110 0011 1001 " +
                    "1100 0000 0000 0000 0001 1000 0100 0010 0002 2000 0200 0020 0201 1020 0102 2010"
            ).split(' ').map { a ->
            (a[0] - '0') or ((a[1] - '0') shl 2) or ((a[2] - '0') shl 4) or ((a[3] - '0') shl 6)
        }.toIntArray()
}
