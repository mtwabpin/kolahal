package com.mtwabpin.kolahal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** What the font picker and `font` call each terminal font. */
class FontNamesTest {
    /** Every font `font.family` offers has a name of its own, not its setting's word. */
    @Test fun everyFontOfferedHasAName() {
        val labels = TermFonts.names.map(TermFonts::label)
        for ((name, label) in TermFonts.names.zip(labels)) assertNotEquals("$name has no name", name, label)
        assertEquals(labels.size, labels.toSet().size)
        assertEquals("System mono", TermFonts.label(TermFonts.SYSTEM))
        assertEquals("Custom", TermFonts.label(TermFonts.CUSTOM))
    }

    /** A font no longer offered (a backup from another version) is called by its word. */
    @Test fun aFontNotOfferedIsCalledByItsWord() {
        assertEquals("comic-mono", TermFonts.label("comic-mono"))
    }
}
