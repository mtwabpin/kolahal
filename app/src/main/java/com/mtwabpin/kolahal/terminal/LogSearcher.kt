package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.commands.LogFilter
import androidx.compose.runtime.snapshotFlow
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.terminal.TerminalViewModel.LogSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** `/battery low` at the prompt searches the log for those words. Returns null when the line isn't a search. */
internal fun searchWords(text: String): List<String>? =
    if (!text.startsWith("/")) null else text.substring(1).split(Regex("\\s+")).filter { it.isNotEmpty() }

/**
 * The log search: `/words` at the prompt. It follows the prompt and the log, and puts what it
 * finds in [search]. The view model holds one as `searcher` and starts it from its init.
 * - [reading]: true while home is shown. A search runs only then.
 * - [locks]: the serials of private spaces that are locked. Their lines are left out.
 * - [prompt]: the prompt's text, read inside a snapshot flow, so each change to it is seen.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LogSearcher(
    private val scope: CoroutineScope,
    private val logs: LogRepository,
    private val reading: StateFlow<Boolean>,
    private val locks: Flow<Set<Long>>,
    private val prompt: () -> String,
) {
    private val _search = MutableStateFlow<LogSearch?>(null)
    /** The log search while the prompt starts with `/`. Null otherwise. */
    val search: StateFlow<LogSearch?> = _search.asStateFlow()

    /** The log search that follows the prompt. Started by the view model's init, at its end. */
    fun start() {
        scope.launch {
            // - Wait for typing to settle before a search. Leaving search happens at once.
            // - A log change runs the search again, while home is shown. A search left in the prompt
            //   runs again once on return.
            // - Locking the private space hides its lines from the results too, as from the log.
            // - Lines a script changes in place (line.set) are patched into the results, without
            //   searching the whole log again ([patchSearch]).
            // What the last whole search was for (its words, the log, the locks), and the last change
            // in place it covers.
            var searchedFor: Triple<List<String>?, Long, Set<Long>>? = null
            var updatesSeen = 0L
            reading.flatMapLatest { on ->
                if (!on) emptyFlow()
                else combine(snapshotFlow { searchWords(prompt()) }.distinctUntilChanged(), logs.linesVersion, locks, logs.version) { words, lines, locked, _ ->
                    Triple(words, lines, locked)
                }
            }
                .debounce { (words, _, _) -> if (words.isNullOrEmpty()) 0L else 120L }
                .collectLatest { key ->
                    val (words, _, locked) = key
                    // A failed search keeps the previous results instead of crashing home.
                    try {
                        val current = _search.value
                        if (words != null && current != null && key == searchedFor) {
                            // Nothing changed but lines in place: patch them in, unless that needs a whole search.
                            val patched = withContext(Dispatchers.IO) { patchSearch(current, locked, updatesSeen) }
                            if (patched != null) {
                                _search.value = patched.first
                                updatesSeen = patched.second
                                return@collectLatest
                            }
                        }
                        // Changes in place from here on come after this search: the next patch reads them.
                        val seq = logs.updatedSeq
                        _search.value = when {
                            words == null -> null
                            else -> {
                                // `@xkcd`, `@feeds`: only lines from these sources (any of them).
                                val (at, plain) = words.partition { it.startsWith("@") }
                                val sources = at.mapNotNull(LogFilter::parse)
                                if (plain.isEmpty() && sources.isEmpty()) LogSearch(emptyList(), emptyList(), 0, 0)
                                else withContext(Dispatchers.IO) {
                                    val job = currentCoroutineContext()[kotlinx.coroutines.Job]
                                    val found = logs.search(
                                        plain,
                                        skip = if (locked.isEmpty()) null else { e -> e.ctx.userSerial in locked },
                                        match = if (sources.isEmpty()) null else { e -> sources.any { it.matches(e) } },
                                        narrowBy = LogFilter.sqlOfAny(sources),
                                        keepGoing = { job?.isActive != false },
                                    )
                                    LogSearch(plain, buildRows(found.entries.asReversed(), trim = false), found.total, found.searched, sources)
                                }
                            }
                        }
                        searchedFor = key
                        updatesSeen = seq
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        android.util.Log.w("Kolahal", "log search failed", e)
                    }
                }
        }
    }

    /**
     * [current] with the lines changed in place since change [since] read again (a script's
     * `line.set`), and the latest change number. A line that still matches is replaced where it is,
     * and one that no longer does goes. Null when the whole log must be searched instead: the
     * changes are too old to know, or a changed line now matches but isn't among the results.
     */
    private fun patchSearch(current: LogSearch, locked: Set<Long>, since: Long): Pair<LogSearch, Long>? {
        val seq = logs.updatedSeq
        val ids = logs.updatedSince(since) ?: return null
        if (ids.isEmpty()) return current to seq
        val changed = logs.byIds(ids).associateBy { it.id }
        // The results, oldest first, as buildRows takes them.
        val shown = current.rows.mapNotNull { (it as? LogRow.Entry)?.entry }
        val shownIds = shown.mapTo(HashSet()) { it.id }
        fun matches(e: LogEntry) = e.ctx.userSerial !in locked &&
            (current.sources.isEmpty() || current.sources.any { it.matches(e) }) && logs.matchesWords(e, current.words)
        if (changed.values.any { it.id !in shownIds && matches(it) }) return null
        if (changed.keys.none { it in shownIds }) return current to seq
        var total = current.total
        val entries = shown.mapNotNull { e ->
            val now = changed[e.id] ?: return@mapNotNull e
            if (matches(now)) now else null.also { total-- }
        }
        return current.copy(rows = buildRows(entries, trim = false), total = total) to seq
    }
}
