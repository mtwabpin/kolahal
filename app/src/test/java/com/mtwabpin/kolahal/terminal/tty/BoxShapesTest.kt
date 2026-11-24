package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.terminal.tty.BoxShapes.DOUBLE
import com.mtwabpin.kolahal.terminal.tty.BoxShapes.HEAVY
import com.mtwabpin.kolahal.terminal.tty.BoxShapes.LIGHT
import com.mtwabpin.kolahal.terminal.tty.BoxShapes.NONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shapes the painter draws for box-drawing and block characters. */
class BoxShapesTest {
    /** Up, right, down, left of [c]. */
    private fun arms(c: Char): List<Int> = BoxShapes.arms(c.code).let {
        listOf(BoxShapes.up(it), BoxShapes.right(it), BoxShapes.down(it), BoxShapes.left(it))
    }

    @Test
    fun linesHaveTheArmsTheirNamesSay() {
        assertEquals(listOf(LIGHT, LIGHT, LIGHT, LIGHT), arms('┼'))
        assertEquals(listOf(NONE, LIGHT, NONE, LIGHT), arms('─'))
        assertEquals(listOf(HEAVY, NONE, HEAVY, NONE), arms('┃'))
        assertEquals(listOf(NONE, LIGHT, LIGHT, NONE), arms('┌'))
        assertEquals(listOf(LIGHT, NONE, NONE, LIGHT), arms('┘'))
        assertEquals(listOf(NONE, HEAVY, HEAVY, NONE), arms('┏'))
        assertEquals(listOf(HEAVY, LIGHT, LIGHT, NONE), arms('┞'))
        assertEquals(listOf(NONE, DOUBLE, NONE, DOUBLE), arms('═'))
        assertEquals(listOf(NONE, DOUBLE, DOUBLE, NONE), arms('╔'))
        assertEquals(listOf(DOUBLE, DOUBLE, DOUBLE, DOUBLE), arms('╬'))
        assertEquals(listOf(LIGHT, DOUBLE, LIGHT, DOUBLE), arms('╪'))
        assertEquals(listOf(NONE, HEAVY, NONE, LIGHT), arms('╼'))
        assertEquals(listOf(NONE, LIGHT, LIGHT, NONE), arms('╭'))
    }

    /** Every solid line character joins at least one side, so none is drawn as nothing. */
    @Test
    fun everyLineHasAnArm() {
        for (cp in (0x2500..0x254B) + (0x2550..0x2570) + (0x2574..0x257F)) {
            assertTrue("U+%04X has no arm".format(cp), BoxShapes.arms(cp) != 0)
        }
    }

    @Test
    fun dashesArcsAndDiagonals() {
        assertEquals(3, BoxShapes.dashes('┄'.code))
        assertEquals(4, BoxShapes.dashes('┊'.code))
        assertEquals(2, BoxShapes.dashes('╌'.code))
        assertEquals(0, BoxShapes.dashes('─'.code))
        assertTrue(BoxShapes.isArc('╰'.code))
        assertFalse(BoxShapes.isArc('└'.code))
        assertEquals(1, BoxShapes.diagonal('╱'.code))
        assertEquals(2, BoxShapes.diagonal('╲'.code))
        assertEquals(3, BoxShapes.diagonal('╳'.code))
    }

    /** left, top, right, bottom in eighths. */
    private fun eighths(c: Char) = BoxShapes.eighths(c.code).let {
        listOf(it and 0xF, (it shr 4) and 0xF, (it shr 8) and 0xF, (it shr 12) and 0xF)
    }

    @Test
    fun blocksAreEighthsQuadrantsOrShades() {
        assertEquals(listOf(0, 0, 8, 8), eighths('█'))
        assertEquals(listOf(0, 0, 8, 4), eighths('▀'))
        assertEquals(listOf(0, 4, 8, 8), eighths('▄'))
        assertEquals(listOf(0, 7, 8, 8), eighths('▁'))
        assertEquals(listOf(0, 0, 4, 8), eighths('▌'))
        assertEquals(listOf(0, 0, 1, 8), eighths('▏'))
        assertEquals(listOf(7, 0, 8, 8), eighths('▕'))
        assertEquals(-1, BoxShapes.eighths('▚'.code))
        assertEquals(1 or 8, BoxShapes.quadrants('▚'.code))
        assertEquals(2 or 4 or 8, BoxShapes.quadrants('▟'.code))
        assertEquals(2, BoxShapes.shade('▒'.code))
        assertTrue(BoxShapes.isDrawn('█'.code))
        assertFalse(BoxShapes.isDrawn('a'.code))
    }

    /**
     * Each cell of the battery at each level, charging or not, is a sign of its own that the
     * painter draws, and says which cell it is, how full and whether charging. None is a box or
     * block character, and none is a character of the fonts.
     */
    @Test
    fun theBatterysCellsAreDrawnSigns() {
        val all = HashSet<Int>()
        for (charging in listOf(false, true)) for (i in 0 until BoxShapes.BATTERY_CELLS) for (percent in 0..100) {
            val cp = BoxShapes.battery(i, percent, charging)
            assertTrue(all.add(cp))
            assertTrue(BoxShapes.isDrawn(cp))
            assertEquals(Character.PRIVATE_USE.toInt(), Character.getType(cp))
            assertEquals(i, BoxShapes.batteryCell(cp))
            assertEquals(percent, BoxShapes.batteryPercent(cp))
            assertEquals(charging, BoxShapes.batteryCharging(cp))
            assertEquals(1, CellWidth.ofCodePoint(cp))
            // Turned around in a right-to-left frame by the painter, so it fills from the right.
            assertEquals(0, BoxShapes.mirror(cp))
            // Not a box or block shape.
            assertEquals(0, BoxShapes.arms(cp))
            assertEquals(-1, BoxShapes.eighths(cp))
        }
        // Out of range, a level stays within 0 and 100.
        assertEquals(BoxShapes.battery(0, 100, false), BoxShapes.battery(0, 120, false))
        assertEquals(BoxShapes.battery(0, 0, false), BoxShapes.battery(0, -5, false))
        // The characters around them are not battery cells.
        assertEquals(-1, BoxShapes.batteryCell(all.min() - 1))
        assertEquals(-1, BoxShapes.batteryCell(all.max() + 1))
        assertFalse(BoxShapes.isDrawn(all.max() + 1))
        assertEquals(-1, BoxShapes.batteryCell('█'.code))
    }
}
