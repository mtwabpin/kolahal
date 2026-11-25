/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.data.StyledText.ColorRole
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.BackButton
import com.mtwabpin.kolahal.ui.GroupHeader
import com.mtwabpin.kolahal.ui.home.colorOf
import com.mtwabpin.kolahal.ui.scripts.OptionDialog
import com.mtwabpin.kolahal.ui.settings.UndoMessage
import com.mtwabpin.kolahal.ui.settings.undoMessage
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import com.mtwabpin.kolahal.ui.theme.navigationBarScrim
import com.mtwabpin.kolahal.ui.theme.segColors
import com.mtwabpin.kolahal.ui.theme.segShapes
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.WidgetColors
import com.mtwabpin.kolahal.widgets.WidgetHost
import com.mtwabpin.kolahal.widgets.WidgetLayout
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetParts
import com.mtwabpin.kolahal.widgets.WidgetRef
import com.mtwabpin.kolahal.widgets.WidgetShape
import com.mtwabpin.kolahal.widgets.WidgetSource
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetStates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/** The words under This card on a script widget's settings page: whose options these are. Pure, for tests. */
internal object WidgetOptionWords {
    /** The first widget shows the script's own options. */
    const val SCRIPT =
        "These are the script's own options. Every run reads them, and so does each of its widgets that has no value of its own."

    /** A further widget follows the script until you set a value for it. */
    const val WIDGET =
        "This widget follows the script's options. A value you set here is for this widget only."
    const val NONE = "This script has no options. A script declares one with an @option line."
    const val GONE = "The script is gone, so there is nothing to set."

    /** What the page says for the widget [ref]. [found]: its script is there. [options]: how many options the script declares. */
    fun note(ref: WidgetRef, found: Boolean, options: Int): String = when {
        !found -> GONE
        options == 0 -> NONE
        ref.first -> SCRIPT
        else -> WIDGET
    }
}

/** The words of the page that tests read. */
internal object WidgetSettingsWords {
    const val PREVIEW = "As on the widgets page"
    const val RESET = "Reset to the card's own"

    /** What the snackbar says after Reset, with Undo: as Settings says it of a setting. */
    fun resetDone(title: String): String = "Reset “$title”"

    /** The line under Reset: what it clears, and, for a script with options, what it leaves. */
    fun resetLine(options: Boolean): String =
        if (options) "Color, shape and what shows. The script's options keep their values."
        else "Color, shape and what shows."

    const val BATTERY = "Low and charging keep their own color: bad and good."

    /** The line under the card's name: what it is, its size and its look. "Weather script · 2×2 · Shape". */
    fun subtitle(what: String, size: String, look: WidgetLook): String = "$what · $size · ${look.label}"
}

/** A card taller than this share of the page's room scrolls with the page; a lower one stays at the top. */
private const val PINNED_SHARE = 0.4f

/**
 * The settings page of one of Kolahal's own cards ([TerminalViewModel.widgetSettingsKey]), opened
 * from its Settings button in Arrange widgets, or as the picker places a further widget of a
 * script with options. Back returns to Arrange widgets, with the card still chosen.
 * - At the top, the card itself, live and at its real size, as on the widgets page. It changes as
 *   choices are made. It stays there while the rest scrolls; a card too tall for that (a 4×4)
 *   scrolls away with the page.
 * - This card: a script's options (a value saved runs the card again at once), or the Shortcuts
 *   card's commands.
 * - Color: the card's own first (a ring), then each of the theme's roles whose color is not the
 *   twin of one before it ([WidgetColors.offered]).
 * - Shape: in the Shape look, for a card with a choice of shape ([hasShapeChoice]), its own first,
 *   then the shapes it may take ([shapesFor]).
 * - What shows: a switch for each part the card has to hide ([WidgetParts.offered]).
 * - Reset to the card's own: color, shape and what shows. A script's options keep their values.
 *   The snackbar offers Undo, as Settings does after a reset.
 * An app's widget has no page: its button opens the app's own setup.
 */
