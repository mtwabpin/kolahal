package com.mtwabpin.kolahal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptSources
import com.mtwabpin.kolahal.terminal.TerminalViewModel

/**
 * Get more scripts → Sources: the repositories that Get more scripts reads, in order.
 * - Each shows its name, its address, how many scripts it has, when it was read, and the error of
 *   its last read.
 * - kolahal-scripts is always first and can't be removed. Remove takes off one you added, with Undo.
 * - Add source asks for `owner/name` or a link to an index.json, shows the address it will read,
 *   and says that Kolahal doesn't check the scripts there.
 */
@Composable
fun ScriptSourcesScreen(vm: TerminalViewModel) {
    val shared by vm.scriptActions.sharedScripts.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    BackHandler { vm.closeScreen() }
    val sources = shared.sources

    GuiTheme {
        val snackbar = rememberNoticeSnackbar(vm)

        GuiListPage(
            title = { Text("Sources") },
            subtitle = { Text("Where Get more scripts finds scripts") },
            onBack = vm::closeScreen,
            snackbar = snackbar,
            floatingActionButton = {
                SmallExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(painterResource(R.drawable.ic_add), null) },
                    text = { Text("Add source") },
                )
            },
            bottom = 96.dp,
        ) {
            if (shared.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
            itemsIndexed(sources, key = { _, l -> l.source.index }) { i, l ->
                SourceRow(l, i, sources.size, onRemove = { vm.scriptActions.removeSource(l.source) })
            }
            item(key = "about") {
                PageHint(
                    "Get more scripts lists the scripts of each source, in this order. When two have a script " +
                        "with the same name, 'script get <name>' installs the first one's. Updates come from the source " +
                        "a script was installed from. Terminal: script sources · script source add <owner/name> · " +
                        "script source rm <name>",
                )
            }
        }

        if (adding) AddSourceDialog(onDismiss = { adding = false }) { source ->
            adding = false
            vm.scriptActions.addSource(source)
        }
    }
}

/** One source: its name, its address, and what its last read found. */
@Composable
private fun SourceRow(l: ScriptCatalog.Listing, index: Int, count: Int, onRemove: () -> Unit) {
    val facts = listOfNotNull(
        if (l.source.builtIn) "Reviewed" else null,
        l.readAt?.let { ScriptSources.scriptCount(l.entries.size) },
        l.readAt?.let { "Read ${relative(it)}" } ?: "Not read yet",
    ).joinToString(" · ")
    SegmentedListItem(
        onClick = {},
        shapes = segShapes(index, count),
        colors = segColors(),
        overlineContent = {
            Text(
                l.source.index,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                Text(facts, color = MaterialTheme.colorScheme.onSurfaceVariant)
                l.error?.let { Text("Can't read it: $it", color = MaterialTheme.colorScheme.error) }
            }
        },
        trailingContent = if (l.source.builtIn) null else {
            { LabeledIconButton(R.drawable.ic_delete, "Remove", onClick = onRemove) }
        },
    ) { Text(l.source.name) }
}

/**
 * Add source: `owner/name` or a link to an index.json. It shows the address it will read, or why
 * the text isn't a source, and that Kolahal doesn't check the scripts of a source you add.
 */
@Composable
private fun AddSourceDialog(onDismiss: () -> Unit, onAdd: (ScriptSources.Source) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text) { ScriptSources.parse(text) }
    val source = (parsed as? ScriptSources.Parsed.Ok)?.source
    val bad = (parsed as? ScriptSources.Parsed.Bad)?.why?.takeIf { text.isNotBlank() }

    fun submit() {
        source?.let(onAdd)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_download), null) },
        title = { Text("Add source") },
        text = {
            Column {
                TextField(
                    shape = TextFieldDefaults.roundedShape,
                    colors = fieldColors(),
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Repository") },
                    placeholder = { Text("owner/name") },
                    singleLine = true,
                    isError = bad != null,
                    supportingText = {
                        Text(
                            when {
                                source != null -> "Reads ${source.index}"
                                bad != null -> bad
                                else -> "owner/name on GitHub, or an https:// link to an index.json"
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Kolahal doesn't check scripts from it. Only add repositories you trust.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), enabled = source != null, onClick = ::submit) { Text("Add") } },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Cancel") } },
    )
}
