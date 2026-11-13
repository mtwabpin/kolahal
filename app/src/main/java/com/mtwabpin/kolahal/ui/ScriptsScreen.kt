package com.mtwabpin.kolahal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.commands.CommandParsing
import com.mtwabpin.kolahal.terminal.ScriptActions
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

internal val INTERVALS = listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 60 to "1 hour", 180 to "3 hours", 360 to "6 hours", 720 to "12 hours", 1440 to "1 day")

/**
 * A library script, its state compared with the installed copy, and its `@on` events. [newer]: the
 * library has a newer version than the one the copy came from ([ScriptLibrary.hasNewer]).
 */
internal class LibRow(val entry: ScriptLibrary.Entry, val state: ScriptLibrary.State, val events: List<String>, val newer: Boolean = false)

/**
 * The library's scripts, each compared with [scripts]. Their files and directives come from assets
 * and are read off the main thread. The value is null until they are read.
 */
@Composable
internal fun rememberLibraryRows(vm: TerminalViewModel, scripts: List<Script>): State<List<LibRow>?> =
    produceState<List<LibRow>?>(null, vm.scriptActions.library, scripts) {
        value = withContext(Dispatchers.IO) {
            val library = vm.scriptActions.library
            runCatching {
                library.entries.map { e ->
                    val state = library.state(e, scripts)
                    LibRow(e, state, ScriptMeta.events(library.code(e)), library.hasNewer(e, state))
                }
            }.getOrDefault(emptyList())
        }
    }

/**
 * A library or shared copy, compared with where it came from.
 * - [edited]: you changed its code since it was installed, so Update or Reset asks first.
 * - [lib]: the library script, for a library copy.
 * - [shared]: the shared script in the list kept from Get more scripts, for a shared copy. Null when
 *   the list isn't kept or no longer has it.
 */
internal class ScriptCopy(
    val source: ScriptSource,
    val state: CopyState,
    val edited: Boolean,
    val lib: LibRow? = null,
    val shared: ScriptCatalog.Entry? = null,
) {
    /** Whether Update or Reset can bring the source's version: the copy differs, and the source is known. */
    val canRestore get() = state != CopyState.UP_TO_DATE && (lib != null || shared != null)

    /** What [ScriptActions.restore] needs to bring [s] to the source's version. */
    fun restore(s: Script) = ScriptActions.Restore(s, library = lib?.entry, shared = shared)

    companion object {
        /** A library copy of [lib]'s script. */
        fun of(lib: LibRow) = ScriptCopy(
            ScriptSource.LIBRARY,
            when {
                lib.newer -> CopyState.NEW_VERSION
                lib.state == ScriptLibrary.State.MODIFIED -> CopyState.EDITED
                else -> CopyState.UP_TO_DATE
            },
            edited = lib.state == ScriptLibrary.State.MODIFIED,
            lib = lib,
        )
    }
}

/** A shared copy: what is known of it, and its entry in the kept list (null if none). */
private class SharedCopy(val copy: ScriptCatalog.Copy, val entry: ScriptCatalog.Entry?)

/** Facts read from a script's code, for its card and the filters. They are read once per version of the code. */
internal class ScriptCode(code: String, name: String) {
    val title = ScriptMeta.title(code)
    val desc = ScriptMeta.description(code)
    val author = ScriptMeta.author(code)
    val isCommand = ScriptMeta.isCommand(code)
    val usage = if (isCommand) ScriptMeta.usage(code, name) else null
    val events = ScriptMeta.events(code)
    val uses = ScriptMeta.uses(code)
    val secrets = ScriptMeta.secrets(code)
    val notifies = ScriptMeta.postsNotifications(code)
    /** Whether it uses the internet and a status line ([ScriptAccess.base]). */
    val base = ScriptAccess.base(code).toSet()
    /** The items its sheet and expanded card list: first its access, then its events ([ScriptAccess.items]). */
    val accessItems = ScriptAccess.items(code)
}

/**
 * The [ScriptCode] of each script as last shown, keyed by name and code. It is kept while the page
 * is closed: the editor replaces the page, and without this cache every script would be read again
 * after a save. Use it on the main thread only.
 */
private val keptCodes = HashMap<Pair<String, String>, ScriptCode>()

