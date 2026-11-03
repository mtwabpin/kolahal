package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.Script
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The screens around [ScriptActions] and [BackupActions], as a test sees them: the notices and
 * messages they would show, the events they would act on, the editor opened. Work runs at once, in
 * order; a file operation that fails is kept as what its notice would say after "<label> failed: ".
 */
internal class RecordingHost : ScriptActions.Host {
    data class Notice(val text: String, val error: Boolean)

    val notices = CopyOnWriteArrayList<Notice>()
    val shown = CopyOnWriteArrayList<String>()
    val events = CopyOnWriteArrayList<UiEvent>()
    val opened = CopyOnWriteArrayList<Script>()
    val failures = CopyOnWriteArrayList<Pair<String, String>>()
    /** The scripts list the command names were last read from, or null if they never were. */
    @Volatile var commandList: List<Script>? = null
        private set

    override fun io(block: suspend () -> Unit) = runBlocking { block() }

    override fun fileOp(label: String, block: suspend () -> Unit) {
        runBlocking { runCatching { block() }.onFailure { failures += label to (it.message ?: it.javaClass.simpleName) } }
    }

    override fun notice(text: String, error: Boolean) {
        notices += Notice(text, error)
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
