package com.mtwabpin.kolahal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.shownText
import com.mtwabpin.kolahal.script.ApiDoc
import com.mtwabpin.kolahal.script.ScriptWorker
import kotlin.math.floor
import kotlin.math.round
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Monospace editor for scripts: line numbers, light syntax colouring, undo/redo, find/replace,
 * API completion and inline docs. Saving says what it changed beyond the code (a timer its `@every`
 * line set) and what [com.mtwabpin.kolahal.script.ScriptCheck] finds; a run's lines show under the code ([runLines]), and a
 * long press on run runs it with args or as an event ([RunWithDialog]).
 * Ctrl+S saves, Ctrl+R saves and runs, Ctrl+Z / Ctrl+Shift+Z undo/redo, Ctrl+F find, Ctrl+H replace,
 * F3 / Shift+F3 next/previous match, Ctrl+G go to line, Tab accepts the first suggestion,
 * Esc closes the find bar, then saves and closes the editor (as does Back).
 * When opened from an error, [jumpToLine] is selected and briefly highlighted.
 * A message line above the symbol bar says what the last button did: saved, the run's result
 * (from [lastRun] / [lastStatus]; a failing line is a tap away), replacements made.
 * [onSave] reports back with null or the failure; [onClose] gets the text when it had unsaved changes.
 */
