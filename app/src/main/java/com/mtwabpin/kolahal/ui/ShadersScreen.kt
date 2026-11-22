/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.StateFlow

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
    val bundled = remember(all) { all.orEmpty().filter { it.bundled } }
    val mine = remember(all) { all.orEmpty().filter { !it.bundled } }
    var adding by rememberSaveable { mutableStateOf(false) }
    // The shader whose sheet is open, by name. It survives rotation.
    var details by rememberSaveable { mutableStateOf<String?>(null) }
    // Many file managers don't know .agsl. So accept any file and check its name.
    val importFiles =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
            actions.importFiles(it)
        }
    BackHandler { onBack() }

    GuiTheme {
        val snackbar = rememberNoticeSnackbar(notice)

        GuiListPage(
            title = { Text("Shaders") },
            subtitle = { Text("Effects over home and the full-screen screens") },
            onBack = onBack,
            snackbar = snackbar,
            floatingActionButton = {
                SmallExtendedFloatingActionButton(
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
            // The user's own come first: the ones that come with Kolahal fill several screens. With none of
            // their own, None and Kolahal's are one group.
            val noneIn = if (mine.isEmpty()) bundled.size + 1 else 1
            item(key = "none") {
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
            if (mine.isNotEmpty()) item(key = "bundled") { GroupHeader("Come with Kolahal") }
            itemsIndexed(bundled, key = { _, r -> "shader:" + r.name }) { i, r ->
                ShaderChoice(
                    r, inUse, problems[r.name], if (mine.isEmpty()) i + 1 else i, if (mine.isEmpty()) bundled.size + 1 else bundled.size,
                    onUse = { actions.use(r.name) },
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

        details?.let { name -> all?.firstOrNull { it.name == name } }?.let { row ->
            ShaderSheet(row, inUse, problems[row.name], actions, onDismiss = { details = null })
        }

        if (adding) AddShaderSheet(
            onDismiss = { adding = false },
            onGetMore = { adding = false; open(Screen.GET_SHADERS) },
            onImport = { adding = false; importFiles.launch(arrayOf("*/*")) },
            onReadFolder = { adding = false; actions.readFolder() },
        )
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
            RadioButton(
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
