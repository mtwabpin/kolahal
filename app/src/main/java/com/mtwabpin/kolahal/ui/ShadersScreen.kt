/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.script.ShaderCatalog
import com.mtwabpin.kolahal.script.Shaders
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.ShaderActions
import com.mtwabpin.kolahal.terminal.ShaderRow
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Settings → Shaders: the shader over home and the full-screen screens (`screen.shader`).
 * - None, the shaders that come with Kolahal, then the user's own. A tap turns one on, if it compiles:
 *   otherwise the one that is on stays, and the row says why. The one in use is selected.
 * - Details, on each row, opens its sheet ([ShaderSheet]).
 * - Add shader: Get more shaders, Import a file, Read the script folder again.
 * - On Android 12 and older the shaders are listed, but can't be used.
 *
 * Without the view model: it needs its [actions], the page's [notice], and the way to other screens
 * ([open]) and [onBack].
 */
@Composable
fun ShadersScreen(
    actions: ShaderActions,
    notice: StateFlow<TerminalViewModel.Notice?>,
    open: (Screen) -> Unit,
    onBack: () -> Unit,
    peek: StateFlow<TerminalViewModel.Peek?> = remember { MutableStateFlow(null) },
    onPeek: (TerminalViewModel.Peek?) -> Unit = {},
) {
    val files by Shaders.version.collectAsStateWithLifecycle()
    val trips by actions.trips.collectAsStateWithLifecycle()
    val settings by actions.settings.collectAsStateWithLifecycle()
    val shared by actions.shared.collectAsStateWithLifecycle()
    val problems by actions.problems.collectAsStateWithLifecycle()
    val inUse = remember(settings, files) { actions.inUse() }
    // Their files are read off the main thread, again when one changes. Null until they are read.
    val rows by produceIo<List<ShaderRow>?>(
        null,
        files,
        trips,
        shared.sources,
        what = "Reading the shaders"
    ) { actions.rows() }
    val all = rows
    var query by rememberSaveable { mutableStateOf("") }
    var chips by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val filters = remember(chips) { ShaderPage.Filter.entries.filterTo(LinkedHashSet()) { it.name in chips } }
    val looking = query.isNotBlank() || filters.isNotEmpty()
    val found = remember(all, query, filters) { all.orEmpty().filter { ShaderPage.matches(it, query.trim(), filters) } }
    // The one-off shaders are a group of their own: they aren't turned on, but played.
    val bundled = remember(found) { found.filter { it.bundled && it.meta.once == null } }
    val mine = remember(found) { found.filter { !it.bundled && it.meta.once == null } }
    val once = remember(found) { found.filter { it.meta.once != null } }
    val play: (String) -> Unit = {
        actions.play(it)
        open(Screen.TERMINAL)
    }
    var adding by rememberSaveable { mutableStateOf(false) }
    // The shader whose sheet is open, by name. It survives rotation.
    var details by rememberSaveable { mutableStateOf<String?>(null) }
    // Many file managers don't know .agsl. So accept any file and check its name.
    val importFiles =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
            actions.importFiles(it)
        }
    BackHandler { onBack() }
    // While home shows through (an eye is held), the page and an open sheet clear.
    val peeking by peek.collectAsStateWithLifecycle()
    val shown = animateFloatAsState(if (peeking == null) 1f else 0f, tween(PEEK_FADE_MS), label = "peek")
    val scope = rememberCoroutineScope()

    GuiTheme {
      Box(Modifier.fillMaxSize()) {
        val snackbar = rememberNoticeSnackbar(notice)
        val sayHold: () -> Unit = {
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar("Tap and hold the eye to see the home screen")
            }
        }

        GuiListPage(
            title = { Text("Shaders") },
            subtitle = { Text("Effects over home and the full-screen screens") },
            onBack = onBack,
            snackbar = snackbar,
            actions = {
                PeekButton(
                    onHold = { holding -> onPeek(if (holding) TerminalViewModel.Peek(null, false, inUse.takeIf { it != ShaderCatalog.OFF }) else null) },
                    onTap = sayHold,
                )
            },
            fade = { shown.value },
            listModifier = Modifier.graphicsLayer { alpha = shown.value },
            floatingActionButton = {
                SmallExtendedFloatingActionButton(
                    modifier = Modifier.graphicsLayer { alpha = shown.value },
                    onClick = { adding = true },
                    icon = { Icon(painterResource(R.drawable.ic_add), null) },
                    text = { Text("Add shader") },
                )
            },
            bottom = 96.dp,
        ) {
            if (!Shaders.supported) item(key = "needs") {
                Text(
                    "${Shaders.NEEDS}. This phone has Android ${Build.VERSION.RELEASE}, so no shader is drawn.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
            }
            if (all == null) return@GuiListPage
            item(key = "search") {
                Column(Modifier.padding(bottom = 8.dp)) {
                    SearchField(query, { query = it }, "Search shaders", Modifier.fillMaxWidth().testTag("shader-search"), clear = "Clear the search")
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (f in ShaderPage.Filter.entries) FilterChip(
                            selected = f in filters,
                            onClick = { chips = if (f.name in chips) chips - f.name else chips + f.name },
                            label = { Text(f.label) },
                            modifier = Modifier.testTag("shader-filter ${f.name}"),
                        )
                    }
                }
            }
            if (looking && found.isEmpty()) item(key = "none-found") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No shader matches", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp))
                    TextButton(onClick = { query = ""; chips = emptyList() }, shapes = ButtonDefaults.shapes()) { Text("Clear") }
                }
            }
            // The user's own come first: the ones that come with Kolahal fill several screens. With none of
            // their own, None and Kolahal's are one group.
            val above = if (mine.isEmpty() && !looking) 1 else 0
            val noneIn = if (mine.isEmpty()) bundled.size + 1 else 1
            if (!looking) item(key = "none") {
                SegmentedListItem(
                    selected = inUse == ShaderCatalog.OFF,
                    onClick = { actions.off() },
                    shapes = segShapes(0, noneIn),
                    colors = segColors(),
                    leadingContent = {
                        RadioButton(
                            selected = inUse == ShaderCatalog.OFF,
                            onClick = null
                        )
                    },
                    supportingContent = { Text("No shader") },
                    modifier = Modifier.testTag("shader off"),
                ) { Text("None") }
            }
            if (mine.isNotEmpty()) item(key = "mine") { GroupHeader("Your shaders") }
            itemsIndexed(mine, key = { _, r -> "shader:" + r.name }) { i, r ->
                ShaderChoice(
                    r, inUse, problems[r.name], i, mine.size,
                    onUse = { actions.use(r.name) },
                    onDetails = { details = r.name },
                )
            }
            if (mine.isNotEmpty() && bundled.isNotEmpty()) item(key = "bundled") { GroupHeader("Come with Kolahal") }
            itemsIndexed(bundled, key = { _, r -> "shader:" + r.name }) { i, r ->
                ShaderChoice(
                    r, inUse, problems[r.name], i + above, bundled.size + above,
                    onUse = { actions.use(r.name) },
                    onDetails = { details = r.name },
                )
            }
            if (once.isNotEmpty()) item(key = "once") { GroupHeader("One-off: played once over home") }
            itemsIndexed(once, key = { _, r -> "shader:" + r.name }) { i, r ->
                ShaderChoice(
                    r, inUse, problems[r.name], i, once.size,
                    onUse = { play(r.name) },
                    onDetails = { details = r.name },
                )
            }
            item(key = "hint") {
                PageHint(
                    "If a shader makes home hard to read, hold two fingers still on it for a second. " +
                            "Terminal: shader · shader <name> · shader off",
                )
            }
        }

        val order = mine + bundled + once
        details?.takeIf { name -> order.any { it.name == name } }?.let { name ->
            ShaderSheet(
                order, name, inUse, problems, actions,
                shown = { shown.value },
                onShow = { details = it },
                onPlay = play,
                onPeek = { shader -> onPeek(shader?.let { TerminalViewModel.Peek(null, false, it) }) },
                onDismiss = { details = null },
            )
        }

        if (adding) AddShaderSheet(
            onDismiss = { adding = false },
            onGetMore = { adding = false; open(Screen.GET_SHADERS) },
            onImport = { adding = false; importFiles.launch(arrayOf("*/*")) },
            onReadFolder = { adding = false; actions.readFolder() },
        )
        if (peeking != null) PeekHint(Modifier.align(Alignment.TopCenter))
      }
    }
}

