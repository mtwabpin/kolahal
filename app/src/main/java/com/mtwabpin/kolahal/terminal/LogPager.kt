/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.commands.LogFilter
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private const val PAGE = 400
private const val MAX_PAGE = 5000

/**
 * The whole page is read again at least this often: a safety net for a change another process made
 * whose message never came. Trims, removals, changes in place and changes in the middle are
 * followed by their own counts.
 */
private const val FULL_RELOAD_MS = 60_000L

/** How a reload reads the page ([pageReading]). */
internal enum class PageReading {
    /** Only the lines after the newest one on the page, on top of the page it has. */
    NEW_LINES,

    /** The whole page, only because [FULL_RELOAD_MS] passed since the last whole read. */
    TIMED,

    /** The whole page, because there is no page yet, or a change can't be applied to it. */
    WHOLE,
}

/**
 * How a reload reads the page:
 * - [WHOLE][PageReading.WHOLE] with no page yet ([hasPage]), after a change in the middle of the log
 *   ([sameStructure] false), when a trim reached into the page ([trimReached]), and when the
 *   removals or the changes in place since the last reload are no longer known ([removalsKnown], [updatesKnown]);
 * - else [TIMED][PageReading.TIMED] when [sinceWhole] ms passed since the last whole read is
 *   [FULL_RELOAD_MS] or more;
 * - else [NEW_LINES][PageReading.NEW_LINES].
 */
internal fun pageReading(
    hasPage: Boolean,
    sameStructure: Boolean,
    trimReached: Boolean,
    removalsKnown: Boolean,
    updatesKnown: Boolean,
    sinceWhole: Long,
): PageReading = when {
    !hasPage || !sameStructure || trimReached || !removalsKnown || !updatesKnown -> PageReading.WHOLE
    sinceWhole >= FULL_RELOAD_MS -> PageReading.TIMED
    else -> PageReading.NEW_LINES
}

/**
 * What a reload tells the view model ([LogPager.Read]) after it read [lines] the way [reading] says.
 * After a [TIMED][PageReading.TIMED] read, [LogPager.Read.fresh] holds only the lines after [newest],
 * the newest line of the page before: what else follows the log is up to date by its own signals.
 */
internal fun readOf(
    reading: PageReading,
    lines: List<LogEntry>,
    newest: Long?,
    trimmed: Boolean,
    commandGone: Boolean,
): LogPager.Read = LogPager.Read(
    whole = reading == PageReading.WHOLE,
    fresh = if (reading == PageReading.TIMED && newest != null) lines.filter { it.id > newest } else lines,
    trimmed = trimmed,
    commandGone = commandGone,
    timed = reading == PageReading.TIMED,
)

/**
 * What the work after the rows ([LogPager.reload]'s `afterShown`) still has to apply: [read], and
 * [before] when that work did not finish for an earlier reload (canceled by a newer change, or failed).
 * Together they say what both said. Past [MAX_PAGE] new lines, it becomes a whole read.
 */
internal fun owed(before: LogPager.Read?, read: LogPager.Read): LogPager.Read {
    if (before == null) return read
    val whole = before.whole || read.whole
    // With a whole read the lines don't matter: everything that follows them is read again.
    val fresh = if (read.whole) read.fresh else before.fresh + read.fresh
    return LogPager.Read(
        whole = whole || fresh.size > MAX_PAGE,
        fresh = fresh,
        trimmed = before.trimmed || read.trimmed,
        commandGone = before.commandGone || read.commandGone,
        timed = before.timed || read.timed,
    )
}

/**
 * The log's page: its lines, oldest first. [atStart] is true when the log is known to have no
 * visible line before them. False can also mean "not known yet".
 */
internal data class LogPage(val lines: List<LogEntry>, val atStart: Boolean)

/**
 * Returns [page] after the log changed, without reading the whole page again:
 * - the removed lines ([gone], by id) are dropped;
 * - a line changed in place ([again], by id) replaces the one on the page. Ids not on the page are ignored;
 * - the new lines ([fresh], oldest first) come at the end;
 * - if the page is then shorter than [limit] and the log may have more before it, [older] reads them:
 *   `older(count, beforeId)` gives the newest `count` lines before the line `beforeId`, oldest first.
 *   This happens after scrolling back, after showing a search result, and after lines were removed;
 * - at most [limit] lines stay, the newest.
 *
 * This gives the same lines as reading the page again ([LogRepository.recent]). The one exception:
 * when every line of the page went and no new one came, the page is empty, and the next reload
 * reads it whole.
 */
