package com.mtwabpin.kolahal.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** The letters a person's picture shows when there's no photo. */
class AvatarsTest {
    @Test fun initialsFromTheFirstAndLastWords() {
        assertEquals("RK", initials("Ravi Kumar"))
        assertEquals("RK", initials("Ravi Prasad Kumar"))
        assertEquals("A", initials("asha"))
        assertEquals("AB", initials("  Asha   Bhat "))
    }

    @Test fun numbersAndNothing() {
        assertEquals("#", initials("+91 98765 43210"))
        assertEquals("#", initials("1234"))
        assertEquals("?", initials("?"))
        assertEquals("?", initials("   "))
        // Words that don't start with a letter are skipped; with none, the first character shows.
        assertEquals("M", initials("(Mom) Mom"))
        assertEquals("★", initials("★"))
    }
}
