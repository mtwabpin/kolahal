/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.input.TextFieldValue
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.terminal.commands.ShaderCommands
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The editor for a shader's code (AGSL): shader [name], opened with [code]. The editing is
 * [CodeEditor]'s, as in the script editor; this adds what a shader needs:
 * - Save writes the user's file through [onSave], then shows what `shader check` finds
 *   ([shaderSavedMessage]): the compiler's error at its line (a tap goes there), or what keeps the
 *   shader from being used. A shader that doesn't pass is saved all the same, as an imported file is.
 * - For one that comes with Kolahal ([bundled]), a note says that saving makes the user's own copy,
 *   which is used instead of it.
 * - Close, Back and Esc save unsaved changes through [onSave] first, then call [onClose] with `saved` true
 *   and what the check found. When the save fails, the editor stays open with the text and says why, as
 *   Save does. Leaving the app saves it without a message.
 * - It takes up to 64 KB of text ([ShaderMeta.MAX_BYTES] characters), the most a shader's file has.
 *
 * There is no Run, API reference or completion: those are a script's.
 *
 * Keys:
 * - Ctrl+S saves.
 * - Esc closes the find bar; otherwise it closes the editor (Back does the same).
 * - The editing keys are [CodeEditor]'s.
 */
@Composable
fun ShaderEditor(
    name: String,
    code: String,
    bundled: Boolean,
    onSave: suspend (String) -> Shaders.Check?,
    onClose: (saved: Boolean, check: Shaders.Check?) -> Unit,
) {
    val limit = EditorLimit(ShaderMeta.MAX_BYTES, ShaderMeta.MAX_BYTES)
    val state = rememberSaveable(name, saver = EditorState.saver(code, null, limit)) {
        EditorState(code, TextFieldValue(code, selectionAtLine(code, null)), limit = limit)
    }
    val scope = rememberCoroutineScope()

    /** Counts saves, so a slow save's result can't show after a later save's result. */
    var saves by remember(name) { mutableIntStateOf(0) }

    LaunchedEffect(name) {
        if (bundled && state.message == null) state.message = EditorMessage(BUNDLED_NOTE, EditorMessage.Kind.NOTE)
    }

    /**
     * With [quiet], it saves without a message (used when leaving the app). [then] runs after a save that
     * succeeded, with what the check found.
     */
    fun save(quiet: Boolean = false, then: ((Shaders.Check?) -> Unit)? = null) {
        if (!state.dirty) {
            if (!quiet) state.message = EditorMessage("No changes to save", EditorMessage.Kind.INFO)
            return
        }
        val text = state.value.text
        val save = ++saves
        scope.launch {
            // Saving writes a file and compiles the shader: onSave does it off the main thread.
            val check = try {
                onSave(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.message = EditorMessage(
                    "Couldn't save: ${e.message ?: e.javaClass.simpleName}",
                    EditorMessage.Kind.ERROR
                )
                return@launch
            }
            state.saved = text
            if (then != null) return@launch then(check)
            if (!quiet && save == saves) state.message = shaderSavedMessage(check)
        }
    }

    /** Saves the changes first, if there are any; it closes only when they are saved. */
    fun close() {
        if (state.dirty) save(then = { check -> onClose(true, check) }) else onClose(false, null)
    }

    BackHandler { close() }
    // Leaving the app (or the system killing the process in the background) must not lose edits.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) {
        save(quiet = true)
    }

    CodeEditor(
        state = state,
        title = "$name.${ShaderCatalog.EXT}",
        lang = Syntax.AGSL,
        startLine = null,
        onKey = { e ->
            if (e.isCtrlPressed && e.key == Key.S) {
                save(); true
            } else false
        },
        onTab = { false },
        onEscape = ::close,
        buttons = {
            EditorIconButton(R.drawable.ic_save, "Save") { save() }
            EditorIconButton(R.drawable.ic_close, "Close") { close() }
        },
    )
}

/** What the editor says when it opens a shader that comes with Kolahal. */
internal const val BUNDLED_NOTE = "This shader comes with Kolahal: when you save, your copy is used instead"

/**
 * What the message line says after a save, from what [check] found (null: the file can't be read):
 * - "Saved · compiles" when nothing is wrong;
 * - otherwise, in red, why it can't be used, at the compiler's line when it gives one (a tap goes there),
 *   or the first header line that is left out; "(and 1 more)" when there is more.
 */
internal fun shaderSavedMessage(check: Shaders.Check?): EditorMessage {
    if (check == null) return EditorMessage("Saved · it can't be read", EditorMessage.Kind.ERROR)
    val problem = check.problem
    val found = listOfNotNull(problem?.let { "can't be used: $it" }) + check.warnings
    val first = found.firstOrNull()
        ?: return EditorMessage("Saved · ${ShaderCommands.checkLines(check).first()}", EditorMessage.Kind.OK)
    val head = if (problem != null) "Saved" else "Saved · ${ShaderCommands.checkLines(check).first()}"
    val more = if (found.size > 1) " (and ${found.size - 1} more)" else ""
    return EditorMessage("$head · $first$more", EditorMessage.Kind.ERROR, check.line.takeIf { problem != null })
}