@Composable
fun WidgetSettingsScreen(vm: TerminalViewModel) {
    val context = LocalContext.current
    val widgets = remember { WidgetHost.get(context) }
    val key = remember { vm.widgetSettingsKey.value }
    val all by widgets.widgets.collectAsStateWithLifecycle()
    val w = all.firstOrNull { it.key == key }
    BackHandler { vm.closeScreen() }
    // The card is gone, removed from another screen or by a command: there is nothing to set.
    if (w == null || w.source == null) {
        LaunchedEffect(Unit) { vm.closeScreen() }
        return
    }
    val scripts by vm.widgetScripts.collectAsStateWithLifecycle()
    val ref = WidgetSource.scriptRef(w.source)
    val builtin = WidgetSource.builtinId(w.source)
    val kind = builtin?.let(WidgetSource::builtinKind) ?: ref?.let { scripts?.get(it.script)?.widget?.kind }
    // What a script's card shows, read again each time its run stores something.
    val spec by produceState<WidgetSpec?>(ref?.let { WidgetStates.lastRead(it.key)?.spec }, ref) {
        if (ref == null) return@produceState
        WidgetStates.versionOf(ref.key).collectLatest {
            value = withContext(Dispatchers.IO) { WidgetStates.read(context, ref.key).spec }
        }
    }
    val look = w.look.drawnFor(kind, builtin)
    val title = sourceTitle(w.provider, scripts.orEmpty()) ?: w.provider
    val what = when {
        ref != null -> (scripts?.get(ref.script)?.title ?: ref.script) + " script"
        else -> "Built-in widget"
    }
    val size = widgets.sizeOf(w)?.label ?: WidgetLayout.sizeLine(w.span, w.heightDp)
    val hasOptions = ref != null && scripts?.get(ref.script)?.hasOptions == true
    var shortcutsEditor by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    // "Reset …" with Undo, after Reset to the card's own.
    val undo = undoMessage(rememberCoroutineScope(), snackbar)

    GuiTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                MediumFlexibleTopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    subtitle = { Text(WidgetSettingsWords.subtitle(what, size, look)) },
                    navigationIcon = { BackButton(vm::closeScreen) },
                )
            },
        ) { padding ->
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    .navigationBarScrim()
            ) {
                val room = maxHeight
                val pageDp = maxWidth.value.toInt() - 2 * WidgetLayout.GAP_DP
                // The card and the space around it and its line.
                val pinned = (w.heightDp + 56).dp <= room * PINNED_SHARE
                val preview = @Composable { CardPreview(w, pageDp, vm) }
                Column(Modifier.fillMaxSize()) {
                    if (pinned) preview()
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = padding.calculateBottomPadding() + 16.dp)
                    ) {
                        if (!pinned) preview()
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            ThisCard(vm, w, ref) { shortcutsEditor = true }
                            ColorGroup(widgets, w, spec, battery = builtin == WidgetSource.BATTERY)
                            if (look == WidgetLook.SHAPE) {
                                shapeChoicesFor(w, kind, spec, pageDp)?.let { ShapeGroup(widgets, w, it) }
                            }
                            PartsGroup(widgets, w, WidgetParts.offered(kind, spec, look))
                            ResetOwn(widgets, w, hasOptions, title, undo)
                        }
                    }
                }
            }
        }
        if (shortcutsEditor) ShortcutsEditor(vm) { shortcutsEditor = false }
    }
}

/**
 * The card [w] at its real size on a page [pageDp] wide, live, on the page's ground, with a line
 * under it. It takes no taps; a screen reader hears it as the card says itself.
 */
