package com.mtwabpin.kolahal.feeds

import com.mtwabpin.kolahal.data.takeWhole
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

/** Pure URL helpers for feeds. They use no Android APIs, so they are unit-tested on the JVM. */
object FeedUrl {
    /**
     * Normalises a feed address that the user typed:
     * - trims spaces
     * - adds https when there is no scheme, and maps `feed://` to https
     * - lowercases the scheme and host
     * - drops the fragment and a default port
     *
     * Returns null when the result isn't an http(s) URL with a host.
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
        // java.net.URI finds no host in an international name (bücher.de). OkHttp finds it, as punycode.
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

    /**
     * Short label for log lines, at most [max] chars. It is the feed title, or else the host without `www.`.
     * A subtitle after ": " or " - " is dropped ("Hacker News: Front Page" → "Hacker News").
     */
    fun shortName(title: String?, url: String, max: Int = 16): String {
        val base = title?.trim()?.split(": ", " - ", " | ", " – ")?.first()?.trim()?.takeIf { it.isNotEmpty() }
            ?: runCatching { URI(url).host }.getOrNull()?.removePrefix("www.")
            ?: url
        return if (base.length > max) base.takeWhole(max - 1).trimEnd() + "…" else base
    }
}
