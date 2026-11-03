package com.mtwabpin.kolahal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.terminal.CommandParsing
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Scripts → Add script → From the library: the ready-made scripts that come with Kolahal. */
@Composable
fun ScriptLibraryScreen(vm: TerminalViewModel) {
    val scripts by vm.scriptList.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    BackHandler { vm.closeScreen() }
    val library by produceState<List<LibRow>?>(null, vm.scriptActions.library, scripts) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                vm.scriptActions.library.entries.map { LibRow(it, vm.scriptActions.library.state(it, scripts), ScriptMeta.events(vm.scriptActions.library.code(it))) }
            }.getOrDefault(emptyList())
        }
    }
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
                    title = { Text("Library") },
                    subtitle = { Text("Ready-made scripts that come with Kolahal") },
                    navigationIcon = { BackButton(vm::closeScreen) },
                    scrollBehavior = scroll,
                )
            },
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Nothing until the rows are read: "Get more scripts" shown alone first stayed where it
                // was as the rows came above it, so the list opened scrolled to its end.
                val rows = library ?: return@LazyColumn
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
}

/** A library script not installed yet: its title, tap for the description; one tap installs it with suggested timers. */
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
                // Replacing your edited copy is in its sheet (Scripts), behind a confirmation.
                ScriptLibrary.State.MODIFIED -> Text("Edited", color = MaterialTheme.colorScheme.onSurfaceVariant)
                ScriptLibrary.State.INSTALLED -> Text("Installed", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    ) { Text(e.title) }
}
