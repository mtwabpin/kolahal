package com.mtwabpin.kolahal.terminal

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.Profile
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.CustomThemes
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.script.Files
import com.mtwabpin.kolahal.script.HostApi
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptFolder
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.status.DeviceStatus
import com.mtwabpin.kolahal.status.Status
import com.mtwabpin.kolahal.ui.HeaderTarget
import com.mtwabpin.kolahal.ui.KeyBarKey
import com.mtwabpin.kolahal.ui.TermFonts
import com.mtwabpin.kolahal.unlock.UnlockCounter
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE = 400
private const val MAX_PAGE = 5000

/** Commands whose effect shows in the todo, note, reminder and feed caches. */
private val PRODUCTIVITY_COMMANDS = CommandCatalog.PRODUCTIVITY + setOf("feed", "feeds")

/** The whole page is read again at least this often, catching trims by other processes. */
private const val FULL_RELOAD_MS = 60_000L

/** `/battery low` at the prompt searches the log for those words; null when the line isn't a search. */
internal fun searchWords(text: String): List<String>? =
    if (!text.startsWith("/")) null else text.substring(1).split(Regex("\\s+")).filter { it.isNotEmpty() }

@OptIn(kotlinx.coroutines.FlowPreview::class)
class TerminalViewModel(app: Application, private val state: SavedStateHandle) : AndroidViewModel(app), TerminalUi {
    private val context: Context get() = getApplication()

    /** Reload requests from anywhere (log changes, paging, resume) funnel into one latest-wins pipeline. */
    private val reloadRequests = MutableStateFlow(0L)
    private fun requestReload() = reloadRequests.update { it + 1 }

    /** Serials of private spaces that are locked. */
    private fun lockedPrivate(profiles: List<Profile>): Set<Long> =
        profiles.filter { it.kind == ProfileKind.PRIVATE && it.quiet }.mapTo(HashSet()) { it.serial }
    private val logs = LogRepository.get(app)
    private val appsRepo = AppRepository.get(app)
    private val scripts = ScriptRepository.get(app)
    private val unlockCounter = UnlockCounter.get(app)
    private val processor = CommandProcessor(app, this)
    private val appPrefs = AppPrefs.get(app)
    private val completer = Completer(app, object : Completer.Data {
        override val history get() = this@TerminalViewModel.history
        override val scripts get() = scriptList.value
        override val commandNames get() = _commandNames.value
        override val scriptUsages get() = this@TerminalViewModel.scriptUsages
        override val scriptDescriptions get() = this@TerminalViewModel.scriptDescriptions
        override val statusNames get() = _statuses.value.keys
        override val libraryNames get() = this@TerminalViewModel.libraryNames
        override val sharedNames get() = scriptActions.sharedScripts.value.entries.map { it.name }
        override val feeds get() = feedCache
        override val todos get() = todoCache
        override val notes get() = noteCache
        override val reminders get() = reminderCache
        override val logSources get() = this@TerminalViewModel.logSources
    })

    /** Scripts, refreshed off the main thread whenever the log changes (every script action logs). */
    val scriptList: StateFlow<List<Script>>
        field = MutableStateFlow<List<Script>>(emptyList())

    private val actionsHost = object : ScriptActions.Host {
        override fun io(block: suspend () -> Unit) = this@TerminalViewModel.io(block)
        override fun fileOp(label: String, block: suspend () -> Unit) = this@TerminalViewModel.fileOp(label, block)
        override fun notice(text: String, error: Boolean) = this@TerminalViewModel.notice(text, error)
        override fun show(text: String) = this@TerminalViewModel.show(text)
        override fun emit(event: UiEvent) { _events.tryEmit(event) }
        override fun openEditor(script: Script) = this@TerminalViewModel.openEditor(script)
        override fun setCommandScripts(list: List<Script>) = this@TerminalViewModel.setCommandScripts(list)
    }

    /** What the script screens do to scripts: their switches, timers, keys, options, files and installs. */
    val scriptActions = ScriptActions(app, state, viewModelScope, scriptList, actionsHost)

    /** Backup files, and the folder for automatic ones. */
    val backupActions = BackupActions(app, scriptActions, actionsHost)

    private val _commandNames = MutableStateFlow<List<String>>(emptyList())
    /** Names of `@command` scripts; part of the suggestion inputs. */
    val commandNames: StateFlow<List<String>> = _commandNames.asStateFlow()

    /** Usage lines of the command scripts that have a known one (see [ScriptMeta.usage]), set with [commandNames]. */
    @Volatile private var scriptUsages: Map<String, String> = emptyMap()

    /** `@desc` of the command scripts that have one, for their suggestion rows; set with [commandNames]. */
    @Volatile private var scriptDescriptions: Map<String, String> = emptyMap()

    /** [scriptUsages] and the built-in commands' usage lines: what instant launch checks a line against, on every key. */
    @Volatile private var commandUsages: Map<String, String> = scriptUsages + CommandCatalog.COMMAND_USAGES

    /** The bundled library's script names, read once (`script install` completes them). */
    private val libraryNames: List<String> by lazy { ScriptLibrary(context).entries.map { it.name } }

    private fun setCommandScripts(list: List<Script>) {
        val commands = list.filter { it.enabled && ScriptMeta.isCommand(it.code) }
        scriptUsages = commands.mapNotNull { s -> ScriptMeta.usage(s.code, s.name)?.let { s.name to it } }.toMap()
        commandUsages = scriptUsages + CommandCatalog.COMMAND_USAGES
        scriptDescriptions = commands.mapNotNull { s -> ScriptMeta.description(s.code)?.let { s.name to it } }.toMap()
        _commandNames.value = commands.map { it.name }
    }

    /** Bumps when aliases, hidden apps or labels change. */
    val appPrefsVersion: StateFlow<Int> get() = appPrefs.version