internal fun mergePage(
    page: LogPage,
    gone: Set<Long>,
    again: Map<Long, LogEntry>,
    fresh: List<LogEntry>,
    limit: Int,
    older: (count: Int, beforeId: Long) -> List<LogEntry>,
): LogPage {
    var start = page.atStart
    val left = if (gone.isEmpty()) page.lines else page.lines.filterNot { it.id in gone }
    // Lines a script changed in place (line.set), read again where they are. Ids not on the page are ignored.
    val kept = if (again.isEmpty()) left else left.map { again[it.id] ?: it }
    val have = kept.size + fresh.size
    // After scrolling back (or showing a search result): read the lines before the page, up to the new limit.
    val before = (kept.firstOrNull() ?: fresh.firstOrNull())?.id
    val more = if (have >= limit || start || before == null) emptyList() else {
        older(limit - have, before).also { if (it.size < limit - have) start = true }
    }
    val merged = more + kept + fresh
    // The page shrank ([LogPager.atNewest]) or grew past its limit. The lines cut off are older than the page now.
    if (merged.size > limit) start = false
    return LogPage(merged.takeLast(limit), start)
}

/** True when a removed line ([gone], by id) may have been a command: it was one, or it wasn't on the page ([lines]) to tell. */
internal fun mayRemoveCommand(lines: List<LogEntry>, gone: Set<Long>): Boolean =
    gone.isNotEmpty() && lines.filter { it.id in gone }
        .let { had -> had.size < gone.size || had.any { it.kind == LogKind.COMMAND } }

/** What `log only …` shows: its newest lines, oldest first, and how many lines of the whole log the filter lets through ([count]). */
internal data class FilterPage(val lines: List<LogEntry>, val count: Int)

/**
 * Returns `log only …` after the log changed, from what it showed ([shown]), without searching the whole log again:
 * - a line changed in place ([again], by id) is replaced where it is, or goes once [matches] no longer accepts it;
 * - the new lines ([fresh], oldest first) that [matches] accepts come at the end, without those of a
 *   locked private space ([locked], by serial);
 * - at most [limit] lines stay, the newest. [FilterPage.count] counts them all.
 *
 * Returns null when the whole log must be searched instead: a changed line now matches, but wasn't shown.
 */
internal fun patchFilterPage(
    shown: FilterPage,
    again: Map<Long, LogEntry>,
    fresh: List<LogEntry>,
    limit: Int,
    locked: Set<Long>,
    matches: (LogEntry) -> Boolean,
): FilterPage? {
    if (again.isNotEmpty()) {
        val shownIds = shown.lines.mapTo(HashSet()) { it.id }
        if (again.values.any { it.id !in shownIds && it.ctx.userSerial !in locked && matches(it) }) return null
    }
    var count = shown.count
    // Lines changed in place: patched where they are, or dropped once they no longer match.
    val patched = if (again.isEmpty()) shown.lines else shown.lines.mapNotNull { e ->
        val now = again[e.id] ?: return@mapNotNull e
        if (matches(now)) now else null.also { count-- }
    }
    val more =
        (if (locked.isEmpty()) fresh else fresh.filterNot { it.ctx.userSerial in locked }).filter(
            matches
        )
    count += more.size
    return FilterPage((patched + more).takeLast(limit), count)
}

/**
 * Whether `log only …` can be patched from what it showed ([patchFilterPage]) instead of searching
 * the whole log: the same [filter] as then ([filteredFor]), a page no bigger ([limit], [filteredLimit]),
 * no line removed ([removed]), and the same locked private spaces ([locked], [filteredLocks]). A
 * space locked since keeps its lines out only after a whole search: a patch looks at new lines only.
 */
