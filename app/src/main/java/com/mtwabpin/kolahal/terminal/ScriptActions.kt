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
import com.mtwabpin.kolahal.script.ScriptTemplates
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
 * What the script screens do to scripts: their switches, timers, keys and options, their files, and
 * installing from the library or Get more scripts. The view model holds one as `scriptActions`.
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

    /** Applies a script change, reschedules it, and refreshes the manager list. */
    private fun updateScript(s: Script, change: () -> Unit) = io {
        change()
        scripts.byId(s.id)?.let { ScriptScheduler.sync(context, it) }
        scriptList.value = scripts.all()
    }

    /** The Scripts screen's switch: it asks first when there is anything to decide ([enableScript]). */
    fun setScriptEnabled(s: Script, on: Boolean) = updateScript(s) {
        scripts.setEnabled(s.id, on, grant = on)
        if (on) scripts.byId(s.id)?.let { ScriptAccess.askForSecrets(context, it) }
    }

    /** Switches [s] on with the user's answers for what it asks to use ([choices]: item to allowed). */
    fun enableScript(s: Script, choices: Map<String, Boolean>) = updateScript(s) {
        choices.forEach { (item, on) -> scripts.setAccess(s.id, item, on) }
        scripts.setEnabled(s.id, true, grant = true)
        scripts.byId(s.id)?.let { ScriptAccess.askForSecrets(context, it) }
    }

    /** One access switch; pinning or unpinning shows at the top of home at once. */
    fun setScriptAccess(s: Script, item: String, on: Boolean) = updateScript(s) {
        scripts.setAccess(s.id, item, on)
        if (item == ScriptAccess.HEADER) logs.onStatusChanged()
    }

    /** Long-press on a pinned script's line at the top: run it, unpin it, or edit it. */
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
        updateScript(s) { scripts.setNotify(s.id, on) }
        if (on) host.emit(UiEvent.RequestNotificationPermission)
    }

    fun setScriptInterval(s: Script, minutes: Int) = updateScript(s) {
        scripts.setInterval(s.id, if (minutes > 0) minutes.coerceAtLeast(ScriptScheduler.MIN_INTERVAL_MIN) else 0)
        // A timer switches it on, but allows nothing it asks for: that stays a tap away.
        if (minutes > 0 && !s.enabled) {
            scripts.setEnabled(s.id, true)
            scripts.byId(s.id)?.let { ScriptAccess.askForNew(context, it) }
        }
    }

    /** "08:00, 18:30" -> daily times; returns false if any time is invalid. */
    fun setScriptTimes(s: Script, raw: String): Boolean {
        val parts = raw.split(',', ' ').filter { it.isNotBlank() }
        val times = parts.mapNotNull(ScriptScheduler::normalizeTime)
        if (times.size != parts.size) return false
        updateScript(s) {
            scripts.setAtTimes(s.id, times.distinct().sorted())
            // Times switch it on, as a timer does, but allow nothing it asks for: that stays a tap away.
            if (times.isNotEmpty() && !s.enabled) {
                scripts.setEnabled(s.id, true)
                scripts.byId(s.id)?.let { ScriptAccess.askForNew(context, it) }
            }
        }
        return true
    }

    fun runScriptNow(s: Script) = io {
        ScriptScheduler.runNow(context, s)
        notice("Running ${s.name}…")
    }

    /** Renames [s] ([ScriptRepository.rename]); the sheet checks the name as it is typed. */
    fun renameScript(s: Script, to: String) = io {
        ScriptNames.invalid(to) { scripts.byName(it) != null }?.let { return@io notice(it, error = true) }
        scripts.rename(s.id, to)
        logs.onStatusChanged()
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
            val code = if (asCommand) ScriptTemplates.command(lang, clean) else ScriptTemplates.forLang(lang, clean)
            val s = scripts.create(clean, lang, code)
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

    /** Script waiting for the "save as" picker; kept in SavedStateHandle so process death can't lose it. */
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
            val s = library.install(entry)
            ScriptAccess.askForSecrets(context, s)
            refreshScriptCaches()
            notice("Installed ${s.name}.${s.lang.ext}" + if (entry.command) " — run it as '${s.name}'" else "")
        }
    }

    // ---- script secrets ------------------------------------------------------------------

    val secretStore by lazy { SecretStore(context) }

    /** Bumped when a secret is set or cleared, so sheets showing "set" or "not set" read again. */
    private val _secretsVersion = MutableStateFlow(0)
    val secretsVersion: StateFlow<Int> = _secretsVersion.asStateFlow()

    /** The names among [s]'s `@secret` lines that have a value. */
    fun secretsSet(s: Script): Set<String> = ScriptMeta.secrets(s.code).filter { secretStore.isSet(s.name, it) }.mapTo(HashSet()) { it.name }

    /** Sets (or, with null, clears) [secret] for [script], for the sites its line names; the log says which, never the value. */
    fun setSecret(script: String, secret: ScriptMeta.Secret, value: String?) = io {
        secretStore.set(script, secret.name, value, secret.hosts)
        _secretsVersion.value++
        logs.add(LogKind.INFO, "$script: ${secret.name} " + if (value.isNullOrBlank()) "cleared" else "set", ctx = LogContext.of(LogContext.SCRIPT to script))
    }

    // ---- script options ------------------------------------------------------------------

    /** Bumped when an option is changed, so sheets showing its value read again. */
    private val _optionsVersion = MutableStateFlow(0)
    val optionsVersion: StateFlow<Int> = _optionsVersion.asStateFlow()

    /** [s]'s `@option` values (yours or the defaults), and which ones you set. */
    fun optionValues(s: Script): Pair<Map<String, String>, Set<String>> {
        val declared = ScriptMeta.options(s.code)
        val store = com.mtwabpin.kolahal.script.ScriptOptions(context)
        return store.values(s.name, declared) to store.changed(s.name, declared)
    }

    /** Sets [option] for [script]; null goes back to its default. */
    fun setOption(script: String, option: String, value: String?) = io {
        com.mtwabpin.kolahal.script.ScriptOptions(context).set(script, option, value)
        _optionsVersion.value++
        logs.add(LogKind.INFO, "$script: $option " + if (value == null) "reset to default" else "= $value", ctx = LogContext.of(LogContext.SCRIPT to script))
    }

    /** A secret to ask for from the terminal (`script secret`), shown over home. */
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

    /** The shared scripts, whether they are loading, and why they couldn't be read. */
    data class SharedScripts(val entries: List<ScriptCatalog.Entry> = emptyList(), val loading: Boolean = false, val error: String? = null)

    private val _shared = MutableStateFlow(SharedScripts())
    val sharedScripts: StateFlow<SharedScripts> = _shared.asStateFlow()

    /** Reads the list: the copy kept for an hour, or GitHub when [refresh]. A failure keeps what was there. */
    fun loadSharedScripts(refresh: Boolean = false) {
        _shared.value = _shared.value.copy(loading = true, error = null)
        scope.launch(Dispatchers.IO) {
            _shared.value = runCatching { SharedScripts(scriptCatalog.entries(refresh)) }.getOrElse {
                SharedScripts(_shared.value.entries.ifEmpty { scriptCatalog.cached().orEmpty() }, error = it.message ?: it.javaClass.simpleName)
            }
        }
    }

    /** Downloaded code by SHA-256: read once for a script's sheet, then installed from it. */
    private val sharedCode = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun sharedScriptCode(e: ScriptCatalog.Entry): String =
        withContext(Dispatchers.IO) { sharedCode.getOrPut(e.sha256) { scriptCatalog.download(e) } }

    /** Installs or updates [e]; [allow] is the answer for what it declares, [replace] overwrites your edits. */
    fun installSharedScript(e: ScriptCatalog.Entry, allow: Map<String, Boolean> = emptyMap(), replace: Boolean = false) {
        fileOp("install") {
            val code = sharedScriptCode(e)
            val update = scripts.byName(e.name) != null
            val s = scriptCatalog.install(e, code, allow, replace)
            refreshScriptCaches()
            notice(if (update) "Updated ${s.name}.${s.lang.ext} to ${e.version}" else "Installed ${s.name}.${s.lang.ext}" + if (e.command) " — run it as '${s.name}'" else "")
        }
    }

    /** Scripts list + command names, refreshed after edits/imports (not only when the log changes). */
    suspend fun refreshScriptCaches() {
        val list = withContext(Dispatchers.IO) { scripts.all() }
        scriptList.value = list
        host.setCommandScripts(list)
    }
}
