package com.mtwabpin.kolahal.feeds

import com.mtwabpin.kolahal.data.takeWhole
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

/** Pure URL helpers for feeds (no Android APIs, unit-tested on the JVM). */
object FeedUrl {
    /**
     * Normalises a user-typed feed address: trims, defaults to https, maps `feed://` to https,
     * lowercases scheme and host, drops the fragment and a default port. Returns null when the
     * result isn't an http(s) URL with a host.
     */
    fun normalize(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        when {
            s.startsWith("feed://", ignoreCase = true) -> s = "https://" + s.substring(7)
            s.startsWith("feed:", ignoreCase = true) -> s = s.substring(5) // feed:https://...
        }
        if (!s.contains("://")) s = "https://$s"
        val uri = runCatching { URI(s) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        // java.net.URI reads no host in an international name (bücher.de): OkHttp does, as punycode.
        val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: s.toHttpUrlOrNull()?.host ?: return null
        val port = uri.port.takeUnless { it == -1 || (scheme == "http" && it == 80) || (scheme == "https" && it == 443) }
        return buildString {
            append(scheme).append("://")
            uri.rawUserInfo?.let { append(it).append('@') }
            append(host)
            port?.let { append(':').append(it) }
            append(uri.rawPath.orEmpty().ifEmpty { "/" })
            uri.rawQuery?.let { append('?').append(it) }
        }
    }

    /** Short label for log lines: the feed title, else the host without `www.`; at most [max] chars.
     *  A subtitle after ": " or " - " is dropped ("Hacker News: Front Page" → "Hacker News"). */
    fun shortName(title: String?, url: String, max: Int = 16): String {
        val base = title?.trim()?.split(": ", " - ", " | ", " – ")?.first()?.trim()?.takeIf { it.isNotEmpty() }
            ?: runCatching { URI(url).host }.getOrNull()?.removePrefix("www.")
            ?: url
        return if (base.length > max) base.takeWhole(max - 1).trimEnd() + "…" else base
    }
}
