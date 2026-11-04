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
import androidx.compose.material3.AlertDialog
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
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.ProviderEntry
import com.mtwabpin.kolahal.widgets.WidgetHost
import com.mtwabpin.kolahal.widgets.WidgetLayout
import com.mtwabpin.kolahal.widgets.WidgetLocation
import com.mtwabpin.kolahal.widgets.WidgetOrder
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
 * Widgets page (Material 3 Expressive): every placed widget, live, laid out as on the widgets page
 * ([WidgetFlow]). Long-press a widget, or tap Edit and then a widget, for its toolbar at the bottom:
 * width in twelfths of the page, height in 8dp steps (held, they repeat), square, move, remove.
 * Entries restored from a backup show an "Add again" row.
 */
@Composable
fun WidgetsScreen(vm: TerminalViewModel, onReAdd: (ProviderEntry, PlacedWidget) -> Unit) {
    val context = LocalContext.current
    val widgets = remember { WidgetHost.get(context) }
    val all by widgets.widgets.collectAsStateWithLifecycle()
    val providersVersion by widgets.providersVersion.collectAsStateWithLifecycle()
    val profiles by remember { AppRepository.get(context).profiles }.collectAsStateWithLifecycle()
    val version by vm.config.version.collectAsStateWithLifecycle()
    // widgets.page off: no page beside the terminal shows them, so the screen says so.
    val pageOff = remember(version) { vm.config[Keys.WIDGETS_PAGE] == "off" }
    var editing by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf<PlacedWidget?>(null) }
    val snackbar = remember { SnackbarHostState() }
    // Each waiting message once, oldest first, whichever widget screen is open.
    LaunchedEffect(Unit) { widgets.messages.collect { if (it.isNotEmpty()) widgets.takeMessage()?.let { m -> snackbar.showSnackbar(m) } } }
    BackHandler { if (editing || selected != null) { editing = false; selected = null } else vm.closeScreen() }

    val panel = remember(all) { WidgetOrder.inLocation(all, WidgetLocation.PANEL) }
    val pending = remember(all) { WidgetOrder.sorted(all.filter { it.pending }) }
    // Each label is a binder call and a load: only again when widgets come or go, not on every resize step.
    val placed = remember(all) { all.map { Triple(it.key, it.id, it.provider) } }
    val labels = remember(placed, providersVersion) {
        placed.associate { (key, id, provider) -> key to (id?.let { widgets.cachedInfo(it) }?.loadLabel(context.packageManager) ?: provider.substringAfterLast('.')) }
    }
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
                    WidgetToolbar(
                        w = chosen,
                        label = labels[chosen.key] ?: chosen.provider,
                        index = panel.indexOf(chosen),
                        count = panel.size,
                        minHeight = remember(chosen.id, providersVersion) {
                            chosen.id?.let { widgets.cachedInfo(it) }?.let { widgets.minHeightDp(it) } ?: WidgetLayout.MIN_DP
                        },
                        onMove = { widgets.move(chosen.key, it) },
                        onHeight = { widgets.resize(chosen.key, it * WidgetLayout.STEP_DP) },
                        onWidth = { widgets.resizeWidth(chosen.key, it) },
                        onSquare = { widgets.square(chosen.key) },
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
                    // The widgets as wide as on the widgets page (its margins and the side insets it
                    // clears); text keeps the usual margin.
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
                    Text(
                        if (pageOff) "No widgets yet." else "No widgets yet. They appear on the widgets page beside the terminal: swipe sideways on the home screen to reach it.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = inset.fillMaxWidth().padding(vertical = 32.dp),
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
                                label = labels[w.key] ?: w.provider,
                                selected = selected == w.key,
                                editing = editing,
                                pausedKind = profiles.pausedKind(w.profileSerial),
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
                    Text(
                        "Long-press a widget, or tap ✎ and then a widget, to resize and move it. Terminal: widgets ls · widget add",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = inset.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                    )
                }
            }
        }

        confirmRemove?.let { w ->
            AlertDialog(
                onDismissRequest = { confirmRemove = null },
                icon = { Icon(painterResource(R.drawable.ic_delete), null) },
                title = { Text("Remove ${labels[w.key] ?: "widget"}?") },
                text = { Text("The widget is deleted; add it again from the picker any time.") },
                confirmButton = {
                    TextButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = { confirmRemove = null; selected = null; widgets.remove(w.key) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Remove") }
                },
                dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmRemove = null }) { Text("Cancel") } },
            )
        }
    }
}

/** One widget as it shows on the widgets page; while [editing], a tap picks it instead of reaching it. */
@Composable
private fun WidgetCard(
    w: PlacedWidget,
    label: String,
    selected: Boolean,
    editing: Boolean,
    pausedKind: ProfileKind?,
    onSelect: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
    ) {
        Box {
            HostedWidget(w, w.heightDp.dp, pausedKind, onSelect) { why ->
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

/** The chosen widget's controls, docked at the bottom: width, height, square, move, remove. */
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
            // Side by side where they fit, one under the other on a narrow screen or with large text.
            FlowRow(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Stepper("Width", WidgetLayout.spanLabel(w.span), w.span > WidgetLayout.MIN_SPAN, w.span < WidgetLayout.COLUMNS, onWidth)
                Stepper("Height", "${w.heightDp}", w.heightDp > minHeight, w.heightDp < WidgetLayout.MAX_DP, onHeight)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { onMove(-1) }, enabled = index > 0) { Icon(painterResource(R.drawable.ic_arrow_back), "Move earlier") }
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { onMove(1) }, enabled = index < count - 1) { Icon(painterResource(R.drawable.ic_arrow_forward), "Move later") }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onSquare) { Text("Square") }
            }
        }
    }
}

/** label − value +: each button steps once on a tap and keeps stepping while held. */
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
    // Held past the long-press delay it repeats; the tap that ends such a hold doesn't step again.
    // [heldSince] is reset whenever a press ends, so a hold that slid off or hit the limit doesn't
    // swallow the next activation (TalkBack's has no press at all).
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
    val label = found?.info?.loadLabel(context.packageManager) ?: w.provider.substringAfterLast('.')
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