@Composable
fun ScriptEditor(
    script: Script,
    jumpToLine: Int?,
    lastRun: Long?,
    lastStatus: String?,
    onSave: (String, (Throwable?, String?) -> Unit) -> Unit,
    onRun: (String, List<String>, Map<String, Any?>?, (Throwable?, String?) -> Unit) -> Unit,
    runLines: suspend (since: Long) -> List<com.mtwabpin.kolahal.data.LogEntry>,
    onClose: (unsaved: String?) -> Unit,
) {
    var value by rememberSaveable(script.id, stateSaver = keptText(script.code)) {
        mutableStateOf(TextFieldValue(script.code, selectionAtLine(script.code, jumpToLine)))
    }
    var saved by rememberSaveable(script.id, stateSaver = keptSaved(script.code)) { mutableStateOf(script.code) }
    val dirty = value.text != saved
    val focus = remember { FocusRequester() }
    val findFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val history = remember(script.id) { UndoHistory(script.code) }
    var flashLine by remember(script.id) { mutableStateOf(jumpToLine) }
    var editorFocused by remember { mutableStateOf(false) }

    // Find / replace
    var findOpen by rememberSaveable(script.id) { mutableStateOf(false) }
    var replaceOpen by rememberSaveable(script.id) { mutableStateOf(false) }
    var query by rememberSaveable(script.id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var replacement by rememberSaveable(script.id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var ignoreCase by rememberSaveable(script.id) { mutableStateOf(true) }
    var matches by remember(script.id) { mutableStateOf(EditorLogic.Matches.EMPTY) }
    var current by remember(script.id) { mutableIntStateOf(-1) }

    var gotoOpen by remember { mutableStateOf(false) }
    var referenceOpen by remember { mutableStateOf(false) }
    var runWithOpen by remember { mutableStateOf(false) }
    // The last run's lines, under the code until closed or the next run.
    var output by remember(script.id) { mutableStateOf<List<com.mtwabpin.kolahal.data.LogEntry>?>(null) }

    // What the last button did; a run waits for its recorded result.
    var message by remember(script.id) { mutableStateOf<EditorMessage?>(null) }
    var runSince by remember(script.id) { mutableStateOf<Long?>(null) }
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        when (m.kind) {
            // Errors stay until the next message: they're read while fixing the code.
            EditorMessage.Kind.ERROR -> Unit
            // A run that never reports (the worker didn't start) stops waiting after the time limit.
            EditorMessage.Kind.BUSY -> {
                delay((ScriptWorker.TIMEOUT_MS + 15_000).milliseconds)
                if (message === m) { message = null; runSince = null }
            }
            else -> {
                delay(3_000.milliseconds)
                if (message === m) message = null
            }
        }
    }
    LaunchedEffect(lastRun, lastStatus) {
        val since = runSince ?: return@LaunchedEffect
        if (lastRun == null || lastRun < since) return@LaunchedEffect
        runSince = null
        val r = EditorLogic.runResult(lastStatus, ScriptWorker.TIMEOUT_MS / 1000)
        // The lines are written as the run ends, its result just after.
        val lines = runLines(since)
        output = lines
        val text = if (lines.isEmpty() || r.failed) r.text else r.text.replace("its output is in the log", "${lines.size} line${if (lines.size == 1) "" else "s"} below")
        message = EditorMessage(text, if (r.failed) EditorMessage.Kind.ERROR else EditorMessage.Kind.OK, r.line)
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
        val x = l.getHorizontalPosition(o, true).toInt()
        val margin = with(density) { 48.dp.roundToPx() }
        if (x < hScroll.value || x > hScroll.value + viewportWidth - margin) {
            scope.launch { hScroll.animateScrollTo((x - viewportWidth / 3).coerceAtLeast(0)) }
        }
    }

    /** "Saved", what saving changed, and the first of what [com.mtwabpin.kolahal.script.ScriptCheck] finds (a tap goes to its line). */
    fun savedMessage(text: String, changed: String?): EditorMessage {
        val head = listOfNotNull("Saved", changed).joinToString(" · ")
        val issues = com.mtwabpin.kolahal.script.ScriptCheck.check(text, script.lang)
        val first = issues.firstOrNull() ?: return EditorMessage(head, EditorMessage.Kind.OK)
        val more = if (issues.size > 1) " (and ${issues.size - 1} more)" else ""
        return EditorMessage("$head · line ${first.line}: ${first.text}$more", EditorMessage.Kind.ERROR, first.line)
    }

    /** [quiet]: leaving the app saves without a message. */
    fun save(quiet: Boolean = false) {
        if (!dirty) {
            if (!quiet) message = EditorMessage("No changes to save", EditorMessage.Kind.INFO)
            return
        }
        val text = value.text
        onSave(text) { failure, changed ->
            if (failure == null) {
                saved = text
                if (!quiet) message = savedMessage(text, changed)
            } else {
                message = EditorMessage("Couldn't save: ${failure.message ?: failure.javaClass.simpleName}", EditorMessage.Kind.ERROR)
            }
        }
    }

    fun run(args: List<String> = emptyList(), event: Map<String, Any?>? = null) {
        // Running saves the text first; it counts as saved once it is, so a failed save keeps the edits.
        val text = value.text
        runSince = System.currentTimeMillis()
        output = null
        message = EditorMessage("Saving…", EditorMessage.Kind.BUSY)
        onRun(text, args, event) { failure, changed ->
            if (failure == null) {
                saved = text
                message = EditorMessage(listOfNotNull("Saved", changed, "running…").joinToString(" · "), EditorMessage.Kind.BUSY)
            } else {
                // It didn't run: a later run (its timer's) isn't this one's result.
                runSince = null
                message = EditorMessage("Couldn't save: ${failure.message ?: failure.javaClass.simpleName}", EditorMessage.Kind.ERROR)
            }
        }
    }

    fun close() {
        onClose(value.text.takeIf { dirty })
    }

    fun edit(new: TextFieldValue) {
        if (new.text != value.text) history.record(new.text)
        value = new
    }

    fun insert(s: String) {
        val sel = value.selection
        edit(TextFieldValue(value.text.replaceRange(sel.min, sel.max, s), TextRange(sel.min + s.length)))
    }

    fun undo() = history.undo()?.let { value = TextFieldValue(it, TextRange(it.length.coerceAtMost(value.selection.end))) }
    fun redo() = history.redo()?.let { value = TextFieldValue(it, TextRange(it.length.coerceAtMost(value.selection.end))) }

    fun focusEditor() {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    // ---- find / replace -------------------------------------------------------------------

    fun select(start: Int, length: Int) {
        val s = start.coerceIn(0, value.text.length)
        value = value.copy(selection = TextRange(s, (s + length).coerceAtMost(value.text.length)))
        reveal(s)
    }

    fun gotoMatch(delta: Int) {
        val m = matches
        if (m.size == 0) return
        val from = value.selection.min
        current = if (delta > 0) {
            m.indexAtOrAfter(from + 1)
        } else {
            // 0 means either "first match is at/after the cursor" or "none is": both wrap to the last.
            val i = m.indexAtOrAfter(from)
            if (i == 0) m.size - 1 else i - 1
        }
        select(m.starts[current], m.length)
    }

    fun openFind(withReplace: Boolean) {
        val sel = value.selection
        if (!sel.collapsed && sel.length <= 200) {
            val picked = value.text.substring(sel.min, sel.max)
            if ('\n' !in picked) query = TextFieldValue(picked)
        }
        query = query.copy(selection = TextRange(0, query.text.length))
        findOpen = true
        if (withReplace) replaceOpen = true
        scope.launch {
            delay(50.milliseconds)
            runCatching { findFocus.requestFocus() }
        }
    }

    fun closeFind() {
        findOpen = false
        replaceOpen = false
        focusEditor()
    }

    fun replaceOne() {
        val q = query.text
        if (q.isEmpty()) return
        val sel = value.selection
        val text = value.text
        val onMatch = sel.length == q.length && text.regionMatches(sel.min, q, 0, q.length, ignoreCase)
        if (!onMatch) {
            gotoMatch(1)
            return
        }
        val newText = text.replaceRange(sel.min, sel.max, replacement.text)
        val after = sel.min + replacement.text.length
        history.checkpoint()
        edit(TextFieldValue(newText, TextRange(after)))
        history.checkpoint()
        val next = newText.indexOf(q, after, ignoreCase).takeIf { it >= 0 } ?: newText.indexOf(q, 0, ignoreCase)
        if (next >= 0) select(next, q.length)
    }

    fun replaceAll() {
        val q = query.text
        if (q.isEmpty()) return
        val (newText, n) = EditorLogic.replaceAll(value.text, q, replacement.text, ignoreCase)
        if (n == 0) {
            message = EditorMessage("No matches to replace", EditorMessage.Kind.INFO)
            return
        }
        history.checkpoint()
        edit(TextFieldValue(newText, TextRange(value.selection.min.coerceAtMost(newText.length))))
        history.checkpoint()
        message = EditorMessage("Replaced $n", EditorMessage.Kind.OK)
    }

    // Matches are found off the main thread, debounced while typing.
    var lastSearch by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    LaunchedEffect(findOpen, query.text, ignoreCase, value.text) {
        if (!findOpen || query.text.isEmpty()) {
            matches = EditorLogic.Matches.EMPTY
            current = -1
            lastSearch = null
            return@LaunchedEffect
        }
        delay((if (value.text.length > 200_000) 250 else 80).milliseconds)
        val text = value.text
        val q = query.text
        val ic = ignoreCase
        val m = withContext(Dispatchers.Default) { EditorLogic.findMatches(text, q, ic) }
        matches = m
        val queryChanged = lastSearch != (q to ic)
        lastSearch = q to ic
        if (m.size == 0) {
            current = -1
        } else if (queryChanged) {
            // Incremental search: jump to the first match from the cursor.
            current = m.indexAtOrAfter(value.selection.min)
            select(m.starts[current], m.length)
        } else {
            current = m.indexAtOrAfter(value.selection.min)
        }
    }

    // ---- completion / docs ------------------------------------------------------------------

    val suggestions = remember(value.text, value.selection, editorFocused, script.lang) {
        if (!editorFocused || !value.selection.collapsed) emptyList()
        else EditorLogic.completions(value.text, value.selection.end, script.lang)
    }
    val inlineDoc = remember(value.text, value.selection.end, editorFocused, script.lang) {
        if (!editorFocused) null
        else EditorLogic.docAt(value.text, value.selection.end, script.lang)?.takeIf { it.kind != ApiDoc.Kind.KEYWORD }
    }

    fun accept(doc: ApiDoc) {
        val c = value.selection.end
        val p = EditorLogic.prefixAt(value.text, c) ?: return
        val next = value.text.getOrNull(c)
        val ins = EditorLogic.insertion(doc, next)
        var cursor = p.start + ins.length
        // Step over an existing `(` / `.` that the insertion relied on.
        if ((doc.kind == ApiDoc.Kind.FUNCTION && next == '(') || (doc.kind == ApiDoc.Kind.NAMESPACE && next == '.')) cursor++
        edit(TextFieldValue(value.text.replaceRange(p.start, c, ins), TextRange(cursor)))
    }

    fun gotoLine(line: Int) {
        val offset = EditorLogic.lineStart(value.text, line)
        value = value.copy(selection = TextRange(offset))
        flashLine = line
        reveal(offset)
        focusEditor()
    }

    BackHandler { close() }
    BackHandler(enabled = findOpen) { closeFind() }
    // Leaving the app (or the process being killed in the background) must not lose edits.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { save(quiet = true) }
    LaunchedEffect(script.id) { runCatching { focus.requestFocus() } }
    LaunchedEffect(flashLine) {
        if (flashLine != null) {
            delay(3_000.milliseconds)
            flashLine = null
        }
    }
    // Scroll an error jump into view once the text has been laid out.
    var revealedJump by remember(script.id) { mutableStateOf(jumpToLine == null) }
    LaunchedEffect(layout, viewportHeight) {
        if (!revealedJump && layout != null && viewportHeight > 0) {
            revealedJump = true
            reveal(value.selection.start)
        }
    }

    val highlights = remember(findOpen, matches, current) {
        if (!findOpen || matches.size == 0) null
        else {
            val c = current.coerceIn(0, matches.size - 1)
            val from = (c - EditorLogic.MAX_HIGHLIGHTS / 2).coerceAtLeast(0)
            val to = (from + EditorLogic.MAX_HIGHLIGHTS).coerceAtMost(matches.size)
            MatchHighlights(matches.starts.copyOfRange(from, to), matches.length, if (current >= 0) matches.starts[c] else -1)
        }
    }
    // The tokens of each line stay across highlighters: find matches and a flashed line make new ones.
    val lineTokens = remember(script.lang) { LineTokens(script.lang) }
    val highlighter = remember(script.lang, flashLine, Term.palette, highlights) { SyntaxHighlighter(script.lang, flashLine, highlights, lineTokens) }
    val lineCount = value.text.count { it == '\n' } + 1
    val numbers = remember(lineCount) { (1..lineCount).joinToString("\n") }

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
                    e.isCtrlPressed && e.key == Key.S -> { save(); true }
                    e.isCtrlPressed && e.isShiftPressed && e.key == Key.R -> { runWithOpen = true; true }
                    e.isCtrlPressed && e.key == Key.R -> { run(); true }
                    e.isCtrlPressed && e.key == Key.F -> { openFind(false); true }
                    e.isCtrlPressed && e.key == Key.H -> { openFind(true); true }
                    e.isCtrlPressed && e.key == Key.G -> { gotoOpen = true; true }
                    e.key == Key.F3 -> { if (findOpen) gotoMatch(if (e.isShiftPressed) -1 else 1) else openFind(false); true }
                    // Text fields in the find bar keep their own undo.
                    editorFocused && e.isCtrlPressed && e.isShiftPressed && e.key == Key.Z -> { redo(); true }
                    editorFocused && e.isCtrlPressed && e.key == Key.Z -> { undo(); true }
                    editorFocused && e.isCtrlPressed && e.key == Key.Y -> { redo(); true }
                    e.key == Key.Escape -> { if (findOpen) closeFind() else close(); true }
                    else -> false
                }
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(Term.surface).padding(start = 12.dp),
        ) {
            Text(
                "${script.name}.${script.lang.ext}" + if (dirty) " *" else "",
                style = Term.code.copy(color = Term.prompt, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clickable { if (findOpen) closeFind() else openFind(false) },
            ) {
                Icon(painterResource(R.drawable.ic_search), "Find", tint = if (findOpen) Term.prompt else Term.fg, modifier = Modifier.size(20.dp))
            }
            EditorButton("?") { referenceOpen = true }
            EditorButton("save") { save() }
            EditorButton("run", onLongClick = { runWithOpen = true }) { run() }
            EditorButton("close") { close() }
        }
        if (findOpen) {
            FindBar(
                query = query,
                onQuery = { query = it },
                replacement = replacement,
                onReplacement = { replacement = it },
                replaceOpen = replaceOpen,
                onToggleReplace = { replaceOpen = !replaceOpen },
                ignoreCase = ignoreCase,
                onToggleCase = { ignoreCase = !ignoreCase },
                count = when {
                    query.text.isEmpty() -> ""
                    matches.size == 0 -> "0/0"
                    else -> "${current + 1}/${matches.size}${if (matches.capped) "+" else ""}"
                },
                noMatch = query.text.isNotEmpty() && matches.size == 0 && lastSearch != null,
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
                    value = value.copy(selection = TextRange(value.text.length))
                    focusEditor()
                }
                .verticalScroll(vScroll)
                .padding(vertical = 12.dp)
        ) {
            Row {
                // Line numbers share the field's style, so they stay aligned while scrolling.
                Text(
                    numbers,
                    style = Term.code.copy(color = Term.dim, textAlign = TextAlign.End),
                    modifier = Modifier.widthIn(min = 28.dp).padding(start = 6.dp, end = 10.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .onSizeChanged { viewportWidth = it.width }
                        .horizontalScroll(hScroll)
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = ::edit,
                        textStyle = Term.code,
                        // No caret from the field: it draws its caret with the text, so each blink drew
                        // the whole script again. [EditorCaret] draws it over the text instead.
                        cursorBrush = SolidColor(Color.Unspecified),
                        visualTransformation = highlighter,
                        onTextLayout = { layout = it },
                        decorationBox = { field ->
                            // The field keeps its minimum width, as without a box around it.
                            Box(propagateMinConstraints = true) {
                                field()
                                EditorCaret(value, { layout }, editorFocused, Term.prompt, Modifier.matchParentSize())
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            capitalization = KeyboardCapitalization.None,
                            // Plain text (not password) so non-Latin layouts and voice input work in code.
                            keyboardType = KeyboardType.Text,
                        ),
                        modifier = Modifier
                            .widthIn(min = 600.dp)
                            .padding(end = 12.dp)
                            .focusRequester(focus)
                            .onFocusChanged { editorFocused = it.isFocused }
                            .testTag("editor")
                            .onPreviewKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && e.key == Key.Tab) {
                                    val first = suggestions.firstOrNull()
                                    if (first != null && !e.isShiftPressed) accept(first) else insert("  ")
                                    true
                                } else false
                            },
                    )
                }
            }
        }
        output?.let { lines ->
            RunOutput(lines, maxHeight = with(density) { (viewportHeight * 0.4f).toDp() }) { output = null }
        }
        val shown = message
        if (shown != null) {
            Text(
                shown.text,
                style = Term.code.copy(
                    color = when (shown.kind) {
                        EditorMessage.Kind.OK -> Term.prompt
                        EditorMessage.Kind.ERROR -> Term.error
                        EditorMessage.Kind.BUSY -> Term.fg
                        EditorMessage.Kind.INFO -> Term.dim
                    },
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Term.surface)
                    // A failing line: go there. Anything else: tap to dismiss.
                    .clickable { if (shown.line != null) gotoLine(shown.line) else message = null }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("message"),
            )
        } else if (inlineDoc != null) {
            Text(
                "${inlineDoc.signature}  ${inlineDoc.doc}",
                style = Term.code.copy(color = Term.dim, fontSize = Term.code.fontSize * 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Term.bg)
                    .padding(horizontal = 12.dp, vertical = 3.dp),
            )
        }
        if (suggestions.isNotEmpty()) {
            LazyRow(Modifier.fillMaxWidth().background(Term.surface.copy(alpha = 0.6f)).testTag("completions")) {
                items(suggestions.size) { i ->
                    val d = suggestions[i]
                    val label = d.member + when (d.kind) {
                        ApiDoc.Kind.FUNCTION -> "()"
                        ApiDoc.Kind.NAMESPACE -> "."
                        else -> ""
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .widthIn(min = 48.dp)
                            // Keep the editor (and the soft keyboard) focused while tapping.
                            .clickable { accept(d); runCatching { focus.requestFocus() } }
                            .padding(horizontal = 12.dp),
                    ) {
                        Text(
                            label,
                            style = Term.code.copy(
                                color = if (d.kind == ApiDoc.Kind.KEYWORD) Term.prompt else Term.fg,
                                fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                    }
                }
            }
        }
        val symbols = listOf("↶", "↷", "  ", "(", ")", "{", "}", "[", "]", "\"", "'", "=", ".", ",", ":", ";", "#", "-", "~", "<", ">", "Ln")
        // Read here, not inside the row's items, so a change re-dims the arrows.
        val canUndo = history.canUndo
        val canRedo = history.canRedo
        LazyRow(Modifier.fillMaxWidth().background(Term.surface)) {
            items(symbols) { s ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .widthIn(min = 48.dp)
                        .clickable {
                            when (s) {
                                "↶" -> undo()
                                "↷" -> redo()
                                "Ln" -> gotoOpen = true
                                else -> insert(s)
                            }
                        }
                        .padding(horizontal = 12.dp),
                ) {
                    val idle = (s == "↶" && !canUndo) || (s == "↷" && !canRedo)
                    Text(if (s == "  ") "⇥" else s, style = Term.code.copy(color = if (idle) Term.dim else Term.fg, fontWeight = FontWeight.Bold))
                }
            }
        }
    }

    if (runWithOpen) {
        RunWithDialog(value.text, onDismiss = { runWithOpen = false }) { args, event ->
            runWithOpen = false
            run(args, event)
        }
    }
    if (gotoOpen) {
        GotoLineDialog(lineCount, onDismiss = { gotoOpen = false }) { line ->
            gotoOpen = false
            gotoLine(line)
        }
    }
    if (referenceOpen) {
        ApiReferenceSheet(
            script.lang,
            onDismiss = { referenceOpen = false },
            onPick = { doc ->
                referenceOpen = false
                insert(doc.name + if (doc.kind == ApiDoc.Kind.FUNCTION) "(" else "")
                focusEditor()
            },
            onDirective = { line ->
                referenceOpen = false
                // A directive is a line of its own: it goes above the line the cursor is on, the
                // cursor after it to type what follows (or on the line below when nothing does).
                val text = value.text
                val start = if (value.selection.min == 0) 0 else text.lastIndexOf('\n', value.selection.min - 1) + 1
                val body = if (line.endsWith("\n")) line else line + "\n"
                val caret = start + if (line.endsWith("\n")) body.length else line.length
                edit(TextFieldValue(text.substring(0, start) + body + text.substring(start), TextRange(caret)))
                focusEditor()
            },
        )
    }
}

