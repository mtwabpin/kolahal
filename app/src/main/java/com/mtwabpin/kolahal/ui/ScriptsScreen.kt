package com.mtwabpin.kolahal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.terminal.CommandParsing
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

internal val INTERVALS = listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 60 to "1 hour", 180 to "3 hours", 360 to "6 hours", 720 to "12 hours", 1440 to "1 day")

/** A library script with its state against what is installed, and its `@on` events. */
internal class LibRow(val entry: ScriptLibrary.Entry, val state: ScriptLibrary.State, val events: List<String>)

/** What a script's code says, for its card and the filters: read once per version of the code. */
internal class ScriptCode(code: String, name: String) {
    val title = ScriptMeta.title(code)
    val desc = ScriptMeta.description(code)
    val isCommand = ScriptMeta.isCommand(code)
    val usage = if (isCommand) ScriptMeta.usage(code, name) else null
    val events = ScriptMeta.events(code)
    val uses = ScriptMeta.uses(code)
    val secrets = ScriptMeta.secrets(code)
    val notifies = ScriptMeta.postsNotifications(code)
    /** Which of internet and a status line it uses ([ScriptAccess.base]). */
    val base = ScriptAccess.base(code).toSet()
    /** What its sheet and expanded card list: its access, then its events ([ScriptAccess.items]). */
    val accessItems = ScriptAccess.items(code)
}

/**
 * The [ScriptCode] of each script as last shown, by name and code: kept while the page is away (the
 * editor replaces it, and coming back after a save would read every script again). Main thread.
 */
private val keptCodes = HashMap<Pair<String, String>, ScriptCode>()

/** What a script's card shows and the filters match. */
internal class ScriptInfo(val script: Script, code: ScriptCode) {
    val title = code.title
    val desc = code.desc
    val isCommand = code.isCommand
    val usage = code.usage
    val events = code.events
    val uses = code.uses
    val timed = script.intervalMin > 0 || script.atTimes.isNotEmpty()
    val secrets = code.secrets
    val notifies = code.notifies
    /** [Script.grants], from the code as read once. */
    val grants = ScriptAccess.Grants(code.uses, script.access, code.base)
    val accessItems = code.accessItems
}

/**
 * What a filter can ask about a script beyond its code: a secret not set yet, a library copy that
 * differs from this version's (edited, or from an older Kolahal).
 */
private class ScriptState(val needsKey: Boolean, val differs: Boolean)

/** A script's `@secret` names: those with no value yet, and those set, in the order declared. */
private class SecretNames(val missing: List<String>, val set: List<String>)

/** A filter chip: its name and the scripts it keeps. */
private class ScriptFilter(val id: String, val label: String, val keeps: (info: ScriptInfo, state: ScriptState) -> Boolean)

/** Short names for what scripts use, in chips. */
internal val USES_SHORT = mapOf("notifications" to "notifications", "clipboard" to "clipboard", "apps" to "apps", "commands" to "commands", "dnd" to "Do Not Disturb", "work" to "work profile")

private val SCRIPT_FILTERS = listOf(
    ScriptFilter("commands", "Commands") { s, _ -> s.isCommand },
    ScriptFilter("timers", "Timers") { s, _ -> s.timed },
    ScriptFilter("events", "Events") { s, _ -> s.events.isNotEmpty() },
    ScriptFilter("pinned", "Pinned") { s, _ -> s.script.grants.allows(ScriptAccess.HEADER) },
    ScriptFilter("needs-key", "Needs a key") { _, st -> st.needsKey },
    ScriptFilter("failed", "Failed") { s, _ -> s.script.failed() },
    ScriptFilter("off", "Off") { s, _ -> !s.script.enabled },
    ScriptFilter("differs", "Differs from the library") { _, st -> st.differs },
) + ScriptMeta.USES.map { what -> ScriptFilter("uses:$what", "Uses ${USES_SHORT[what] ?: what}") { s, _ -> what in s.uses } }

