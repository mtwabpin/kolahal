/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import android.text.format.DateUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.commands.CommandParsing
import com.mtwabpin.kolahal.data.Quietly
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.theme.navigationBarScrim
import com.mtwabpin.kolahal.ui.theme.segColors
import com.mtwabpin.kolahal.ui.theme.segShapes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * [produceState] for a read that runs on [Dispatchers.IO] and may fail, such as a database query.
 * - [read] runs again whenever one of [keys] changes.
 * - A failure is logged through [Quietly] as "[what] failed: …", at most once a day, and the value
 *   stays as it was: [initial], or the last read that worked.
 * - The value is set on the main thread, where composition reads it. Where the effect resumes off it (a
 *   test's effects run unconfined, so the code goes on on the IO thread), a write there while a frame is
 *   composed could be missed, and the value never shown.
 */
@Composable
fun <T> produceIo(initial: T, vararg keys: Any?, what: String, read: suspend () -> T): State<T> {
    val context = LocalContext.current.applicationContext
    return produceState(initial, *keys) {
        val result = withContext(Dispatchers.IO) {
            try {
                Result.success(read())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Quietly.report(context, what, e)
                Result.failure(e)
            }
        }
        withContext(Dispatchers.Main.immediate) { result.onSuccess { value = it } }
    }
}

/** Single-select connected button group (Material 3 Expressive). [label] gives the words shown for a choice; the test tag keeps the choice itself. */
@Composable
fun ConnectedChoices(
    choices: List<String>,
    selected: String,
    tagPrefix: String = "choice ",
    label: (String) -> String = { it },
    onPick: (String) -> Unit
) {
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
                modifier = Modifier
                    .weight(1f)
                    .testTag(tagPrefix + choice),
            ) {
                Text(label(choice), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

data class Action(
    val icon: Int,
    val title: String,
    val subtitle: String,
    val chevron: Boolean = false,
    val onClick: () -> Unit
)

/** A segmented group of clickable rows. [leading], when given, takes the place of each row's icon: a picture, by the row's index. */
@Composable
fun ActionGroup(actions: List<Action>, leading: (@Composable (Int) -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        actions.forEachIndexed { i, a ->
            SegmentedListItem(
                colors = segColors(),
                onClick = a.onClick,
                shapes = segShapes(i, actions.size),
                leadingContent = {
                    if (leading != null) leading(i)
                    else Icon(
                        painterResource(a.icon),
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                supportingContent = { Text(a.subtitle) },
                trailingContent = if (a.chevron) {
                    {
                        Icon(
                            painterResource(R.drawable.ic_chevron_right),
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

/** The title over a group of rows on a sheet. A page uses [GroupHeader]. */
@Composable
internal fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun BackButton(onClick: () -> Unit) {
    LabeledIconButton(R.drawable.ic_arrow_back, "Back", onClick = onClick)
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
        // When fading, the page draws its own background. A transparent container has no content color, so set it here.
        containerColor = if (fade != null) Color.Transparent else surface,
        contentColor = if (fade != null) MaterialTheme.colorScheme.onSurface else contentColorFor(
            surface
        ),
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = title,
                subtitle = subtitle,
                navigationIcon = { BackButton(onBack) },
                actions = actions,
                scrollBehavior = scroll,
                modifier = if (fade != null) Modifier.graphicsLayer {
                    alpha = fade()
                } else Modifier,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarScrim(fade ?: { 1f })
                    .then(listModifier),
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
internal fun rememberNoticeSnackbar(vm: TerminalViewModel): SnackbarHostState =
    rememberNoticeSnackbar(vm.notice)

/** The same, for a page that has only the [notices]. */
@Composable
internal fun rememberNoticeSnackbar(notices: StateFlow<TerminalViewModel.Notice?>): SnackbarHostState {
    val notice by notices.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(notice) {
        val n = notice ?: return@LaunchedEffect
        val undo = n.undo
        if (undo == null) snackbar.showSnackbar(n.text)
        else if (snackbar.showSnackbar(
                n.text,
                actionLabel = "Undo",
                duration = SnackbarDuration.Long
            ) == SnackbarResult.ActionPerformed
        ) undo()
    }
    return snackbar
}

/** A modal sheet state that is either fully open or closed. It has no half-open stop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberExpandedSheetState(): SheetState =
    rememberBottomSheetState(
        SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

/**
 * A bottom sheet over a GUI page. It is fully open or closed ([rememberExpandedSheetState]), in
 * the sheets' color, and its content is a column with 24 dp at each side. It hides the bars that
 * home hides ([DialogBars]).
 * - [scroll]: the column scrolls. For a sheet that can be taller than the screen.
 * - [scrimColor]: what dims the page behind it. Transparent leaves the page as it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GuiSheet(
    onDismiss: () -> Unit,
    scroll: Boolean = false,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    /** How much of the sheet shows, 0 to 1: it clears while home is looked at through it. */
    alpha: () -> Float = { 1f },
    /** The space at the sides: 0 for content that goes from edge to edge. */
    sides: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shown = alpha()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberExpandedSheetState(),
        scrimColor = scrimColor.copy(alpha = scrimColor.alpha * shown),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = shown),
        dragHandle = { Box(Modifier.graphicsLayer { this.alpha = shown }) { BottomSheetDefaults.DragHandle() } },
    ) {
        DialogBars()
        Column(
            (if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .navigationBarsPadding()
                .padding(start = sides, end = sides, bottom = 16.dp),
            content = content,
        )
    }
}

/**
 * Asks for confirmation before an action that cannot be undone.
 * - [confirm] uses the error color and runs [onConfirm].
 * - [dismiss] (Cancel unless given), Back and a tap outside run [onDismiss].
 * - [icon], if given, shows above the [title].
 * - It hides the bars that home hides ([DialogBars]).
 */
@Composable
internal fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    icon: Int? = null,
    dismiss: String = "Cancel",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = if (icon != null) {
            { Icon(painterResource(icon), null) }
        } else null,
        title = { DialogBars(); Text(title) },
        text = { Text(text) },
        confirmButton = { ErrorTextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) {
                Text(
                    dismiss
                )
            }
        },
    )
}

/** A text button in the error color, for an action that removes or replaces something. */
@Composable
internal fun ErrorTextButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        shapes = ButtonDefaults.shapes(),
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        content = content,
    )
}

/** The text a page shows when it has nothing to list. It is centered, with [top] and [bottom] space from the rows around it. */
@Composable
internal fun EmptyNote(
    text: String,
    modifier: Modifier = Modifier,
    top: Dp = 32.dp,
    bottom: Dp = top
) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = top, bottom = bottom),
    )
}

/**
 * The line a page's search shows when it finds nothing. It starts where the rows' text starts, under
 * the search field. A page with nothing to list uses [EmptyNote].
 */
@Composable
internal fun NoMatchNote(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp)
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
    if (System.currentTimeMillis() - time < DateUtils.MINUTE_IN_MILLIS) "just now" else relativeSpan(
        time,
        short
    )

private fun relativeSpan(time: Long, short: Boolean = false): String =
    DateUtils.getRelativeTimeSpanString(
        time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
        // The same flags as the three-argument form, plus the short form when [short] is true.
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH or
                (if (short) DateUtils.FORMAT_ABBREV_RELATIVE else 0),
    ).toString()

/**
 * A full-width row (56 dp or more). A tap anywhere on it toggles the switch, and a screen reader
 * hears it as a checkbox, on or off. [color] is the background it sits on.
 */
@Composable
internal fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onChange,
        shapes = rowShapes(),
        colors = rowColors(color),
        contentPadding = ROW_PADDING,
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) { Text(title, style = MaterialTheme.typography.bodyLarge) }
}

/**
 * A [SwitchRow] with its own [supporting] content under the title, such as a list of lines, in the
 * subtitle's style. The whole row is still the one tap, and the switch keeps its place at the end.
 */
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    supporting: @Composable () -> Unit,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onChange,
        shapes = rowShapes(),
        colors = rowColors(MaterialTheme.colorScheme.surfaceContainerLow),
        contentPadding = ROW_PADDING,
        supportingContent = {
            ProvideTextStyle(
                MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                supporting
            )
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) { Text(title, style = MaterialTheme.typography.bodyLarge) }
}

