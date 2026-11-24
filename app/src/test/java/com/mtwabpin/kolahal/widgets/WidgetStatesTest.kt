package com.mtwabpin.kolahal.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** What [WidgetStates] keeps beside a widget's content: its times, its last error, and whether a switch stopped that run. */
class WidgetStatesTest {
    /** A run that a switch stopped is noted with its error, so the card can lead to the Scripts screen. */
    @Test
    fun aSwitchThatStoppedTheRunIsKeptWithItsError() {
        val error = "weather: internet is off for this script\nTap to allow it"
        val text = WidgetStates.encodeMeta(5, 7, error, off = true)
        assertEquals(WidgetStates.Meta(5, 7, error, off = true), WidgetStates.parseMeta(text))
        // Any other failure, and a run that worked, say nothing of a switch.
        assertEquals(
            WidgetStates.Meta(5, 7, "weather: no network"),
            WidgetStates.parseMeta(WidgetStates.encodeMeta(5, 7, "weather: no network"))
        )
        assertFalse(WidgetStates.encodeMeta(5, 7, "weather: no network").contains("off"))
        // Without an error there is nothing for it to say.
        assertEquals(WidgetStates.Meta(5, 7), WidgetStates.parseMeta(WidgetStates.encodeMeta(5, 7, null, off = true)))
        assertEquals(WidgetStates.Meta(5, 7), WidgetStates.parseMeta("""{"set":5,"tried":7,"off":true}"""))
        // What an earlier version kept reads as before.
        assertEquals(
            WidgetStates.Meta(5, 7, "weather: no network"),
            WidgetStates.parseMeta("""{"set":5,"tried":7,"error":"weather: no network"}""")
        )
    }
}
