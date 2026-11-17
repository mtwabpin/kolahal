package com.mtwabpin.kolahal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalCursorBlinkEnabled
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import kotlin.math.floor
import kotlin.math.round
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/** When a text is longer than this (about 550 lines), only the lines around the view are coloured. */
private const val WINDOW_FROM = 20_000

/** How many lines are coloured above and below the view, and how close the view can come to the edge of that range before it moves. */
private const val WINDOW_SPAN = 150
private const val WINDOW_MARGIN = 50

/** How long the caret stays visible, then hidden, in each blink. Same timing as the text field's own caret. */
private val CARET_BLINK = 500.milliseconds

/** The most a tab can move the text forward: Android's tab stops are 20 pixels apart. */
private const val TAB_PX = 20f

/** The keys of the symbol bar under the code, after undo and redo. "Ln" opens Go to line. */
private val SYMBOLS = listOf("  ", "(", ")", "{", "}", "[", "]", "\"", "'", "=", ".", ",", ":", ";", "#", "-", "~", "<", ">", "Ln")

/**
 * The widest the code field gets, in pixels. A longer line wraps inside it.
 * Compose can't lay out a fixed width of 262,143 pixels or more (it keeps a size in 18 bits), and
 * one very long line (minified JSON, a data file) would be wider than that.
 */
private const val MAX_FIELD_PX = 250_000f

/**
 * A text with a line longer than this gets the widest field ([MAX_FIELD_PX]) without being measured:
 * such a line is about that wide or wider, and measuring it costs as much as laying it out.
 */
private const val LONG_LINE = 10_000

/**
 * The editor that the script editor and the text editor share: a monospace text field with line
 * numbers, light syntax colouring, undo/redo, find/replace, Go to line and a symbol bar. It draws
 * [state] and changes it.
 *
 * What the screen around it adds:
 * - [title]: the name at the top. A `*` after it marks unsaved changes.
 * - [lang]: the language whose colours the text gets. Null: plain text.
 * - [buttons]: the buttons after Find, at the top right (save, close, …).
 * - [belowCode]: what shows under the code, at most as tall as it is told (a run's lines).
 * - [hint]: one line above the symbol bar, shown while there is no message (inline docs).
 * - [aboveSymbols]: a row above the symbol bar (completions).
 * - [onKey]: the screen's own key shortcuts. It gets each key press first. True: the key is used.
 * - [onTab]: Tab in the code, with whether Shift is held. True: the screen used it. Otherwise it types two spaces.
 * - [onEscape]: Esc while the find bar is closed.
 * - [wrap]: long lines wrap at the screen's edge, and there are no line numbers. Otherwise each
 *   line stays one line, and the code scrolls sideways. (A line too long for the widest field
 *   wraps even then, and such a text has no line numbers either.)
 * - [focusOnOpen]: the code gets the focus, and the keyboard shows, as soon as the editor opens.
 *
 * When opened at [startLine], that line is scrolled into view.
 *
 * Keys:
 * - Ctrl+Z / Ctrl+Shift+Z undo/redo.
 * - Ctrl+F finds; Ctrl+H replaces.
 * - F3 / Shift+F3 go to the next/previous match.
 * - Ctrl+G goes to a line.
 * - Esc and Back close the find bar.
 */
