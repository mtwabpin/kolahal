package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.terminal.WidgetActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which of a script's widgets a run is for ([ScriptRun.widgetOf]). A run for a widget fills that
 * widget, with that widget's options. Every other run fills the script's first widget.
 */
class WidgetRunTest {
    /** The widgets page asks for a run of one widget. The event is the one the queue builds. */
    @Test fun aWidgetsRunIsForThatWidget() {
        assertEquals("", ScriptRun.widgetOf(ScriptScheduler.refreshEvent(ScriptScheduler.widgetKind())))
        assertEquals("2", ScriptRun.widgetOf(ScriptScheduler.refreshEvent(ScriptScheduler.widgetKind("2"))))
        assertEquals("", ScriptRun.widgetOf(mapOf("name" to "widget")))
    }

    /** A tap on a card starts a run for that card's widget. The event is the one the card's tap builds. */
    @Test fun aTapOnACardIsForThatWidget() {
        assertEquals(mapOf("name" to "action", "label" to "Add", "widget" to true, "instance" to "2"), WidgetActions.event("Add", "2"))
        assertEquals(mapOf("name" to "action", "widget" to true), WidgetActions.event(null, ""))
        assertEquals("2", ScriptRun.widgetOf(WidgetActions.event("Add", "2")))
        assertEquals("", ScriptRun.widgetOf(WidgetActions.event("Add")))
    }

    /** A timer's run, a daily time's, an event's, a pinned line's refresh and a log line's button are for no widget. */
    @Test fun everyOtherRunIsForNoWidget() {
        assertNull(ScriptRun.widgetOf(null))
        assertNull(ScriptRun.widgetOf(emptyMap<String, Any?>()))
        assertNull(ScriptRun.widgetOf(ScriptScheduler.refreshEvent(ScriptScheduler.REFRESH)))
        assertNull(ScriptRun.widgetOf(mapOf("name" to "at", "time" to "08:00")))
        assertNull(ScriptRun.widgetOf(mapOf("name" to "action", "label" to "Add")))
        // The field alone names no widget: only a widget's run or a card's tap reads it.
        assertNull(ScriptRun.widgetOf(mapOf("name" to "action", "instance" to "2")))
        assertNull(ScriptRun.widgetOf(mapOf("name" to "charging", "instance" to "2")))
        assertNull(ScriptRun.widgetOf(mapOf("name" to "action", "widget" to "true", "instance" to "2")))
    }

    /** An id that can't be one is not used: the run is then for the first widget. */
    @Test fun onlyAnIdOfTheAllowedFormCounts() {
        for (bad in listOf<Any?>("", "Two", "2#3", "123456789", "a b", 2, 2.0, true, null, listOf("2"))) {
            assertEquals(bad.toString(), "", ScriptRun.widgetOf(mapOf("name" to "widget", "instance" to bad)))
        }
        assertEquals("abc12345", ScriptRun.widgetOf(mapOf("name" to "widget", "instance" to "abc12345")))
    }

    /** A quiet run's error is logged once, by this key. Each further widget has its own. */
    @Test fun eachWidgetHasItsOwnErrorKey() {
        assertEquals("refresh-error:12", ScriptRun.errorKey(12, ""))
        assertEquals("refresh-error:12#2", ScriptRun.errorKey(12, "2"))
    }
}
