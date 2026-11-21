package com.mtwabpin.kolahal.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The profile flags, one table for every command and for completion. */
class ProfileKindTest {
    @Test
    fun eachFlagPicksItsProfile() {
        for (kind in ProfileKind.entries) {
            assertEquals(kind, ProfileKind.fromFlags(listOf("maps", kind.flag)))
            assertEquals(kind, ProfileKind.fromFlags(listOf(kind.longFlag, "maps")))
        }
        assertNull(ProfileKind.fromFlags(listOf("maps")))
        // With two, work comes first, as before.
        assertEquals(ProfileKind.WORK, ProfileKind.fromFlags(listOf("-p", "-w")))
        // Each profile has a flag of its own: a paused clone profile is not resumed with -w.
        assertEquals(ProfileKind.entries.size, ProfileKind.entries.map { it.flag }.toSet().size)
    }
}
