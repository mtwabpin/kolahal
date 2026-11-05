package com.mtwabpin.kolahal.script

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The size-capped copy files, feeds and log pictures go through. */
class FilesTest {
    private val data = ByteArray(40_000) { it.toByte() }

    @Test fun whatFitsIsCopiedWhole() {
        val out = ByteArrayOutputStream()
        assertTrue(Files.copyLimited(ByteArrayInputStream(data), out, data.size.toLong()))
        assertArrayEquals(data, out.toByteArray())
        assertTrue(Files.copyLimited(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream(), 0))
    }

    /** One byte over the limit stops it, with no more than the limit written. */
    @Test fun moreThanTheLimitStops() {
        val out = ByteArrayOutputStream()
        assertFalse(Files.copyLimited(ByteArrayInputStream(data), out, data.size - 1L))
        assertTrue("${out.size()}", out.size() <= data.size - 1)
        assertEquals(false, Files.copyLimited(ByteArrayInputStream(byteArrayOf(1)), ByteArrayOutputStream(), 0))
    }
}
