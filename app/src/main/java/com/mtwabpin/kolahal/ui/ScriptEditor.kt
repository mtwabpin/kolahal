package com.mtwabpin.kolahal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.shownText
import com.mtwabpin.kolahal.script.ApiDoc
import com.mtwabpin.kolahal.script.ScriptWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Monospace editor for scripts. It has line numbers, light syntax colouring, undo/redo,
 * find/replace, API completion and inline docs. The editing itself is [CodeEditor]'s; this adds
 * what only a script has.
 * - On save, it reports what changed beyond the code (for example, a timer set by an `@every`
 *   line) and what [com.mtwabpin.kolahal.script.ScriptCheck] finds.
 * - A run's lines show under the code ([runLines]). A long press on run runs the script with
 *   args or as an event ([RunWithDialog]).
 * - When opened from an error, [jumpToLine] is selected and briefly highlighted.
 * - A message line above the symbol bar says what the last button did: saved, the run's result
 *   (from [lastRun] / [lastStatus]; tap it to go to a failing line), or how many replacements were made.
 * - [onSave] reports back with null or the failure. [onClose] gets the text if it had unsaved changes.
 *
 * Keys:
 * - Ctrl+S saves; Ctrl+R saves and runs.
 * - Ctrl+Z / Ctrl+Shift+Z undo/redo.
 * - Ctrl+F finds; Ctrl+H replaces.
 * - F3 / Shift+F3 go to the next/previous match.
 * - Ctrl+G goes to a line.
 * - Tab accepts the first suggestion.
 * - Esc closes the find bar; otherwise it saves and closes the editor (Back does the same).
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
    val state = rememberSaveable(script.id, saver = EditorState.saver(script.code, jumpToLine)) {
        EditorState(script.code, TextFieldValue(script.code, selectionAtLine(script.code, jumpToLine)), flashLine = jumpToLine)
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    var referenceOpen by remember { mutableStateOf(false) }
    var runWithOpen by remember { mutableStateOf(false) }
    // The last run's lines. They stay under the code until closed or until the next run.
    var output by remember(script.id) { mutableStateOf<List<com.mtwabpin.kolahal.data.LogEntry>?>(null) }

    /** Counts saves, so a slow check's result can't show after a later save's result. */
    var saves by remember(script.id) { mutableIntStateOf(0) }
    // For a run, the message waits for the recorded result.
    var runSince by remember(script.id) { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        // If a run never reports (the worker didn't start), stop waiting after the time limit.
        if (m.kind == EditorMessage.Kind.BUSY) {
            delay((ScriptWorker.TIMEOUT_MS + 15_000).milliseconds)
            if (state.message === m) { state.message = null; runSince = null }
        }
    }
    LaunchedEffect(lastRun, lastStatus) {
        val since = runSince ?: return@LaunchedEffect
        if (lastRun == null || lastRun < since) return@LaunchedEffect
        runSince = null
        val r = EditorLogic.runResult(lastStatus, ScriptWorker.TIMEOUT_MS / 1000)
        // The lines are written when the run ends, and its result just after, so they are ready here.
        val lines = runLines(since)
        output = lines
        val text = if (lines.isEmpty() || r.failed) r.text else r.text.replace("its output is in the log", "${lines.size} line${if (lines.size == 1) "" else "s"} below")
        state.message = EditorMessage(text, if (r.failed) EditorMessage.Kind.ERROR else EditorMessage.Kind.OK, r.line)
    }

    /** "Saved", what saving changed, and the first issue [com.mtwabpin.kolahal.script.ScriptCheck] finds (a tap goes to its line). */
    fun savedMessage(text: String, changed: String?): EditorMessage {
        val head = listOfNotNull("Saved", changed).joinToString(" · ")
        val issues = com.mtwabpin.kolahal.script.ScriptCheck.check(text, script.lang)
        val first = issues.firstOrNull() ?: return EditorMessage(head, EditorMessage.Kind.OK)
        val more = if (issues.size > 1) " (and ${issues.size - 1} more)" else ""
        return EditorMessage("$head · line ${first.line}: ${first.text}$more", EditorMessage.Kind.ERROR, first.line)
    }

    /** With [quiet], it saves without a message (used when leaving the app). */
    fun save(quiet: Boolean = false) {
        if (!state.dirty) {
            if (!quiet) state.message = EditorMessage("No changes to save", EditorMessage.Kind.INFO)
            return
        }
        val text = state.value.text
        onSave(text) { failure, changed ->
            if (failure == null) {
                state.saved = text
                // The check reads the whole script, so it runs off the main thread. Only the latest
                // save's result shows. It does not replace a message that came in the meantime; it is
                // fine if that message has since gone (timed out or tapped).
                if (!quiet) {
                    val before = state.message
                    val save = ++saves
                    scope.launch {
                        val saidSaved = withContext(Dispatchers.Default) { savedMessage(text, changed) }
                        if (save == saves && (state.message === before || state.message == null)) state.message = saidSaved
                    }
                }
            } else {
                state.message = EditorMessage("Couldn't save: ${failure.message ?: failure.javaClass.simpleName}", EditorMessage.Kind.ERROR)
            }
        }
    }

    fun run(args: List<String> = emptyList(), event: Map<String, Any?>? = null) {
        // Running saves the text first. It counts as saved only after the save succeeds, so a failed save keeps the edits.
        val text = state.value.text
        runSince = System.currentTimeMillis()
        output = null
        state.message = EditorMessage("Saving…", EditorMessage.Kind.BUSY)
        onRun(text, args, event) { failure, changed ->
            if (failure == null) {
                state.saved = text
                state.message = EditorMessage(listOfNotNull("Saved", changed, "running…").joinToString(" · "), EditorMessage.Kind.BUSY)
            } else {
                // It didn't run. A later run (for example, by its timer) is not the result of this one.
                runSince = null
                state.message = EditorMessage("Couldn't save: ${failure.message ?: failure.javaClass.simpleName}", EditorMessage.Kind.ERROR)
            }
        }
    }

    fun close() {
        onClose(state.value.text.takeIf { state.dirty })
    }

    fun focusEditor() {
        runCatching { state.focus.requestFocus() }
        keyboard?.show()
    }

    // ---- completion / docs ------------------------------------------------------------------

    val value = state.value
    val editorFocused = state.focused
    val suggestions = remember(value.text, value.selection, editorFocused, script.lang) {
        if (!editorFocused || !value.selection.collapsed) emptyList()
        else EditorLogic.completions(value.text, value.selection.end, script.lang)
    }
    val inlineDoc = remember(value.text, value.selection.end, editorFocused, script.lang) {
        if (!editorFocused) null
        else EditorLogic.docAt(value.text, value.selection.end, script.lang)?.takeIf { it.kind != ApiDoc.Kind.KEYWORD }
    }

    fun accept(doc: ApiDoc) {
        val done = EditorLogic.accept(state.value.text, state.value.selection.end, doc) ?: return
        state.edit(TextFieldValue(done.text, TextRange(done.cursor)))
    }

    BackHandler { close() }
    // Leaving the app (or the system killing the process in the background) must not lose edits.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { save(quiet = true) }

    CodeEditor(
        state = state,
        title = "${script.name}.${script.lang.ext}",
        lang = script.lang,
        startLine = jumpToLine,
        onKey = { e ->
            when {
                e.isCtrlPressed && e.key == Key.S -> { save(); true }
                e.isCtrlPressed && e.isShiftPressed && e.key == Key.R -> { runWithOpen = true; true }
                e.isCtrlPressed && e.key == Key.R -> { run(); true }
                else -> false
            }
        },
        onTab = { shift ->
            val first = suggestions.firstOrNull()
            if (first != null && !shift) { accept(first); true } else false
        },
        onEscape = ::close,
        buttons = {
            EditorButton("?") { referenceOpen = true }
            EditorButton("save") { save() }
            EditorButton("run", onLongClick = { runWithOpen = true }) { run() }
            EditorButton("close") { close() }
        },
        belowCode = { maxHeight ->
            output?.let { lines -> RunOutput(lines, maxHeight) { output = null } }
        },
        hint = {
            if (inlineDoc != null) {
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
        },
        aboveSymbols = {
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
                                .clickable { accept(d); runCatching { state.focus.requestFocus() } }
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
        },
    )

    if (runWithOpen) {
        RunWithDialog(state.value.text, onDismiss = { runWithOpen = false }) { args, event ->
            runWithOpen = false
            run(args, event)
        }
    }
    if (referenceOpen) {
        ApiReferenceSheet(
            script.lang,
            onDismiss = { referenceOpen = false },
            onPick = { doc ->
                referenceOpen = false
                state.insert(doc.name + if (doc.kind == ApiDoc.Kind.FUNCTION) "(" else "")
                focusEditor()
            },
            onDirective = { line ->
                referenceOpen = false
                // A directive is a line of its own. It goes above the line the cursor is on.
                val done = EditorLogic.insertDirective(state.value.text, state.value.selection.min, line)
                state.edit(TextFieldValue(done.text, TextRange(done.cursor)))
                focusEditor()
            },
        )
    }
}

/** A run's lines under the code, in plain text as the log shows them, with errors in red. ✕ closes it. */
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