@Composable
private fun CardPreview(w: PlacedWidget, pageDp: Int, vm: TerminalViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = WidgetLayout.GAP_DP.dp, vertical = 8.dp)
            .testTag("widget-preview"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The stage, as the picker's previews have it. The line goes under it, on the screen's own
        // ground: on a light theme the stage is a mid grey, where it would not read.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(WidgetLayout.widthDp(pageDp, w.span).dp)) {
                // A shape or a color chosen below settles here as on the widgets page.
                CompositionLocalProvider(LocalArranging provides true) {
                    SourceWidget(w, w.heightDp.dp, actions = null, feed = vm.widgetFeed)
                }
            }
        }
        Text(
            WidgetSettingsWords.PREVIEW,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * This card: what the card itself holds.
 * - A script's widget: what its options are ([WidgetOptionWords]) and a row for each, as the
 *   Scripts screen has them. A tap opens the option's dialog; a value saved runs the widget again.
 *   The first widget edits the script's own options; a further one has values of its own.
 * - The Shortcuts card: its commands, in their editor ([onShortcuts]).
 * Nothing for the other built-in cards.
 */
@Composable
private fun ThisCard(
    vm: TerminalViewModel,
    w: PlacedWidget,
    ref: WidgetRef?,
    onShortcuts: () -> Unit
) {
    if (ref != null) ScriptOptions(vm, ref)
    else if (w.source == WidgetSource.builtin(WidgetSource.SHORTCUTS)) {
        GroupHeader("This card")
        SegmentedListItem(
            onClick = onShortcuts,
            colors = segColors(),
            shapes = segShapes(0, 1),
            supportingContent = { Text("The commands its buttons run") },
            modifier = Modifier.testTag("edit-shortcuts"),
        ) { Text("Edit shortcuts") }
    }
}

/** The options of the script widget [ref], as [ThisCard] says. */
@Composable
private fun ScriptOptions(vm: TerminalViewModel, ref: WidgetRef) {
    val scripts by vm.scriptList.collectAsStateWithLifecycle()
    val script = scripts.firstOrNull { it.name == ref.script }
    val options = remember(script?.code) { script?.let { ScriptMeta.options(it.code) }.orEmpty() }
    val version by vm.scriptActions.optionsVersion.collectAsStateWithLifecycle()
    val values by produceState(
        emptyMap<String, String>() to emptySet<String>(),
        script?.code,
        ref,
        version
    ) {
        value = script?.let { s ->
            withContext(Dispatchers.IO) { vm.scriptActions.optionValues(s, ref.id) }
        } ?: (emptyMap<String, String>() to emptySet())
    }
    var editing by remember(ref) { mutableStateOf<ScriptMeta.Option?>(null) }

    GroupHeader("This card")
    Text(
        WidgetOptionWords.note(ref, script != null, options.size),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .testTag("widget-options"),
    )
    options.forEachIndexed { i, o ->
        val own = o.name in values.second
        Box(Modifier.padding(bottom = ListItemDefaults.SegmentedGap)) {
            SegmentedListItem(
                onClick = { editing = o },
                colors = segColors(),
                shapes = segShapes(i, options.size),
                supportingContent = if (o.desc.isNotBlank()) {
                    { Text(o.desc) }
                } else null,
                trailingContent = {
                    // A value can be long (an address, a list): it gets one line, and the name keeps the rest.
                    Text(
                        (values.first[o.name] ?: o.default).ifEmpty { "—" },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (own) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp),
                    )
                },
                modifier = Modifier.testTag("option ${o.name}"),
            ) { Text(o.name, fontFamily = FontFamily.Monospace) }
        }
    }

    editing?.let { o ->
        // The dialog the script's own sheet uses. For a further widget, Reset takes its own value away.
        OptionDialog(
            ref.titled(ref.script),
            o,
            values.first[o.name] ?: o.default,
            changed = o.name in values.second,
            onDismiss = { editing = null }) { value ->
            editing = null
            vm.scriptActions.setWidgetOption(ref, o.name, value)
        }
    }
}

