package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.Stores
import androidx.core.content.edit
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
private const val KEY_BANNER_HIDDEN = "banner_hidden"

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
        /** Shows [text] on the page and logs it. With [undo], the page offers Undo for a while. */
        fun notice(text: String, error: Boolean = false, undo: (() -> Unit)? = null)
        fun show(text: String)
        fun emit(event: UiEvent)
        fun openEditor(script: Script)
        fun setCommandScripts(list: List<Script>)
    }

    private val scripts = ScriptRepository.get(context)
    private val logs = LogRepository.get(context)

    private fun io(block: suspend () -> Unit) = host.io(block)
    private fun fileOp(label: String, block: suspend () -> Unit) = host.fileOp(label, block)
    private fun notice(text: String, error: Boolean = false, undo: (() -> Unit)? = null) = host.notice(text, error, undo)
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

    /** Installs [entry] from the library. If you have a copy already, it updates it as [restore] does. */
    fun installFromLibrary(entry: ScriptLibrary.Entry) {
        fileOp("install") {
            scripts.byName(entry.name)?.let { return@fileOp restoreNow(listOf(Restore(it, library = entry))) }
            val s = changes.install(library, entry)
            refreshScriptCaches()
            notice("Installed ${s.name}.${s.lang.ext}" + if (entry.command) " — run it as '${s.name}'" else "")
        }
    }

    // ---- updates and resets of library and shared copies ----------------------------------

    /** A copy to bring to its source's version: [library]'s, or [shared]'s from Get more scripts. */
    class Restore(val script: Script, val library: ScriptLibrary.Entry? = null, val shared: ScriptCatalog.Entry? = null)

    /**
     * Brings each copy in [list] to the library's or the shared version. Only the code changes: its
     * timers, options, access and pinning stay. The notice offers Undo, which puts every copy back as
     * it was, with the text it was installed from, so it shows as edited or older again.
     */
    fun restore(list: List<Restore>) {
        if (list.isNotEmpty()) fileOp("update") { restoreNow(list) }
    }

    /** A copy as it was before [restore], for Undo. */
    private class Before(val script: Script, val libraryOriginal: String?, val sharedOrigin: String?, val secrets: Map<String, String>)

    private suspend fun restoreNow(list: List<Restore>) {
        val done = ArrayList<Before>()
        val failed = ArrayList<String>()
        // The words for one copy: an edited copy with nothing newer is reset, anything else is updated.
        var reset: String? = null
        for (r in list) {
            val now = scripts.byId(r.script.id) ?: continue
            val before = Before(now, library.recorded(now.name), scriptCatalog.recorded(now.name), secretStore.kept(now.name))
            runCatching {
                when {
                    r.library != null -> {
                        val state = library.state(r.library, listOf(now))
                        if (state == ScriptLibrary.State.MODIFIED && !library.hasNewer(r.library, state)) reset = "library"
                        changes.install(library, r.library)
                    }
                    r.shared != null -> {
                        val copy = scriptCatalog.copy(now, r.shared)
                        if (copy != null && copy.edited && !copy.newer) reset = "shared"
                        scriptCatalog.install(r.shared, sharedScriptCode(r.shared), replace = true)
                    }
                    else -> throw IllegalStateException("it has nothing to update from")
                }
            }.onSuccess { done += before }.onFailure { failed += "${now.name} (${it.message ?: it.javaClass.simpleName})" }
        }
        refreshScriptCaches()
        if (done.isEmpty() && failed.isEmpty()) return
        val file = done.singleOrNull()?.script?.let { "${it.name}.${it.lang.ext}" }
        val text = listOfNotNull(
            when {
                done.isEmpty() -> null
                file != null && list.size == 1 && reset != null -> "$file is back to the $reset version"
                file != null && list.size == 1 -> "$file is updated"
                else -> "${done.size} ${if (done.size == 1) "script" else "scripts"} updated"
            },
            if (failed.isEmpty()) null else (if (done.isEmpty()) "Not updated: " else "not updated: ") + failed.joinToString(", "),
        ).joinToString(" · ")
        if (done.isEmpty()) return notice(text, error = true)
        notice(text, error = failed.isNotEmpty(), undo = { io { putBack(done) } })
    }

    /** Undo of [restore]: each copy gets its code, access and recorded original back. */
    private suspend fun putBack(done: List<Before>) {
        for (b in done) {
            scripts.byId(b.script.id) ?: continue
            scripts.putBack(b.script)
            library.record(b.script.name, b.libraryOriginal)
            scriptCatalog.record(b.script.name, b.sharedOrigin)
            secretStore.putBack(b.script.name, b.secrets)
            scripts.byId(b.script.id)?.let { ScriptScheduler.sync(context, it) }
        }
        logs.onStatusChanged()
        refreshScriptCaches()
        val one = done.singleOrNull()?.script
        notice(if (one != null) "${one.name}.${one.lang.ext} is as it was" else "${done.size} scripts are as they were")
    }

    // ---- notes on the Scripts tab ----------------------------------------------------------

    /**
     * What the Scripts tab notes above its list:
     * - [selfUpdate]: the library copies Kolahal updated by itself, until the user closes the note;
     * - [bannerHiddenFor]: the copies that the banner was about when the user closed it. It shows
     *   again when they change.
     */
    data class PageNotes(val selfUpdate: ScriptLibrary.SelfUpdate?, val bannerHiddenFor: String?)

    private val pagePrefs by lazy { Stores.SCRIPTS_PAGE.open(context) }

    private val _pageNotes = MutableStateFlow<PageNotes?>(null)
    /** Null until [loadPageNotes] read them. */
    val pageNotes: StateFlow<PageNotes?> = _pageNotes.asStateFlow()

    fun loadPageNotes() = io { _pageNotes.value = PageNotes(library.selfUpdate(), pagePrefs.getString(KEY_BANNER_HIDDEN, null)) }

    /** The user closed the note about the copies Kolahal updated by itself. */
    fun dismissSelfUpdate() {
        _pageNotes.update { it?.copy(selfUpdate = null) }
        io { library.dismissSelfUpdate() }
    }

    /** The user closed the banner about copies that differ. [signature] names them and their states. */
    fun hideBanner(signature: String) {
        _pageNotes.update { it?.copy(bannerHiddenFor = signature) }
        io { pagePrefs.edit { putString(KEY_BANNER_HIDDEN, signature) } }
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
