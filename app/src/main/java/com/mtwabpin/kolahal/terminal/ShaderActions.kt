/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import android.content.Context
import android.net.Uri
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.Lab
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.notifications.NotificationActions
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptFiles
import com.mtwabpin.kolahal.script.ScriptFolder
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.shader.ParamTarget
import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.ShaderFit
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderOverApps
import com.mtwabpin.kolahal.shader.ShaderParam
import com.mtwabpin.kolahal.shader.ShaderParams
import com.mtwabpin.kolahal.shader.ShaderPlay
import com.mtwabpin.kolahal.shader.ShaderSafety
import com.mtwabpin.kolahal.shader.ShaderStore
import com.mtwabpin.kolahal.shader.ShaderSuits
import com.mtwabpin.kolahal.shader.ShaderSurface
import com.mtwabpin.kolahal.shader.ShaderValues
import com.mtwabpin.kolahal.shader.ShaderWords
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.shader.SharedShader
import com.mtwabpin.kolahal.shader.UsageValues
import com.mtwabpin.kolahal.shader.UsageWatch
import com.mtwabpin.kolahal.shader.WallpaperChoice
import com.mtwabpin.kolahal.status.DeviceStatus
import com.mtwabpin.kolahal.terminal.commands.ShaderCommands
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
 * A shader open in the editor: its [name] and its [code] as it was read or last saved. [bundled]: it comes with
 * Kolahal and the user has no file of it yet ([ShaderCatalog.Edit.COPY]); the first save makes one.
 */
data class ShaderEdit(val name: String, val code: String, val bundled: Boolean)

/**
 * What the Shaders screen and Get more shaders do: turn a shader on or off, import, check, edit, copy and
 * remove the user's own, and install shared ones.
 *
 * The shared lists are those of Get more scripts ([ScriptActions.sharedScripts]): one download has
 * both. The view model holds one as `shaderActions`.
 */
