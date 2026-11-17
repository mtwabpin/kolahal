package com.mtwabpin.kolahal.documents

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A file read for the editor and written back: unchanged text gives the same bytes, and what isn't text is refused. */
class TextFormatTest {
    private fun text(bytes: ByteArray) = TextFiles.decode(bytes) as TextFiles.Decoded.Text
    private fun text(s: String) = text(s.toByteArray())
    private fun refusal(bytes: ByteArray) = (TextFiles.decode(bytes) as TextFiles.Decoded.Refused).why

    /** Read and written back without a change, the bytes are the same. */
    private fun roundTrips(bytes: ByteArray) {
        val read = text(bytes)
        assertArrayEquals(String(bytes), bytes, read.format.encode(read.format.complete(read.text)))
    }

    @Test fun unchangedTextIsWrittenBackByteForByte() {
        for (s in listOf(
            "", "\n", "one", "one\n", "one\ntwo", "one\ntwo\n", "one\n\n\n",
            "one\r\ntwo\r\n", "one\r\ntwo", "\r\n", "one\rtwo\r", "tab\there\n", "日本語 🙂 é\n",
        )) roundTrips(s.toByteArray())
        // With a byte-order mark, which stays.
        roundTrips(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "one\r\ntwo\r\n".toByteArray())
    }

    /** The editor's text has `\n` only, whatever the file ends its lines with. */
    @Test fun theEditorsTextHasPlainLineBreaks() {
        assertEquals("one\ntwo\n", text("one\r\ntwo\r\n").text)
        assertEquals(LineEnding.CRLF, text("one\r\ntwo\r\n").format.lineEnding)
        assertEquals("one\ntwo", text("one\rtwo").text)
        assertEquals(LineEnding.CR, text("one\rtwo").format.lineEnding)
        assertEquals(LineEnding.LF, text("one\ntwo").format.lineEnding)
        // No line break at all: a new line gets the usual one.
        assertEquals(LineEnding.LF, text("one").format.lineEnding)
    }

    /** New lines typed in the editor get the file's own line ending. */
    @Test fun newLinesGetTheFilesLineEnding() {
        val read = text("one\r\ntwo\r\n")
        assertEquals("one\r\ntwo\r\nthree\r\n", String(read.format.encode(read.text + "three\n")))
    }

    /** A file that ended with a line break still does after a save; one that didn't isn't given one. */
    @Test fun theLastLineBreakIsKept() {
        val with = text("one\n").format
        assertTrue(with.finalNewline)
        assertEquals("one\ntwo\n", with.complete("one\ntwo"))
        assertEquals("one\ntwo\n", with.complete("one\ntwo\n"))
        val without = text("one").format
        assertFalse(without.finalNewline)
        assertEquals("one\ntwo", without.complete("one\ntwo"))
        // What the user typed at the end stays.
        assertEquals("one\ntwo\n", without.complete("one\ntwo\n"))
        // A new, empty file ends its text with one. An emptied text stays empty.
        assertTrue(text("").format.finalNewline)
        assertEquals("hello\n", text("").format.complete("hello"))
        assertEquals("", with.complete(""))
    }

    /** Mixed line endings are all written as the most common one, and the format says so. */
    @Test fun mixedLineEndingsBecomeTheMostCommon() {
        val read = text("one\r\ntwo\r\nthree\nfour\r\n")
        assertTrue(read.format.mixed)
        assertEquals(LineEnding.CRLF, read.format.lineEnding)
        assertEquals("one\ntwo\nthree\nfour\n", read.text)
        assertEquals("one\r\ntwo\r\nthree\r\nfour\r\n", String(read.format.encode(read.text)))
        // As many of each: the plain one.
        assertEquals(LineEnding.LF, text("one\r\ntwo\n").format.lineEnding)
        assertFalse(text("one\ntwo\n").format.mixed)
    }

    /** Text pasted with `\r\n` in it doesn't end up with a doubled `\r`. */
    @Test fun pastedCarriageReturnsAreLineBreaks() {
        assertEquals("a\r\nb\r\nc\r\n", String(TextFormat(LineEnding.CRLF).encode("a\r\nb\rc\n")))
        assertEquals("a\nb\nc\n", String(TextFormat(LineEnding.LF).encode("a\r\nb\rc\n")))
    }

    @Test fun binaryFilesAreRefused() {
        assertEquals("It is not a text file: it holds binary data.", refusal(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00)))
        // Bytes that are not UTF-8: Latin-1 "café", and a cut-off sequence.
        val notUtf8 = "It is not UTF-8 text. It may be a binary file, or text in an older encoding."
        assertEquals(notUtf8, refusal(byteArrayOf('c'.code.toByte(), 'a'.code.toByte(), 'f'.code.toByte(), 0xE9.toByte())))
        assertEquals(notUtf8, refusal("日本".toByteArray().copyOf(5)))
        assertEquals("It is UTF-16 text. The editor reads and writes UTF-8 only.", refusal("hi".toByteArray(Charsets.UTF_16)))
        assertEquals("It is UTF-16 text. The editor reads and writes UTF-8 only.", refusal(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 'h'.code.toByte(), 0)))
    }

    @Test fun filesTooBigAreRefused() {
        val tooLong = "It is too long to edit here: the editor takes up to 500,000 characters."
        text("x".repeat(TextFiles.MAX_CHARS))
        assertEquals(tooLong, refusal("x".repeat(TextFiles.MAX_CHARS + 1).toByteArray()))
        // Characters count, not bytes: half a million three-byte letters are still taken.
        text("日".repeat(TextFiles.MAX_CHARS))
        assertEquals(tooLong, refusal(ByteArray(TextFiles.MAX_BYTES + 1) { 'x'.code.toByte() }))
        val tooManyLines = "It has too many lines to edit here: the editor takes up to 50,000 lines."
        text("\n".repeat(TextFiles.MAX_LINES - 1))
        assertEquals(tooManyLines, refusal("\n".repeat(TextFiles.MAX_LINES).toByteArray()))
        assertEquals(tooManyLines, refusal("\r\n".repeat(TextFiles.MAX_LINES).toByteArray()))
    }
}
