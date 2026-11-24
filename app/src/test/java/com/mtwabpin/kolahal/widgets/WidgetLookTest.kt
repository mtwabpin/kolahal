package com.mtwabpin.kolahal.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLookTest {
    /** Each kind is offered the looks that change it; a kind not known yet is offered all of them but the built-in cards' own and an agenda's own. */
    @Test
    fun eachKindIsOfferedTheLooksThatChangeIt() {
        // Now is an agenda's own look: no other kind has it.
        val all = WidgetLook.entries - WidgetLook.ownOnly - WidgetLook.NOW
        assertEquals(all, WidgetLook.offeredFor(null))
        assertEquals(
            listOf(WidgetLook.PLAIN, WidgetLook.TONAL, WidgetLook.SHAPE),
            WidgetLook.offeredFor(WidgetKind.IMAGE)
        )
        listOf(WidgetKind.BUTTONS, WidgetKind.TOGGLES, WidgetKind.MEDIA).forEach {
            assertEquals(it.id, all - WidgetLook.BOLD - WidgetLook.POSTER, WidgetLook.offeredFor(it))
        }
        assertEquals(all - WidgetLook.SHAPE - WidgetLook.POSTER, WidgetLook.offeredFor(WidgetKind.VERSUS))
        listOf(WidgetKind.STAT, WidgetKind.COUNTDOWN, WidgetKind.CLOCK)
            .forEach { assertEquals(it.id, all, WidgetLook.offeredFor(it)) }
        listOf(
            WidgetKind.TEXT,
            WidgetKind.LIST,
            WidgetKind.PROGRESS,
            WidgetKind.CHART,
            WidgetKind.SKY
        )
            .forEach { assertEquals(it.id, all - WidgetLook.POSTER, WidgetLook.offeredFor(it)) }
        assertEquals(
            all - WidgetLook.POSTER + WidgetLook.NOW,
            WidgetLook.offeredFor(WidgetKind.AGENDA)
        )
        // Six choices at most, so the Look row fits a phone's width.
        for (kind in WidgetKind.entries + null) assertTrue("$kind", WidgetLook.offeredFor(kind).size <= 6)
    }

    /** A look the kind doesn't offer draws Plain; the others draw as chosen. */
    @Test
    fun aLookNotOfferedDrawsPlain() {
        assertEquals(WidgetLook.PLAIN, WidgetLook.BOLD.drawnFor(WidgetKind.BUTTONS))
        assertEquals(WidgetLook.PLAIN, WidgetLook.BOLD.drawnFor(WidgetKind.IMAGE))
        assertEquals(WidgetLook.SHAPE, WidgetLook.SHAPE.drawnFor(WidgetKind.IMAGE))
        assertEquals(WidgetLook.TONAL, WidgetLook.TONAL.drawnFor(WidgetKind.IMAGE))
        assertEquals(WidgetLook.SPLIT, WidgetLook.SPLIT.drawnFor(WidgetKind.TOGGLES))
        assertEquals(WidgetLook.BOLD, WidgetLook.BOLD.drawnFor(WidgetKind.STAT))
        assertEquals(WidgetLook.BOLD, WidgetLook.BOLD.drawnFor(null))
        assertEquals(WidgetLook.NOW, WidgetLook.NOW.drawnFor(WidgetKind.AGENDA))
        assertEquals(WidgetLook.PLAIN, WidgetLook.NOW.drawnFor(WidgetKind.LIST))
        assertEquals(WidgetLook.NOW, WidgetLook.named("now"))
    }

    /**
     * Next is for Todos and Reminders, Stack for Notes, last in their row; on Todos Next takes
     * Shape's place. A script's widget never has them, and draws Plain if one is stored.
     */
    @Test
    fun builtInCardsHaveLooksOfTheirOwn() {
        val list = WidgetLook.offeredFor(WidgetKind.LIST)
        assertEquals(list - WidgetLook.SHAPE + WidgetLook.NEXT, WidgetLook.offeredFor(WidgetKind.LIST, WidgetSource.TODOS))
        assertEquals(list + WidgetLook.NEXT, WidgetLook.offeredFor(WidgetKind.LIST, WidgetSource.REMINDERS))
        assertEquals(
            WidgetLook.offeredFor(WidgetKind.TEXT) + WidgetLook.STACK,
            WidgetLook.offeredFor(WidgetKind.TEXT, WidgetSource.NOTES)
        )
        // The others keep their kind's looks.
        listOf(WidgetSource.BATTERY, WidgetSource.SHORTCUTS, WidgetSource.MEDIA, WidgetSource.AGENDA).forEach {
            assertEquals(it, WidgetLook.offeredFor(WidgetSource.builtinKind(it)), WidgetLook.offeredFor(WidgetSource.builtinKind(it), it))
        }
        WidgetKind.entries.forEach { kind ->
            WidgetLook.ownOnly.forEach { assertTrue("$kind $it", it !in WidgetLook.offeredFor(kind)) }
        }
        assertEquals(WidgetLook.NEXT, WidgetLook.NEXT.drawnFor(WidgetKind.LIST, WidgetSource.TODOS))
        assertEquals(WidgetLook.NEXT, WidgetLook.NEXT.drawnFor(WidgetKind.LIST, WidgetSource.REMINDERS))
        assertEquals(WidgetLook.PLAIN, WidgetLook.NEXT.drawnFor(WidgetKind.LIST))
        assertEquals(WidgetLook.PLAIN, WidgetLook.NEXT.drawnFor(WidgetKind.TEXT, WidgetSource.NOTES))
        assertEquals(WidgetLook.PLAIN, WidgetLook.STACK.drawnFor(WidgetKind.LIST, WidgetSource.TODOS))
        assertEquals(WidgetLook.STACK, WidgetLook.STACK.drawnFor(WidgetKind.TEXT, WidgetSource.NOTES))
        assertEquals(WidgetLook.PLAIN, WidgetLook.SHAPE.drawnFor(WidgetKind.LIST, WidgetSource.TODOS))
        assertEquals(WidgetLook.NEXT, WidgetLook.named("next"))
        assertEquals(WidgetLook.STACK, WidgetLook.named("Stack"))
        // Every built-in card's row still fits a phone's width in one line: six at most.
        listOf(
            WidgetSource.BATTERY, WidgetSource.SHORTCUTS, WidgetSource.MEDIA, WidgetSource.TODOS,
            WidgetSource.AGENDA, WidgetSource.REMINDERS, WidgetSource.NOTES
        ).forEach {
            assertTrue(it, WidgetLook.offeredFor(WidgetSource.builtinKind(it), it).size <= 6)
        }
    }

    @Test
    fun builtInWidgetsHaveTheirKind() {
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
