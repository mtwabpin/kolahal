/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.SearchWords
import com.mtwabpin.kolahal.script.LibraryTag
import com.mtwabpin.kolahal.script.LibraryTags
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.commands.CommandParsing
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel

/**
 * Scripts → Add script → From the library: the ready-made scripts that come with Kolahal.
 * - At the top: a search field, and a chip for each kind of script ([LibraryTag]): Template, Widget…
 *   - The search keeps the scripts that have every word typed, in their name, title or description.
 *   - Each chip that is on adds its scripts. A chip shows how many scripts it keeps.
 *   - Both together: the scripts the search finds among those the chips keep.
 * - Your copies that differ (edited, or with a newer version) come first, under "Your copy differs",
 *   with Compare and Update or Reset.
 * - Then every other library script, with Install or its state.
 */
@Composable
fun ScriptLibraryScreen(vm: TerminalViewModel) {
    val scripts by vm.scriptList.collectAsStateWithLifecycle()
    // The search, and the chips that are on ([LibraryTag.id]). They survive rotation.
    var query by rememberSaveable { mutableStateOf("") }
    var chips by rememberSaveable { mutableStateOf(listOf<String>()) }
    // Back empties the search first, as in the Scripts tab and in Settings. Then it closes the page.
    BackHandler { if (query.isNotEmpty()) query = "" else vm.closeScreen() }
    val library by rememberLibraryRows(vm, scripts)
    // What the search and the chips keep is worked out in LibraryTags (plain functions, with JVM tests).
    val words = remember(query) { SearchWords.of(query) }
    val chosen = remember(chips) { LibraryTags.known(chips) }
    val counts = remember(library, words) { LibraryTags.counts(library.orEmpty().map { it.entry }, words) }
    val shown = remember(library, words, chosen) { library?.filter { LibraryTags.shows(it.entry, words, chosen) } }
    // The library script whose copy shows a dialog, and which one: the question before Update or Reset, or Compare.
    var copyDialog by remember { mutableStateOf<Pair<String, CopyDialog>?>(null) }
    // The library script whose code is open to read, by name. It survives rotation.
    var reading by rememberSaveable { mutableStateOf<String?>(null) }
    // Your copy of a library script: the same name and language.
    // Your copy of each library script, by name: looked up for each row, at each key in the search.
    val copiesByName = remember(scripts) { scripts.associateBy { it.name to it.lang } }
    fun mine(r: LibRow): Script? = copiesByName[r.entry.name to r.entry.lang]
    // The rows shown, in two groups: the copies that differ, then the rest. Worked out again only when they change.
    val differ = remember(shown, copiesByName) { shown.orEmpty().filter { it.state.differs() && mine(it) != null } }
    val rest = remember(shown, differ) { differ.toSet().let { d -> shown.orEmpty().filter { it !in d } } }

    // As on a card: an edited copy asks first; an unedited one updates at once, and the notice offers Undo.
    fun restore(r: LibRow) {
        val s = mine(r) ?: return
        restoreCopy(vm, s, ScriptCopy.of(r)) { copyDialog = r.entry.name to CopyDialog.CONFIRM }
    }

    GuiTheme {
        val snackbar = rememberNoticeSnackbar(vm)
        GuiListPage(
            title = { Text("Library") },
            subtitle = { Text("Ready-made scripts that come with Kolahal") },
            onBack = vm::closeScreen,
            snackbar = snackbar,
        ) {
            // A first item that never moves. The list keeps its first visible item in place, so
            // without it, "Your copy differs" coming above once the scripts are read would be scrolled away.
            // It is there while the rows load too: a list that went from no items to some could measure
            // an item that was gone ("Index 0, size 0", seen in full test runs).
            item(key = "top") { Spacer(Modifier.height(1.dp)) }
            // Show nothing else until the rows are loaded. If "Get more scripts" shows alone first, it
            // keeps its place while the rows appear above it, so the list opens scrolled to the end.
            val rows = shown ?: return@GuiListPage
            item(key = "search") {
                SearchField(
                    query,
                    { query = it },
                    "Search the library",
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    clear = "Clear",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            }
            if (counts.isNotEmpty()) {
                item(key = "tags") {
                    TagChips(counts, chosen) { tag -> chips = if (tag.id in chips) chips - tag.id else chips + tag.id }
                }
            }
            if (rows.isEmpty() && (words.isNotEmpty() || chosen.isNotEmpty())) {
                item(key = "none") {
                    // With chips on, the × of the search field is not enough to show everything again.
                    NoLibraryMatch(libraryNoMatch(query, chosen), onShowAll = if (chosen.isEmpty()) null else ({ chips = emptyList(); query = "" }))
                }
            }
            if (differ.isNotEmpty()) {
                item(key = "differs") { GroupHeader("Your copy differs · ${differ.size}") }
                itemsIndexed(differ, key = { _, r -> "differs:" + r.entry.name }) { i, r ->
                    DiffersRow(r, i, differ.size, onCompare = { copyDialog = r.entry.name to CopyDialog.COMPARE }) { restore(r) }
                }
                // The search or the chips can leave only copies that differ: then nothing comes under this title.
                if (rest.isNotEmpty()) item(key = "all") { GroupHeader("All scripts") }
            }
            itemsIndexed(rest, key = { _, r -> "lib:" + r.entry.name }) { i, r ->
                LibraryRow(r, i, rest.size, nameTaken = r.state != ScriptLibrary.State.NOT_INSTALLED && mine(r) == null, onRead = { reading = r.entry.name }) {
                    vm.scriptActions.installFromLibrary(r.entry)
                }
            }
            item(key = "more") {
                Column(Modifier.padding(top = 16.dp)) {
                    ActionGroup(
                        listOf(
                            Action(R.drawable.ic_download, "Get more scripts", "Shared by people who use Kolahal, on GitHub", chevron = true) {
                                vm.openScreen(Screen.GET_SCRIPTS)
                            },
                        )
                    )
                }
            }
        }

        reading?.let { name ->
            library.orEmpty().firstOrNull { it.entry.name == name }?.let { r ->
                LibrarySheet(
                    vm, r,
                    nameTaken = r.state != ScriptLibrary.State.NOT_INSTALLED && mine(r) == null,
                    onInstall = { vm.scriptActions.installFromLibrary(r.entry) },
                    onDismiss = { reading = null },
                )
            }
        }

        copyDialog?.let { (name, dialog) ->
            val r = library.orEmpty().firstOrNull { it.entry.name == name }
            val s = r?.let { mine(it) }
            if (r != null && s != null) {
                CopyDialogs(vm, s, ScriptCopy.of(r), dialog, onShow = { copyDialog = it?.let { d -> name to d } }, onExport = { vm.requestScriptExport(s) })
            }
        }
    }
}

/**
 * The chips under the search field, one for each tag that a library script has, in one row. The
 * row scrolls sideways when the chips are wider than the page.
 * - [counts]: the chips, each with the number of scripts it keeps ([LibraryTags.counts]).
 * - [chosen]: the chips that are on.
 */
@Composable
private fun TagChips(counts: Map<LibraryTag, Int>, chosen: Set<LibraryTag>, onToggle: (LibraryTag) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        for ((tag, count) in counts) {
            CountChip(tag.label, count, on = tag in chosen, modifier = Modifier.testTag("tag:${tag.id}")) { onToggle(tag) }
        }
    }
}

