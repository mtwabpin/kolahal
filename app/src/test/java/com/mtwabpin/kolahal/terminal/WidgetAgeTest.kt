package com.mtwabpin.kolahal.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which widgets are old, which ones the page runs again, and what a tap hands to the script. */
class WidgetAgeTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val now = 1_800_000_000_000L

    @Test fun aWidgetThatNothingFilledOrTriedIsDueAtOnce() {
        assertTrue(WidgetAge.due(setAt = 0, triedAt = 0, staleAfter = null, now = now))
    }

    @Test fun contentIsCurrentForAnHourWithoutItsOwnLimit() {
        assertFalse(WidgetAge.due(setAt = now - 30 * minute, triedAt = now - 30 * minute, staleAfter = null, now = now))
        assertTrue(WidgetAge.due(setAt = now - 61 * minute, triedAt = now - 61 * minute, staleAfter = null, now = now))
        assertFalse(WidgetAge.old(setAt = now - 30 * minute, staleAfter = null, now = now))
        assertTrue(WidgetAge.old(setAt = now - hour, staleAfter = null, now = now))
    }

    @Test fun theDescriptionsOwnLimitCounts() {
        assertTrue("a minute", WidgetAge.due(setAt = now - 61_000, triedAt = 0, staleAfter = 60, now = now))
        assertFalse(WidgetAge.due(setAt = now - 59_000, triedAt = 0, staleAfter = 60, now = now))
        assertFalse("two hours", WidgetAge.due(setAt = now - 90 * minute, triedAt = 0, staleAfter = 7200, now = now))
        assertTrue(WidgetAge.old(setAt = now - 61_000, staleAfter = 60, now = now))
    }

    /** Old content, and the run the page asked for failed just now: no second run, or it would never end. */
    @Test fun aRunThatJustFailedIsNotAskedForAgain() {
        assertFalse(WidgetAge.due(setAt = now - 5 * hour, triedAt = now - 2_000, staleAfter = null, now = now))
        assertTrue("the content is still old, so the card says its age", WidgetAge.old(setAt = now - 5 * hour, staleAfter = null, now = now))
        assertTrue("an hour after the failed run, try again", WidgetAge.due(setAt = now - 5 * hour, triedAt = now - hour, staleAfter = null, now = now))
    }

    /** A script that never shows anything: one run when the page shows it, then not until an hour has passed. */
    @Test fun aRunThatShowedNothingIsNotAskedForAgain() {
        assertFalse(WidgetAge.due(setAt = 0, triedAt = now - 10_000, staleAfter = null, now = now))
        assertTrue(WidgetAge.due(setAt = 0, triedAt = now - 2 * hour, staleAfter = null, now = now))
    }

    @Test fun aTimeInTheFutureCountsAsOld() {
        assertTrue("the clock was set back", WidgetAge.due(setAt = now + hour, triedAt = 0, staleAfter = null, now = now))
    }

    @Test fun theLastTimeIsTheLaterOne() {
        assertEquals(7L, WidgetAge.last(setAt = 3, triedAt = 7))
        assertEquals(7L, WidgetAge.last(setAt = 7, triedAt = 0))
        assertEquals(0L, WidgetAge.last(setAt = 0, triedAt = 0))
    }

    @Test fun aTapsArgumentsAreSplitAtSpaces() {
        assertEquals(listOf("add", "1"), WidgetActions.words("add 1"))
        assertEquals(listOf("add", "1"), WidgetActions.words("  add \t 1 "))
        assertEquals(emptyList<String>(), WidgetActions.words(""))
    }

    @Test fun aTapsEventIsALineButtonsEventMarkedAsAWidgets() {
        assertEquals(mapOf("name" to "action", "label" to "Add a glass", "widget" to true), WidgetActions.event("Add a glass"))
        assertEquals("no label, no key", mapOf("name" to "action", "widget" to true), WidgetActions.event(null))
    }
}
