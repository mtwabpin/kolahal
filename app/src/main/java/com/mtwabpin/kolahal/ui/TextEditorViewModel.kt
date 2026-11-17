package com.mtwabpin.kolahal.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mtwabpin.kolahal.documents.Documents
import com.mtwabpin.kolahal.documents.LineEnding
import com.mtwabpin.kolahal.documents.TextDocument
import com.mtwabpin.kolahal.documents.TextFiles
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The text editor's view model: one file open in [TextEditorActivity].
 * - It holds the editor's state ([EditorState]), so the text, cursor and undo history live through
 *   a rotation or any other change that makes Android build the window again.
 * - It asks the Model ([TextDocument]) to read, save and keep drafts, off the main thread and one
 *   call at a time, and turns the answers into what the screen shows.
 * - The file's text is never put in the saved instance state: that must stay under about 1 MB.
 *   After Android has closed the editor, the text comes back from the file and its draft. Only
 *   the cursor and the wrap choice are saved.
 */
class TextEditorViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    /** What the window shows. */
    sealed interface Phase {
        data object Loading : Phase

        /** The editor, on [state]. */
        class Editing internal constructor(internal val state: EditorState) : Phase

        /** The file can't be shown. [title] says what, [why] in a sentence or two. */
        data class Failed(val title: String, val why: String) : Phase
    }

    /** What the activity must do: only it can open pickers and windows. */
    sealed interface Event {
        /** Close this window: the file is closed. */
        data object Close : Event

        /** Ask where to save the text as another file. */
        data object AskSaveAs : Event

        /** The text now lives in [uri]. Open that file, and close this window. */
        data class Moved(val uri: Uri) : Event
    }

    var phase by mutableStateOf<Phase>(Phase.Loading)
        private set

    /** The file's name, for the title and the window's name in Recents. */
    var name by mutableStateOf("")
        private set

    /** Whether long lines wrap. It starts from the kind of file ([EditorLogic.wrapsByDefault]). */
    var wrap by mutableStateOf(false)
        private set

    /** True while the editor asks whether to overwrite a file that changed since it was read. */
    var askOverwrite by mutableStateOf(false)
        private set

    /** True while the editor asks what to do with unsaved changes before it closes. */
    var askClose by mutableStateOf(false)
        private set

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events

    private var uri: Uri? = null
    @Volatile private var document: TextDocument? = null
    private val editing get() = (phase as? Phase.Editing)?.state

    /** The Model's calls run here: one at a time and in order, so a draft can't be written after a newer one. */
    private val io = Dispatchers.IO.limitedParallelism(1)

    /** The file is closed, or its text moved to another file: no draft is written for it any more. */
    @Volatile private var done = false

    /**
     * The text that needs no draft written: the one last written, or one the user discarded.
     * It is the same object as the editor's text, not only equal to it. Read and set on [io] only,
     * in the order of the Model's calls.
     */
    private var drafted: String? = null

    /** Opens the file at [uri]. Later calls do nothing: the window is built again, the file is already open. */
    fun open(uri: Uri) {
        if (this.uri != null) return
        this.uri = uri
        val app = getApplication<Application>()
        name = TextDocument.nameIn(uri.toString())
        Documents.source(app).refusal(uri)?.let {
            phase = Phase.Failed("Can't open this", "$it.")
            return
        }
        viewModelScope.launch {
            val doc = Documents.open(app, uri)
            val opened = withContext(io) {
                doc.open().also { if (it is TextDocument.Opened.Ready) Documents.remember(app, uri, doc.name) }
            }
            document = doc
            name = doc.name
            wrap = saved[KEY_WRAP] ?: EditorLogic.wrapsByDefault(doc.name)
            when (opened) {
                is TextDocument.Opened.Ready -> show(opened.text, opened.saved, notes(doc, opened))
                is TextDocument.Opened.Refused -> phase = Phase.Failed("Can't edit ${doc.name}", opened.why)
                is TextDocument.Opened.Unavailable -> {
                    val draft = opened.draft
                    if (draft == null) {
                        // It can't be opened again from the recent list either.
                        withContext(io) { Documents.forget(app, uri.toString()) }
                        phase = Phase.Failed("Can't open ${doc.name}", "${opened.why}.")
                    } else {
                        show(draft, "", EditorMessage(
                            "${opened.why}. Your unsaved text is here · tap to save it as another file",
                            EditorMessage.Kind.ERROR, action = { _events.tryEmit(Event.AskSaveAs) },
                        ))
                    }
                }
            }
        }
    }

    /** What to say when a file opens, or null: unsaved text came back, it can't be written, or it will be slow. */
    private fun notes(doc: TextDocument, opened: TextDocument.Opened.Ready): EditorMessage? {
        val notes = listOfNotNull(
            when {
                opened.changedSince -> "Your unsaved changes are back. The file has changed since: saving asks first"
                opened.restored -> "Your unsaved changes are back"
                else -> null
            },
            "Kolahal can't write this file: use Save as".takeIf { !doc.writable },
            "Mixed line endings: saving writes them all as ${doc.format.lineEnding.name}".takeIf { doc.format.mixed },
            "A big file: typing is slow".takeIf { opened.text.length >= TextFiles.SLOW_FROM },
        )
        return if (notes.isEmpty()) null else EditorMessage(notes.joinToString(" · "), EditorMessage.Kind.NOTE)
    }

    private fun show(text: String, savedText: String, message: EditorMessage?) {
        // A file opens at its start. After Android closed the editor, the cursor is where it was.
        val start = (saved.get<Int>(KEY_START) ?: 0).coerceIn(0, text.length)
        val end = (saved.get<Int>(KEY_END) ?: start).coerceIn(0, text.length)
        val state = EditorState(
            initial = text,
            value = TextFieldValue(text, TextRange(start, end)),
            saved = savedText,
            limit = EditorLimit(TextFiles.MAX_CHARS, TextFiles.MAX_LINES),
        )
        state.message = message
        phase = Phase.Editing(state)
        // A draft follows the typing, a moment after it stops.
        viewModelScope.launch {
            snapshotFlow { state.value.text }.drop(1).collectLatest { typed ->
                delay(DRAFT_AFTER)
                keepDraft(typed).join()
            }
        }
    }

    /** Writes [text] as the draft, after the Model's earlier calls. The write is not cancelled with the view model. */
    private fun keepDraft(text: String) = background.launch(io) {
        val doc = document ?: return@launch
        if (done || text === drafted) return@launch
        runCatching { doc.keepDraft(text) }.onSuccess { drafted = text }
    }

    /**
     * The window is leaving the screen. Android may close the editor at any time from now on.
     * - The draft is written before this returns. It waits two seconds at most: a slow save before
     *   it (a file in the cloud) must not stop the phone, and the draft is still written after it.
     * - The cursor and the wrap choice go into the saved state. The text itself doesn't: it comes
     *   back from the file and the draft.
     */
    fun onHidden() {
        val state = editing ?: return
        saved[KEY_START] = state.value.selection.start
        saved[KEY_END] = state.value.selection.end
        saved[KEY_WRAP] = wrap
        val job = keepDraft(state.value.text)
        runBlocking { withTimeoutOrNull(2_000) { job.join() } }
    }

    /**
     * The window is on screen again. If the file changed meanwhile:
     * - with no unsaved changes, the editor shows the file as it is now;
     * - with unsaved changes, it says so. Saving then asks before it overwrites.
     */
    fun onShown() {
        val doc = document ?: return
        val state = editing ?: return
        if (done) return
        viewModelScope.launch {
            // A quick look, as this runs each time the window comes back. Saving looks properly.
            if (!withContext(io) { runCatching { doc.changedOnDisk(quick = true) }.getOrDefault(false) }) return@launch
            if (state.dirty) {
                state.message = EditorMessage("This file changed on disk. Saving asks before it overwrites", EditorMessage.Kind.NOTE)
            } else {
                reload(doc, state, "The file changed on disk: this is how it is now")
            }
        }
    }

    /** Reads the file again and shows it, as one undo step. It does nothing if the user typed meanwhile. */
    private suspend fun reload(doc: TextDocument, state: EditorState, said: String) {
        val before = state.value.text
        val opened = withContext(io) { doc.open() }
        if (state.value.text != before) return
        when (opened) {
            is TextDocument.Opened.Ready -> {
                state.history.checkpoint()
                state.edit(TextFieldValue(opened.text, TextRange(state.value.selection.start.coerceAtMost(opened.text.length))))
                state.history.checkpoint()
                state.saved = opened.saved
                name = doc.name
                state.message = EditorMessage(said, EditorMessage.Kind.OK)
            }
            is TextDocument.Opened.Refused -> state.message = EditorMessage("This file changed on disk, and can't be edited now. ${opened.why}", EditorMessage.Kind.NOTE)
            is TextDocument.Opened.Unavailable -> state.message = EditorMessage("${opened.why}. What you see is the text from before", EditorMessage.Kind.NOTE)
        }
    }

    /** Saves the text to the file. [overwrite]: also when the file changed since it was read. [then] runs once it is saved. */
    fun save(overwrite: Boolean = false, then: () -> Unit = {}) {
        val doc = document ?: return
        val state = editing ?: return
        askOverwrite = false
        if (!state.dirty && !overwrite) {
            state.message = EditorMessage("No changes to save", EditorMessage.Kind.INFO)
            return then()
        }
        val text = state.value.text
        val busy = EditorMessage("Saving…", EditorMessage.Kind.BUSY)
        state.message = busy
        viewModelScope.launch {
            val result = withContext(io) { doc.save(text, overwrite) }
            if (state.message === busy) state.message = null
            when (result) {
                is TextDocument.Saved.Done -> {
                    // The file ends with a line break that the text didn't have: the editor shows what the file holds.
                    if (result.text != text && state.value.text == text) state.edit(state.value.copy(text = result.text))
                    state.saved = result.text
                    state.message = EditorMessage("Saved", EditorMessage.Kind.OK)
                    then()
                }
                TextDocument.Saved.Changed -> askOverwrite = true
                is TextDocument.Saved.Failed -> state.message = EditorMessage(
                    "Couldn't save: ${result.why} · tap to save as another file",
                    EditorMessage.Kind.ERROR, action = { _events.tryEmit(Event.AskSaveAs) },
                )
            }
        }
    }

    /** The user's answer to "the file changed": not to overwrite. */
    fun keepTheirs() {
        askOverwrite = false
    }

    /** Asks the activity for a place to save the text as another file. */
    fun askSaveAs() {
        askOverwrite = false
        askClose = false
        _events.tryEmit(Event.AskSaveAs)
    }

    /**
     * The picker's answer to Save as: writes the text to [to]. The editor then moves to that file,
     * in a window of its own, because a window stands for one file.
     */
    fun saveAs(to: Uri?) {
        val doc = document ?: return
        val state = editing ?: return
        if (to == null) return
        // The same file, picked again to replace it.
        if (to == uri) return save(overwrite = true)
        val text = state.value.text
        val app = getApplication<Application>()
        state.message = EditorMessage("Saving…", EditorMessage.Kind.BUSY)
        viewModelScope.launch {
            when (val result = withContext(io) { doc.saveCopy(to.toString(), text) }) {
                is TextDocument.Saved.Done -> {
                    done = true
                    state.saved = state.value.text
                    state.message = null
                    Documents.keepPermission(app, to)
                    _events.emit(Event.Moved(to))
                }
                is TextDocument.Saved.Failed -> state.message = EditorMessage("Couldn't save: ${result.why}", EditorMessage.Kind.ERROR)
                TextDocument.Saved.Changed -> Unit
            }
        }
    }

    /** Throws the unsaved changes away and shows the file as it is. Undo brings the text back. */
    fun revert() {
        val doc = document ?: return
        val state = editing ?: return
        viewModelScope.launch {
            val typed = state.value.text
            // No draft of what was thrown away is written again: by a write on its way, or on leaving just now.
            withContext(io) {
                doc.discardDraft()
                drafted = typed
            }
            reload(doc, state, "Changes discarded. Undo brings them back")
        }
    }

    fun toggleWrap() {
        wrap = !wrap
    }

    /** Close: at once when nothing is unsaved, otherwise the editor asks first. */
    fun close() {
        if (editing?.dirty == true) askClose = true else finish()
    }

    /** The answer to "unsaved changes": save them, then close. */
    fun saveAndClose() {
        askClose = false
        save(then = ::finish)
    }

    /** The answer to "unsaved changes": lose them, and close. */
    fun discardAndClose() {
        askClose = false
        done = true
        val doc = document
        background.launch(io) { runCatching { doc?.discardDraft() } }
        finish()
    }

    /** The answer to "unsaved changes": stay. */
    fun stay() {
        askClose = false
    }

    private fun finish() {
        done = true
        _events.tryEmit(Event.Close)
    }

    /** What the menu says about the file: its encoding, line endings and length. */
    fun details(): String {
        val format = document?.format
        val text = editing?.value?.text.orEmpty()
        val lines = text.count { it == '\n' } + 1
        return listOfNotNull(
            "UTF-8" + if (format?.bom == true) " with BOM" else "",
            when (format?.lineEnding) {
                LineEnding.CRLF -> "CRLF line endings"
                LineEnding.CR -> "CR line endings"
                else -> null
            },
            "$lines line${if (lines == 1) "" else "s"}",
            "${"%,d".format(java.util.Locale.ROOT, text.length)} characters",
        ).joinToString(" · ")
    }

    private companion object {
        const val KEY_START = "cursor.start"
        const val KEY_END = "cursor.end"
        const val KEY_WRAP = "wrap"

        /** How long after the last key the draft is written. */
        val DRAFT_AFTER = 800.milliseconds

        /** Draft writes run here, so one that started is finished even if its window is closed meanwhile. */
        val background = CoroutineScope(SupervisorJob())
    }
}
