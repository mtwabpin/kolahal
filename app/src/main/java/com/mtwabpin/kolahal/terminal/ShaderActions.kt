/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import android.content.Context
import android.net.Uri
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.notifications.NotificationActions
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptFiles
import com.mtwabpin.kolahal.script.ScriptFolder
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ShaderCatalog
import com.mtwabpin.kolahal.script.ShaderFit
import com.mtwabpin.kolahal.script.ShaderMeta
import com.mtwabpin.kolahal.script.ShaderParam
import com.mtwabpin.kolahal.script.ShaderParams
import com.mtwabpin.kolahal.script.ShaderPlay
import com.mtwabpin.kolahal.script.ShaderSafety
import com.mtwabpin.kolahal.script.ShaderStore
import com.mtwabpin.kolahal.script.ShaderSuits
import com.mtwabpin.kolahal.script.ShaderSurface
import com.mtwabpin.kolahal.script.ShaderValues
import com.mtwabpin.kolahal.script.ShaderWords
import com.mtwabpin.kolahal.script.Shaders
import com.mtwabpin.kolahal.script.SharedShader
import com.mtwabpin.kolahal.status.DeviceStatus
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * One shader as the Shaders screen shows it.
 * - [bundled]: it comes with Kolahal. [yours]: the user has a file of it, which is then the one used.
 * - [turnedOff]: the watchdog or the safe mode turned it off ([ShaderSafety]), for the reason [why] when
 *   that is known.
 * - [origin]: for one from Get more shaders, whether it was edited and whether the list has a newer
 *   version, which is then [latest].
 */
data class ShaderRow(
    val name: String,
    val bundled: Boolean,
    val yours: Boolean,
    val code: String,
    val turnedOff: Boolean = false,
    val why: String? = null,
    val origin: ScriptCatalog.Copy? = null,
    val latest: SharedShader? = null,
) {
    /** What its header says. Read once, as the rows are made. */
    val meta: ShaderMeta = ShaderMeta.parse(code)
    val title: String get() = meta.title ?: name
}

/**
 * What the Shaders screen and Get more shaders do: turn a shader on or off, import, check, copy and
 * remove the user's own, and install shared ones.
 *
 * The shared lists are those of Get more scripts ([ScriptActions.sharedScripts]): one download has
 * both. The view model holds one as `shaderActions`.
 */