internal fun canPatchFilter(
    filter: LogFilter,
    incremental: Boolean,
    filteredFor: LogFilter?,
    limit: Int,
    filteredLimit: Int,
    removed: Boolean,
    locked: Set<Long>,
    filteredLocks: Set<Long>,
): Boolean =
    incremental && filter == filteredFor && limit <= filteredLimit && !removed && locked == filteredLocks

/**
 * The page of the log that home shows: the newest lines, and older ones as the user scrolls back.
 * It keeps the page between reloads, so a reload usually reads only what changed. The view model
 * holds one as `pager`, and its reload collector calls [reload]. The pager starts no work itself.
 * - [only]: the filter of `log only …`, or null. With one, the page shows the filter's lines, from the whole log.
 * - [locks]: the serials of the private spaces that are locked now. Their lines are left out.
 * - [seenUpTo], [unreadUntil]: script lines between them arrived while the user was away ([buildRows]).
 * - [requestReload]: asks for a reload, after the page's size changed.
 */
class LogPager(
    private val logs: LogRepository,
    private val only: StateFlow<LogFilter?>,
    private val locks: () -> Set<Long>,
    private val seenUpTo: () -> Long,
    private val unreadUntil: () -> Long,
    private val requestReload: () -> Unit,
) {
    /**
     * What one [reload] read. The view model refreshes from it what else follows the log (↑ history,
     * the todo cache, …).
     */
    class Read(
        /**
         * True when the whole page was read again because a change could not be applied to it (or
         * there was no page yet). Then anything may be different.
         */
        val whole: Boolean,
        /**
         * The lines read, oldest first: the page with [whole], otherwise the lines that came after
         * the page before (also with [timed]).
         */
        val fresh: List<LogEntry>,
        /** True when a trim removed lines since the last reload. */
        val trimmed: Boolean,
        /** True when a command's line may be gone: after a trim, or when a removed line was a command or was not on the page. */
        val commandGone: Boolean,
        /** True when the whole page was read again only because a minute passed ([FULL_RELOAD_MS]). */
        val timed: Boolean = false,
    )

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    /** The lines shown, oldest first: the lines behind [rows]. */
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val _filterCount = MutableStateFlow(0)

    /** How many lines the filter ([only]) lets through, shown on its bar. */
    val filterCount: StateFlow<Int> = _filterCount.asStateFlow()

    private val _rows = MutableStateFlow<List<LogRow>>(emptyList())

    /** What the log list shows: entries (with dimming and gutter) and day separators. */
    val rows: StateFlow<List<LogRow>> = _rows.asStateFlow()

    private val _rowsLoaded = MutableStateFlow(false)

    /**
     * False until a reload has set [rows], so an empty list then means an empty log, not "not read
     * yet": before the first reload, and from [dropUpTo] to the next.
     */
    val rowsLoaded: StateFlow<Boolean> = _rowsLoaded.asStateFlow()

    /**
     * The marks ([seenUpTo], [unreadUntil]) that the rows were last built with, or null before the
     * first build. It is set before the rows are built, so a reload under way that read older marks
     * is seen here as not having the new ones.
     */
    @Volatile
    var rowsMarks: Pair<Long, Long>? = null
        private set

    /** What the work after the rows ([reload]'s `afterShown`) did not finish applying ([owed]). Only the reload collector touches it. */
    private var owedRead: Read? = null

    /** How many log lines are loaded. It grows as the user scrolls up (older lines load on demand). */
    @Volatile
    private var pageLimit = PAGE

    /** Every visible line loaded, before the private-space filter. The next reload builds on this. */
    @Volatile
    private var loaded: List<LogEntry> = emptyList()

    /** The newest line loaded so far. It is kept even if the page becomes empty (a pause records it as seen). */
    @Volatile
    private var newestLoaded: Long? = null
    private var loadedStructure = -1L
    private var loadedTrimmed = -1L

    /** The last removal ([LogRepository.removedSeq]) already dropped from the page. */
    private var removedSeen = 0L

    /** The last change in place ([LogRepository.updatedSeq]) already read again. */
    private var updatedSeen = 0L

    /** True when no visible line is older than the page's first line, so scrolling back has nothing more to read. */
    private var logStart = false
    private var fullAt = 0L

    /** The lines of `log only …` as last shown, with the filter and the page size they were found for. */
    private var filtered: List<LogEntry> = emptyList()
    private var filteredFor: LogFilter? = null
    private var filteredLimit = 0

    /** The locked private spaces when [filtered] was made. */
    private var filteredLocks: Set<Long> = emptySet()

    /** The newest line loaded so far, with or without a filter. It is kept even if the page becomes empty. */
    val newest: Long? get() = newestLoaded

    /** The user scrolled back to the page's first line. The page grows by one step, up to its cap. */
    fun loadOlder() {
        // With `log only …`, the screen shows the filter's page, not the log's.
        val shown = if (only.value == null) loaded.size else _entries.value.size
        if (shown < pageLimit || pageLimit >= MAX_PAGE) return
        pageLimit += PAGE
        requestReload()
    }

    /** Called when the user is back at the newest lines after scrolling up. The page shrinks again, so each reload stays small. */
    fun atNewest() {
        if (pageLimit <= PAGE) return
        pageLimit = PAGE
        requestReload()
    }

    /**
     * Takes the lines up to [id] off the screen at once, as a fresh screen hides them
     * ([LogRepository.hideScreen]: every line up to its mark, starred ones too). Home calls it on the
     * main thread when it is shown again, before the hide is written and the page read again.
     * - Under `log only …` nothing changes: the filter's lines come from the whole log, hidden lines too.
     * - [rowsLoaded] is false until the next reload, so the empty log's hint doesn't show meanwhile.
     * Only [entries], [rows] and [rowsLoaded] change: the next reload reads the whole page, because
     * the hide changes the log's structure. True when lines left the screen.
     */
    fun dropUpTo(id: Long): Boolean {
        if (only.value != null) return false
        val shown = _entries.value
        if (shown.none { it.id <= id }) return false
        val kept = shown.filter { it.id > id }
        _entries.value = kept
        _rowsLoaded.value = false
        _rows.value = buildRows(kept, seenUpTo(), unreadUntil())
        return true
    }

    /**
     * Makes the page long enough to hold the line [id] and a few lines before it, up to the cap.
     * The next reload reads them. The caller asks for that reload.
     */
    fun reach(id: Long) {
        pageLimit = maxOf(pageLimit, (logs.countFrom(id) + 20).coerceAtMost(MAX_PAGE))
    }

    /**
     * Brings the page up to date.
     * - Usually only the lines after the newest shown line are read.
     * - An older page is read as the user scrolls back.
     * - Removed lines (a swipe, a launch line moved down) are dropped from the page.
     * - The whole page is read again: after a change in its middle (a restore, a hide, a clear),
     *   when a trim reached into it, and once a minute (another process may have changed something
     *   this one didn't see) ([pageReading]).
     *
     * [afterRead] runs once the page is read, before it shows: the view model reads there what the
     * screen shows with the rows. [afterShown] runs once the rows are published: the view model
     * reads there what only completion and the widgets use. Each reads only when something it
     * follows could have changed. If [afterShown] doesn't finish (a newer change came in), the next
     * reload hands it this reload's [Read] too ([owed]).
     *
     * Everything runs on one background thread. A trip back to the main thread between the reads
     * would wait for its current work (a frame while typing or scrolling), so new lines would show
     * later. Only the view model's reload collector calls this, so only it writes the fields kept
     * here (the page's size apart). The main thread reads the page from [rows].
     */
    suspend fun reload(
        afterRead: suspend (Read) -> Unit,
        afterShown: suspend (Read) -> Unit = {},
    ): Unit = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // Home keeps the log at its size limit as it grows: one trim every few hundred lines written.
        logs.trimIfOverdue()
        val structure = logs.structure
        val trimmed = logs.trimmedUpTo
        // Removals up to this number are applied below. A later one may be seen twice, which is harmless.
        val removedSeq = logs.removedSeq
        val removed = logs.removedSince(removedSeen)
        val gone = removed ?: emptySet()
        // The same for lines changed in place.
        val updatedSeq = logs.updatedSeq
        val updated = logs.updatedSince(updatedSeen)
        val changed = updated ?: emptySet()
        // Use one limit for this reload. loadOlder may raise pageLimit while this reload is suspended.
        // The reload it requests must then read the longer page, not find this one already at that size.
        val limit = pageLimit
        val newest = loaded.lastOrNull()?.id
        // Trims keep starred lines. So the oldest non-starred line tells whether a trim reached into the page.
        val oldest = loaded.firstOrNull { !it.starred }?.id ?: Long.MAX_VALUE
        val reading = pageReading(
            hasPage = newest != null,
            sameStructure = structure == loadedStructure,
            trimReached = trimmed >= oldest,
            removalsKnown = removed != null,
            updatesKnown = updated != null,
            sinceWhole = now - fullAt,
        )
        val incremental = reading == PageReading.NEW_LINES
        val fresh = if (incremental && newest != null) logs.recent(limit, afterId = newest) else logs.recent(limit)
        // Lines changed in place since the last reload, read again once for the page and for `log only …`.
        val again: Map<Long, LogEntry> =
            if (incremental && changed.isNotEmpty()) logs.byIds(changed)
                .associateBy { it.id } else emptyMap()
        val page = if (!incremental) LogPage(fresh, atStart = fresh.size < limit) else {
            mergePage(
                LogPage(loaded, logStart),
                gone,
                again,
                fresh,
                limit
            ) { count, beforeId -> logs.recent(count, beforeId = beforeId) }
        }
        val all = page.lines
        val read = readOf(
            reading,
            fresh,
            newest,
            trimmed = trimmed != loadedTrimmed,
            commandGone = trimmed != loadedTrimmed || mayRemoveCommand(loaded, gone),
        )
        afterRead(read)
        // A locked private space hides its apps. Its lines (launches, notifications) are hidden too.
        val locked = locks()
        val list = if (locked.isEmpty()) all else all.filterNot { it.ctx.userSerial in locked }
        // `log only …` takes its lines from the whole log, not just this page (which may have none of them).
        // - New lines are checked as they arrive, and lines changed in place are patched ([patchFilterPage]).
        // - The whole log is searched when the whole page is read, when the filter or the page size
        //   changed, after a line was removed, when a private space was locked or unlocked, and when a
        //   changed line now matches but wasn't shown ([canPatchFilter]).
        val filter = only.value
        var count = _filterCount.value
        val patched = filter?.takeIf {
            canPatchFilter(
                it,
                incremental,
                filteredFor,
                limit,
                filteredLimit,
                gone.isNotEmpty(),
                locked,
                filteredLocks
            )
        }?.let { f ->
            patchFilterPage(FilterPage(filtered, count), again, fresh, limit, locked, f::matches)
        }
        val shown = when {
            filter == null -> list
            patched != null -> patched.lines.also { count = patched.count }
            else -> {
                val job = currentCoroutineContext()[kotlinx.coroutines.Job]
                val found = logs.search(
                    emptyList(),
                    limit = limit,
                    skip = if (locked.isEmpty()) null else { e -> e.ctx.userSerial in locked },
                    match = filter::matches,
                    narrowBy = filter.sql,
                    keepGoing = { job?.isActive != false },
                )
                count = found.total
                found.entries.asReversed()
            }
        }
        // Update the state only now, after the last read. If the reload is canceled earlier (a newer
        // change came in), the next reload reads these lines again, and [afterRead] gets them again.
        if (!incremental) {
            loadedStructure = structure
            fullAt = now
        }
        loadedTrimmed = trimmed
        removedSeen = removedSeq
        updatedSeen = updatedSeq
        logStart = page.atStart
        loaded = all
        // The newest line, with or without a filter. A pause records it as seen. It is kept when the
        // page is empty (`fresh`, `clear`), because those lines were seen before they were hidden.
        all.lastOrNull()?.let { newestLoaded = it.id }
        filtered = if (filter == null) emptyList() else shown
        filteredFor = filter
        filteredLimit = limit
        filteredLocks = locked
        // From here on the state says these lines are read. The work after the rows owes them until it ends.
        val after = owed(owedRead, read)
        owedRead = after
        _filterCount.value = count
        _entries.value = shown
        _rows.value = withContext(Dispatchers.Default) {
            val marks = seenUpTo() to unreadUntil()
            rowsMarks = marks
            buildRows(shown, marks.first, marks.second)
        }
        _rowsLoaded.value = true
        afterShown(after)
        owedRead = null
    }
}
