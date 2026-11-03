package com.mtwabpin.kolahal.data

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.core.database.sqlite.transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistent terminal log. Writes from the `:script` process are announced with a
 * package-scoped broadcast so the UI process can refresh.
 */
class LogRepository private constructor(private val context: Context) {
    private val db get() = Db.get(context)
    private val prefs by lazy { context.getSharedPreferences("logs", Context.MODE_PRIVATE) }

    private val _version = MutableStateFlow(0L)

    /** Bumped on every change seen by this process. */
    val version: StateFlow<Long> = _version.asStateFlow()

    fun add(
        kind: LogKind,
        text: String,
        source: String? = null,
        ctx: LogContext = LogContext.EMPTY,
    ): Long {
        val line = Pending(kind, text, source, caused(ctx), System.currentTimeMillis())
        capture.get()?.let { buffer ->
            buffer += line
            return -1
        }
        batch.get()?.let { b ->
            b.add(line)
            return -1
        }
        val id = insert(db.writableDatabase, line)
        writes.incrementAndGet()
        changed()
        return id
    }

    private fun insert(w: android.database.sqlite.SQLiteDatabase, p: Pending): Long = w.insert("logs", null, ContentValues().apply {
        put("ts", p.ts)
        put("kind", p.kind.key)
        put("source", p.source)
        put("text", clip(p.text))
        put("ctx", clip(p.ctx).toJson())
    })

    /**
     * Like [add], but first removes earlier lines with the same kind and text, so a repeated
     * event (launching the same app again) keeps a single line, moved to the bottom.
     */
    fun addReplacing(kind: LogKind, text: String, ctx: LogContext = LogContext.EMPTY): Long {
        if (capture.get() == null) {
            val ids = db.readableDatabase.rawQuery("SELECT id FROM logs WHERE kind = ? AND text = ?", arrayOf(kind.key, text))
                .use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
            if (ids.isNotEmpty()) {
                db.writableDatabase.delete("logs", "kind = ? AND text = ?", arrayOf(kind.key, text))
                noteRemoved(ids)
            }
        }
        return add(kind, text, ctx = ctx)
    }

    data class Pending(val kind: LogKind, val text: String, val source: String?, val ctx: LogContext, val ts: Long)

