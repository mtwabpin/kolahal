package com.mtwabpin.kolahal.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the suggestions read off the main thread for a shader ([ReadAtVersion]): kept until the files change. */
class ReadAtVersionTest {
    @Test
    fun keptForItsVersion() {
        val read = ReadAtVersion<List<String>>()
        assertNull(read.get(3, "rain"))
        read.put(3, "rain", listOf("amount"))
        read.put(3, "snow", emptyList())
        assertEquals(listOf("amount"), read.get(3, "rain"))
        // A shader without parameters has none, and isn't read again.
        assertEquals(emptyList<String>(), read.get(3, "snow"))
    }

    /** A file was added, changed or removed: everything is read again. */
    @Test
    fun aNewVersionForgetsTheRest() {
        val read = ReadAtVersion<List<String>>()
        read.put(3, "rain", listOf("amount"))
        assertNull(read.get(4, "rain"))
        read.put(4, "snow", listOf("wind"))
        assertNull(read.get(3, "rain"))
        assertNull(read.get(4, "rain"))
        assertEquals(listOf("wind"), read.get(4, "snow"))
    }

    /** A read that ends after the files changed again is left out. */
    @Test
    fun aLateReadIsLeftOut() {
        val read = ReadAtVersion<List<String>>()
        read.put(5, "snow", listOf("wind"))
        read.put(4, "rain", listOf("amount"))
        assertNull(read.get(4, "rain"))
        assertNull(read.get(5, "rain"))
        assertEquals(listOf("wind"), read.get(5, "snow"))
    }
}
