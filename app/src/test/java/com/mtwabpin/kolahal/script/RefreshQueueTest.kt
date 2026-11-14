package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.script.ScriptScheduler.Queued
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A script's two kinds of refresh runs share one queue: its pinned line's (`"refresh"`) and its
 * widget's (`"widget"`). [ScriptScheduler.queued] says where a new request goes.
 */
class RefreshQueueTest {
    private val pinned = ScriptScheduler.REFRESH
    private val widget = ScriptScheduler.WIDGET

    @Test fun bothKindsUseOneQueue() {
        assertEquals("script-7-refresh", ScriptScheduler.refreshQueue(7))
        assertEquals("refresh" to "widget", pinned to widget)
    }

    /** Nothing waits or runs: the run starts the queue. */
    @Test fun theFirstRunStartsTheQueue() {
        assertEquals(Queued.ALONE, ScriptScheduler.queued(pinned, emptyList()))
        assertEquals(Queued.ALONE, ScriptScheduler.queued(widget, emptyList()))
    }

    /** Never two of a kind: while one waits or runs, more requests for it are dropped. */
    @Test fun aSecondRunOfAKindIsDropped() {
        assertEquals(Queued.DROPPED, ScriptScheduler.queued(pinned, listOf(pinned)))
        assertEquals(Queued.DROPPED, ScriptScheduler.queued(widget, listOf(widget)))
        // Also when the other kind is there too, in either order.
        assertEquals(Queued.DROPPED, ScriptScheduler.queued(pinned, listOf(pinned, widget)))
        assertEquals(Queued.DROPPED, ScriptScheduler.queued(pinned, listOf(widget, pinned)))
        assertEquals(Queued.DROPPED, ScriptScheduler.queued(widget, listOf(pinned, widget)))
    }

    /**
     * The other kind waits or runs: this run goes after it. It is not dropped, because it has its
     * own event and its own job: a widget's run notes its end for the card, and a script may set its
     * pinned line only in a `"refresh"` run.
     */
    @Test fun theOtherKindGoesAfterNotBeside() {
        assertEquals(Queued.AFTER, ScriptScheduler.queued(widget, listOf(pinned)))
        assertEquals(Queued.AFTER, ScriptScheduler.queued(pinned, listOf(widget)))
    }

    /** A pinned line and a widget that both ask every few seconds: the queue never holds more than one of each. */
    @Test fun theQueueHoldsOneOfEachKindAtMost() {
        val queue = ArrayList<String>()
        for (kind in listOf(pinned, widget, widget, pinned, pinned, widget)) {
            if (ScriptScheduler.queued(kind, queue) != Queued.DROPPED) queue += kind
        }
        assertEquals(listOf(pinned, widget), queue)
        // The first one ends. Its kind may be asked for again, and goes to the end.
        queue.removeAt(0)
        assertEquals(Queued.AFTER, ScriptScheduler.queued(pinned, queue))
        assertEquals(Queued.DROPPED, ScriptScheduler.queued(widget, queue))
    }
}