/** The ground of the grids of Color and Shape: a group as the Settings screen's rows are. */
@Composable
private fun gridGround(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

/**
 * A grid of [count] cells, [most] to a row, on a group's ground, as one choice for a screen
 * reader ([selectableGroup]). [cell] draws cell [i]. With a larger font, fewer to a row
 * ([gridColumns]), so a cell's words keep their room.
 */
@Composable
private fun ChoiceGrid(count: Int, most: Int, tag: String, cell: @Composable (i: Int, modifier: Modifier) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(gridGround())
            .padding(8.dp)
    ) {
        val columns = gridColumns(maxWidth.value.toInt(), fontScale, most)
        Column(
            Modifier
                .fillMaxWidth()
                .selectableGroup()
                .testTag(tag),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            (0 until count).chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { i -> cell(i, Modifier.weight(1f)) }
                    // The last row keeps the others' cell widths.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * One cell of a [ChoiceGrid]: [mark] over [name], chosen when [selected], at least 48 dp each way.
 * A screen reader hears [description] as one radio button.
 */
@Composable
private fun GridCell(
    name: String,
    description: String,
    selected: Boolean,
    tag: String,
    modifier: Modifier,
    onClick: () -> Unit,
    mark: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cellMarks(scheme, selected).fill)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(vertical = 8.dp, horizontal = 2.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        mark()
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = cellMarks(scheme, selected).ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * Color: "Its own" first, a ring in the card's own color (its script's, else the accent), then a
 * dot for each role the theme draws apart from those before it ([WidgetColors.offered]). A tap gives
 * the card that color at once; "Its own" gives it its own again. The battery keeps its warning
 * colors ([battery]).
 */
@Composable
private fun ColorGroup(widgets: WidgetHost, w: PlacedWidget, spec: WidgetSpec?, battery: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val own = spec?.base?.color ?: ColorRole.ACCENT
    val roles = WidgetColors.offered({ colorOf(it) }, w.color)
    GroupHeader("Color")
    ChoiceGrid(roles.size + 1, 5, "colors") { i, modifier ->
        val role = if (i == 0) null else roles[i - 1]
        val selected = w.color == role
        val ground = if (selected) scheme.primaryContainer else gridGround()
        GridCell(
            name = role?.let(WidgetColors::label) ?: "Its own",
            description = role?.let(WidgetColors::label) ?: "Its own: ${WidgetColors.label(own)}",
            selected = selected,
            tag = "color " + (role?.role ?: "own"),
            modifier = modifier,
            onClick = { widgets.setColor(w.key, role) },
        ) {
            ColorDot(
                colorOf(role ?: own),
                ground,
                28.dp,
                own = role == null,
                ring = if (selected) cellMarks(scheme, true).ink else null,
            )
        }
    }
    if (battery) Text(
        WidgetSettingsWords.BATTERY,
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
    )
}

/**
 * Shape: "Its own" first, the card's own shape, then the others it may take ([ShapeChoices]). A tap
 * gives the card that shape at once, settling as on the widgets page.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeGroup(widgets: WidgetHost, w: PlacedWidget, choices: ShapeChoices) {
    val scheme = MaterialTheme.colorScheme
    val shapes = choices.shapes
    GroupHeader("Shape")
    ChoiceGrid(shapes.size, 6, "shapes") { i, modifier ->
        val shape: WidgetShape = shapes[i]
        val selected = if (i == 0) choices.shown == choices.own else choices.chosen == shape
        GridCell(
            name = if (i == 0) "Its own" else shape.short,
            description = if (i == 0) "Its own: ${shape.label}" else shape.label,
            selected = selected,
            tag = "shape " + (if (i == 0) "own" else shape.id),
            modifier = modifier,
            onClick = { widgets.setShape(w.key, choices.pick(shape)) },
        ) {
            val drawn = polygonOf(shape).toShape()
            Box(
                Modifier
                    .size(30.dp)
                    .background(cellMarks(scheme, selected).ink, drawn)
            )
        }
    }
}

/** What shows: a switch for each part of [offered], on while the card shows it. */
@Composable
private fun PartsGroup(widgets: WidgetHost, w: PlacedWidget, offered: List<String>) {
    if (offered.isEmpty()) return
    GroupHeader("What shows")
    offered.forEachIndexed { i, part ->
        val shown = part !in w.hidden
        Box(Modifier.padding(bottom = ListItemDefaults.SegmentedGap)) {
            SegmentedListItem(
                checked = shown,
                onCheckedChange = { widgets.setShown(w.key, part, it) },
                colors = segColors(),
                shapes = segShapes(i, offered.size),
                supportingContent = { Text(WidgetParts.detail(part)) },
                trailingContent = { Switch(checked = shown, onCheckedChange = null) },
                modifier = Modifier.testTag("part $part"),
            ) { Text(WidgetParts.label(part)) }
        }
    }
}

/**
 * Reset to the card's own: its color, its shape and what shows. Only when one of them is set. Then
 * [undo] shows "Reset “[title]”" with Undo, which puts the three back as they were.
 */
@Composable
private fun ResetOwn(widgets: WidgetHost, w: PlacedWidget, hasOptions: Boolean, title: String, undo: UndoMessage) {
    Spacer(Modifier.height(24.dp))
    OutlinedButton(
        shapes = ButtonDefaults.shapes(),
        onClick = {
            val before = w
            widgets.resetOwn(w.key)
            undo(WidgetSettingsWords.resetDone(title)) {
                widgets.setColor(before.key, before.color)
                widgets.setShape(before.key, before.shape)
                before.hidden.forEach { widgets.setShown(before.key, it, false) }
            }
        },
        enabled = w.color != null || w.shape != null || w.hidden.isNotEmpty(),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("reset-own"),
    ) { Text(WidgetSettingsWords.RESET) }
    Text(
        WidgetSettingsWords.resetLine(hasOptions),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}
