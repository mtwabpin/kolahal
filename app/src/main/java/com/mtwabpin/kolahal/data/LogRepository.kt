package com.mtwabpin.kolahal.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import androidx.core.database.sqlite.transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The persistent terminal log. Writes from the `:script` process are announced with a
 * package-scoped broadcast, so the UI process can refresh.
 */
class LogRepository private constructor(private val context: Context) {
    private val db get() = Db.get(context)
    /** The log's marks: what `clear` and `fresh` hid, what you've seen, and the rest ([LogMarks]). */
    internal val marks = LogMarks(context, inMemory = !Singleton.inScriptProcess)

    private val _version = MutableStateFlow(0L)

    /** Incremented on every change this process sees. */
    val version: StateFlow<Long> = _version.asStateFlow()

    private val _linesVersion = MutableStateFlow(0L)

    /**
     * Like [version], but not for lines changed in place ([updateAll], a script's `line.set`). A log
     * search runs again on this; for changes in place it patches its results ([updatedSince]).
     */
    val linesVersion: StateFlow<Long> = _linesVersion.asStateFlow()

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
     * Like [add], but first removes earlier lines with the same kind and text. So a repeated event
     * (launching the same app again) keeps a single line, moved to the bottom. A starred line stays
     * where it is.
     */
    fun addReplacing(kind: LogKind, text: String, ctx: LogContext = LogContext.EMPTY): Long {
        if (capture.get() == null) {
            val ids = db.readableDatabase.rawQuery("SELECT id FROM logs WHERE kind = ? AND text = ? AND starred = 0", arrayOf(kind.key, text))
                .use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
            if (ids.isNotEmpty()) {
                // Delete the rows just found, instead of searching every line of that kind a second time.
                db.writableDatabase.delete("logs", "id IN (${ids.joinToString(",")})", null)
                noteRemoved(ids)
            }
        }
        return add(kind, text, ctx = ctx)
    }

    data class Pending(val kind: LogKind, val text: String, val source: String?, val ctx: LogContext, val ts: Long)

    /** Lines added on one thread while [batched] runs. They are written together when it ends, or [BATCH_MS] after the first one starts waiting. */
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
     * Runs [block] and writes the lines it adds on this thread together: in one transaction when it
     * ends, or every [BATCH_MS] while it runs, so a slow command still shows its output as it goes.
     * A command that prints fifty lines then costs one write, not fifty. Lines added on other threads
     * are written as usual. Inside a [capture], nothing is written at all.
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

    /** Inserts [lines] in one transaction and announces them once. Returns their ids, in order. */
    private fun write(lines: List<Pending>): List<Long> {
        val w = db.writableDatabase
        val ids = ArrayList<Long>(lines.size)
        w.transaction { for (p in lines) ids += insert(w, p) }
        writes.addAndGet(lines.size)
        changed()
        return ids
    }

    /** The command line being run on this thread ([withCause]). Lines added in the meantime carry its id. */
    private val cause = ThreadLocal<Long?>()

    /** The command line that lines added on this thread now belong to ([LogContext.FROM]), if any. */
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

    /** A per-thread output buffer for pipes. Lines added on other threads are written as usual. */
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

    /**
     * Inserts several lines in one transaction with a single change notification. Returns their ids
     * in order, or null when they were not written yet (inside [capture] or [batched]).
     */
    fun addAll(lines: List<Pending>): List<Long>? {
        if (lines.isEmpty()) return emptyList()
        val tagged = lines.map { it.copy(ctx = caused(it.ctx)) }
        capture.get()?.let { buffer ->
            buffer += tagged
            return null
        }
        batch.get()?.let { b ->
            tagged.forEach(b::add)
            return null
        }
        return write(tagged)
    }

    /**
     * Changes lines in place (a script's `line.set`), each to its text and context. They keep their
     * places and times. All are written in one transaction and announced once, so home reads them
     * again together ([updatedSince]). Lines that are gone are skipped. Returns the ids changed.
     */
    fun updateAll(changes: Map<Long, Pair<String, LogContext>>): List<Long> {
        if (changes.isEmpty()) return emptyList()
        val w = db.writableDatabase
        val done = ArrayList<Long>(changes.size)
        w.transaction {
            for ((id, change) in changes) {
                val values = ContentValues().apply {
                    put("text", clip(change.first))
                    put("ctx", clip(change.second).toJson())
                }
                if (w.update("logs", values, "id = ?", arrayOf(id.toString())) > 0) done += id
            }
        }
        noteUpdated(done)
        return done
    }

