/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ShaderCatalog
import com.mtwabpin.kolahal.script.Shaders
import com.mtwabpin.kolahal.script.SharedShader
import com.mtwabpin.kolahal.script.SharedShaders
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.ShaderActions
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import kotlinx.coroutines.flow.StateFlow

/** A shared shader's key in the list: the same name can be in two sources. */
private val SharedShader.key get() = "$source/$name"

/** Where a shared shader stands, and the sentence for why its name is taken, if it is. */
private data class ShaderState(val shared: SharedState, val taken: String?)

/**
 * Settings → Shaders → Get more shaders: the shaders shared in kolahal-scripts and in the sources the
 * user added, as Get more scripts does for scripts ([GetScriptsScreen]): the same lists, the same
 * search, Refresh and Sources. Each shader's sheet shows its code before Install; installing doesn't
 * turn it on, and the sheet then offers Use.
 */
@Composable
fun GetShadersScreen(
    actions: ShaderActions,
    notice: StateFlow<TerminalViewModel.Notice?>,
    open: (Screen) -> Unit,
    onBack: () -> Unit,
) {
    val shared by actions.shared.collectAsStateWithLifecycle()
    val files by Shaders.version.collectAsStateWithLifecycle()
    val settings by actions.settings.collectAsStateWithLifecycle()
    val problems by actions.problems.collectAsStateWithLifecycle()
    val entries = remember(shared) { shared.shaders }
    var query by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf<String?>(null) }
    val inUse = remember(settings, files) { actions.inUse() }
    BackHandler { onBack() }

    // Where each stands: installed, edited, has an update, and so on. It reads the user's files.
    val states by produceIo(
        emptyMap<String, ShaderState>(),
        entries,
        files,
        what = "Reading the shared shaders"
    ) {
        entries.associate { e ->
            val (state, taken) = actions.state(e)
            val takenBy =
                if (state == ScriptCatalog.State.NAME_TAKEN) actions.installedFrom(e.name)
                    ?.takeIf { it != e.source } else null
            e.key to ShaderState(SharedState(state, takenBy), taken)
        }
    }
    val shown = remember(entries, query) { SharedShaders.found(entries, query) }
    val added = shared.sources.count { !it.source.builtIn }

    GuiTheme {
        val snackbar = rememberNoticeSnackbar(notice)

        GuiListPage(
            title = { Text("Get more shaders") },
            subtitle = { Text(sharedIn(added)) },
            onBack = onBack,
            snackbar = snackbar,
            actions = {
                LabeledIconButton(
                    R.drawable.ic_refresh,
                    "Refresh the list",
                    onClick = { actions.refreshShared() },
                    enabled = !shared.loading
                )
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = { open(Screen.SCRIPT_SOURCES) }) { Text("Sources") }
            },
        ) {
            item(key = "search") {
                SearchField(
                    query,
                    { query = it },
                    "Search by name, author or tag",
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }
            if (shared.loading) item(key = "loading") {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
            if (!shared.loading && shared.failed.isNotEmpty()) {
                item(key = "error") {
                    SharedFailures(shared.failed) { actions.refreshShared() }
                }
            }
            itemsIndexed(shown, key = { _, e -> e.key }) { i, e ->
                SharedShaderRow(e, states[e.key]?.shared, i, shown.size) { details = e.key }
            }
            if (!shared.loading && shown.isEmpty() && shared.failed.isEmpty()) {
                item(key = "none") {
                    NoMatchNote(if (query.isBlank()) "No shaders shared yet." else "No shader matches “$query”.")
                }
            }
            item(key = "about") {
                PageHint(
                    (if (added == 0) "Every shader here is reviewed before it is added. "
                    else "Every shader in ${ScriptCatalog.SOURCE} is reviewed before it is added. Kolahal doesn't check shaders from sources you added. ") +
                            "A shader can only change how the screen looks: it has no files and no network. " +
                            "Share your own at github.com/mtwabpin/${ScriptCatalog.SOURCE}.",
                )
            }
        }

        details?.let { key -> entries.firstOrNull { it.key == key } }?.let { e ->
            SharedShaderSheet(
                actions,
                e,
                states[e.key],
                on = inUse == e.name,
                problem = problems[e.name],
                onDismiss = { details = null }
            )
        }
    }
}

@Composable
private fun SharedShaderRow(
    e: SharedShader,
    shared: SharedState?,
    index: Int,
    count: Int,
    onClick: () -> Unit
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = segShapes(index, count),
        colors = segColors(),
        overlineContent = {
            Text(
                ShaderPage.fileLine(e),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                listOfNotNull(
                    e.desc.ifBlank { null },
                    addedSource(e)?.let { "From $it" },
                    stateLabel(shared, e.version, e.requires)
                ).joinToString(" · "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (shared?.state == ScriptCatalog.State.UPDATE_AVAILABLE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) { Text(e.title) }
}

/**
 * The sheet for one shared shader: what it is, its code, and Install or Update. Once it is installed, it
 * offers Use, or Turn off while it is [on]. [problem] says why it couldn't be used.
 */
@Composable
private fun SharedShaderSheet(
    actions: ShaderActions,
    e: SharedShader,
    state: ShaderState?,
    on: Boolean,
    problem: String?,
    onDismiss: () -> Unit
) {
    var confirmReplace by remember(e.key) { mutableStateOf(false) }
    // Download the file and check it against the list, so the user can read it before installing.
    val code by produceState<Result<String>?>(null, e.sha256) {
        value = runCatching { actions.sharedCode(e) }
    }
    val tilt = remember(e, code) { ShaderPage.usesTilt(e, code?.getOrNull()) }

    GuiSheet(onDismiss, scroll = true) {
        Text(e.title, style = MaterialTheme.typography.titleLarge)
        Text(
            listOfNotNull(
                "${e.name}.${ShaderCatalog.EXT}",
                e.version.ifBlank { null }?.let { "v$it" },
                e.author.ifBlank { null }?.let { "by $it" }).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (e.desc.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(e.desc, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(12.dp))
        Fact("Moves", if (e.animated) "yes" else "no")
        if (tilt) Fact(ShaderPage.USES_TILT, "yes")
        if (e.license.isNotBlank()) Fact("License", e.license)
        if (e.tags.isNotEmpty()) Fact("Tags", e.tags.joinToString(", "))
        addedSource(e)?.let { Fact("From", "$it, a source you added") }

        Spacer(Modifier.height(16.dp))
        SharedButtons(
            state?.shared?.state,
            e.version,
            e.requires,
            taken = "${state?.taken ?: "The name ${e.name} is taken"}.",
            ready = code?.isSuccess == true,
            onInstall = { actions.install(e) },
            onUpdate = { actions.install(e) },
            onReplace = { confirmReplace = true },
            installed = {
                if (on) OutlinedButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = { actions.off() }) { Text("Turn off") }
                else Button(
                    shapes = ButtonDefaults.shapes(),
                    onClick = { actions.use(e.name) },
                    enabled = Shaders.supported
                ) { Text("Use") }
            },
        )
        val error = problem?.let { "Can't be used: $it" } ?: Shaders.NEEDS.takeIf { !Shaders.supported }
        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        SharedCode(code) { ReadOnlyCode(it, Syntax.AGSL) }
    }

    if (confirmReplace) {
        ConfirmDialog(
            "Replace ${e.name}.${ShaderCatalog.EXT}?",
            "Your copy differs from the shared one. Its code is replaced and your changes are lost.",
            "Replace",
            onDismiss = { confirmReplace = false },
        ) { confirmReplace = false; actions.install(e, replace = true) }
    }
}
