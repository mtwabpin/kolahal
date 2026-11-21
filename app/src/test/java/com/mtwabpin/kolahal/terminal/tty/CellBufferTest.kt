package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.terminal.Ink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The dense grid screens draw into. */
class CellBufferTest {
    private val plain = CellStyle.PLAIN

    @Test
    fun textGoesOneClusterPerCell() {
        val b = CellBuffer(10, 2)
        assertEquals(2, b.put(0, 0, "hi", plain))
        assertEquals("hi        ", b.rowText(0))
        assertEquals('h'.code, b.glyphAt(0, 0))
        assertEquals(CellBuffer.EMPTY, b.glyphAt(2, 0))
    }

    @Test
    fun whatFallsOffTheEdgeIsCut() {
        val b = CellBuffer(10, 1)
        assertEquals(13, b.put(8, 0, "hello", plain))
        assertEquals("        he", b.rowText(0))
        // From the left too: only what is on the grid shows.
        b.clear()
        b.put(-3, 0, "hello", plain)
        assertEquals("lo        ", b.rowText(0))
        // Rows and columns outside are ignored.
        b.put(0, 5, "x", plain)
        b.put(-1, 0, 'x'.code, plain)
        b.put(10, 0, 'x'.code, plain)
        assertEquals("lo        ", b.rowText(0))
    }

    @Test
    fun aWideClusterTakesTwoCells() {
        val b = CellBuffer(10, 1)
        assertEquals(3, b.put(0, 0, "中a", plain))
        assertEquals(0x4E2D, b.glyphAt(0, 0))
        assertEquals(CellBuffer.WIDE_TAIL, b.glyphAt(1, 0))
        assertEquals('a'.code, b.glyphAt(2, 0))
        assertEquals(2, b.cellsOf(b.glyphAt(0, 0)))
        assertEquals("中a" + " ".repeat(7), b.rowText(0))
    }

    @Test
    fun aWideClusterNeverSplitsAtTheEdge() {
        val b = CellBuffer(10, 1)
        b.put(9, 0, "中", plain)
        assertEquals(CellBuffer.EMPTY, b.glyphAt(9, 0))
    }

    @Test
    fun overwritingHalfAWideClusterBlanksTheOtherHalf() {
        val b = CellBuffer(4, 1)
        b.put(0, 0, "中", plain)
        b.put(1, 0, "x", plain)
        assertEquals(CellBuffer.EMPTY, b.glyphAt(0, 0))
        assertEquals('x'.code, b.glyphAt(1, 0))
        b.put(2, 0, "文", plain)
        b.put(2, 0, "y", plain)
        assertEquals(CellBuffer.EMPTY, b.glyphAt(3, 0))
    }

    @Test
    fun clustersOfSeveralCodePointsAreKeptOnce() {
        val b = CellBuffer(6, 1)
        b.put(0, 0, "é", plain)
        val g = b.glyphAt(0, 0)
        assertTrue(g < CellBuffer.WIDE_TAIL)
        assertEquals("é", b.clusterOf(g))
        b.put(1, 0, "é", plain)
        assertEquals(g, b.glyphAt(1, 0))
        val family = "👨‍👩‍👧"
        assertEquals(4, b.put(2, 0, family, plain))
        assertEquals(family, b.clusterOf(b.glyphAt(2, 0)))
        assertEquals(CellBuffer.WIDE_TAIL, b.glyphAt(3, 0))
    }

    @Test
    fun stylesKeepTheirInksAndFlags() {
        val b = CellBuffer(3, 1)
        b.put(1, 0, 'x'.code, CellStyle.of(Ink.ERROR, flags = CellStyle.BOLD))
        val s = b.styleAt(1, 0)
        assertEquals(Ink.ERROR, s.fg)
        assertEquals(Ink.BG, s.bg)
        assertTrue(s.has(CellStyle.BOLD))
        assertFalse(s.has(CellStyle.FAINT))
        val inverse = CellStyle.of(Ink.PROMPT, Ink.SURFACE, CellStyle.INVERSE)
        assertEquals(Ink.PROMPT, inverse.fg)
        assertEquals(Ink.SURFACE, inverse.bg)
        assertEquals(Ink.FG, inverse.ink(Ink.FG).fg)
        assertEquals(Ink.SURFACE, inverse.ink(Ink.FG).bg)
        assertTrue(inverse.ink(Ink.FG).has(CellStyle.INVERSE))
    }

    @Test
    fun onlyChangedRowsAreDirty() {
        val b = CellBuffer(4, 3)
        assertEquals(3, b.dirty.cardinality())
        b.dirty.clear()
        b.put(0, 1, 'a'.code, plain)
        assertEquals(listOf(1), b.dirty.stream().toArray().toList())
        b.dirty.clear()
        // The same again changes nothing.
        b.put(0, 1, 'a'.code, plain)
        b.put(0, 0, CellBuffer.EMPTY, plain)
        assertTrue(b.dirty.isEmpty)
    }

    @Test
    fun fillClearAndScroll() {
        val b = CellBuffer(4, 3)
        b.fill(0, 0, 2, 2, '#'.code, plain)
        assertEquals("##  \n##  \n    ", b.dump())
        b.put(0, 2, "abcd", plain)
        b.scrollUp(1)
        assertEquals("##  \nabcd\n    ", b.dump())
        b.clear()
        assertEquals("    \n    \n    ", b.dump())
        b.put(0, 0, 'z'.code, plain)
        b.put(0, 0, CellBuffer.EMPTY, plain)
        assertEquals(CellBuffer.EMPTY, b.glyphAt(0, 0))
    }

    @Test
    fun resizeMakesABlankGrid() {
        val b = CellBuffer(4, 3)
        b.put(0, 0, "ab", plain)
        b.resize(6, 2)
        assertEquals(6, b.cols)
        assertEquals(2, b.rows)
        assertEquals("      \n      ", b.dump())
        assertEquals(2, b.dirty.cardinality())
    }
}