@Composable
internal fun CodeEditor(
    state: EditorState,
    title: String,
    lang: Syntax?,
    startLine: Int?,
    onKey: (KeyEvent) -> Boolean,
    onTab: (shift: Boolean) -> Boolean,
    onEscape: () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
    belowCode: @Composable ColumnScope.(maxHeight: Dp) -> Unit = {},
    hint: @Composable ColumnScope.() -> Unit = {},
    aboveSymbols: @Composable ColumnScope.() -> Unit = {},
    wrap: Boolean = false,
    focusOnOpen: Boolean = true,
) {
    val value = state.value
    val findFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        when (m.kind) {
            // Errors and notes stay until the next message, because the user reads them while fixing the text.
            EditorMessage.Kind.ERROR, EditorMessage.Kind.NOTE -> Unit
            // The screen that set it ends it: it knows what it waits for.
            EditorMessage.Kind.BUSY -> Unit
            EditorMessage.Kind.INFO, EditorMessage.Kind.OK -> {
                delay(3_000.milliseconds)
                if (state.message === m) state.message = null
            }
        }
    }

    // Scrolling to a match / line
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var viewportWidth by remember { mutableIntStateOf(0) }

    fun reveal(offset: Int) {
        val l = layout ?: return
        val o = offset.coerceIn(0, l.layoutInput.text.length)
        val padTop = with(density) { 12.dp.roundToPx() }
        val line = l.getLineForOffset(o)
        val top = l.getLineTop(line).toInt() + padTop
        val bottom = l.getLineBottom(line).toInt() + padTop
        if (top < vScroll.value || bottom > vScroll.value + viewportHeight) {
            scope.launch { vScroll.animateScrollTo((top - viewportHeight / 3).coerceAtLeast(0)) }
        }
        // Wrapped lines are all in view sideways.
        if (wrap) return
        val x = l.getHorizontalPosition(o, true).toInt()
        val margin = with(density) { 48.dp.roundToPx() }
        if (x < hScroll.value || x > hScroll.value + viewportWidth - margin) {
            scope.launch { hScroll.animateScrollTo((x - viewportWidth / 3).coerceAtLeast(0)) }
        }
    }

    fun focusEditor() {
        runCatching { state.focus.requestFocus() }
        keyboard?.show()
    }

    // ---- find / replace -------------------------------------------------------------------

    fun select(start: Int, length: Int) {
        val s = start.coerceIn(0, state.value.text.length)
        state.value = state.value.copy(selection = TextRange(s, (s + length).coerceAtMost(state.value.text.length)))
        reveal(s)
    }

    fun gotoMatch(delta: Int) {
        val m = state.matches
        if (m.size == 0) return
        state.current = m.indexFrom(state.value.selection.min, forward = delta > 0)
        select(m.starts[state.current], m.length)
    }

    fun openFind(withReplace: Boolean) {
        val sel = state.value.selection
        if (!sel.collapsed && sel.length <= 200) {
            val picked = state.value.text.substring(sel.min, sel.max)
            if ('\n' !in picked) state.query = TextFieldValue(picked)
        }
        state.query = state.query.copy(selection = TextRange(0, state.query.text.length))
        state.findOpen = true
        if (withReplace) state.replaceOpen = true
        scope.launch {
            delay(50.milliseconds)
            runCatching { findFocus.requestFocus() }
        }
    }

    fun closeFind() {
        state.findOpen = false
        state.replaceOpen = false
        focusEditor()
    }

    fun replaceOne() {
        val q = state.query.text
        if (q.isEmpty()) return
        val sel = state.value.selection
        val text = state.value.text
        val onMatch = sel.length == q.length && text.regionMatches(sel.min, q, 0, q.length, state.ignoreCase)
        if (!onMatch) {
            gotoMatch(1)
            return
        }
        val newText = text.replaceRange(sel.min, sel.max, state.replacement.text)
        val after = sel.min + state.replacement.text.length
        state.history.checkpoint()
        state.edit(TextFieldValue(newText, TextRange(after)))
        state.history.checkpoint()
        val next = newText.indexOf(q, after, state.ignoreCase).takeIf { it >= 0 } ?: newText.indexOf(q, 0, state.ignoreCase)
        if (next >= 0) select(next, q.length)
    }

    fun replaceAll() {
        val q = state.query.text
        if (q.isEmpty()) return
        val (newText, n) = EditorLogic.replaceAll(state.value.text, q, state.replacement.text, state.ignoreCase)
        if (n == 0) {
            state.message = EditorMessage("No matches to replace", EditorMessage.Kind.INFO)
            return
        }
        state.history.checkpoint()
        state.edit(TextFieldValue(newText, TextRange(state.value.selection.min.coerceAtMost(newText.length))))
        state.history.checkpoint()
        state.message = EditorMessage("Replaced $n", EditorMessage.Kind.OK)
    }

    // Matches are found off the main thread, with a short delay while typing.
    var lastSearch by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    LaunchedEffect(state.findOpen, state.query.text, state.ignoreCase, value.text) {
        if (!state.findOpen || state.query.text.isEmpty()) {
            state.matches = EditorLogic.Matches.EMPTY
            state.current = -1
            lastSearch = null
            return@LaunchedEffect
        }
        delay((if (state.value.text.length > 200_000) 250 else 80).milliseconds)
        val text = state.value.text
        val q = state.query.text
        val ic = state.ignoreCase
        val m = withContext(Dispatchers.Default) { EditorLogic.findMatches(text, q, ic) }
        state.matches = m
        val queryChanged = lastSearch != (q to ic)
        lastSearch = q to ic
        if (m.size == 0) {
            state.current = -1
        } else if (queryChanged) {
            // Incremental search: jump to the first match from the cursor.
            state.current = m.indexAtOrAfter(state.value.selection.min)
            select(m.starts[state.current], m.length)
        } else {
            state.current = m.indexAtOrAfter(state.value.selection.min)
        }
    }

    fun gotoLine(line: Int) {
        val offset = EditorLogic.lineStart(state.value.text, line)
        state.value = state.value.copy(selection = TextRange(offset))
        state.flashLine = line
        reveal(offset)
        focusEditor()
    }

    BackHandler(enabled = state.findOpen) { closeFind() }
    LaunchedEffect(state) { if (focusOnOpen) runCatching { state.focus.requestFocus() } }
    LaunchedEffect(state.flashLine) {
        if (state.flashLine != null) {
            delay(3_000.milliseconds)
            state.flashLine = null
        }
    }
    // Scroll an error jump into view once the text has been laid out.
    var revealedJump by remember(state) { mutableStateOf(startLine == null) }
    // Read inside the effect, not used as its keys. Each key press lays out the text again, and as
    // keys they would rebuild the editor after every layout.
    LaunchedEffect(state) {
        androidx.compose.runtime.snapshotFlow { layout != null && viewportHeight > 0 }.first { it }
        if (!revealedJump) {
            revealedJump = true
            reveal(state.value.selection.start)
        }
    }

    val highlights = remember(state.findOpen, state.matches, state.current) {
        if (state.findOpen) EditorLogic.highlights(state.matches, state.current) else null
    }
    // The field's width, computed here from its widest line. Inside the horizontal scroll the field
    // would measure all of the text to find it before layout: a second pass over the text at every key.
    val measurer = androidx.compose.ui.text.rememberTextMeasurer(cacheSize = 16)
    val bold = Term.code.copy(fontWeight = FontWeight.Bold)
    val cellPx = remember(bold, density) { listOf("W", "M", "@").maxOf { measurer.measure(it, bold).size.width }.toFloat() }
    val textWidth = remember(value.text, bold, density, wrap) {
        when {
            // The field is as wide as the screen, so there is nothing to measure.
            wrap -> 0f
            EditorLogic.hasLineLongerThan(value.text, LONG_LINE) -> MAX_FIELD_PX
            // Measured in bold, the widest style a line uses, so a bold keyword doesn't wrap its line.
            else -> EditorLogic.widestLine(value.text, cellPx, maxOf(TAB_PX, cellPx)) { line -> measurer.measure(line, bold).size.width.toFloat() }
        }
    }
    val widest = with(density) { MAX_FIELD_PX.toDp() }
    val fieldWidth = with(density) { minOf(maxOf(600.dp, (textWidth + cellPx).toDp() + 12.dp), widest) }
    // Whether a line of the text can take more than one line on screen.
    val wraps by androidx.compose.runtime.rememberUpdatedState(wrap || fieldWidth >= widest)
    // In a big text, only the lines around the view are coloured: colouring costs several times
    // the plain text's layout at every key. The coloured range follows the view after scrolling
    // stops, so a fling doesn't cause new layouts along the way.
    val big = value.text.length > WINDOW_FROM
    var window by remember(state) {
        mutableStateOf(((startLine ?: 1) - 1).let { (it - WINDOW_SPAN).coerceAtLeast(0)..(it + WINDOW_SPAN) })
    }
    LaunchedEffect(state) {
        val padTop = with(density) { 12.dp.toPx() }
        androidx.compose.runtime.snapshotFlow { Triple(vScroll.isScrollInProgress, vScroll.value, layout) }.collect { (scrolling, y, l) ->
            if (scrolling || l == null || l.lineCount == 0) return@collect
            // The window counts the text's own lines. Where lines wrap, a line on screen is not one of those.
            fun lineAt(y: Float): Int = l.getLineForVerticalPosition(y).let { shown ->
                if (wraps) EditorLogic.lineOf(l.layoutInput.text.text, l.getLineStart(shown)) else shown
            }
            val first = lineAt((y - padTop).coerceAtLeast(0f))
            val last = lineAt(y - padTop + viewportHeight)
            val w = window
            val nearTop = w.first > 0 && first < w.first + WINDOW_MARGIN
            val nearBottom = w.last < l.lineCount - 1 && last > w.last - WINDOW_MARGIN
            if (nearTop || nearBottom) window = (first - WINDOW_SPAN).coerceAtLeast(0)..(last + WINDOW_SPAN)
        }
    }
    // The tokens of each line are kept across highlighters, because find matches and a flashed line create new highlighters.
    val lineTokens = remember(lang) { lang?.let(::LineTokens) }
    // Plain text has no colours to leave out.
    val coloured = if (big && lang != null) window else null
    val flashLine = state.flashLine
    val highlighter = remember(lang, flashLine, Term.palette, highlights, coloured) { SyntaxHighlighter(lang, flashLine, highlights, lineTokens, coloured) }
    val lineCount = value.text.count { it == '\n' } + 1
    // Where lines wrap there are no line numbers: a number would stand beside the wrong line.
    val numbers = remember(lineCount, wraps) { if (wraps) null else (1..lineCount).joinToString("\n") }

    Column(
        Modifier
            .fillMaxSize()
            .background(Term.bg)
            .cutoutSafePadding()
            .navigationBarsPadding()
            .imePadding()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    onKey(e) -> true
                    e.isCtrlPressed && e.key == Key.F -> { openFind(false); true }
                    e.isCtrlPressed && e.key == Key.H -> { openFind(true); true }
                    e.isCtrlPressed && e.key == Key.G -> { state.gotoOpen = true; true }
                    e.key == Key.F3 -> { if (state.findOpen) gotoMatch(if (e.isShiftPressed) -1 else 1) else openFind(false); true }
                    // Only for the editor: the text fields in the find bar keep their own undo.
                    state.focused && e.isCtrlPressed && e.isShiftPressed && e.key == Key.Z -> { state.redo(); true }
                    state.focused && e.isCtrlPressed && e.key == Key.Z -> { state.undo(); true }
                    state.focused && e.isCtrlPressed && e.key == Key.Y -> { state.redo(); true }
                    e.key == Key.Escape -> { if (state.findOpen) closeFind() else onEscape(); true }
                    else -> false
                }
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(Term.surface).padding(start = 12.dp),
        ) {
            Text(
                title + if (state.dirty) " *" else "",
                style = Term.phoneText.copy(color = Term.prompt, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            EditorIconButton(R.drawable.ic_search, "Find", tint = if (state.findOpen) Term.prompt else Term.fg) {
                if (state.findOpen) closeFind() else openFind(false)
            }
            buttons()
        }
        if (state.findOpen) {
            FindBar(
                query = state.query,
                onQuery = { state.query = it },
                replacement = state.replacement,
                onReplacement = { state.replacement = it },
                replaceOpen = state.replaceOpen,
                onToggleReplace = { state.replaceOpen = !state.replaceOpen },
                ignoreCase = state.ignoreCase,
                onToggleCase = { state.ignoreCase = !state.ignoreCase },
                count = when {
                    state.query.text.isEmpty() -> ""
                    state.matches.size == 0 -> "0/0"
                    else -> "${state.current + 1}/${state.matches.size}${if (state.matches.capped) "+" else ""}"
                },
                noMatch = state.query.text.isNotEmpty() && state.matches.size == 0 && lastSearch != null,
                focusRequester = findFocus,
                onNext = { gotoMatch(1) },
                onPrev = { gotoMatch(-1) },
                onReplace = ::replaceOne,
                onReplaceAll = ::replaceAll,
                onClose = ::closeFind,
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { viewportHeight = it.height }
                // Tapping empty space below the code focuses the editor.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    state.value = state.value.copy(selection = TextRange(state.value.text.length))
                    focusEditor()
                }
                .verticalScroll(vScroll)
                .padding(vertical = 12.dp)
        ) {
            Row {
                // Line numbers share the field's style, so they stay aligned while scrolling.
                if (numbers != null) {
                    Text(
                        numbers,
                        style = Term.code.copy(color = Term.dim, textAlign = TextAlign.End),
                        modifier = Modifier.widthIn(min = 28.dp).padding(start = 6.dp, end = 10.dp),
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .onSizeChanged { viewportWidth = it.width }
                        .then(if (numbers == null) Modifier.padding(start = 12.dp) else Modifier)
                        .then(if (wrap) Modifier else Modifier.horizontalScroll(hScroll))
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = state::edit,
                        textStyle = Term.code,
                        // Hide the field's own caret: it is drawn with the text, so each blink redrew
                        // the whole text. [EditorCaret] draws it over the text instead.
                        cursorBrush = SolidColor(Color.Unspecified),
                        visualTransformation = highlighter,
                        onTextLayout = { layout = it },
                        decorationBox = { field ->
                            // The field keeps its minimum width, as it would without a box around it.
                            Box(propagateMinConstraints = true) {
                                field()
                                EditorCaret(value, { layout }, state.focused, Term.prompt)
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            capitalization = KeyboardCapitalization.None,
                            // Plain text (not password), so non-Latin layouts and voice input work in code.
                            keyboardType = KeyboardType.Text,
                        ),
                        modifier = (if (wrap) Modifier.fillMaxWidth() else Modifier.width(fieldWidth))
                            .padding(end = 12.dp)
                            .focusRequester(state.focus)
                            .onFocusChanged { state.focused = it.isFocused }
                            .testTag("editor")
                            .onPreviewKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && e.key == Key.Tab) {
                                    if (!onTab(e.isShiftPressed)) state.insert("  ")
                                    true
                                } else false
                            },
                    )
                }
            }
        }
        belowCode(with(density) { (viewportHeight * 0.4f).toDp() })
        val shown = state.message
        if (shown != null) {
            Text(
                shown.text,
                style = Term.phoneText.copy(
                    color = when (shown.kind) {
                        EditorMessage.Kind.OK -> Term.prompt
                        EditorMessage.Kind.ERROR -> Term.error
                        EditorMessage.Kind.BUSY -> Term.fg
                        EditorMessage.Kind.INFO -> Term.dim
                        EditorMessage.Kind.NOTE -> Term.info
                    },
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Term.surface)
                    // Tap a message with an action to do it, or one with a failing line to go there.
                    // Tap any other message to dismiss it.
                    .clickable {
                        when {
                            shown.action != null -> shown.action.invoke()
                            shown.line != null -> gotoLine(shown.line)
                            else -> state.message = null
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("message"),
            )
        } else {
            hint()
        }
        aboveSymbols()
        // Read here, not inside the row's items, so a change updates the undo and redo buttons.
        val canUndo = state.history.canUndo
        val canRedo = state.history.canRedo
        LazyRow(Modifier.fillMaxWidth().background(Term.surface)) {
            item { EditorIconButton(R.drawable.ic_undo, "Undo", enabled = canUndo) { state.undo() } }
            item { EditorIconButton(R.drawable.ic_redo, "Redo", enabled = canRedo) { state.redo() } }
            items(SYMBOLS) { s ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .widthIn(min = 48.dp)
                        .clickable(onClickLabel = if (s == "Ln") "Go to line" else null) {
                            if (s == "Ln") state.gotoOpen = true else state.insert(s)
                        }
                        .padding(horizontal = 12.dp),
                ) {
                    Text(if (s == "  ") "⇥" else s, style = Term.phoneText.copy(color = Term.fg, fontWeight = FontWeight.Bold))
                }
            }
        }
    }

    if (state.gotoOpen) {
        GotoLineDialog(lineCount, onDismiss = { state.gotoOpen = false }) { line ->
            state.gotoOpen = false
            gotoLine(line)
        }
    }
}