    /** [updateAll] for one line. False if the line is gone. */
    fun update(id: Long, text: String, ctx: LogContext): Boolean = updateAll(mapOf(id to (text to ctx))).isNotEmpty()

    /** The lines with these [ids] (those that still exist), in no set order. */
    fun byIds(ids: Collection<Long>): List<LogEntry> {
        if (ids.isEmpty()) return emptyList()
        val out = ArrayList<LogEntry>()
        db.readableDatabase.rawQuery("SELECT $ENTRY_COLUMNS FROM logs WHERE id IN (${ids.joinToString(",")})", null).use { c ->
            while (c.moveToNext()) out += c.entry()
        }
        return out
    }

    /**
     * Lines changed in place ([update]), as removals are tracked: home reads them again instead of
     * the whole page. The last [REMOVALS_KEPT] are remembered.
     */
    private val updates = ArrayDeque<Pair<Long, Long>>()

    /** The number of the latest change in place, or 0 before any. */
    @Volatile var updatedSeq = 0L
        private set

    /** Ids changed in place after change number [since], or null when that is too long ago to know. Then the page must be read again. */
    @Synchronized fun updatedSince(since: Long): Set<Long>? {
        if (since < updatedSeq - updates.size) return null
        return updates.filter { (seq, _) -> seq > since }.mapTo(HashSet()) { (_, id) -> id }
    }

    private fun noteUpdated(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        // Another process (a script) tells home which lines changed.
        if (!inMainProcess) return changed(updated = ids, inPlace = true)
        synchronized(this) {
            for (id in ids) {
                updates.addLast(++updatedSeq to id)
                if (updates.size > REMOVALS_KEPT) updates.removeFirst()
            }
        }
        changed(inPlace = true)
    }

    /** Another process changed these lines in place. */
    fun onExternalUpdate(ids: LongArray) = noteUpdated(ids.toList())

    /**
     * Incremented by a change in the middle of the log that home can't apply to its page by itself
     * (a restore, a hide, a clear). Home then reads the page again. Removed lines ([removedSince])
     * and trims ([trimmedUpTo]) are tracked separately, so the page is read again only when they
     * reach into it.
     */
    @Volatile var structure = 0L
        private set

    private fun restructured() {
        structure++
        changed(restructure = true)
    }

    /**
     * Numbered lines removed from the middle of the log (a swiped line, a launch line moved to the
     * bottom). Home drops them from its page instead of reading it again, starting from the number
     * it last applied ([removedSince]). The last [REMOVALS_KEPT] are remembered.
     */
    private val removals = ArrayDeque<Pair<Long, Long>>()

    /** The number of the latest removal, or 0 before any. */
    @Volatile var removedSeq = 0L
        private set

    /**
     * Ids removed after removal number [since], or null when that is too long ago to know. Then the
     * page must be read again.
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
        // Inside a batch, the flush announces the change together with the lines it writes.
        if (batch.get() == null) changed()
    }

    /**
     * The id up to which trims have removed lines. It only grows. Trims don't touch a page whose
     * oldest line is newer than this, so home keeps that page.
     */
    @Volatile var trimmedUpTo = 0L
        private set

    private fun trimmed(upTo: Long) {
        trimmedUpTo = maxOf(trimmedUpTo, upTo)
        changed(trimmedUpTo = upTo)
    }

    /**
     * The newest [limit] visible lines, oldest first. If given, only lines after [afterId], and only
     * lines before [beforeId] (an older page, read as the user scrolls back).
     */
    fun recent(limit: Int = 400, afterId: Long = 0L, beforeId: Long = Long.MAX_VALUE): List<LogEntry> {
        val out = ArrayList<LogEntry>()
        db.readableDatabase.rawQuery(
            "SELECT $ENTRY_COLUMNS FROM logs WHERE id > ? AND id < ? ORDER BY id DESC LIMIT ?",
            arrayOf(maxOf(visibleAfter(), afterId).toString(), beforeId.toString(), limit.toString()),
        ).use { c ->
            while (c.moveToNext()) out += c.entry()
        }
        out.reverse()
        return out
    }