/**
 * Script manager (Material 3 Expressive), the Scripts tab: a card per script, compact (its title,
 * kind, timers and last run on one line) or expanded (its status line, next run, usage, events and
 * access), filtered by chips and search. Add script holds new, library, shared and imported ones;
 * a card opens the script's sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptsScreen(
    vm: TerminalViewModel,
    onImportFiles: () -> Unit,
    onExportScript: (Script) -> Unit,
) {
    val scripts by vm.scriptList.collectAsStateWithLifecycle()
    val statuses by vm.statuses.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var sheetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var consentFor by remember { mutableStateOf<Script?>(null) }
    var resetting by remember { mutableStateOf<LibRow?>(null) }
    // The script whose copy Compare shows against the library version.
    var comparing by remember { mutableStateOf<Long?>(null) }
    var filters by rememberSaveable { mutableStateOf(listOf<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    // Details: all cards open or closed, and the ones you flipped from that.
    var expandAll by rememberSaveable { mutableStateOf(false) }
    var flipped by rememberSaveable { mutableStateOf(listOf<Long>()) }
    BackHandler {
        when {
            searching -> { searching = false; query = "" }
            else -> vm.closeScreen()
        }
    }

    // Switching on a script that declares what nobody allowed yet asks first, with everything on.
    fun setEnabled(s: Script, on: Boolean) {
        if (on && s.grants.undecided.isNotEmpty()) consentFor = s else vm.scriptActions.setScriptEnabled(s, on)
    }

    // Back to the library version: an edited copy's edits go, so it asks first; an older copy of yours had none.
    fun resetFromLibrary(row: LibRow) {
        if (row.state == ScriptLibrary.State.MODIFIED) resetting = row else vm.scriptActions.installFromLibrary(row.entry)
    }

    // The library scripts and their directives come from assets; read them off the main thread.
    val library by produceState<List<LibRow>?>(null, vm.scriptActions.library, scripts) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                vm.scriptActions.library.entries.map { LibRow(it, vm.scriptActions.library.state(it, scripts), ScriptMeta.events(vm.scriptActions.library.code(it))) }
            }.getOrDefault(emptyList())
        }
    }
    val libByName = remember(library) { library.orEmpty().associateBy { it.entry.name } }
    // Shared scripts with a newer version, from the list kept by Get more scripts (no download here).
    val sharedUpdates by produceState(emptyMap(), scripts) {
        value = withContext(Dispatchers.IO) {
            vm.scriptActions.scriptCatalog.cached().orEmpty()
                .filter { vm.scriptActions.scriptCatalog.state(it, scripts) == ScriptCatalog.State.UPDATE_AVAILABLE }.associateBy { it.name }
        }
    }

    // The list is read again when a script runs (its last run changes) or one is saved: only code
    // that changed is read again, also after the page was away.
    val infos = remember(scripts) {
        keptCodes.keys.retainAll(scripts.mapTo(HashSet()) { it.name to it.code })
        scripts.map { s -> ScriptInfo(s, keptCodes.getOrPut(s.name to s.code) { ScriptCode(s.code, s.name) }) }
    }
    // Declared secrets with and without a value, by script name: the same lists each time the
    // page is built again, so a card can skip when nothing of its own changed.
    val secretsVersion by vm.scriptActions.secretsVersion.collectAsStateWithLifecycle()
    val secretNames by produceState(emptyMap<String, SecretNames>(), infos, secretsVersion) {
        value = withContext(Dispatchers.IO) {
            infos.filter { it.secrets.isNotEmpty() }.associate { i ->
                val declared = i.secrets.map { it.name }
                val set = vm.scriptActions.secretsSet(i.script)
                i.script.name to SecretNames(missing = declared.filter { it !in set }, set = declared.filter { it in set })
            }
        }
    }
    fun stateOf(info: ScriptInfo) = ScriptState(
        secretNames[info.script.name]?.missing.orEmpty().isNotEmpty(),
        libByName[info.script.name]?.state.let { it == ScriptLibrary.State.MODIFIED || it == ScriptLibrary.State.UPDATE_AVAILABLE },
    )
    // Only chips something matches, with how many; a selected one stays, so it can be unselected.
    val chips = remember(infos, statuses, secretNames, libByName, filters) {
        SCRIPT_FILTERS.map { f -> f to infos.count { f.keeps(it, stateOf(it)) } }.filter { it.second > 0 || it.first.id in filters }
    }
    val shown = remember(infos, statuses, secretNames, libByName, filters, query) {
        val active = SCRIPT_FILTERS.filter { it.id in filters }
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        infos.filter { info ->
            val text = listOfNotNull(info.script.name, info.title, info.desc).joinToString(" ").lowercase()
            active.all { it.keeps(info, stateOf(info)) } && words.all { it in text }
        }
    }
    val on = scripts.count { it.enabled }

    GuiTheme {
        val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it.text) } }

        Scaffold(
            modifier = Modifier.imePadding().nestedScroll(scroll.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                LargeFlexibleTopAppBar(
                    title = { Text("Scripts") },
                    subtitle = {
                        Text(
                            if (scripts.isEmpty()) "Lua 5.4 · JavaScript"
                            else listOfNotNull("$on on", (scripts.size - on).takeIf { it > 0 }?.let { "$it off" }).joinToString(" · ")
                        )
                    },
                    navigationIcon = { BackButton(vm::closeScreen) },
                    actions = {
                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = { searching = !searching; if (!searching) query = "" }) {
                            Icon(painterResource(R.drawable.ic_search), contentDescription = "Search scripts")
                        }
                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = { expandAll = !expandAll; flipped = emptyList() }) {
                            Icon(
                                painterResource(R.drawable.ic_expand_more),
                                contentDescription = if (expandAll) "Hide details" else "Show details",
                                modifier = Modifier.rotate(if (expandAll) 180f else 0f),
                            )
                        }
                    },
                    scrollBehavior = scroll,
                )
            },
            floatingActionButton = {
                SmallExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(painterResource(R.drawable.ic_add), null) },
                    text = { Text("Add script") },
                )
            },
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().navigationBarScrim(),
            ) {
                item(key = "tabs") { FeatureTabs(vm, Screen.SCRIPTS) }
                if (searching) {
                    item(key = "search") {
                        TextField(
                            colors = fieldColors(),
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search by name, title or description") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                            singleLine = true,
                            shape = TextFieldDefaults.roundedShape,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if ((scripts.size > 1 && chips.isNotEmpty()) || filters.isNotEmpty()) {
                    // One row, scrolled sideways: the chips grow with what scripts use.
                    item(key = "chips") {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((f, count) in chips) {
                                val selected = f.id in filters
                                FilterChip(
                                    selected = selected,
                                    onClick = { filters = if (selected) filters - f.id else filters + f.id },
                                    label = { Text("${f.label} $count") },
                                )
                            }
                        }
                    }
                }
                when {
                    scripts.isEmpty() -> item(key = "empty") {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                "No scripts yet. Write one, add one from the library, get a shared one, or import a file.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                            )
                            ActionGroup(addActions(vm, onNew = { creating = true }, onImportFiles = onImportFiles))
                        }
                    }
                    shown.isEmpty() -> item(key = "nomatch") {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("No script matches", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = { filters = emptyList(); query = "" }) { Text("Show all") }
                        }
                    }
                    else -> items(shown, key = { it.script.id }) { info ->
                        val s = info.script
                        ScriptCard(
                            info,
                            line = statuses[s.name],
                            missingKeys = secretNames[s.name]?.missing.orEmpty(),
                            secretsSet = secretNames[s.name]?.set.orEmpty(),
                            expanded = expandAll != (s.id in flipped),
                            updateAvailable = libByName[s.name]?.state == ScriptLibrary.State.UPDATE_AVAILABLE || s.name in sharedUpdates,
                            library = libByName[s.name]?.state?.takeIf { it != ScriptLibrary.State.NOT_INSTALLED },
                            onExpand = { flipped = if (s.id in flipped) flipped - s.id else flipped + s.id },
                            onClick = { sheetId = s.id },
                            onEnabled = { setEnabled(s, it) },
                            onRun = { vm.scriptActions.runScriptNow(s) },
                            onAccess = { item, on -> vm.scriptActions.setScriptAccess(s, item, on) },
                            onNotify = { vm.scriptActions.setScriptNotify(s, it) },
                            onCompare = { comparing = s.id },
                            onReset = { libByName[s.name]?.let(::resetFromLibrary) },
                        )
                    }
                }
                if (scripts.isNotEmpty()) {
                    item(key = "hint") {
                        Text(
                            "Tap a script for its timers, access, code and more. The arrow shows its details here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
                        )
                    }
                }
            }
        }

        val sheetScript = sheetId?.let { id -> scripts.firstOrNull { it.id == id } }
        if (sheetScript != null) {
            ScriptSheet(
                vm, sheetScript, libByName[sheetScript.name], sharedUpdates[sheetScript.name],
                onEnabled = { setEnabled(sheetScript, it) },
                onDismiss = { sheetId = null },
                onEdit = { sheetId = null; vm.openEditor(sheetScript) },
                onExport = { onExportScript(sheetScript) },
            )
        }

        if (adding) {
            ModalBottomSheet(onDismissRequest = { adding = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    ActionGroup(addActions(vm, onNew = { adding = false; creating = true }, onImportFiles = { adding = false; onImportFiles() }, close = { adding = false }))
                }
            }
        }

        resetting?.let { lib ->
            ResetDialog(lib, onDismiss = { resetting = null }) { resetting = null; vm.scriptActions.installFromLibrary(lib.entry) }
        }

        val compared = comparing?.let { id -> scripts.firstOrNull { it.id == id } }
        val comparedLib = compared?.let { libByName[it.name] }?.takeIf { it.state.differs() }
        if (compared != null && comparedLib != null) {
            CompareDialog(vm, compared, comparedLib, onDismiss = { comparing = null }) { comparing = null; resetFromLibrary(comparedLib) }
        }

        consentFor?.let { s ->
            AccessDialog("Turn on ${s.name}.${s.lang.ext}?", s.grants.undecided, "Turn on", onDismiss = { consentFor = null }) { choices ->
                consentFor = null
                vm.scriptActions.enableScript(s, choices)
            }
        }

        if (creating) NewScriptDialog(scripts.map { it.name }.toSet(), onDismiss = { creating = false }) { name, lang, command ->
            creating = false
            vm.scriptActions.newScript(name, lang, command)
        }
    }
}

/** The ways to add a script: Add script's sheet, and the Scripts tab while it is empty. */
private fun addActions(vm: TerminalViewModel, onNew: () -> Unit, onImportFiles: () -> Unit, close: () -> Unit = {}) = listOf(
    Action(R.drawable.ic_add, "New script", "Lua or JavaScript, from a template", onClick = onNew),
    Action(R.drawable.ic_code, "From the library", "Ready-made scripts that come with Kolahal", chevron = true) {
        close()
        vm.openScreen(Screen.SCRIPT_LIBRARY)
    },
    Action(R.drawable.ic_download, "Get more scripts", "Shared by people who use Kolahal, on GitHub", chevron = true) {
        close()
        vm.openScreen(Screen.GET_SCRIPTS)
    },
    Action(R.drawable.ic_upload, "Import a file", ".lua or .js; the file name becomes the script name", onClick = onImportFiles),
)

