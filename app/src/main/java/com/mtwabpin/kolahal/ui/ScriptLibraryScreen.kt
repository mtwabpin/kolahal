package com.mtwabpin.kolahal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.terminal.CommandParsing
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel

/** Scripts → Add script → From the library: the ready-made scripts that come with Kolahal. */
@Composable
fun ScriptLibraryScreen(vm: TerminalViewModel) {
    val scripts by vm.scriptList.collectAsStateWithLifecycle()
    BackHandler { vm.closeScreen() }
    val library by rememberLibraryRows(vm, scripts)
    GuiTheme {
        val snackbar = rememberNoticeSnackbar(vm)
        GuiListPage(
            title = { Text("Library") },
            subtitle = { Text("Ready-made scripts that come with Kolahal") },
            onBack = vm::closeScreen,
            snackbar = snackbar,
        ) {
            // Show nothing until the rows are loaded. If "Get more scripts" shows alone first, it
            // keeps its place while the rows appear above it, so the list opens scrolled to the end.
            val rows = library ?: return@GuiListPage
            itemsIndexed(rows, key = { _, r -> "lib:" + r.entry.name }) { i, r ->
                LibraryRow(r, i, rows.size) { vm.scriptActions.installFromLibrary(r.entry) }
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
    }
}

/**
 * One library script and its install state.
 * - A tap on the row shows or hides the description.
 * - Install (or Update) installs it with its suggested timers.
 */
@Composable
private fun LibraryRow(r: LibRow, index: Int, count: Int, onInstall: () -> Unit) {
    var open by rememberSaveable(r.entry.name) { mutableStateOf(false) }
    val e = r.entry
    SegmentedListItem(
        onClick = { open = !open },
        shapes = segShapes(index, count),
        colors = segColors(),
        overlineContent = {
            Text(
                "${e.name}.${e.lang.ext}" + (if (e.command) " · command" else
                    if (e.suggestedEvery > 0) " · every ${CommandParsing.formatInterval(e.suggestedEvery)}" else "") +
                    r.events.let { if (it.isEmpty()) "" else " · on " + it.joinToString(", ") },
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            )
        },
        supportingContent = if (open) {
            { Text(e.description) }
        } else null,
        trailingContent = {
            when (r.state) {
                ScriptLibrary.State.NOT_INSTALLED -> FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onInstall) { Text("Install") }
                ScriptLibrary.State.UPDATE_AVAILABLE -> FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onInstall) { Text("Update") }
                // To replace an edited copy, the user opens its sheet in Scripts and confirms there.
                ScriptLibrary.State.MODIFIED -> Text("Edited", color = MaterialTheme.colorScheme.onSurfaceVariant)
                ScriptLibrary.State.INSTALLED -> Text("Installed", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    ) { Text(e.title) }
}
