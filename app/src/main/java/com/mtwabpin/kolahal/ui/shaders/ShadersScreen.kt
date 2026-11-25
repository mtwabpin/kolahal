/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.shaders

import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.commands.Access
import com.mtwabpin.kolahal.commands.Accesses
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.SettingTitles
import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderOverApps
import com.mtwabpin.kolahal.shader.ShaderParams
import com.mtwabpin.kolahal.shader.ShaderWords
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.shader.UsageWatch
import com.mtwabpin.kolahal.shader.WallpaperChoice
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.ShaderActions
import com.mtwabpin.kolahal.terminal.ShaderRow
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.terminal.commands.ShaderCommands
import com.mtwabpin.kolahal.ui.AccessNeeded
import com.mtwabpin.kolahal.ui.AccessNeeds
import com.mtwabpin.kolahal.ui.Action
import com.mtwabpin.kolahal.ui.ActionGroup
import com.mtwabpin.kolahal.ui.DialogBars
import com.mtwabpin.kolahal.ui.GroupHeader
import com.mtwabpin.kolahal.ui.GuiListPage
import com.mtwabpin.kolahal.ui.LabeledIconButton
import com.mtwabpin.kolahal.ui.PageHint
import com.mtwabpin.kolahal.ui.SearchField
import com.mtwabpin.kolahal.ui.home.rememberScreenReader
import com.mtwabpin.kolahal.ui.produceIo
import com.mtwabpin.kolahal.ui.rememberMissing
import com.mtwabpin.kolahal.ui.rememberExpandedSheetState
import com.mtwabpin.kolahal.ui.rememberNoticeSnackbar
import com.mtwabpin.kolahal.ui.settings.NEEDS_ACCESS
import com.mtwabpin.kolahal.ui.settings.PEEK_FADE_MS
import com.mtwabpin.kolahal.ui.settings.groupShows
import com.mtwabpin.kolahal.ui.settings.PeekButton
import com.mtwabpin.kolahal.ui.settings.PeekHint
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import com.mtwabpin.kolahal.ui.theme.segColors
import com.mtwabpin.kolahal.ui.theme.segShapes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The Shaders tab: the shader over home and the full-screen screens (`screen.shader`).
 * - None, the shaders that come with Kolahal, then the user's own. A tap turns one on, if it compiles:
 *   otherwise the one that is on stays, and the row says why. The one in use is selected.
 * - Details, on each row, opens its sheet ([ShaderSheet]). Its Edit opens the shader's code in the editor ([onEdit]).
 * - Add shader: Get more shaders, Import a file, Read the script folder again, Start from a template
 *   ([TemplateSheet]).
 * - Under the one-off shaders, Play over other apps ([OverAppsSwitch]), in a build that has it.
 * - On Android 12 and older the shaders are listed, but can't be used.
 * - While it is open, the values from the phone's use that any listed shader follows are worked out ([UsageWatch.list]),
 *   so its details and previews show them even when it isn't in use.
 *
 * Without the view model: it needs its [actions], the page's [notice], and the way to other screens
 * ([open]) and [onBack]. [tabs] is the tab row above the list (com.mtwabpin.kolahal.ui.FeatureTabs).
 */