/**
 * What the page says when the search and the chips keep no script.
 * - The search alone: No script matches “piano”.
 * - With chips on, it names them, as each chip adds its scripts: No Template or Event script matches “piano”.
 * - Without a search (a chip for a tag that no script has): No Event script in the library.
 */
internal fun libraryNoMatch(query: String, chosen: Collection<LibraryTag>): String {
    val labels = chosen.map { it.label }
    val kinds = if (labels.size < 2) labels.joinToString() else labels.dropLast(1).joinToString() + " or " + labels.last()
    val scripts = if (kinds.isEmpty()) "No script" else "No $kinds script"
    return if (query.isBlank()) "$scripts in the library" else "$scripts matches “${query.trim()}”"
}

/** The line for no match ([libraryNoMatch]). With [onShowAll], a button under it empties the search and turns the chips off. */
@Composable
private fun NoLibraryMatch(text: String, onShowAll: (() -> Unit)?) {
    Column {
        NoMatchNote(text)
        // The button's own padding puts its label where the line above starts.
        if (onShowAll != null) TextButton(shapes = ButtonDefaults.shapes(), onClick = onShowAll, modifier = Modifier.padding(start = 4.dp)) { Text("Show all") }
    }
}

/** The line above a library script's title: its file and how it runs. */
private fun overline(r: LibRow): String {
    val e = r.entry
    return "${e.name}.${e.lang.ext}" + (if (e.command) " · command" else
        if (e.suggestedEvery > 0) " · every ${CommandParsing.formatInterval(e.suggestedEvery)}" else "") +
        e.events.let { if (it.isEmpty()) "" else " · on " + it.joinToString(", ") }
}

/**
 * One library script and its install state.
 * - A tap on the row shows or hides the description.
 * - The code button opens its code to read ([LibrarySheet]), before installing it.
 * - Install installs it with its suggested timers.
 * - [nameTaken]: one of your scripts has its name, in the other language.
 */
