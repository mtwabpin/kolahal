/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.SecretStore
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import com.mtwabpin.kolahal.terminal.tty.CellStyle
import com.mtwabpin.kolahal.terminal.tty.HomeCells
import com.mtwabpin.kolahal.terminal.tty.SheetCells
import com.mtwabpin.kolahal.ui.ActionMenu

/** An action's row is at least this tall, so it is easy to hit (the GUI's sheet has 48 dp rows). */
private val ACTION_HEIGHT = 48.dp

/**
 * A long-press menu on the grid ([SheetCells]): a box at the bottom of the screen with the line in
 * its top border, where it came from and its link under that, then its actions, the destructive
 * ones last in the error color. It renders the same [ActionMenu] as the GUI's sheet, so it has the
 * same actions: a tap runs one and closes the sheet.
 *
 * It is a dialog, so Back, focus and TalkBack treat it as one. A tap outside it closes it. Keys:
 * ↑ and ↓ move between the actions (the one focused is inverse), Enter runs it, Esc closes.
 */
@Composable
internal fun TtySheet(menu: ActionMenu, onDismiss: () -> Unit) {
    var focused by remember(menu) { mutableIntStateOf(-1) }
    TtyDialog(onDismiss, onKey = { key ->
        val n = menu.actions.size
        when (key) {
            Key.DirectionDown -> focused = if (n == 0) -1 else (focused + 1).coerceAtMost(n - 1)
            Key.DirectionUp -> focused = if (n == 0) -1 else (focused - 1).coerceAtLeast(0)
            Key.Enter, Key.NumPadEnter -> menu.actions.getOrNull(focused)?.let { onDismiss(); it.run() } ?: return@TtyDialog false
            else -> return@TtyDialog false
        }
        true
    }) { frame ->
        val rows = remember(menu, frame, focused) {
            SheetCells.sheet(menu.title, menu.detail, menu.url, menu.actions.map { it.label to it.destructive }, frame.cols, focused)
        }
        val actionHeight = with(LocalDensity.current) { ACTION_HEIGHT.roundToPx() }
        var detailShown = false
        for (row in rows) when (row.kind) {
            SheetCells.Kind.ACTION -> {
                val action = menu.actions[row.action]
                TtyBand(row.cells, actionHeight) {
                    SpotAction(onClick = { onDismiss(); action.run() }, text = action.label)
                }
            }

            SheetCells.Kind.TEXT -> {
                // The first text is where the line came from; the GUI's tag for it is menu-detail.
                val isDetail = !detailShown && !menu.detail.isNullOrBlank()
                detailShown = true
                val words = if (isDetail) menu.detail.orEmpty() else menu.url.orEmpty()
                TtyBand(
                    row.cells, row.cells.rows * frame.cellH,
                    Modifier
                        .then(if (isDetail) Modifier.testTag("menu-detail") else Modifier)
                        .semantics { text = AnnotatedString(words) },
                )
            }

            SheetCells.Kind.BORDER -> if (row === rows.first()) {
                TtyBand(row.cells, frame.cellH, Modifier.semantics {
                    heading()
                    text = AnnotatedString(menu.title)
                })
            } else TtyBand(row.cells, frame.cellH)
        }
    }
}

/**
 * The box `script secret` opens on the grid, as the GUI's dialog: the secret's name and script, what
 * it is for, where it is sent, and a field for its value that shows `•` for each character until
 * Show. Save keeps it (at least [SecretStore.MIN_LENGTH] characters), Clear removes one that is
 * set, Cancel closes. The field's tag is the GUI's, `secret-value`.
 */