/** How [s] gets started: `command · every 1h · daily 08:00 · on boot`, or "run by hand". */
internal fun schedule(s: Script, isCommand: Boolean, events: List<String>): String = buildList {
    if (isCommand) add("command")
    if (s.intervalMin > 0) add("every ${CommandParsing.formatInterval(s.intervalMin)}")
    if (s.atTimes.isNotEmpty()) add("daily ${s.atTimes.joinToString(", ")}")
    if (events.isNotEmpty()) add("on ${events.joinToString(", ")}")
}.joinToString(" · ").ifEmpty { "manual" }

internal fun Script.failed() = lastStatus?.let { it.startsWith("error") || it.startsWith("stopped") || it == "timeout" } == true

/** When a switched-on script runs next on its own: its next daily time, or about one interval after its last run. */
internal fun nextRun(s: Script): Pair<Long, Boolean>? {
    if (!s.enabled) return null
    val now = System.currentTimeMillis()
    // To the minute: the daily time itself, not a few seconds before it.
    val daily = s.atTimes.minOfOrNull { ((now + ScriptScheduler.millisUntil(it)) / 60_000.0).roundToLong() * 60_000 }
    val every = if (s.intervalMin > 0) maxOf(now, (s.lastRun ?: now) + s.intervalMin * 60_000L) else null
    return when {
        daily != null && (every == null || daily <= every) -> daily to true
        every != null -> every to false
        else -> null
    }
}
