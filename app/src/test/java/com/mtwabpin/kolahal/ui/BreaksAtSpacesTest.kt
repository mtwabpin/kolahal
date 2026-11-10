package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.StyledText
import org.junit.Assert.assertEquals
import org.junit.Test

/** `help`'s rows wrap only at a space or after "|": the invisible marks that [breaksAtSpaces] adds. */
class BreaksAtSpacesTest {
    @Test fun aBarMayBreakAHyphenOrDashMayNot() {
        assertEquals("<5m|​1h30m>", breaksAtSpaces("<5m|1h30m>"))
        assertEquals("|​-⁠-⁠settings]", breaksAtSpaces("|--settings]"))
        assertEquals("0–⁠100 · up", breaksAtSpaces("0–100 · up"))
        assertEquals("screen-⁠timeout", breaksAtSpaces("screen-timeout"))
    }

    @Test fun nothingIsAddedBeforeASpaceOrAtTheEnd() {
        assertEquals("a | b -", breaksAtSpaces("a | b -"))
        assertEquals("on · off", breaksAtSpaces("on · off"))
    }

    @Test fun styledTextStaysAsItIs() {
        val muted = StyledText.colour(StyledText.Colour.MUTED, "a|b")
        assertEquals(muted, breaksAtSpaces(muted))
    }
}