    /** The result of [LogRepository.search]: the newest matches, how many there are, and how many lines were read. */
    data class Found(val entries: List<LogEntry>, val total: Int, val searched: Int)

    /**
     * Lines with every one of [words] (ignoring case) in their text, source, link, script or second
     * column, newest first. It searches everything the log keeps, including lines a fresh screen hid
     * and starred lines that a clear took off the screen.
     * - At most [limit] lines are returned. [Found.total] counts them all.
     * - [skip] leaves lines out (those of a locked private space).
     * - With [match], only the lines it accepts are searched (`/@xkcd`, `log only xkcd`). With no
     *   words, all of those lines are found. [narrowBy] is a condition that every line [match] accepts
     *   passes, so the database leaves out the rest ([com.mtwabpin.kolahal.terminal.LogFilter.sql]).
     */
    fun search(
        words: List<String>,
        limit: Int = 500,
        skip: ((LogEntry) -> Boolean)? = null,
        match: ((LogEntry) -> Boolean)? = null,
        narrowBy: com.mtwabpin.kolahal.terminal.LogFilter.Sql? = null,
        keepGoing: () -> Boolean = { true },
    ): Found {
        val found = ArrayList<LogEntry>()
        var total = 0
        var searched = 0
        var skipped = 0
        val from = marks.long(KEY_CLEARED_UP_TO).toString()
        // The database looks for plain words first, so only lines that may hold them are read. This
        // works only for plain words because LIKE ignores case for ASCII only, and a quote or a
        // backslash looks different in the stored JSON. With [match], every line is read, because
        // the count of lines it accepts is shown.
        val narrow = match == null && words.isNotEmpty() && words.all { w -> w.all { it in ' '..'~' && it != '"' && it != '\\' } }
        val where = StringBuilder(KEPT)
        val args = arrayListOf(from)
        if (match != null && narrowBy != null) {
            where.append(" AND ").append(narrowBy.where)
            args += narrowBy.args
        }
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
            "SELECT $ENTRY_COLUMNS FROM logs WHERE $where ORDER BY id DESC",
            args.toTypedArray(),
        ).use { c ->
            while (c.moveToNext()) {
                // A newer search (more typing, another log line) makes this one useless, so stop.
                if (c.position % 256 == 0 && !keepGoing()) break
                val text = c.getString(4)
                val source = c.getString(3)
                // JSON escapes slashes in links. Matching the raw row first avoids parsing most rows.
                // Styled text matches as it reads, so a word across a style's edge is still found.
                val rawCtx = c.getString(5)?.replace("\\/", "/")?.let(StyledText::plain)
                val shown = StyledText.plain(text)
                // A quote, a backslash or a tab is escaped in the raw JSON, so such a word is matched
                // only on the parsed line.
                val candidate = words.any { w -> w.any { it == '"' || it == '\\' || it < ' ' } } || words.all { w ->
                    shown.contains(w, ignoreCase = true) || source?.contains(w, ignoreCase = true) == true || rawCtx?.contains(w, ignoreCase = true) == true
                }
                // Only a line from a profile ([LogContext.USER]) can be skipped. Other lines aren't parsed unless they match.
                // With [match], every line is parsed, and only the lines it accepts count as searched.
                if (!candidate && match == null && (skip == null || rawCtx?.contains("\"${LogContext.USER}\"") != true)) {
                    searched++
                    continue
                }
                val e = c.entry()
                if (skip?.invoke(e) == true) {
                    skipped++
                    continue
                }
                if (match != null && !match(e)) continue
                searched++
                if (!candidate || !matchesWords(e, words)) continue
                total++
                if (found.size < limit) found += e
            }
        }
        // The lines the database ruled out count as searched too.
        if (narrow) {
            val all = db.readableDatabase.number("SELECT COUNT(*) FROM logs WHERE $KEPT", from).toInt()
            searched = maxOf(searched, all - skipped)
        }
        return Found(found, total, searched)
    }

    /** Where lines came from (scripts, feeds, apps), most recent first. `/@` and `log only` suggest these. */
    fun sources(): List<String> = db.readableDatabase.rawQuery(
        "SELECT source FROM logs WHERE source IS NOT NULL AND $KEPT GROUP BY source ORDER BY MAX(id) DESC",
        arrayOf(marks.long(KEY_CLEARED_UP_TO).toString()),
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    /** `log only …`, as typed ([com.mtwabpin.kolahal.terminal.LogFilter.parse]). It stays until cleared. */
    var filter: String?
        get() = marks.string(KEY_FILTER)
        set(value) = marks.set(KEY_FILTER to value)

    /** Whether line [e] has every one of [words] where a search looks ([searchable]), ignoring case. A search checks each line it finds with this. */
    fun matchesWords(e: LogEntry, words: List<String>): Boolean = words.all { w -> searchable(e).any { it.contains(w, ignoreCase = true) } }

    /** What a search looks in: what the line shows (including a script line's body, without styles), where it came from, and where it leads. */
    private fun searchable(e: LogEntry): List<String> =
        listOfNotNull(e.shownText, e.source, e.ctx.url, e.ctx.script, e.ctx.values[LogContext.DESC])

    /** Whether a fresh screen ([hideScreen]) keeps line [id] out of view. */
    fun isHidden(id: Long): Boolean = id <= marks.long(KEY_HIDDEN_UP_TO) && id > marks.long(KEY_CLEARED_UP_TO)

    /** How many visible lines there are from [id] to the newest. This is the page that reaches back to it. */
    fun countFrom(id: Long): Int =
        db.readableDatabase.number("SELECT COUNT(*) FROM logs WHERE id >= ? AND id > ?", id.toString(), visibleAfter().toString()).toInt()

    /** Distinct commands, most recent first, for history navigation. */
    fun commandHistory(limit: Int = HISTORY_LIMIT): List<String> {
        val out = ArrayList<String>()
        db.readableDatabase.rawQuery(
            "SELECT text FROM logs WHERE kind = ? AND id > ? GROUP BY text ORDER BY MAX(id) DESC LIMIT ?",
            arrayOf(LogKind.COMMAND.key, marks.long(KEY_HISTORY_FROM).toString(), limit.toString()),
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
            put("starred", if (e.starred) 1 else 0)
        }, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
        restructured()
    }

    /**
     * Stars line [id], or removes its star. A starred line stays beyond `log.keep-days` and
     * `log.keep-lines` ([trim]) and through a clear ([purgeCleared]). `/@starred` finds it.
     */
    fun setStarred(id: Long, starred: Boolean) {
        val values = ContentValues().apply { put("starred", if (starred) 1 else 0) }
        if (db.writableDatabase.update("logs", values, "id = ?", arrayOf(id.toString())) > 0) restructured()
    }

    /** Whether a clear took line [id] off the screen. Only a starred line is then still in the log. */
    fun isCleared(id: Long): Boolean = id <= marks.long(KEY_CLEARED_UP_TO)

    /** Empties ↑ recall. The log itself (commands and their output) stays as it is. */
    fun clearHistory() {
        val maxId = maxId()
        marks.set(KEY_HISTORY_FROM to maxId)
        restructured()
    }

    /** Deletes the whole log, history included. */
    @Synchronized
    fun clearAll() {
        db.writableDatabase.delete("logs", null, null)
        marks.set(KEY_CLEARED_UP_TO to 0L, KEY_HISTORY_FROM to 0L, KEY_HIDDEN_UP_TO to 0L, KEY_PURGE_UP_TO to null)
        restructured()
    }

    /** Lines at or below this id are not shown. It is the larger of a real clear and a fresh-screen hide. */
    private fun visibleAfter(): Long = maxOf(marks.long(KEY_CLEARED_UP_TO), marks.long(KEY_HIDDEN_UP_TO))

    /**
     * Fresh screen (`log.fresh`): hides what is on screen without deleting anything. [hiddenCount]
     * says how many lines [unhide] can bring back.
     */
    fun hideScreen(upTo: Long = maxId()) {
        // Never lower it: `fresh` may already hide more than what was seen when home was left.
        marks.set(KEY_HIDDEN_UP_TO to maxOf(upTo, marks.long(KEY_HIDDEN_UP_TO)))
        restructured()
    }

    fun unhide() {
        marks.set(KEY_HIDDEN_UP_TO to 0L)
        restructured()
    }

    /** The lines a fresh-screen hide keeps out of view (0 when nothing can be restored). */
    fun hiddenCount(): Int {
        val cleared = marks.long(KEY_CLEARED_UP_TO)
        val hidden = marks.long(KEY_HIDDEN_UP_TO)
        if (hidden <= cleared) return 0
        return db.readableDatabase.number("SELECT COUNT(*) FROM logs WHERE id > ? AND id <= ?", cleared.toString(), hidden.toString()).toInt()
    }

    /** The newest line. Used to avoid logging the same command twice in a row with nothing in between. */
    fun last(): Triple<Long, LogKind, String>? = db.readableDatabase.rawQuery(
        "SELECT id, kind, text FROM logs WHERE id > ? ORDER BY id DESC LIMIT 1", arrayOf(visibleAfter().toString()),
    ).use { c -> if (c.moveToFirst()) Triple(c.getLong(0), LogKind.of(c.getString(1)), c.getString(2)) else null }

    /** A [clearScreen]: lines up to [upTo] went out of view. [before] is how far the last clear reached. */
    data class Cleared(val before: Long, val upTo: Long)

    /**
     * Takes everything on screen out of view at once. The next [purgeCleared] removes it from the log
     * (command history is kept for recall). Until then, [undoClear] brings it back.
     */
    @Synchronized
    fun clearScreen(): Cleared {
        purgeCleared()
        val before = marks.long(KEY_CLEARED_UP_TO)
        val maxId = maxId()
        marks.set(KEY_CLEARED_UP_TO to maxId, KEY_PURGE_UP_TO to maxId)
        restructured()
        return Cleared(before, maxId)
    }

    /** Brings back what [cleared] took out of view, unless it is already deleted. */
    @Synchronized
    fun undoClear(cleared: Cleared): Boolean {
        if (marks.long(KEY_PURGE_UP_TO) != cleared.upTo) return false
        marks.set(KEY_CLEARED_UP_TO to cleared.before, KEY_PURGE_UP_TO to null)
        restructured()
        return true
    }

    /**
     * Deletes what a clear took out of view, up to [upTo], except commands and starred lines.
     * It runs when the clear's Undo is gone, before the next clear, and on a fresh start for a clear
     * that a closed launcher left behind.
     */
    @Synchronized
    fun purgeCleared(upTo: Long = marks.long(KEY_PURGE_UP_TO)) {
        // The lines were brought back since then, so they are on screen again.
        if (upTo <= 0L || marks.long(KEY_CLEARED_UP_TO) < upTo) return
        db.writableDatabase.delete("logs", "id <= ? AND kind != ? AND starred = 0", arrayOf(upTo.toString(), LogKind.COMMAND.key))
        if (marks.long(KEY_PURGE_UP_TO) <= upTo) marks.set(KEY_PURGE_UP_TO to null)
    }

    /**
     * Applies `log.keep-days` for a launcher that stays running for weeks. It runs the age trim at
     * most every 12 hours, so notification and call lines don't outlive the setting.
     */
    fun trimIfDue(maxAgeDays: Int) {
        val now = System.currentTimeMillis()
        if (now - marks.long(KEY_TRIMMED_AT) < 12 * 3_600_000L) return
        marks.set(KEY_TRIMMED_AT to now)
        trim(maxAgeDays = maxAgeDays)
    }

    /** Lines this process wrote since it last trimmed ([trimIfOverdue]). */
    private val writes = java.util.concurrent.atomic.AtomicInteger()

    /**
     * Applies `log.keep-lines` as the log grows: one trim every [TRIM_EVERY] lines this process
     * wrote. So notifications and commands can't pile up past the cap between script runs (which
     * trim) and restarts. Home calls it off the main thread whenever the log changes.
     */
    fun trimIfOverdue() {
        if (writes.get() < TRIM_EVERY) return
        writes.set(0)
        trim()
    }

    /**
     * Trims only if this process wrote a line since its last trim. A script run that logged nothing
     * (most pinned refreshes) has nothing to trim, and a trim is a write transaction with two counts.
     */
    fun trimIfWritten() {
        if (writes.getAndSet(0) > 0) trim()
    }

    /** `log.keep-lines`, for every process ([SharedSettings]). */
    var keepLines: Int
        get() = SharedSettings.get(context, Keys.LOG_KEEP_LINES)
        set(value) = SharedSettings.set(context, Keys.LOG_KEEP_LINES, value)

    /**
     * Keeps the newest [keep] lines, and separately the newest [keep] typed commands, so a script
     * that logs a lot can't push out ↑ history. Drops output older than [maxAgeDays]. Only the UI
     * process passes [maxAgeDays], because the :script process has an old copy of the settings.
     * Starred lines are kept, and not counted.
     */
    fun trim(keep: Int = keepLines, maxAgeDays: Int? = null) {
        val w = db.writableDatabase
        val command = LogKind.COMMAND.key
        var removed = 0
        var upTo = 0L
        // For each kind, find the newest line past the cap. It and every older line of that kind are deleted.
        // Count on the kind index first, then find the line from the oldest end, a few lines in after the
        // last trim. (Searching from the newest end read the whole log at every script run.) It runs in one
        // transaction, so another process's trim can't move the oldest end between the count and the cut.
        w.transaction {
            for (kind in listOf("!=", "=")) {
                // The starred lines are few. They are counted on their own index and subtracted.
                val count = w.number(
                    "SELECT (SELECT COUNT(*) FROM logs WHERE kind $kind ?) - (SELECT COUNT(*) FROM logs WHERE kind $kind ? AND starred = 1)",
                    command, command,
                ).toInt()
                if (count <= keep) continue
                val cut = w.rawQuery(
                    "SELECT id FROM logs WHERE kind $kind ? AND starred = 0 ORDER BY id LIMIT 1 OFFSET ?", arrayOf(command, (count - keep - 1).toString()),
                ).use { c -> if (c.moveToFirst()) c.getLong(0) else null } ?: continue
                removed += w.delete("logs", "kind $kind ? AND id <= ? AND starred = 0", arrayOf(command, cut.toString()))
                upTo = maxOf(upTo, cut)
            }
        }
        if (maxAgeDays != null) {
            val cutoff = System.currentTimeMillis() - maxAgeDays * 86_400_000L
            // Commands are kept (↑ history). Only their output ages out.
            val last = w.number("SELECT COALESCE(MAX(id), 0) FROM logs WHERE ts < ? AND kind != ? AND starred = 0", cutoff.toString(), command)
            if (last > 0) {
                removed += w.delete("logs", "ts < ? AND kind != ? AND starred = 0", arrayOf(cutoff.toString(), command))
                upTo = maxOf(upTo, last)
            }
        }
        if (removed > 0) trimmed(upTo)
    }

    fun maxId(): Long = db.readableDatabase.number("SELECT COALESCE(MAX(id), 0) FROM logs")

    /** The newest log id the user has seen (set when home is left), for unread markers. */
    var seenUpTo: Long
        get() = marks.long(KEY_SEEN_UP_TO, Long.MAX_VALUE)
        set(value) = marks.set(KEY_SEEN_UP_TO to value)

    /** Script status lines live in the kv table. This reuses the log change signal to refresh the header. */
    fun onStatusChanged() = changed()

    /** Called when another process tells us the log changed. */
    fun onExternalChange() {
        _version.value++
        _linesVersion.value++
    }

    /** Another process changed the middle of the log, so reload the page. */
    fun onExternalRestructure() = restructured()

    /** Another process trimmed the log up to [upTo]. The page is read again only if the trim reached into it. */
    fun onExternalTrim(upTo: Long) {
        trimmedUpTo = maxOf(trimmedUpTo, upTo)
        _version.value++
        _linesVersion.value++
    }

    /** Makes home look again without a new line, because a script run's result was recorded after its last line. */
    fun touch() = changed()

    private fun changed(restructure: Boolean = false, trimmedUpTo: Long = 0L, updated: Collection<Long> = emptyList(), inPlace: Boolean = false) {
        _version.value++
        if (!inPlace) _linesVersion.value++
        // Other processes (scripts) tell home. Home's own writes already incremented the version above.
        if (!inMainProcess) {
            context.sendBroadcast(
                Intent(ACTION_LOGS_CHANGED).setPackage(context.packageName)
                    .putExtra(EXTRA_RESTRUCTURE, restructure)
                    .putExtra(EXTRA_TRIMMED_UP_TO, trimmedUpTo)
                    .putExtra(EXTRA_UPDATED, updated.toLongArray()),
            )
        }
    }

    private val inMainProcess by lazy { android.app.Application.getProcessName() == context.packageName }

    /** Caps a line's text, so one huge value (an HTTP body, a script's log(bigString)) stays readable. */
    private fun clip(s: String) = if (s.length <= MAX_FIELD) s else s.takeWhole(MAX_FIELD - 1) + "…"

    /**
     * Caps a line's context: each value at a quarter of [MAX_FIELD], and the whole context, as JSON,
     * at [MAX_FIELD], so it reads back whole. Values past that are left out; the first ones are kept.
     */
    private fun clip(ctx: LogContext): LogContext {
        if (ctx.values.values.none { it.length > MAX_FIELD / 4 } && jsonLength(ctx.values) <= MAX_FIELD) return ctx
        val kept = LinkedHashMap<String, String>()
        var left = MAX_FIELD - 2
        for ((k, v) in ctx.values) {
            val value = if (v.length > MAX_FIELD / 4) v.takeWhole(MAX_FIELD / 4 - 1) + "…" else v
            val size = jsonLength(mapOf(k to value)) - 1
            if (size > left) continue
            kept[k] = value
            left -= size
        }
        return LogContext(kept)
    }

    /** The [LogEntry] at the cursor of a query for [ENTRY_COLUMNS]. */
    private fun Cursor.entry() =
        LogEntry(getLong(0), getLong(1), LogKind.of(getString(2)), getString(3), getString(4), LogContext.fromJson(getString(5)), getInt(6) != 0)

    companion object : Singleton<LogRepository>(::LogRepository) {
        /** Characters kept per stored field. This is far below the 2 MB SQLite cursor window. */
        const val MAX_FIELD = 64 * 1024

        /**
         * A line's columns, as the log reads them ([entry]). substr is needed because a row over the
         * 2 MB cursor window (written before lines were capped) can't be read at all, and would break
         * loading the log for good.
         */
        private const val ENTRY_COLUMNS = "id, ts, kind, source, substr(text, 1, $MAX_FIELD), substr(ctx, 1, $MAX_FIELD), starred"

        /** The number of commands that ↑ goes back through. */
        const val HISTORY_LIMIT = 200

        /** The length of [values] written as a JSON object by [LogContext.toJson] (with org.json's escapes). */
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

        private const val KEY_CLEARED_UP_TO = LogMarks.CLEARED_UP_TO
        private const val KEY_HIDDEN_UP_TO = LogMarks.HIDDEN_UP_TO
        private const val KEY_PURGE_UP_TO = LogMarks.PURGE_UP_TO
        private const val KEY_TRIMMED_AT = LogMarks.TRIMMED_AT
        private const val KEY_SEEN_UP_TO = LogMarks.SEEN_UP_TO
        private const val KEY_HISTORY_FROM = LogMarks.HISTORY_FROM
        private const val KEY_FILTER = LogMarks.FILTER

        /** The lines a search reads, with the clear's id as the argument: lines after it, and starred lines before it. */
        private const val KEPT = "(id > ? OR starred = 1)"

        const val ACTION_LOGS_CHANGED = "com.mtwabpin.kolahal.LOGS_CHANGED"
        const val EXTRA_RESTRUCTURE = "restructure"
        const val EXTRA_TRIMMED_UP_TO = "trimmed_up_to"
        /** Lines another process changed in place ([update]). */
        const val EXTRA_UPDATED = "updated"

        /** A batched command's output reaches the log at least this often while it runs. */
        private const val BATCH_MS = 250L

        /** Lines written between the trims that a process does on its own ([trimIfOverdue]). */
        const val TRIM_EVERY = 200

        /** Removals remembered for [removedSince]. This is more than home can fall behind by between two looks. */
        private const val REMOVALS_KEPT = 500
    }
}
