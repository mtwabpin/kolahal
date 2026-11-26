package com.mtwabpin.kolahal.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Undo in Arrange widgets ([ArrangeUndo]): the page as it was before one change comes back, only the
 * last change goes back, and a second change takes the place of the first.
 */
class ArrangeUndoTest {
    private fun script(name: String, column: Int, top: Int, span: Int = 6, height: Int = 88, order: Int = 0) =
        PlacedWidget(null, WidgetSource.script(name), 0L, height, order, span = span, column = column, topDp = top)

    private val page = listOf(
        script("a", column = 0, top = 0),
        script("b", column = 6, top = 160, order = 1),
        script("c", column = 0, top = 320, span = 12, order = 2),
    )

    private fun places(all: List<PlacedWidget>) =
        all.associate { it.source!!.removePrefix("script:") to listOf(it.column, it.topDp, it.span, it.heightDp) }

    @Test
    fun undoAfterCloseGapsPutsThePageBack() {
        val undo = ArrangeUndo()
        val packed = WidgetPlacement.closeGaps(page)
        val step = undo.record(ArrangeUndo.Change.CLOSE_GAPS, null, page, packed)!!
        assertEquals("Gaps closed", step.change.done)
        assertTrue(undo.take(step))
        assertEquals(places(page), places(ArrangeUndo.restored(packed, step.before)))
    }

    @Test
    fun undoAfterAMoveBringsBackTheWidgetsItPushed() {
        assumeTrue("Undo after a move", ArrangeUndo.Change.MOVE in ArrangeUndo.OFFERED)
        val undo = ArrangeUndo()
        val key = page[2].key
        // "c", full width, goes to the top: "a" and "b" move down.
        val moved = WidgetPlacement.moveTo(page, key, 0, 0)
        assertTrue(moved.first { it.key == page[0].key }.topDp > 0)
        val step = undo.record(ArrangeUndo.Change.MOVE, key, page, moved)!!
        assertEquals("Widget moved", step.change.done)
        assertTrue(undo.take(step))
        assertEquals(places(page), places(ArrangeUndo.restored(moved, step.before)))
    }

    @Test
    fun oneLevelOnly() {
        val undo = ArrangeUndo()
        val packed = WidgetPlacement.closeGaps(page)
        val step = undo.record(ArrangeUndo.Change.CLOSE_GAPS, null, page, packed)!!
        assertTrue(undo.take(step))
        assertFalse("taken already", undo.take(step))
    }

    @Test
    fun aSecondChangeTakesThePlaceOfTheFirst() {
        assumeTrue("Undo after a move", ArrangeUndo.Change.MOVE in ArrangeUndo.OFFERED)
        val undo = ArrangeUndo()
        val packed = WidgetPlacement.closeGaps(page)
        val first = undo.record(ArrangeUndo.Change.CLOSE_GAPS, null, page, packed)!!
        val moved = WidgetPlacement.moveTo(packed, page[1].key, 0, 0)
        val second = undo.record(ArrangeUndo.Change.MOVE, page[1].key, packed, moved)!!
        assertFalse("the first snackbar's Undo does nothing now", undo.take(first))
        assertTrue(undo.take(second))
        // Only the move goes back: the gaps stay closed.
        assertEquals(places(packed), places(ArrangeUndo.restored(moved, second.before)))
    }

    @Test
    fun resizesOfOneWidgetInARowAreOneStep() {
        assumeTrue("Undo after a resize", ArrangeUndo.Change.RESIZE in ArrangeUndo.OFFERED)
        val undo = ArrangeUndo()
        val key = page[0].key
        val taller = WidgetPlacement.resize(page, key, 6, 96)
        val first = undo.record(ArrangeUndo.Change.RESIZE, key, page, taller)!!
        assertEquals("Widget resized", first.change.done)
        val tallest = WidgetPlacement.resize(taller, key, 6, 104)
        val second = undo.record(ArrangeUndo.Change.RESIZE, key, taller, tallest)!!
        // The first snackbar, replaced, expires: that doesn't touch the step that goes on.
        undo.expire(first)
        assertTrue(undo.take(second))
        assertEquals(places(page), places(ArrangeUndo.restored(tallest, second.before)))
    }

    @Test
    fun aResizeOfAnotherWidgetIsAStepOfItsOwn() {
        assumeTrue("Undo after a resize", ArrangeUndo.Change.RESIZE in ArrangeUndo.OFFERED)
        val undo = ArrangeUndo()
        val taller = WidgetPlacement.resize(page, page[0].key, 6, 96)
        undo.record(ArrangeUndo.Change.RESIZE, page[0].key, page, taller)!!
        val wider = WidgetPlacement.resize(taller, page[1].key, 3, 88)
        val step = undo.record(ArrangeUndo.Change.RESIZE, page[1].key, taller, wider)!!
        assertSame(taller, step.before)
    }

    @Test
    fun nothingToTakeBackWhenNothingMoved() {
        assumeTrue("Undo after a move", ArrangeUndo.Change.MOVE in ArrangeUndo.OFFERED)
        val undo = ArrangeUndo()
        assertNull(undo.record(ArrangeUndo.Change.MOVE, page[0].key, page, page))
    }

    @Test
    fun anExpiredStepCantBeTaken() {
        val undo = ArrangeUndo()
        val step = undo.record(ArrangeUndo.Change.CLOSE_GAPS, null, page, WidgetPlacement.closeGaps(page))!!
        undo.expire(step)
        assertFalse(undo.take(step))
    }

    @Test
    fun aChangeThatCantBeTakenBackForgetsTheStepBefore() {
        val undo = ArrangeUndo()
        val step = undo.record(ArrangeUndo.Change.CLOSE_GAPS, null, page, WidgetPlacement.closeGaps(page))!!
        undo.forget()
        assertFalse(undo.take(step))
    }

    @Test
    fun restoredKeepsWhatChangedSinceOtherThanPlaces() {
        val packed = WidgetPlacement.closeGaps(page)
        // Since then: "a" got another look, "b" was removed, "d" was added.
        val now = packed.map { if (it.key == page[0].key) it.copy(look = WidgetLook.SHAPE) else it }
            .filterNot { it.key == page[1].key } + script("d", column = 6, top = 0, order = 3)
        val back = ArrangeUndo.restored(now, page)
        assertEquals(WidgetLook.SHAPE, back.first { it.key == page[0].key }.look)
        assertEquals(0 to 0, back.first { it.key == page[0].key }.let { it.column to it.topDp })
        assertEquals(page[2].topDp, back.first { it.key == page[2].key }.topDp)
        assertNull(back.firstOrNull { it.key == page[1].key })
        assertEquals(6 to 0, back.first { it.source == WidgetSource.script("d") }.let { it.column to it.topDp })
    }
}
