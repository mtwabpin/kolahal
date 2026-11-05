package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ApiDoc
import com.mtwabpin.kolahal.script.ApiDocs

@Composable
internal fun EditorButton(label: String, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Text(
        "[$label]",
        style = Term.code.copy(color = Term.fg),
        modifier = Modifier
            .then(if (onLongClick == null) Modifier.clickable(onClick = onClick) else Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick))
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    )
}

/**
 * The editor's Run with (a long press on run): the words to run it with, as `args` when it runs as
 * a command, and an event to run it as, with made-up values ([com.mtwabpin.kolahal.script.ScriptMeta.sampleEvent]): its `@on`
 * events and `@at` times, or none.
 */
@Composable
internal fun RunWithDialog(code: String, onDismiss: () -> Unit, onRun: (List<String>, Map<String, Any?>?) -> Unit) {
    var words by remember { mutableStateOf(TextFieldValue("")) }
    val events = remember(code) {
        com.mtwabpin.kolahal.script.ScriptMeta.events(code).map { it to null } +
            com.mtwabpin.kolahal.script.ScriptMeta.at(code).map { "at" to it }
    }
    var chosen by remember { mutableStateOf<Pair<String, String?>?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Run with", style = Term.code.copy(color = Term.prompt, fontWeight = FontWeight.Bold)) },
        containerColor = Term.surface,
        text = {
            Column {
                TextField(
                    shape = TextFieldDefaults.roundedShape,
                    colors = fieldColors(),
                    value = words,
                    onValueChange = { words = it },
                    singleLine = true,
                    label = { Text("args") },
                    placeholder = { Text("words after its name", style = Term.code.copy(color = Term.dim)) },
                    textStyle = Term.code.copy(color = Term.fg),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().testTag("run-args"),
                )
                if (events.isNotEmpty()) {
                    Text("simulate event", style = Term.code.copy(color = Term.dim), modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    (listOf<Pair<String, String?>?>(null) + events).forEach { e ->
                        Text(
                            (if (e == chosen) "● " else "○ ") + when {
                                e == null -> "none"
                                e.second != null -> "at ${e.second}"
                                else -> e.first
                            },
                            style = Term.code.copy(color = if (e == chosen) Term.prompt else Term.fg),
                            modifier = Modifier.fillMaxWidth().clickable { chosen = e }.heightIn(min = 40.dp).padding(vertical = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                val args = com.mtwabpin.kolahal.terminal.CommandParsing.tokenize(words.text)
                onRun(args, chosen?.let { com.mtwabpin.kolahal.script.ScriptMeta.sampleEvent(it.first, it.second) })
            }) { Text("run", style = Term.code.copy(color = Term.prompt)) }
        },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("cancel", style = Term.code.copy(color = Term.fg)) } },
    )
}

@Composable
private fun BarButton(label: String, color: Color = Term.fg, description: String? = null, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clickable(onClickLabel = description, onClick = onClick)
            .padding(horizontal = 8.dp),
    ) {
        Text(label, style = Term.code.copy(color = color, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun BarField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    imeAction: ImeAction,
    onAction: () -> Unit,
    onKey: (KeyEvent) -> Boolean,
    modifier: Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = Term.code,
        cursorBrush = SolidColor(Term.prompt),
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Text,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onSearch = { onAction() }, onDone = { onAction() }),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.heightIn(min = 48.dp)) {
                if (value.text.isEmpty()) Text(placeholder, style = Term.code.copy(color = Term.dim))
                inner()
            }
        },
        modifier = modifier.onPreviewKeyEvent(onKey),
    )
}

private fun KeyEvent.isEnter() = key == Key.Enter || key == Key.NumPadEnter

