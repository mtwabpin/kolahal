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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.commands.CommandParsing
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scripts → Get more scripts: the scripts shared in kolahal-scripts.
 * - The list is searchable.
 * - Each script has a sheet that shows what it does, what it uses and its code, before install.
 * - The list is fetched from GitHub only when this screen opens (cached for an hour) or on refresh.
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
    val states by produceState(emptyMap(), shared.entries, scripts) {
        value = withContext(Dispatchers.IO) { shared.entries.associate { it.name to vm.scriptActions.scriptCatalog.state(it, scripts) } }
    }
    val shown = remember(shared.entries, query) {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        shared.entries.filter { e ->
            val text = listOf(e.name, e.title, e.desc, e.author, e.tags.joinToString(" ")).joinToString(" ").lowercase()
            words.all { it in text }
        }
    }

    GuiTheme {
        val snackbar = rememberNoticeSnackbar(vm)

        GuiListPage(
            title = { Text("Get more scripts") },
            subtitle = { Text("Shared in ${ScriptCatalog.SOURCE} on GitHub") },
            onBack = vm::closeScreen,
            snackbar = snackbar,
            actions = {
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { vm.scriptActions.loadSharedScripts(refresh = true) }, enabled = !shared.loading) {
                    Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh the list")
                }
            },
        ) {
            item(key = "search") {
                SearchField(query, { query = it }, "Search by name, author or tag", Modifier.fillMaxWidth().padding(bottom = 8.dp))
            }
            if (shared.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
            shared.error?.let { error ->
                item(key = "error") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Can't read the list: $error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = { vm.scriptActions.loadSharedScripts(refresh = true) }) { Text("Try again") }
                    }
                }
            }
            itemsIndexed(shown, key = { _, e -> e.name }) { i, e ->
                SharedRow(e, states[e.name], i, shown.size) { open = e.name }
            }
            if (!shared.loading && shown.isEmpty() && shared.error == null) {
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
                    "Every script here is reviewed before it is added. When you install one, you choose what it can " +
                        "use; its Access switches change that later. Share your own at " +
                        "github.com/mtwabpin/${ScriptCatalog.SOURCE}.",
                )
            }
        }

        open?.let { name -> shared.entries.firstOrNull { it.name == name } }?.let { e ->
            SharedSheet(vm, e, states[e.name], onDismiss = { open = null })
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

/** The same text, read from the script file itself. This is what installing it sets up. */
private fun runs(code: String): String = buildList {
    if (ScriptMeta.isCommand(code)) add("command")
    ScriptMeta.every(code)?.let { add("every ${CommandParsing.formatInterval(it)}") }
    ScriptMeta.at(code).takeIf { it.isNotEmpty() }?.let { add("daily ${it.joinToString(", ")}") }
    ScriptMeta.events(code).takeIf { it.isNotEmpty() }?.let { add("on ${it.joinToString(", ")}") }
}.joinToString(" · ")

private fun stateLabel(state: ScriptCatalog.State?, e: ScriptCatalog.Entry): String? = when (state) {
    ScriptCatalog.State.INSTALLED -> "Installed"
    ScriptCatalog.State.UPDATE_AVAILABLE -> "Update to ${e.version}"
    ScriptCatalog.State.MODIFIED -> "Installed, edited"
    ScriptCatalog.State.NAME_TAKEN -> "Name taken"
    ScriptCatalog.State.NEEDS_NEWER -> "Needs Kolahal ${e.requires}"
    else -> null
}

@Composable
private fun SharedRow(e: ScriptCatalog.Entry, state: ScriptCatalog.State?, index: Int, count: Int, onClick: () -> Unit) {
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
                listOfNotNull(e.desc.ifBlank { null }, stateLabel(state, e)).joinToString(" · "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (state == ScriptCatalog.State.UPDATE_AVAILABLE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) { Text(e.title) }
}

/** The sheet for one shared script: what it is, how it runs, what it uses, its code, and Install or Update. */
@Composable
private fun SharedSheet(vm: TerminalViewModel, e: ScriptCatalog.Entry, state: ScriptCatalog.State?, onDismiss: () -> Unit) {
    var asking by remember(e.name) { mutableStateOf(false) }
    var confirmReplace by remember(e.name) { mutableStateOf(false) }
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
        Fact("Runs", remember(text) { text?.let { runs(it) } ?: runs(e) }.ifEmpty { "manually" })
        Fact("Uses", if (uses.isEmpty()) "nothing private" else uses.joinToString(", ") { ScriptAccess.label(it).lowercase() })
        code?.getOrNull()?.let(ScriptMeta::secrets)?.takeIf { it.isNotEmpty() }?.let { keys ->
            Fact("Keys", keys.joinToString(", ") { "${it.name} (you set it; sent only to ${it.hosts.joinToString()})" })
        }
        if (e.tags.isNotEmpty()) Fact("Tags", e.tags.joinToString(", "))

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
                    "One of your scripts, a command or an app already has the name ${e.name}.",
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
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
            val result = code
            Text(
                when {
                    result == null -> "Downloading…"
                    result.isSuccess -> result.getOrThrow()
                    else -> "Can't download it: ${result.exceptionOrNull()?.message}"
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp),
                modifier = Modifier.padding(12.dp),
            )
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