@Composable
internal fun TtySecretSheet(
    script: String,
    secret: ScriptMeta.Secret,
    isSet: Boolean,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue("")) }
    var visible by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val canSave = value.text.trim().length >= SecretStore.MIN_LENGTH
    TtyDialog(onDismiss, onKey = { false }) { frame ->
        val title = "${secret.name} for $script"
        val top = remember(title, frame) { SheetCells.top(title, frame.cols) }
        TtyBand(top, frame.cellH, Modifier.semantics {
            heading()
            text = AnnotatedString(title)
        })
        val texts = buildList {
            if (secret.desc.isNotBlank()) add(secret.desc to CellStyle.of(Ink.FG))
            add("Sent only to ${secret.hosts.joinToString()}. Kept encrypted on this phone: never in the script, its backups or the log." to CellStyle.of(Ink.DIM))
            add((if (isSet) "Set · type a new one to replace it" else "Paste the value") to CellStyle.of(Ink.DIM))
        }
        for ((text, style) in texts) {
            val cells = remember(text, frame) { SheetCells.text(text, style, frame.cols, 6) }
            TtyBand(cells, cells.rows * frame.cellH, Modifier.semantics { this.text = AnnotatedString(text) })
        }
        // The field's row: the box's sides, and the field between them.
        val sides = remember(frame) { SheetCells.boxed(HomeCells(CellBuffer(1, 1)), frame.cols) }
        val fieldHeight = with(LocalDensity.current) { ACTION_HEIGHT.roundToPx() }
        Box {
            TtyBand(sides, fieldHeight)
            TtyField(
                value = value,
                onValueChange = { value = it },
                masked = !visible,
                focus = focus,
                frame = frame,
                x = frame.originX + 2 * frame.cellW,
                cols = SheetCells.inner(frame.cols),
                height = fieldHeight,
                prompt = "›",
                tag = "secret-value",
                onSubmit = { if (canSave) onSave(value.text.trim()) },
                onEscape = onDismiss,
            )
        }
        val labels = buildList {
            add((if (visible) "Hide it" else "Show it") to true)
            if (isSet) add("Clear" to true)
            add("Cancel" to true)
            add("Save" to canSave)
        }
        val buttons = remember(labels, frame) { SheetCells.buttons(labels, frame.cols) }
        TtyBand(buttons, fieldHeight) { spot ->
            val label = labels[spot.id].first
            SpotAction(
                onClick = {
                    when (label) {
                        "Save" -> onSave(value.text.trim())
                        "Clear" -> onSave(null)
                        "Cancel" -> onDismiss()
                        else -> visible = !visible
                    }
                },
                text = label,
            )
        }
        val bottom = remember(frame) { SheetCells.rule(frame.cols, bottom = true) }
        TtyBand(bottom, frame.cellH)
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
}

/**
 * A dialog of the grid's parts at the bottom of the screen, on the theme's background, at most 90 %
 * of the screen tall (its rows scroll past that). A tap outside it, Back or Esc closes it; [onKey]
 * gets the other keys first and says whether it used them.
 */
@Composable
private fun TtyDialog(onDismiss: () -> Unit, onKey: (Key) -> Boolean, content: @Composable ColumnScope.(TtyFrame) -> Unit) {
    val grid = rememberTtyGrid()
    val keyFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                // Tells it from the GUI's sheets, which have no tag (`tty.quick-actions`, `tty.menu`). On the
                // clickable: what is under it is read as part of it.
                .testTag("tty-sheet")
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val margin = with(LocalDensity.current) { GRID_MARGIN.roundToPx() }
            val frame = remember(grid, constraints.maxWidth) { TtyFrame.of(grid, constraints.maxWidth, margin) }
            CompositionLocalProvider(LocalTtyFrame provides frame) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * 0.9f)
                        .background(Color(grid.palette.background))
                        // Taps on the box that no row takes stay in the box: they don't close it.
                        // The touch's down is enough for that: the tap outside starts only from
                        // a down nobody took. The moves and the up must stay as they are. A row
                        // gives up its tap when anything takes a move, and a finger always moves
                        // a little between down and up; the scroll needs the moves too.
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { if (it.changedToDownIgnoreConsumed()) it.consume() }
                                }
                            }
                        }
                        .verticalScroll(rememberScrollState())
                        .focusRequester(keyFocus)
                        .onKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                            if (e.key == Key.Escape) {
                                onDismiss()
                                true
                            } else onKey(e.key)
                        }
                        .focusable(),
                ) {
                    content(frame)
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { keyFocus.requestFocus() } }
    }
}
