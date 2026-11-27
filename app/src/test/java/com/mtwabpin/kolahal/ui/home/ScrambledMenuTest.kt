package com.mtwabpin.kolahal.ui.home

import com.mtwabpin.kolahal.terminal.ActionPlace
import com.mtwabpin.kolahal.terminal.LogAction
import com.mtwabpin.kolahal.terminal.Scramble
import com.mtwabpin.kolahal.ui.ActionMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** A line's sheet while home is scrambled: the user's words go, the sheet's own words stay. */
class ScrambledMenuTest {
    @Test
    fun ownHeadingsAndWordsAreScrambledTheSheetsOwnStay() {
        val script = ActionPlace.Group("script", "weather", own = true)
        val todo = ActionPlace.Group("todo", "Todo")
        val shortcuts = ActionPlace.Group("shortcuts", "Shortcuts")
        val menu = ActionMenu(
            "Lisbon 18°",
            listOf(
                LogAction("Run weather now", short = "Run now", place = script) {},
                LogAction("Edit weather.lua", short = "Edit", place = script) {},
                LogAction("Mark done", short = "Done", place = todo) {},
                LogAction("Maps › Alex", short = "Alex", place = shortcuts) {},
                LogAction("Star line", short = "Star", place = ActionPlace.STAR) {},
            ),
            1L,
        )
        val shown = scrambledMenu(menu, Scramble(7L)).actions
        val headings = shown.map { (it.place as? ActionPlace.Group)?.heading }
        // One scrambled heading for the script's group, the same on both of its actions.
        assertNotEquals("weather", headings[0])
        assertEquals(headings[0], headings[1])
        assertEquals(listOf("Todo", "Shortcuts", null), headings.drop(2))
        assertEquals(listOf("Run now", "Edit", "Done"), shown.take(3).map { it.short })
        assertNotEquals("Alex", shown[3].short)
        assertEquals("Star line", shown[4].label)
        assertNotEquals("Run weather now", shown[0].label)
    }
}
