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
 * Downloads and stores feeds. Uses conditional GETs (ETag / Last-Modified) so unchanged feeds cost
 * a 304 and no parsing. Every attempt is recorded in `feeds.last_status`.
 */
class FeedFetcher(context: Context) {
    private val repo = FeedRepository.get(context)

    /**
     * Outcome of one feed; [error] is null on success (including 304). [first]: this was the feed's
     * first successful fetch, so [newItems] is its backlog, not news.
     */
    data class Result(val feed: Feed, val newItems: List<FeedItem>, val error: String?, val first: Boolean = false)

    /**
     * Fetches [feed] and stores unseen items. Returns them newest first (empty on 304).
     * Failures are recorded as `error: ...` and rethrown.
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

    /** One feed: its new items, or why it failed. A cancellation passes through. */
    suspend fun fetchOne(feed: Feed): Result =
        try {
            // Not `lastFetch == null`: a failed attempt sets last_fetch too, and must not turn the
            // backlog into "new" items on the next success.
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
                // guid keeps the raw link so dedupe against existing rows is unchanged.
                guid = it.id.ifBlank { it.link ?: it.title },
                title = it.title,
                link = safeLink(it.link, feed.url),
                publishedText = it.date,
                summary = it.summary,
            )
        })
        // Everything stored arrived just now (pruning may have dropped some): the first successful fetch.
        val first = repo.itemCount(feed.id) <= fresh.size
        val newTitle = parsed.title?.takeIf { feed.title.isNullOrBlank() && it.isNotBlank() }
        val n = parsed.items.size
        val status = if (first) "ok · $n ${if (n == 1) "item" else "items"}" else "ok · ${fresh.size} new"
        repo.recordFetch(feed.id, status, etag, lastModified, newTitle)
        return fresh
    }

    /**
     * Runs [call] asynchronously and reads the (capped) body on OkHttp's thread. Returns
     * (body, ETag, Last-Modified), or null on 304. Cancelling the coroutine cancels the call,
     * which also aborts a body read in progress.
     */
    private suspend fun download(call: Call): Triple<String, String?, String?>? =
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // Ignored by the continuation when it was already cancelled.
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

    /** Reads at most [MAX_BODY_BYTES], failing beyond that, and decodes using the declared charset. */
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
         * The charset a byte-order mark at the start names. Before the prolog: a UTF-16 one can't be
         * read as bytes, and XML lets the mark decide.
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

        /** `encoding="..."` from the XML prolog, when the server didn't send a charset. */
        internal fun xmlDeclaredCharset(bytes: ByteArray): Charset? {
            val head = String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
            val name = ENCODING.find(head)?.groupValues?.get(1) ?: return null
            return runCatching { Charset.forName(name) }.getOrNull()
        }

        /**
         * An item link safe to hand to ACTION_VIEW: [link] resolved against [feedUrl] when relative,
         * kept only when the result is http(s). Anything else (javascript:, intent:, file:, content:,
         * garbage) becomes null.
         */
        internal fun safeLink(link: String?, feedUrl: String): String? {
            val raw = link?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val resolved = raw.toHttpUrlOrNull()
                ?: if (SCHEME.containsMatchIn(raw)) null else feedUrl.toHttpUrlOrNull()?.resolve(raw)
            return resolved?.takeIf { it.scheme == "http" || it.scheme == "https" }?.toString()
        }

        /** An explicit URI scheme (RFC 3986); such links are never resolved relative to the feed. */
        private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

        /** One-line reason for `last_status` and the log. */
        fun describe(e: Throwable): String =
            e.message?.lines()?.firstOrNull { it.isNotBlank() }?.take(120) ?: e.javaClass.simpleName
    }
}
