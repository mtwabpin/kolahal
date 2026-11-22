/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import android.content.Context
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.StatusLines
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.notify.Notifier
import com.mtwabpin.kolahal.widgets.PlacedIds
import com.mtwabpin.kolahal.widgets.WidgetJson
import com.mtwabpin.kolahal.widgets.WidgetParser
import com.mtwabpin.kolahal.widgets.WidgetRef
import com.mtwabpin.kolahal.widgets.WidgetStates
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

/**
 * The launcher-side API exposed to scripts. It doesn't depend on the engine: the Lua and JS bindings
 * both turn their arguments into these calls, so scripts behave the same in either language.
 *
 *   log(title, body, ctx)       -> add a line to the terminal: a bold title, a muted body (ctx sets tap / long-press actions)
 *   logs(titles)                -> one title line per item
 *   notify(title, body, ctx)    -> post a notification (if enabled for the script)
 *   http.get(url, headers)      -> { status, ok, body, headers }
 *   http.request(opts)          -> same, opts = { url, method, body, headers, contentType }
 *   rss.fetch(url) / rss.parse(xml) -> { title, link, items = [{ title, link, id, date, summary }] }
 *   rss.fresh(url)              -> like fetch, but only items that no earlier call returned
 *   store.get(key) / store.set(key, value) -> persistent strings, per script
 *   device.dnd() / device.setDnd(mode), device.work() / device.setWork(on) -> Do Not Disturb, the work profile
 *   shader.play(name, seconds?) -> a one-off shader over home
 *   shader.give(name, value) -> a value that shaders' parameters can follow
 *   widget.show(desc) / widget.clear() -> this script's widget on the widgets page
 *   json.encode(v) / json.decode(s)
 */
