package com.mtwabpin.kolahal.feeds

import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The charset a feed's XML prolog declares, read when the server names none; and the one-line reason a failed fetch records. */
class FeedCharsetTest {
    private fun declared(head: String) = FeedFetcher.xmlDeclaredCharset(head.toByteArray(Charsets.ISO_8859_1))

    /** Double or single quotes, spaces round the `=`, any case, after blank lines. */
    @Test fun theEncodingInTheProlog() {
        assertEquals(Charsets.ISO_8859_1, declared("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><rss/>"))
        assertEquals(Charset.forName("windows-1252"), declared("<?xml version='1.0' encoding='windows-1252'?><rss/>"))
        assertEquals(Charset.forName("KOI8-R"), declared("<?xml version=\"1.0\" encoding = \"koi8-r\" ?><rss/>"))
        assertEquals(Charsets.UTF_8, declared("\n  \t<?xml version=\"1.0\" encoding=\"utf-8\"?><rss/>"))
    }

    /** A byte-order mark names the charset: UTF-8, or UTF-16 either way round; text with none, nothing. */
    @Test fun theByteOrderMark() {
        fun bom(vararg b: Int) = FeedFetcher.bomCharset(ByteArray(b.size) { b[it].toByte() } + "<rss/>".toByteArray())
        assertEquals(Charsets.UTF_8, bom(0xEF, 0xBB, 0xBF))
        assertEquals(Charsets.UTF_16BE, bom(0xFE, 0xFF))
        assertEquals(Charsets.UTF_16LE, bom(0xFF, 0xFE))
        assertNull(bom())
        assertNull(FeedFetcher.bomCharset(byteArrayOf(0xEF.toByte(), 0xBB.toByte())))
        assertNull(FeedFetcher.bomCharset(ByteArray(0)))
    }

    /** No prolog, one without an encoding, one not at the start, or a charset this phone doesn't know: none, so UTF-8 is used. */
    @Test fun noEncodingToRead() {
        assertNull(declared("<rss version=\"2.0\"><channel/></rss>"))
        assertNull(declared("<?xml version=\"1.0\"?><rss/>"))
        assertNull(declared("<rss><?xml version=\"1.0\" encoding=\"ISO-8859-1\"?></rss>"))
        assertNull(declared("<?xml version=\"1.0\" encoding=\"x-no-such-charset\"?><rss/>"))
        assertNull(FeedFetcher.xmlDeclaredCharset(ByteArray(0)))
    }

    /** The first line that says something, at most 120 characters; the exception's name when it says nothing. */
    @Test fun theReasonIsOneLine() {
        assertEquals("HTTP 503", FeedFetcher.describe(IOException("HTTP 503")))
        assertEquals("Unexpected token", FeedFetcher.describe(IOException("\n  \nUnexpected token\n(position: TEXT @1:2)")))
        assertEquals("x".repeat(120), FeedFetcher.describe(IOException("x".repeat(300))))
        assertEquals("SocketTimeoutException", FeedFetcher.describe(SocketTimeoutException()))
        assertEquals("IOException", FeedFetcher.describe(IOException("  ")))
    }
}