class ShaderActions(
    private val context: Context,
    private val scripts: ScriptActions,
    private val host: ScriptActions.Host,
) : ParamTarget {
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

    /**
     * Plays the one-off shader [name] over the Shaders screen, or home once it is in front. One that was turned off
     * is on again by this tap, as a shader over home is by the tap that uses it, but only when it plays. False when
     * another plays: this one is left out, and stays off if it was.
     */
    fun play(name: String): Boolean {
        val safety = ShaderSafety.get(context)
        return ShaderPlay.play(name, null) {
            if (safety.tripped()?.let(ShaderSafety::nameOf) == name) safety.turnedOn()
        }
    }

    /**
     * Play on the Shaders screen: [name] plays over the screen, which stays. As `shader play`: one that doesn't
     * compile doesn't play, and the screen says why; while another plays, this one is left out, and the screen says so.
     */
    fun playHere(name: String) = host.io {
        val why = Shaders.problem(context, name)
        if (why != null) {
            _problems.update { it + (name to why) }
            host.notice(Shaders.problemLine(name, why), error = true)
        } else if (!play(name)) host.notice(ShaderPlay.PLAYING)
    }

    /** Whether Kolahal's live wallpaper is the phone's. */
    suspend fun isWallpaper(): Boolean = withContext(Dispatchers.IO) { WallpaperChoice.isWallpaper(context) }

    /**
     * [name] is the live wallpaper's shader, shown in Android's own preview, where people set it: always,
     * also when Kolahal's wallpaper is the phone's already, as `wallpaper shader <name>` does. There the
     * person sees it and says where it goes, the home screen, the lock screen or both.
     */
    fun setWallpaper(name: String) = host.io {
        WallpaperChoice.set(context, name)
        if (runCatching { context.startActivity(WallpaperChoice.preview(context)) }.isFailure) {
            host.notice("This phone has no preview for live wallpapers", error = true)
        }
    }

    /** How much of the wallpaper shows behind the log, in percent. */
    fun wallpaper(): Int = config[Keys.LOG_WALLPAPER]

    /** No shader. `shader on` turns the last one on again. */
    fun off() = host.io { Shaders.off(context) }

    // What its parameters are, as home uses them ([ParamTarget]).
    override val values: StateFlow<Map<String, Map<String, String>>> get() = ShaderParams.values
    override val follows: StateFlow<Map<String, Map<String, String>>> get() = ShaderParams.follows
    override val formulas: StateFlow<Map<String, Map<String, String>>> get() = ShaderParams.formulas

    /** Sets [param] of [name], and keeps it. */
    override fun setParam(name: String, param: ShaderParam, value: Float) = ShaderParams.set(context, name, param, value)

    override fun tryOut(name: String, param: ShaderParam, value: Float) = ShaderParams.tryOut(name, param, value)

    /** [param] of [name] follows the value [id], with [formula] if there is one; null for no value. */
    override fun follow(name: String, param: ShaderParam, id: String?, formula: String?) =
        ShaderParams.setFollow(context, name, param, id, formula)

    /** The one-off shader [name] plays when a notification has one of [words], with commas between; blank for never. */
    fun setWords(name: String, words: String) = ShaderWords.set(context, name, words)

    /** Whether Kolahal may see notifications. */
    fun seesNotifications(): Boolean = NotificationActions.isEnabled(context)

    /** Whether this build, this Android and this phone can play a one-off shader over other apps ([ShaderOverApps.offered]). */
    val overAppsOffered: Boolean get() = ShaderOverApps.offered(context)

    /** Whether a word's one-off shader plays over the app in front (`screen.shader-over-apps`). */
    fun overApps(): Boolean = config[Keys.SHADER_OVER_APPS]

    fun setOverApps(on: Boolean) = host.io { config.set(Keys.SHADER_OVER_APPS, if (on) "on" else "off") }

    /** Opens Android's page where Display over other apps is given, as `allow overlay` does. */
    fun askOverApps() {
        if (runCatching { context.startActivity(ShaderOverApps.accessIntent(context)) }.isFailure) {
            host.notice("Can't open display over other apps", error = true)
        }
    }

    /**
     * The values there are to follow, for the Follow menu: Kolahal's, and those of the scripts that are on. The rows
     * of days and blocks ([UsageValues.series]) aren't there: `shader follow` and a header's `@follow` take them.
     */
    override suspend fun sources(): List<ShaderValues.Source> = withContext(Dispatchers.IO) { sources(context, series = false, fresh = true) }

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
    override fun phone(): ShaderValues.Phone = phone(context)

    /** The parameters of [name] are as it comes. */
    override fun resetParams(name: String) = ShaderParams.reset(context, name)

    /** Whether Lab's pictures are on ([Lab.Feature.PICTURE]): a scene's details then have Make a picture. */
    fun picturesOn(): Boolean = Lab.has(config, Lab.Feature.PICTURE)

    /** Compiles [name] and reads its uniforms. Null when it can't be read. */
    suspend fun check(name: String): Shaders.Check? =
        withContext(Dispatchers.IO) { Shaders.check(context, name) }

    // ---- the editor ------------------------------------------------------------------------

    private val _editing = MutableStateFlow<ShaderEdit?>(null)

    /** The shader open in the editor, or null. Home's window shows the editor while there is one. */
    val editing: StateFlow<ShaderEdit?> = _editing.asStateFlow()

    /**
     * Opens shader [name] in the editor: the user's file, or the code of the one that comes with Kolahal
     * ([ShaderCatalog.edit]). Reads a file, on the view model's thread.
     */
    fun edit(name: String) = host.io {
        val how = ShaderCatalog.edit(name, Shaders.userNames)
            ?: return@io host.notice("No shader '$name'", error = true)
        val code = Shaders.source(context, name)
            ?: return@io host.notice("Shader $name can't be read", error = true)
        _editing.value = ShaderEdit(name, code, bundled = how == ShaderCatalog.Edit.COPY)
    }

    /**
     * Saves [code] as the user's shader [name] ([Shaders.save]), so home, the live wallpaper and the Shaders screen
     * read it again, and checks it as `shader check` does. When the script folder has a file of it, that file gets
     * the same code ([ShaderCommands.saveInFolder]); when it can't, the first of the check's warnings says so.
     * What the check found; null when the file can't be read. Throws when it can't be saved.
     */
    suspend fun save(name: String, code: String): Shaders.Check? = withContext(Dispatchers.IO) {
        Shaders.save(context, name, code)
        forgetProblems(listOf(name))
        // The editor's copy is the saved one now, and the user's own.
        _editing.update { e -> if (e?.name == name) e.copy(code = code, bundled = false) else e }
        val inFolder = ShaderCommands.saveInFolder(folder, name, code)
        val check = Shaders.check(context, name)
        if (check == null || inFolder == null) check
        else with(check) { Shaders.Check(problem, sets, unset, animated, params, pace, listOf(inFolder) + warnings, line) }
    }

    /**
     * Closes the editor. [saved]: the editor saved its changes on the way out ([save]), and the page you return to
     * says so, and what the check found ([check]).
     */
    fun closeEditor(saved: Boolean = false, check: Shaders.Check? = null) {
        val edit = _editing.value ?: return
        _editing.value = null
        if (saved) {
            val problem = check?.problem ?: check?.warnings?.firstOrNull()
            host.notice(
                listOfNotNull("Saved ${edit.name}.${ShaderCatalog.EXT}", problem).joinToString(" · "),
                error = problem != null,
            )
        }
    }

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

    /** The templates to start a shader from ([ShaderCatalog.TEMPLATES]), each with what its header says. Reads files: not on the main thread. */
    fun templates(): List<Pair<String, ShaderMeta>> =
        ShaderCatalog.TEMPLATES.map { it to ShaderMeta.parse(Shaders.bundled(context, it)) }

    /**
     * Add shader → Start from a template: a shader of the user's from [template], to edit as a file
     * ([ShaderCommands.fromTemplate]). Without a script folder it is added all the same, and the page
     * asks for a folder, as Copy does.
     */
    fun startFrom(template: String) {
        host.fileOp("copy") {
            val (name, path) = ShaderCommands.fromTemplate(context, folder, template)
            forgetProblems(listOf(name))
            if (path == null) pickFolder("Added $name\nPick a script folder, then Save to folder in its details writes its file")
            else host.notice("Added $name to $path\n'shader reload' reads a file you changed elsewhere")
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
                "Removed $name" + if (name in ShaderCatalog.BUNDLED) "\nThe one that comes with Kolahal is used again" else "",
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

        /**
         * The same for a shader with [meta], while [shows] percent of the wallpaper does. A scene hides
         * the wallpaper while it is on ([WallpaperChoice.homeShare]): it gets no warning.
         */
        fun wallpaperWarning(meta: ShaderMeta, name: String, shows: Int): String? =
            if (WallpaperChoice.homeShare(shows, meta) <= ShaderSuits.WALLPAPER_SHOWS ||
                meta.suits[ShaderSurface.WALLPAPER] != ShaderFit.POOR
            ) null
            else "${meta.title ?: name} doesn't work well with a wallpaper\nYours shows at $shows%"

        fun phone(context: Context): ShaderValues.Phone {
            val status = DeviceStatus.get(context).status.value
            return ShaderValues.Phone((status.batteryPct ?: 100) / 100f, status.charging, LocalTime.now())
        }

        /**
         * The values there are to follow: Kolahal's, the rows of days and blocks of the phone's use with [series], and
         * those of the scripts that are on. [fresh]: the phone's use is read now where no shader follows it, for a list
         * that shows the values ([UsageWatch] reads it only for what is followed). Reads scripts: not on the main thread.
         */
        fun sources(context: Context, series: Boolean = true, fresh: Boolean = false): List<ShaderValues.Source> {
            val phone = phone(context)
            val followed = ShaderValues.given.value
            val given = if (fresh && UsageValues.IDS.any { it !in followed }) {
                runCatching { UsageWatch.values(context, UsageValues.IDS) }.getOrDefault(emptyMap()) + followed
            } else followed
            val scripts = ScriptRepository.get(context).all().filter { it.enabled && it.name !in UsageValues.GROUPS }
                .flatMap { s -> ScriptMeta.parse(s.code).gives.map { ShaderValues.id(s.name, it) } }.sorted()
            val use = if (series) UsageValues.IDS else UsageValues.LISTED.keys
            return (ShaderValues.BUILT_IN.keys + use + scripts).map { id ->
                ShaderValues.Source(id, ShaderValues.label(id), ShaderValues.of(id, given, phone))
            }
        }
    }
}