class ShaderActions(
    private val context: Context,
    private val scripts: ScriptActions,
    private val host: ScriptActions.Host,
) {
    private val config = Config.get(context)
    private val safety by lazy { ShaderSafety.get(context) }
    private val store by lazy { ShaderStore(context, scripts.scriptCatalog) }
    private val folder by lazy { ScriptFolder(context) }

    // ---- the list --------------------------------------------------------------------------

    /** Grows when a setting changes: [inUse] may be another shader then. */
    val settings: StateFlow<Int> get() = config.version

    /** Grows when a shader is turned off by itself, or may be drawn again. */
    val trips: StateFlow<Int> get() = safety.version

    /** The shader that is set, or [ShaderCatalog.OFF]. */
    fun inUse(): String = config[Keys.SCREEN_SHADER]

    /** Every shader, the bundled ones first. Reads files: not on the main thread. */
    fun rows(): List<ShaderRow> {
        val latest = store.cached()
        val tripped = safety.tripped()
        return Shaders.entries().mapNotNull { e ->
            val code = Shaders.source(context, e.name) ?: return@mapNotNull null
            val off = tripped == ShaderSafety.id(e.name, code)
            val origin = if (e.yours) store.copy(e.name, code, latest) else null
            ShaderRow(
                name = e.name,
                bundled = e.bundled,
                yours = e.yours,
                code = code,
                turnedOff = off,
                why = if (off) safety.why() else null,
                origin = origin,
                latest = if (origin != null) store.entry(e.name, latest) else null,
            )
        }
    }

    private val _problems = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Why each shader that was tried last couldn't be used, by name, until it can or its file changes. */
    val problems: StateFlow<Map<String, String>> = _problems.asStateFlow()

    private fun forgetProblems(names: Collection<String>) = _problems.update { it - names.toSet() }

    // ---- using one -------------------------------------------------------------------------

    /** Turns [name] on, if it compiles. Otherwise the one that is on stays, and the screen says why. */
    fun use(name: String) = host.io {
        val why = Shaders.use(context, name)
        if (why == null) {
            forgetProblems(listOf(name))
            wallpaperWarning(context, name)?.let { host.notice(it) }
        } else {
            _problems.update { it + (name to why) }
            host.notice("Shader $name: $why", error = true)
        }
    }

    /** Plays the one-off shader [name] over home, once it is in front. */
    fun play(name: String) {
        ShaderPlay.play(name, null)
    }

    /** How much of the wallpaper shows behind the log, in percent. */
    fun wallpaper(): Int = config[Keys.LOG_WALLPAPER]

    /** No shader. `shader on` turns the last one on again. */
    fun off() = host.io { Shaders.off(context) }

    /** Sets [param] of [name], and keeps it. */
    fun setParam(name: String, param: ShaderParam, value: Float) = ShaderParams.set(context, name, param, value)

    /** [param] of [name] follows the value [id], with [formula] if there is one; null for no value. */
    fun follow(name: String, param: ShaderParam, id: String?, formula: String? = null) =
        ShaderParams.setFollow(context, name, param, id, formula)

    /** The one-off shader [name] plays when a notification has one of [words], with commas between; blank for never. */
    fun setWords(name: String, words: String) = ShaderWords.set(context, name, words)

    /** Whether Kolahal may see notifications. */
    fun seesNotifications(): Boolean = NotificationActions.isEnabled(context)

    /** The values there are to follow: Kolahal's, and those of the scripts that are on. */
    suspend fun sources(): List<ShaderValues.Source> = withContext(Dispatchers.IO) { sources(context) }

    /** Which shaders' parameters follow the values of [script], by the value's name: "rain (amount)". */
    suspend fun followers(script: String): Map<String, List<String>> = withContext(Dispatchers.IO) {
        Shaders.entries().flatMap { e -> Shaders.params(context, e.name).map { e.name to it } }
            .mapNotNull { (shader, param) ->
                val id = ShaderParams.follow(shader, param)?.takeIf { it.startsWith("$script.") } ?: return@mapNotNull null
                id.substringAfter('.') to "$shader (${param.name})"
            }
            .groupBy({ it.first }, { it.second })
    }

    /** The phone as it is now, for the values that Kolahal gives. */
    fun phone(): ShaderValues.Phone = phone(context)

    /** The parameters of [name] are as it comes. */
    fun resetParams(name: String) = ShaderParams.reset(context, name)

    /** Compiles [name] and reads its uniforms. Null when it can't be read. */
    suspend fun check(name: String): Shaders.Check? =
        withContext(Dispatchers.IO) { Shaders.check(context, name) }

    // ---- files -----------------------------------------------------------------------------

    /** Add shader → Import a file: each `.agsl` file becomes one of the user's own shaders. */
    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        host.fileOp("import") {
            val r = ScriptFiles(context).importShaders(uris)
            forgetProblems(r.shaders)
            host.notice(
                listOfNotNull(
                    r.shaders.takeIf { it.isNotEmpty() }?.let { "Added ${it.joinToString()}" },
                    r.skipped.takeIf { it.isNotEmpty() }?.let { "skipped ${it.joinToString()}" },
                ).joinToString(" · ").replaceFirstChar { it.uppercase() },
                error = r.shaders.isEmpty(),
            )
        }
    }

    /** Add shader → Read the script folder again. Without a script folder, it asks for one. */
    fun readFolder() {
        host.fileOp("reading the script folder") {
            if (folder.uri == null) return@fileOp pickFolder("Pick a script folder, then read it again")
            val (found, skipped) = ShaderCommands.readAgain(context, folder)
            _problems.value = emptyMap()
            host.notice(
                listOfNotNull(found, skipped).joinToString(" · "),
                error = skipped != null
            )
        }
    }

    /** Writes [name] into the script folder's `shaders` folder. Without a script folder, it asks for one. */
    fun copyToFolder(name: String) {
        host.fileOp("copy") {
            val code = Shaders.source(context, name)
                ?: throw IllegalStateException("$name can't be read")
            if (folder.uri == null) return@fileOp pickFolder("Pick a script folder, then copy $name again")
            host.notice("Copied $name to ${folder.exportShader(name, code)} in the script folder")
        }
    }

    private fun pickFolder(text: String) {
        host.notice(text)
        host.emit(UiEvent.PickScriptFolder)
    }

    /** Removes the user's shader [name]. Undo puts it back, with where it came from, and on if it was. */
    fun remove(name: String) {
        host.fileOp("remove") {
            val code = Shaders.own(context, name) ?: return@fileOp
            val from = store.recorded(name)
            val was = inUse() == name
            if (!Shaders.remove(context, name)) throw IllegalStateException("$name can't be removed")
            forgetProblems(listOf(name))
            host.notice(
                "Removed $name" + if (name in ShaderCatalog.BUNDLED) " · the one that comes with Kolahal is used again" else "",
                undo = {
                    putBack(name, code, from)
                    if (was) host.io { config.set(Keys.SCREEN_SHADER, name) }
                },
            )
        }
    }

    /** Undo: the user's shader [name] gets the code [code] and the record [from] again. */
    private fun putBack(name: String, code: String, from: String?) = host.io {
        store.record(name, from)
        Shaders.save(context, name, code)
        forgetProblems(listOf(name))
    }

    // ---- Get more shaders --------------------------------------------------------------------

    /** The shared lists, with the scripts' ([ScriptActions.SharedScripts.shaders] are the shaders). */
    val shared: StateFlow<ScriptActions.SharedScripts> get() = scripts.sharedScripts

    /** Reads the shared lists again. */
    fun refreshShared() = scripts.loadSharedScripts(refresh = true)

    /** Where [e] stands, and why its name is taken when it is. Reads a file: not on the main thread. */
    fun state(e: SharedShader): Pair<ScriptCatalog.State, String?> {
        val state = store.state(e)
        return state to if (state == ScriptCatalog.State.NAME_TAKEN) store.taken(e) else null
    }

    /** The source that the user's shader [name] was installed from, if it was. */
    fun installedFrom(name: String): String? = store.installedFrom(name)

    /** The code of [e], downloaded once ([ScriptActions.sharedCode]) and checked against the list. */
    suspend fun sharedCode(e: SharedShader): String = withContext(Dispatchers.IO) {
        scripts.sharedCode.getOrPut(e.sha256) { store.download(e) }
    }

    /**
     * Installs [e], or updates the copy installed from its source; it isn't turned on. An update can be
     * undone. [replace]: overwrite the user's edits.
     */
    fun install(e: SharedShader, replace: Boolean = false) {
        host.fileOp("install") {
            val code = sharedCode(e)
            val before = Shaders.own(context, e.name)
            val from = store.recorded(e.name)
            store.install(e, code, replace)
            forgetProblems(listOf(e.name))
            if (before == null) host.notice("Installed ${e.name}")
            else host.notice(
                "Updated ${e.name}" + e.version.ifBlank { null }?.let { " to $it" }.orEmpty(),
                undo = { putBack(e.name, before, from) },
            )
        }
    }

    companion object {
        /** That shader [name] doesn't go with a wallpaper, while enough of one shows. Reads a file: not on the main thread. */
        fun wallpaperWarning(context: Context, name: String): String? {
            val shows = Config.get(context)[Keys.LOG_WALLPAPER]
            val meta = Shaders.source(context, name)?.let(ShaderMeta::parse) ?: return null
            return wallpaperWarning(meta, name, shows)
        }

        /** The same for a shader with [meta], while [shows] percent of the wallpaper does. */
        fun wallpaperWarning(meta: ShaderMeta, name: String, shows: Int): String? =
            if (shows <= ShaderSuits.WALLPAPER_SHOWS || meta.suits[ShaderSurface.WALLPAPER] != ShaderFit.POOR) null
            else "${meta.title ?: name} doesn't work well with a wallpaper, and yours shows at $shows%"

        fun phone(context: Context): ShaderValues.Phone {
            val status = DeviceStatus.get(context).status.value
            return ShaderValues.Phone((status.batteryPct ?: 100) / 100f, status.charging, LocalTime.now())
        }

        /** [ShaderActions.sources]. Reads scripts: not on the main thread. */
        fun sources(context: Context): List<ShaderValues.Source> {
            val phone = phone(context)
            val given = ShaderValues.given.value
            val scripts = ScriptRepository.get(context).all().filter { it.enabled }
                .flatMap { s -> ScriptMeta.parse(s.code).gives.map { ShaderValues.id(s.name, it) } }.sorted()
            return (ShaderValues.BUILT_IN.keys + scripts).map { id ->
                ShaderValues.Source(id, ShaderValues.label(id), ShaderValues.of(id, given, phone))
            }
        }
    }
}
