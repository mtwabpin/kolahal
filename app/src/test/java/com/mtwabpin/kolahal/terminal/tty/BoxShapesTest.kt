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
}
