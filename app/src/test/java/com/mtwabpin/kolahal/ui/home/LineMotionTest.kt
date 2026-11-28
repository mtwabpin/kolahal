package com.mtwabpin.kolahal.ui.home

import com.mtwabpin.kolahal.terminal.UndoOffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The log's lines move only around a line's delete and its Undo: a clear and everything else stay at once. */
class LineMotionTest {
    private fun line() = UndoOffer("Line deleted", oneLine = true) {}
    private fun clear() = UndoOffer("Screen cleared") {}

    @Test
    fun aLinesOfferMovesTheLinesComingAndGoing() {
        val offer = line()
        assertTrue("the line goes", LineMotion.moves(null, offer))
        assertTrue("it comes back on Undo", LineMotion.moves(offer, null))
        assertTrue("another line goes", LineMotion.moves(offer, line()))
        assertFalse("the same offer again", LineMotion.moves(offer, offer))
        assertFalse("nothing at all", LineMotion.moves(null, null))
    }

    @Test
    fun aClearMovesNothing() {
        val offer = clear()
        assertFalse(LineMotion.moves(null, offer))
        assertFalse(LineMotion.moves(offer, null))
        // A clear right after a delete: every line goes at once.
        assertFalse(LineMotion.moves(line(), offer))
        // A delete right after a clear.
        assertTrue(LineMotion.moves(offer, line()))
    }

    @Test
    fun seeingTheSameOfferAgainKeepsTheMotion() {
        val motion = LineMotion()
        val offer = line()
        motion.see(offer)
        assertTrue(motion.on)
        // Home composed again with the same offer: the lines still move.
        motion.see(offer)
        assertTrue(motion.on)
        motion.stop()
        motion.see(offer)
        assertFalse("its window ended", motion.on)
        motion.see(clear())
        assertFalse(motion.on)
        motion.see(null)
        assertFalse("the clear's Undo", motion.on)
    }

    @Test
    fun aSwipesDeleteMovesTheLinesBeforeItsOfferComes() {
        val motion = LineMotion()
        motion.start()
        assertTrue(motion.on)
        // Its offer keeps them moving.
        motion.see(line())
        assertTrue(motion.on)
    }
}
