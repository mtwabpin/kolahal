package com.mtwabpin.kolahal.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.commands.InstantRun

/**
 * The prompt: what is typed, ↑ and ↓ through the history, the touch keys, suggestions, instant
 * launch and Enter. The view model holds one as `prompt`, and gives access to the command queue
 * and the settings through [Host].
 */
class PromptActions(private val host: Host) {
    /** What the prompt needs from the view model. */
    interface Host {
        /** Runs [block] on the serial command queue. */
        fun io(block: suspend () -> Unit)
        fun emit(event: UiEvent)
        /** What launch.instant launches or runs for [text] as typed, or null when the line waits ([InstantRun.target]). */
        fun instantTarget(text: String): InstantRun.Target<AppEntry>?
        /** True when a tapped line runs at once, in `launch.instant all`. */
        fun runsOnTap(line: String): Boolean
        /** What the alias [word] stands for, or null when it isn't one. */
        fun abbreviation(word: String): String?
        /** What [line] in the prompt would change in the look ([LivePreview]), or null. */
        fun livePreview(line: String): LivePreview?
        /** The suggestions for [text], the first one first. */
        fun suggestions(text: String): List<Suggestion>
        /** Runs [line] as a typed command. It is called on the command queue. */
        suspend fun run(line: String)
        /** Opens [app]. It is called on the command queue. */
        suspend fun launch(app: AppEntry)
    }

    /** The line in the prompt, with its cursor. */
    var input by mutableStateOf(TextFieldValue(""))
        private set

    /**
     * The preview of a line that was just entered. It stays until the command has run; otherwise
     * the old look would show for a moment.
     */
    var entered by mutableStateOf<LivePreview?>(null)
        private set

    /** The commands for ↑, newest first. Written by the log's reload outside the main thread. Read on the main thread. */
    @Volatile var history: List<String> = emptyList()

    private var historyIndex = -1
    private var draft = ""

    fun onInputChange(value: TextFieldValue) {
        val old = input.text
        // Cursor moves and IME composition updates must not abandon history navigation.
        if (value.text != old) historyIndex = -1
        // An alias as the first word, then a space, expands. The line then waits for Enter, as an alias does.
        abbreviated(old, value)?.let { input = it; return }
        input = value
        // Instant run fires only while typing forward at the end of the line, so backspacing or
        // editing in the middle never fires it.
        // - Typing means one character at a time (or IME composition). Pasting a whole word is not typing.
        // - A line with arguments may also end in a word that the keyboard commits whole, with its
        //   space (`torch on `). Then only a line with nothing left to choose runs.
        val typed = value.text.length - old.length == 1 || value.composition != null
        if (searchWords(value.text) == null && value.text.length > old.length && value.selection.collapsed && value.selection.end == value.text.length &&
            (typed || value.text.trim().contains(' '))
        ) {
            instantRun(value.text)
        }
    }

    /** launch.instant: launches or runs as soon as the input can mean only one thing. */
    private fun instantRun(text: String) {
        val target = host.instantTarget(text) ?: return
        setText("")
        host.emit(UiEvent.ScrollToBottom)
        when (target) {
            is InstantRun.Target.App -> host.io { host.launch(target.app) }
            is InstantRun.Target.Command -> host.io { host.run(target.line) }
        }
    }

    fun submit() {
        val line = input.text
        // Enter in a log search hides the keyboard to make room for the results. Results are tapped, not run.
        if (searchWords(line) != null) {
            host.emit(UiEvent.HideKeyboard)
            return
        }
        entered = host.livePreview(line)
        input = TextFieldValue("")
        historyIndex = -1
        if (line.isBlank()) return
        host.io {
            try {
                host.run(line)
            } finally {
                entered = null
            }
        }
        host.emit(UiEvent.ScrollToBottom)
    }

    /** Inserts [text] at the cursor (for touch keys). */
    fun insert(text: String) {
        val v = input
        val newText = v.text.replaceRange(v.selection.min, v.selection.max, text)
        historyIndex = -1
        val value = TextFieldValue(newText, TextRange(v.selection.min + text.length))
        input = abbreviated(v.text, value) ?: value
    }