/** The data a script's card shows and the filters match against. */
internal class ScriptInfo(val script: Script, code: ScriptCode) {
    val title = code.title
    val desc = code.desc
    val author = code.author
    val isCommand = code.isCommand
    val usage = code.usage
    val events = code.events
    val uses = code.uses
    val timed = script.intervalMin > 0 || script.atTimes.isNotEmpty()
    val secrets = code.secrets
    val notifies = code.notifies
    /** Same as [Script.grants], but built from the code that was already read. */
    val grants = ScriptAccess.Grants(code.uses, script.access, code.base)
    val accessItems = code.accessItems
}

/** A script's `@secret` names, split into those with no value yet and those that are set. Both keep the declared order. */
private class SecretNames(val missing: List<String>, val set: List<String>)

/** What the filters know about [info]: see [ScriptFacts]. */
private fun factsOf(info: ScriptInfo, copy: ScriptCopy?, needsKey: Boolean, tags: List<String>, selfUpdated: Boolean): ScriptFacts {
    val s = info.script
    return ScriptFacts(
        name = s.name,
        lang = s.lang.ext,
        on = s.enabled,
        failed = s.failed(),
        needsKey = needsKey,
        command = info.isCommand,
        timer = info.timed,
        event = info.events.isNotEmpty(),
        pinned = info.grants.allows(ScriptAccess.HEADER),
        source = copy?.source ?: ScriptSource.YOURS,
        copy = copy?.state,
        uses = info.uses.toSet(),
        text = (listOfNotNull(s.name, info.title, info.desc, info.author) + tags).joinToString(" ").lowercase(),
        lastRun = s.lastRun,
        changedAt = s.changedAt,
        selfUpdated = selfUpdated,
    )
}