@Composable
private fun LibraryRow(r: LibRow, index: Int, count: Int, nameTaken: Boolean, onRead: () -> Unit, onInstall: () -> Unit) {
    var open by rememberSaveable(r.entry.name) { mutableStateOf(false) }
    val e = r.entry
    SegmentedListItem(
        onClick = { open = !open },
        shapes = segShapes(index, count),
        colors = segColors(),
        overlineContent = { Text(overline(r), style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)) },
        supportingContent = if (open) {
            { Text(e.description) }
        } else null,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabeledIconButton(R.drawable.ic_code, "Read the code of ${e.name}", onClick = onRead, modifier = Modifier.testTag("read ${e.name}"), tint = MaterialTheme.colorScheme.primary)
                InstallState(r, nameTaken, onInstall)
            }
        },
    ) { Text(e.title) }
}

/**
 * A library script whose copy differs from this version: what differs, Compare, and Update (a new
 * version) or Reset (edited, nothing newer). Both ask first when the copy is edited.
 */
@Composable
private fun DiffersRow(r: LibRow, index: Int, count: Int, onCompare: () -> Unit, onRestore: () -> Unit) {
    val update = ScriptCopy.of(r).state == CopyState.NEW_VERSION
    SegmentedListItem(
        onClick = onCompare,
        shapes = segShapes(index, count),
        colors = segColors(),
        overlineContent = { Text(overline(r), style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)) },
        supportingContent = {
            Text(
                when {
                    r.state == ScriptLibrary.State.UPDATE_AVAILABLE -> "New version."
                    update -> "New version. You edited your copy."
                    else -> "Edited. Nothing newer."
                },
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabeledIconButton(R.drawable.ic_visibility, "Compare", onClick = onCompare, tint = MaterialTheme.colorScheme.primary)
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onRestore) { Text(if (update) "Update" else "Reset") }
            }
        },
    ) { Text(r.entry.title) }
}

/** What a library script's row and sheet say of it: Install or Update, or that it is installed, edited, or its name is in use. */
@Composable
private fun InstallState(r: LibRow, nameTaken: Boolean, onInstall: () -> Unit) {
    when {
        nameTaken -> Text("Name in use", color = MaterialTheme.colorScheme.onSurfaceVariant)
        r.state == ScriptLibrary.State.NOT_INSTALLED -> FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onInstall) { Text("Install") }
        r.state == ScriptLibrary.State.UPDATE_AVAILABLE -> FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onInstall) { Text("Update") }
        r.state == ScriptLibrary.State.MODIFIED -> Text("Edited", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Text("Installed", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One library script to read before installing it: what it is, how it runs, what it uses, Install,
 * and its code, read-only ([ReadOnlyCode]). The code is read from the app, off the main thread.
 */
@Composable
private fun LibrarySheet(vm: TerminalViewModel, r: LibRow, nameTaken: Boolean, onInstall: () -> Unit, onDismiss: () -> Unit) {
    val e = r.entry
    val code by produceState<String?>(null, e.file) {
        value = withContext(Dispatchers.IO) { runCatching { vm.scriptActions.library.code(e) }.getOrNull() }
    }
    GuiSheet(onDismiss, scroll = true) {
        Text(e.title, style = MaterialTheme.typography.titleLarge)
        val text = code
        Text(
            listOfNotNull(
                "${e.name}.${e.lang.ext}",
                text?.let(ScriptMeta::version)?.let { "v$it" },
                text?.let(ScriptMeta::author)?.let { "by $it" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (e.description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(e.description, style = MaterialTheme.typography.bodyLarge)
        }
        if (text != null) {
            Spacer(Modifier.height(12.dp))
            val uses = remember(text) { ScriptMeta.uses(text).toList() }
            Fact("Runs", remember(text) { scriptRuns(text) }.ifEmpty { "manually" })
            Fact("Uses", if (uses.isEmpty()) "nothing private" else uses.joinToString(", ") { ScriptAccess.label(it).lowercase() })
            Fact("Internet", if (ScriptMeta.usesInternet(text)) "yes" else "no")
            ScriptMeta.secrets(text).takeIf { it.isNotEmpty() }?.let { keys ->
                Fact("Keys", keys.joinToString(", ") { "${it.name} (you set it; sent only to ${it.hosts.joinToString()})" })
            }
        }
        Spacer(Modifier.height(16.dp))
        androidx.compose.foundation.layout.Box(Modifier.testTag("library-sheet-install")) { InstallState(r, nameTaken, onInstall) }
        Spacer(Modifier.height(16.dp))
        SectionTitle("Code")
        Spacer(Modifier.height(8.dp))
        if (text == null) Text("Reading…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else ReadOnlyCode(text, e.lang)
    }
}
