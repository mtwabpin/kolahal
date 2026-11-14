package com.mtwabpin.kolahal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.terminal.WidgetFeed
import com.mtwabpin.kolahal.terminal.WidgetScript
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.ProviderEntry
import com.mtwabpin.kolahal.widgets.WidgetHost
import com.mtwabpin.kolahal.widgets.WidgetLayout
import com.mtwabpin.kolahal.widgets.WidgetLocation
import com.mtwabpin.kolahal.widgets.WidgetOrder
import com.mtwabpin.kolahal.widgets.WidgetRef
import com.mtwabpin.kolahal.widgets.WidgetSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import android.os.SystemClock
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shows the messages of [WidgetHost] in a snackbar: each waiting message once, oldest first.
 * It works on whichever widget screen is open.
 */
@Composable
internal fun rememberWidgetMessages(widgets: WidgetHost): SnackbarHostState {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { widgets.messages.collect { if (it.isNotEmpty()) widgets.takeMessage()?.let { m -> snackbar.showSnackbar(m) } } }
    return snackbar
}

/**
 * The name of a widget Kolahal draws itself, from its source ([WidgetSource]). Null for an app's widget.
 * - `script:weather`: the script's `@title`, else its name.
 * - `script:weather#2`, a further widget of the script: the same, then its id: "Weather 2".
 * - `status:weather`: the script's title or name, then "status".
 * - `builtin:battery`: the built-in widget's name ([WidgetSource.builtinTitle]), "Battery and network".
 * - A script that is gone, and a built-in widget this version does not know: the name in the source.
 *
 * [scripts] are the scripts as the view model has read them, by name ([WidgetScript]). No code is
 * read here.
 */
internal fun sourceTitle(provider: String, scripts: Map<String, WidgetScript>): String? {
    if (!WidgetSource.isSource(provider)) return null
    WidgetSource.builtinId(provider)?.let { return WidgetSource.builtinTitle(it) ?: it }
    WidgetSource.scriptRef(provider)?.let { ref -> return ref.titled(scripts[ref.script]?.title ?: ref.script) }
    WidgetSource.statusName(provider)?.let { name -> return "${scripts[name]?.title ?: name} status" }
    // A script's source whose id can't be one: the text as it is stored.
    return provider.substringAfter(':')
}

/**
 * The widgets screen (Material 3 Expressive). It shows every placed widget, live, in the same
 * layout as the widgets page ([WidgetFlow]). A widget Kolahal draws itself shows here as a box with
 * its name, to be resized and moved: its content is on the widgets page.
 * To open a widget's toolbar at the bottom, long-press the widget, or tap Edit and then the widget.
 * The toolbar has:
 * - width, in twelfths of the page;
 * - height, in 8dp steps (both repeat while held);
 * - square, move and remove.
 * Entries restored from a backup show an "Add again" row.
 */