/**
 * A full-width row like [SwitchRow], with a value at its end instead of a switch. A tap opens what
 * changes the value. [name] is in the code font: it is a name from a script.
 * - [detail] shows under the name, unless it is blank.
 * - [value] is in [valueColor]. With [valueMaxWidth], it stays on one line of at most that width,
 *   and a longer one is cut with "…".
 */
@Composable
internal fun ValueRow(
    name: String,
    detail: String,
    value: String,
    valueColor: Color,
    valueMaxWidth: Dp? = null,
    onClick: () -> Unit
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = rowShapes(),
        colors = rowColors(MaterialTheme.colorScheme.surfaceContainerLow),
        contentPadding = ROW_PADDING,
        supportingContent = if (detail.isNotBlank()) {
            {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else null,
        trailingContent = {
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                color = valueColor,
                // Without a width limit, these are the defaults of Text: as many lines as it needs.
                maxLines = if (valueMaxWidth != null) 1 else Int.MAX_VALUE,
                overflow = if (valueMaxWidth != null) TextOverflow.Ellipsis else TextOverflow.Clip,
                modifier = if (valueMaxWidth != null) Modifier.widthIn(max = valueMaxWidth) else Modifier,
            )
        },
        // A row without a detail is one line: 48 dp, the touch size, not a list's 56 dp.
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

/**
 * The space inside [SwitchRow], [ValueRow] and [IntervalSelector]: none at the sides, so their words
 * line up with the titles and buttons of the sheet or dialog they are on.
 */
private val ROW_PADDING = PaddingValues(vertical = 8.dp)

/** The rows' shape: the large corners, in every state. */
@Composable
private fun rowShapes() = ListItemDefaults.shapes(
    shape = MaterialTheme.shapes.large,
    selectedShape = MaterialTheme.shapes.large,
    pressedShape = MaterialTheme.shapes.large,
)

/**
 * The rows' colors: [color] behind them, on or off. A list's switch row turns another color while it
 * is on; these rows look like the sheet they are on.
 */
@Composable
private fun rowColors(color: Color) = ListItemDefaults.segmentedColors(
    containerColor = color,
    selectedContainerColor = color,
    selectedContentColor = MaterialTheme.colorScheme.onSurface,
    selectedTrailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

/**
 * Material's Expressive text field colors, but with the container color of the tonal buttons
 * beside it. The default container (`surfaceContainer`) is barely visible on a sheet in these themes.
 */
@Composable
internal fun fieldColors() = TextFieldDefaults.tonalColors().copy(
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
)

/**
 * A page's search field: one line, with the search icon in front and [placeholder] while it is empty.
 * - [clear]: the name of the × button. It shows while there is text, and empties the field. Null: no button.
 * - [keyboardOptions]: for example, a Search key on the keyboard.
 */
@Composable
internal fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    clear: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    TextField(
        shape = TextFieldDefaults.roundedShape,
        colors = fieldColors(),
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
        trailingIcon = if (clear != null && query.isNotEmpty()) {
            { LabeledIconButton(R.drawable.ic_close, clear, onClick = { onQuery("") }) }
        } else null,
        keyboardOptions = keyboardOptions,
        modifier = modifier,
    )
}

/**
 * A filter chip with the number of rows it keeps after its name: "Widget 18".
 * - [on]: the chip is chosen. It is then filled. It has no check mark: the fill already shows it.
 * - A chip that keeps no row (0) is grayed while it is off. A tap on it still works.
 */
@Composable
internal fun CountChip(
    label: String,
    count: Int,
    on: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val muted = count == 0 && !on
    FilterChip(
        selected = on,
        onClick = onClick,
        shapes = FilterChipDefaults.shapes(),
        label = {
            Text(buildAnnotatedString {
                append(label)
                append(" ")
                withStyle(SpanStyle(color = if (muted) colors.outline else colors.onSurfaceVariant)) {
                    append(
                        "$count"
                    )
                }
            })
        },
        colors = FilterChipDefaults.filterChipColors(labelColor = if (muted) colors.outline else colors.onSurfaceVariant),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = on,
            borderColor = if (muted) colors.outlineVariant else colors.outline
        ),
        modifier = modifier,
    )
}

/**
 * Picks how often something runs. It is a row like [SwitchRow]. Its button shows the interval and
 * opens a menu of presets. An interval set from the terminal (not a preset) shows as it is.
 */
@Composable
internal fun IntervalSelector(
    label: String,
    selected: Int,
    onPick: (Int) -> Unit,
    choices: List<Pair<Int, String>>
) {
    var open by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(
        if (open) 180f else 0f,
        MaterialTheme.motionScheme.defaultSpatialSpec()
    )
    SegmentedListItem(
        onClick = { open = !open },
        shapes = rowShapes(),
        colors = rowColors(MaterialTheme.colorScheme.surfaceContainerLow),
        contentPadding = ROW_PADDING,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        trailingContent = {
            Box {
                FilledTonalToggleButton(checked = open, onCheckedChange = { open = it }) {
                    Text(
                        choices.firstOrNull { it.first == selected }?.second
                            ?: CommandParsing.formatInterval(selected)
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Icon(
                        painterResource(R.drawable.ic_expand_more),
                        null,
                        Modifier
                            .size(ButtonDefaults.IconSize)
                            .rotate(turn)
                    )
                }
                DropdownMenuPopup(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                        // Use the theme's accent, like switches and tabs. Material's default is tertiaryContainer, which is the work profile's color here.
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
        },
    ) { Text(label, style = MaterialTheme.typography.bodyLarge) }
}
