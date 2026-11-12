package com.mtwabpin.kolahal.script

import android.text.Html
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/** A minimal parser for RSS 2.0, RSS 1.0 (RDF) and Atom. */
object RssParser {
    private val SPACES = Regex("\\s+")

    data class Item(
        val title: String,
        val link: String?,
        val id: String,
        val date: String?,
        val summary: String?,
    ) {
        fun toMap(): Map<String, Any?> = mapOf(
            "title" to title, "link" to link, "id" to id, "date" to date, "summary" to summary,
        )
    }

    data class Feed(val title: String?, val link: String?, val items: List<Item>) {
        fun toMap(): Map<String, Any?> = mapOf(
            "title" to title, "link" to link, "items" to items.map { it.toMap() },
        )
    }

    private const val SUMMARY_MAX = 280

    /** The most items read from one feed. The rest of a huge feed is not parsed, because feeds list the newest items first. */
    const val MAX_ITEMS = 300

    /** The most characters kept from one element's text. Feeds are at most 3 MB, but a script can pass more. */
    private const val MAX_TEXT = 256 * 1024

    fun parse(xml: String): Feed {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            // Android's parser expands a feed's own <!ENTITY> declarations, with no limit: a few nested
            // ones grow a small feed to gigabytes ("billion laughs"). Without DOCTYPE processing, they
            // aren't stored, so a reference to one fails or reads as nothing.
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            // A feed's text must not be able to style a log line.
            setInput(StringReader(com.mtwabpin.kolahal.data.StyledText.strip(xml)))
        }
        var feedTitle: String? = null
        var feedLink: String? = null
        val items = ArrayList<Item>()

        var inItem = false
        var title: String? = null
        var link: String? = null
        var id: String? = null
        var date: String? = null
        var summary: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val name = parser.name.lowercase()
                when {
                    name == "item" || name == "entry" -> {
                        inItem = true
                        title = null; link = null; id = null; date = null; summary = null
                    }
                    // An Atom entry's <source> describes the feed it came from. It is not the entry's title or id.
                    inItem && name == "source" -> readText(parser)
                    name == "title" -> readText(parser).let { if (inItem) title = it else if (feedTitle == null) feedTitle = it }
                    name == "link" -> {
                        val href = parser.getAttributeValue(null, "href")
                        val rel = parser.getAttributeValue(null, "rel")
                        val value = href ?: readText(parser)
                        val usable = rel == null || rel == "alternate"
                        if (usable && value.isNotBlank()) {
                            if (inItem) { if (link == null) link = value.trim() }
                            else if (feedLink == null) feedLink = value.trim()
                        }
                    }
                    inItem && (name == "guid" || name == "id") -> id = readText(parser).trim()
                    inItem && (name == "pubdate" || name == "published" || name == "updated" || name == "dc:date") ->
                        if (date == null) date = readText(parser).trim()
                    inItem && (name == "description" || name == "summary" || name == "content:encoded" || name == "content") ->
                        if (summary == null) summary = cleanSummary(readText(parser))
                }
            } else if (event == XmlPullParser.END_TAG) {
                val name = parser.name.lowercase()
                if (inItem && (name == "item" || name == "entry")) {
                    inItem = false
                    val t = title?.let(::stripHtml)?.trim().orEmpty()
                    items += Item(
                        title = t.ifEmpty { link ?: "(untitled)" },
                        link = link,
                        id = id?.takeIf { it.isNotBlank() } ?: link ?: t,
                        date = date,
                        summary = summary,
                    )
                    if (items.size >= MAX_ITEMS) break
                }
            }
            event = parser.next()
        }
        return Feed(feedTitle?.trim(), feedLink, items)
    }

    /** Reads all text inside the current element, including nested markup (e.g. Atom XHTML content), up to [MAX_TEXT] characters. */
    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (sb.length < MAX_TEXT) {
                    val text = parser.text
                    sb.append(text, 0, minOf(text.length, MAX_TEXT - sb.length))
                }
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return sb.toString()
    }

    private fun stripHtml(s: String): String =
        Html.fromHtml(s, Html.FROM_HTML_MODE_COMPACT).toString()

    private fun cleanSummary(s: String): String? {
        val text = stripHtml(s).replace(SPACES, " ").trim()
        if (text.isEmpty()) return null
        return if (text.length > SUMMARY_MAX) text.take(SUMMARY_MAX - 1) + "…" else text
    }
}