    /** [value] with an alias typed as its first word expanded ([Abbreviations]), or null. */
    private fun abbreviated(old: String, value: TextFieldValue) = Abbreviations.onSpace(old, value, host::abbreviation)

    fun historyUp() {
        val history = history
        if (history.isEmpty()) return
        if (historyIndex == -1) draft = input.text
        historyIndex = (historyIndex + 1).coerceAtMost(history.lastIndex)
        recall(history.getOrNull(historyIndex) ?: return)
    }

    fun historyDown() {
        if (historyIndex == -1) return
        historyIndex--
        recall(if (historyIndex == -1) draft else history.getOrNull(historyIndex) ?: draft)
    }

    /** Backspace for the touch key bar. Deletes the selection, or the character before the cursor. */
    fun deleteBackward() {
        val v = input
        val sel = v.selection
        val (start, end) = if (sel.collapsed) (sel.start - 1).coerceAtLeast(0) to sel.start else sel.min to sel.max
        if (start == end) return
        historyIndex = -1
        input = TextFieldValue(v.text.removeRange(start, end), TextRange(start))
    }

    fun moveCursor(delta: Int) {
        val pos = (input.selection.end + delta).coerceIn(0, input.text.length)
        input = input.copy(selection = TextRange(pos))
    }

    fun onKeyBar(key: KeyBarKey) {
        when (key) {
            KeyBarKey.ESC -> escape()
            KeyBarKey.TAB -> complete()
            KeyBarKey.UP -> historyUp()
            KeyBarKey.DOWN -> historyDown()
            KeyBarKey.LEFT -> moveCursor(-1)
            KeyBarKey.RIGHT -> moveCursor(1)
            KeyBarKey.HOME -> input = input.copy(selection = TextRange(0))
            KeyBarKey.END -> input = input.copy(selection = TextRange(input.text.length))
            KeyBarKey.ENTER -> submit()
            KeyBarKey.KBD -> host.emit(UiEvent.FocusPrompt)
            KeyBarKey.SPACE -> insert(" ")
            KeyBarKey.BKSP -> deleteBackward()
        }
    }

    fun escape() {
        setText("")
    }

    /** TAB: accepts the first suggestion. */
    fun complete() {
        val first = host.suggestions(input.text).firstOrNull() ?: return
        when (first) {
            is Suggestion.Completion -> pickCompletion(first.replacement, tapped = false)
            is Suggestion.App -> setText(first.app.label)
        }
    }

    /**
     * A completion fills the prompt. In `launch.instant all`, if the line then has nothing left to
     * choose, it runs (`torch on`). [tapped] is true for a tapped suggestion. A suggestion taken
     * with Tab counts as typing.
     */
    private fun pickCompletion(replacement: String, tapped: Boolean) {
        // An alias picked as the first word (`a `) expands, as it does when typed with a space.
        abbreviated(replacement.dropLast(1), TextFieldValue(replacement, TextRange(replacement.length)))?.let {
            historyIndex = -1
            input = it
            return
        }
        setText(replacement)
        if (searchWords(replacement) != null || !replacement.trim().contains(' ')) return
        // A contact tapped for `call` opens the dialer at once, as a tap in the log does. The
        // dialer still waits for Call. Any other line runs only once it is complete (`torch on`).
        val line = replacement.trim()
        if (tapped && InstantRun.canonical(line.substringBefore(' ').lowercase()) == "call" && host.runsOnTap(line)) {
            setText("")
            host.emit(UiEvent.ScrollToBottom)
            host.io { host.run(line) }
        } else {
            instantRun(replacement)
        }
    }

    fun onSuggestion(s: Suggestion) {
        when (s) {
            is Suggestion.App -> {
                setText("")
                host.io { host.launch(s.app) }
            }
            is Suggestion.Completion -> pickCompletion(s.replacement, tapped = true)
        }
    }

    /** Sets a new line in the prompt. ↑ and ↓ start again from it. */
    fun setText(text: String) {
        historyIndex = -1
        recall(text)
    }

    private fun recall(text: String) {
        input = TextFieldValue(text, TextRange(text.length))
    }
}
