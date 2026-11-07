package com.mtwabpin.kolahal.feeds

/**
 * OPML import and export.
 * - OPML is simple, so a small scanner over the attributes of `<outline>` tags is enough. This avoids
 *   Android's XML parser, so the code can be tested on the JVM.
 * - Nested outlines (folders) are flattened. Only outlines with an `xmlUrl` become feeds.
 */
object Opml {
    data class Entry(val title: String?, val url: String)

    // Match quoted values as a whole, because a `>` inside quotes doesn't need escaping.
    // The quantifiers are possessive, so the regex can't backtrack over a long value.
    private val OUTLINE = Regex("<outline\\b((?:[^>\"']++|\"[^\"]*+\"|'[^']*+')*+)>", RegexOption.IGNORE_CASE)
    private val ATTR = Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')")
    private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)

    /** Returns the feeds listed in [xml], in document order, without duplicate URLs. */
    fun parse(xml: String): List<Entry> {
        val seen = HashSet<String>()
        val out = ArrayList<Entry>()
        for (m in OUTLINE.findAll(xml.replace(COMMENT, ""))) {
            val attrs = attributes(m.groupValues[1])
            val url = attrs["xmlurl"]?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            if (!seen.add(url)) continue
            val title = (attrs["title"] ?: attrs["text"])?.trim()?.takeIf { it.isNotEmpty() }
            out += Entry(title, url)
        }
        return out
    }

    /** Builds an OPML 2.0 document for [feeds]. */
    fun write(feeds: List<Entry>, title: String = "Kolahal feeds"): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<opml version=\"2.0\">\n")
        append("  <head><title>").append(escape(title)).append("</title></head>\n")
        append("  <body>\n")
        for ((feedTitle, url) in feeds) {
            val name = escape(feedTitle ?: url)
            append("    <outline type=\"rss\" text=\"").append(name)
                .append("\" title=\"").append(name)
                .append("\" xmlUrl=\"").append(escape(url)).append("\"/>\n")
        }
        append("  </body>\n")
        append("</opml>\n")
    }

    /** Attribute names are lowercased, because OPML files use both `xmlUrl` and `xmlurl`. */
    private fun attributes(raw: String): Map<String, String> =
        ATTR.findAll(raw).associate { m ->
            val value = m.groups[3]?.value ?: m.groups[4]?.value.orEmpty()
            m.groupValues[1].lowercase() to unescape(value)
        }

    fun escape(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }

    private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);")

    fun unescape(s: String): String = if ('&' !in s) s else ENTITY.replace(s) { m ->
        when (val e = m.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            else -> {
                val code = if (e.startsWith("#x")) e.substring(2).toIntOrNull(16) else e.substring(1).toIntOrNull()
                // A lone surrogate is only half a character, so keep the entity as written.
                code?.takeIf { Character.isValidCodePoint(it) && it !in 0xD800..0xDFFF }?.let { String(Character.toChars(it)) } ?: m.value
            }
        }
    }
}

/** Subscribes to every feed in an OPML document and returns how many were new. Invalid URLs are skipped. */
fun FeedRepository.importOpml(xml: String): Int {
    val before = feeds().size
    for ((title, url) in Opml.parse(xml)) runCatching { add(url, title) }
    return feeds().size - before
}

/** Returns all subscriptions as an OPML document. */
fun FeedRepository.exportOpml(): String = Opml.write(feeds().map { Opml.Entry(it.title, it.url) })