@Composable
fun WidgetsScreen(vm: TerminalViewModel, onReAdd: (ProviderEntry, PlacedWidget) -> Unit) {
    val context = LocalContext.current
    val widgets = remember { WidgetHost.get(context) }
    val all by widgets.widgets.collectAsStateWithLifecycle()
    val providersVersion by widgets.providersVersion.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val version by vm.config.version.collectAsStateWithLifecycle()
    // With widgets.page off, no page next to the terminal shows the widgets, so this screen says so.
    val pageOff = remember(version) { vm.config[Keys.WIDGETS_PAGE] == "off" }
    var editing by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf<PlacedWidget?>(null) }
    // The Shortcuts widget's editor is open: from the toolbar of that widget, or because a tap on its card asked for it.
    var shortcutsEditor by rememberSaveable { mutableStateOf(false) }
    val editorAsked by vm.shortcutsEditorAsked.collectAsStateWithLifecycle()
    LaunchedEffect(editorAsked) {
        if (editorAsked) {
            shortcutsEditor = true
            vm.shortcutsEditorOpened()
        }
    }
    // The options sheet of one script widget is open, by the widget's key: from that widget's toolbar,
    // or because the picker just placed a further widget of a script and asked for it.
    var optionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    val optionsAsked by vm.widgetOptionsAsked.collectAsStateWithLifecycle()
    LaunchedEffect(optionsAsked) {
        optionsAsked?.let {
            optionsFor = it.key
            vm.widgetOptionsOpened()
        }
    }
    val snackbar = rememberWidgetMessages(widgets)
    BackHandler { if (editing || selected != null) { editing = false; selected = null } else vm.closeScreen() }

    val panel = remember(all) { WidgetOrder.inLocation(all, WidgetLocation.PANEL) }
    val pending = remember(all) { WidgetOrder.sorted(all.filter { it.pending }) }
    // Each label needs a binder call and a load. So labels load again only when widgets are added or
    // removed, not on every resize step.
    val placed = remember(all) { all.map { Triple(it.key, it.id, it.provider) } }
    val labels = remember(placed, providersVersion) {
        placed.associate { (key, id, provider) -> key to (id?.let { widgets.cachedInfo(it) }?.loadLabel(context.packageManager) ?: provider.substringAfterLast('.')) }
    }
    // A widget Kolahal draws itself is named by its script's title. The view model has read the titles
    // from the code, off the main thread, and its answer changes only when a title does: not at
    // every run. They are apart from the labels above, which cost a call to another app each.
    val scripts by vm.widgetScripts.collectAsStateWithLifecycle()
    val titles = remember(placed, scripts) {
        placed.mapNotNull { (key, _, provider) -> sourceTitle(provider, scripts.orEmpty())?.let { key to it } }.toMap()
    }
    fun labelOf(w: PlacedWidget): String? = titles[w.key] ?: labels[w.key]
    val chosen = panel.firstOrNull { it.key == selected }

    GuiTheme {
        val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            modifier = Modifier.imePadding().nestedScroll(scroll.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                LargeFlexibleTopAppBar(
                    title = { Text("Widgets") },
                    subtitle = {
                        Text(
                            when {
                                pageOff -> "The widgets page is off"
                                panel.isEmpty() -> "On the widgets page beside the terminal"
                                else -> "${panel.size} on the widgets page"
                            }
                        )
                    },
                    navigationIcon = { BackButton(vm::closeScreen) },
                    actions = {
                        if (panel.isNotEmpty()) {
                            IconToggleButton(shapes = IconButtonDefaults.toggleableShapes(), checked = editing, onCheckedChange = { editing = it; selected = null }) {
                                Icon(painterResource(if (editing) R.drawable.ic_check else R.drawable.ic_edit), if (editing) "Done" else "Edit widgets")
                            }
                        }
                    },
                    scrollBehavior = scroll,
                )
            },
            bottomBar = {
                if (chosen != null) {
                    val chosenRef = WidgetSource.scriptRef(chosen.source)
                    WidgetToolbar(
                        w = chosen,
                        label = labelOf(chosen) ?: chosen.provider,
                        index = panel.indexOf(chosen),
                        count = panel.size,
                        minHeight = remember(chosen.key, providersVersion) { widgets.minHeightOf(chosen) },
                        onMove = { widgets.move(chosen.key, it) },
                        onHeight = { widgets.resize(chosen.key, it * WidgetLayout.STEP_DP) },
                        onWidth = { widgets.resizeWidth(chosen.key, it) },
                        onSquare = { widgets.square(chosen.key) },
                        // What the widget itself holds, if anything:
                        // - the Shortcuts widget: its list of commands;
                        // - a widget of a script that declares options: its options. The first widget's
                        //   are the script's own, and a further widget can have values of its own.
                        edit = when {
                            chosen.source == WidgetSource.builtin(WidgetSource.SHORTCUTS) -> ToolbarEdit("Edit shortcuts") { shortcutsEditor = true }
                            chosenRef != null && scripts?.get(chosenRef.script)?.hasOptions == true -> ToolbarEdit("Options") { optionsFor = chosenRef.key }
                            else -> null
                        },
                        onRemove = { confirmRemove = chosen },
                        onDone = { selected = null; editing = false },
                    )
                } else if (editing) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Tap a widget to resize or move it",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                        )
                    }
                }
            },
            floatingActionButton = {
                if (!editing && chosen == null) {
                    SmallExtendedFloatingActionButton(
                        onClick = { vm.openScreen(Screen.WIDGET_PICKER) },
                        icon = { Icon(painterResource(R.drawable.ic_add), null) },
                        text = { Text("Add widget") },
                    )
                }
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .navigationBarScrim()
                    .verticalScroll(rememberScrollState())
                    .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp)
                    // The widgets get the same width as on the widgets page (its margins and side
                    // insets). Text keeps the usual margin.
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = WidgetLayout.GAP_DP.dp),
            ) {
                val inset = Modifier.padding(horizontal = 16.dp - WidgetLayout.GAP_DP.dp)
                if (pageOff) {
                    Box(inset.padding(vertical = 8.dp)) {
                        ActionGroup(
                            listOf(
                                Action(R.drawable.ic_visibility, "Turn on the widgets page", "Beside the terminal: swipe sideways on the home screen to reach it") {
                                    vm.config.set(Keys.WIDGETS_PAGE, "left")
                                },
                            )
                        )
                    }
                }
                if (panel.isEmpty() && pending.isEmpty()) {
                    EmptyNote(
                        if (pageOff) "No widgets yet." else "No widgets yet. They appear on the widgets page beside the terminal: swipe sideways on the home screen to reach it.",
                        inset,
                    )
                    Box(inset) {
                    ActionGroup(
                        listOf(
                            Action(R.drawable.ic_widgets, "Add a widget", "Clock, weather, calendar, music… from any app") {
                                vm.openScreen(Screen.WIDGET_PICKER)
                            },
                        )
                    )
                    }
                }
                WidgetFlow(panel.map { it.span }, WidgetLayout.GAP_DP.dp, Modifier.fillMaxWidth()) {
                    panel.forEach { w ->
                        key(w.key) {
                            WidgetCard(
                                w = w,
                                label = labelOf(w) ?: w.provider,
                                selected = selected == w.key,
                                editing = editing,
                                pausedKind = profiles.pausedKind(w.profileSerial),
                                feed = vm.widgetFeed,
                                onSelect = {
                                    selected = if (selected == w.key) null else w.key
                                    editing = true
                                },
                            )
                        }
                    }
                }
                if (pending.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    GroupHeader("Restored from backup")
                    Text(
                        "Widgets can't be copied between installs — add each one again with one tap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = inset.padding(start = 16.dp, bottom = 8.dp),
                    )
                    Column(inset, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        pending.forEachIndexed { i, w ->
                            PendingRow(w, i, pending.size, providersVersion, onReAdd = onReAdd, onDismiss = { widgets.remove(w.key) })
                        }
                    }
                }
                if (panel.isNotEmpty()) {
                    PageHint("Long-press a widget, or tap ✎ and then a widget, to resize and move it. Terminal: widgets ls · widget add", inset)
                }
            }
        }

        confirmRemove?.let { w ->
            ConfirmDialog(
                "Remove ${labelOf(w) ?: "widget"}?",
                "The widget is deleted; add it again from the picker any time.",
                "Remove",
                icon = R.drawable.ic_delete,
                onDismiss = { confirmRemove = null },
            ) { confirmRemove = null; selected = null; widgets.remove(w.key) }
        }

        if (shortcutsEditor) ShortcutsEditor(vm) { shortcutsEditor = false }

        optionsFor?.let(WidgetRef::parse)?.let { ref -> WidgetOptionsSheet(vm, ref) { optionsFor = null } }
    }
}

