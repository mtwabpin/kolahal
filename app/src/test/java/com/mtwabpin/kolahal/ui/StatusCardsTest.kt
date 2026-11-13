package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.StatusLine
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

/** The Status card: a script's status line as a stat card ([StatusCards]). */
class StatusCardsTest {
    @Test fun aLineIsAStatWithItsBarAndItsColour() {
        val line = StatusLine("cricket", "NZ 136/8", progress = 0.6f, refresh = 30, colour = StyledText.Colour.GOOD)
        assertEquals(
            WidgetSpec.Stat(WidgetBase(title = "Cricket", color = StyledText.Colour.GOOD), value = "NZ 136/8", progress = 0.6f),
            StatusCards.spec("Cricket", line),
        )
        // Text alone: no bar, and the theme's accent.
        assertEquals(WidgetSpec.Stat(WidgetBase(title = "cricket"), value = "NZ 136/8"), StatusCards.spec("cricket", StatusLine("cricket", "NZ 136/8")))
    }

    @Test fun theValueIsOneLine() {
        assertEquals("NZ 136/8 20 overs", StatusCards.spec("c", StatusLine("c", "  NZ 136/8 \n\n 20 overs\n"))?.value)
    }

    @Test fun aBarStaysBetweenEmptyAndFull() {
        assertEquals(1f, StatusCards.spec("c", StatusLine("c", "x", progress = 7f))?.progress)
        assertEquals(0f, StatusCards.spec("c", StatusLine("c", "x", progress = -1f))?.progress)
        assertNull(StatusCards.spec("c", StatusLine("c", "x", progress = Float.NaN))?.progress)
    }

    @Test fun noLineMeansNoStat() {
        assertNull(StatusCards.spec("cricket", null))
        assertNull(StatusCards.spec("cricket", StatusLine("cricket", "")))
        assertNull(StatusCards.spec("cricket", StatusLine("cricket", " \n ")))
    }

    /** A screen reader hears the title, the line and how far the bar is. */
    @Test fun theCardIsSaidInOneSentence() {
        val spec = StatusCards.spec("Cricket", StatusLine("cricket", "NZ 136/8", progress = 0.6f))!!
        assertEquals("Cricket: NZ 136/8, 60%", WidgetWords.description("Cricket", spec, 0L, ZoneId.of("UTC")))
    }

    @Test fun aRefreshIsDueAtOnceAndThenWhenItIsOld() {
        // It has not run for its refresh yet: now.
        assertEquals(0L, StatusCards.waitMs(30, null, 5_000L))
        // It ran 10 seconds ago, and asks for every 30.
        assertEquals(20_000L, StatusCards.waitMs(30, 100_000L, 110_000L))
        assertEquals(0L, StatusCards.waitMs(30, 100_000L, 130_000L))
        assertEquals(0L, StatusCards.waitMs(30, 100_000L, 900_000L))
        // Just after a run, the whole gap is still to wait. So the card's loop never spins.
        assertEquals(30_000L, StatusCards.waitMs(30, 100_000L, 100_000L))
    }

    @Test fun aRefreshIsNeverFasterThanAStatusLineAllows() {
        assertEquals(10_000L, StatusCards.waitMs(1, 100_000L, 100_000L))
        assertEquals(10_000L, StatusCards.waitMs(0, 100_000L, 100_000L))
        assertEquals(3_600_000L, StatusCards.waitMs(99_999, 100_000L, 100_000L))
        // A last run in the future (the clock started again) waits one gap at most.
        assertEquals(30_000L, StatusCards.waitMs(30, 500_000L, 100_000L))
    }
}
