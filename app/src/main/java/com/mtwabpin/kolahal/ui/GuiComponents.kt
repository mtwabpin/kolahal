package com.mtwabpin.kolahal.ui

import android.text.format.DateUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.terminal.CommandParsing
import com.mtwabpin.kolahal.terminal.TerminalViewModel

/** Single-select connected button group (Material 3 Expressive). [label] gives the words shown for a choice; the test tag keeps the choice itself. */
@Composable
fun ConnectedChoices(choices: List<String>, selected: String, tagPrefix: String = "choice ", label: (String) -> String = { it }, onPick: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.fillMaxWidth(),
    ) {
        choices.forEachIndexed { i, choice ->
            ToggleButton(
                checked = choice == selected,
                onCheckedChange = { onPick(choice) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f).testTag(tagPrefix + choice),
            ) {
                Text(label(choice), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

data class Action(val icon: Int, val title: String, val subtitle: String, val chevron: Boolean = false, val onClick: () -> Unit)

/** A segmented group of clickable rows. */
@Composable
fun ActionGroup(actions: List<Action>) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        actions.forEachIndexed { i, a ->
            SegmentedListItem(
                colors = segColors(),
                onClick = a.onClick,
                shapes = segShapes(i, actions.size),
                leadingContent = { Icon(painterResource(a.icon), null, tint = MaterialTheme.colorScheme.primary) },
                supportingContent = { Text(a.subtitle) },
                trailingContent = if (a.chevron) {
                    { Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else null,
            ) { Text(a.title) }
        }
    }
}

@Composable
fun GroupHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClick) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
}

/**
 * A Material screen page with a large top bar and a list. The top bar has [title], [subtitle],
 * Back (calls [onBack]) and [actions]. It collapses as the list scrolls.
 * - The list keeps the page's side margins, adds [top] and [bottom] padding beyond the bars, and
 *   spaces its rows with [verticalArrangement].
 * - Without [imePadding], the page stays full screen height while a keyboard shows.
 * - [fade] (1 to 0) fades the page background, top bar and navigation bar scrim. Settings uses it
 *   while home shows through.
 * - [pinned] stays between the top bar and the list. The list scrolls under it.
 * - [state] and [listModifier] go to the list. A page uses them to follow its rows, for example
 *   while the user drags one.
 */
// [listModifier] goes to the list, not the page, so it isn't named `modifier`.
@Suppress("ModifierParameter")
@Composable
internal fun GuiListPage(
    title: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)?,
    onBack: () -> Unit,
    snackbar: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    imePadding: Boolean = true,
    fade: (() -> Float)? = null,
    top: Dp = 8.dp,
    bottom: Dp = 32.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    pinned: (@Composable () -> Unit)? = null,
    state: LazyListState? = null,
    listModifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val surface = MaterialTheme.colorScheme.surface
    Scaffold(
        modifier = (if (imePadding) Modifier.imePadding() else Modifier)
            .then(if (fade != null) Modifier.drawBehind { drawRect(surface.copy(alpha = fade())) } else Modifier)
            .nestedScroll(scroll.nestedScrollConnection),
        // When fading, the page draws its own background. A transparent container has no content colour, so set it here.
        containerColor = if (fade != null) Color.Transparent else surface,
        contentColor = if (fade != null) MaterialTheme.colorScheme.onSurface else contentColorFor(surface),
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = title,
                subtitle = subtitle,
                navigationIcon = { BackButton(onBack) },
                actions = actions,
                scrollBehavior = scroll,
                modifier = if (fade != null) Modifier.graphicsLayer { alpha = fade() } else Modifier,
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
    ) { padding ->
        Column(Modifier.fillMaxSize()) {
            // With a pinned item, the bar padding goes above that item instead of inside the list.
            if (pinned != null) Box(Modifier.padding(top = padding.calculateTopPadding())) { pinned() }
            LazyColumn(
                state = state ?: rememberLazyListState(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = (if (pinned != null) 0.dp else padding.calculateTopPadding()) + top,
                    bottom = padding.calculateBottomPadding() + bottom,
                ),
                verticalArrangement = verticalArrangement,
                modifier = Modifier.fillMaxWidth().weight(1f).navigationBarScrim(fade ?: { 1f }).then(listModifier),
                content = content,
            )
        }
    }
}

/**
 * Shows [TerminalViewModel.notice] as a snackbar. Each new notice shows once.
 * A notice with Undo stays 10 seconds, longer than others, because it is about changes to scripts.
 */
@Composable
internal fun rememberNoticeSnackbar(vm: TerminalViewModel): SnackbarHostState {
    val notice by vm.notice.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(notice) {
        val n = notice ?: return@LaunchedEffect
        val undo = n.undo
        if (undo == null) snackbar.showSnackbar(n.text)
        else if (snackbar.showSnackbar(n.text, actionLabel = "Undo", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) undo()
    }
    return snackbar
}

/** A modal sheet state that is either fully open or closed. It has no half-open stop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberExpandedSheetState(): SheetState =
    rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

/**
 * Asks for confirmation before an action that cannot be undone.
 * - [confirm] uses the error colour and runs [onConfirm].
 * - Cancel, Back and a tap outside run [onDismiss].
 * - [icon], if given, shows above the [title].
 */
@Composable
internal fun ConfirmDialog(title: String, text: String, confirm: String, icon: Int? = null, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = if (icon != null) { { Icon(painterResource(icon), null) } } else null,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The text a page shows when it has nothing to list. It is centred, with [top] and [bottom] space from the rows around it. */
@Composable
internal fun EmptyNote(text: String, modifier: Modifier = Modifier, top: Dp = 32.dp, bottom: Dp = top) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(top = top, bottom = bottom),
    )
}

/** Small text under a page's rows. It explains how they work and names the terminal commands that do the same. */
@Composable
internal fun PageHint(text: String, modifier: Modifier = Modifier, top: Dp = 16.dp) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = modifier.padding(start = 16.dp, top = top, end = 16.dp),
    )
}

