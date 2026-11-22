/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * One line of feedback above the symbol bar. A tap on it:
 * - runs [action], if it has one ("… · tap to save as");
 * - otherwise jumps to [line], if it has one;
 * - otherwise dismisses it.
 */
internal class EditorMessage(
    val text: String,
    val kind: Kind,
    val line: Int? = null,
    val action: (() -> Unit)? = null
) {
    /**
     * - [INFO] and [OK] go away after a few seconds.
     * - [ERROR] and [NOTE] stay until the next message or a tap: something to read and act on.
     *   A note is not a failure (unsaved text came back, the file changed on disk).
     * - [BUSY] stays until the work it names is done. The screen that set it ends it.
     */
    enum class Kind { INFO, OK, BUSY, ERROR, NOTE }
}

/**
 * The most text an editor takes. A change that makes the text longer than [maxChars], or gives it
 * more than [maxLines] lines, is refused.
 */
internal class EditorLimit(val maxChars: Int, val maxLines: Int) {
    /** Why [text] is too much, or null. */
    fun over(text: String): String? = when {
        text.length > maxChars -> "The editor takes up to ${
            "%,d".format(
                java.util.Locale.ROOT,
                maxChars
            )
        } characters"

        text.count { it == '\n' } >= maxLines -> "The editor takes up to ${
            "%,d".format(
                java.util.Locale.ROOT,
                maxLines
            )
        } lines"

        else -> null
    }
}

/**
 * What an editor holds while a text is open. [CodeEditor] draws it and changes it. The screen
 * around the editor reads it to save, and writes [saved] and [message].
 * - [value]: the text, with the cursor or selection.
 * - [saved]: the text as last saved. The text is [dirty] when it differs.
 * - [history]: the undo steps. They start from [initial].
 * - [flashLine]: a line to highlight for a moment (1-based), after a jump to it.
 * - The find bar: whether it is open, what it looks for, and the matches found.
 * - [message]: what the last button did.
 * - [limit]: the most text it takes, or null for no limit. The script editor has none.
 */
@Stable
internal class EditorState(
    initial: String,
    value: TextFieldValue,
    saved: String = initial,
    flashLine: Int? = null,
    private val limit: EditorLimit? = null,
) {
    var value by mutableStateOf(value)
    var saved by mutableStateOf(saved)
    val dirty get() = value.text != saved
    val history = UndoHistory(initial)
    var flashLine by mutableStateOf(flashLine)

    /** Gives the code field the focus. */
    val focus = FocusRequester()

    /** Whether the code field has the focus. The find bar's fields do not count. */
    var focused by mutableStateOf(false)

    var findOpen by mutableStateOf(false)
    var replaceOpen by mutableStateOf(false)
    var query by mutableStateOf(TextFieldValue(""))
    var replacement by mutableStateOf(TextFieldValue(""))
    var ignoreCase by mutableStateOf(true)
    var matches by mutableStateOf(EditorLogic.Matches.EMPTY)

    /** The index of the match the cursor is on, or -1. */
    var current by mutableIntStateOf(-1)

    var gotoOpen by mutableStateOf(false)
    var message by mutableStateOf<EditorMessage?>(null)

    /**
     * Sets the text and cursor after a change by the user. A changed text becomes an undo step.
     * A change that makes the text grow past the [limit] is not made, and the message line says why.
     */
    fun edit(new: TextFieldValue) {
        if (new.text != value.text) {
            val why = limit?.takeIf { new.text.length > value.text.length }?.over(new.text)
            if (why != null) {
                message = EditorMessage(why, EditorMessage.Kind.ERROR)
                return
            }
            history.record(new.text)
        }
        value = new
    }

    /** Puts [s] in place of the selection, or at the cursor. */
    fun insert(s: String) {
        val sel = value.selection
        edit(
            TextFieldValue(
                value.text.replaceRange(sel.min, sel.max, s),
                TextRange(sel.min + s.length)
            )
        )
    }

    fun undo() = history.undo()
        ?.let { value = TextFieldValue(it, TextRange(it.length.coerceAtMost(value.selection.end))) }

    fun redo() = history.redo()
        ?.let { value = TextFieldValue(it, TextRange(it.length.coerceAtMost(value.selection.end))) }

    companion object {
        /**
         * Keeps an editor's state while the app is in the background: the text and cursor
         * ([keptText]), the saved copy ([keptSaved]) and the find bar. [stored] is the text as it
         * is stored now. It comes back when the text was too long to keep.
         *
         * What comes back starts a new undo history from [stored], and flashes [flashLine] again.
         */
        fun saver(stored: String, flashLine: Int?) = Saver<EditorState, Any>(
            save = { state ->
                arrayListOf(
                    with(keptText(stored)) { save(state.value) },
                    with(keptSaved(stored)) { save(state.saved) },
                    state.findOpen,
                    state.replaceOpen,
                    with(TextFieldValue.Saver) { save(state.query) },
                    with(TextFieldValue.Saver) { save(state.replacement) },
                    state.ignoreCase,
                )
            },
            restore = { kept ->
                kept as List<*>
                fun field(i: Int) =
                    kept[i]?.let { TextFieldValue.Saver.restore(it) } ?: TextFieldValue("")
                EditorState(
                    initial = stored,
                    value = kept[0]?.let { keptText(stored).restore(it) } ?: TextFieldValue(stored),
                    saved = kept[1]?.let { keptSaved(stored).restore(it) } ?: stored,
                    flashLine = flashLine,
                ).apply {
                    findOpen = kept[2] as Boolean
                    replaceOpen = kept[3] as Boolean
                    query = field(4)
                    replacement = field(5)
                    ignoreCase = kept[6] as Boolean
                }
            },
        )
    }
}

/**
 * The longest script text Android keeps for the editor while Kolahal is in the background.
 * - A screen's saved state must fit in about 1 MB (two bytes per character), or the app crashes
 *   when it goes to the background.
 * - The editor keeps its text twice: as typed, and as last saved.
 */
internal const val KEPT_TEXT_MAX = 100_000

/**
 * Saves the editor's text while the app is in the background. Past [KEPT_TEXT_MAX], only the
 * cursor is kept, and [stored] (the saved script) comes back instead. The editor saves when it goes
 * to the background, so [stored] is the same text.
 */
internal fun keptText(stored: String) = Saver<TextFieldValue, Any>(
    save = {
        arrayListOf(it.text.takeIf { text -> text.length <= KEPT_TEXT_MAX } ?: false,
            it.selection.start,
            it.selection.end)
    },
    restore = { kept ->
        val (text, start, end) = kept as List<*>
        val back = text as? String ?: stored
        TextFieldValue(
            back,
            TextRange(
                (start as Int).coerceIn(0, back.length),
                (end as Int).coerceIn(0, back.length)
            )
        )
    },
)

/** Keeps the last saved copy the same way: past [KEPT_TEXT_MAX], [stored] comes back. */
internal fun keptSaved(stored: String) = Saver<String, Any>(
    save = { it.takeIf { text -> text.length <= KEPT_TEXT_MAX } ?: false },
    restore = { it as? String ?: stored },
)

/** A cursor at the start of [line] (1-based), or at the end of the text if there is no line. */
internal fun selectionAtLine(text: String, line: Int?): TextRange =
    if (line == null || line < 1) TextRange(text.length) else TextRange(
        EditorLogic.lineStart(
            text,
            line
        )
    )
