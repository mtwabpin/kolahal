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
import com.mtwabpin.kolahal.data.StatusLine
import com.mtwabpin.kolahal.data.StatusLines
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
import com.mtwabpin.kolahal.ui.PinnedTurns
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE = 400
private const val MAX_PAGE = 5000

/** Commands that change the todo, note, reminder and feed caches. */
private val PRODUCTIVITY_COMMANDS = CommandCatalog.PRODUCTIVITY + setOf("feed", "feeds")

/** The whole page is read again at least this often, to catch trims by other processes. */
private const val FULL_RELOAD_MS = 60_000L

/**
 * Returns the ↑ history after [fresh] lines (oldest first) were added to it:
 * - the new commands come first, newest first;
 * - each text appears once;
 * - at most [limit] entries in total.
 * This gives the same result as [LogRepository.commandHistory].
 */
internal fun withNewCommands(history: List<String>, fresh: List<LogEntry>, limit: Int = LogRepository.HISTORY_LIMIT): List<String> {
    val added = fresh.asReversed().filter { it.kind == LogKind.COMMAND }.map { it.text }
    return if (added.isEmpty()) history else (added + history).distinct().take(limit)
}

/** `/battery low` at the prompt searches the log for those words. Returns null when the line isn't a search. */
internal fun searchWords(text: String): List<String>? =
    if (!text.startsWith("/")) null else text.substring(1).split(Regex("\\s+")).filter { it.isNotEmpty() }

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TerminalViewModel(app: Application, private val state: SavedStateHandle) : AndroidViewModel(app), TerminalUi {
    private val context: Context get() = getApplication()

    /** Reload requests from all sources (log changes, paging, resume) go into one pipeline. The latest request wins. */
    private val reloadRequests = MutableStateFlow(0L)
    private fun requestReload() = reloadRequests.update { it + 1 }

    /**
     * True while home is shown: from [onResume] (once a fresh screen is decided) to MainActivity's onStop.
     * The log page is read again, and a `/` search runs, only during this time.
     */
    private val reading = MutableStateFlow(true)

    /** True when coming back after a stop. Then the single read of the page on return doesn't wait. */
    @Volatile private var back = false

    /** True from MainActivity's onStart to its onStop. */
    @Volatile private var activityStarted = true

    /**
     * Called on MainActivity's onStart (true) and onStop (false). While stopped, the log page and a
     * `/` search aren't read, and contacts wait to reload. On return, [onResume] starts them again.
     */
    fun onStarted(on: Boolean) {
        activityStarted = on
        // On return, [onResume] starts reading again.
        if (!on) reading.value = false
        com.mtwabpin.kolahal.contacts.ContactsRepository.get(context).setActive(on)
    }

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
        override val statusNames get() = _lines.value.mapTo(HashSet()) { it.name }
        override val libraryNames get() = this@TerminalViewModel.libraryNames
        override val sharedNames get() = scriptActions.sharedScripts.value.entries.map { it.name }
        override val feeds get() = feedCache
        override val todos get() = todoCache
        override val notes get() = noteCache
        override val reminders get() = reminderCache
        override val logSources get() = this@TerminalViewModel.logSources
    })

    /** The scripts. Refreshed outside the main thread whenever the log changes (every script action writes to the log). */
    val scriptList: StateFlow<List<Script>>
        field = MutableStateFlow<List<Script>>(emptyList())

    private val actionsHost = object : ScriptActions.Host {
        override fun io(block: suspend () -> Unit) = this@TerminalViewModel.io(block)
        override fun fileOp(label: String, block: suspend () -> Unit) = this@TerminalViewModel.fileOp(label, block)
        override fun notice(text: String, error: Boolean, undo: (() -> Unit)?) = this@TerminalViewModel.notice(text, error, undo)
        override fun show(text: String) = this@TerminalViewModel.show(text)
        override fun emit(event: UiEvent) { _events.tryEmit(event) }
        override fun openEditor(script: Script) = this@TerminalViewModel.openEditor(script)
        override fun setCommandScripts(list: List<Script>) = this@TerminalViewModel.setCommandScripts(list)
    }

    /** The actions that the script screens perform on scripts: switches, timers, keys, options, files and installs. */
    val scriptActions = ScriptActions(app, state, viewModelScope, scriptList, actionsHost)

    /** Backup files, and the folder for automatic backups. */
    val backupActions = BackupActions(app, scriptActions, actionsHost)

    private val _commandNames = MutableStateFlow<List<String>>(emptyList())
    /** Names of `@command` scripts. They are one of the inputs for suggestions. */
    val commandNames: StateFlow<List<String>> = _commandNames.asStateFlow()

    /** Usage lines of the command scripts that have a known one (see [ScriptMeta.usage]). Set together with [commandNames]. */
    @Volatile private var scriptUsages: Map<String, String> = emptyMap()

    /** The `@desc` of each command script that has one, for its suggestion row. Set together with [commandNames]. */
    @Volatile private var scriptDescriptions: Map<String, String> = emptyMap()

    /** [scriptUsages] plus the built-in commands' usage lines. Instant launch checks a line against these on every key. */
    @Volatile private var commandUsages: Map<String, String> = scriptUsages + CommandCatalog.COMMAND_USAGES

    /** The script names in the bundled library, read once. `script install` completes them. */
    private val libraryNames: List<String> by lazy { ScriptLibrary(context).entries.map { it.name } }

    /** [commandUsages] without the built-in commands turned off (commands.off): their names are plain words. */
    private fun usages(): Map<String, String> {
        val off = CommandsOff.names(config[Keys.COMMANDS_OFF])
        return if (off.isEmpty()) commandUsages else commandUsages - off
    }

    private fun setCommandScripts(list: List<Script>) {
        val commands = list.filter { it.enabled && ScriptMeta.isCommand(it.code) }
        scriptUsages = commands.mapNotNull { s -> ScriptMeta.usage(s.code, s.name)?.let { s.name to it } }.toMap()
        commandUsages = scriptUsages + CommandCatalog.COMMAND_USAGES
        scriptDescriptions = commands.mapNotNull { s -> ScriptMeta.description(s.code)?.let { s.name to it } }.toMap()
        _commandNames.value = commands.map { it.name }
    }

    /** Goes up by one when aliases, hidden apps or labels change. */
    val appPrefsVersion: StateFlow<Int> get() = appPrefs.version

    /** Every stored `status(text, opts)` line, by script name. */
    private val _lines = MutableStateFlow<List<StatusLine>>(emptyList())

    /** The status lines of scripts that are on, by name. Their cards on the Scripts screen show them. */
    val statuses: StateFlow<Map<String, String>> = combine(_lines, scriptList) { lines, list ->
        if (lines.isEmpty()) return@combine emptyMap()
        val on = list.filter { it.enabled }.mapTo(HashSet()) { it.name }
        lines.filter { it.name in on }.associate { it.name to it.text }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * The status lines you pinned ([ScriptAccess.HEADER]), of scripts that are on. The pinned header
     * shows them after what's playing, by name.
     */
    val pinned: StateFlow<List<StatusLine>> = combine(_lines, scriptList) { lines, list ->
        if (lines.isEmpty()) return@combine emptyList()
        // A script's permissions are read from its code on the main thread, so check only scripts that have a line.
        val named = lines.mapTo(HashSet()) { it.name }
        val shown = list.filter { it.name in named && it.enabled && it.grants.allows(ScriptAccess.HEADER) }.mapTo(HashSet()) { it.name }
        lines.filter { it.name in shown }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** When each pinned line last ran again, by script name, in [SystemClock.elapsedRealtime] time. */
    private val pinRuns = HashMap<String, Long>()

    /**
     * Runs again the pinned lines that ask for it (`refresh`) and are due. Home calls this every second
     * while the pinned header is on screen, so they run only then. A line that is still running is left alone.
     */
    fun refreshPinned(now: Long = android.os.SystemClock.elapsedRealtime()) {
        val due = PinnedTurns.due(pinned.value, pinRuns, now)
        if (due.isEmpty()) return
        due.forEach { pinRuns[it] = now }
        io { due.forEach { name -> scripts.byName(name)?.let { ScriptScheduler.refreshPinned(context, it) } } }
    }

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val _filter = MutableStateFlow(logs.filter?.let(LogFilter::parse))
    /** `log only …`: the log shows only these lines until ✕, Back, `log all` or another command. */
    val filter: StateFlow<LogFilter?> = _filter.asStateFlow()

    private val _filterCount = MutableStateFlow(0)
    /** How many lines [filter] lets through, shown on its bar. */
    val filterCount: StateFlow<Int> = _filterCount.asStateFlow()

    /** The sources of the log's lines, newest first. `/@` and `log only` suggest them. */
    @Volatile private var logSources: List<String> = emptyList()

    /** The log column's width on screen, from the last layout ([onLogWidth]). `fetch` fits its lines to this width. */
    @Volatile private var logWidth: Int? = null

    fun onLogWidth(px: Int) {
        if (px > 0) logWidth = px
    }

    override fun logWidthPx(): Int? = logWidth

    override fun filterLog(filter: LogFilter?) {
        if (_filter.value == filter) return
        _filter.value = filter
        // Kept until cleared, also after leaving home and after a restart.
        logs.filter = filter?.let { if (it is LogFilter.OfKind) it.word else it.label }
        requestReload()
    }

    private val _screen = MutableStateFlow(Screen.TERMINAL)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _rows = MutableStateFlow<List<LogRow>>(emptyList())
    /** What the log list shows: entries (with dimming and gutter) and day separators. */
    val rows: StateFlow<List<LogRow>> = _rows.asStateFlow()

    val apps: StateFlow<List<AppEntry>> = appsRepo.apps
    val profiles: StateFlow<List<Profile>> = appsRepo.profiles
    val unlocksToday: StateFlow<Int> = unlockCounter.today
    val status: StateFlow<Status> = DeviceStatus.get(app).status
    val config: Config = Config.get(app)
    private val mediaRepo = com.mtwabpin.kolahal.control.MediaRepository.get(app)
    /**
     * The media session for the header. Null without notification access or without a session.
     * [com.mtwabpin.kolahal.control.NowPlaying.showInHeader] hides an idle one.
     */
    val nowPlaying: StateFlow<com.mtwabpin.kolahal.control.NowPlaying?> = mediaRepo.nowPlaying

    /** The header's now-playing line: a tap toggles play/pause, a long-press (or ⏭) skips. */
    fun mediaToggle() = io { mediaRepo.send(com.mtwabpin.kolahal.control.MediaRepository.Action.TOGGLE) }
    fun mediaNext() = io { mediaRepo.send(com.mtwabpin.kolahal.control.MediaRepository.Action.NEXT) }

    var input by mutableStateOf(TextFieldValue(""))
        private set

    /** What the line in the prompt would change ([LivePreview]). */
    val livePreview: LivePreview?
        get() = LivePreview.of(input.text, config) { TermFonts.customFile(getApplication()) != null }

    /**
     * The preview of a line that was just entered. It stays until the command has run; otherwise
     * the old look would show for a moment.
     */
    private var entered by mutableStateOf<LivePreview?>(null)

    /**
     * The look to use now, when it differs from the settings. Only on home: other screens show
     * what is in use.
     */
    val previewed: LivePreview? get() = entered ?: livePreview?.takeIf { _screen.value == Screen.TERMINAL }

    private val _editing = MutableStateFlow<Script?>(null)
    val editing: StateFlow<Script?> = _editing.asStateFlow()

    private val _editLine = MutableStateFlow<Int?>(null)
    /** The line to jump to (and flash) when the editor opens from an error. */
    val editLine: StateFlow<Int?> = _editLine.asStateFlow()

    fun openEditorAt(script: Script, line: Int?) {
        // Clear the notice. Otherwise the screen below shows its last message again when the editor closes.
        _notice.value = null
        _editLine.value = line
        _editing.value = script
    }

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events

    /** Written by [reload] outside the main thread. Read on the main thread. */
    @Volatile private var history: List<String> = emptyList()
    private var historyIndex = -1
    private var draft = ""

    /** Receives launcher.run(cmd) from scripts that the user started (see HostApi.runCommand). */
    private val runCommandReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            // Check again here, where the command runs: a sensitive command never runs from a script.
            intent.getStringExtra(HostApi.EXTRA_COMMAND)?.takeIf { !InstantRun.isSensitive(it) }?.let { runCommand(it, byScript = true) }
        }
    }

    override fun onCleared() {
        runCatching { context.unregisterReceiver(runCommandReceiver) }
    }

    /** Now playing, scripts' commands, and the log's reloads while home is shown. Started by the init at the end. */
    private fun startWatching() {
        mediaRepo.start()
        androidx.core.content.ContextCompat.registerReceiver(
            context, runCommandReceiver, android.content.IntentFilter(HostApi.ACTION_RUN_COMMAND),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        viewModelScope.launch {
            // Bursts of changes (a script logging many lines, another process) collapse into one reload.
            // Locking or unlocking the private space shows or hides its lines.
            launch { appsRepo.profiles.map(::lockedPrivate).distinctUntilChanged().drop(1).collect { requestReload() } }
            // Away from home, only the trim continues. It keeps the log at its size limit as new lines arrive.
            launch { logs.version.collect { if (!reading.value) withContext(Dispatchers.IO) { runCatching { logs.trimIfOverdue() } } } }
            // Reload only while home is shown, because nobody sees the page otherwise.
            // - On return, it reads what arrived in the meantime at once, and only once.
            // - Bursts wait as before (debounce).
            // - The first read waits too.
            reading.flatMapLatest { on -> if (on) combine(logs.version, reloadRequests) { a, b -> a to b }.withIndex() else emptyFlow() }
                .debounce { if (it.index == 0 && back) 0L.also { back = false } else 40L }
                .collectLatest {
                // A failed reload keeps the previous rows instead of crashing home. It is not logged,
                // because writing to the log would trigger the next reload.
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

    /** How many log lines are loaded. It grows as the user scrolls up (older lines load on demand). */
    @Volatile private var pageLimit = PAGE

    fun loadOlder() {
        // With `log only …`, the screen shows the filter's page, not the log's.
        val shown = if (_filter.value == null) loaded.size else _entries.value.size
        if (shown < pageLimit || pageLimit >= MAX_PAGE) return
        pageLimit += PAGE
        requestReload()
    }

    /** Every visible line loaded, before the private-space filter. The next reload builds on this. */
    @Volatile private var loaded: List<LogEntry> = emptyList()
    /** The newest line loaded so far. It is kept even if the page becomes empty ([onPause] records it as seen). */
    @Volatile private var newestLoaded: Long? = null
    private var loadedStructure = -1L
    private var loadedTrimmed = -1L
    /** The last removal ([LogRepository.removedSeq]) already dropped from the page. */
    private var removedSeen = 0L
    /** The last change in place ([LogRepository.updatedSeq]) already read again. */
    private var updatedSeen = 0L
    /** True when no visible line is older than the page's first line, so scrolling back has nothing more to read. */
    private var logStart = false
    private var fullAt = 0L
    private var scriptsPrint: String? = null
    /** The lines of `log only …` as last shown, with the filter and the page size they were found for. */
    private var filtered: List<LogEntry> = emptyList()
    private var filteredFor: LogFilter? = null
    private var filteredLimit = 0

    /**
     * Brings the log up to date.
     * - Usually only the lines after the newest shown line are read.
     * - An older page is read as the user scrolls back.
     * - Removed lines (a swipe, a launch line moved down) are dropped from the page.
     * - The whole page is read again: after a change in its middle (a restore, a hide, a clear),
     *   when a trim reached into it, and once a minute (another process may have changed something
     *   this one didn't see).
     * - Scripts, history and the todo and feed caches are read only when something they show could
     *   have changed.
     *
     * Everything runs on one background thread. A trip back to the main thread between the reads
     * would wait for its current work (a frame while typing or scrolling), so new lines would show
     * later. Only this collector writes the fields it keeps. The main thread reads the page from [rows].
     */
    private suspend fun reload() = withContext(Dispatchers.IO) {
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
        val incremental = newest != null && structure == loadedStructure && trimmed < oldest &&
            removed != null && updated != null && now - fullAt < FULL_RELOAD_MS
        val fresh = if (incremental) logs.recent(limit, afterId = newest) else logs.recent(limit)
        // Lines changed in place since the last reload, read again once for the page and for `log only …`.
        val again: Map<Long, LogEntry> = if (incremental && changed.isNotEmpty()) logs.byIds(changed).associateBy { it.id } else emptyMap()
        var start = logStart
        val all = if (!incremental) {
            start = fresh.size < limit
            fresh
        } else {
            val left = if (gone.isEmpty()) loaded else loaded.filterNot { it.id in gone }
            // Lines a script changed in place (line.set), read again where they are. Ids not on the page are ignored.
            val kept = if (again.isEmpty()) left else left.map { again[it.id] ?: it }
            val have = kept.size + fresh.size
            // After scrolling back (or showing a search result): read the lines before the page, up to the new limit.
            val before = (kept.firstOrNull() ?: fresh.firstOrNull())?.id
            val older = if (have >= limit || start || before == null) emptyList() else {
                logs.recent(limit - have, beforeId = before).also { if (it.size < limit - have) start = true }
            }
            val page = older + kept + fresh
            // The page shrank ([atNewest]) or grew past its limit. The lines cut off are older than the page now.
            if (page.size > limit) start = false
            page.takeLast(limit)
        }
        val print = scripts.fingerprint()
        if (print != scriptsPrint) {
            val latest = scripts.all()
            scriptList.value = latest
            withContext(Dispatchers.Default) { setCommandScripts(latest) }
            scriptsPrint = print
        }
        _lines.value = StatusLines.all(context)
        if (!incremental || trimmed != loadedTrimmed) _hiddenLines.value = logs.hiddenCount()
        // A typed command's own line puts it first in the history. Read the history again only when a
        // command may be gone: after a trim, or when a removed line was a command or is not on this page.
        val commandGone = trimmed != loadedTrimmed ||
            (gone.isNotEmpty() && loaded.filter { it.id in gone }.let { had -> had.size < gone.size || had.any { it.kind == LogKind.COMMAND } })
        history = if (!incremental || commandGone) logs.commandHistory() else withNewCommands(history, fresh)
        if (!incremental || fresh.any(::touchesProductivity)) refreshProductivity()
        if (!incremental || fresh.any { it.source != null && it.source !in logSources }) logSources = logs.sources()
        // A locked private space hides its apps. Its lines (launches, notifications) are hidden too.
        val locked = lockedPrivate(appsRepo.profiles.value)
        val list = if (locked.isEmpty()) all else all.filterNot { it.ctx.userSerial in locked }
        // `log only …` takes its lines from the whole log, not just this page (which may have none of them).
        // - New lines are checked as they arrive.
        // - The whole log is searched when the whole page is read, when the filter or the page size
        //   changed, and after a line was removed.
        val filter = _filter.value
        var count = _filterCount.value
        val shown = when {
            filter == null -> list
            // Lines changed in place: patched where they are, or dropped once they no longer match. Only
            // a changed line that now matches but wasn't shown needs the whole log searched again.
            incremental && filter == filteredFor && limit <= filteredLimit && gone.isEmpty() &&
                (again.isEmpty() || filtered.map { it.id }.toHashSet().let { shownIds -> again.values.none { it.id !in shownIds && it.ctx.userSerial !in locked && filter.matches(it) } }) -> {
                val patched = if (again.isEmpty()) filtered else filtered.mapNotNull { e ->
                    val now = again[e.id] ?: return@mapNotNull e
                    if (filter.matches(now)) now else null.also { count-- }
                }
                val more = (if (locked.isEmpty()) fresh else fresh.filterNot { it.ctx.userSerial in locked }).filter(filter::matches)
                count += more.size
                (patched + more).takeLast(limit)
            }
            else -> {
                val job = currentCoroutineContext()[kotlinx.coroutines.Job]
                val found = logs.search(
                    emptyList(), limit = limit, skip = if (locked.isEmpty()) null else { e -> e.ctx.userSerial in locked },
                    match = filter::matches, keepGoing = { job?.isActive != false },
                )
                count = found.total
                found.entries.asReversed()
            }
        }
        // Update the state only now, after the last read. If the reload is cancelled earlier (a newer
        // change came in), the next reload reads these lines again and refreshes history, todos and
        // the hidden count for them.
        if (!incremental) {
            loadedStructure = structure
            fullAt = now
        }
        loadedTrimmed = trimmed
        removedSeen = removedSeq
        updatedSeen = updatedSeq
        logStart = start
        loaded = all
        // The newest line, with or without a filter. A pause records it as seen. It is kept when the
        // page is empty (`fresh`, `clear`), because those lines were seen before they were hidden.
        all.lastOrNull()?.let { newestLoaded = it.id }
        filtered = if (filter == null) emptyList() else shown
        filteredFor = filter
        filteredLimit = limit
        _filterCount.value = count
        _entries.value = shown
        _rows.value = withContext(Dispatchers.Default) { buildRows(shown, seenUpTo, unreadUntil) }
    }

    /** True for a line after which the todo, note, reminder or feed caches may be out of date. */
    private fun touchesProductivity(e: LogEntry): Boolean {
        // `notes rm 2` prints no numbered line, but the numbers after it changed.
        if (e.kind == LogKind.COMMAND && InstantRun.canonical(e.text.substringBefore(' ')) in PRODUCTIVITY_COMMANDS) return true
        val k = e.ctx.values.keys
        return LogContext.TODO in k || LogContext.NOTE in k || LogContext.REMINDER in k ||
            com.mtwabpin.kolahal.feeds.FeedWorker.CTX_FEED in k || com.mtwabpin.kolahal.feeds.FeedWorker.CTX_ITEM in k ||
            e.source == "remind"
    }

    /** Called when the user is back at the newest lines after scrolling up. The page shrinks again, so each reload stays small. */
    fun atNewest() {
        if (pageLimit <= PAGE) return
        pageLimit = PAGE
        requestReload()
    }

    private var rowsZone = java.time.ZoneId.systemDefault()

    /**
     * The phone's time zone changed ([told]) or may have changed. The log and a search build their
     * rows again, with each line's day in the new time zone.
     */
    fun onZoneChanged(told: Boolean) {
        val now = java.time.ZoneId.systemDefault()
        if (told || now != rowsZone) logs.touch()
        rowsZone = now
    }

    private var rowsDay: java.time.LocalDate? = null

    /** A new day started (at midnight, with the log on screen). The rows are built again for their day dividers. */
    fun onDayChanged(today: java.time.LocalDate) {
        if (rowsDay != null && today != rowsDay) logs.touch()
        rowsDay = today
    }

    /** Script lines in (seenUpTo, unreadUntil] arrived while the user was away. Later ones were seen live. */
    @Volatile private var seenUpTo = Long.MAX_VALUE
    @Volatile private var unreadUntil = Long.MAX_VALUE
    @Volatile private var pausedAt = 0L

    private val _hiddenLines = MutableStateFlow(0)
    /** Lines hidden by a fresh screen. The log shows a tap-to-restore line for them. */
    val hiddenLines: StateFlow<Int> = _hiddenLines.asStateFlow()

    fun showHidden() = io { logs.unhide() }

    // ---- log search: `/words` at the prompt -------------------------------------------------

    /** What `/words` found. [rows] are oldest first, as in the log. With no [words] yet, a hint shows. */
    data class LogSearch(val words: List<String>, val rows: List<LogRow>, val total: Int, val searched: Int, val sources: List<LogFilter> = emptyList()) {
        /** True when nothing is typed yet (or only `@`). Then a hint shows instead of results. */
        val isEmpty: Boolean get() = words.isEmpty() && sources.isEmpty()
    }

    private val _search = MutableStateFlow<LogSearch?>(null)
    /** The log search while the prompt starts with `/`. Null otherwise. */
    val search: StateFlow<LogSearch?> = _search.asStateFlow()

    private val _jumpTo = MutableStateFlow<Long?>(null)
    /** A line to scroll the log to once it is loaded (after a search result is tapped). */
    val jumpTo: StateFlow<Long?> = _jumpTo.asStateFlow()

    /** The log search that follows the prompt. Started by the init at the end. */
    private fun startSearch() {
        viewModelScope.launch {
            // - Wait for typing to settle before a search. Leaving search happens at once.
            // - A log change runs the search again, while home is shown. A search left in the prompt
            //   runs again once on return.
            // - Locking the private space hides its lines from the results too, as from the log.
            // - Lines a script changes in place (line.set) are patched into the results, without
            //   searching the whole log again ([patchSearch]).
            val locks = appsRepo.profiles.map(::lockedPrivate).distinctUntilChanged()
            // What the last whole search was for (its words, the log, the locks), and the last change
            // in place it covers.
            var searchedFor: Triple<List<String>?, Long, Set<Long>>? = null
            var updatesSeen = 0L
            reading.flatMapLatest { on ->
                if (!on) emptyFlow()
                else combine(androidx.compose.runtime.snapshotFlow { searchWords(input.text) }.distinctUntilChanged(), logs.linesVersion, locks, logs.version) { words, lines, locked, _ ->
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

    /** A search result was tapped. The search ends, and the log shows that line, loading older lines as far back as needed. */
    fun showInLog(entry: LogEntry) {
        setText("")
        _events.tryEmit(UiEvent.HideKeyboard)
        // `log only …` could hide the line, so it ends, as it does when a command runs.
        // A starred line that a clear removed from the screen shows only among the starred lines.
        val filter = if (entry.starred && logs.isCleared(entry.id)) LogFilter.OfKind(LogFilter.Kind.STARRED) else _filter.value?.takeIf { it.matches(entry) }
        filterLog(filter)
        io {
            if (logs.isHidden(entry.id)) logs.unhide()
            pageLimit = maxOf(pageLimit, (logs.countFrom(entry.id) + 20).coerceAtMost(MAX_PAGE))
            _jumpTo.value = entry.id
            requestReload()
            // If the line never shows up (deleted meanwhile, or past the page limit), cancel the pending jump.
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
        // Use the newest line, not the last shown line. After `fresh` nothing shows, and under `log only`
        // the last shown line can be old. The lines before the newest were seen anyway.
        newestLoaded?.let { logs.seenUpTo = it }
        pausedAt = System.currentTimeMillis()
    }

    /** The exact-alarm permission at the last resume. Null before the first resume. */
    @Volatile private var exactAlarms: Boolean? = null

    fun onResume() {
        // Allowing exact alarms (`remind exact`) doesn't restart the app. So schedule the pending
        // reminders again, so they no longer fire late in Doze.
        val exact = com.mtwabpin.kolahal.productivity.ReminderScheduler.canBeExact(context)
        if (exact && exactAlarms == false) io { com.mtwabpin.kolahal.productivity.ReminderScheduler.rescheduleAll(context) }
        exactAlarms = exact
        // Housekeeping runs beside the command queue, not in it: an app opened right after returning
        // mustn't wait behind a trim or the scripts' schedule.
        viewModelScope.launch(Dispatchers.IO) {
            com.mtwabpin.kolahal.data.Quietly.run(context, "Trimming the old log") { logs.trimIfDue(config[Keys.LOG_KEEP_DAYS]) }
            // Daily times drift (late runs, a daylight-saving change), so they are set again every few hours.
            com.mtwabpin.kolahal.data.Quietly.run(context, "Scheduling scripts") { ScriptScheduler.syncAllIfDue(context) }
        }
        // A crash in the script process doesn't restart home. So its report is announced here,
        // outside the command queue, so that a command typed at once doesn't wait for it.
        viewModelScope.launch(Dispatchers.IO) { runCatching { com.mtwabpin.kolahal.CrashReports.announce(context) } }
        seenUpTo = logs.seenUpTo
        // log.fresh: after some time away, home starts clean. Only lines you had already seen are
        // hidden (script lines that arrived meanwhile stay). Nothing is deleted.
        val freshMin = config[Keys.LOG_FRESH]
        val seen = seenUpTo
        val hide = freshMin > 0 && pausedAt > 0 && System.currentTimeMillis() - pausedAt >= freshMin * 60_000L && seen != Long.MAX_VALUE
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (hide) runCatching { logs.hideScreen(seen) }
                unreadUntil = runCatching { logs.maxId() }.getOrDefault(unreadUntil)
            }
            // On return after a stop, the page is read here, once, after the fresh screen is applied.
            when {
                reading.value -> requestReload()
                activityStarted -> { back = true; reading.value = true }
            }
        }
        if (_screen.value == Screen.SETUP || _screen.value == Screen.ONBOARDING) refreshSetup()
        refreshIsHome()
        unlockCounter.refresh()
        // Don't reload the app list here. It is kept, and installs, updates, removals, profiles and a
        // new language reload it when they happen ([AppRepository.start]). `restart` reads it again.
        mediaRepo.start() // picks up notification access granted while we were away
        io { tipOfTheDay() }
        // keyboard.on-home: returning from an app or the lock screen also counts as showing home.
        // The keyboard opens only for a focused window, which comes just after this ([onWindowFocused]).
        focusOnWindowFocus = true
    }

    private var focusOnWindowFocus = false

    /** When Kolahal was installed. Read once, not every time home is shown. */
    private val installedAt: Long? by lazy {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime }.getOrNull()
    }

    /**
     * `log.tips`: the first time home is shown each day, from one to fourteen days after install,
     * adds a line about a feature not used yet ([Tips]). Tapping it puts the example in the prompt.
     */
    private fun tipOfTheDay() {
        if (!config[Keys.LOG_TIPS]) return
        val prefs = context.getSharedPreferences("tips", Context.MODE_PRIVATE)
        val today = java.time.LocalDate.now().toString()
        if (prefs.getString("day", null) == today) return
        val installed = installedAt ?: return
        if ((System.currentTimeMillis() - installed) / 86_400_000L !in Tips.DAYS) return
        val shown = prefs.getStringSet("shown", emptySet()).orEmpty()
        val tip = Tips.next(logs.commandHistory(), shown, CommandsOff.names(config[Keys.COMMANDS_OFF])) ?: return
        prefs.edit { putString("day", today).putStringSet("shown", shown + tip.id) }
        // The first tip says how to turn tips off.
        val off = if (shown.isEmpty()) " (config set log.tips off to stop them)" else ""
        logs.add(LogKind.INFO, "Tip: ${tip.text} · tap to try it$off", ctx = LogContext.of(LogContext.COMMAND to tip.example.trimEnd() + " "))
    }

    /** Home's window has focus again. After [onResume], keyboard.on-home opens the keyboard on the terminal. */
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
        // An alias as the first word, then a space, expands. The line then waits for Enter, as an alias does.
        abbreviated(old, value)?.let { input = it; return }
        input = value
        // Instant run fires only while typing forward at the end of the line, so backspacing or
        // editing in the middle never fires it.
        // - Typing means one character at a time (or IME composition). Pasting a whole word is not typing.
        // - A line with arguments may also end in a word that the keyboard commits whole, with its
        //   space (`torch on `). Then only a line with nothing left to choose runs.
        val typed = value.text.length - old.length == 1 || value.composition != null
        if (searchWords(value.text) == null && value.text.length > old.length && value.selection.collapsed && value.selection.end == value.text.length &&
            (typed || value.text.trim().contains(' '))
        ) {
            instantRun(value.text)
        }
    }

    /** launch.instant: launches or runs as soon as the input can mean only one thing. */
    private fun instantRun(text: String) {
        val target = InstantRun.target(
            text, config[Keys.LAUNCH_INSTANT], config[Keys.LAUNCH_INSTANT_MIN],
            rankedApps = { appsRepo.searchRanked(it) }, appLabel = { it.label },
            commands = usages(), aliases = appPrefs.aliases(), scriptCommands = _commandNames.value,
            isOn = { !it.profile.quiet },
        ) ?: return
        setText("")
        _events.tryEmit(UiEvent.ScrollToBottom)
        when (target) {
            is InstantRun.Target.App -> io { processor.launch(target.app) }
            is InstantRun.Target.Command -> io { processor.run(target.line) }
        }
    }

    /**
     * In `launch.instant all` mode, a tapped history line runs at once. It doesn't run if it is
     * sensitive, unless it is a whole line such as `torch on`.
     */
    private fun runsOnTap(line: String) = config[Keys.LAUNCH_INSTANT] == InstantRun.ALL &&
        // A trailing space marks a prefill ("apps " from help) that waits for an argument.
        !line.endsWith(' ') &&
        (InstantRun.canRun(line, usages(), appPrefs.aliases(), _commandNames.value, tapped = true) { name ->
            appsRepo.searchRanked(name).firstOrNull()?.second == 0
        } || InstantRun.wholeLine(line, usages(), appPrefs.aliases()))

    fun submit() {
        val line = input.text
        // Enter in a log search hides the keyboard to make room for the results. Results are tapped, not run.
        if (searchWords(line) != null) {
            _events.tryEmit(UiEvent.HideKeyboard)
            return
        }
        entered = livePreview
        input = TextFieldValue("")
        historyIndex = -1
        if (line.isBlank()) return
        io {
            try {
                processor.run(line)
            } finally {
                entered = null
            }
        }
        _events.tryEmit(UiEvent.ScrollToBottom)
    }

    /** Inserts [text] at the cursor (for touch keys). */
    fun insert(text: String) {
        val v = input
        val newText = v.text.replaceRange(v.selection.min, v.selection.max, text)
        historyIndex = -1
        val value = TextFieldValue(newText, TextRange(v.selection.min + text.length))
        input = abbreviated(v.text, value) ?: value
    }

    /** [value] with an alias typed as its first word expanded ([Abbreviations]), or null. */
    private fun abbreviated(old: String, value: TextFieldValue) = Abbreviations.onSpace(old, value, processor::abbreviation)

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

    /** Backspace for the touch key bar. Deletes the selection, or the character before the cursor. */
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

    /** TAB: accepts the first suggestion. */
    fun complete() {
        val first = suggestions(input.text).firstOrNull() ?: return
        when (first) {
            is Suggestion.Completion -> pickCompletion(first.replacement, tapped = false)
            is Suggestion.App -> setText(first.app.label)
        }
    }

    /**
     * A completion fills the prompt. In `launch.instant all`, if the line then has nothing left to
     * choose, it runs (`torch on`). [tapped] is true for a tapped suggestion. A suggestion taken
     * with Tab counts as typing.
     */
    private fun pickCompletion(replacement: String, tapped: Boolean) {
        // An alias picked as the first word (`a `) expands, as it does when typed with a space.
        abbreviated(replacement.dropLast(1), TextFieldValue(replacement, TextRange(replacement.length)))?.let {
            historyIndex = -1
            input = it
            return
        }
        setText(replacement)
        if (searchWords(replacement) != null || !replacement.trim().contains(' ')) return
        // A contact tapped for `call` opens the dialer at once, as a tap in the log does. The
        // dialer still waits for Call. Any other line runs only once it is complete (`torch on`).
        val line = replacement.trim()
        if (tapped && InstantRun.canonical(line.substringBefore(' ').lowercase()) == "call" && runsOnTap(line)) {
            setText("")
            _events.tryEmit(UiEvent.ScrollToBottom)
            io { processor.run(line) }
        } else {
            instantRun(replacement)
        }
    }

    fun onSuggestion(s: Suggestion) {
        when (s) {
            is Suggestion.App -> {
                setText("")
                io { processor.launch(s.app) }
            }
            is Suggestion.Completion -> pickCompletion(s.replacement, tapped = true)
        }
    }

    /** Sets a new line in the prompt. ↑ and ↓ start again from it. */
    private fun setText(text: String) {
        historyIndex = -1
        recall(text)
    }

    private fun recall(text: String) {
        input = TextFieldValue(text, TextRange(text.length))
    }

    /** Goes up when a list the suggestions wait for has arrived (an app's activities or shortcuts): work them out again. */
    val suggestionsLoaded: StateFlow<Int> get() = completer.loaded

    fun suggestions(text: String): List<Suggestion> {
        if (searchWords(text) == null) return completer.suggestions(text)
        // `/@xk`: suggest the sources to search in.
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
        override fun runsOnTap(line: String) = this@TerminalViewModel.runsOnTap(line)
        override fun runCommand(cmd: String) = this@TerminalViewModel.runCommand(cmd)
        override fun openEditorAt(script: Script, line: Int?) = this@TerminalViewModel.openEditorAt(script, line)
        override fun startActivity(intent: Intent) = this@TerminalViewModel.startActivity(intent)
        override fun filterLog(filter: LogFilter?) = this@TerminalViewModel.filterLog(filter)
    })

    /** Single tap: runs the most likely action for this line. */
    fun onTap(entry: LogEntry) = lineActions.onTap(entry)

    /**
     * A tap on button [index] under a script's line (`ctx.actions`). The line's own script runs
     * again with that button's arguments and `event` `{ name = "action", label }`, never any other
     * command. The line then shows the button as done, so it isn't run twice; a second tap within
     * 2 seconds does nothing either.
     */
    fun runLineAction(entry: LogEntry, index: Int) {
        val name = entry.ctx.script ?: return
        if (entry.ctx.values[LogContext.ACTION_DONE] != null) return
        val (label, args) = LogContext.actionsOf(entry.ctx).getOrNull(index) ?: return
        if (!itemTaps.allow("action:${entry.id}")) return
        io {
            val script = scripts.byName(name) ?: return@io run { logs.add(LogKind.ERROR, "No script named '$name' — 'script ls'") }
            if (!script.enabled) return@io run { logs.add(LogKind.INFO, "$name is off: script on $name", ctx = LogContext.of(LogContext.COMMAND to "script on $name")) }
            logs.update(entry.id, entry.text, entry.ctx + mapOf(LogContext.ACTION_DONE to index.toString()))
            ScriptScheduler.runNow(context, script, args.split(Regex("\\s+")).filter { it.isNotEmpty() }, mapOf("name" to "action", "label" to label))
        }
    }

    /** Long-press: returns every action that makes sense for the saved context. Destructive ones come last. */
    fun actionsFor(entry: LogEntry): List<LogAction> = lineActions.actionsFor(entry)

    /** The actions for one app, for log lines and grid chips. */
    fun appActions(app: AppEntry): List<LogAction> = lineActions.appActions(app)

    /** Returns the apps in a grid line. */
    fun appsOf(entry: LogEntry): List<AppEntry> = lineActions.appsOf(entry)

    fun launchApp(app: AppEntry) = io { processor.launch(app) }

    /** Keys of favourite apps. The app selector lists them first, with ★. */
    fun pinnedKeys(): Set<String> = appPrefs.pinned().toSet()

    /** One serial worker. Commands and actions run in order. Failures are logged and never crash home. */
    private val serialIo = Dispatchers.IO.limitedParallelism(1)
    private val errors = CoroutineExceptionHandler { _, e ->
        runCatching { logs.add(LogKind.ERROR, "Error: ${e.message ?: e.javaClass.simpleName}") }
    }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch(serialIo + errors) { block() }
    }

    private val _undo = MutableStateFlow<UndoOffer?>(null)
    /** A line that a swipe just deleted, or a screen just cleared. The terminal offers Undo for a few seconds. */
    val undo: StateFlow<UndoOffer?> = _undo.asStateFlow()
    private var undoJob: kotlinx.coroutines.Job? = null

    /**
     * Deletes and restores run here, not on the command queue. A slow command must not leave the
     * swiped line's gap on screen.
     */
    private val deleteIo = Dispatchers.IO.limitedParallelism(1)

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

    /** A tap on a link in a line's text (`fmt.link`). Only web links open: scripts can link to nothing else. */
    fun openUrl(url: String) {
        val web = com.mtwabpin.kolahal.script.Net.webUrl(url) ?: return
        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(web)))
    }

    /** Swipe right on a log line. */
    fun swipeCopy(entry: LogEntry) = lineActions.swipeCopy(entry)

    private fun startActivity(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { io { logs.add(LogKind.ERROR, "Can't open: ${it.message}") } }
    }

    // ---- script editor --------------------------------------------------------------------

    /**
     * Saves the editor's text. [onDone] then gets:
     * - the error, or null once the text is written;
     * - what saving changed beyond the code ([saveCode]).
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
     * Saves [code] and runs the script, with [args] and as [event] when given (the editor's "Run with").
     * [onDone] gets the save error, if any, and what saving changed.
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
     * Writes [code] from the editor, and applies its `@every` and `@at` lines to the script's timers:
     * - A new or changed line sets the timers.
     * - A removed line undoes what it had set. A timer set in the sheet stays.
     * - The timers are synced in every case, because whether the script waits for a network depends on the code.
     *
     * Returns what changed, such as "runs every 1h", or null.
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

    /** Returns what [script] logged from [since] on, oldest first. This is a run's output, for the editor. */
    suspend fun runLines(script: String, since: Long): List<LogEntry> = withContext(Dispatchers.IO) {
        logs.recent(limit = 200).filter { it.source == script && it.ts >= since }
    }

    /**
     * Header and pinned items that run a command or a script, or log a line: each runs at most once
     * in 2 seconds, so quick repeated taps log once. Items that only open a screen, and the music
     * controls, are not held back.
     */
    private val itemTaps = TapGate(2_000) { android.os.SystemClock.elapsedRealtime() }

    fun onHeaderTap(target: HeaderTarget) {
        when (target) {
            HeaderTarget.CLOCK, HeaderTarget.ALARM -> startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            HeaderTarget.UNLOCKS -> if (itemTaps.allow("header:unlocks")) runCommand("unlocks 7")
            HeaderTarget.BATTERY -> startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            HeaderTarget.NETWORK -> startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            HeaderTarget.SETTINGS -> openScreen(Screen.SETTINGS)
            HeaderTarget.TODO -> if (itemTaps.allow("header:todo")) runCommand("todo")
        }
    }

    /** A tap on a pinned script's line: runs the script, at most once in 2 seconds ([itemTaps]). */
    fun onPinTap(name: String) {
        if (itemTaps.allow("pin:$name")) runScriptNamed(name)
    }

    /**
     * Quick actions (tap `$` on the prompt, or the `quick` gesture): everything the header offers,
     * within thumb reach at the bottom of the screen.
     */
    fun openQuick() { _events.tryEmit(UiEvent.ShowQuick) }

    fun quickActions(): List<LogAction> = buildList {
        // Use the header's icons where it has them (battery and network as the header shows them now).
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
            // As in the header: the profile's colour while it runs, dim while it is paused.
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

    /** Resuming is harmless, so it runs at once. Pausing needs a second tap, on the logged line. */
    fun onProfileTap(profile: Profile) {
        if (!itemTaps.allow("profile:${profile.kind}")) return
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

    /**
     * Runs [cmd] as if typed, but without alias expansion. [byScript] is true when a script sent it;
     * then the scripts it starts can't send more commands, and a command turned off doesn't run.
     * Otherwise Kolahal sent it (a button, a header tap), so it runs even when turned off.
     */
    fun runCommand(cmd: String, byScript: Boolean = false) {
        _events.tryEmit(UiEvent.ScrollToBottom)
        io { if (byScript) ScriptScheduler.sentByScript { processor.run(cmd, expand = false) } else processor.run(cmd, expand = false, fromApp = true) }
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
            // Locking needs no log line. Report only when the accessibility service is off.
            // Without the service, explain once per session, not on every accidental double-tap.
            // This is behind the build switch, so the Play build also drops the service's code.
            g.LOCK -> if (!(com.mtwabpin.kolahal.BuildConfig.LOCK_SERVICE && com.mtwabpin.kolahal.gesture.LockService.lock()) && !lockHintShown) {
                lockHintShown = true
                runCommand("lock")
            }
            // Gesture actions may be aliases, so they expand like typed input.
            else -> {
                _events.tryEmit(UiEvent.ScrollToBottom)
                io { processor.run(action) }
            }
        }
    }

    /**
     * Closes the editor. [unsaved] is the editor's text when it had changes. It is saved first,
     * and the result is shown on the screen you return to.
     */
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

    /**
     * A message for the GUI screens. [seq] makes the same text shown twice count as two messages, so both show.
     * With [undo], the page offers Undo for a while.
     */
    data class Notice(val text: String, val seq: Long, val undo: (() -> Unit)? = null)

    private val _notice = MutableStateFlow<Notice?>(null)
    private var noticeSeq = 0L
    /** A short result message shown on the GUI screens (also logged to the terminal). */
    val notice: StateFlow<Notice?> = _notice.asStateFlow()

    private fun show(text: String, undo: (() -> Unit)? = null) {
        _notice.value = Notice(text, ++noticeSeq, undo)
    }

    /** Pages opened from other pages (Settings → Script manager). Back returns through them in order. */
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

    /** Settings, Feeds and Scripts are tabs of one place. Switching replaces the screen, so Back leaves all of them. */
    fun switchTab(screen: Screen) {
        _notice.value = null
        _peek.value = null
        // Next time, Settings opens on its main page, not on the category that was open when the tab changed.
        settingsCategory.value = null
        _screen.value = screen
        if (screen == Screen.SCRIPTS) scriptActions.refreshScripts()
    }

    private val _setup = MutableStateFlow<List<SetupCheck>>(emptyList())
    val setupChecks: StateFlow<List<SetupCheck>> = _setup.asStateFlow()

    fun refreshSetup() = io { _setup.value = SetupChecks.run(context) }

    private val _isHome = MutableStateFlow(true)

    /** Whether Kolahal is the home app. Checked at each resume; home shows a band while it isn't. */
    val isHome: StateFlow<Boolean> = _isHome.asStateFlow()

    /** Checks off the main thread: it is a call to the system, on the way back to home. */
    fun refreshIsHome() = io { _isHome.value = SetupChecks.isDefaultLauncher(context) }

    /** Opens the system page for a check. The list refreshes when the user comes back. */
    fun fixSetup(check: SetupCheck) {
        check.fix?.let { cmd -> io { processor.run(cmd, expand = false, fromApp = true) } }
    }

    /** Back from a page: returns to the page it was opened from, or to the terminal. */
    fun closeScreen() {
        _notice.value = null
        val previous = synchronized(backStack) { backStack.removeLastOrNull() } ?: Screen.TERMINAL
        // A category stays open only while a manager screen opened from it is on top, not after going home.
        if (_screen.value == Screen.SETTINGS && previous == Screen.TERMINAL) settingsCategory.value = null
        _screen.value = previous
        if (previous == Screen.SCRIPTS) scriptActions.refreshScripts()
        if (previous == Screen.SETUP || previous == Screen.ONBOARDING) refreshSetup()
    }

    private fun notice(text: String, error: Boolean = false, undo: (() -> Unit)? = null) {
        show(text, undo)
        logs.add(if (error) LogKind.ERROR else LogKind.INFO, text)
    }

    /**
     * File operations run outside the serial command queue, because SAF providers can be slow
     * (e.g. cloud storage). Failures show as a notice on the GUI page, not only in the terminal log.
     */
    private fun fileOp(label: String, block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { block() }.onFailure { notice("$label failed: ${it.message ?: it.javaClass.simpleName}", error = true) }
        }
    }

    override fun askSecret(script: Script, name: String) = scriptActions.askSecret(script, name)

    override fun askBackupFolder(thenBackUp: Boolean) = backupActions.askBackupFolder(thenBackUp)

    fun setConfig(key: ConfigKey<*>, raw: String): Boolean = config.set(key, raw).also { set ->
        // Automatic backups need a folder, so ask for one.
        if (set && key == Keys.BACKUP_AUTO && raw != "off" && com.mtwabpin.kolahal.data.AutoBackup.folder(context) == null) askBackupFolder(false)
    }

    fun resetConfig(key: ConfigKey<*>) = config.reset(key)

    // ---- TerminalUi -----------------------------------------------------------------------

    override fun openEditor(script: Script) = openEditorAt(script, null)

    override fun requestContactsPermission() {
        _events.tryEmit(UiEvent.RequestContactsPermission)
    }

    /** What to do with the contact being picked. Null once the picker returns (or after the process restarted). */
    @Volatile private var contactPicked: ((com.mtwabpin.kolahal.contacts.Contact) -> Unit)? = null

    override fun pickContact(then: (com.mtwabpin.kolahal.contacts.Contact) -> Unit) {
        contactPicked = then
        _events.tryEmit(UiEvent.PickContact)
    }

    /**
     * Android's contact picker returned [uri], or null when nothing was picked. The contact's numbers
     * are read now, while the picker allows it, and remembered for the next time the name is typed.
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

    /** Settings → Your themes → Import a theme: imports a theme someone shared, saves it with yours, and uses it. */
    fun importTheme(uri: Uri?) {
        if (uri == null) return
        fileOp("theme import") {
            val (name, added) = CustomThemes.import(context, Files.readText(context, uri), Files.displayName(context, uri))
            withContext(Dispatchers.Main) { config.set(Keys.THEME, name) }
            notice(if (added) "Theme $name imported and in use" else "You already have theme $name: it's in use")
        }
    }

    /** The theme builder's Export: writes one of your themes, as last saved, to a file to share. */
    fun exportTheme(name: String, uri: Uri?) {
        if (uri == null) return
        fileOp("theme export") {
            val colors = CustomThemes.colors(name) ?: throw IllegalStateException("$name was deleted")
            Files.writeText(context, uri, CustomThemes.encode(colors, name))
            notice("Exported theme $name")
        }
    }

    @Volatile private var fontErrorShown = false

    /** The configured font could not be loaded (e.g. custom is set but the file is gone). Reported once per run. */
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
        io { logs.hideScreen() }
    }

    // ---- productivity (todo, notes, reminders, agenda) --------------------------------------

    private val _todoCount = MutableStateFlow(0)
    /** The number of open todos, for the header's todo item. */
    val todoCount: StateFlow<Int> = _todoCount.asStateFlow()

    /** Data for suggestions. Refreshed with the log, because every todo / note / remind command writes to the log. */
    @Volatile private var todoCache: List<com.mtwabpin.kolahal.productivity.Todo> = emptyList()
    @Volatile private var noteCache: List<com.mtwabpin.kolahal.productivity.Note> = emptyList()
    @Volatile private var reminderCache: List<com.mtwabpin.kolahal.productivity.Reminder> = emptyList()
    @Volatile private var feedCache: List<com.mtwabpin.kolahal.feeds.FeedInfo> = emptyList()

    /** Contacts with numbers (empty without the permission). Suggestions are computed again when they load. */
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
    /** True while Kolahal asks before Android's Notification access screen opens ([askNotificationAccess]). */
    val notificationAccessAsk: StateFlow<Boolean> = _notificationAccessAsk.asStateFlow()

    override fun askNotificationAccess() {
        _notificationAccessAsk.value = true
    }

    /** The user's answer. Allow opens Android's screen. Not now opens nothing and leaves a line to ask again. */
    fun answerNotificationAccess(allow: Boolean) {
        _notificationAccessAsk.value = false
        if (allow) {
            startActivity(com.mtwabpin.kolahal.notifications.NotificationActions.settingsIntent(context))
        } else {
            io { logs.add(LogKind.INFO, "Notification access is off · tap to turn it on", ctx = LogContext.of(LogContext.RUN to "notifs enable")) }
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
    /** Goes up by one on every Home press while already home. Then the widgets pager returns to the terminal. */
    val homePresses: StateFlow<Int> = _homePresses.asStateFlow()

    /**
     * Settings shows home through itself, so you can see a look setting while you change it:
     * - while a slider is dragged ([key] stays visible);
     * - while the page's eye button is held ([key] is null).
     * [typing]: show home as if typing, for the key bar, prompt and suggestions.
     */
    data class Peek(val key: String?, val typing: Boolean)

    private val _peek = MutableStateFlow<Peek?>(null)
    val peek: StateFlow<Peek?> = _peek.asStateFlow()

    fun peek(value: Peek?) {
        _peek.value = value
    }

    /** The open Settings category page. It is kept while a manager screen opened from it is on top. */
    val settingsCategory = mutableStateOf<String?>(null)

    /** The custom theme that the theme builder edits. Null while it creates a new one. */
    val themeBuilderName = mutableStateOf<String?>(null)

    fun openThemeBuilder(name: String?) {
        themeBuilderName.value = name
        openScreen(Screen.THEME_BUILDER)
    }

    /**
     * The position of the log's app selector in the window: the log's area between the header and
     * the prompt. The widgets page puts its selector's strip in the same place, so selector.position
     * and selector.height put it under the same thumb on both pages. Its list uses the whole page.
     * Only the vertical position and the height are used. The horizontal position changes on every
     * frame of a swipe between the pages, and the widgets page would be built again each time.
     */
    var selectorArea by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)

    /**
     * How far the log is faded out while the app selector is open: 0 shown, 1 gone. The selector sets
     * it; the log reads it in the draw phase. So the wallpaper or the theme's background shows behind
     * the list, not the log.
     */
    val selectorFade = androidx.compose.runtime.mutableFloatStateOf(0f)

    /** The same for the widgets page: its widgets fade out while its app selector is open. */
    val widgetsSelectorFade = androidx.compose.runtime.mutableFloatStateOf(0f)

    /** widgets.start: home opens on the widgets page, and Home and Back return to it. */
    private fun widgetsFirst() = Keys.widgetsFirst(config)

    /** True when home shows the log page. (The widgets page can be first, see [widgetsFirst].) */
    @Volatile private var logShown = !widgetsFirst()

    /**
     * Home's pager settled on a page. With the widgets page first, keyboard.on-home opens the
     * keyboard on reaching the log (where the prompt is), and the widgets page hides it.
     * [opened] is true when the user turned to the page, not when the same page is only reported again.
     */
    fun onHomePageShown(log: Boolean, opened: Boolean = true) {
        logShown = log
        if (!opened || _screen.value != Screen.TERMINAL || _editing.value != null) return
        if (!log) _events.tryEmit(UiEvent.HideKeyboard)
        else if (config[Keys.FOCUS_ON_HOME]) _events.tryEmit(UiEvent.FocusPrompt)
    }

    /** Pressing Home while already home resets the prompt. */
    fun onHomePressed() {
        settingsCategory.value = null
        _homePresses.value++
        _editing.value?.let { return }
        val fromOtherScreen = _screen.value != Screen.TERMINAL
        openScreen(Screen.TERMINAL) // Home always lands on the terminal and forgets the page stack
        escape()
        // With the widgets page first, Home goes there. It has no prompt, so don't open the keyboard.
        val focus = config[Keys.FOCUS_ON_HOME] && !widgetsFirst()
        if (fromOtherScreen) {
            // The terminal isn't composed yet, so an event now would have no collector. The terminal
            // picks these up when it appears (consumeArrival).
            arrival = Arrival(focus = focus)
        } else {
            _events.tryEmit(UiEvent.ScrollToBottom)
            if (focus) _events.tryEmit(UiEvent.FocusPrompt)
        }
    }

    /** What the terminal should do when it is on screen again after Home was pressed on another page. */
    data class Arrival(val focus: Boolean)

    @Volatile private var arrival: Arrival? = null

    fun consumeArrival(): Arrival? = arrival.also { arrival = null }

    /**
     * The view model's one init, last in the class. Everything it starts runs at once: viewModelScope
     * runs a launch up to its first suspension right here, and the work on other threads doesn't wait.
     * So every field must be set by then. With inits spread through the class, one that started work
     * early could find fields further down still unset (a first-run crash once came from that).
     * Keep it last, and start new work from here.
     */
    init {
        startWatching()
        startSearch()
        // Lines hidden by a clear are deleted once their Undo is gone. When the launcher closed, its Undo went too.
        viewModelScope.launch(deleteIo) { runCatching { logs.purgeCleared() } }
        checkFirstRun()
    }

    /** On the first start: a welcome and the tour, which sets up the first few things. */
    private fun checkFirstRun() {
        viewModelScope.launch(Dispatchers.IO) {
            // Use a persistent flag, so `clear all` doesn't look like a first run.
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