    /** Every stored `status(text)` line, keyed by script name. */
    private val _statuses = MutableStateFlow<Map<String, String>>(emptyMap())
    /**
     * Header lines pinned by scripts with `status(text)`: only those of scripts that are switched on and
     * allowed a header line, whichever way they were switched off.
     */
    val statuses: StateFlow<Map<String, String>> = combine(_statuses, scriptList) { lines, list ->
        val shown = list.filter { it.enabled && it.grants.allows(ScriptAccess.HEADER) }.mapTo(HashSet()) { it.name }
        lines.filterKeys { it in shown }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val _filter = MutableStateFlow(logs.filter?.let(LogFilter::parse))
    /** `log only …`: the log shows only these lines until ✕, Back, `log all` or another command. */
    val filter: StateFlow<LogFilter?> = _filter.asStateFlow()

    private val _filterCount = MutableStateFlow(0)
    /** How many lines [filter] lets through, for its bar. */
    val filterCount: StateFlow<Int> = _filterCount.asStateFlow()

    /** Where the log's lines came from, newest first: `/@` and `log only` suggest them. */
    @Volatile private var logSources: List<String> = emptyList()

    /** The log column's width on screen, as last laid out ([onLogWidth]); `fetch` lays its lines out to fit it. */
    @Volatile private var logWidth: Int? = null

    fun onLogWidth(px: Int) {
        if (px > 0) logWidth = px
    }

    override fun logWidthPx(): Int? = logWidth

    override fun filterLog(filter: LogFilter?) {
        if (_filter.value == filter) return
        _filter.value = filter
        // Kept until cleared, through leaving home and a restart.
        logs.filter = filter?.let { if (it is LogFilter.OfKind) it.word else it.label }
        requestReload()
    }

    private val _headerCollapsed = MutableStateFlow(false)
    /** header.autohide: the header shrinks to one line while the log is in use. */
    val headerCollapsed: StateFlow<Boolean> = _headerCollapsed.asStateFlow()

    private val _headerHidden = MutableStateFlow(false)
    /** Scrolling back through older output hides the header entirely (header.autohide). */
    val headerHidden: StateFlow<Boolean> = _headerHidden.asStateFlow()

    fun collapseHeader() {
        if (!config[Keys.HEADER_AUTOHIDE]) return
        _headerCollapsed.value = true
        _headerHidden.value = false
    }

    /** Scrolling toward older lines: no header at all, the log gets the whole screen. */
    fun hideHeader() {
        if (!config[Keys.HEADER_AUTOHIDE]) return
        _headerCollapsed.value = true
        _headerHidden.value = true
    }

    fun expandHeader() {
        _headerCollapsed.value = false
        _headerHidden.value = false
    }

    private val _screen = MutableStateFlow(Screen.TERMINAL)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _rows = MutableStateFlow<List<LogRow>>(emptyList())
    /** What the log list shows: entries (with dimming/gutter) and day separators. */
    val rows: StateFlow<List<LogRow>> = _rows.asStateFlow()

    val apps: StateFlow<List<AppEntry>> = appsRepo.apps
    val profiles: StateFlow<List<Profile>> = appsRepo.profiles
    val unlocksToday: StateFlow<Int> = unlockCounter.today
    val status: StateFlow<Status> = DeviceStatus.get(app).status
    val config: Config = Config.get(app)
    private val mediaRepo = com.mtwabpin.kolahal.control.MediaRepository.get(app)
    /** The media session for the header (null without notification access or a session; [com.mtwabpin.kolahal.control.NowPlaying.showInHeader] hides an idle one). */
    val nowPlaying: StateFlow<com.mtwabpin.kolahal.control.NowPlaying?> = mediaRepo.nowPlaying

    /** Header now-playing line: tap toggles play/pause, long-press (or ⏭) skips. */
    fun mediaToggle() = io { mediaRepo.send(com.mtwabpin.kolahal.control.MediaRepository.Action.TOGGLE) }
    fun mediaNext() = io { mediaRepo.send(com.mtwabpin.kolahal.control.MediaRepository.Action.NEXT) }

    var input by mutableStateOf(TextFieldValue(""))
        private set

    /** What the line in the prompt would change ([LivePreview]). */
    val livePreview: LivePreview?
        get() = LivePreview.of(input.text, config) { TermFonts.customFile(getApplication()) != null }

    /** A preview whose line was just entered, until its command has run: the old look would show for a moment. */
    private var entered by mutableStateOf<LivePreview?>(null)

    /** How to look for now, if not as the settings say. On home alone: other screens show what's in use. */
    val previewed: LivePreview? get() = entered ?: livePreview?.takeIf { _screen.value == Screen.TERMINAL }

    private val _editing = MutableStateFlow<Script?>(null)
    val editing: StateFlow<Script?> = _editing.asStateFlow()

    private val _editLine = MutableStateFlow<Int?>(null)
    /** Line to jump to (and flash) when the editor opens from an error. */
    val editLine: StateFlow<Int?> = _editLine.asStateFlow()

    fun openEditorAt(script: Script, line: Int?) {
        // Otherwise the screen underneath shows its last message again when the editor closes.
        _notice.value = null
        _editLine.value = line
        _editing.value = script
    }

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events

    private var history: List<String> = emptyList()
    private var historyIndex = -1
    private var draft = ""

    /** launcher.run(cmd) from scripts the user started (see HostApi.runCommand). */
    private val runCommandReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            // Checked again here, where it runs: a sensitive command never runs from a script.
            intent.getStringExtra(HostApi.EXTRA_COMMAND)?.takeIf { !InstantRun.isSensitive(it) }?.let { runCommand(it, byScript = true) }
        }
    }

    override fun onCleared() {
        runCatching { context.unregisterReceiver(runCommandReceiver) }
    }

    init {
        mediaRepo.start()
        androidx.core.content.ContextCompat.registerReceiver(
            context, runCommandReceiver, android.content.IntentFilter(HostApi.ACTION_RUN_COMMAND),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        viewModelScope.launch {
            // Bursts of changes (a script logging many lines, another process) collapse into one reload.
            // Locking or unlocking the private space shows or hides its lines.
            launch { appsRepo.profiles.map(::lockedPrivate).distinctUntilChanged().drop(1).collect { requestReload() } }
            combine(logs.version, reloadRequests) { a, b -> a to b }.debounce(40.milliseconds).collectLatest {
                // A failed reload keeps the previous rows instead of crashing home. Not logged:
                // writing to the log would trigger the next reload.
                try {
                    reload()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    android.util.Log.w("Kolahal", "log reload failed", e)
                }
            }
        }
    }

    /** How many log lines are loaded; grows as the user scrolls up (older lines load on demand). */
    @Volatile private var pageLimit = PAGE

    fun loadOlder() {
        // With `log only …`, what shows is the filter's page, not the log's.
        val shown = if (_filter.value == null) loaded.size else _entries.value.size
        if (shown < pageLimit || pageLimit >= MAX_PAGE) return
        pageLimit += PAGE
        requestReload()
    }

    /** Every visible line loaded, before the private-space filter; what the next reload builds on. */
    private var loaded: List<LogEntry> = emptyList()
    /** The newest line loaded so far, kept across an emptied page ([onPause] records it as seen). */
    @Volatile private var newestLoaded: Long? = null
    private var loadedStructure = -1L
    private var loadedTrimmed = -1L
    /** The last removal ([LogRepository.removedSeq]) dropped from the page. */
    private var removedSeen = 0L
    /** No visible line is older than the page's first: scrolling back has nothing more to read. */
    private var logStart = false
    private var fullAt = 0L
    private var scriptsPrint: String? = null
    /** `log only …`'s lines as last shown, with the filter and the page size they were found for. */
    private var filtered: List<LogEntry> = emptyList()
    private var filteredFor: LogFilter? = null
    private var filteredLimit = 0

    /**
     * Brings the log up to date. Usually only the lines after the newest one shown are read; an
     * older page is read as the user scrolls back, and lines removed (a swipe, a launch line moved
     * down) are dropped from the page. The whole page is read again after a change in its middle
     * (a restore, a hide, a clear), when a trim reached into it, and once a minute (another process
     * may have changed what this one didn't see). Scripts, history and the todo and feed caches are
     * read only when something they show could have changed.
     */
    private suspend fun reload() {
        val now = System.currentTimeMillis()
        // Home keeps the log at its cap as it grows: a trim every few hundred lines it wrote.
        withContext(Dispatchers.IO) { logs.trimIfOverdue() }
        val structure = logs.structure
        val trimmed = logs.trimmedUpTo
        // The removals up to this number are applied below; a later one may be seen twice, harmlessly.
        val removedSeq = logs.removedSeq
        val removed = logs.removedSince(removedSeen)
        val gone = removed ?: emptySet()
        // One limit for this reload: loadOlder may raise pageLimit while it is suspended, and the
        // reload that asks for must then read the longer page, not find this one already at it.
        val limit = pageLimit
        val newest = loaded.lastOrNull()?.id
        val oldest = loaded.firstOrNull()?.id
        val incremental = newest != null && oldest != null && structure == loadedStructure && trimmed < oldest &&
            removed != null && now - fullAt < FULL_RELOAD_MS
        val fresh = withContext(Dispatchers.IO) { if (incremental) logs.recent(limit, afterId = newest) else logs.recent(limit) }
        var start = logStart
        val all = if (!incremental) {
            start = fresh.size < limit
            fresh
        } else {
            val kept = if (gone.isEmpty()) loaded else loaded.filterNot { it.id in gone }
            val have = kept.size + fresh.size
            // Scrolled back (or a search result shown): the lines before the page, up to the new limit.
            val before = (kept.firstOrNull() ?: fresh.firstOrNull())?.id
            val older = if (have >= limit || start || before == null) emptyList() else {
                withContext(Dispatchers.IO) { logs.recent(limit - have, beforeId = before) }.also { if (it.size < limit - have) start = true }
            }
            val page = older + kept + fresh
            // The page shrank ([atNewest]) or grew past its limit: what's cut is older than the page now.
            if (page.size > limit) start = false
            page.takeLast(limit)
        }
        val print = withContext(Dispatchers.IO) { scripts.fingerprint() }
        if (print != scriptsPrint) {
            val latest = withContext(Dispatchers.IO) { scripts.all() }
            scriptList.value = latest
            withContext(Dispatchers.Default) { setCommandScripts(latest) }
            scriptsPrint = print
        }
        _statuses.value = withContext(Dispatchers.IO) { KvStore(context, HostApi.STATUS_SCOPE).all() }
        if (!incremental || trimmed != loadedTrimmed) _hiddenLines.value = withContext(Dispatchers.IO) { logs.hiddenCount() }
        if (!incremental || fresh.any { it.kind == LogKind.COMMAND }) history = withContext(Dispatchers.IO) { logs.commandHistory() }
        if (!incremental || fresh.any(::touchesProductivity)) refreshProductivity()
        if (!incremental || fresh.any { it.source != null && it.source !in logSources }) logSources = withContext(Dispatchers.IO) { logs.sources() }
        // A locked private space hides its apps; its lines (launches, notifications) hide with it.
        val locked = lockedPrivate(appsRepo.profiles.value)
        val list = if (locked.isEmpty()) all else all.filterNot { it.ctx.userSerial in locked }
        // `log only …`: its lines from the whole log, not just this page (which may hold none of
        // them). New lines are checked as they come; the log is searched whole when the page is read
        // whole, when the filter or the page size changed, and after a line was removed.
        val filter = _filter.value
        var count = _filterCount.value
        val shown = when {
            filter == null -> list
            incremental && filter == filteredFor && limit <= filteredLimit && gone.isEmpty() -> {
                val more = (if (locked.isEmpty()) fresh else fresh.filterNot { it.ctx.userSerial in locked }).filter(filter::matches)
                count += more.size
                (filtered + more).takeLast(limit)
            }
            else -> {
                val job = currentCoroutineContext()[kotlinx.coroutines.Job]
                val found = withContext(Dispatchers.IO) {
                    logs.search(
                        emptyList(), limit = limit, skip = if (locked.isEmpty()) null else { e -> e.ctx.userSerial in locked },
                        match = filter::matches, keepGoing = { job?.isActive != false },
                    )
                }
                count = found.total
                found.entries.asReversed()
            }
        }
        // Only now, past the last read: cancelled earlier (a newer change came in), the next reload
        // reads these lines again and refreshes history, todos and the hidden count for them.
        if (!incremental) {
            loadedStructure = structure
            fullAt = now
        }
        loadedTrimmed = trimmed
        removedSeen = removedSeq
        logStart = start
        loaded = all
        // The newest line there is, filter or not: what a pause records as seen. Kept when the page
        // is empty (`fresh`, `clear`): those lines were seen before they were hidden.
        all.lastOrNull()?.let { newestLoaded = it.id }
        filtered = if (filter == null) emptyList() else shown
        filteredFor = filter
        filteredLimit = limit
        _filterCount.value = count
        _entries.value = shown
        _rows.value = withContext(Dispatchers.Default) { buildRows(shown, seenUpTo, unreadUntil) }
    }

    /** A line after which the todo, note, reminder or feed caches may be out of date. */
    private fun touchesProductivity(e: LogEntry): Boolean {
        // `notes rm 2` prints no numbered line, but the numbers after it moved.
        if (e.kind == LogKind.COMMAND && InstantRun.canonical(e.text.substringBefore(' ')) in PRODUCTIVITY_COMMANDS) return true
        val k = e.ctx.values.keys
        return LogContext.TODO in k || LogContext.NOTE in k || LogContext.REMINDER in k ||
            com.mtwabpin.kolahal.feeds.FeedWorker.CTX_FEED in k || com.mtwabpin.kolahal.feeds.FeedWorker.CTX_ITEM in k ||
            e.source == "remind"
    }

    /** Back at the newest lines after scrolling up: the page shrinks again, so each reload stays small. */
    fun atNewest() {
        if (pageLimit <= PAGE) return
        pageLimit = PAGE
        requestReload()
    }

    private var rowsZone = java.time.ZoneId.systemDefault()

    /** The phone's time zone changed ([told]) or may have: the log and a search build their rows again, with each line's day in the new one. */
    fun onZoneChanged(told: Boolean) {
        val now = java.time.ZoneId.systemDefault()
        if (told || now != rowsZone) logs.touch()
        rowsZone = now
    }

    private var rowsDay: java.time.LocalDate? = null

    /** A new day, at midnight with the log on screen: the rows are built again for their day dividers. */
    fun onDayChanged(today: java.time.LocalDate) {
        if (rowsDay != null && today != rowsDay) logs.touch()
        rowsDay = today
    }

    /** Script lines in (seenUpTo, unreadUntil] arrived while the user was away; later ones were seen live. */
    @Volatile private var seenUpTo = Long.MAX_VALUE
    @Volatile private var unreadUntil = Long.MAX_VALUE
    @Volatile private var pausedAt = 0L

    private val _hiddenLines = MutableStateFlow(0)
    /** Lines a fresh-screen hide keeps out of view; the log shows a tap-to-restore line for them. */
    val hiddenLines: StateFlow<Int> = _hiddenLines.asStateFlow()

    fun showHidden() = io { logs.unhide() }

    // ---- log search: `/words` at the prompt -------------------------------------------------

    /** What `/words` found: [rows] oldest first, as the log lists them; no [words] yet shows a hint. */
    data class LogSearch(val words: List<String>, val rows: List<LogRow>, val total: Int, val searched: Int, val sources: List<LogFilter> = emptyList()) {
        /** Nothing typed yet (or only `@`): a hint shows instead of results. */
        val isEmpty: Boolean get() = words.isEmpty() && sources.isEmpty()
    }

    private val _search = MutableStateFlow<LogSearch?>(null)
    /** The log search while the prompt starts with `/`; null otherwise. */
    val search: StateFlow<LogSearch?> = _search.asStateFlow()

    private val _jumpTo = MutableStateFlow<Long?>(null)
    /** A line to scroll the log to once it is loaded (a search result tapped). */
    val jumpTo: StateFlow<Long?> = _jumpTo.asStateFlow()

    init {
        viewModelScope.launch {
            // Typing settles before a search; leaving it is at once. The log changing searches again.
            // Locking the private space hides its lines from the results too, as from the log.
            val locks = appsRepo.profiles.map(::lockedPrivate).distinctUntilChanged()
            combine(androidx.compose.runtime.snapshotFlow { searchWords(input.text) }.distinctUntilChanged(), logs.version, locks) { words, _, locked -> words to locked }
                .debounce { (words, _) -> if (words.isNullOrEmpty()) 0L else 120L }
                .collectLatest { (words, locked) ->
                    // A failed search keeps the previous results instead of crashing home.
                    try {
                        _search.value = when {
                            words == null -> null
                            else -> {
                                // `@xkcd`, `@feeds`: only lines from there (any of them).
                                val (at, plain) = words.partition { it.startsWith("@") }
                                val sources = at.mapNotNull(LogFilter::parse)
                                if (plain.isEmpty() && sources.isEmpty()) LogSearch(emptyList(), emptyList(), 0, 0)
                                else withContext(Dispatchers.IO) {
                                    val job = currentCoroutineContext()[kotlinx.coroutines.Job]
                                    val found = logs.search(
                                        plain,
                                        skip = if (locked.isEmpty()) null else { e -> e.ctx.userSerial in locked },
                                        match = if (sources.isEmpty()) null else { e -> sources.any { it.matches(e) } },
                                        keepGoing = { job?.isActive != false },
                                    )
                                    LogSearch(plain, buildRows(found.entries.asReversed(), trim = false), found.total, found.searched, sources)
                                }
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        android.util.Log.w("Kolahal", "log search failed", e)
                    }
                }
        }
    }

    /** A search result tapped: the search ends and the log shows that line, loading back as far as it is. */
    fun showInLog(entry: LogEntry) {
        setText("")
        _events.tryEmit(UiEvent.HideKeyboard)
        // `log only …` would keep the line out of sight: it ends, as running a command ends it.
        _filter.value?.let { if (!it.matches(entry)) filterLog(null) }
        io {
            if (logs.isHidden(entry.id)) logs.unhide()
            pageLimit = maxOf(pageLimit, (logs.countFrom(entry.id) + 20).coerceAtMost(MAX_PAGE))
            _jumpTo.value = entry.id
            requestReload()
            // A line that never shows up (deleted meanwhile, past the page limit) doesn't stay a pending jump.
            kotlinx.coroutines.delay(3_000.milliseconds)
            if (_jumpTo.value == entry.id) {
                _jumpTo.value = null
                atNewest()
            }
        }
    }

    fun jumpDone() {
        _jumpTo.value = null
    }

    fun onPause() {
        _peek.value = null
        // The newest line, not the last shown: after `fresh` nothing shows, and under `log only`
        // the last shown line can be old; the lines before it were seen all the same.
        newestLoaded?.let { logs.seenUpTo = it }
        pausedAt = System.currentTimeMillis()
    }

    /** Exact-alarm permission at the last resume; null before the first. */
    @Volatile private var exactAlarms: Boolean? = null

    fun onResume() {
        // Allowing exact alarms (`remind exact`) doesn't restart us: re-arm the pending reminders
        // so they stop firing late in Doze.
        val exact = com.mtwabpin.kolahal.productivity.ReminderScheduler.canBeExact(context)
        if (exact && exactAlarms == false) io { com.mtwabpin.kolahal.productivity.ReminderScheduler.rescheduleAll(context) }
        exactAlarms = exact
        io { logs.trimIfDue(config[Keys.LOG_KEEP_DAYS]) }
        // Daily times drift (late runs, a daylight-saving change): anchored again every few hours.
        io { ScriptScheduler.syncAllIfDue(context) }
        // A crash in the script process doesn't restart home: its report is announced here.
        io { runCatching { com.mtwabpin.kolahal.CrashReports.announce(context) } }
        // Coming back from an app is a fresh look at home: show the full header again.
        expandHeader()
        seenUpTo = logs.seenUpTo
        // log.fresh: after a while away, home starts clean. Only what you had already seen is
        // hidden (script lines that arrived meanwhile stay), and nothing is deleted.
        val freshMin = config[Keys.LOG_FRESH]
        val seen = seenUpTo
        if (freshMin > 0 && pausedAt > 0 && System.currentTimeMillis() - pausedAt >= freshMin * 60_000L && seen != Long.MAX_VALUE) {
            io { logs.hideScreen(seen) }
        }
        viewModelScope.launch {
            unreadUntil = withContext(Dispatchers.IO) { logs.maxId() }
            requestReload()
        }
        if (_screen.value == Screen.SETUP || _screen.value == Screen.ONBOARDING) refreshSetup()
        unlockCounter.refresh()
        // No app list reload here: it's kept, and installs, updates, removals, profiles and a new
        // language reload it as they happen ([AppRepository.start]). `restart` reads it afresh.
        mediaRepo.start() // picks up notification access granted while we were away
        io { tipOfTheDay() }
        // keyboard.on-home: back from an app or the lock screen is home shown too. The keyboard
        // only opens for a focused window, which comes just after this ([onWindowFocused]).
        focusOnWindowFocus = true
    }

    private var focusOnWindowFocus = false

    /**
     * `log.tips`: on the first look at home each day, from one to fourteen days after installing, a
     * line about something not used yet ([Tips]); tapping it puts the example in the prompt.
     */
    private fun tipOfTheDay() {
        if (!config[Keys.LOG_TIPS]) return
        val prefs = context.getSharedPreferences("tips", Context.MODE_PRIVATE)
        val today = java.time.LocalDate.now().toString()
        if (prefs.getString("day", null) == today) return
        val installed = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime }.getOrNull() ?: return
        if ((System.currentTimeMillis() - installed) / 86_400_000L !in Tips.DAYS) return
        val shown = prefs.getStringSet("shown", emptySet()).orEmpty()
        val tip = Tips.next(logs.commandHistory(), shown) ?: return
        prefs.edit { putString("day", today).putStringSet("shown", shown + tip.id) }
        // The first one says how they stop.
        val off = if (shown.isEmpty()) " (config set log.tips off to stop them)" else ""
        logs.add(LogKind.INFO, "Tip: ${tip.text} · tap to try it$off", ctx = LogContext.of(LogContext.COMMAND to tip.example.trimEnd() + " "))
    }

    /** Home's window has focus again: after [onResume], keyboard.on-home opens the keyboard on the terminal. */
    fun onWindowFocused() {
        if (!focusOnWindowFocus) return
        focusOnWindowFocus = false
        if (config[Keys.FOCUS_ON_HOME] && (logShown || !widgetsFirst()) && _screen.value == Screen.TERMINAL && _editing.value == null) _events.tryEmit(UiEvent.FocusPrompt)
    }

    // ---- prompt ---------------------------------------------------------------------------

    fun onInputChange(value: TextFieldValue) {
        val old = input.text
        // Cursor moves and IME composition updates must not abandon history navigation.
        if (value.text != old) historyIndex = -1
        input = value
        // Only while typing forward at the end, so backspacing or editing mid-line never fires it.
        // One character at a time (or IME composition): a paste of a whole word is not "typing".
        // A line with arguments may also end in a word the keyboard commits whole, space and all
        // (`torch on `); only a line with nothing left to choose runs then.
        val typed = value.text.length - old.length == 1 || value.composition != null
        if (searchWords(value.text) == null && value.text.length > old.length && value.selection.collapsed && value.selection.end == value.text.length &&
            (typed || value.text.trim().contains(' '))
        ) {
            instantRun(value.text)
        }
    }

    /** launch.instant: launch / run as soon as the input is unambiguous. */
    private fun instantRun(text: String) {
        val target = InstantRun.target(
            text, config[Keys.LAUNCH_INSTANT], config[Keys.LAUNCH_INSTANT_MIN],
            rankedApps = { appsRepo.searchRanked(it) }, appLabel = { it.label },
            commands = commandUsages, aliases = appPrefs.aliases(), scriptCommands = _commandNames.value,
            isOn = { !it.profile.quiet },
        ) ?: return
        setText("")
        collapseHeader()
        _events.tryEmit(UiEvent.ScrollToBottom)
        when (target) {
            is InstantRun.Target.App -> io { processor.launch(target.app) }
            is InstantRun.Target.Command -> io { processor.run(target.line) }
        }
    }

    /** In `launch.instant all` mode a tapped history line runs at once, unless it is sensitive and not a whole line such as `torch on`. */
    private fun runsOnTap(line: String) = config[Keys.LAUNCH_INSTANT] == InstantRun.ALL &&
        // A trailing space marks a prefill ("apps " from help): it is waiting for an argument.
        !line.endsWith(' ') &&
        (InstantRun.canRun(line, commandUsages, appPrefs.aliases(), _commandNames.value) { name ->
            appsRepo.searchRanked(name).firstOrNull()?.second == 0
        } || InstantRun.wholeLine(line, commandUsages, appPrefs.aliases()))

    fun submit() {
        val line = input.text
        // Enter in a log search makes room for the results: they are tapped, not run.
        if (searchWords(line) != null) {
            _events.tryEmit(UiEvent.HideKeyboard)
            return
        }
        entered = livePreview
        input = TextFieldValue("")
        historyIndex = -1
        if (line.isBlank()) return
        collapseHeader()
        io {
            try {
                processor.run(line)
            } finally {
                entered = null
            }
        }
        _events.tryEmit(UiEvent.ScrollToBottom)
    }

    /** Insert [text] at the cursor (for touch keys). */
    fun insert(text: String) {
        val v = input
        val newText = v.text.replaceRange(v.selection.min, v.selection.max, text)
        historyIndex = -1
        input = TextFieldValue(newText, TextRange(v.selection.min + text.length))
    }

    fun historyUp() {
        if (history.isEmpty()) return
        if (historyIndex == -1) draft = input.text
        historyIndex = (historyIndex + 1).coerceAtMost(history.lastIndex)
        recall(history.getOrNull(historyIndex) ?: return)
    }

    fun historyDown() {
        if (historyIndex == -1) return
        historyIndex--
        recall(if (historyIndex == -1) draft else history.getOrNull(historyIndex) ?: draft)
    }

    /** Backspace for the touch key bar (deletes the selection or the char before the cursor). */
    fun deleteBackward() {
        val v = input
        val sel = v.selection
        val (start, end) = if (sel.collapsed) (sel.start - 1).coerceAtLeast(0) to sel.start else sel.min to sel.max
        if (start == end) return
        historyIndex = -1
        input = TextFieldValue(v.text.removeRange(start, end), TextRange(start))
    }

    fun moveCursor(delta: Int) {
        val pos = (input.selection.end + delta).coerceIn(0, input.text.length)
        input = input.copy(selection = TextRange(pos))
    }

    fun onKeyBar(key: KeyBarKey) {
        when (key) {
            KeyBarKey.ESC -> escape()
            KeyBarKey.TAB -> complete()
            KeyBarKey.UP -> historyUp()
            KeyBarKey.DOWN -> historyDown()
            KeyBarKey.LEFT -> moveCursor(-1)
            KeyBarKey.RIGHT -> moveCursor(1)
            KeyBarKey.HOME -> input = input.copy(selection = TextRange(0))
            KeyBarKey.END -> input = input.copy(selection = TextRange(input.text.length))
            KeyBarKey.ENTER -> submit()
            KeyBarKey.KBD -> _events.tryEmit(UiEvent.FocusPrompt)
            KeyBarKey.SPACE -> insert(" ")
            KeyBarKey.BKSP -> deleteBackward()
        }
    }

    fun escape() {
        setText("")
    }

    /** TAB: accept the first suggestion. */
    fun complete() {
        val first = suggestions(input.text).firstOrNull() ?: return
        when (first) {
            is Suggestion.Completion -> pickCompletion(first.replacement)
            is Suggestion.App -> setText(first.app.label)
        }
    }

    /** A completion fills the prompt; in `launch.instant all` a line it leaves with nothing to choose runs (`torch on`). */
    private fun pickCompletion(replacement: String) {
        setText(replacement)
        if (searchWords(replacement) == null && replacement.trim().contains(' ')) instantRun(replacement)
    }

    fun onSuggestion(s: Suggestion) {
        when (s) {
            is Suggestion.App -> {
                setText("")
                io { processor.launch(s.app) }
            }
            is Suggestion.Completion -> pickCompletion(s.replacement)
        }
    }

    /** A new line for the prompt: ↑ and ↓ start again from it. */
    private fun setText(text: String) {
        historyIndex = -1
        recall(text)
    }

    private fun recall(text: String) {
        input = TextFieldValue(text, TextRange(text.length))
    }

    fun suggestions(text: String): List<Suggestion> {
        if (searchWords(text) == null) return completer.suggestions(text)
        // `/@xk`: the sources to search in.
        val token = text.removePrefix("/").substringAfterLast(' ')
        if (!token.startsWith("@")) return emptyList()
        val before = text.dropLast(token.length)
        return LogFilter.suggestions(token, logSources).take(12).map { Suggestion.Completion("@${it.word}", "$before@${it.word} ") }
    }

    // ---- log line interaction -------------------------------------------------------------

    private val lineActions = LogLineActions(context, processor, object : LogLineActions.Host {
        override val entries get() = _entries.value
        override val scripts get() = scriptList.value
        override fun io(block: suspend () -> Unit) = this@TerminalViewModel.io(block)
        override fun setText(text: String) = this@TerminalViewModel.setText(text)
        override fun submit() = this@TerminalViewModel.submit()
        override fun emit(event: UiEvent) { _events.tryEmit(event) }
        override fun collapseHeader() = this@TerminalViewModel.collapseHeader()
        override fun runsOnTap(line: String) = this@TerminalViewModel.runsOnTap(line)
        override fun runCommand(cmd: String) = this@TerminalViewModel.runCommand(cmd)
        override fun openEditorAt(script: Script, line: Int?) = this@TerminalViewModel.openEditorAt(script, line)
        override fun startActivity(intent: Intent) = this@TerminalViewModel.startActivity(intent)
        override fun filterLog(filter: LogFilter?) = this@TerminalViewModel.filterLog(filter)
    })

    /** Single tap: the most likely action for this line. */
    fun onTap(entry: LogEntry) = lineActions.onTap(entry)

    /** Long-press: every action that makes sense for the saved context. Destructive ones come last. */
    fun actionsFor(entry: LogEntry): List<LogAction> = lineActions.actionsFor(entry)

    /** Actions for one app (log lines and grid chips). */
    fun appActions(app: AppEntry): List<LogAction> = lineActions.appActions(app)

    /** Apps referenced by a grid line. */
    fun appsOf(entry: LogEntry): List<AppEntry> = lineActions.appsOf(entry)

    fun launchApp(app: AppEntry) = io { processor.launch(app) }

    /** Keys of favourite apps (listed first, with ★, in the app selector). */
    fun pinnedKeys(): Set<String> = appPrefs.pinned().toSet()

    /** One serial worker: commands and actions run in order, and failures are logged, never crash home. */
    private val serialIo = Dispatchers.IO.limitedParallelism(1)
    private val errors = CoroutineExceptionHandler { _, e ->
        runCatching { logs.add(LogKind.ERROR, "Error: ${e.message ?: e.javaClass.simpleName}") }
    }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch(serialIo + errors) { block() }
    }

    private val _undo = MutableStateFlow<UndoOffer?>(null)
    /** A line a swipe just deleted, or a screen just cleared: the terminal offers Undo for a few seconds. */
    val undo: StateFlow<UndoOffer?> = _undo.asStateFlow()
    private var undoJob: kotlinx.coroutines.Job? = null

    /** Deletes and restores run here, not on the command queue: a slow command mustn't leave the swiped line's gap on screen. */
    private val deleteIo = Dispatchers.IO.limitedParallelism(1)

    init {
        // What a clear took out of view is deleted once its Undo is gone; a closed launcher's Undo went with it.
        viewModelScope.launch(deleteIo) { runCatching { logs.purgeCleared() } }
    }

    /** Main thread only. */
    private fun offerUndo(offer: UndoOffer) {
        _undo.value?.expire?.invoke()
        _undo.value = offer
        undoJob?.cancel()
        undoJob = viewModelScope.launch {
            kotlinx.coroutines.delay(8_000.milliseconds)
            _undo.value = null
            offer.expire()
        }
    }

    fun undo() {
        val offer = _undo.value ?: return
        _undo.value = null
        undoJob?.cancel()
        offer.undo()
    }

    /** Swipe left on a log line. */
    fun swipeDelete(entry: LogEntry) {
        viewModelScope.launch(deleteIo) { runCatching { logs.delete(entry.id) } }
        offerUndo(UndoOffer("Line deleted") { viewModelScope.launch(deleteIo) { runCatching { logs.restore(entry) } } })
    }

    /** Swipe right on a log line. */
    fun swipeCopy(entry: LogEntry) = lineActions.swipeCopy(entry)

    private fun startActivity(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { io { logs.add(LogKind.ERROR, "Can't open: ${it.message}") } }
    }

    // ---- script editor --------------------------------------------------------------------

    /**
     * Saves the editor's text; [onDone] gets null once it is written, with what saving changed
     * beyond the code ([saveCode]), or what went wrong.
     */
    fun saveScript(script: Script, code: String, onDone: (Throwable?, String?) -> Unit = { _, _ -> }) {
        _editing.value = script.copy(code = code)
        viewModelScope.launch {
            var changed: String? = null
            val failure = withContext(serialIo) {
                runCatching {
                    changed = saveCode(script, code)
                    scriptActions.refreshScriptCaches()
                }.exceptionOrNull()
            }
            onDone(failure, changed)
        }
    }

    /**
     * Saves [code] and runs the script, with [args] and as [event] when given (the editor's Run
     * with); [onDone] gets what went wrong with the save, if anything, and what saving changed.
     */
    fun runScript(
        script: Script, code: String, args: List<String> = emptyList(), event: Map<String, Any?>? = null,
        onDone: (Throwable?, String?) -> Unit = { _, _ -> },
    ) {
        viewModelScope.launch {
            var changed: String? = null
            val failure = withContext(serialIo) {
                runCatching {
                    changed = saveCode(script, code)
                    scriptActions.refreshScriptCaches()
                    scripts.byId(script.id)?.let { ScriptScheduler.runNow(context, it, args, event = event) }
                    logs.add(LogKind.INFO, "Running ${script.name}…", ctx = LogContext.of(LogContext.SCRIPT to script.name))
                }.exceptionOrNull()
            }
            onDone(failure, changed)
        }
    }

    /**
     * Writes [code] typed in the editor, and what its `@every` and `@at` lines say to its timers: a
     * new or changed line sets them, one taken out takes back what it had set (a timer set in the
     * sheet stays). Its timers are synced either way (whether it waits for a network follows the
     * code). Returns what changed, such as "runs every 1h", or null.
     */
    private fun saveCode(script: Script, code: String): String? {
        val before = scripts.byId(script.id) ?: return null
        scripts.saveCode(script.id, code, byUser = true)
        val notes = ArrayList<String>()
        val minutes = ScriptMeta.intervalAfter(before.code, code, before.intervalMin)
        if (minutes != before.intervalMin) {
            scripts.setInterval(script.id, minutes)
            notes += if (minutes == 0) "timer off" else "runs every ${CommandParsing.formatInterval(minutes)}"
        }
        val times = ScriptMeta.timesAfter(before.code, code, before.atTimes)
        if (times != before.atTimes) {
            scripts.setAtTimes(script.id, times)
            notes += if (times.isEmpty()) "no daily times" else "daily at ${times.joinToString(", ")}"
        }
        scripts.byId(script.id)?.let { ScriptScheduler.sync(context, it) }
        if (notes.isNotEmpty() && !before.enabled) notes += "once it's on"
        return notes.joinToString(" · ").ifEmpty { null }
    }

    /** What [script] logged from [since] on, oldest first: a run's output, for the editor. */
    suspend fun runLines(script: String, since: Long): List<LogEntry> = withContext(Dispatchers.IO) {
        logs.recent(limit = 200).filter { it.source == script && it.ts >= since }
    }

    fun onHeaderTap(target: HeaderTarget) {
        when (target) {
            HeaderTarget.CLOCK, HeaderTarget.ALARM -> startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            HeaderTarget.UNLOCKS -> runCommand("unlocks 7")
            HeaderTarget.BATTERY -> startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            HeaderTarget.NETWORK -> startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            HeaderTarget.SETTINGS -> openScreen(Screen.SETTINGS)
            HeaderTarget.TODO -> runCommand("todo")
        }
    }

    /**
     * Quick actions (tap `$` on the prompt, or the `quick` gesture): everything the header
     * offers, in thumb reach at the bottom of the screen.
     */
    fun openQuick() { _events.tryEmit(UiEvent.ShowQuick) }

    fun quickActions(): List<LogAction> = buildList {
        // The header's icons where it has them (battery and network as it shows them now).
        fun icon(id: Int) = ActionIcon.Drawable(id)
        val s = status.value
        add(LogAction("Settings", icon = icon(R.drawable.ic_settings)) { openScreen(Screen.SETTINGS) })
        add(LogAction("Notifications", icon = icon(R.drawable.ic_notifications)) { expandNotifications() })
        add(LogAction("Alarms & clock", icon = icon(R.drawable.ic_alarm)) { onHeaderTap(HeaderTarget.ALARM) })
        add(LogAction("Battery", icon = s.batteryPct?.let { ActionIcon.Battery(it, s.charging) }) { onHeaderTap(HeaderTarget.BATTERY) })
        val net = when (s.network) {
            "wifi" -> icon(R.drawable.ic_wifi)
            "offline" -> ActionIcon.Drawable(R.drawable.ic_cloud_off, alert = true)
            else -> icon(R.drawable.ic_signal)
        }
        add(LogAction("Network", icon = net) { onHeaderTap(HeaderTarget.NETWORK) })
        add(LogAction("Unlocks this week", icon = icon(R.drawable.ic_lock_open)) { onHeaderTap(HeaderTarget.UNLOCKS) })
        profiles.value.filter { it.kind == ProfileKind.WORK || it.kind == ProfileKind.PRIVATE }.forEach { p ->
            val cmd = if (p.kind == ProfileKind.PRIVATE) "private" else "work"
            // As in the header: the profile's colour while it runs, dim while paused.
            val res = if (p.kind == ProfileKind.WORK) R.drawable.ic_work else R.drawable.ic_lock
            add(
                LogAction(
                    if (p.quiet) "Resume ${p.kind.label} profile" else "Pause ${p.kind.label} profile",
                    icon = ActionIcon.Drawable(res, profile = p.kind.takeUnless { p.quiet }),
                ) {
                    runCommand("$cmd ${if (p.quiet) "on" else "off"}")
                },
            )
        }
        add(LogAction("Apps", icon = icon(R.drawable.ic_apps)) { runCommand("apps") })
        add(LogAction("Widgets", icon = icon(R.drawable.ic_widgets)) { openScreen(Screen.WIDGETS) })
        add(LogAction("Feeds", icon = icon(R.drawable.ic_rss)) { openScreen(Screen.FEEDS) })
        add(LogAction("Scripts", icon = icon(R.drawable.ic_code)) { openScreen(Screen.SCRIPTS) })
        if (com.mtwabpin.kolahal.BuildConfig.LOCK_SERVICE) {
            add(LogAction("Lock screen", icon = icon(R.drawable.ic_lock)) { if (!com.mtwabpin.kolahal.gesture.LockService.lock()) runCommand("lock") })
        }
    }

    /** Resuming is harmless, pausing asks for a second tap (the logged line). */
    fun onProfileTap(profile: Profile) {
        val cmd = if (profile.kind == ProfileKind.PRIVATE) "private" else "work"
        if (profile.quiet) {
            runCommand("$cmd on")
        } else {
            io {
                logs.add(
                    LogKind.INFO,
                    "Tap to pause the ${profile.kind.label} profile",
                    ctx = LogContext.of(LogContext.RUN to "$cmd off"),
                )
            }
        }
    }

    /** Runs [cmd] as if typed (without alias expansion); [byScript]: a script sent it, and the scripts it starts can't send more. */
    fun runCommand(cmd: String, byScript: Boolean = false) {
        collapseHeader()
        _events.tryEmit(UiEvent.ScrollToBottom)
        io { if (byScript) ScriptScheduler.sentByScript { processor.run(cmd, expand = false) } else processor.run(cmd, expand = false) }
    }

    fun saveFontSize(size: Int) {
        config.set(Keys.FONT_SIZE, size.toString())
    }

    fun runScriptNamed(name: String) {
        io {
            scripts.byName(name)?.let {
                ScriptScheduler.runNow(context, it)
                logs.add(LogKind.INFO, "Running $name…", ctx = LogContext.of(LogContext.SCRIPT to name))
            }
        }
    }

    fun expandNotifications() = com.mtwabpin.kolahal.gesture.Gestures.expandShade(context)

    private var lockHintShown = false

    /** Runs the action configured for a gesture: lock, shade, none or a terminal command. */
    fun onGesture(key: ConfigKey.Text) {
        val g = com.mtwabpin.kolahal.gesture.Gestures
        when (val action = config[key].trim()) {
            "", g.NONE -> Unit
            g.SHADE -> expandNotifications()
            "quick" -> _events.tryEmit(UiEvent.ShowQuick)
            // Locking needs no log line; only report when the accessibility service is off.
            // Without the service, explain once per session instead of on every stray double-tap.
            // Behind the build's switch, so the Play build drops the service's code too.
            g.LOCK -> if (!(com.mtwabpin.kolahal.BuildConfig.LOCK_SERVICE && com.mtwabpin.kolahal.gesture.LockService.lock()) && !lockHintShown) {
                lockHintShown = true
                runCommand("lock")
            }
            // Gesture actions may be aliases, so they expand like typed input.
            else -> {
                collapseHeader()
                _events.tryEmit(UiEvent.ScrollToBottom)
                io { processor.run(action) }
            }
        }
    }

    /** Closes the editor; [unsaved] (its text, when it had changes) is saved first and reported where you land. */
    fun closeEditor(unsaved: String? = null) {
        val script = _editing.value
        _editLine.value = null
        _editing.value = null
        if (script != null && unsaved != null) io {
            val changed = saveCode(script, unsaved)
            scriptActions.refreshScriptCaches()
            notice(listOfNotNull("Saved ${script.name}.${script.lang.ext}", changed).joinToString(" · "))
        }
    }

    // ---- GUI screens (settings, script manager) ------------------------------------------

    /** A message for the GUI screens; [seq] tells the same text twice apart, so both show. */
    data class Notice(val text: String, val seq: Long)

    private val _notice = MutableStateFlow<Notice?>(null)
    private var noticeSeq = 0L
    /** Short result message shown on the GUI screens (also logged to the terminal). */
    val notice: StateFlow<Notice?> = _notice.asStateFlow()

    private fun show(text: String) {
        _notice.value = Notice(text, ++noticeSeq)
    }

    /** Pages opened from other pages (Settings → Script manager); Back returns along it. */
    private val backStack = ArrayDeque<Screen>()

    override fun openScreen(screen: Screen) {
        _notice.value = null
        _peek.value = null
        synchronized(backStack) {
            val current = _screen.value
            if (screen == Screen.TERMINAL) backStack.clear()
            else if (current != Screen.TERMINAL && current != screen) backStack.addLast(current)
            _screen.value = screen
        }
        if (screen == Screen.SCRIPTS || screen == Screen.SCRIPT_LIBRARY || screen == Screen.GET_SCRIPTS) scriptActions.refreshScripts()
        if (screen == Screen.GET_SCRIPTS) scriptActions.loadSharedScripts()
        if (screen == Screen.SETUP || screen == Screen.ONBOARDING) refreshSetup()
    }

    /** Settings, Feeds and Scripts are tabs of one place: switching replaces the screen, so Back leaves them all. */
    fun switchTab(screen: Screen) {
        _notice.value = null
        _peek.value = null
        // Settings opens on its main page next time, not the category left by a tab.
        settingsCategory.value = null
        _screen.value = screen
        if (screen == Screen.SCRIPTS) scriptActions.refreshScripts()
    }

    private val _setup = MutableStateFlow<List<SetupCheck>>(emptyList())
    val setupChecks: StateFlow<List<SetupCheck>> = _setup.asStateFlow()

    fun refreshSetup() = io { _setup.value = SetupChecks.run(context) }

    /** Opens the system page for a check; the list refreshes when the user comes back. */
    fun fixSetup(check: SetupCheck) {
        check.fix?.let { cmd -> io { processor.run(cmd, expand = false) } }
    }

    /** Back from a page: the page it was opened from, or the terminal. */
    fun closeScreen() {
        _notice.value = null
        val previous = synchronized(backStack) { backStack.removeLastOrNull() } ?: Screen.TERMINAL
        // A category stays open only across a manager screen opened from it, not a trip home.
        if (_screen.value == Screen.SETTINGS && previous == Screen.TERMINAL) settingsCategory.value = null
        _screen.value = previous
        if (previous == Screen.SCRIPTS) scriptActions.refreshScripts()
        if (previous == Screen.SETUP || previous == Screen.ONBOARDING) refreshSetup()
    }

    private fun notice(text: String, error: Boolean = false) {
        show(text)
        logs.add(if (error) LogKind.ERROR else LogKind.INFO, text)
    }

    /**
     * File operations (SAF providers can be slow, e.g. cloud storage) run off the serial command
     * queue, and failures show as a notice on the GUI page instead of only in the terminal log.
     */
    private fun fileOp(label: String, block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { block() }.onFailure { notice("$label failed: ${it.message ?: it.javaClass.simpleName}", error = true) }
        }
    }

    override fun askSecret(script: Script, name: String) = scriptActions.askSecret(script, name)

    override fun askBackupFolder(thenBackUp: Boolean) = backupActions.askBackupFolder(thenBackUp)

    fun setConfig(key: ConfigKey<*>, raw: String): Boolean = config.set(key, raw).also { set ->
        // Backups on their own need somewhere to go.
        if (set && key == Keys.BACKUP_AUTO && raw != "off" && com.mtwabpin.kolahal.data.AutoBackup.folder(context) == null) askBackupFolder(false)
    }

    fun resetConfig(key: ConfigKey<*>) = config.reset(key)

    // ---- TerminalUi -----------------------------------------------------------------------

    override fun openEditor(script: Script) = openEditorAt(script, null)

    override fun requestContactsPermission() {
        _events.tryEmit(UiEvent.RequestContactsPermission)
    }

    /** What the contact being picked is for; null once it's back (or after the process restarted). */
    @Volatile private var contactPicked: ((com.mtwabpin.kolahal.contacts.Contact) -> Unit)? = null

    override fun pickContact(then: (com.mtwabpin.kolahal.contacts.Contact) -> Unit) {
        contactPicked = then
        _events.tryEmit(UiEvent.PickContact)
    }

    /**
     * Android's contact picker is back with [uri], or null when nothing was picked. Its numbers are
     * read now, while the picker allows it, and remembered for the next time the name is typed.
     */
    fun onContactPicked(uri: Uri?) {
        val then = contactPicked
        contactPicked = null
        if (uri == null) return
        io {
            val picked = com.mtwabpin.kolahal.contacts.ContactPicker.read(context, uri)
            val c = picked.firstOrNull() ?: run {
                logs.add(LogKind.ERROR, "That contact has no phone number")
                return@io
            }
            com.mtwabpin.kolahal.contacts.ContactsRepository.get(context).remember(picked)
            if (then != null) then(c) else logs.add(LogKind.OUTPUT, c.name, ctx = PeopleCommands.ctx(c))
        }
    }

    fun onContactPickerMissing() {
        contactPicked = null
        io { logs.add(LogKind.ERROR, "This phone has no contact picker") }
    }

    fun onContactsPermission(granted: Boolean) {
        refreshSetup()
        if (granted) com.mtwabpin.kolahal.contacts.ContactsRepository.get(context).refresh()
        io { logs.add(if (granted) LogKind.INFO else LogKind.ERROR, if (granted) "Contacts allowed" else "Contacts permission denied") }
    }

    override fun pickScriptFolder() {
        _events.tryEmit(UiEvent.PickScriptFolder)
    }

    override fun requestFontImport() {
        _events.tryEmit(UiEvent.PickFont)
    }

    /**
     * Copies a picked .ttf/.otf to filesDir/fonts/custom.<ext> (replacing any earlier import),
     * checks that Android can load it, and switches `font` to custom.
     */
    fun importFont(uri: Uri?) {
        if (uri == null) return
        fileOp("font import") {
            val name = TermFonts.importFrom(context, uri)
            fontErrorShown = false
            withContext(Dispatchers.Main) {
                TermFonts.revision++
                config.set(Keys.FONT, TermFonts.CUSTOM)
            }
            notice("Font = custom (${name ?: "imported font"})")
        }
    }

    /** Settings → Your themes → Import a theme: a theme someone shared, kept with yours and used. */
    fun importTheme(uri: Uri?) {
        if (uri == null) return
        fileOp("theme import") {
            val (name, added) = CustomThemes.import(context, Files.readText(context, uri), Files.displayName(context, uri))
            withContext(Dispatchers.Main) { config.set(Keys.THEME, name) }
            notice(if (added) "Theme $name imported and in use" else "You already have theme $name: it's in use")
        }
    }

    /** The theme builder's Export: one of your themes, as last saved, in a file to share. */
    fun exportTheme(name: String, uri: Uri?) {
        if (uri == null) return
        fileOp("theme export") {
            val colors = CustomThemes.colors(name) ?: throw IllegalStateException("$name was deleted")
            Files.writeText(context, uri, CustomThemes.encode(colors, name))
            notice("Exported theme $name")
        }
    }

    @Volatile private var fontErrorShown = false

    /** The configured font could not be loaded (e.g. custom set but the file is gone); said once per run. */
    fun onFontUnavailable(font: String) {
        if (fontErrorShown) return
        fontErrorShown = true
        io {
            logs.add(
                LogKind.ERROR,
                "Font '$font' could not be loaded — using the system monospace · tap to import one",
                ctx = LogContext.of(LogContext.RUN to "font import"),
            )
        }
    }

    fun onScriptFolderPicked(uri: Uri?) {
        if (uri == null) return
        io {
            ScriptFolder(context).setFolder(uri)
            logs.add(LogKind.INFO, "Script folder set — 'script export' / 'script import' to sync")
        }
    }

    fun onNotificationPermission(granted: Boolean) {
        refreshSetup()
        io {
            logs.add(
                if (granted) LogKind.INFO else LogKind.ERROR,
                if (granted) "Notifications allowed" else "Notifications denied — tap to open notification settings",
                ctx = if (granted) LogContext.EMPTY else LogContext.of(LogContext.RUN to "notifications settings"),
            )
        }
    }

    override fun requestNotificationPermission() {
        _events.tryEmit(UiEvent.RequestNotificationPermission)
    }

    override fun clearScreen() {
        expandHeader()
        io {
            val cleared = logs.clearScreen()
            withContext(Dispatchers.Main) {
                offerUndo(UndoOffer(
                    "Screen cleared",
                    expire = { viewModelScope.launch(deleteIo) { runCatching { logs.purgeCleared(cleared.upTo) } } },
                ) { viewModelScope.launch(deleteIo) { runCatching { logs.undoClear(cleared) } } })
            }
        }
    }

    override fun freshScreen() {
        expandHeader()
        io { logs.hideScreen() }
    }

    // ---- productivity (todo, notes, reminders, agenda) --------------------------------------

    private val _todoCount = MutableStateFlow(0)
    /** Open todos, for the header (header.todo). */
    val todoCount: StateFlow<Int> = _todoCount.asStateFlow()

    /** Suggestion data, refreshed with the log (every todo / note / remind command logs). */
    @Volatile private var todoCache: List<com.mtwabpin.kolahal.productivity.Todo> = emptyList()
    @Volatile private var noteCache: List<com.mtwabpin.kolahal.productivity.Note> = emptyList()
    @Volatile private var reminderCache: List<com.mtwabpin.kolahal.productivity.Reminder> = emptyList()
    @Volatile private var feedCache: List<com.mtwabpin.kolahal.feeds.FeedInfo> = emptyList()

    /** Contacts with numbers (empty without the permission); suggestions recompute when they load. */
    val contacts get() = com.mtwabpin.kolahal.contacts.ContactsRepository.get(context).contacts

    private fun refreshProductivity() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                todoCache = com.mtwabpin.kolahal.productivity.TodoStore.get(context).all()
                noteCache = com.mtwabpin.kolahal.productivity.NoteStore.get(context).all()
                reminderCache = com.mtwabpin.kolahal.productivity.ReminderStore.get(context).all()
                _todoCount.value = todoCache.count { !it.done }
            }
            runCatching { feedCache = com.mtwabpin.kolahal.feeds.FeedRepository.get(context).all() }
        }
    }

    override fun requestCalendarPermission() {
        _events.tryEmit(UiEvent.RequestCalendarPermission)
    }

    override fun requestCallLogPermission() {
        _events.tryEmit(UiEvent.RequestCallLogPermission)
    }

    private val _notificationAccessAsk = MutableStateFlow(false)
    /** Kolahal is asking before Android's Notification access screen opens ([askNotificationAccess]). */
    val notificationAccessAsk: StateFlow<Boolean> = _notificationAccessAsk.asStateFlow()

    override fun askNotificationAccess() {
        _notificationAccessAsk.value = true
    }

    /** The answer: Allow opens Android's screen; Not now opens nothing and leaves a line to ask again. */
    fun answerNotificationAccess(allow: Boolean) {
        _notificationAccessAsk.value = false
        if (allow) {
            startActivity(com.mtwabpin.kolahal.notifications.NotificationActions.settingsIntent(context))
        } else {
            logs.add(LogKind.INFO, "Notification access is off · tap to turn it on", ctx = LogContext.of(LogContext.RUN to "notifs enable"))
        }
    }

    fun onCallLogPermission(granted: Boolean) {
        refreshSetup()
        if (granted) com.mtwabpin.kolahal.calls.CallEvents.get(context).start()
        io {
            if (granted) {
                logs.add(LogKind.INFO, "Call log allowed — new calls appear here; tap to list recent ones", ctx = LogContext.of(LogContext.RUN to "calls"))
            } else {
                logs.add(LogKind.ERROR, "Call log permission denied — tap to open app settings", ctx = LogContext.of(LogContext.RUN to "calls --settings"))
            }
        }
    }

    fun onCalendarPermission(granted: Boolean) {
        refreshSetup()
        io {
            if (granted) {
                logs.add(LogKind.INFO, "Calendar allowed — tap to see your agenda", ctx = LogContext.of(LogContext.RUN to "agenda"))
            } else {
                logs.add(LogKind.ERROR, "Calendar permission denied — tap to open app settings", ctx = LogContext.of(LogContext.RUN to "agenda --settings"))
            }
        }
    }

    private val _homePresses = MutableStateFlow(0)
    /** Bumps on every Home press while already home; the widgets pager returns to the terminal. */
    val homePresses: StateFlow<Int> = _homePresses.asStateFlow()

    /**
     * Settings showing home through it, to see a look setting while changing it: while a slider is
     * dragged ([key] stays visible) or the page's eye button is held ([key] null). [typing]: show home
     * as if typing, for the key bar, prompt and suggestions.
     */
    data class Peek(val key: String?, val typing: Boolean)

    private val _peek = MutableStateFlow<Peek?>(null)
    val peek: StateFlow<Peek?> = _peek.asStateFlow()

    fun peek(value: Peek?) {
        _peek.value = value
    }

    /** The open Settings category page; kept while a manager screen opened from it is on top. */
    val settingsCategory = mutableStateOf<String?>(null)

    /** The custom theme the theme builder edits; null while it makes a new one. */
    val themeBuilderName = mutableStateOf<String?>(null)

    fun openThemeBuilder(name: String?) {
        themeBuilderName.value = name
        openScreen(Screen.THEME_BUILDER)
    }

    /**
     * Where the log's app selector is, in the window: the log's area between the header and the
     * prompt. The widgets page puts its selector in the same place, so selector.position and the
     * selector's height come out the same on both pages. Only how far down and how high it is: where
     * it is across changes on every frame of a swipe between the pages, and the widgets page would be
     * built again each time.
     */
    var selectorArea by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)

    /** widgets.start: home opens on the widgets page, and Home and Back return to it. */
    private fun widgetsFirst() = config[Keys.WIDGETS_START] && config[Keys.WIDGETS_PAGE] != "off"

    /** Whether home's page showing is the log's (the widgets page can be first, [widgetsFirst]). */
    @Volatile private var logShown = !widgetsFirst()

    /**
     * Home's pager settled on a page. With the widgets page first, keyboard.on-home opens the
     * keyboard on reaching the log, where the prompt is, and the widgets page puts it away.
     * [opened]: a page turned to, not only told again.
     */
    fun onHomePageShown(log: Boolean, opened: Boolean = true) {
        logShown = log
        if (!opened || _screen.value != Screen.TERMINAL || _editing.value != null) return
        if (!log) _events.tryEmit(UiEvent.HideKeyboard)
        else if (config[Keys.FOCUS_ON_HOME]) _events.tryEmit(UiEvent.FocusPrompt)
    }

    /** Pressing Home while already home: reset the prompt. */
    fun onHomePressed() {
        settingsCategory.value = null
        _homePresses.value++
        _editing.value?.let { return }
        expandHeader()
        val fromOtherScreen = _screen.value != Screen.TERMINAL
        openScreen(Screen.TERMINAL) // Home always lands on the terminal and forgets the page stack
        escape()
        // With the widgets page first, Home goes there: no prompt to open the keyboard for.
        val focus = config[Keys.FOCUS_ON_HOME] && !widgetsFirst()
        if (fromOtherScreen) {
            // The terminal isn't composed yet, so an event now would have no collector: it picks
            // these up when it appears (consumeArrival).
            arrival = Arrival(focus = focus)
        } else {
            _events.tryEmit(UiEvent.ScrollToBottom)
            if (focus) _events.tryEmit(UiEvent.FocusPrompt)
        }
    }

    /** What the terminal should do once it is on screen again after Home from another page. */
    data class Arrival(val focus: Boolean)

    @Volatile private var arrival: Arrival? = null

    fun consumeArrival(): Arrival? = arrival.also { arrival = null }

    // Keep this last in the class. The first-run check runs on another thread straight away and
    // opens the setup screen, which reads fields declared above; started from an earlier init it
    // could find them still unset and crash the first start (seen as a crash soon after install).
    init {
        viewModelScope.launch(Dispatchers.IO) {
            // A persistent flag, so `clear all` doesn't look like a first run.
            val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
            val firstRun = !prefs.getBoolean("intro_shown", false) && logs.recent(1).isEmpty()
            prefs.edit { putBoolean("intro_shown", true) }
            if (firstRun) {
                logs.add(LogKind.INFO, "Kolahal — a terminal launcher. Type 'help' or start typing an app name.")
                logs.add(LogKind.INFO, "↑/↓ recall commands · tap a line to reuse/open it · long-press for actions")
                openScreen(Screen.ONBOARDING) // first run: the tour, which sets up the first few things
            }
        }
    }
}
