package com.mtwabpin.kolahal.feeds

import android.content.Context
import com.mtwabpin.kolahal.script.Net
import com.mtwabpin.kolahal.script.RssParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.Charset
import kotlin.coroutines.resumeWithException

/**
 * Downloads and stores feeds. It uses conditional GETs (ETag / Last-Modified), so an unchanged feed
 * costs only a 304 and no parsing. Every attempt is recorded in `feeds.last_status`.
 */
class FeedFetcher(context: Context) {
    private val repo = FeedRepository.get(context)

    /**
     * Result for one feed.
     * - [error] is null on success (including 304).
     * - [first] is true when this was the feed's first successful fetch. Then [newItems] is its
     *   backlog, not news.
     */
    data class Result(val feed: Feed, val newItems: List<FeedItem>, val error: String?, val first: Boolean = false)

    /**
     * Fetches [feed] and stores the items not seen before. Returns them newest first (empty on 304).
     * A failure is recorded as `error: ...` and thrown again.
     */
    suspend fun fetch(feed: Feed): List<FeedItem> = withContext(Dispatchers.IO) {
        try {
            fetchInternal(feed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            runCatching { repo.recordFetch(feed.id, "error: ${describe(e)}") }
            throw e
        }
    }

    /** Fetches one feed and returns its new items, or why it failed. A cancellation is thrown on. */
    suspend fun fetchOne(feed: Feed): Result =
        try {
            // Don't use `lastFetch == null`. A failed attempt also sets last_fetch, and that must not
            // turn the backlog into "new" items on the next success.
            val first = repo.itemCount(feed.id) == 0
            Result(feed, fetch(feed), null, first)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result(feed, emptyList(), describe(e))
        }

    private suspend fun fetchInternal(feed: Feed): List<FeedItem> {
        val request = Request.Builder()
            .url(feed.url)
            .header("User-Agent", Net.USER_AGENT)
            .header("Accept", "application/rss+xml, application/atom+xml, application/rdf+xml, application/xml;q=0.9, text/xml;q=0.9, */*;q=0.5")
            .apply {
                feed.etag?.let { header("If-None-Match", it) }
                feed.lastModified?.let { header("If-Modified-Since", it) }
            }
            .build()
        val download = download(Net.client.newCall(request))
        if (download == null) {
            repo.recordFetch(feed.id, "not modified")
            return emptyList()
        }
        val (body, etag, lastModified) = download

        val parsed = RssParser.parse(body)
        if (parsed.items.isEmpty() && parsed.title == null) throw IOException("not a feed")
        val fresh = repo.insertItems(feed.id, parsed.items.map {
            FeedRepository.NewItem(
                // guid uses the raw link, not the safe one, so duplicate checks against existing rows still work.
                guid = it.id.ifBlank { it.link ?: it.title },
                title = it.title,
                link = safeLink(it.link, feed.url),
                publishedText = it.date,
                summary = it.summary,
            )
        })
        // If every stored item arrived just now, this is the first successful fetch. (Pruning may
        // have dropped some items, so the check is <=.)
        val first = repo.itemCount(feed.id) <= fresh.size
        val newTitle = parsed.title?.takeIf { feed.title.isNullOrBlank() && it.isNotBlank() }
        val n = parsed.items.size
        val status = if (first) "ok · $n ${if (n == 1) "item" else "items"}" else "ok · ${fresh.size} new"
        repo.recordFetch(feed.id, status, etag, lastModified, newTitle)
        return fresh
    }

    /**
     * Runs [call] asynchronously and reads the body, up to a size limit, on OkHttp's thread.
     * Returns (body, ETag, Last-Modified), or null on 304. Cancelling the coroutine cancels the call,
     * which also stops a body read in progress.
     */
    private suspend fun download(call: Call): Triple<String, String?, String?>? =
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // If the continuation was already cancelled, it ignores this.
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use { resp ->
                            when {
                                resp.code == 304 -> null
                                !resp.isSuccessful -> throw IOException("HTTP ${resp.code}")
                                else -> Triple(readCapped(resp), resp.header("ETag"), resp.header("Last-Modified"))
                            }
                        }
                    }
                    cont.resumeWith(result)
                }
            })
        }

    /** Reads at most [MAX_BODY_BYTES] and fails if the body is larger. Decodes it with the declared charset. */
    private fun readCapped(resp: Response): String {
        val body = resp.body
        if (body.contentLength() > MAX_BODY_BYTES) throw IOException("feed too large")
        val out = ByteArrayOutputStream()
        if (!body.byteStream().use { com.mtwabpin.kolahal.script.Files.copyLimited(it, out, MAX_BODY_BYTES.toLong()) }) throw IOException("feed too large (> 3 MB)")
        val bytes = out.toByteArray()
        val charset = body.contentType()?.charset() ?: bomCharset(bytes) ?: xmlDeclaredCharset(bytes) ?: Charsets.UTF_8
        return String(bytes, charset).removePrefix("\uFEFF")
    }

    companion object {
        const val MAX_BODY_BYTES = 3 * 1024 * 1024

        private val ENCODING = Regex("^<\\?xml[^>]*encoding\\s*=\\s*[\"']([A-Za-z0-9._-]+)[\"']")

        /**
         * Returns the charset named by a byte-order mark at the start. This is checked before the
         * prolog, because a UTF-16 prolog can't be read as bytes, and XML lets the mark decide.
         */
        internal fun bomCharset(bytes: ByteArray): Charset? {
            fun startsWith(vararg b: Int) = bytes.size >= b.size && b.indices.all { bytes[it] == b[it].toByte() }
            return when {
                startsWith(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8
                startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE
                startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE
                else -> null
            }
        }

        /** Reads `encoding="..."` from the XML prolog. Used when the server didn't send a charset. */
        internal fun xmlDeclaredCharset(bytes: ByteArray): Charset? {
            val head = String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
            val name = ENCODING.find(head)?.groupValues?.get(1) ?: return null
            return runCatching { Charset.forName(name) }.getOrNull()
        }

        /**
         * Returns an item link that is safe to pass to ACTION_VIEW. A relative [link] is resolved
         * against [feedUrl]. The result is kept only when it is http(s). Anything else (javascript:,
         * intent:, file:, content:, garbage) becomes null.
         */
        internal fun safeLink(link: String?, feedUrl: String): String? {
            val raw = link?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val resolved = raw.toHttpUrlOrNull()
                ?: if (SCHEME.containsMatchIn(raw)) null else feedUrl.toHttpUrlOrNull()?.resolve(raw)
            return resolved?.takeIf { it.scheme == "http" || it.scheme == "https" }?.toString()
        }

        /** An explicit URI scheme (RFC 3986). Links with a scheme are never resolved relative to the feed. */
        private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

        /** A one-line reason for `last_status` and the log. */
        fun describe(e: Throwable): String =
            e.message?.lines()?.firstOrNull { it.isNotBlank() }?.take(120) ?: e.javaClass.simpleName
    }
}