/** A button of the toolbar for what the selected widget itself holds: its [label], and what a tap does. */
private class ToolbarEdit(val label: String, val onClick: () -> Unit)

/**
 * One widget as it shows on the widgets page. While [editing], a tap selects it instead of going to the widget.
 * A card Kolahal draws reads its script from [feed], as it does on the widgets page.
 */
@Composable
private fun WidgetCard(
    w: PlacedWidget,
    label: String,
    selected: Boolean,
    editing: Boolean,
    pausedKind: ProfileKind?,
    feed: WidgetFeed,
    onSelect: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
    ) {
        Box {
            // A widget Kolahal draws itself shows its real card here, without taps of its own: a tap
            // anywhere on it selects it, as on an app's widget.
            if (w.source != null) {
                Box(Modifier.fillMaxWidth().height(w.heightDp.dp)) {
                    SourceWidget(w, w.heightDp.dp, actions = null, feed = feed)
                    Box(Modifier.matchParentSize().clickable(onClickLabel = "change $label", onClick = onSelect))
                }
            }
            if (w.source == null) HostedWidget(w, w.heightDp.dp, pausedKind, onSelect) { why ->
                Box(
                    Modifier.fillMaxWidth().height(w.heightDp.dp).alpha(0.6f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = onSelect) { Text("Edit") }
                    }
                }
            }
            if (editing) Box(Modifier.matchParentSize().clickable(onClickLabel = "change $label", onClick = onSelect))
        }
    }
}

/**
 * The controls for the selected widget, docked at the bottom: width, height, square, move, remove.
 * With [edit], there is a button for what the widget itself holds: the Shortcuts widget's list, or
 * the options of a script's widget.
 */