/** A run's lines under the code, as the log shows them in plain text, errors red; ✕ closes it. */
@Composable
private fun RunOutput(lines: List<com.mtwabpin.kolahal.data.LogEntry>, maxHeight: androidx.compose.ui.unit.Dp, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Term.surface.copy(alpha = 0.6f)).testTag("run-output")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
            Text(
                if (lines.isEmpty()) "output · none" else "output · ${lines.size} line${if (lines.size == 1) "" else "s"}",
                style = Term.code.copy(color = Term.dim),
                modifier = Modifier.weight(1f),
            )
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp).clickable(onClick = onClose)) {
                Text("✕", style = Term.code.copy(color = Term.dim))
            }
        }
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)) {
            lines.forEach { e ->
                Text(
                    e.shownText,
                    style = Term.code.copy(color = if (e.kind == com.mtwabpin.kolahal.data.LogKind.ERROR) Term.error else Term.fg),
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * The most of a script's text Android keeps for the editor while Kolahal is in the background. A
 * screen's kept state must fit in about 1 MB, two bytes a character, or the app stops as it goes,
 * and the editor keeps its text twice: as typed, and as last saved.
 */
internal const val KEPT_TEXT_MAX = 100_000

/**
 * The editor's text as Android keeps it in the background: past [KEPT_TEXT_MAX] only the cursor,
 * and [stored], the script as saved, comes back instead. The editor saves as it goes to the
 * background, so that's the text as it was.
 */
internal fun keptText(stored: String) = Saver<TextFieldValue, Any>(
    save = { arrayListOf(it.text.takeIf { text -> text.length <= KEPT_TEXT_MAX } ?: false, it.selection.start, it.selection.end) },
    restore = { kept ->
        val (text, start, end) = kept as List<*>
        val back = text as? String ?: stored
        TextFieldValue(back, TextRange((start as Int).coerceIn(0, back.length), (end as Int).coerceIn(0, back.length)))
    },
)

/** The last saved copy, kept the same way: past [KEPT_TEXT_MAX], [stored] comes back. */
internal fun keptSaved(stored: String) = Saver<String, Any>(
    save = { it.takeIf { text -> text.length <= KEPT_TEXT_MAX } ?: false },
    restore = { it as? String ?: stored },
)

/** Cursor at the start of [line] (1-based), or at the end of the text. */
internal fun selectionAtLine(text: String, line: Int?): TextRange =
    if (line == null || line < 1) TextRange(text.length) else TextRange(EditorLogic.lineStart(text, line))

/** One line of feedback above the symbol bar; [line] makes it a jump to that line. */
private class EditorMessage(val text: String, val kind: Kind, val line: Int? = null) {
    enum class Kind { INFO, OK, BUSY, ERROR }
}

/**
 * The editor's caret, in a layer of its own over the text, so a blink draws the caret alone and not
 * the whole script. Drawn as the text field drew its own: 2 dp wide at the cursor, shown at once
 * after a change and then blinking every 500 ms, while the editor and its window have focus and
 * nothing is selected.
 */
@Composable
private fun EditorCaret(value: TextFieldValue, layout: () -> TextLayoutResult?, focused: Boolean, color: Color, modifier: Modifier) {
    val blinks = LocalCursorBlinkEnabled.current
    val show = LocalWindowInfo.current.isWindowFocused && focused && value.selection.collapsed
    var alpha by remember { mutableFloatStateOf(0f) }
    if (show) {
        LaunchedEffect(value.text, value.selection, blinks) {
            try {
                alpha = 1f
                if (!blinks) awaitCancellation()
                while (true) {
                    delay(500)
                    alpha = 0f
                    delay(500)
                    alpha = 1f
                }
            } finally {
                alpha = 0f
            }
        }
    }
    Box(
        modifier.graphicsLayer().drawBehind {
            val a = alpha
            if (!show || a == 0f) return@drawBehind
            val result = layout() ?: return@drawBehind
            val rect = result.getCursorRect(value.selection.start.coerceIn(0, result.layoutInput.text.length))
            val width = floor(2.dp.toPx()).coerceAtLeast(1f)
            val x = (rect.left + width / 2)
                .coerceAtMost(size.width - width / 2)
                .coerceAtLeast(width / 2)
                // An odd width is drawn in the middle of a pixel, so it isn't blurred.
                .let { if (width.toInt() % 2 == 1) floor(it) + 0.5f else round(it) }
            drawLine(color, Offset(x, rect.top), Offset(x, rect.bottom), strokeWidth = width, alpha = a)
        },
    )
}
