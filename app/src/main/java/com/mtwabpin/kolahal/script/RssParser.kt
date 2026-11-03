package com.mtwabpin.kolahal.script

import android.text.Html
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/** Minimal RSS 2.0 / RSS 1.0 (RDF) / Atom parser. */
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

    /** Items read from one feed; the rest of a huge feed isn't parsed (feeds list newest first). */
    const val MAX_ITEMS = 300

    fun parse(xml: String): Feed {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xml))
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
                    // An Atom entry's <source> describes the feed it came from: not its title or id.
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

    /** Reads all text inside the current element, including nested markup (e.g. Atom XHTML content). */
    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> sb.append(parser.text)
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