/**
 * The script manager in the Scripts tab (Material 3 Expressive). It shows one card per script.
 * - Above the cards: a search field, Filters (a sheet of chips, with a count of those on) and Sort.
 *   The chips that are on show under the field, each with ×.
 * - A note when Kolahal updated library scripts by itself, and a banner when copies differ from
 *   the library or the shared version, with Review and Update all.
 * - A compact card shows the title, how it runs and its last run on one line, and a pill for what
 *   needs doing: Update, Reset or Add key.
 * - An expanded card also shows the status line and description, a line for each problem (with
 *   its fix), and lines with an icon: when it runs, its usage and its access.
 * - Add script offers new, library, shared and imported scripts.
 * - Tapping a card opens the script's sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptsScreen(vm: TerminalViewModel) {
    val scripts by vm.scriptList.collectAsStateWithLifecycle()
    val statuses by vm.statuses.collectAsStateWithLifecycle()
    val notes by vm.scriptActions.pageNotes.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var sheetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var consentFor by remember { mutableStateOf<Script?>(null) }
    // The script whose Update or Reset asks first, because its edits would be lost.
    var restoring by remember { mutableStateOf<Long?>(null) }
    // The script that Compare shows next to its library version.
    var comparing by remember { mutableStateOf<Long?>(null) }
    var updatingAll by remember { mutableStateOf(false) }
    // The script and the secret that Add key sets.
    var addingKey by remember { mutableStateOf<Pair<Long, String>?>(null) }
    // The filter chips that are on (ScriptFilters ids), the sort and the search. They survive rotation.
    var filters by rememberSaveable { mutableStateOf(listOf<String>()) }
    var sortName by rememberSaveable { mutableStateOf(ScriptSort.NAME.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var filtering by rememberSaveable { mutableStateOf(false) }
    // Details: whether all cards are open or closed, and the cards the user toggled after that.
    var expandAll by rememberSaveable { mutableStateOf(false) }
    var flipped by rememberSaveable { mutableStateOf(listOf<Long>()) }
    val sort = runCatching { ScriptSort.valueOf(sortName) }.getOrDefault(ScriptSort.NAME)
    BackHandler {
        when {
            query.isNotEmpty() -> query = ""
            else -> vm.closeScreen()
        }
    }
    LaunchedEffect(vm) { vm.scriptActions.loadPageNotes() }

    // If a script declares access that nobody has allowed yet, switching it on asks first. All access is on in that dialog.
    fun setEnabled(s: Script, on: Boolean) {
        if (on && s.grants.undecided.isNotEmpty()) consentFor = s else vm.scriptActions.setScriptEnabled(s, on)
    }

    // Update or Reset. An edited copy loses its edits, so it asks first. An unedited one has nothing to lose: Undo is enough.
    fun restore(s: Script, copy: ScriptCopy) {
        if (copy.edited) restoring = s.id else vm.scriptActions.restore(listOf(copy.restore(s)))
    }

    val library by rememberLibraryRows(vm, scripts)
    val libByName = remember(library) { library.orEmpty().associateBy { it.entry.name } }
    // Copies from Get more scripts. The newest versions come from its kept list, so nothing is downloaded here.
    val shared by produceState(emptyMap<String, SharedCopy>(), scripts) {
        value = withContext(Dispatchers.IO) {
            val catalog = vm.scriptActions.scriptCatalog
            val latest = runCatching { catalog.cached().orEmpty() }.getOrDefault(emptyList()).associateBy { it.name }
            scripts.mapNotNull { s -> catalog.copy(s, latest[s.name])?.let { s.name to SharedCopy(it, latest[s.name]) } }.toMap()
        }
    }

    // The list is rebuilt when a script runs (its last run changes) or is saved. Only code that
    // changed is read again, also after the page was closed.
    val infos = remember(scripts) {
        keptCodes.keys.retainAll(scripts.mapTo(HashSet()) { it.name to it.code })
        scripts.map { s -> ScriptInfo(s, keptCodes.getOrPut(s.name to s.code) { ScriptCode(s.code, s.name) }) }
    }
    // Library copies (the same name and language as a library script), then copies from Get more scripts. The rest are yours.
    val copies = remember(infos, libByName, shared) {
        infos.associate { i ->
            val s = i.script
            val lib = libByName[s.name]?.takeIf { it.state != ScriptLibrary.State.NOT_INSTALLED && it.entry.lang == s.lang }
            s.id to (lib?.let(ScriptCopy::of) ?: shared[s.name]?.let { c ->
                val state = when {
                    c.copy.newer -> CopyState.NEW_VERSION
                    c.copy.edited -> CopyState.EDITED
                    else -> CopyState.UP_TO_DATE
                }
                ScriptCopy(ScriptSource.SHARED, state, c.copy.edited, shared = c.entry)
            })
        }
    }
    // Declared secrets with and without a value, by script name. The lists stay the same when the
    // page is rebuilt, so a card can skip recomposition when nothing of its own changed.
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
    // The library copies Kolahal updated by itself, while the note about them shows. Only those that are still here count.
    val selfUpdate = notes?.selfUpdate
    val selfUpdated = remember(selfUpdate, scripts) {
        selfUpdate?.names.orEmpty().filterTo(HashSet()) { n -> scripts.any { it.name == n } }
    }
    val facts = remember(infos, copies, secretNames, shared, selfUpdated) {
        infos.map { i ->
            val name = i.script.name
            factsOf(i, copies[i.script.id], secretNames[name]?.missing.orEmpty().isNotEmpty(), shared[name]?.entry?.tags.orEmpty(), name in selfUpdated)
        }
    }
    val infoByName = remember(infos) { infos.associateBy { it.script.name } }
    // Only chips this version has: a saved id of an older one is left out.
    val chosen = remember(filters) { filters.filterTo(LinkedHashSet()) { ScriptFilters.byId(it) != null } }
    val found = remember(facts, query) { facts.filter { ScriptFilters.found(it, query) } }
    // Names only: facts are compared by value, so a change they don't hold (an access switch) keeps them
    // equal. The cards must still get the scripts as they are now.
    val shownNames = remember(found, chosen, sort) { ScriptFilters.sorted(found.filter { ScriptFilters.matches(it, chosen) }, sort).map { it.name } }
    val shown = shownNames.mapNotNull { infoByName[it] }
    // Copies that differ from where they came from, for the banner. It stays closed while the user closed it for these same ones.
    val differing = remember(infos, copies) { infos.mapNotNull { i -> copies[i.script.id]?.takeIf { it.state != CopyState.UP_TO_DATE }?.let { i.script to it } } }
    val signature = differing.map { (s, c) -> "${s.name}:${c.state}" }.toSet()
    val bannerHidden = notes?.bannerHiddenFor?.split(',')?.toSet()
    val showBanner = differing.isNotEmpty() && notes != null && (bannerHidden == null || !bannerHidden.containsAll(signature))
    val on = scripts.count { it.enabled }

    GuiTheme {
        val snackbar = rememberNoticeSnackbar(vm)

        GuiListPage(
            title = { Text("Scripts") },
            subtitle = {
                Text(
                    if (scripts.isEmpty()) "Lua 5.4 · JavaScript"
                    else listOfNotNull("$on on", (scripts.size - on).takeIf { it > 0 }?.let { "$it off" }).joinToString(" · ")
                )
            },
            onBack = vm::closeScreen,
            snackbar = snackbar,
            actions = {
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { expandAll = !expandAll; flipped = emptyList() }) {
                    Icon(
                        painterResource(R.drawable.ic_expand_more),
                        contentDescription = if (expandAll) "Hide details" else "Show details",
                        modifier = Modifier.rotate(if (expandAll) 180f else 0f),
                    )
                }
            },
            floatingActionButton = {
                SmallExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(painterResource(R.drawable.ic_add), null) },
                    text = { Text("Add script") },
                )
            },
            top = 0.dp,
            bottom = 96.dp,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "tabs") { FeatureTabs(vm, Screen.SCRIPTS) }
            if (scripts.isNotEmpty()) {
                item(key = "search") {
                    SearchRow(query, { query = it }, chosen.size, onFilters = { filtering = true }, sort) { sortName = it.name }
                }
            }
            if (chosen.isNotEmpty()) {
                item(key = "chosen") {
                    ChosenFilters(chosen, selfUpdate?.version, onRemove = { id -> filters = filters - id }) { filters = emptyList() }
                }
            }
            if (selfUpdate != null && selfUpdated.isNotEmpty()) {
                item(key = "self-updated") {
                    SelfUpdateNote(
                        selfUpdate.version,
                        selfUpdated.size,
                        onShow = { filters = listOf(ScriptFilters.SELF_UPDATED) },
                        onClose = {
                            filters = filters - ScriptFilters.SELF_UPDATED
                            vm.scriptActions.dismissSelfUpdate()
                        },
                    )
                }
            }
            if (showBanner) {
                item(key = "banner") {
                    CopiesBanner(
                        newer = differing.count { it.second.state == CopyState.NEW_VERSION },
                        edited = differing.count { it.second.state == CopyState.EDITED },
                        canUpdate = differing.any { it.second.canRestore },
                        onReview = { filters = ScriptFilters.review(filters.toSet()).toList() },
                        onUpdateAll = { updatingAll = true },
                        onHide = { vm.scriptActions.hideBanner(signature.sorted().joinToString(",")) },
                    )
                }
            }
            if (scripts.isNotEmpty()) item(key = "count") { CountLine(shown.size, scripts.size, sort) }
            when {
                scripts.isEmpty() -> item(key = "empty") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        EmptyNote("No scripts yet. Write one, add one from the library, get a shared one, or import a file.", top = 24.dp, bottom = 0.dp)
                        ActionGroup(addActions(vm, onNew = { creating = true }, onImportFiles = vm::requestScriptImport))
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
                    val copy = copies[s.id]
                    ScriptCard(
                        info,
                        line = statuses[s.name],
                        missingKeys = secretNames[s.name]?.missing.orEmpty(),
                        expanded = expandAll != (s.id in flipped),
                        copy = copy,
                        selfUpdatedIn = selfUpdate?.version?.takeIf { s.name in selfUpdated },
                        onExpand = { flipped = if (s.id in flipped) flipped - s.id else flipped + s.id },
                        onClick = { sheetId = s.id },
                        onEnabled = { setEnabled(s, it) },
                        onRun = { vm.scriptActions.runScriptNow(s) },
                        onAccess = { item, on -> vm.scriptActions.setScriptAccess(s, item, on) },
                        onNotify = { vm.scriptActions.setScriptNotify(s, it) },
                        onCompare = { comparing = s.id },
                        onRestore = { copy?.let { restore(s, it) } },
                        onAddKey = { name -> addingKey = s.id to name },
                    )
                }
            }
            if (scripts.isNotEmpty()) {
                item(key = "hint") {
                    PageHint("Tap a script for its timers, access, code and more. The arrow shows its details here.", top = 4.dp)
                }
            }
        }

        val sheetScript = sheetId?.let { id -> scripts.firstOrNull { it.id == id } }
        if (sheetScript != null) {
            ScriptSheet(
                vm, sheetScript, copies[sheetScript.id],
                onEnabled = { setEnabled(sheetScript, it) },
                onDismiss = { sheetId = null },
                onEdit = { sheetId = null; vm.openEditor(sheetScript) },
                onExport = { vm.requestScriptExport(sheetScript) },
            )
        }

        if (filtering) {
            FilterSheet(
                groups = ScriptFilters.sheetGroups(facts, chosen),
                counts = ScriptFilters.counts(found, chosen),
                chosen = chosen,
                shown = shown.size,
                onToggle = { id -> filters = if (id in chosen) filters - id else filters + id },
                onClear = { filters = emptyList() },
                onDismiss = { filtering = false },
            )
        }

        if (adding) {
            ModalBottomSheet(onDismissRequest = { adding = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    ActionGroup(addActions(vm, onNew = { adding = false; creating = true }, onImportFiles = { adding = false; vm.requestScriptImport() }, close = { adding = false }))
                }
            }
        }

        restoring?.let { id ->
            val s = scripts.firstOrNull { it.id == id }
            val copy = s?.let { copies[it.id] }?.takeIf { it.canRestore }
            if (s != null && copy != null) {
                RestoreDialog(vm, s, copy, onExport = { vm.requestScriptExport(s) }, onDismiss = { restoring = null }) {
                    restoring = null
                    vm.scriptActions.restore(listOf(copy.restore(s)))
                }
            }
        }

        val compared = comparing?.let { id -> scripts.firstOrNull { it.id == id } }
        val comparedCopy = compared?.let { copies[it.id] }?.takeIf { it.lib != null && it.canRestore }
        if (compared != null && comparedCopy?.lib != null) {
            CompareDialog(vm, compared, comparedCopy.lib, onDismiss = { comparing = null }) { comparing = null; restore(compared, comparedCopy) }
        }

        if (updatingAll) {
            UpdateAllDialog(differing.filter { it.second.canRestore }, onDismiss = { updatingAll = false }) { picked ->
                updatingAll = false
                vm.scriptActions.restore(picked.map { (s, c) -> c.restore(s) })
            }
        }

        addingKey?.let { (id, name) ->
            val s = scripts.firstOrNull { it.id == id }
            val secret = s?.let { infoByName[it.name] }?.secrets?.firstOrNull { it.name == name }
            if (s != null && secret != null) {
                SecretDialog(s.name, secret, isSet = false, onDismiss = { addingKey = null }) { value ->
                    addingKey = null
                    vm.scriptActions.setSecret(s.name, secret, value)
                }
            }
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

/** The ways to add a script. Used in the Add script sheet, and in the Scripts tab while it is empty. */
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

