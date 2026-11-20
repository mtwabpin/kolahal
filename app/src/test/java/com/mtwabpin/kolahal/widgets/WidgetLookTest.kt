package com.mtwabpin.kolahal.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetLookTest {
    /** Each kind is offered the looks that change it; a kind not known yet is offered all of them. */
    @Test fun eachKindIsOfferedTheLooksThatChangeIt() {
        val all = WidgetLook.entries
        assertEquals(all, WidgetLook.offeredFor(null))
        assertEquals(listOf(WidgetLook.PLAIN, WidgetLook.TONAL), WidgetLook.offeredFor(WidgetKind.IMAGE))
        listOf(WidgetKind.BUTTONS, WidgetKind.TOGGLES, WidgetKind.MEDIA).forEach {
            assertEquals(it.id, all - WidgetLook.BOLD, WidgetLook.offeredFor(it))
        }
        listOf(WidgetKind.STAT, WidgetKind.TEXT, WidgetKind.LIST, WidgetKind.PROGRESS, WidgetKind.CHART, WidgetKind.COUNTDOWN, WidgetKind.CLOCK, WidgetKind.AGENDA)
            .forEach { assertEquals(it.id, all, WidgetLook.offeredFor(it)) }
    }

    /** A look the kind doesn't offer draws Plain; the others draw as chosen. */
    @Test fun aLookNotOfferedDrawsPlain() {
        assertEquals(WidgetLook.PLAIN, WidgetLook.BOLD.drawnFor(WidgetKind.BUTTONS))
        assertEquals(WidgetLook.PLAIN, WidgetLook.SHAPE.drawnFor(WidgetKind.IMAGE))
        assertEquals(WidgetLook.TONAL, WidgetLook.TONAL.drawnFor(WidgetKind.IMAGE))
        assertEquals(WidgetLook.SPLIT, WidgetLook.SPLIT.drawnFor(WidgetKind.TOGGLES))
        assertEquals(WidgetLook.BOLD, WidgetLook.BOLD.drawnFor(WidgetKind.STAT))
        assertEquals(WidgetLook.BOLD, WidgetLook.BOLD.drawnFor(null))
    }

    @Test fun builtInWidgetsHaveTheirKind() {
        assertEquals(WidgetKind.PROGRESS, WidgetSource.builtinKind(WidgetSource.BATTERY))
        assertEquals(WidgetKind.BUTTONS, WidgetSource.builtinKind(WidgetSource.SHORTCUTS))
        assertEquals(WidgetKind.MEDIA, WidgetSource.builtinKind(WidgetSource.MEDIA))
        assertEquals(WidgetKind.LIST, WidgetSource.builtinKind(WidgetSource.TODOS))
        assertEquals(WidgetKind.LIST, WidgetSource.builtinKind(WidgetSource.REMINDERS))
        assertEquals(WidgetKind.AGENDA, WidgetSource.builtinKind(WidgetSource.AGENDA))
        assertEquals(WidgetKind.TEXT, WidgetSource.builtinKind(WidgetSource.NOTES))
        assertNull(WidgetSource.builtinKind("weather"))
    }
}