@Composable
private fun WidgetToolbar(
    w: PlacedWidget,
    label: String,
    index: Int,
    count: Int,
    minHeight: Int,
    onMove: (Int) -> Unit,
    onHeight: (Int) -> Unit,
    onWidth: (Int) -> Unit,
    onSquare: () -> Unit,
    edit: ToolbarEdit?,
    onRemove: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${WidgetLayout.spanLabel(w.span)} wide · ${w.heightDp}dp tall",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = onRemove) { Icon(painterResource(R.drawable.ic_delete), "Remove", tint = MaterialTheme.colorScheme.error) }
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = onDone) { Icon(painterResource(R.drawable.ic_check), "Done") }
            }
            // Side by side if they fit. On a narrow screen or with large text, one below the other.
            FlowRow(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                val minSpan = WidgetLayout.minSpan(w.source != null)
                Stepper("Width", WidgetLayout.spanLabel(w.span), w.span > minSpan, w.span < WidgetLayout.COLUMNS, onWidth)
                Stepper("Height", "${w.heightDp}", w.heightDp > minHeight, w.heightDp < WidgetLayout.MAX_DP, onHeight)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { onMove(-1) }, enabled = index > 0) { Icon(painterResource(R.drawable.ic_arrow_back), "Move earlier") }
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { onMove(1) }, enabled = index < count - 1) { Icon(painterResource(R.drawable.ic_arrow_forward), "Move later") }
                Spacer(Modifier.weight(1f))
                if (edit != null) {
                    FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = edit.onClick) { Text(edit.label) }
                    Spacer(Modifier.width(8.dp))
                }
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onSquare) { Text("Square") }
            }
        }
    }
}

/** Shows "label − value +". Each button steps once on a tap and keeps stepping while held. */
@Composable
private fun Stepper(label: String, value: String, canLess: Boolean, canMore: Boolean, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
        RepeatingButton(R.drawable.ic_remove, "Decrease ${label.lowercase()}", canLess) { onStep(-1) }
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 36.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
        RepeatingButton(R.drawable.ic_add, "Increase ${label.lowercase()}", canMore) { onStep(1) }
    }
}

@Composable
private fun RepeatingButton(icon: Int, description: String, enabled: Boolean, onStep: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val step by rememberUpdatedState(onStep)
    // When held past the long-press delay, the button repeats. The release that ends such a hold
    // does not step again.
    // [heldSince] is reset each time a press ends. So a hold that slid off the button or reached the
    // limit doesn't block the next activation. (A TalkBack activation has no press at all.)
    var heldSince by remember { mutableLongStateOf(0L) }
    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) {
            heldSince = 0L
            return@LaunchedEffect
        }
        heldSince = SystemClock.uptimeMillis()
        delay(400.milliseconds)
        while (true) {
            step()
            delay(70.milliseconds)
        }
    }
    IconButton(
        shapes = IconButtonDefaults.shapes(),
        onClick = {
            if (heldSince == 0L || SystemClock.uptimeMillis() - heldSince < 400) step()
            heldSince = 0L
        },
        enabled = enabled,
        interactionSource = interaction,
    ) {
        Icon(painterResource(icon), description)
    }
}

@Composable
private fun PendingRow(
    w: PlacedWidget,
    index: Int,
    count: Int,
    providersVersion: Int,
    onReAdd: (ProviderEntry, PlacedWidget) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val widgets = remember { WidgetHost.get(context) }
    val entry by produceState<ProviderEntry?>(null, w.provider, w.profileSerial, providersVersion) {
        value = withContext(Dispatchers.IO) { widgets.findProvider(w.provider, w.profileSerial) }
    }
    val found = entry
    // Loading a label reads the other app's resources, so it is done once per provider, not on every redraw.
    val label = remember(found, w.provider) { found?.info?.loadLabel(context.packageManager) ?: w.provider.substringAfterLast('.') }
    SegmentedListItem(
        colors = segColors(),
        shapes = segShapes(index, count),
        leadingContent = { Icon(painterResource(R.drawable.ic_widgets), null, tint = MaterialTheme.colorScheme.primary) },
        overlineContent = { Text(w.packageName, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(if (found == null) "app not installed" else "${w.location} · ${w.heightDp}dp") },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { found?.let { onReAdd(it, w) } }, enabled = found != null) { Text("Add again") }
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = onDismiss) { Icon(painterResource(R.drawable.ic_delete), "Forget") }
            }
        },
    ) { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) }
}
