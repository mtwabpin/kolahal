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

    // ---- several widgets of one script: each is a kind of its own ----

    private val second = ScriptScheduler.widgetKind("2")
    private val third = ScriptScheduler.widgetKind("3")

    /** The first widget's kind is the one there always was. A further widget's has its id. */
    @Test fun eachWidgetIsAKindOfItsOwn() {
        assertEquals("widget", ScriptScheduler.widgetKind())
        assertEquals(widget, ScriptScheduler.widgetKind(""))
        assertEquals("widget#2", second)
        assertEquals("refresh:widget", ScriptScheduler.kindTag(widget))
        assertEquals("refresh:widget#2", ScriptScheduler.kindTag(second))
    }

    /** A run's tags say its kind again, with the widget's id. */
    @Test fun theTagCarriesTheWidgetsId() {
        val others = setOf("script", "com.mtwabpin.kolahal.script.ScriptWorker")
        for (kind in listOf(widget, second, third, ScriptScheduler.widgetKind("abc123"))) {
            assertEquals(kind, ScriptScheduler.kindOf(others + ScriptScheduler.kindTag(kind)))
        }
        assertEquals(pinned, ScriptScheduler.kindOf(others + ScriptScheduler.kindTag(pinned)))
        // A run queued before runs had this tag is a pinned line's.
        assertEquals(pinned, ScriptScheduler.kindOf(others))
        // A tag that only starts like a widget's is not one.
        assertEquals(pinned, ScriptScheduler.kindOf(others + "refresh:widgets"))
    }

    /** Only a further widget's event says which widget it is for. The first widget's event is as it always was. */
    @Test fun theEventNamesTheWidget() {
        assertEquals(mapOf("name" to "refresh"), ScriptScheduler.refreshEvent(pinned))
        assertEquals(mapOf("name" to "widget"), ScriptScheduler.refreshEvent(widget))
        assertEquals(mapOf("name" to "widget", "instance" to "2"), ScriptScheduler.refreshEvent(second))
        assertEquals("instance", ScriptScheduler.INSTANCE)
    }

    /**
     * A run for one widget does not stand for a run of another: the second widget's request is not
     * dropped while the first widget's waits. Before, the queue knew one widget kind, and the
     * second widget's run would have been dropped.
     */
    @Test fun aSecondWidgetsRunIsNotDropped() {
        assertEquals(Queued.AFTER, ScriptScheduler.queued(second, listOf(widget)))
        assertEquals(Queued.AFTER, ScriptScheduler.queued(widget, listOf(second)))
        assertEquals(Queued.AFTER, ScriptScheduler.queued(third, listOf(widget, second)))
        assertEquals(Queued.DROPPED, ScriptScheduler.queued(second, listOf(widget, second)))
        // Three widgets and a pinned line that all ask twice: one run of each, one after another.
        val queue = ArrayList<String>()
        for (kind in listOf(widget, second, pinned, third, second, widget, third, pinned)) {
            if (ScriptScheduler.queued(kind, queue) != Queued.DROPPED) queue += kind
        }
        assertEquals(listOf(widget, second, pinned, third), queue)
    }

    /**
     * After an option of a widget changed, its run is asked for "again". The run of that widget
     * that is there may have read the old value, so the new one is not dropped: it goes after it.
     */
    @Test fun aRunAfterAChangeIsNeverDropped() {
        assertEquals(Queued.AFTER, ScriptScheduler.queued(second, listOf(second), again = true))
        assertEquals(Queued.AFTER, ScriptScheduler.queued(widget, listOf(pinned, widget), again = true))
        assertEquals(Queued.AFTER, ScriptScheduler.queued(second, listOf(widget), again = true))
        // Nothing is there: it starts the queue, as any run does.
        assertEquals(Queued.ALONE, ScriptScheduler.queued(second, emptyList(), again = true))
        // A request that is not "again" is dropped as before.
        assertEquals(Queued.DROPPED, ScriptScheduler.queued(second, listOf(second)))
    }
}