/**
 * The editor's caret, in its own layer over the text, so a blink redraws only the caret and not the
 * whole text. It looks like the text field's own caret:
 * - 2 dp wide, at the cursor;
 * - shown at once after a change, then blinking every 500 ms;
 * - visible only while the editor and its window have focus and nothing is selected.
 *
 * The layer is as big as the text, and takes its size from the text's [layout]. It must not be
 * given that size as a fixed one (`matchParentSize`). Compose keeps a fixed width and height
 * together in 31 bits, so it can't hold:
 * - a text that is both wide and long (on a usual phone, a line of 400 characters in a file of 1,500 lines);
 * - any text taller than 262,142 pixels (there, about 5,000 lines).
 */
@Composable
private fun EditorCaret(value: TextFieldValue, layout: () -> TextLayoutResult?, focused: Boolean, color: Color) {
    val blinks = LocalCursorBlinkEnabled.current
    val show = LocalWindowInfo.current.isWindowFocused && focused && value.selection.collapsed
    var alpha by remember { mutableFloatStateOf(0f) }
    if (show) {
        LaunchedEffect(value.text, value.selection, blinks) {
            try {
                alpha = 1f
                if (!blinks) awaitCancellation()
                while (true) {
                    delay(CARET_BLINK)
                    alpha = 0f
                    delay(CARET_BLINK)
                    alpha = 1f
                }
            } finally {
                alpha = 0f
            }
        }
    }
    Box(
        Modifier
            .graphicsLayer()
            .drawBehind {
                val a = alpha
                if (!show || a == 0f) return@drawBehind
                val result = layout() ?: return@drawBehind
                val rect = result.getCursorRect(value.selection.start.coerceIn(0, result.layoutInput.text.length))
                val width = floor(2.dp.toPx()).coerceAtLeast(1f)
                val x = (rect.left + width / 2)
                    .coerceAtMost(size.width - width / 2)
                    .coerceAtLeast(width / 2)
                    // Draw an odd width at the middle of a pixel, so it isn't blurred.
                    .let { if (width.toInt() % 2 == 1) floor(it) + 0.5f else round(it) }
                drawLine(color, Offset(x, rect.top), Offset(x, rect.bottom), strokeWidth = width, alpha = a)
            }
            // The text is measured before this, in the same pass, so its layout is the current one.
            .layout { measurable, _ ->
                val text = layout()?.size ?: IntSize.Zero
                val placeable = measurable.measure(Constraints.fixed(0, 0))
                layout(text.width, text.height) { placeable.place(0, 0) }
            },
    )
}