/**
 * One shader to choose: its file, its title, its description, and what it is ([ShaderPage.notes]). [problem]
 * says why it couldn't be used. [inUse] is the one set.
 */
@Composable
private fun ShaderChoice(
    row: ShaderRow,
    inUse: String,
    problem: String?,
    index: Int,
    count: Int,
    onUse: () -> Unit,
    onDetails: () -> Unit
) {
    val selected = row.name == inUse
    val colors = MaterialTheme.colorScheme
    SegmentedListItem(
        selected = selected,
        onClick = onUse,
        shapes = segShapes(index, count),
        colors = segColors(),
        enabled = Shaders.supported,
        leadingContent = {
            if (row.meta.once != null) Icon(painterResource(R.drawable.ic_play), null, tint = colors.primary)
            else RadioButton(
                selected = selected,
                onClick = null,
                enabled = Shaders.supported
            )
        },
        overlineContent = {
            Text(
                "${row.name}.${ShaderCatalog.EXT}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
            )
        },
        supportingContent = {
            Column {
                row.meta.description?.let { Text(it) }
                val notes = ShaderPage.notes(row, inUse).joinToString(" · ")
                if (notes.isNotEmpty()) Text(
                    notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        row.turnedOff -> colors.error
                        selected || row.origin?.newer == true -> colors.primary
                        else -> colors.onSurfaceVariant
                    }
                )
                problem?.let { Text("Can't be used: $it", color = colors.error) }
            }
        },
        trailingContent = {
            LabeledIconButton(
                R.drawable.ic_info,
                "Details of ${row.title}",
                onClick = onDetails,
                tint = colors.primary
            )
        },
        modifier = Modifier.testTag("shader ${row.name}"),
    ) { Text(row.title) }
}

/** The ways to add a shader. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddShaderSheet(
    onDismiss: () -> Unit,
    onGetMore: () -> Unit,
    onImport: () -> Unit,
    onReadFolder: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            ActionGroup(
                listOf(
                    Action(
                        R.drawable.ic_download,
                        "Get more shaders",
                        "Shared by people who use Kolahal, on GitHub",
                        chevron = true,
                        onClick = onGetMore
                    ),
                    Action(
                        R.drawable.ic_upload,
                        "Import a file",
                        ".agsl; the file name becomes its name",
                        onClick = onImport
                    ),
                    Action(
                        R.drawable.ic_folder,
                        "Read the script folder again",
                        "The .agsl files in it, and in its ${Shaders.DIR} folder",
                        onClick = onReadFolder
                    ),
                )
            )
        }
    }
}
