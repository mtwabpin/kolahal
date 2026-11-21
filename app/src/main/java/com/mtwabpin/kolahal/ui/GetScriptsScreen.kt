/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptSources
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.commands.CommandParsing
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A shared script's key in the list: the same name can be in two sources. */
private val ScriptCatalog.Entry.key get() = "$source/$name"

/** Where a shared script stands, and the other source its name was installed from, if that is why it is taken. */
private data class SharedState(val state: ScriptCatalog.State, val takenBy: String? = null)

/**
 * Scripts → Get more scripts: the scripts shared in kolahal-scripts and in the sources you added.
 * - The list is searchable. A script from a source you added says which one.
 * - Each script has a sheet that shows what it does, what it uses and its code, before install.
 * - The lists are read only when this screen opens (each kept for an hour) or on refresh. A source
 *   that can't be read says why, and the others still show.
 * - Sources, in the top bar, adds and removes sources.
 */
@Composable
fun GetScriptsScreen(vm: TerminalViewModel) {
    val shared by vm.scriptActions.sharedScripts.collectAsStateWithLifecycle()
    val scripts by vm.scriptList.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler { vm.closeScreen() }

    // The state of each script: installed, edited, has an update, and so on. The name checks read
    // the app list, so this runs off the main thread.
    val states by produceState(emptyMap<String, SharedState>(), shared.entries, scripts) {
        value = withContext(Dispatchers.IO) {
            val catalog = vm.scriptActions.scriptCatalog
            shared.entries.associate { e ->
                val state = catalog.state(e, scripts)
                e.key to SharedState(state, if (state == ScriptCatalog.State.NAME_TAKEN) catalog.installedFrom(e.name)?.takeIf { it != e.source } else null)
            }
        }
    }
    val shown = remember(shared.entries, query) { ScriptCatalog.found(shared.entries, query) }
    val added = shared.sources.count { !it.source.builtIn }

    GuiTheme {
        val snackbar = rememberNoticeSnackbar(vm)

        GuiListPage(
            title = { Text("Get more scripts") },
            subtitle = { Text(if (added == 0) "Shared in ${ScriptCatalog.SOURCE} on GitHub" else "Shared in ${ScriptCatalog.SOURCE} and ${ScriptSources.sourceCount(added)} you added") },
            onBack = vm::closeScreen,
            snackbar = snackbar,
            actions = {
                LabeledIconButton(R.drawable.ic_refresh, "Refresh the list", onClick = { vm.scriptActions.loadSharedScripts(refresh = true) }, enabled = !shared.loading)
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { vm.openScreen(Screen.SCRIPT_SOURCES) }) { Text("Sources") }
            },
        ) {
            item(key = "search") {
                SearchField(query, { query = it }, "Search by name, author or tag", Modifier.fillMaxWidth().padding(bottom = 8.dp))
            }
            if (shared.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
            if (!shared.loading && shared.failed.isNotEmpty()) {
                item(key = "error") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        shared.failed.forEach { l ->
                            Text("Can't read ${l.source.name}: ${l.error}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = { vm.scriptActions.loadSharedScripts(refresh = true) }) { Text("Try again") }
                    }
                }
            }
            itemsIndexed(shown, key = { _, e -> e.key }) { i, e ->
                SharedRow(e, states[e.key], i, shown.size) { open = e.key }
            }
            if (!shared.loading && shown.isEmpty() && shared.failed.isEmpty()) {
                item(key = "none") {
                    Text(
                        if (query.isBlank()) "No scripts shared yet." else "No script matches “$query”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            item(key = "about") {
                PageHint(
                    (if (added == 0) "Every script here is reviewed before it is added. "
                    else "Every script in ${ScriptCatalog.SOURCE} is reviewed before it is added. Kolahal doesn't check scripts from sources you added. ") +
                        "When you install one, you choose what it can " +
                        "use; its Access switches change that later. Kolahal's own scripts are under Add script → From " +
                        "the library. Share your own at github.com/mtwabpin/${ScriptCatalog.SOURCE}.",
                )
            }
        }

        open?.let { key -> shared.entries.firstOrNull { it.key == key } }?.let { e ->
            SharedSheet(vm, e, states[e.key], onDismiss = { open = null })
        }
    }
}

/** How a shared script runs: `command · every 1h · daily 06:00 · on unlock`. */
private fun runs(e: ScriptCatalog.Entry): String = buildList {
    if (e.command) add("command")
    e.every?.let { add("every ${CommandParsing.formatInterval(it)}") }
    if (e.at.isNotEmpty()) add("daily ${e.at.joinToString(", ")}")
    if (e.on.isNotEmpty()) add("on ${e.on.joinToString(", ")}")
}.joinToString(" · ")

private fun stateLabel(state: SharedState?, e: ScriptCatalog.Entry): String? = when (state?.state) {
    ScriptCatalog.State.INSTALLED -> "Installed"
    ScriptCatalog.State.UPDATE_AVAILABLE -> "Update to ${e.version}"
    ScriptCatalog.State.MODIFIED -> "Installed, edited"
    ScriptCatalog.State.NAME_TAKEN -> state.takenBy?.let { "Installed from $it" } ?: "Name taken"
    ScriptCatalog.State.NEEDS_NEWER -> "Needs Kolahal ${e.requires}"
    else -> null
}

/** The source to name for [e]: one the user added. Null for kolahal-scripts. */
private fun addedSource(e: ScriptCatalog.Entry): String? = e.source.takeIf { it != ScriptCatalog.SOURCE }

@Composable
private fun SharedRow(e: ScriptCatalog.Entry, shared: SharedState?, index: Int, count: Int, onClick: () -> Unit) {
    val state = shared?.state
    SegmentedListItem(
        onClick = onClick,
        shapes = segShapes(index, count),
        colors = segColors(),
        overlineContent = {
            Text(
                listOf("${e.name}.${e.lang.ext}", runs(e)).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                listOfNotNull(e.desc.ifBlank { null }, addedSource(e)?.let { "From $it" }, stateLabel(shared, e)).joinToString(" · "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (state == ScriptCatalog.State.UPDATE_AVAILABLE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) { Text(e.title) }
}

/** The sheet for one shared script: what it is, how it runs, what it uses, its code, and Install or Update. */
@Composable
private fun SharedSheet(vm: TerminalViewModel, e: ScriptCatalog.Entry, shared: SharedState?, onDismiss: () -> Unit) {
    val state = shared?.state
    var asking by remember(e.key) { mutableStateOf(false) }
    var confirmReplace by remember(e.key) { mutableStateOf(false) }
    // Download the file and check it against the list, so the user can read it before installing.
    val code by produceState<Result<String>?>(null, e.sha256) { value = runCatching { vm.scriptActions.sharedScriptCode(e) } }

    GuiSheet(onDismiss, scroll = true) {
        Text(e.title, style = MaterialTheme.typography.titleLarge)
        Text(
            listOfNotNull("${e.name}.${e.lang.ext}", e.version.ifBlank { null }?.let { "v$it" }, e.author.ifBlank { null }?.let { "by $it" })
                .joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (e.desc.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(e.desc, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(12.dp))
        // Once the file is downloaded (and checked against the list), use it, because the file
        // is what installing grants. Until then, use the list's values.
        val text = code?.getOrNull()
        val uses = remember(text) { text?.let { ScriptMeta.uses(it).toList() } ?: e.uses }
        Fact("Runs", remember(text) { text?.let { scriptRuns(it) } ?: runs(e) }.ifEmpty { "manually" })
        Fact("Uses", if (uses.isEmpty()) "nothing private" else uses.joinToString(", ") { ScriptAccess.label(it).lowercase() })
        code?.getOrNull()?.let(ScriptMeta::secrets)?.takeIf { it.isNotEmpty() }?.let { keys ->
            Fact("Keys", keys.joinToString(", ") { "${it.name} (you set it; sent only to ${it.hosts.joinToString()})" })
        }
        if (e.tags.isNotEmpty()) Fact("Tags", e.tags.joinToString(", "))
        addedSource(e)?.let { Fact("From", "$it, a source you added") }

        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                ScriptCatalog.State.NOT_INSTALLED, null -> Button(
                    shapes = ButtonDefaults.shapes(),
                    onClick = { if (uses.isEmpty()) vm.scriptActions.installSharedScript(e) else asking = true },
                    enabled = code?.isSuccess == true,
                ) {
                    IconLabel(R.drawable.ic_download, "Install")
                }
                ScriptCatalog.State.UPDATE_AVAILABLE -> Button(shapes = ButtonDefaults.shapes(), onClick = { vm.scriptActions.installSharedScript(e) }, enabled = code?.isSuccess == true) {
                    Text("Update to ${e.version}")
                }
                ScriptCatalog.State.MODIFIED -> ErrorTextButton(onClick = { confirmReplace = true }, enabled = code?.isSuccess == true) {
                    Text("Replace your edited copy")
                }
                ScriptCatalog.State.INSTALLED -> FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = {}, enabled = false) { Text("Installed") }
                ScriptCatalog.State.NAME_TAKEN -> Text(
                    shared?.takenBy?.let { "You installed ${e.name} from $it." } ?: "One of your scripts, a command or an app already has the name ${e.name}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                ScriptCatalog.State.NEEDS_NEWER -> Text(
                    "Needs Kolahal ${e.requires} or newer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Code")
        Spacer(Modifier.height(8.dp))
        val result = code
        when {
            result == null -> Text("Downloading…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            result.isSuccess -> ReadOnlyCode(result.getOrThrow(), e.lang)
            else -> Text("Can't download it: ${result.exceptionOrNull()?.message}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }

    if (asking) {
        val uses = code?.getOrNull()?.let { ScriptMeta.uses(it).toList() } ?: e.uses
        AccessDialog("Install ${e.name}.${e.lang.ext}?", uses, "Install", onDismiss = { asking = false }) { choices ->
            asking = false
            vm.scriptActions.installSharedScript(e, choices)
        }
    }
    if (confirmReplace) {
        ConfirmDialog(
            "Replace ${e.name}.${e.lang.ext}?",
            "Your copy differs from the shared one. Its code is replaced and your changes are lost. Export it first to keep a copy.",
            "Replace",
            onDismiss = { confirmReplace = false },
        ) { confirmReplace = false; vm.scriptActions.installSharedScript(e, replace = true) }
    }
}

