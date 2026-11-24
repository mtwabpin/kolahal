package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.Script
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The screens around [ScriptActions] and [BackupActions], as a test sees them: the notices and
 * messages they would show (with the Undo one offered), the events they would act on, the editor opened. Work runs at once, in
 * order; a file operation that fails is kept as what its notice would say after "<label> failed: ".
 */
internal class RecordingHost : ScriptActions.Host {
    data class Notice(val text: String, val error: Boolean)

    val notices = CopyOnWriteArrayList<Notice>()

    /** The Undo of the last notice that offered one, and the notice it came with. */
    @Volatile
    var undo: Pair<Notice, () -> Unit>? = null
        private set
    val shown = CopyOnWriteArrayList<String>()
    val events = CopyOnWriteArrayList<UiEvent>()
    val opened = CopyOnWriteArrayList<Script>()
    val failures = CopyOnWriteArrayList<Pair<String, String>>()

    /** The scripts list the command names were last read from, or null if they never were. */
    @Volatile
    var commandList: List<Script>? = null
        private set

    override fun io(block: suspend () -> Unit) = runBlocking { block() }

    override fun fileOp(label: String, block: suspend () -> Unit) {
        runBlocking {
            runCatching { block() }.onFailure {
                failures += label to (it.message ?: it.javaClass.simpleName)
            }
        }
    }

    override fun notice(text: String, error: Boolean, undo: (() -> Unit)?) {
        val n = Notice(text, error)
        notices += n
        copy = null
        if (undo != null) this.undo = n to undo
    }

    /** What Copy gives on the last notice's line, when that notice suggested one command; else null. */
    @Volatile
    var copy: String? = null
        private set

    override fun notice(text: String, copy: String) {
        notice(text, false, null)
        this.copy = copy
    }

    override fun show(text: String) {
        shown += text
    }

    override fun emit(event: UiEvent) {
        events += event
    }

    override fun openEditor(script: Script) {
        opened += script
    }

    override fun setCommandScripts(list: List<Script>) {
        commandList = list
    }

    fun lastNotice(): Notice? = notices.lastOrNull()
}