    /** Lines added on one thread while [batched] runs, written together: when it ends, or [BATCH_MS] after the first one waiting. */
    private inner class Batch {
        private val lines = ArrayList<Pending>()
        private var due: java.util.concurrent.ScheduledFuture<*>? = null

        @Synchronized fun add(line: Pending) {
            lines += line
            if (due == null) due = flusher.schedule({ flush() }, BATCH_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        }

        @Synchronized fun flush() {
            due?.cancel(false)
            due = null
            if (lines.isEmpty()) return
            val out = ArrayList(lines)
            lines.clear()
            write(out)
        }
    }

    private val batch = ThreadLocal<Batch?>()
    private val flusher = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "log-batch").apply { isDaemon = true } }

    /**
     * Runs [block] with the lines it adds on this thread written together: in one transaction when
     * it ends, or every [BATCH_MS] while it runs, so a slow command still shows its output as it
     * goes. A command that prints fifty lines then costs one write, not fifty. Lines added on
     * other threads are written as usual; inside a [capture], nothing is written at all.
     */
    fun <T> batched(block: () -> T): T {
        if (batch.get() != null || capture.get() != null) return block()
        val b = Batch()
        batch.set(b)
        try {
            return block()
        } finally {
            batch.set(null)
            b.flush()
        }
    }

    /** Inserts [lines] in one transaction and announces them once. */
    private fun write(lines: List<Pending>) {
        val w = db.writableDatabase
        w.transaction { for (p in lines) insert(w, p) }
        writes.addAndGet(lines.size)
        changed()
    }

    /** The command line being run on this thread ([withCause]); lines added meanwhile carry its id. */
    private val cause = ThreadLocal<Long?>()

    /** The command line lines added on this thread now belong to ([LogContext.FROM]), if any. */
    val currentCause: Long? get() = cause.get()

    /** Runs [block] with every line added on this thread marked as [commandId]'s output. */
    fun <T> withCause(commandId: Long?, block: () -> T): T {
        val previous = cause.get()
        cause.set(commandId)
        try {
            return block()
        } finally {
            cause.set(previous)
        }
    }

    private fun caused(ctx: LogContext): LogContext {
        val id = cause.get() ?: return ctx
        return if (LogContext.FROM in ctx.values) ctx else ctx + mapOf(LogContext.FROM to id.toString())
    }

    /** Per-thread output buffer for pipes; lines added on other threads are written as usual. */
    private val capture = ThreadLocal<MutableList<Pending>?>()

    /** Runs [block] with [add] on this thread buffered instead of written, and returns the buffer. */
    fun <T> capture(block: () -> T): Pair<T, List<Pending>> {
        val buffer = ArrayList<Pending>()
        val previous = capture.get()
        capture.set(buffer)
        try {
            return block() to buffer
        } finally {
            capture.set(previous)
        }
    }

    /** Inserts several lines in one transaction with a single change notification. */
    fun addAll(lines: List<Pending>) {
        if (lines.isEmpty()) return
        val tagged = lines.map { it.copy(ctx = caused(it.ctx)) }
        capture.get()?.let { buffer ->
            buffer += tagged
            return
        }
        batch.get()?.let { b ->
            tagged.forEach(b::add)
            return
        }
        write(tagged)
    }

    /**
     * Bumped by a change in the middle of the log that home can't apply to its page by itself (a
     * restore, a hide, a clear): it then reads the page again. Lines removed ([removedSince]) and
     * trims ([trimmedUpTo]) are told apart, so the page is only read again when they reach into it.
     */
    @Volatile var structure = 0L
        private set

    private fun restructured() {
        structure++
        changed(restructure = true)
    }

    /**
     * Lines removed from the middle of the log (a swiped line, a launch line moved to the bottom),
     * numbered: home drops them from its page instead of reading it again, from the number it last
     * applied ([removedSince]). The last [REMOVALS_KEPT] are remembered.
     */
    private val removals = ArrayDeque<Pair<Long, Long>>()

    /** The number of the latest removal; 0 before any. */
    @Volatile var removedSeq = 0L
        private set

    /**
     * Ids removed after removal number [since], or null when that is too long ago to know: the
     * page must then be read again.
     */
    @Synchronized fun removedSince(since: Long): Set<Long>? {
        if (since < removedSeq - removals.size) return null
        return removals.filter { (seq, _) -> seq > since }.mapTo(HashSet()) { (_, id) -> id }
    }

    private fun noteRemoved(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        if (!inMainProcess) return restructured()
        synchronized(this) {
            for (id in ids) {
                removals.addLast(++removedSeq to id)
                if (removals.size > REMOVALS_KEPT) removals.removeFirst()
            }
        }
        // Inside a batch, the flush announces the change along with the lines it writes.
        if (batch.get() == null) changed()
    }

    /**
     * The id up to which trims have removed lines; only grows. A page whose oldest line is newer
     * isn't touched by them, so home keeps it.
     */
    @Volatile var trimmedUpTo = 0L
        private set

    private fun trimmed(upTo: Long) {
        trimmedUpTo = maxOf(trimmedUpTo, upTo)
        changed(trimmedUpTo = upTo)
    }

    /**
     * The newest [limit] visible lines, oldest first; only those after [afterId] when given, and
     * only those before [beforeId] (an older page, read as the user scrolls back).
     */
    fun recent(limit: Int = 400, afterId: Long = 0L, beforeId: Long = Long.MAX_VALUE): List<LogEntry> {
        val out = ArrayList<LogEntry>()
        db.readableDatabase.rawQuery(
            // substr: a row over the 2 MB cursor window (written before lines were capped) can't be
            // read at all, and would break loading the log for good.
            "SELECT id, ts, kind, source, substr(text, 1, $MAX_FIELD), substr(ctx, 1, $MAX_FIELD) FROM logs WHERE id > ? AND id < ? ORDER BY id DESC LIMIT ?",
            arrayOf(maxOf(visibleAfter(), afterId).toString(), beforeId.toString(), limit.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                out += LogEntry(
                    id = c.getLong(0),
                    ts = c.getLong(1),
                    kind = LogKind.of(c.getString(2)),
                    source = c.getString(3),
                    text = c.getString(4),
                    ctx = LogContext.fromJson(c.getString(5)),
                )
            }
        }
        out.reverse()
        return out
    }

    /** [LogRepository.search]'s answer: the newest matches, how many there are, and how many lines were read. */
    data class Found(val entries: List<LogEntry>, val total: Int, val searched: Int)

    /**
     * Lines with every one of [words] (ignoring case) in their text, source, link, script or second
     * column, newest first: all the log keeps, lines a fresh screen hid included. At most [limit] are
     * returned; [Found.total] counts them all. [skip] leaves lines out (a locked private space's).
     * With [match], only the lines it takes are searched (`/@xkcd`, `log only xkcd`); with no words,
     * all of those are found.
     */
    fun search(
        words: List<String>,
        limit: Int = 500,
        skip: ((LogEntry) -> Boolean)? = null,
        match: ((LogEntry) -> Boolean)? = null,
        keepGoing: () -> Boolean = { true },
    ): Found {
        val found = ArrayList<LogEntry>()
        var total = 0
        var searched = 0
        var skipped = 0
        val from = prefs.getLong(KEY_CLEARED_UP_TO, 0L).toString()
        // Plain words are looked for by the database first, so only lines that may hold them are
        // read: LIKE ignores case for ASCII only, and a quote or a backslash reads differently in
        // the stored JSON. With [match], every line is read: the count of lines it takes is shown.
        val narrow = match == null && words.isNotEmpty() && words.all { w -> w.all { it in ' '..'~' && it != '"' && it != '\\' } }
        val where = StringBuilder("id > ?")
        val args = arrayListOf(from)
        if (narrow) {
            // A styled line may hold a word across a style's edge, so it is read and checked whole.
            // JSON escapes the slashes in a link.
            val every = words.joinToString(" AND ") { "(text LIKE ? ESCAPE '\\' OR source LIKE ? ESCAPE '\\' OR replace(ctx, '\\/', '/') LIKE ? ESCAPE '\\')" }
            where.append(" AND (text GLOB ? OR ctx GLOB ? OR ($every))")
            args += StyledText.GLOB
            args += StyledText.GLOB
            for (w in words) {
                val like = "%" + w.replace("%", "\\%").replace("_", "\\_") + "%"
                repeat(3) { args += like }
            }
        }
        db.readableDatabase.rawQuery(
            "SELECT id, ts, kind, source, substr(text, 1, $MAX_FIELD), substr(ctx, 1, $MAX_FIELD) FROM logs WHERE $where ORDER BY id DESC",
            args.toTypedArray(),
        ).use { c ->
            while (c.moveToNext()) {
                // A newer search (more typing, another log line) makes this one moot.
                if (c.position % 256 == 0 && !keepGoing()) break
                val text = c.getString(4)
                val source = c.getString(3)
                // JSON escapes slashes in links; matching the raw row first spares parsing most of them.
                // Styled text matches as it reads: a word across a style's edge is still the word.
                val rawCtx = c.getString(5)?.replace("\\/", "/")?.let(StyledText::plain)
                val shown = StyledText.plain(text)
                // A quote, a backslash or a tab is escaped in the raw JSON: such a word is matched on
                // the parsed line only.
                val candidate = words.any { w -> w.any { it == '"' || it == '\\' || it < ' ' } } || words.all { w ->
                    shown.contains(w, ignoreCase = true) || source?.contains(w, ignoreCase = true) == true || rawCtx?.contains(w, ignoreCase = true) == true
                }
                // Only a line from a profile ([LogContext.USER]) can be skipped: the others aren't parsed unless they match.
                // With [match], every line is parsed: only the ones it takes count as searched.
                if (!candidate && match == null && (skip == null || rawCtx?.contains("\"${LogContext.USER}\"") != true)) {
                    searched++
                    continue
                }
                val e = LogEntry(c.getLong(0), c.getLong(1), LogKind.of(c.getString(2)), source, text, LogContext.fromJson(c.getString(5)))
                if (skip?.invoke(e) == true) {
                    skipped++
                    continue
                }
                if (match != null && !match(e)) continue
                searched++
                if (!candidate || !words.all { w -> searchable(e).any { it.contains(w, ignoreCase = true) } }) continue
                total++
                if (found.size < limit) found += e
            }
        }
        // The lines the database ruled out were searched too.
        if (narrow) {
            val all = db.readableDatabase.rawQuery("SELECT COUNT(*) FROM logs WHERE id > ?", arrayOf(from)).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            searched = maxOf(searched, all - skipped)
        }
        return Found(found, total, searched)
    }

    /** Where lines came from (scripts, feeds, apps), most recent first: `/@` and `log only` suggest them. */
    fun sources(): List<String> = db.readableDatabase.rawQuery(
        "SELECT source FROM logs WHERE source IS NOT NULL AND id > ? GROUP BY source ORDER BY MAX(id) DESC",
        arrayOf(prefs.getLong(KEY_CLEARED_UP_TO, 0L).toString()),
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    /** `log only …`, as typed ([com.mtwabpin.kolahal.terminal.LogFilter.parse]); it stays until cleared. */
    var filter: String?
        get() = prefs.getString(KEY_FILTER, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_FILTER) else putString(KEY_FILTER, value) }

    /** What a search looks in: what the line shows (a script line's body too, styles aside), where it came from and where it leads. */
    private fun searchable(e: LogEntry): List<String> =
        listOfNotNull(e.shownText, e.source, e.ctx.url, e.ctx.script, e.ctx.values[LogContext.DESC])

    /** Whether a fresh screen ([hideScreen]) keeps line [id] out of view. */
    fun isHidden(id: Long): Boolean = id <= prefs.getLong(KEY_HIDDEN_UP_TO, 0L) && id > prefs.getLong(KEY_CLEARED_UP_TO, 0L)

    /** How many visible lines there are from [id] to the newest: the page that reaches back to it. */
    fun countFrom(id: Long): Int = db.readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM logs WHERE id >= ? AND id > ?", arrayOf(id.toString(), visibleAfter().toString()),
    ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    /** Distinct commands, most recent first, for history navigation. */
    fun commandHistory(limit: Int = HISTORY_LIMIT): List<String> {
        val out = ArrayList<String>()
        db.readableDatabase.rawQuery(
            "SELECT text FROM logs WHERE kind = ? AND id > ? GROUP BY text ORDER BY MAX(id) DESC LIMIT ?",
            arrayOf(LogKind.COMMAND.key, prefs.getLong(KEY_HISTORY_FROM, 0L).toString(), limit.toString()),
        ).use { c -> while (c.moveToNext()) out += c.getString(0) }
        return out
    }

    fun delete(id: Long) {
        if (db.writableDatabase.delete("logs", "id = ?", arrayOf(id.toString())) > 0) noteRemoved(listOf(id))
    }

    /** Puts a deleted line back with its original id, so it returns to the same place. */
    fun restore(e: LogEntry) {
        db.writableDatabase.insertWithOnConflict("logs", null, ContentValues().apply {
            put("id", e.id)
            put("ts", e.ts)
            put("kind", e.kind.key)
            put("source", e.source)
            put("text", clip(e.text))
            put("ctx", clip(e.ctx).toJson())
        }, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
        restructured()
    }

    /** Empties ↑ recall; the log itself (commands and their output) stays as it is. */
    fun clearHistory() {
        val maxId = db.readableDatabase.rawQuery("SELECT COALESCE(MAX(id), 0) FROM logs", null)
            .use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        prefs.edit { putLong(KEY_HISTORY_FROM, maxId) }
        restructured()
    }

    /** Deletes the whole log, history included. */
    @Synchronized
    fun clearAll() {
        db.writableDatabase.delete("logs", null, null)
        prefs.edit { putLong(KEY_CLEARED_UP_TO, 0L).putLong(KEY_HISTORY_FROM, 0L).putLong(KEY_HIDDEN_UP_TO, 0L).remove(KEY_PURGE_UP_TO) }
        restructured()
    }

    /** Lines at or below this id are not shown: the larger of a real clear and a fresh-screen hide. */
    private fun visibleAfter(): Long = maxOf(prefs.getLong(KEY_CLEARED_UP_TO, 0L), prefs.getLong(KEY_HIDDEN_UP_TO, 0L))

    /**
     * Fresh screen (`log.fresh`): hides what is on screen without deleting anything. [hiddenCount]
     * says how much can come back with [unhide].
     */
    fun hideScreen(upTo: Long = maxId()) {
        // Never lower: `fresh` may already hide more than what was seen when home was left.
        prefs.edit { putLong(KEY_HIDDEN_UP_TO, maxOf(upTo, prefs.getLong(KEY_HIDDEN_UP_TO, 0L))) }
        restructured()
    }

    fun unhide() {
        prefs.edit { putLong(KEY_HIDDEN_UP_TO, 0L) }
        restructured()
    }

    /** Lines a fresh-screen hide is keeping out of view (0 when nothing can be restored). */
    fun hiddenCount(): Int {
        val cleared = prefs.getLong(KEY_CLEARED_UP_TO, 0L)
        val hidden = prefs.getLong(KEY_HIDDEN_UP_TO, 0L)
        if (hidden <= cleared) return 0
        return db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM logs WHERE id > ? AND id <= ?", arrayOf(cleared.toString(), hidden.toString()),
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    /** The newest line, to avoid logging the same command twice in a row with nothing in between. */
    fun last(): Triple<Long, LogKind, String>? = db.readableDatabase.rawQuery(
        "SELECT id, kind, text FROM logs WHERE id > ? ORDER BY id DESC LIMIT 1", arrayOf(visibleAfter().toString()),
    ).use { c -> if (c.moveToFirst()) Triple(c.getLong(0), LogKind.of(c.getString(1)), c.getString(2)) else null }

    /** A [clearScreen]: lines up to [upTo] went out of view; [before] is how far the last clear reached. */
    data class Cleared(val before: Long, val upTo: Long)

    /**
     * Takes everything on screen out of view at once, and out of the log at the next [purgeCleared]
     * (command history is kept for recall); until then [undoClear] brings it back.
     */
    @Synchronized
    fun clearScreen(): Cleared {
        purgeCleared()
        val before = prefs.getLong(KEY_CLEARED_UP_TO, 0L)
        val maxId = db.readableDatabase.rawQuery("SELECT COALESCE(MAX(id), 0) FROM logs", null)
            .use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        prefs.edit { putLong(KEY_CLEARED_UP_TO, maxId).putLong(KEY_PURGE_UP_TO, maxId) }
        restructured()
        return Cleared(before, maxId)
    }

    /** Brings back what [cleared] took out of view, unless it is deleted already. */
    @Synchronized
    fun undoClear(cleared: Cleared): Boolean {
        if (prefs.getLong(KEY_PURGE_UP_TO, 0L) != cleared.upTo) return false
        prefs.edit { putLong(KEY_CLEARED_UP_TO, cleared.before).remove(KEY_PURGE_UP_TO) }
        restructured()
        return true
    }

    /**
     * Deletes what a clear took out of view, commands aside, up to [upTo]: once its Undo is gone,
     * before the next clear, and on a fresh start for one a closed launcher left behind.
     */
    @Synchronized
    fun purgeCleared(upTo: Long = prefs.getLong(KEY_PURGE_UP_TO, 0L)) {
        // Brought back since: they are on screen again.
        if (upTo <= 0L || prefs.getLong(KEY_CLEARED_UP_TO, 0L) < upTo) return
        db.writableDatabase.delete("logs", "id <= ? AND kind != ?", arrayOf(upTo.toString(), LogKind.COMMAND.key))
        if (prefs.getLong(KEY_PURGE_UP_TO, 0L) <= upTo) prefs.edit { remove(KEY_PURGE_UP_TO) }
    }

    /**
     * `log.keep-days` for a launcher that stays running for weeks: the age trim, at most every 12
     * hours, so notification and call lines don't outlive the setting.
     */
    fun trimIfDue(maxAgeDays: Int) {
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_TRIMMED_AT, 0L) < 12 * 3_600_000L) return
        prefs.edit { putLong(KEY_TRIMMED_AT, now) }
        trim(maxAgeDays = maxAgeDays)
    }

    /** Lines this process wrote since it last trimmed ([trimIfOverdue]). */
    private val writes = java.util.concurrent.atomic.AtomicInteger()

    /**
     * `log.keep-lines` as the log grows: a trim once every [TRIM_EVERY] lines this process wrote,
     * so notifications and commands can't pile up past the cap between script runs (which trim) and
     * restarts. Home calls it off the main thread whenever the log changes.
     */
    fun trimIfOverdue() {
        if (writes.get() < TRIM_EVERY) return
        writes.set(0)
        trim()
    }

    /** `log.keep-lines`, for every process ([SharedSettings]). */
    var keepLines: Int
        get() = SharedSettings.get(context, Keys.LOG_KEEP_LINES)
        set(value) = SharedSettings.set(context, Keys.LOG_KEEP_LINES, value)

    /**
     * Keeps the newest [keep] lines and, counted apart, the newest [keep] typed commands, so a script
     * that logs a lot can't push out ↑ history. Drops output older than [maxAgeDays], which only
     * the UI process passes (the :script process has a stale settings copy).
     */
    fun trim(keep: Int = keepLines, maxAgeDays: Int? = null) {
        val w = db.writableDatabase
        val command = LogKind.COMMAND.key
        var removed = 0
        var upTo = 0L
        // The newest line past the cap, of each kind: it and everything before it of that kind go.
        for (kind in listOf("!=", "=")) {
            val cut = w.rawQuery(
                "SELECT id FROM logs WHERE kind $kind ? ORDER BY id DESC LIMIT 1 OFFSET ?", arrayOf(command, keep.toString()),
            ).use { c -> if (c.moveToFirst()) c.getLong(0) else null } ?: continue
            removed += w.delete("logs", "kind $kind ? AND id <= ?", arrayOf(command, cut.toString()))
            upTo = maxOf(upTo, cut)
        }
        if (maxAgeDays != null) {
            val cutoff = System.currentTimeMillis() - maxAgeDays * 86_400_000L
            // Commands are kept (↑ history); only their output ages out.
            val last = w.rawQuery("SELECT COALESCE(MAX(id), 0) FROM logs WHERE ts < ? AND kind != ?", arrayOf(cutoff.toString(), command))
                .use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
            if (last > 0) {
                removed += w.delete("logs", "ts < ? AND kind != ?", arrayOf(cutoff.toString(), command))
                upTo = maxOf(upTo, last)
            }
        }
        if (removed > 0) trimmed(upTo)
    }

    fun maxId(): Long = db.readableDatabase.rawQuery("SELECT COALESCE(MAX(id), 0) FROM logs", null)
        .use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

    /** Newest log id the user has seen (set when home is left), for unread markers. */
    var seenUpTo: Long
        get() = prefs.getLong(KEY_SEEN_UP_TO, Long.MAX_VALUE)
        set(value) = prefs.edit { putLong(KEY_SEEN_UP_TO, value) }

    /** Script status lines live in the kv table; reuse the log change signal to refresh the header. */
    fun onStatusChanged() = changed()

    /** Called when another process tells us the log changed. */
    fun onExternalChange() {
        _version.value++
    }

    /** Another process changed the middle of the log: reload the page. */
    fun onExternalRestructure() = restructured()

    /** Another process trimmed the log up to [upTo]: the page is read again only if it reached into it. */
    fun onExternalTrim(upTo: Long) {
        trimmedUpTo = maxOf(trimmedUpTo, upTo)
        _version.value++
    }

    /** Has home look again without a new line: a script run's result was just recorded after its last line. */
    fun touch() = changed()

    private fun changed(restructure: Boolean = false, trimmedUpTo: Long = 0L) {
        _version.value++
        // Other processes (scripts) tell home; home's own writes already bumped the version above.
        if (!inMainProcess) {
            context.sendBroadcast(
                Intent(ACTION_LOGS_CHANGED).setPackage(context.packageName)
                    .putExtra(EXTRA_RESTRUCTURE, restructure)
                    .putExtra(EXTRA_TRIMMED_UP_TO, trimmedUpTo),
            )
        }
    }

    private val inMainProcess by lazy { android.app.Application.getProcessName() == context.packageName }

    /** Caps a line's text: one huge value (an HTTP body, a script's log(bigString)) must stay readable. */
    private fun clip(s: String) = if (s.length <= MAX_FIELD) s else s.takeWhole(MAX_FIELD - 1) + "…"

    /**
     * Caps a line's context: each value at a quarter of [MAX_FIELD], and all of it, as JSON, at
     * [MAX_FIELD] so it reads back whole. Values past that are left out, the first ones kept.
     */
    private fun clip(ctx: LogContext): LogContext {
        if (ctx.values.values.none { it.length > MAX_FIELD / 4 } && jsonLength(ctx.values) <= MAX_FIELD) return ctx
        val kept = LinkedHashMap<String, String>()
        var left = MAX_FIELD - 2
        for ((k, v) in ctx.values) {
            val value = if (v.length > MAX_FIELD / 4) v.take(MAX_FIELD / 4 - 1) + "…" else v
            val size = jsonLength(mapOf(k to value)) - 1
            if (size > left) continue
            kept[k] = value
            left -= size
        }
        return LogContext(kept)
    }

    companion object {
        /** Characters kept per stored field; far below the 2 MB SQLite cursor window. */
        const val MAX_FIELD = 64 * 1024

        /** Commands ↑ goes back through. */
        const val HISTORY_LIMIT = 200

        /** Length of [values] written as a JSON object by [LogContext.toJson] (org.json's escapes). */
        internal fun jsonLength(values: Map<String, String>): Int {
            fun quoted(s: String) = 2 + s.sumOf { c ->
                when {
                    c == '"' || c == '\\' || c == '/' || c == '\t' || c == '\b' || c == '\n' || c == '\r' || c == '\u000c' -> 2
                    c < ' ' || c == '\u2028' || c == '\u2029' -> 6
                    else -> 1
                }
            }
            return 2 + values.entries.sumOf { (k, v) -> quoted(k) + 1 + quoted(v) } + maxOf(values.size - 1, 0)
        }

        private const val KEY_CLEARED_UP_TO = "cleared_up_to"
        private const val KEY_HIDDEN_UP_TO = "hidden_up_to"
        private const val KEY_PURGE_UP_TO = "purge_up_to"
        private const val KEY_TRIMMED_AT = "trimmed_at"
        private const val KEY_SEEN_UP_TO = "seen_up_to"
        private const val KEY_HISTORY_FROM = "history_from"
        private const val KEY_FILTER = "filter"

        /** The preferences that hold a line's id, which a backup carries along with the lines ([FullBackup]). */
        internal val ID_KEYS = setOf(KEY_CLEARED_UP_TO, KEY_HIDDEN_UP_TO, KEY_PURGE_UP_TO, KEY_SEEN_UP_TO, KEY_HISTORY_FROM)

        const val ACTION_LOGS_CHANGED = "com.mtwabpin.kolahal.LOGS_CHANGED"
        const val EXTRA_RESTRUCTURE = "restructure"
        const val EXTRA_TRIMMED_UP_TO = "trimmed_up_to"

        /** A batched command's output reaches the log at least this often while it runs. */
        private const val BATCH_MS = 250L

        /** Lines written between the trims a process does on its own ([trimIfOverdue]). */
        const val TRIM_EVERY = 200

        /** Removals remembered for [removedSince]; more than home can fall behind by between two looks. */
        private const val REMOVALS_KEPT = 500

        @SuppressLint("StaticFieldLeak") // application context only

        @Volatile
        private var instance: LogRepository? = null

        fun get(context: Context): LogRepository = instance ?: synchronized(this) {
            instance ?: LogRepository(context.applicationContext).also { instance = it }
        }
    }
}
