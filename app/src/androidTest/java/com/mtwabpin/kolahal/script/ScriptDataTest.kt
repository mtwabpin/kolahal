package com.mtwabpin.kolahal.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** What scripts get from `json.decode` and the feed parser. */
@RunWith(AndroidJUnit4::class)
class ScriptDataTest {
    private fun refused(text: String) {
        try {
            Json.decode(text)
            fail("decoded: $text")
        } catch (_: org.json.JSONException) {
        }
    }

    @Test fun decodeTakesJson() {
        assertEquals(mapOf("a" to 1), Json.decode(""" { "a": 1 } """))
        assertEquals("text", Json.decode("\"text\""))
        assertEquals(listOf(1, 2), Json.decode("[1, 2]\n"))
        assertEquals(null, Json.decode("null"))
    }

    @Test fun decodeRefusesWhatIsntJson() {
        refused("<!DOCTYPE html><html>…</html>")
        refused("""{"a": 1} trailing""")
        refused("[1] [2]")
    }

    @Test fun aBlankGuidIsNoId() {
        val feed = RssParser.parse(
            """<rss><channel><item><title>One</title><link>https://example.com/1</link><guid/></item>
               <item><title>Two</title><link>https://example.com/2</link><guid> </guid></item></channel></rss>""",
        )
        assertEquals(listOf("https://example.com/1", "https://example.com/2"), feed.items.map { it.id })
    }

    /**
     * A feed's own entities are never expanded (the parser reads no DOCTYPE declarations), so a
     * "billion laughs" feed stays small: it fails at once, or reads as short text. A plain DOCTYPE is fine.
     */
    @Test fun aFeedsEntitiesDontExpand() {
        val levels = (1..9).joinToString("") { i -> "<!ENTITY e$i \"" + "&e${i - 1};".repeat(10) + "\">" }
        // Without an outside DTD, a reference to an entity it doesn't know is an error; with one, it reads as nothing.
        for (doctype in listOf("<!DOCTYPE rss", "<!DOCTYPE rss SYSTEM \"https://example.com/rss.dtd\"")) {
            val bomb = """<?xml version="1.0"?>$doctype [<!ENTITY e0 "aaaaaaaaaa">$levels]>
                <rss><channel><item><title>&e9;</title><link>https://example.com/1</link></item></channel></rss>"""
            val started = System.nanoTime()
            val result = runCatching { RssParser.parse(bomb) }
            // Expanding it would run out of memory, which runCatching would also catch.
            val failure = result.exceptionOrNull()
            assertTrue("$doctype: $failure", failure == null || failure is org.xmlpull.v1.XmlPullParserException)
            assertTrue("$doctype: took too long", System.nanoTime() - started < 1_000_000_000L)
            val title = result.getOrNull()?.items?.firstOrNull()?.title.orEmpty()
            assertTrue("$doctype: ${title.length} characters", title.length < 1_000)
        }
        val old = RssParser.parse(
            """<?xml version="1.0"?><!DOCTYPE rss PUBLIC "-//Netscape Communications//DTD RSS 0.91//EN" "http://my.netscape.com/publish/formats/rss-0.91.dtd">
               <rss version="0.91"><channel><title>Old</title><item><title>One</title><link>https://example.com/1</link></item></channel></rss>""",
        )
        assertEquals(listOf("One"), old.items.map { it.title })
    }

    @Test fun anAtomSourceDoesntRenameTheEntry() {
        val feed = RssParser.parse(
            """<feed xmlns="http://www.w3.org/2005/Atom"><entry><id>tag:entry</id><title>Entry</title>
               <source><id>tag:other-feed</id><title>Other feed</title></source></entry></feed>""",
        )
        val item = feed.items.single()
        assertEquals("Entry", item.title)
        assertEquals("tag:entry", item.id)
        assertTrue(feed.title == null || feed.title != "Other feed")
    }
}
