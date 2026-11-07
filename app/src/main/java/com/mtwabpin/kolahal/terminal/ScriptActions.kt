package com.mtwabpin.kolahal.terminal

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.StatusLines
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptFiles
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptNames
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.script.SecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val KEY_PENDING_EXPORT = "pending_export"

/**
 * The actions that the script screens perform on scripts:
 * - switches, timers, keys and options;
 * - script files;
 * - installing from the library or from Get more scripts.
 *
 * The view model holds one as `scriptActions`.
 */
class ScriptActions(
    private val context: Context,
    private val state: SavedStateHandle,
    private val scope: CoroutineScope,
    private val scriptList: MutableStateFlow<List<Script>>,
    private val host: Host,
) {
    /** What these actions need from the view model. */
    interface Host {
        fun io(block: suspend () -> Unit)
        fun fileOp(label: String, block: suspend () -> Unit)
        fun notice(text: String, error: Boolean = false)
        fun show(text: String)
        fun emit(event: UiEvent)
        fun openEditor(script: Script)
        fun setCommandScripts(list: List<Script>)
    }

    private val scripts = ScriptRepository.get(context)
    private val logs = LogRepository.get(context)

    private fun io(block: suspend () -> Unit) = host.io(block)
    private fun fileOp(label: String, block: suspend () -> Unit) = host.fileOp(label, block)
    private fun notice(text: String, error: Boolean = false) = host.notice(text, error)
    private fun show(text: String) = host.show(text)
    private fun openEditor(script: Script) = host.openEditor(script)

    fun refreshScripts() = io { scriptList.value = scripts.all() }

    private val changes = ScriptChanges(context)

    /** Applies a script change outside the main thread, then refreshes the manager list. */
    private fun change(block: () -> Unit) = io {
        block()
        scriptList.value = scripts.all()
    }

    /** The switch on the Scripts screen. When there is something to decide, the screen asks first and uses [enableScript]. */
    fun setScriptEnabled(s: Script, on: Boolean) = change {
        changes.enabled(s, on)
        if (on) scripts.byId(s.id)?.let { ScriptAccess.askForSecrets(context, it) }
    }

    /** Switches [s] on with the user's answers about what it asks to use. [choices] maps each item to allowed or not. */
    fun enableScript(s: Script, choices: Map<String, Boolean>) = change {
        choices.forEach { (item, on) -> scripts.setAccess(s.id, item, on) }
        changes.enabled(s, true)
        scripts.byId(s.id)?.let { ScriptAccess.askForSecrets(context, it) }
    }

    /** Sets one access switch. Pinning or unpinning updates the top of home at once. */
    fun setScriptAccess(s: Script, item: String, on: Boolean) = change { changes.access(s, item, on) }

    /** The long-press menu of a pinned script's line at the top: run it, unpin it, or edit it. */
    fun statusActions(name: String): List<LogAction> {
        val s = scripts.byName(name) ?: return listOf(
            LogAction("Clear this line") { io { StatusLines.clear(context, name); logs.onStatusChanged() } },
        )
        return listOf(
            LogAction("Run $name now") { io { ScriptScheduler.runNow(context, s) } },
            LogAction("Unpin $name") {
                setScriptAccess(s, ScriptAccess.HEADER, false)
                io { logs.add(LogKind.INFO, "$name: unpinned — 'script pin $name' pins it again", ctx = LogContext.of(LogContext.SCRIPT to name)) }
            },
            LogAction("Edit $name.${s.lang.ext}") { openEditor(s) },
        )
    }
    fun setScriptNotify(s: Script, on: Boolean) {
        change { scripts.setNotify(s.id, on) }
        if (on) host.emit(UiEvent.RequestNotificationPermission)
    }

    fun setScriptInterval(s: Script, minutes: Int) = change { changes.interval(s, minutes) }

    /**
     * Sets daily times from text like "08:00, 18:30", read the same way as `script at`.
     * "off" or empty text clears the times. Returns false if a word is not a time.
     */
    fun setScriptTimes(s: Script, raw: String): Boolean {
        val times = when (val read = CommandParsing.dailyTimes(CommandParsing.clockWords(listOf(raw)))) {
            null -> emptyList()
            is CommandParsing.DailyTimes.Bad -> return false
            is CommandParsing.DailyTimes.Times -> read.times
        }
        change { changes.times(s, times) }
        return true
    }

    fun runScriptNow(s: Script) = io {
        ScriptScheduler.runNow(context, s)
        notice("Running ${s.name}…")
    }

    /** Renames [s] ([ScriptRepository.rename]). The sheet checks the name while the user types it. */
    fun renameScript(s: Script, to: String) = io {
        changes.rename(s, to)?.let { return@io notice(it, error = true) }
        scriptList.value = scripts.all()
        notice("Renamed ${s.name}.${s.lang.ext} to $to.${s.lang.ext}")
    }

    fun deleteScript(s: Script) = io {
        scripts.delete(s.id)
        scriptList.value = scripts.all()
        notice("Deleted ${s.name}")
    }

    fun newScript(name: String, lang: ScriptLang, asCommand: Boolean) {
        val clean = name.trim().lowercase()
        if (!ScriptNames.NAME.matches(clean)) {
            show("Names: lowercase letters, digits, - and _")
            return
        }
        if (clean in CommandCatalog.RESERVED_NAMES) {
            show("'$clean' is a built-in command, pick another name")
            return
        }
        io {
            if (scripts.byName(clean) != null) return@io notice("Script '$clean' already exists", error = true)
            val s = changes.create(clean, lang, asCommand)
            scriptList.value = scripts.all()
            openEditor(s)
        }
    }

    fun importScriptFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        fileOp("import") {
            val r = ScriptFiles(context).import(uris)
            refreshScriptCaches()
            notice(
                buildList {
                    if (r.created.isNotEmpty()) add("added ${r.created.joinToString()}")
                    if (r.updated.isNotEmpty()) add("updated ${r.updated.joinToString()}")
                    if (r.created.isNotEmpty() || r.updated.isNotEmpty()) add("off until you turn them on (script on <name>)")
                    if (r.skipped.isNotEmpty()) add("skipped ${r.skipped.joinToString()}")
                }.joinToString(" · ").ifEmpty { "nothing imported" }.replaceFirstChar { it.uppercase() },
                error = r.created.isEmpty() && r.updated.isEmpty(),
            )
        }
    }

    /** Remembers the script that waits for the "save as" picker. It is kept in SavedStateHandle, so it survives process death. */
    fun beginExportScript(s: Script) {
        state[KEY_PENDING_EXPORT] = s.id
    }

    fun finishExportScript(uri: Uri?) {
        val id = state.get<Long>(KEY_PENDING_EXPORT) ?: return
        state.remove<Long>(KEY_PENDING_EXPORT)
        if (uri == null) return
        fileOp("export") {
            val s = scripts.byId(id) ?: throw IllegalStateException("script was deleted")
            ScriptFiles(context).export(s, uri)
            notice("Exported ${s.name}.${s.lang.ext}")
        }
    }

    val library by lazy { ScriptLibrary(context) }

    fun installFromLibrary(entry: ScriptLibrary.Entry) {
        fileOp("install") {
            val s = changes.install(library, entry)
            refreshScriptCaches()
            notice("Installed ${s.name}.${s.lang.ext}" + if (entry.command) " — run it as '${s.name}'" else "")
        }
    }

    // ---- script secrets ------------------------------------------------------------------

    val secretStore by lazy { SecretStore(context) }

    /** Goes up by one when a secret is set or cleared, so sheets that show "set" or "not set" refresh. */
    private val _secretsVersion = MutableStateFlow(0)
    val secretsVersion: StateFlow<Int> = _secretsVersion.asStateFlow()

    /** Returns the names from the `@secret` lines of [s] that have a value. */
    fun secretsSet(s: Script): Set<String> = ScriptMeta.secrets(s.code).filter { secretStore.isSet(s.name, it) }.mapTo(HashSet()) { it.name }

    /**
     * Sets [secret] for [script], for the sites that its line names. A null value clears it.
     * The log says which secret changed, but never shows the value.
     */
    fun setSecret(script: String, secret: ScriptMeta.Secret, value: String?) = io {
        secretStore.set(script, secret.name, value, secret.hosts)
        _secretsVersion.value++
        logs.add(LogKind.INFO, "$script: ${secret.name} " + if (value.isNullOrBlank()) "cleared" else "set", ctx = LogContext.of(LogContext.SCRIPT to script))
    }

    // ---- script options ------------------------------------------------------------------

    /** Goes up by one when an option changes, so sheets that show its value refresh. */
    private val _optionsVersion = MutableStateFlow(0)
    val optionsVersion: StateFlow<Int> = _optionsVersion.asStateFlow()

    /** Returns the `@option` values of [s] (yours or the defaults), and the names of the ones you set. */
    fun optionValues(s: Script): Pair<Map<String, String>, Set<String>> {
        val declared = ScriptMeta.options(s.code)
        val store = com.mtwabpin.kolahal.script.ScriptOptions(context)
        return store.values(s.name, declared) to store.changed(s.name, declared)
    }

    /** Sets [option] for [script]. A null value resets it to its default. */
    fun setOption(script: String, option: String, value: String?) = io {
        com.mtwabpin.kolahal.script.ScriptOptions(context).set(script, option, value)
        _optionsVersion.value++
        logs.add(LogKind.INFO, "$script: $option " + if (value == null) "reset to default" else "= $value", ctx = LogContext.of(LogContext.SCRIPT to script))
    }

    /** A secret that the terminal asks for (`script secret`). The prompt is shown over home. */
    data class SecretPrompt(val script: String, val name: String)

    private val _secretPrompt = MutableStateFlow<SecretPrompt?>(null)
    val secretPrompt: StateFlow<SecretPrompt?> = _secretPrompt.asStateFlow()

    fun askSecret(script: Script, name: String) {
        _secretPrompt.value = SecretPrompt(script.name, name)
    }

    fun closeSecretPrompt() {
        _secretPrompt.value = null
    }

    // ---- Get more scripts (kolahal-scripts) ------------------------------------------------

    val scriptCatalog by lazy { ScriptCatalog(context) }

    /** The shared scripts, whether they are loading, and the error if they couldn't be read. */
    data class SharedScripts(val entries: List<ScriptCatalog.Entry> = emptyList(), val loading: Boolean = false, val error: String? = null)

    private val _shared = MutableStateFlow(SharedScripts())
    val sharedScripts: StateFlow<SharedScripts> = _shared.asStateFlow()

    /**
     * Loads the list. It uses the cached copy (kept for an hour), or GitHub when [refresh] is true.
     * If loading fails, the list keeps what it had.
     */
    fun loadSharedScripts(refresh: Boolean = false) {
        _shared.value = _shared.value.copy(loading = true, error = null)
        scope.launch(Dispatchers.IO) {
            _shared.value = runCatching { SharedScripts(scriptCatalog.entries(refresh)) }.getOrElse {
                SharedScripts(_shared.value.entries.ifEmpty { scriptCatalog.cached().orEmpty() }, error = it.message ?: it.javaClass.simpleName)
            }
        }
    }

    /** Downloaded code, keyed by SHA-256. It is downloaded once for a script's sheet, and then installed from this copy. */
    private val sharedCode = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun sharedScriptCode(e: ScriptCatalog.Entry): String =
        withContext(Dispatchers.IO) { sharedCode.getOrPut(e.sha256) { scriptCatalog.download(e) } }

    /**
     * Installs or updates [e].
     * - [allow]: the user's answers for the access it declares.
     * - [replace]: overwrite your own edits.
     */
    fun installSharedScript(e: ScriptCatalog.Entry, allow: Map<String, Boolean> = emptyMap(), replace: Boolean = false) {
        fileOp("install") {
            val code = sharedScriptCode(e)
            val update = scripts.byName(e.name) != null
            val s = scriptCatalog.install(e, code, allow, replace)
            refreshScriptCaches()
            notice(if (update) "Updated ${s.name}.${s.lang.ext} to ${e.version}" else "Installed ${s.name}.${s.lang.ext}" + if (e.command) " — run it as '${s.name}'" else "")
        }
    }

    /** Refreshes the scripts list and the command names after edits or imports, not only when the log changes. */
    suspend fun refreshScriptCaches() {
        val list = withContext(Dispatchers.IO) { scripts.all() }
        scriptList.value = list
        host.setCommandScripts(list)
    }
}