class HostApi(
    internal val context: Context,
    val scriptName: String,
    private val scriptId: Long?,
    private val notifyEnabled: Boolean,
    /** The user started the run from the prompt (not a timer). Only then may the script drive the launcher with launcher.run. */
    private val interactive: Boolean = false,
    /** Another script's launcher.run started this run. It may not send commands itself, so scripts can't start each other without end. */
    private val byScript: Boolean = false,
    /** What the script declares and the user allows ([ScriptAccess]). Null for code typed at the prompt, which may use anything. */
    private val grants: ScriptAccess.Grants? = null,
    /** The script's `@secret` names and the values you set for them ([SecretStore]). Null for code typed at the prompt. */
    secrets: Secrets? = null,
    /** The line of the typed command that started this run. The run's lines are that command's output. */
    from: Long? = null,
    /** Its `@option`s with your values or their defaults ([ScriptOptions]). This is the `options` table. */
    val options: Map<String, String> = emptyMap(),
    /** Its `@widget` line ([ScriptMeta.widget]). Null when it has none: then `widget.show` is an error. */
    private val widget: ScriptMeta.Widget? = null,
    /**
     * Which of the script's widgets this run fills ([WidgetRef.id]): "" for its first widget, which
     * every run fills that is not for a further one. [options] are that widget's.
     */
    private val widgetId: String = "",
    /** The names on its `@gives` line ([ScriptMeta.gives]): what `shader.give` takes. */
    private val gives: List<String> = emptyList(),
) {
    class Secrets(val declared: List<ScriptMeta.Secret>, val values: Map<String, String>)

    /** When the run must end ([startClock]). Host calls that wait stop by then. */
    @Volatile
    private var deadline = Long.MAX_VALUE
    private var timeoutS = 0L

    /** The engine calls this when the run starts. */
    fun startClock(timeoutMs: Long) {
        timeoutS = timeoutMs / 1000
        deadline = System.currentTimeMillis() + timeoutMs
    }

    /** Stops the run as its timeout would. Lua's guards don't let a script catch this message. */
    private fun timedOut(): Nothing {
        stopForTimeout()
        throw IllegalStateException("script timed out after ${timeoutS}s")
    }

    /**
     * What stopped this run, recorded where it happened: its time was up, or a switch was off. Null
     * while nothing has. The worker reports the run's end from this, not from the error's text.
     */
    @Volatile
    var stoppedBy: RunStop? = null
        private set

    /** Records that the run's time was up. Lua's hook calls this as it stops the run, and so do host calls that wait. */
    fun stopForTimeout() {
        stoppedBy = RunStop.Timeout
    }

    /** The time left in the run, for host calls that wait (sleep, a request). If no time is left, the run stops. */
    private fun remainingMs(): Long =
        (deadline - System.currentTimeMillis()).takeIf { it > 0 } ?: timedOut()

    /** `sleep(ms)`: up to 5 seconds, and never past the end of the run. */
    fun sleep(ms: Long) {
        val wait = ms.coerceIn(0, 5_000)
        val left = remainingMs()
        Thread.sleep(minOf(wait, left))
        if (wait >= left) timedOut()
    }

    /** JavaScript's `await sleep(ms)`. The same, but it doesn't hold a thread. */
    suspend fun sleepAsync(ms: Long) {
        val wait = ms.coerceIn(0, 5_000)
        val left = remainingMs()
        kotlinx.coroutines.delay(minOf(wait, left).milliseconds)
        if (wait >= left) timedOut()
    }

    /**
     * Set when the run has ended (by the worker, whatever the outcome). After that, a request or a
     * timer still in flight changes nothing: no store write, status, notification or line.
     */
    @Volatile
    var over = false
        private set

    fun finish() {
        over = true
    }

    internal fun live() {
        if (over) throw IllegalStateException("$scriptName has finished running")
    }

    /** This run's secrets: their marks, where a mark becomes its value, and the masking ([RunSecrets]). */
    private val runSecrets = RunSecrets(secrets)

    /** `secrets.get(name)`: a mark that stands for the value of a declared `@secret` name, never the value itself ([RunSecrets.mark]). */
    fun secretGet(name: String): String? = runSecrets.mark(name)

    /** [text] with this run's marks replaced by their values, for a request to [url]'s host, where that is allowed ([RunSecrets.fill]). */
    internal fun fillSecrets(text: String, url: okhttp3.HttpUrl): String =
        runSecrets.fill(text, url)

    /** [text] with this script's secrets masked, both values and marks. So nothing a run shows ever shows a key. */
    fun redact(text: String): String = runSecrets.redact(text)

    /** [e] as the script may see it: with this script's secrets masked in its message ([RunSecrets.masked]). */
    fun masked(e: Throwable): Throwable = runSecrets.masked(e)

    /**
     * Private data is only for scripts that declare it. If the script didn't declare it, that is a
     * mistake in the script, and this throws. If the user switched it off, this returns false and the
     * caller answers with an empty result.
     */
    internal fun may(what: String, call: String): Boolean {
        val g = grants ?: return true
        if (what !in g.declared) throw IllegalStateException("$call needs `@uses $what` at the top of the script")
        if (!g.allows(what) && what in g.undecided) undecided += what
        return g.allows(what)
    }

    /** `allowed(what)` for scripts: whether the user lets this script use [what]. */
    fun allowed(what: String): Boolean = grants?.allows(what.trim().lowercase()) ?: true

    /** Items that are declared, not yet decided, and asked for in this run. The worker offers to allow them. */
    val undecided = LinkedHashSet<String>()

    /** Stops the run because [item] is switched off. The worker says so, with a tap to allow it. */
    private fun deny(item: String, message: String): Nothing {
        stoppedBy = RunStop.Denied(item, message)
        throw java.io.IOException(message)
    }

    private val logs = LogRepository.get(context)
    private val store = KvStore(context, scriptId?.let(ScriptRepository::scopeOf) ?: "repl")

    /** Visible side effects (log lines, status, notifications). A REPL run with none prints "nil". */
    var effects = 0
        private set

    /** A notify() call was dropped because notifications are blocked. The worker reports this once. */
    var notifyBlocked = false
        private set

    private val baseCtx: Map<String, String> =
        (if (scriptId != null) mapOf(LogContext.SCRIPT to scriptName) else emptyMap()) +
                (from?.let { mapOf(LogContext.FROM to it.toString()) } ?: emptyMap())

    /**
     * The run's lines on their way to the log ([RunLog]): batched, held to the run's limits
     * ([MAX_LINES_PER_RUN], [MAX_CHARS_PER_RUN]), and open to `line.set` ([setLine]).
     */
    private val runLog = RunLog(
        scriptName,
        baseCtx,
        over = { over },
        write = logs::addAll,
        change = { logs.updateAll(it) })

    /** Writes buffered lines, and the changes to lines already written. The worker calls this when the run ends (success or failure). */
    fun flush() = runLog.flush()

    /**
     * `log(title, body?, ctx?)`: a bold title line, with [body] (any number of lines) muted under it.
     * Returns the line's number for `line.set` ([setLine]), or 0 when it isn't logged (past the run's limits).
     */
    fun log(raw: String, body: String? = null, ctx: Map<*, *>? = null): Int {
        effects++
        val (text, lineCtx) = lineOf(raw, body, ctx)
        return runLog.add(LogKind.SCRIPT, text, lineCtx)
    }

    /** A script line's text and context, as [log] stores them: links checked ([safeLinks]), secrets masked, only the context a script may set. */
    private fun lineOf(raw: String, body: String?, ctx: Map<*, *>?): Pair<String, LogContext> {
        val text = redact(safeLinks(raw))
        val under = body?.let { redact(safeLinks(it)) }?.takeIf { it.isNotEmpty() }
        val title = mapOf(LogContext.TITLE to "1") + (under?.let { mapOf(LogContext.BODY to it) }
            ?: emptyMap())
        val values = baseCtx + title + safeCtx(
            ctx,
            listOfNotNull(text, under).joinToString("\n")
        ).entries.associate { (k, v) -> redact(k) to redact(v) }
        // Kept only as long as the log keeps them, cut after secrets are masked. The log cuts them the same way
        // (one character over is enough for its "…"). The line is kept for line.set, and a run could otherwise
        // hold 500 lines of megabytes each.
        return text.take(LogRepository.MAX_FIELD + 1) to LogContext(values.mapValues { (_, v) ->
            v.take(
                LogRepository.MAX_FIELD / 4 + 1
            )
        })
    }

    /**
     * `line.set(title, body?, ctx?)` on line [number] of this run (what `log` returned): the line
     * changes in place, where it is in the log. [markdown]: it was logged with `md.log`, so the new
     * text is Markdown too.
     * - It works only during the run. After the run, the line stays as it was last set.
     * - A run can change its lines [MAX_SETS_PER_RUN] times. Later changes are ignored, as are
     *   changes after the run's limits were reached.
     * - Changes are written together with new lines, at most about every 300 ms, and before a request.
     */
    fun setLine(
        number: Int,
        markdown: Boolean,
        raw: String,
        body: String? = null,
        ctx: Map<*, *>? = null
    ) {
        live()
        // The line is found first, then its new text is checked (links, secrets), then it is changed ([RunLog.set]).
        runLog.set(number) {
            if (markdown) lineOf(
                Markdown.inline(redact(raw)),
                body?.let { Markdown.block(redact(it)) },
                ctx
            ) else lineOf(raw, body, ctx)
        }
    }

    /**
     * [text] with each link (`fmt.link`, `[text](url)` in `md.log`) checked as a line's `url` is
     * ([safeCtx]): only web links, none with internet switched off for the script, and none that
     * carries a secret anywhere but the sites its `@secret` line names. A link that fails keeps its
     * text, as plain text.
     */
    private fun safeLinks(text: String): String = StyledText.mapLinks(text) { safeWebUrl(it) }

    /**
     * [link], if a link from this script may leave the phone: a tap opens it in another app, and home
     * fetches a picture as soon as its line shows. So:
     * - only web links (http and https), not upi:, tel:, intent: …;
     * - none with internet switched off for this script;
     * - like a request, none that carries a secret to a site other than those its `@secret` line
     *   names, or over plain http. That stops the run ([RunSecrets.check]).
     * Null when the link may not be used. Used for links in text, a line's `url` and `image`, and a notification's link.
     */
    private fun safeWebUrl(link: String): String? {
        val web =
            Net.webUrl(link)?.takeIf { grants == null || grants.allows(ScriptAccess.INTERNET) }
                ?: return null
        web.toHttpUrlOrNull()?.let { runSecrets.check(it.host, listOf(web), it.isHttps) }
        return web
    }

    /** `logs(titles)`: one title line for each, with no bodies. */
    fun logs(titles: List<String>) = titles.forEach { log(it) }

    /**
     * `md.log(title, body?, ctx?)`: [log], with Markdown read in the title (styles) and the body
     * (styles and list items). Secrets are hidden first, so Markdown can't split one apart.
     */
    fun logMarkdown(title: String, body: String? = null, ctx: Map<*, *>? = null): Int =
        log(Markdown.inline(redact(title)), body?.let { Markdown.block(redact(it)) }, ctx)

    /**
     * `ctx.actions`, as stored ([LogContext.ACTIONS]): up to [MAX_ACTIONS] buttons, each
     * `{ label, args }`. A tap runs this script again with the args (and `event.name` "action"),
     * so they are kept only for a saved script's line; code typed at the prompt has no script to run.
     * Anything but a list of such pairs is an error that says what to pass.
     */
    private fun actionsJson(raw: Any?): String? {
        if (raw == null || scriptId == null) return null
        val bad = "ctx.actions expects a list of buttons, each { label, args }"
        val list = Json.listOrNull(raw) ?: throw IllegalArgumentException(bad)
        if (list.size > MAX_ACTIONS) throw IllegalArgumentException("ctx.actions: at most $MAX_ACTIONS buttons")
        fun text(v: Any?) = when (v) {
            null -> ""
            is Double -> Json.numberText(v)
            else -> v.toString()
        }.replace('\n', ' ').trim()

        val buttons = list.map { b ->
            val pair = b as? List<*> ?: throw IllegalArgumentException(bad)
            val label = StyledText.strip(text(pair.getOrNull(0))).takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("ctx.actions: each button needs a label")
            listOf(label.take(MAX_ACTION_LABEL), text(pair.getOrNull(1)).take(MAX_ACTION_ARGS))
        }
        return if (buttons.isEmpty()) null else Json.stringify(buttons)
    }

    /**
     * The context a script may attach to its lines. Launcher-internal keys (tap-to-run commands,
     * script identity, app grids, error lines) are dropped, so a script can't plant a one-tap `clear all`.
     */
    private fun safeCtx(ctx: Map<*, *>?, text: String): Map<String, String> {
        // Keep only what a script's line may carry, so a key added to the launcher later stays out of reach.
        val m = Json.toStringMap(ctx).filterKeys { it in SCRIPT_CTX }.toMutableMap()
        // An item of a list (ctx.gap): true or 1 asks for the gap above the line; anything else is dropped.
        m[LogContext.GAP]?.let {
            if (it == "true" || it == "1") m[LogContext.GAP] = "1" else m.remove(LogContext.GAP)
        }
        // Buttons: only on a saved script's line, since a tap runs that script again.
        actionsJson(ctx?.get("actions"))?.let { m[LogContext.ACTIONS] = it }
        // Home opens the link on a tap, and fetches the picture as soon as the line shows.
        for (key in listOf(LogContext.URL, LogContext.IMAGE)) {
            val link = m[key] ?: continue
            safeWebUrl(link)?.let { m[key] = it } ?: m.remove(key)
        }
        m[LogContext.COPY]?.let {
            if (!honestCopy(
                    it,
                    text,
                    m[LogContext.URL]
                )
            ) m.remove(LogContext.COPY)
        }
        return m
    }

    /**
     * "Copy text" must copy what the line shows: all or part of it, the whole text of a line cut
     * short with "…", or its link. Otherwise a line could show one address and copy another.
     */
    private fun honestCopy(copy: String, text: String, url: String?): Boolean {
        // The link as given, or as it was normalized (with a trailing / added). webUrl is null for
        // anything that isn't a link, so on a line with no link it must not count as a match.
        if (url != null && (copy == url || Net.webUrl(copy) == url)) return true
        // Compare the texts as they read, ignoring styles and list marks.
        fun bare(s: String) = StyledText.plain(s).filterNot { it.isWhitespace() || it == ',' }
        val c = bare(copy)
        if (c.isEmpty()) return false
        if (bare(text).contains(c)) return true
        return text.trimEnd().endsWith("…") && c.startsWith(bare(text.trimEnd().removeSuffix("…")))
    }

    /** `console.error`, and calls the launcher refuses. They count with the run's other lines. */
    fun logError(text: String) {
        runLog.add(LogKind.ERROR, redact(text), LogContext(baseCtx))
    }

    /** The watchdog's last line before it stops the process. It is written at once, whatever the run logged. */
    fun logStopped(text: String) {
        flush()
        logs.add(LogKind.ERROR, redact(text), scriptName, LogContext(baseCtx))
    }

    /** The notifications this run has posted (or tried to post). */
    private var notifications = 0

    fun notify(title: String, body: String, ctx: Map<*, *>? = null): Boolean {
        live()
        effects++
        flush() // earlier log lines appear before the notification
        val values = Json.toStringMap(ctx)
        // With notifications off for this script, the script's own log lines already show the content.
        if (!notifyEnabled) return false
        // Android keeps about 50 notifications per app. A loop must not crowd out Kolahal's own reminders.
        if (++notifications > MAX_NOTIFY_PER_RUN) {
            if (notifications == MAX_NOTIFY_PER_RUN + 1) logError("notify: more than $MAX_NOTIFY_PER_RUN notifications in one run; the rest aren't posted")
            return false
        }
        // Opening the link sends what it holds to its site.
        val url = values[LogContext.URL]?.let(::safeWebUrl)
        val posted = Notifier.post(
            context,
            scriptName,
            StyledText.plain(redact(title)),
            StyledText.plain(redact(body)),
            url
        )
        if (!posted) notifyBlocked = true
        return posted
    }

    fun httpRequest(
        url: String,
        method: String = "GET",
        body: String? = null,
        /** [body] is a table or object that the binding wrote as JSON. It is sent as JSON unless told otherwise. */
        bodyIsJson: Boolean = false,
        contentType: String? = null,
        headers: Map<*, *>? = null,
    ): Map<String, Any?> {
        live()
        flush() // show what was logged before a slow request
        // Don't return a made-up empty response: a script would take it for real data.
        if (grants != null && !grants.allows(ScriptAccess.INTERNET)) {
            deny(ScriptAccess.INTERNET, "internet is off for this script")
        }
        // A script names the site, not the server it lives on. A Host header of its own could send a request
        // (and a secret meant for the site) to another site behind the same address.
        if (Json.toStringMap(headers).keys.any { it.equals("Host", ignoreCase = true) }) {
            throw IllegalArgumentException("http: a request can't set its own Host header")
        }
        val target = url.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("not a web address: ${redact(url)}")
        // From here on, the request holds the values, so anything it throws is masked.
        try {
            return sendFilled(url, target, method, body, bodyIsJson, contentType, headers)
        } catch (e: Exception) {
            throw masked(e)
        }
    }

    /** [httpRequest] after its address is read: the secrets go in, and the request goes out. */
    private fun sendFilled(
        url: String,
        target: okhttp3.HttpUrl,
        method: String,
        body: String?,
        bodyIsJson: Boolean,
        contentType: String?,
        headers: Map<*, *>?,
    ): Map<String, Any?> {
        // The secrets' values go in now, where the script put their marks, and only for their sites.
        val realUrl = fillSecrets(url, target)
        val realBody = body?.let { fillSecrets(it, target) }
        val h = Json.toStringMap(headers).mapValues { fillSecrets(it.value, target) }.toMutableMap()
        if (h.keys.none { it.equals("User-Agent", ignoreCase = true) }) h["User-Agent"] =
            Net.USER_AGENT
        val m = method.uppercase()
        // The body's type is, in order: `contentType`; else a Content-Type header (OkHttp replaces that
        // header with the body's type, so it must become the body's type); else JSON for a table; else text.
        // With no body, nothing replaces the header, so it is sent as it is.
        val typeHeader = h.keys.firstOrNull { it.equals("Content-Type", ignoreCase = true) }
            ?.takeIf { realBody != null }
        val type = contentType ?: typeHeader?.let { h.remove(it) }
        ?: if (bodyIsJson) "application/json; charset=utf-8" else "text/plain; charset=utf-8"
        val reqBody = realBody?.toRequestBody(type.toMediaTypeOrNull())
            ?: if (m in METHODS_REQUIRING_BODY) ByteArray(0).toRequestBody() else null
        val request = Request.Builder()
            .url(realUrl)
            .headers(h.toHeaders())
            .method(m, if (m != "GET" && m != "HEAD") reqBody else null)
            // Redirects too: every hop is checked, with what it carries, before it is sent.
            .tag(Net.HopCheck::class.java, Net.HopCheck { hop ->
                runSecrets.check(
                    hop.url.host,
                    listOf(hop.url.toString()) + hop.headers.map { it.second } + if (hop.body != null) listOf(
                        realBody.orEmpty()
                    ) else emptyList(),
                    hop.url.isHttps)
            })
            .build()
        runSecrets.check(
            request.url.host,
            listOf(request.url.toString(), realBody.orEmpty()) + h.values,
            request.url.isHttps
        )
        val call = client.newCall(request)
        // No longer than the time the run has left (the client's own limit is 30 s).
        call.timeout().timeout(minOf(remainingMs(), Net.CALL_TIMEOUT_MS), TimeUnit.MILLISECONDS)
        return call.execute().use { resp ->
            mapOf(
                "status" to resp.code,
                "ok" to resp.isSuccessful,
                "body" to resp.peekBody(MAX_BODY).string(),
                "headers" to resp.headers.associate { (k, v) -> k.lowercase() to v },
            )
        }
    }

    fun httpGet(url: String, headers: Map<*, *>? = null) = httpRequest(url, headers = headers)

    /** `http.request(opts)`: `{ url, method, body, headers, contentType }`. A body that isn't text is sent as JSON. */
    fun httpRequest(opts: Map<*, *>): Map<String, Any?> = httpRequest(
        url = opts["url"]?.toString() ?: throw IllegalArgumentException("url is required"),
        method = opts["method"]?.toString() ?: "GET",
        body = opts["body"]?.let { it as? String ?: Json.stringify(it) },
        bodyIsJson = opts["body"].let { it != null && it !is String },
        contentType = opts["contentType"]?.toString(),
        headers = opts["headers"] as? Map<*, *>,
    )

    fun rssParse(xml: String): Map<String, Any?> = RssParser.parse(xml).toMap()

    fun rssFetch(url: String): Map<String, Any?> = fetchFeed(url).toMap()

    /** Seen-id updates from `rss.fresh`. [commit] applies them only if the script finishes successfully. */
    private val pendingSeen = LinkedHashMap<String, List<String>>()

    fun rssFresh(url: String): Map<String, Any?> {
        val feed = fetchFeed(url)
        val key = runSecrets.urlKey("rss.seen:", url)
        // JavaScript runs host calls side by side, so two rss.fresh calls that finish together must not lose ids.
        val fresh = synchronized(pendingSeen) {
            // Ids are stored, and a store goes into backups. So a key in an item's link is masked, the same way in each run.
            val seen = (pendingSeen[key] ?: readSeen(key)).map(::redact)
            val seenSet = seen.toHashSet()
            feed.items.filter { redact(it.id) !in seenSet }.also { fresh ->
                // Never forget an id that the feed still lists. Otherwise a feed longer than MAX_SEEN would
                // push its own old items out and report them as new on the next run.
                pendingSeen[key] = (fresh.map { redact(it.id) } + seen).take(
                    maxOf(
                        MAX_SEEN,
                        feed.items.size + 100
                    )
                )
            }
        }
        return feed.copy(items = fresh).toMap()
    }

    private fun readSeen(key: String): List<String> {
        val raw = store.get(key) ?: return emptyList()
        return runCatching { (Json.parse(raw) as? List<*>)?.map { it.toString() } }.getOrNull()
            ?: raw.split('\n') // format used before ids were stored as JSON
    }

    /** Saves deferred state after a successful run, so a failing script sees its items again next time. */
    fun commit() = synchronized(pendingSeen) {
        pendingSeen.forEach { (key, ids) ->
            val value = Json.stringify(ids)
            // If the store is full of cached responses, remove them to make room, as a cache write does.
            runCatching { store.set(key, value) }.recoverCatching {
                store.deleteStartingWith("http.cache:")
                store.set(key, value)
            }.getOrThrow()
        }
        pendingSeen.clear()
    }

    private fun fetchFeed(url: String): RssParser.Feed {
        val resp = httpGet(url)
        if (resp["ok"] != true) throw IllegalStateException("rss: HTTP ${resp["status"]} for $url")
        return RssParser.parse(resp["body"] as String)
    }

    /**
     * This script's status line. It shows in its card and in `status`, and at the top of home after you
     * pin the script. Null or empty clears it. [opts]:
     * - `progress` (0 to 1) draws a bar under it at the top of home.
     * - `refresh` (seconds, 10 at the fastest) runs the script again that often while the line shows.
     */
    fun status(text: String?, opts: Map<*, *>? = null) {
        live()
        effects++
        flush()
        if (scriptId == null) {
            log("status() only works in saved scripts (it is keyed by script name)")
            return
        }
        val unknown = opts?.keys?.map { it.toString() }?.filter { it !in STATUS_OPTIONS }.orEmpty()
        require(unknown.isEmpty()) { "status: no option ${unknown.joinToString(", ")} (there are ${STATUS_OPTIONS.joinToString()})" }
        val progress = opts?.get("progress")?.let {
            val p = (it as? Number)?.toDouble()?.takeIf(Double::isFinite)
                ?: throw IllegalArgumentException("status: progress is a number from 0 to 1")
            p.coerceIn(0.0, 1.0).toFloat()
        }
        val refresh = opts?.get("refresh")?.let {
            val s = (it as? Number)?.toDouble()?.takeIf(Double::isFinite)
                ?: throw IllegalArgumentException("status: refresh is a number of seconds")
            if (s <= 0) null else s.coerceIn(
                StatusLines.MIN_REFRESH.toDouble(),
                StatusLines.MAX_REFRESH.toDouble()
            ).toInt()
        }
        // The bar's color, by role, as fmt.color takes it: the theme picks the color itself.
        val color = opts?.get("color")?.let {
            StyledText.ColorRole.named(it.toString())
                ?: throw IllegalArgumentException("status: color is one of ${StyledText.ColorRole.entries.joinToString { c -> c.role }}")
        }
        // A status line is plain text. Styles only show in the log.
        val value =
            text?.let(::redact)?.let(StyledText::plain)?.lines()?.firstOrNull()?.take(STATUS_MAX)
                ?.takeIf { it.isNotBlank() }
        val extras = value?.let { StatusLines.encodeExtras(progress, refresh, color) }
        // Each write tells home to redraw. So unchanged writes are skipped, and a loop stops after a few.
        if (StatusLines.isSet(
                context,
                scriptName,
                value,
                extras
            ) || ++statusWrites > MAX_STATUS_PER_RUN
        ) return
        StatusLines.set(context, scriptName, value, extras)
    }

    /** Status line writes in this run. */
    private var statusWrites = 0

    /**
     * `shader.give(name, value)`: a value that shaders' parameters can follow ([ShaderValues]). [name] is one
     * of its `@gives` line. The same value again changes nothing, and a run can change [MAX_GIVES_PER_RUN].
     */
    fun shaderGive(name: String, value: Any?) {
        live()
        effects++
        if (scriptId == null) {
            log("shader.give() only works in saved scripts (it is keyed by script name)")
            return
        }
        check(name in gives) { "shader.give: add `@gives $name` at the top of the script" }
        val text = ShaderValues.clean(value)
        // Home shows it in a shader's details, so it must never hold a secret.
        require(text == null || redact(text) == text) { "shader.give: the value contains one of this script's secrets" }
        if (gaveValues >= MAX_GIVES_PER_RUN) return
        if (ShaderValues.give(context, scriptName, name, text)) gaveValues++
    }

    /** The values this run changed. */
    private var gaveValues = 0

    /**
     * `shader.play(name, seconds?)`: a one-off shader over home, for [seconds] or its own time ([ShaderPlay]).
     * Home plays one at a time; a run can ask once.
     */
    fun shaderPlay(name: String, seconds: Any?) {
        live()
        effects++
        val time = when (seconds) {
            null -> null
            is Number -> seconds.toFloat().takeIf { it.isFinite() && it > 0f }
                ?: throw IllegalArgumentException("shader.play: seconds is a number above 0")

            else -> throw IllegalArgumentException("shader.play: seconds is a number above 0")
        }
        val shader = name.trim().lowercase()
        require(ShaderCatalog.valid(shader)) { "shader.play: '$name' can't be a shader's name" }
        if (!may("effects", "shader.play") || played) return
        played = true
        ShaderPlay.request(context, shader, time)
    }

    /** This run asked for a one-off shader. */
    private var played = false

    // ---- its widget ----------------------------------------------------------------------

    /**
     * `widget.show(desc)`: checks [desc] ([WidgetParser]) and stores it as the widget this run fills
     * ([widgetKey]): the script's first widget, or the further one the run is for.
     * - Every text has its secrets masked. The parser takes the styles out first, so a style can't hide a secret from the mask.
     * - Every link is checked ([widgetLink]). A link that is refused is an error.
     * - It is stored only for a widget you placed. Returns true when it is placed. A widget that
     *   isn't placed still has its description checked, so a mistake shows either way.
     * - A run may call it [MAX_WIDGET_PER_RUN] times. Later calls change nothing.
     *
     * This runs in the `:script` process, which can't see the widgets page. So "placed" comes only
     * from the database's copies of the placed list ([widgetPlacedNow]): the main process writes
     * them when you place or remove a widget.
     */
    fun widgetShow(desc: Map<*, *>?): Boolean {
        live()
        effects++
        flush()
        val line = widgetLine("widget.show") ?: return false
        val placed = widgetPlacedNow()
        if (!widgetCall("widget.show")) return placed
        val raw = desc
            ?: throw IllegalArgumentException("widget.show expects a description: a table or object such as { value, label }")
        val spec = WidgetParser.parse(raw, line.kind, text = ::redact, link = ::widgetLink)
        // The texts have limits of their own. A link has none, so this is what stops a huge one.
        val json = WidgetJson.encode(spec)
        require(json.toByteArray().size <= MAX_WIDGET_BYTES) { "widget: the description is over ${MAX_WIDGET_BYTES / 1024} KB" }
        if (placed) {
            WidgetStates.putEncoded(context, widgetKey, json)
            widgetChanged = true
        }
        return placed
    }

    /**
     * The key of the widget this run fills ([WidgetRef.key]): the script's name for its first
     * widget, `name#2` for a further one. `widget.show` and `widget.clear` store under it, and the
     * run's end is noted under it.
     */
    internal val widgetKey: String = WidgetRef(scriptName, widgetId).key

    /**
     * Whether this run changed what the script's widget keeps, with `widget.show` or `widget.clear`.
     * The calls store at once and tell nobody. The cards are told once, when the run ends
     * ([ScriptRun]): a script may call `widget.show` many times, and a widget's run notes its end too.
     */
    @Volatile
    var widgetChanged = false
        private set

    /**
     * Whether the widget this run fills is on the widgets page now. Two things must say so:
     * - the script's `widget` access is on: one of its widgets is placed;
     * - the row of its name has this widget's id ([PlacedIds.holds]). A name with no row was placed
     *   before rows were kept, and has its first widget only.
     *
     * Both are read from the database at each call, not from what the run started with: a widget
     * removed while the run sleeps or waits for the web must not get content back, and one placed
     * meanwhile gets it. Only the access is read, not the code: the run's code has its `@widget`
     * line ([widget]), which the access needs too.
     */
    internal fun widgetPlacedNow(): Boolean {
        if (widget == null) return false
        val access = scriptId?.let { ScriptRepository.get(context).accessOf(it) } ?: return false
        return access[ScriptAccess.WIDGET] == true && PlacedIds.holds(
            WidgetStates.placedIds(
                context,
                scriptName
            ), widgetId
        )
    }

    /** `widget.clear()`: removes the content of the widget this run fills. Its card then says that there is nothing to show. */
    fun widgetClear() {
        live()
        effects++
        flush()
        widgetLine("widget.clear") ?: return
        if (widgetPlacedNow() && widgetCall("widget.clear")) {
            WidgetStates.clear(context, widgetKey)
            widgetChanged = true
        }
    }

    /**
     * The script's `@widget` line, for [call].
     * - Code typed at the prompt has no script to key a widget by: it gets a line that says so, and null.
     * - A saved script without the line gets an error that says to add it, as a call without its `@uses` line does.
     */
    private fun widgetLine(call: String): ScriptMeta.Widget? {
        if (scriptId == null) {
            log("$call() only works in saved scripts (it is keyed by script name)")
            return null
        }
        return widget
            ?: throw IllegalStateException("$call needs `@widget <kind>` at the top of the script")
    }

    /** Counts a widget call. False past the run's limit; the first call past it says so. */
    private fun widgetCall(call: String): Boolean {
        if (++widgetCalls <= MAX_WIDGET_PER_RUN) return true
        if (widgetCalls == MAX_WIDGET_PER_RUN + 1) logError("$call: more than $MAX_WIDGET_PER_RUN widget calls in one run; the rest change nothing")
        return false
    }

    /** `widget.show` and `widget.clear` calls in this run. */
    private var widgetCalls = 0

    /**
     * A widget's link as it may be stored: checked as a line's `url` is ([safeWebUrl]), then with
     * secrets masked. A link that fails stops the run with an error that says why, as a refused
     * request does. A card with a dead tap would be worse.
     */
    private fun widgetLink(link: String): String {
        if (Net.webUrl(link) == null) throw IllegalArgumentException(
            "widget: a link must be a web address (http or https), not '${
                redact(
                    link
                ).take(80)
            }'"
        )
        if (grants != null && !grants.allows(ScriptAccess.INTERNET)) deny(
            ScriptAccess.INTERNET,
            "internet is off for this script"
        )
        // This throws for a link that carries a secret to a site its @secret line doesn't name.
        val web =
            safeWebUrl(link) ?: throw IllegalArgumentException("widget: this link can't be used")
        return redact(web)
    }

    // ---- its own screen ------------------------------------------------------------------

    /** The screen the run opened with `tty.screen` ([screenOpen]). It runs once the code has run ([runScreen]). */
    @Volatile
    private var screen: ScreenSession? = null

    /** Where screens open, for tests; null means home ([ScriptScreens.home]). */
    internal var screenHome: ScreenSession.Home? = null

    /** Whether the run opened a screen of its own. */
    internal val hasScreen: Boolean get() = screen != null

    /**
     * How the run leaves its worker when its screen shows ([ScreenRuns]), or null for a run that stays
     * where it is. A run in a worker that can't leave it ([screens] is false) opens no screen: Android
     * stops a worker after about ten minutes.
     */
    internal var handover: ScreenHandover? = null

    /** Whether a screen can open from where this run is. */
    internal var screens = true

    /**
     * `tty.screen(spec)`: the script opens a full-screen screen of its own over home. The engine passes
     * the fields of the spec ([ScreenSession.Spec.of]) but keeps the functions, and [runScreen] then
     * calls them.
     * - It is for a run you started, as a screen that takes home unasked would be in the way. So not for
     *   a timer, an event or a refresh, and not for a run another script started with launcher.run.
     * - A run opens one screen. It shows once the script's code has run to its end.
     */
    fun screenOpen(spec: Map<*, *>?) {
        live()
        effects++
        val wanted = ScreenSession.Spec.of(spec, scriptName)
        if (!may(ScreenSession.USES, "tty.screen")) {
            // Not decided yet: the run goes on without its screen, and the worker asks to allow it.
            if (ScreenSession.USES in undecided) return
            deny(ScreenSession.USES, "tty.screen: full screen is off for this script")
        }
        check(interactive) { "tty.screen only opens when you start the script, not on a timer or an event" }
        check(!byScript) { "tty.screen only opens when you start the script, not when another script starts it" }
        check(screens) { "tty.screen opens only in a script with `@uses screen`, or in typed code that says tty.screen" }
        check(screen == null) { "tty.screen: a run opens one screen" }
        screen = ScreenSession(
            scriptName,
            ScreenSession.Spec(wanted.fps, redact(wanted.title), wanted.keys, wanted.taps),
            screenHome ?: ScriptScreens.home(context),
            android.os.SystemClock::uptimeMillis,
            ::redact,
            from = baseCtx[LogContext.FROM]?.toLongOrNull(),
        )
    }

    /** A drawing call of the screen's grid (`g.put`…), for the engines ([ScreenDrawing]). */
    fun screenDraw(op: String, args: List<Any?>) {
        val open = screen ?: throw IllegalStateException("g.$op works only inside the functions of tty.screen")
        open.draw(op, args)
    }

    /** Runs the screen the run opened, until it closes. [script] calls the script's functions. */
    internal suspend fun runScreen(script: ScreenSession.Script) {
        val open = screen ?: return
        // Lines the code logged show before the screen does.
        flush()
        handover?.codeDone?.complete(Unit)
        // A line a function logs is written as the function ends, not with the next lines: the screen may
        // stay open for hours. A run logs at most 500 lines, so this is never many writes.
        val prompt = object : ScreenSession.Script {
            override suspend fun call(callback: String, cols: Int, rows: Int, args: List<Any?>, limitMs: Long): Boolean =
                try {
                    script.call(callback, cols, rows, args, limitMs)
                } finally {
                    runLog.flushWaiting()
                }

            override fun waiting(ms: Long) = script.waiting(ms)
        }
        when (open.run(prompt) { handover?.shown?.complete(Unit) }) {
            ScreenSession.End.CLOSED -> Unit
            ScreenSession.End.ABSENT ->
                runLog.add(LogKind.INFO, "$scriptName: its screen didn't open, because home wasn't shown", LogContext(baseCtx))
        }
    }

    // ---- device, apps, launcher ----------------------------------------------------------

    fun clipboardSet(text: String) {
        live()
        if (!may("clipboard", "clipboard.set")) return
        // A clip goes to Android in one transaction. A huge clip would make it fail, and the process with it.
        require(text.length <= MAX_CLIP) { "clipboard.set: text over ${MAX_CLIP / 1000}K characters" }
        // Keyboards keep clipboard history, and Android shows a preview, so never copy a secret.
        require(redact(text) == text) { "clipboard.set: the text contains one of this script's secrets" }
        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching {
                cm.setPrimaryClip(android.content.ClipData.newPlainText(scriptName, text))
            }
        }
    }

    /** Android only lets the focused app read the clipboard, so this is usually null in timers. */
    fun clipboardGet(): String? {
        if (!may("clipboard", "clipboard.get")) return null
        return runCatching {
            context.getSystemService(android.content.ClipboardManager::class.java)
                .primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        }.getOrNull()
    }

    fun battery(): Map<String, Any?> {
        val intent = context.registerReceiver(
            null,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        )
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        return mapOf(
            "level" to if (level >= 0 && scale > 0) level * 100 / scale else null,
            "charging" to ((intent?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0)
                ?: 0) != 0),
        )
    }

    /** The active network's kind, as the header names it ([com.mtwabpin.kolahal.status.NetworkKind]). */
    fun network(): String = com.mtwabpin.kolahal.status.NetworkKind.of(
        com.mtwabpin.kolahal.status.NetworkKind.active(context)
    )

    fun unlocksToday(): Int =
        com.mtwabpin.kolahal.unlock.UnlockCounter.get(context).countFor(java.time.LocalDate.now())

    /**
     * `device.unlocks(days?)`:
     * - Without [days]: today's count, as a number.
     * - With [days]: a list with one count for each of the last [days] days ([unlockDays]), the
     *   oldest first. So today's count is the last one.
     *
     * It needs no `@uses` item. Today's count never needed one, and the days before are the same
     * kind of data: one number a day, which says nothing about what you did. The `unlocks` command
     * shows the same numbers.
     */
    fun unlocks(days: Any?): Any {
        val n = unlockDays(days) ?: return unlocksToday()
        return com.mtwabpin.kolahal.unlock.UnlockCounter.get(context).history(n).map { it.second }
    }

    /** Do Not Disturb now: `off`, `priority`, `alarms` or `none`. Nil when it is switched off for the script. */
    fun dnd(): String? {
        if (!may("dnd", "device.dnd")) return null
        val filter =
            context.getSystemService(android.app.NotificationManager::class.java).currentInterruptionFilter
        return com.mtwabpin.kolahal.control.Dnd.wordOf(filter)
    }

    /**
     * Sets Do Not Disturb to [mode] (`off`, `on` or `priority`, `alarms`, `none`). Returns false when
     * Kolahal has no Do Not Disturb access (`dnd access`), or when it's switched off for the script.
     */
    fun setDnd(mode: String): Boolean {
        // The same words and the same check as the `dnd` command: "off" is true only once it is off.
        val filter = com.mtwabpin.kolahal.control.Dnd.filterOf(mode)
            ?: throw IllegalArgumentException("device.setDnd: mode must be off, on, priority, alarms or none")
        live()
        return may("dnd", "device.setDnd") && com.mtwabpin.kolahal.control.Dnd.set(
            context,
            filter
        ) == com.mtwabpin.kolahal.control.Dnd.Result.DONE
    }

    /** The work profile: `on` or `paused`. Nil when there is none, or when it's switched off for the script. */
    fun work(): String? {
        if (!may("work", "device.work")) return null
        val user = workProfile() ?: return null
        val um = context.getSystemService(android.os.UserManager::class.java)
        return if (runCatching { um.isQuietModeEnabled(user) }.getOrDefault(false)) "paused" else "on"
    }

    /** The argument of setWork: true or false, or "on" or "off". The same in Lua and JavaScript. */
    fun onOff(value: Any?, call: String): Boolean = when (value) {
        is Boolean -> value
        "on" -> true
        "off" -> false
        else -> throw IllegalArgumentException("$call takes true or false (or \"on\" or \"off\")")
    }

    /**
     * Resumes ([on]) or pauses the work profile. Returns false when Android won't let Kolahal do it:
     * - Kolahal must be the default launcher and in front (a daily time runs in the foreground, which
     *   counts), on Android 10 or newer,
     * - resuming needs your work PIN, or
     * - there's no work profile.
     */
    fun setWork(on: Boolean): Boolean {
        live()
        if (!may("work", "device.setWork")) return false
        val user = workProfile() ?: return false
        return com.mtwabpin.kolahal.apps.ProfileQuiet.set(
            context,
            user,
            quiet = !on
        ) == com.mtwabpin.kolahal.apps.ProfileQuiet.Result.DONE
    }

    /** The work profile's user, if there is one. Not this user, a private space or a clone. */
    private fun workProfile(): android.os.UserHandle? {
        val la = context.getSystemService(android.content.pm.LauncherApps::class.java)
        val me = android.os.Process.myUserHandle()
        return la.profiles.firstOrNull {
            AppRepository.kindOf(
                context,
                la,
                it,
                me
            ) == ProfileKind.WORK
        }
    }

    /** Launchable apps across profiles: [{label, pkg, profile}]. */
    fun appsList(): List<Map<String, Any?>> {
        if (!may("apps", "apps.list")) return emptyList()
        val la = context.getSystemService(android.content.pm.LauncherApps::class.java)
        val me = android.os.Process.myUserHandle()
        val um = context.getSystemService(android.os.UserManager::class.java)
        // Private space (and any paused profile) stays invisible to scripts.
        return la.profiles.filter { user ->
            !runCatching { um.isQuietModeEnabled(user) }.getOrDefault(false) && AppRepository.kindOf(
                context,
                la,
                user,
                me
            ) != ProfileKind.PRIVATE
        }.flatMap { user ->
            runCatching { la.getActivityList(null, user) }.getOrDefault(emptyList()).map {
                mapOf(
                    "label" to it.label.toString(),
                    "pkg" to it.componentName.packageName,
                    "profile" to if (user == me) "personal" else "other"
                )
            }
        }.sortedBy { (it["label"] as String).lowercase() }
    }

    /** Recent notifications (needs notification access): [{pkg, app, title, text, time}], newest first. */
    fun notificationsRecent(limit: Int): List<Any?> {
        if (!may("notifications", "notifications.recent")) return emptyList()
        val listener = com.mtwabpin.kolahal.notifications.KolahalNotificationListener
        // If access was revoked while the listener wasn't bound, the copy stays behind. Drop it and share nothing.
        if (!com.mtwabpin.kolahal.notifications.NotificationActions.isEnabled(context)) {
            runCatching { listener.clearMirror(context) }
            return emptyList()
        }
        val raw = KvStore(context, Stores.NOTIFICATIONS_MIRROR).get(listener.MIRROR_KEY)
            ?: return emptyList()
        return (runCatching { Json.parse(raw) as? List<*> }.getOrNull() ?: emptyList<Any?>()).take(
            limit.coerceIn(0, 50)
        )
    }

    /** The commands this run has sent with launcher.run. */
    private var commands = 0

    /** Runs a terminal command in the launcher. Only for scripts started from the prompt. */
    fun runCommand(sent: String): Boolean {
        // Split and checked the same way home will run it ([com.mtwabpin.kolahal.terminal.CommandProcessor.run]).
        val command = com.mtwabpin.kolahal.commands.CommandParsing.spaces(sent)
        live()
        // Commands (and through them, other scripts) are only for scripts that declare them and are allowed them.
        if (!may("commands", "launcher.run")) return false
        if (!interactive) {
            logError("launcher.run is only available when the script is run from the prompt")
            return false
        }
        if (byScript) {
            logError("launcher.run isn't available to a script another script started")
            return false
        }
        if (++commands > MAX_COMMANDS_PER_RUN) {
            if (commands == MAX_COMMANDS_PER_RUN + 1) logError("launcher.run: more than $MAX_COMMANDS_PER_RUN commands in one run; the rest don't run")
            return false
        }
        // A command goes to the terminal, its log and its history, and on to apps and sites. So it must never hold a secret.
        if (redact(command) != command) {
            logError("launcher.run refused: the command contains one of this script's secrets")
            return false
        }
        // State-changing commands (clear, uninstall, notifs clear, config, …) stay with the user. Home checks
        // again with your aliases: this process can't see them change (an alias such as `c = clear`).
        if (com.mtwabpin.kolahal.commands.InstantRun.isSensitive(command)) {
            logError("launcher.run refused '${command.substringBefore(' ')}': sensitive commands must be typed")
            return false
        }
        if (command.contains(' ') && grants != null && !grants.allows(ScriptAccess.INTERNET)) {
            val verb =
                com.mtwabpin.kolahal.commands.InstantRun.canonical(command.substringBefore(' '))
            // A search or a place sends its words to a site through another app, so it needs the internet.
            if (verb in WEB_COMMANDS) {
                logError("launcher.run refused '$verb': it sends text to a website, and this script has no internet access")
                return false
            }
            // A `@command` script runs with its own switches, so it could pass them on itself.
            val other = ScriptRepository.get(context).byName(verb)
            if (other != null && ScriptMeta.isCommand(other.code) && ScriptMeta.goesOnline(other.code)) {
                logError("launcher.run refused '$verb': it goes online, and this script has no internet access")
                return false
            }
        }
        flush()
        context.sendBroadcast(
            android.content.Intent(ACTION_RUN_COMMAND).setPackage(context.packageName)
                .putExtra(EXTRA_COMMAND, command)
        )
        return true
    }

    /** GET with a per-script cache. Returns the stored response if it is younger than [maxAgeSeconds]. */
    fun httpCached(url: String, maxAgeSeconds: Int): Map<String, Any?> {
        val key = runSecrets.urlKey(HTTP_CACHE, url)
        val cached =
            store.get(key)?.let { runCatching { Json.parse(it) as? Map<*, *> }.getOrNull() }
        val at = (cached?.get("at") as? Number)?.toLong() ?: 0L
        // A response cached before bodies holding a key were excluded is removed now, and not returned.
        val kept = cached?.get("body") as? String
        if (kept != null && redact(kept) != kept) {
            store.set(key, null)
        } else if (cached != null && System.currentTimeMillis() - at < maxAgeSeconds * 1000L) {
            return mapOf(
                "status" to 200,
                "ok" to true,
                "body" to cached["body"],
                "headers" to emptyMap<String, String>(),
                "cached" to true
            )
        }
        val resp = httpGet(url)
        // A body near the store's value cap isn't cached, because the store would refuse it anyway.
        val body = resp["body"] as? String
        // A site can send a key back, and a store goes into backups, so a body that holds a key isn't kept.
        // An answer that came after the run ended (a JavaScript call not awaited) isn't kept, because the run changes nothing then.
        if (!over && resp["ok"] == true && (body?.length
                ?: 0) < KvStore.MAX_VALUE / 2 && (body == null || redact(body) == body)
        ) {
            val entry = Json.stringify(mapOf("at" to System.currentTimeMillis(), "body" to body))
            // Cached responses count toward the store's limits. When the store is full, the other cached
            // responses make room: they are only a cache, and a full store would refuse the script's own values.
            runCatching { store.set(key, entry) }.recoverCatching {
                store.deleteStartingWith(HTTP_CACHE, except = key)
                store.set(key, entry)
            }
        }
        return resp + ("cached" to false)
    }

    fun storeGet(key: String): String? = store.get(key)

    fun storeSet(key: String, value: String?) {
        live()
        // A store goes into backups, so it must never hold a secret.
        require(redact(key) == key && (value == null || redact(value) == value)) { "store.set: the key or value contains one of this script's secrets" }
        store.set(key, value)
    }

    companion object {
        private const val MAX_BODY = 4L * 1024 * 1024
        private const val MAX_SEEN = 500
        private const val STATUS_MAX = 80

        /** How often one run can change a value it gives shaders. */
        private const val MAX_GIVES_PER_RUN = 40

        /** What `status(text, opts)` takes in opts. */
        private val STATUS_OPTIONS = listOf("progress", "refresh", "color")
        const val ACTION_RUN_COMMAND = "com.mtwabpin.kolahal.RUN_COMMAND"
        const val EXTRA_COMMAND = "command"

        /** The context keys a script's line may carry. Anything else is dropped. */
        private val SCRIPT_CTX = setOf(
            LogContext.URL,
            LogContext.IMAGE,
            LogContext.COPY,
            LogContext.MONO,
            LogContext.GAP
        )

        /** The prefix of the store key for a response that `http.cached` keeps. */
        const val HTTP_CACHE = "http.cache:"

        /** Commands that send their words to a site (through the browser or another app). */
        private val WEB_COMMANDS = setOf("search", "yt", "wiki", "maps", "play")
        private val METHODS_REQUIRING_BODY = setOf("POST", "PUT", "PATCH", "PROPPATCH", "REPORT")

        private val client get() = Net.client

        /** The most lines one run may log. A runaway loop stops filling the log here. */
        const val MAX_LINES_PER_RUN = 500

        /** The most characters one run may log, including its lines' links and other context. */
        const val MAX_CHARS_PER_RUN = 256 * 1024

        /** The most commands one run may send with launcher.run. Home logs the output of each one. */
        const val MAX_COMMANDS_PER_RUN = 10

        /** The most notifications one run may post. */
        const val MAX_NOTIFY_PER_RUN = 10

        /** The most status line writes one run may make. */
        const val MAX_STATUS_PER_RUN = 20

        /** The most `widget.show` and `widget.clear` calls that count in one run. */
        const val MAX_WIDGET_PER_RUN = 20

        /** The largest description `widget.show` stores, as JSON (bytes). */
        const val MAX_WIDGET_BYTES = 32 * 1024

        /** How many times a run can change its lines with `line.set`. Later changes are ignored. */
        const val MAX_SETS_PER_RUN = 300

        /** At most this many buttons on a line (`ctx.actions`), each label at most [MAX_ACTION_LABEL] characters, its arguments [MAX_ACTION_ARGS]. */
        const val MAX_ACTIONS = 3
        const val MAX_ACTION_LABEL = 24
        const val MAX_ACTION_ARGS = 200

        /** The longest text clipboard.set accepts (characters). */
        const val MAX_CLIP = 100_000
    }
}

/** The most days `device.unlocks(days)` returns: as many as the `unlocks` command shows. */
internal const val MAX_UNLOCK_DAYS = 30

/**
 * How many days `device.unlocks(days)` returns, for the argument [days] a script passed:
 * - nothing (`nil`, `null`, no argument): null. The call then returns today's count as a number.
 * - a number: that many days, from 1 to [MAX_UNLOCK_DAYS]. A fraction is dropped, and a number
 *   out of that range is held at its nearest end.
 * - anything else: an error that says what to pass.
 *
 * It is outside [HostApi] so a JVM test can call it without Android.
 */
internal fun unlockDays(days: Any?): Int? = when (days) {
    null -> null
    is Number -> days.toDouble()
        .let { if (it.isNaN()) 1 else it.coerceIn(1.0, MAX_UNLOCK_DAYS.toDouble()).toInt() }

    else -> throw IllegalArgumentException("device.unlocks: days must be a number from 1 to $MAX_UNLOCK_DAYS")
}