/** A button's icon and label, with Material's icon size and spacing. Use it inside a button. */
@Composable
internal fun IconLabel(icon: Int, text: String) {
    Icon(painterResource(icon), null, Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(text)
}

/** "5 minutes ago"; [short] gives "5 min. ago" for one-line rows. */
internal fun relative(time: Long, short: Boolean = false): String =
    if (System.currentTimeMillis() - time < DateUtils.MINUTE_IN_MILLIS) "just now" else relativeSpan(time, short)

private fun relativeSpan(time: Long, short: Boolean = false): String =
    DateUtils.getRelativeTimeSpanString(
        time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
        // The same flags as the three-argument form, plus the short form when [short] is true.
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH or
            (if (short) DateUtils.FORMAT_ABBREV_RELATIVE else 0),
    ).toString()

/** A full-width row (≥ 56dp). A tap anywhere on it toggles the switch. [color] is the background it sits on. */
@Composable
internal fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onChange(!checked) },
        shape = MaterialTheme.shapes.large,
        color = color,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/**
 * Material's Expressive text field colours, but with the container colour of the tonal buttons
 * beside it. The default container (`surfaceContainer`) is barely visible on a sheet in these themes.
 */
@Composable
internal fun fieldColors() = TextFieldDefaults.tonalColors().copy(
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
)

/**
 * Picks how often something runs. It is a row like [SwitchRow]. Its button shows the interval and
 * opens a menu of presets. An interval set from the terminal (not a preset) shows as it is.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun IntervalSelector(label: String, selected: Int, onPick: (Int) -> Unit, choices: List<Pair<Int, String>>) {
    var open by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec())
    Surface(
        onClick = { open = !open },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Box {
                FilledTonalToggleButton(checked = open, onCheckedChange = { open = it }) {
                    Text(choices.firstOrNull { it.first == selected }?.second ?: CommandParsing.formatInterval(selected))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Icon(painterResource(R.drawable.ic_expand_more), null, Modifier.size(ButtonDefaults.IconSize).rotate(turn))
                }
                DropdownMenuPopup(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                        // Use the theme's accent, like switches and tabs. Material's default is tertiaryContainer, which is the work profile's colour here.
                        val colors = MenuDefaults.selectableItemColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                        )
                        choices.forEachIndexed { i, (minutes, name) ->
                            SelectableDropdownMenuItem(
                                selected = minutes == selected,
                                onClick = {
                                    open = false
                                    if (minutes != selected) onPick(minutes)
                                },
                                text = { Text(name) },
                                shapes = MenuDefaults.itemShape(i, choices.size),
                                colors = colors,
                            )
                        }
                    }
                }
            }
        }
    }
}