@Composable
internal fun FindBar(
    query: TextFieldValue,
    onQuery: (TextFieldValue) -> Unit,
    replacement: TextFieldValue,
    onReplacement: (TextFieldValue) -> Unit,
    replaceOpen: Boolean,
    onToggleReplace: () -> Unit,
    ignoreCase: Boolean,
    onToggleCase: () -> Unit,
    count: String,
    noMatch: Boolean,
    focusRequester: FocusRequester,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Term.surface.copy(alpha = 0.7f)).testTag("findbar")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BarButton("Aa", if (ignoreCase) Term.dim else Term.prompt, if (ignoreCase) "Match case" else "Ignore case", onToggleCase)
            BarField(
                value = query,
                onValueChange = onQuery,
                placeholder = "find",
                imeAction = ImeAction.Search,
                onAction = onNext,
                onKey = { e ->
                    if (e.type == KeyEventType.KeyDown && e.isEnter()) {
                        if (e.isShiftPressed) onPrev() else onNext()
                        true
                    } else e.isEnter() // swallow Enter key-ups too
                },
                modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag("find"),
            )
            Text(count, style = Term.code.copy(color = if (noMatch) Term.error else Term.dim), modifier = Modifier.padding(horizontal = 4.dp))
            BarButton("↑", description = "Previous match", onClick = onPrev)
            BarButton("↓", description = "Next match", onClick = onNext)
            BarButton("⇄", if (replaceOpen) Term.prompt else Term.fg, "Replace", onToggleReplace)
            BarButton("×", description = "Close find", onClick = onClose)
        }
        if (replaceOpen) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BarField(
                    value = replacement,
                    onValueChange = onReplacement,
                    placeholder = "replace with",
                    imeAction = ImeAction.Done,
                    onAction = onReplace,
                    onKey = { e ->
                        if (e.type == KeyEventType.KeyDown && e.isEnter()) {
                            onReplace()
                            true
                        } else e.isEnter()
                    },
                    modifier = Modifier.weight(1f).padding(start = 12.dp).testTag("replace"),
                )
                EditorButton("replace", onClick = onReplace)
                EditorButton("all", onClick = onReplaceAll)
            }
        }
    }
}

@Composable
internal fun GotoLineDialog(lineCount: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val line = text.trim().toIntOrNull()?.takeIf { it in 1..lineCount }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // The GUI theme's full colour scheme: the editor's own sets only a few roles, and a dialog drawn
    // with the rest is unreadable on a light theme.
    GuiTheme { AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to line") },
        text = {
            TextField(
                shape = TextFieldDefaults.roundedShape,
                colors = fieldColors(),
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(9) },
                label = { Text("Line (1–$lineCount)") },
                singleLine = true,
                isError = text.isNotEmpty() && line == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { line?.let(onGo) }),
                modifier = Modifier
                    .focusRequester(focus)
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.isEnter()) {
                            line?.let(onGo)
                            true
                        } else false
                    },
            )
        },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { line?.let(onGo) }, enabled = line != null) { Text("Go") } },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Cancel") } },
    ) }
}

/**
 * Full API reference for the script's language, then its `@` lines (directives); tapping an entry
 * inserts it at the cursor, a directive as a whole comment line ([onDirective]).
 */
@Composable
internal fun ApiReferenceSheet(lang: ScriptLang, onDismiss: () -> Unit, onPick: (ApiDoc) -> Unit, onDirective: (String) -> Unit) {
    val docs = remember(lang) { ApiDocs.forLang(lang).filter { it.kind != ApiDoc.Kind.KEYWORD } }
    TermSheet(onDismiss) {
        LazyColumn(Modifier.navigationBarsPadding().padding(vertical = 8.dp).testTag("apiref")) {
            item {
                Text(
                    "API reference · ${if (lang == ScriptLang.LUA) "Lua" else "JavaScript"}",
                    style = Term.code.copy(color = Term.prompt, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(docs) { d ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { onPick(d) }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(d.signature, style = Term.code.copy(color = if (d.kind == ApiDoc.Kind.NAMESPACE) Term.info else Term.fg))
                    Text(d.doc, style = Term.code.copy(color = Term.dim, fontSize = Term.code.fontSize * 0.85f))
                }
            }
            item {
                Text(
                    "Directives · comment lines at the top",
                    style = Term.code.copy(color = Term.prompt, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                )
            }
            items(ApiDocs.directives) { d ->
                val comment = if (lang == ScriptLang.LUA) "--" else "//"
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { onDirective("$comment @${d.name}" + if (d.args.isEmpty()) "\n" else " ") }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text("$comment @${d.name} ${d.args}".trimEnd(), style = Term.code.copy(color = Term.fg))
                    Text(d.doc, style = Term.code.copy(color = Term.dim, fontSize = Term.code.fontSize * 0.85f))
                }
            }
            item {
                Text(
                    "Keywords: " + ApiDocs.keywords(lang).joinToString(" "),
                    style = Term.code.copy(color = Term.dim, fontSize = Term.code.fontSize * 0.85f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
