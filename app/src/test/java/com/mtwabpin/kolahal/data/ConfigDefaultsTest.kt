package com.mtwabpin.kolahal.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Defaults the maintainer chose; a change here changes every phone that never set them. */
class ConfigDefaultsTest {
    @Test fun chosenDefaults() {
        // 30 again in 0.3.0, after 5 from 0.2.3.
        assertEquals(30, Keys.SUGGESTIONS.default)
        assertEquals(false, Keys.SUGGESTIONS_WRAP.default)
        // Swiping up is off until it is given an action: it fires by accident easily.
        assertEquals("none", Keys.GESTURE_SWIPE_UP.default)
        // From 0.4.14: the header's two items beside the battery.
        assertEquals("alarm", Keys.HEADER_ITEM_1.default)
        assertEquals("todo", Keys.HEADER_ITEM_2.default)
        // From 0.4.3: groups closer together, and lines a line of text and a little room, all alike.
        assertEquals(5, Keys.LOG_SPACING.default)
        assertEquals(22, Keys.LOG_LINE_HEIGHT.default)
        assertEquals(false, Keys.LOG_TALLER_TAPS.default)
        // From 0.4.10: one row, of what the keyboard's letters page doesn't have.
        assertEquals("/,|,=,LEFT,UP,DOWN,RIGHT", Keys.KEYBAR.default)
        assertEquals("", Keys.KEYBAR2.default)
    }

    /** kolahal@device until you set your own name: one word, without an @ of its own. */
    @Test fun yourNameIsOneWord() {
        assertEquals("kolahal", Keys.USER_NAME.default)
        assertEquals("alex", Keys.USER_NAME.parse("  alex "))
        assertEquals("रवि_1", Keys.USER_NAME.parse("रवि_1"))
        assertEquals("a".repeat(32), Keys.USER_NAME.parse("a".repeat(32)))
        for (bad in listOf("", "   ", "alex k", "alex@home", "a".repeat(33))) assertEquals(bad, null, Keys.USER_NAME.parse(bad))
        // Other text settings take anything, an empty value too.
        assertEquals("", Keys.KEYBAR2.parse(""))
    }

    @Test fun logKeepsFiveThousandLinesUnlessChanged() {
        assertEquals(5000, Keys.LOG_KEEP_LINES.default)
        assertEquals(500, Keys.LOG_KEEP_LINES.parse("500"))
        assertEquals(50_000, Keys.LOG_KEEP_LINES.parse("50000"))
        assertEquals(null, Keys.LOG_KEEP_LINES.parse("499"))
        assertEquals(null, Keys.LOG_KEEP_LINES.parse("50001"))
        assertEquals(Keys.LOG_KEEP_LINES, Keys.byName("LOG.KEEP-LINES"))
    }
}
