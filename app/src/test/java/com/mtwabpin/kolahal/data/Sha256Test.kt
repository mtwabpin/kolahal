package com.mtwabpin.kolahal.data

import com.mtwabpin.kolahal.withDefaultLocale
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest
import java.util.Locale

/** A checksum as text: what a shared script, a library file's history and an APK's signer are compared by. */
class Sha256Test {
    private val abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test fun knownChecksums() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(ByteArray(0)))
        assertEquals(abc, sha256Hex("abc".toByteArray()))
    }

    /** Lowercase, two digits a byte, so a leading zero is kept: 64 digits always. */
    @Test fun everyByteIsTwoDigits() {
        for (i in 0..300) {
            val hex = sha256Hex("line $i".toByteArray())
            assertEquals(hex, true, Regex("[0-9a-f]{64}").matches(hex))
        }
    }

    /** The library's history keeps the first 8 bytes of a file's checksum: the first 16 digits of this. */
    @Test fun theFirstBytesAreTheFirstDigits() {
        val bytes = "-- @title Battery\nreturn 1".toByteArray()
        val first8 = MessageDigest.getInstance("SHA-256").digest(bytes).take(8).joinToString("") { "%02x".format(it) }
        assertEquals(first8, sha256Hex(bytes).take(16))
    }

    /** The digits are the same whatever the phone's language: a checksum is compared with one from a file. */
    @Test fun theDigitsDoNotFollowTheLanguage() {
        for (tag in listOf("ar-EG", "fa-IR", "hi-IN-u-nu-deva", "tr-TR")) {
            withDefaultLocale(Locale.forLanguageTag(tag)) { assertEquals(tag, abc, sha256Hex("abc".toByteArray())) }
        }
    }
}