@Composable
fun ShadersScreen(
    actions: ShaderActions,
    notice: StateFlow<TerminalViewModel.Notice?>,
    open: (Screen) -> Unit,
    onBack: () -> Unit,
    peek: StateFlow<TerminalViewModel.Peek?> = remember { MutableStateFlow(null) },
    onPeek: (TerminalViewModel.Peek?) -> Unit = {},
    onAskNotifications: () -> Unit = {},
    onEdit: (String) -> Unit = {},
    tabs: (@Composable () -> Unit)? = null,
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
    // While the screen is open, the phone's use is worked out for every shader listed, not only the one in use.
    DisposableEffect(all) {
        UsageWatch.list(all.orEmpty().filter { it.meta.params.isNotEmpty() }.associate { it.name to it.meta.params })
        onDispose { UsageWatch.list(emptyMap()) }
    }
    // Asked again on the way back from Android's preview.
    val chosen by WallpaperChoice.name.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isWallpaper by produceState(false, chosen, lifecycle) { value = actions.isWallpaper() }
    // The one the wallpaper draws: Kolahal's when the chosen one is gone, or none was chosen.
    val wallpaper = ShaderCommands.wallpaperDrawn(chosen).takeIf { isWallpaper }
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
    var templates by rememberSaveable { mutableStateOf(false) }
    // The shader whose sheet is open, by name. It survives rotation.
    var details by rememberSaveable { mutableStateOf<String?>(null) }
    // Many file managers don't know .agsl. So accept any file and check its name.
    val importFiles =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
            actions.importFiles(it)
        }
    BackHandler { if (query.isNotEmpty()) query = "" else onBack() }
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
            top = 0.dp,
            bottom = 96.dp,
        ) {
            if (tabs != null) item(key = "tabs") { tabs() }
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
                            shapes = FilterChipDefaults.shapes(),
                            colors = FilterChipDefaults.filterChipColors(),
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
                    wallpaper = r.name == wallpaper,
                    onUse = { actions.use(r.name) },
                    onDetails = { details = r.name },
                )
            }
            if (mine.isNotEmpty() && bundled.isNotEmpty()) item(key = "bundled") { GroupHeader("Come with Kolahal") }
            itemsIndexed(bundled, key = { _, r -> "shader:" + r.name }) { i, r ->
                ShaderChoice(
                    r, inUse, problems[r.name], i + above, bundled.size + above,
                    wallpaper = r.name == wallpaper,
                    onUse = { actions.use(r.name) },
                    onDetails = { details = r.name },
                )
            }
            if (once.isNotEmpty()) item(key = "once") { GroupHeader("One-off: played once over home") }
            itemsIndexed(once, key = { _, r -> "shader:" + r.name }) { i, r ->
                ShaderChoice(
                    r, inUse, problems[r.name], i, once.size,
                    wallpaper = false,
                    onUse = { play(r.name) },
                    onDetails = { details = r.name },
                )
            }
            if (actions.overAppsOffered && once.isNotEmpty()) item(key = "over-apps") { OverAppsSwitch(actions, onAskNotifications) }
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
                order, name, inUse, wallpaper, problems, actions,
                shown = { shown.value },
                onShow = { details = it },
                onPlay = play,
                onAskNotifications = onAskNotifications,
                onPeek = { shader -> onPeek(shader?.let { TerminalViewModel.Peek(null, false, it) }) },
                onEdit = onEdit,
                onDismiss = { details = null },
            )
        }

        if (adding) AddShaderSheet(
            onDismiss = { adding = false },
            onGetMore = { adding = false; open(Screen.GET_SHADERS) },
            onImport = { adding = false; importFiles.launch(arrayOf("*/*")) },
            onReadFolder = { adding = false; actions.readFolder() },
            onTemplate = { adding = false; templates = true },
        )
        if (templates) TemplateSheet(
            actions,
            onDismiss = { templates = false },
            onPick = { templates = false; actions.startFrom(it) },
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
    wallpaper: Boolean,
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
                val words by ShaderWords.words.collectAsStateWithLifecycle()
                // One that follows the phone's use draws on its own values without usage access, and says that it needs it.
                val follows by ShaderParams.follows.collectAsStateWithLifecycle()
                val needs = rememberMissing(AccessNeeds.ofParams(row.meta.params, follows[row.name].orEmpty()))
                    .mapNotNull { word -> Accesses.find(word)?.let(AccessNeeds::line) }
                val notes = (ShaderPage.notes(row, inUse, words[row.name].takeIf { row.meta.once != null }, wallpaper) + needs).joinToString(" · ")
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

/**
 * Play over other apps (`screen.shader-over-apps`), under the one-off shaders: a switch, as a setting's row in
 * Settings. Only a build and an Android that can do it show it ([ShaderActions.overAppsOffered]).
 * - It needs notification access, as no word plays a shader without it, and Android's Display over other apps
 *   access ([NEEDS_ACCESS]). While one is missing, its name stands over a line and a button for each missing
 *   access in the switch's place ([AccessNeeded]), as in Settings. What is stored stays, and nothing plays over apps.
 * - The button for Display over other apps says what the access is for, then opens Android's page for it.
 */
@Composable
private fun OverAppsSwitch(actions: ShaderActions, onAskNotifications: () -> Unit) {
    val key = Keys.SHADER_OVER_APPS
    val title = SettingTitles[key.key] ?: key.key
    val shows = groupShows(listOf(key), rememberMissing(remember { NEEDS_ACCESS[key].orEmpty().toSet() }))
    if (key !in shows.keys) {
        val context = LocalContext.current
        val ask = { access: Access ->
            if (access.word == Accesses.OVERLAY) {
                // Android's page covers this one: a toast shows over it.
                Toast.makeText(context, ShaderOverApps.WHY, Toast.LENGTH_LONG).show()
                actions.askOverApps()
            } else onAskNotifications()
        }
        Column(Modifier.testTag("shader-over-apps")) {
            GroupHeader(title)
            shows.asks.forEachIndexed { i, word ->
                AccessNeeded(word, ask, Modifier.padding(start = 16.dp, end = 16.dp, top = if (i == 0) 0.dp else 8.dp))
            }
        }
        return
    }
    val settings by actions.settings.collectAsStateWithLifecycle()
    val on = remember(settings) { actions.overApps() }
    SegmentedListItem(
        checked = on,
        onCheckedChange = { actions.setOverApps(it) },
        shapes = segShapes(0, 1),
        colors = segColors(),
        supportingContent = {
            Column {
                Text(key.help.replaceFirstChar { it.uppercase() })
                Text(
                    key.key,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        },
        trailingContent = { Switch(checked = on, onCheckedChange = null) },
        modifier = Modifier
            .padding(top = 8.dp)
            .testTag("shader-over-apps"),
    ) { Text(title) }
}

/** The ways to add a shader. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddShaderSheet(
    onDismiss: () -> Unit,
    onGetMore: () -> Unit,
    onImport: () -> Unit,
    onReadFolder: () -> Unit,
    onTemplate: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        DialogBars()
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
                    Action(
                        R.drawable.ic_edit,
                        "Start from a template",
                        "A short shader of your own to edit, with comments",
                        chevron = true,
                        onClick = onTemplate
                    ),
                )
            )
        }
    }
}

/**
 * Add shader → Start from a template: the templates ([ShaderCatalog.TEMPLATES]), each with its name and
 * description. A tap makes a shader of the user's from one ([ShaderActions.startFrom]).
 *
 * Each row starts with a picture of its template over a whole screen, small ([SmallShaderPreview]): each is
 * drawn once, and stands still (one that moves doesn't, a one-off shows itself part way, one that follows
 * touches has none). Nothing is drawn once the sheet closes. Without shaders (Android 12 and older), or with a
 * screen reader on, where none is drawn, the rows have their icon instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateSheet(
    actions: ShaderActions,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val context = LocalContext.current.applicationContext
    // Their files are read off the main thread: what each header says, and its code for the picture.
    val templates by produceIo<List<Triple<String, ShaderMeta, String>>?>(null, what = "Reading the templates") {
        actions.templates().map { (name, meta) -> Triple(name, meta, Shaders.bundled(context, name)) }
    }
    val pictures = Shaders.supported && !rememberScreenReader()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberExpandedSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        DialogBars()
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Text(
                "Start from a template",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
            Text(
                "It becomes one of your shaders, as a file in the script folder. Edit in its details opens it; for a file you changed elsewhere, 'shader reload' reads it again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 12.dp),
            )
            templates?.let { all ->
                ActionGroup(
                    all.map { (name, meta, _) ->
                        Action(
                            R.drawable.ic_code,
                            meta.title ?: name,
                            meta.description.orEmpty(),
                            onClick = { onPick(name) }
                        )
                    },
                    leading = if (pictures) {
                        { i -> SmallShaderPreview(all[i].first, all[i].third, Modifier.testTag("template-preview ${all[i].first}")) }
                    } else null,
                )
            }
        }
    }
}
