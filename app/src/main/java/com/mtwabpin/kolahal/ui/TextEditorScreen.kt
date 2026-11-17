package com.mtwabpin.kolahal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.terminal.LogAction
import com.mtwabpin.kolahal.ui.TextEditorViewModel.Phase

/**
 * The text editor's screen, for any text file on the phone. The editing is [CodeEditor]'s, as in
 * the script editor. This adds what a file needs:
 * - save, and a menu: Save as, open or make another file, wrap lines, discard changes;
 * - close, which asks first when there are unsaved changes;
 * - a question before saving over a file that another app changed;
 * - a page that says why a file can't be opened.
 *
 * [onOpen] and [onNew] ask the activity for a file picker. [onLeave] leaves the editor with the
 * file still open, as Home does.
 *
 * Keys:
 * - Ctrl+S saves; Ctrl+Shift+S saves as another file.
 * - Ctrl+O opens a file; Ctrl+N makes a new one. Each opens in a window of its own.
 * - Ctrl+W closes the file.
 * - Esc closes the find bar; otherwise it leaves the editor with the file still open (Back does the same).
 * - The editing keys are [CodeEditor]'s.
 */
@Composable
internal fun TextEditorScreen(vm: TextEditorViewModel, onOpen: () -> Unit, onNew: () -> Unit, onLeave: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Term.bg)) {
        when (val phase = vm.phase) {
            Phase.Loading -> EditorNotice("Opening ${vm.name}…", null) {}
            is Phase.Failed -> EditorNotice(phase.title, phase.why) {
                EditorTextButton("Open another file") { onOpen() }
                EditorTextButton("Close") { vm.close() }
            }
            is Phase.Editing -> {
                // Before the editor's own handler, so that Back closes an open find bar first.
                BackHandler(onBack = onLeave)
                CodeEditor(
                    state = phase.state,
                    title = vm.name,
                    lang = EditorLogic.syntaxFor(vm.name),
                    startLine = null,
                    onKey = { e ->
                        when {
                            e.isCtrlPressed && e.isShiftPressed && e.key == Key.S -> { vm.askSaveAs(); true }
                            e.isCtrlPressed && e.key == Key.S -> { vm.save(); true }
                            e.isCtrlPressed && e.key == Key.O -> { onOpen(); true }
                            e.isCtrlPressed && e.key == Key.N -> { onNew(); true }
                            e.isCtrlPressed && e.key == Key.W -> { vm.close(); true }
                            else -> false
                        }
                    },
                    onTab = { false },
                    onEscape = onLeave,
                    buttons = {
                        EditorIconButton(R.drawable.ic_save, "Save") { vm.save() }
                        EditorIconButton(R.drawable.ic_more_vert, "More") { menuOpen = true }
                        EditorIconButton(R.drawable.ic_close, "Close") { vm.close() }
                    },
                    wrap = vm.wrap,
                    // A file opens to be read first: the keyboard would cover half of it.
                    focusOnOpen = false,
                )
            }
        }
    }

    if (menuOpen) {
        val dirty = (vm.phase as? Phase.Editing)?.state?.dirty == true
        ActionSheet(
            ActionMenu(
                title = vm.name,
                actions = listOfNotNull(
                    LogAction("Save as…") { vm.askSaveAs() },
                    LogAction("Open from its folder…") { vm.askFromFolder() }.takeIf { vm.reopenHelps },
                    LogAction("Open a file…") { onOpen() },
                    LogAction("New file…") { onNew() },
                    LogAction(if (vm.wrap) "Don't wrap lines" else "Wrap lines") { vm.toggleWrap() },
                    LogAction("Discard changes", destructive = true) { vm.revert() }.takeIf { dirty },
                ),
                entryId = null,
                systemFont = true,
                detail = vm.details(),
            ),
            onDismiss = { menuOpen = false },
        )
    }
    if (vm.askOverwrite) {
        EditorQuestion(
            title = "The file has changed",
            text = "${vm.name} was changed by another app after you opened it. Overwrite it with your text?",
            confirm = "Overwrite" to { vm.save(overwrite = true) },
            other = "Save as…" to vm::askSaveAs,
            onCancel = vm::keepTheirs,
        )
    }
    if (vm.askClose) {
        EditorQuestion(
            title = "Save changes?",
            text = "${vm.name} has changes that are not saved.",
            confirm = "Save" to vm::saveAndClose,
            other = "Discard" to vm::discardAndClose,
            onCancel = vm::stay,
        )
    }
}

/** A page in place of the editor: [title], [why] under it, and [buttons]. */
@Composable
private fun EditorNotice(title: String, why: String?, buttons: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().cutoutSafePadding().navigationBarsPadding().padding(vertical = 16.dp).testTag("editor-notice")) {
        Text(title, style = Term.phoneText.copy(color = Term.prompt, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 12.dp))
        if (why != null) Text(why, style = Term.phoneText, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp))
        // The buttons bring their own space at the sides, so their text lines up with the text above.
        Row(Modifier.padding(top = 8.dp)) { buttons() }
    }
}

/**
 * A question with two answers and Cancel. It uses the GUI theme's full colour scheme, like the
 * editor's other dialogs.
 */
@Composable
private fun EditorQuestion(title: String, text: String, confirm: Pair<String, () -> Unit>, other: Pair<String, () -> Unit>, onCancel: () -> Unit) {
    GuiTheme {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = confirm.second) { Text(confirm.first) } },
            dismissButton = {
                Row {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = onCancel) { Text("Cancel") }
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = other.second) { Text(other.first) }
                }
            },
        )
    }
}
