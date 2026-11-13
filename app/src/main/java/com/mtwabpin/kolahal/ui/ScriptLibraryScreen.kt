package com.mtwabpin.kolahal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.commands.CommandParsing
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel

/**
 * Scripts → Add script → From the library: the ready-made scripts that come with Kolahal.
 * - Your copies that differ (edited, or with a newer version) come first, under "Your copy differs",
 *   with Compare and Update or Reset.
 * - Then every other library script, with Install or its state.
 */
@Composable
fun ScriptLibraryScreen(vm: TerminalViewModel) {
    val scripts by vm.scriptList.collectAsStateWithLifecycle()
    BackHandler { vm.closeScreen() }
    val library by rememberLibraryRows(vm, scripts)
    // The library script whose copy shows a dialog, and which one: the question before Update or Reset, or Compare.
    var copyDialog by remember { mutableStateOf<Pair<String, CopyDialog>?>(null) }
    // Your copy of a library script: the same name and language.
    fun mine(r: LibRow): Script? = scripts.firstOrNull { it.name == r.entry.name && it.lang == r.entry.lang }

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
            val rows = library ?: return@GuiListPage
            val differ = rows.filter { it.state.differs() && mine(it) != null }
            val rest = rows.filter { it !in differ }
            if (differ.isNotEmpty()) {
                item(key = "differs") { GroupHeader("Your copy differs · ${differ.size}") }
                itemsIndexed(differ, key = { _, r -> "differs:" + r.entry.name }) { i, r ->
                    DiffersRow(r, i, differ.size, onCompare = { copyDialog = r.entry.name to CopyDialog.COMPARE }) { restore(r) }
                }
                item(key = "all") { GroupHeader("All scripts") }
            }
            itemsIndexed(rest, key = { _, r -> "lib:" + r.entry.name }) { i, r ->
                LibraryRow(r, i, rest.size, nameTaken = r.state != ScriptLibrary.State.NOT_INSTALLED && mine(r) == null) {
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

        copyDialog?.let { (name, dialog) ->
            val r = library.orEmpty().firstOrNull { it.entry.name == name }
            val s = r?.let { mine(it) }
            if (r != null && s != null) {
                CopyDialogs(vm, s, ScriptCopy.of(r), dialog, onShow = { copyDialog = it?.let { d -> name to d } }, onExport = { vm.requestScriptExport(s) })
            }
        }
    }
}

/** The line above a library script's title: its file and how it runs. */
private fun overline(r: LibRow): String {
    val e = r.entry
    return "${e.name}.${e.lang.ext}" + (if (e.command) " · command" else
        if (e.suggestedEvery > 0) " · every ${CommandParsing.formatInterval(e.suggestedEvery)}" else "") +
        r.events.let { if (it.isEmpty()) "" else " · on " + it.joinToString(", ") }
}

/**
 * One library script and its install state.
 * - A tap on the row shows or hides the description.
 * - Install installs it with its suggested timers.
 * - [nameTaken]: one of your scripts has its name, in the other language.
 */
@Composable
private fun LibraryRow(r: LibRow, index: Int, count: Int, nameTaken: Boolean, onInstall: () -> Unit) {
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
            when {
                nameTaken -> Text("Name in use", color = MaterialTheme.colorScheme.onSurfaceVariant)
                r.state == ScriptLibrary.State.NOT_INSTALLED -> FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onInstall) { Text("Install") }
                r.state == ScriptLibrary.State.UPDATE_AVAILABLE -> FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onInstall) { Text("Update") }
                r.state == ScriptLibrary.State.MODIFIED -> Text("Edited", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Text("Installed", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = onCompare) {
                    Icon(painterResource(R.drawable.ic_visibility), contentDescription = "Compare", tint = MaterialTheme.colorScheme.primary)
                }
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onRestore) { Text(if (update) "Update" else "Reset") }
            }
        },
    ) { Text(r.entry.title) }
}
