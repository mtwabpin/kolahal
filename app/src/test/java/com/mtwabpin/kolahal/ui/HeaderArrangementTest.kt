package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.Keys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Arrange the header: its parts under three headings, moved within a side or past a heading to another. */
class HeaderArrangementTest {
    private val default = HeaderArrangement.rows(listOf("time", "date"), listOf("alarm", "todo", "battery"))

    private fun keys(rows: List<ArrangeRow>) = rows.map {
        when (it) {
            is ArrangeRow.Heading -> it.side.name
            is ArrangeRow.Part -> it.kind
        }
    }

    /** Left, then right, then every part neither shows, in the order the parts are listed. */
    @Test fun theRowsAreTheSidesThenTheRest() {
        assertEquals(
            listOf("LEFT", "time", "date", "RIGHT", "alarm", "todo", "battery", "HIDDEN", "network", "unlocks", "profiles", "settings"),
            keys(default),
        )
        assertEquals(Keys.HEADER_PARTS.size + 3, default.size)
        // A part on both sides (only `config` can do that) stays on the left, as the header shows it.
        assertEquals(listOf("LEFT", "time", "RIGHT", "battery", "HIDDEN"), keys(HeaderArrangement.rows(listOf("time"), listOf("time", "battery"))).take(5))
        assertEquals(listOf("time", "date") to listOf("alarm", "todo", "battery"), HeaderArrangement.sides(default))
    }

    /** Within a side, a move reorders it. */
    @Test fun aMoveWithinASideReorders() {
        val moved = HeaderArrangement.move(default, 6, 4) // battery before the alarm
        assertEquals(listOf("time", "date") to listOf("battery", "alarm", "todo"), HeaderArrangement.sides(moved))
    }

    /** Past a heading, a part goes to that side; into Not shown, it goes from the header. */
    @Test fun pastAHeadingItChangesSides() {
        // The date down past Right side: the right side's first part.
        var rows = HeaderArrangement.move(default, 2, 3)
        assertEquals(listOf("time") to listOf("date", "alarm", "todo", "battery"), HeaderArrangement.sides(rows))
        assertEquals(HeaderSide.RIGHT, HeaderArrangement.sideOf(rows, 3))
        // The todos down past Not shown: gone from the header.
        rows = HeaderArrangement.move(default, 5, 7)
        assertEquals(listOf("time", "date") to listOf("alarm", "battery"), HeaderArrangement.sides(rows))
        assertEquals(HeaderSide.HIDDEN, HeaderArrangement.sideOf(rows, 7))
        // The network up into the right side's end.
        rows = HeaderArrangement.move(default, 8, 7)
        assertEquals(listOf("time", "date") to listOf("alarm", "todo", "battery", "network"), HeaderArrangement.sides(rows))
    }

    /** Nothing goes above Left side, and the headings themselves don't move. */
    @Test fun theHeadingsStayPut() {
        assertEquals(listOf("time", "date"), HeaderArrangement.sides(HeaderArrangement.move(default, 1, 0)).first)
        assertEquals("LEFT", keys(HeaderArrangement.move(default, 1, 0)).first())
        assertSame(default, HeaderArrangement.move(default, 3, 5))
        assertSame(default, HeaderArrangement.move(default, 0, 2))
    }

    /** A screen reader's moves: to the end of another side, or out of the header. */
    @Test fun aPartMovesToTheEndOfASide() {
        var rows = HeaderArrangement.moveTo(default, 1, HeaderSide.RIGHT) // the time
        assertEquals(listOf("date") to listOf("alarm", "todo", "battery", "time"), HeaderArrangement.sides(rows))
        rows = HeaderArrangement.moveTo(default, 8, HeaderSide.LEFT) // the network
        assertEquals(listOf("time", "date", "network") to listOf("alarm", "todo", "battery"), HeaderArrangement.sides(rows))
        rows = HeaderArrangement.moveTo(default, 6, HeaderSide.HIDDEN) // the battery
        assertEquals(listOf("time", "date") to listOf("alarm", "todo"), HeaderArrangement.sides(rows))
        assertEquals("battery", keys(rows).last())
    }

    /** Every part a side can hold has its place here, and an empty side is fine. */
    @Test fun anEmptySideIsFine() {
        val rows = HeaderArrangement.rows(emptyList(), emptyList())
        assertEquals(listOf("LEFT", "RIGHT", "HIDDEN") + Keys.HEADER_PARTS, keys(rows))
        assertEquals(emptyList<String>() to emptyList<String>(), HeaderArrangement.sides(rows))
    }
}
