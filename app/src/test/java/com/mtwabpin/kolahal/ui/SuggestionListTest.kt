package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.Keys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The short help beside a completion in the suggestion list. */
class SuggestionListTest {
    @Test fun completionHelpForCommandsAndKeys() {
        assertEquals("Show this list, or just one command", completionHelp("help", "help "))
        assertEquals(Keys.SELECTOR_RAIL.help, completionHelp("selector.rail", "config set selector.rail "))
        assertNull(completionHelp("on", "config set selector.rail on"))
    }
}