/**
 * How [s] is started, in parts for a card's lines: for example "Command", "Every 1h", "Daily 08:00",
 * "On boot". Each part starts with a capital.
 * - [isCommand]: add "Command".
 * - [events]: the events for "On …"; pass none to leave that part out.
 * - [manual]: say "Manual" when there is no other part.
 */
internal fun scheduleParts(s: Script, isCommand: Boolean, events: List<String>, manual: Boolean = true): List<String> = buildList {
    if (isCommand) add("Command")
    if (s.intervalMin > 0) add("Every ${CommandParsing.formatInterval(s.intervalMin)}")
    if (s.atTimes.isNotEmpty()) add("Daily ${s.atTimes.joinToString(", ")}")
    if (events.isNotEmpty()) add("On ${events.joinToString(", ")}")
    if (isEmpty() && manual) add("Manual")
}

internal fun Script.failed() = lastStatus?.let { it.startsWith("error") || it.startsWith("stopped") || it == "timeout" } == true

/**
 * When a switched-on script next runs by itself: its next daily time, or about one interval after
 * its last run. The Boolean is true for a daily time.
 */
internal fun nextRun(s: Script): Pair<Long, Boolean>? {
    if (!s.enabled) return null
    val now = System.currentTimeMillis()
    // Round to the minute, so it shows the daily time itself and not a few seconds before it.
    val daily = s.atTimes.minOfOrNull { ((now + ScriptScheduler.millisUntil(it)) / 60_000.0).roundToLong() * 60_000 }
    val every = if (s.intervalMin > 0) maxOf(now, (s.lastRun ?: now) + s.intervalMin * 60_000L) else null
    return when {
        daily != null && (every == null || daily <= every) -> daily to true
        every != null -> every to false
        else -> null
    }
}
