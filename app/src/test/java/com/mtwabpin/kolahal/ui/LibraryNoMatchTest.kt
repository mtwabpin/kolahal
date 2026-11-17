package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.script.LibraryTag
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the library page says when its search and its chips keep no script ([libraryNoMatch]). */
class LibraryNoMatchTest {
    @Test fun theSearchAloneIsQuoted() {
        assertEquals("No script matches “zzqx”", libraryNoMatch("zzqx", emptyList()))
        // As typed, without the spaces around it.
        assertEquals("No script matches “Hacker  News”", libraryNoMatch("  Hacker  News ", emptyList()))
    }

    /** The chips that are on are named, joined with "or": each chip adds its scripts. */
    @Test fun theChipsThatAreOnAreNamed() {
        assertEquals("No Template script matches “clock”", libraryNoMatch("clock", listOf(LibraryTag.TEMPLATE)))
        assertEquals("No Template or Event script matches “clock”", libraryNoMatch("clock", listOf(LibraryTag.TEMPLATE, LibraryTag.EVENT)))
        assertEquals(
            "No Widget, Command or Offline script matches “clock”",
            libraryNoMatch("clock", listOf(LibraryTag.WIDGET, LibraryTag.COMMAND, LibraryTag.OFFLINE)),
        )
    }

    /** With no search, only the chips can keep nothing: a chip for a tag that no script has. */
    @Test fun withoutASearchItNamesTheLibrary() {
        assertEquals("No Event script in the library", libraryNoMatch("", listOf(LibraryTag.EVENT)))
        assertEquals("No script in the library", libraryNoMatch(" ", emptyList()))
    }
}
