/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import android.content.Context
import android.os.Build
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptFolder
import com.mtwabpin.kolahal.script.ScriptSources
import com.mtwabpin.kolahal.script.ShaderCatalog
import com.mtwabpin.kolahal.script.ShaderClock
import com.mtwabpin.kolahal.script.ShaderMeta
import com.mtwabpin.kolahal.script.ShaderParam
import com.mtwabpin.kolahal.script.ShaderParams
import com.mtwabpin.kolahal.script.ShaderPlay
import com.mtwabpin.kolahal.script.ShaderSafety
import com.mtwabpin.kolahal.script.ShaderStore
import com.mtwabpin.kolahal.script.ShaderValues
import com.mtwabpin.kolahal.script.Shaders
import com.mtwabpin.kolahal.script.SharedShader

/**
 * The `shader` command: the shader over home and the full-screen screens (`screen.shader`).
 * - `shader` lists them; `shader <name>` (or `use`) turns one on, if it compiles: the one that is on
 *   stays when it doesn't.
 * - `shader off` always works: the prompt takes the keys even when nothing can be read. `shader on`
 *   turns the last one on again, also after it was turned off for being slow.
 * - `set` changes a shader's parameters and `reset` puts them back ([ShaderParam]).
 * - `follow` makes a parameter follow a value, and `values` lists the values ([ShaderValues]).
 * - `play` plays a one-off shader ([ShaderPlay]).
 * - `get` lists the shared shaders and installs one; `update` updates the installed ones.
 * - `check`, `reload`, `copy` and `remove` work with the user's own files.
 *
 * On Android 12 and older, shaders are listed but can't be used.
 */
class ShaderCommands(
    context: Context,
    /** Runs slow work (the script folder) outside the serial command queue. Failures are logged. */
    private val background: (label: String, block: () -> Unit) -> Unit,
) : CommandGroup(context) {
    private val conf = Config.get(context)
    private val folder by lazy { ScriptFolder(context) }
    private val catalog by lazy { ScriptCatalog(context) }
    private val store by lazy { ShaderStore(context, catalog) }

    fun run(args: List<String>) {
        val sub = args.firstOrNull()?.lowercase().orEmpty()
        val name = args.getOrNull(1)?.lowercase()
        when (sub) {
            "", "list", "ls" -> list()
            "use" -> use(name ?: return usage("shader"))
            "on" -> on()
            ShaderCatalog.OFF -> off()
            "set" -> set(args.drop(1))
            "reset" -> reset(args.drop(1))
            "follow" -> follow(args.drop(1))
            "values" -> values()
            "play" -> play(name ?: return usage("shader"), args.getOrNull(2))
            // These use the network, so they run outside the serial command queue.
            "get" -> background("shader get") { getShared(args.drop(1)) }
            "update" -> background("shader update") { update(args.drop(1)) }
            "check" -> check(name ?: conf[Keys.SCREEN_SHADER].takeUnless { it == ShaderCatalog.OFF } ?: return usage("shader"))
            "reload" -> reload()
            "copy" -> copy(name ?: return usage("shader"))
            "remove", "rm" -> remove(name ?: return usage("shader"))
            else -> use(sub)
        }
    }

    private fun list() {
        if (!Shaders.supported) needs()
        val current = conf[Keys.SCREEN_SHADER]
        val tripped = ShaderSafety.get(context).tripped()
        Shaders.entries().forEach { e ->
            val source = Shaders.source(context, e.name)
            val meta = source?.let(ShaderMeta::parse)
            val description = meta?.description
            val tags = buildList {
                if (e.name == current) add(StyledText.tag(StyledText.ColorRole.ACCENT, "on"))
                if (source != null && tripped == ShaderSafety.id(e.name, source)) {
                    add(StyledText.tag(StyledText.ColorRole.WARN, "turned off"))
                }
                if (meta?.once != null) add(StyledText.tag(StyledText.ColorRole.INFO, "one-off"))
                if (e.yours) add(StyledText.tag(StyledText.ColorRole.MUTED, "yours"))
                if (!Shaders.supported) add(StyledText.tag(StyledText.ColorRole.MUTED, "needs Android 13"))
            }
            out(
                (listOf(e.name) + tags).joinToString(" "),
                LogContext.of(
                    LogContext.RUN to if (meta?.once != null) "shader play ${e.name}" else "shader use ${e.name}",
                    LogContext.DESC to (description ?: if (e.yours) "Your shader" else ""),
                ) + LogContext.ITEM,
            )
        }
        info("Tap a shader to use it · 'shader off' turns it off · 'shader get' lists shared ones · your own: put .agsl files in the script folder's ${Shaders.DIR} folder, then run 'script import'")
    }

    private fun use(name: String) {
        if (name == ShaderCatalog.OFF) return off()
        if (!Shaders.exists(name)) return error("No shader '$name' — 'shader' lists them")
        if (!Shaders.supported) return needs()
        Shaders.use(context, name)?.let { return error("Shader $name: $it") }
        info("Shader: $name · 'shader off' turns it off")
        ShaderActions.wallpaperWarning(context, name)?.let { info("$it · 'config set log.wallpaper 0' hides it") }
    }

    private fun play(name: String, time: String?) {
        if (!Shaders.exists(name)) return error("No shader '$name' — 'shader' lists them")
        if (!Shaders.supported) return needs()
        if (!Shaders.once(context, name)) return error("$name isn't a one-off shader: it has no @once line")
        Shaders.problem(context, name)?.let { return error("Shader $name: $it") }
        val seconds = time?.let { it.toFloatOrNull()?.takeIf { s -> s > 0f } ?: return usage("shader") }
        if (!ShaderPlay.play(name, seconds)) info("A shader is playing")
    }

    private fun on() {
        if (!Shaders.supported) return needs()
        val safety = ShaderSafety.get(context)
        val current = conf[Keys.SCREEN_SHADER]
        val name = if (current != ShaderCatalog.OFF) current else safety.last
            ?: return error("No shader was on yet — 'shader use <name>'")
        use(name)
    }

    private fun off() {
        val was = Shaders.off(context) ?: return info("No shader is on")
        info("Shader off · 'shader on' turns $was on again")
    }

    /** `shader set <name> <parameter> <value>`; without a value it says what it is, without a parameter it lists them. */
    private fun set(args: List<String>) {
        val name = args.firstOrNull()?.lowercase() ?: return usage("shader")
        val params = paramsOf(name) ?: return
        if (args.size == 1) return params.forEach { line(name, it) }
        val param = params.find(args[1]) ?: return error("$name has no parameter '${args[1]}' · it has ${params.joinToString { it.name }}")
        if (args.size == 2) return line(name, param)
        val value = param.parse(args[2]) ?: return error("${param.name} can be ${param.range}")
        ShaderParams.set(context, name, param, value)
        info("$name: ${param.name} ${param.text(value)}")
    }

    /** `shader reset <name> [<parameter>]`. */
    private fun reset(args: List<String>) {
        val name = args.firstOrNull()?.lowercase() ?: return usage("shader")
        val params = paramsOf(name) ?: return
        val param = args.getOrNull(1)?.let {
            params.find(it) ?: return error("$name has no parameter '$it' · it has ${params.joinToString { p -> p.name }}")
        }
        ShaderParams.reset(context, name, param)
        info(if (param != null) "$name: ${param.name} ${param.text(param.default)}" else "$name: parameters reset")
    }

    /** `shader follow <name> <parameter> <value>|off`; without a value it says what it follows, without a parameter it lists them. */
    private fun follow(args: List<String>) {
        val name = args.firstOrNull()?.lowercase() ?: return usage("shader")
        val params = paramsOf(name) ?: return
        if (args.size == 1) return params.forEach { line(name, it) }
        val param = params.find(args[1]) ?: return error("$name has no parameter '${args[1]}' · it has ${params.joinToString { it.name }}")
        if (args.size == 2) return line(name, param)
        val id = args[2].lowercase()
        if (id == ShaderCatalog.OFF) {
            ShaderParams.setFollow(context, name, param, null)
            return info("$name: ${param.name} ${param.text(ShaderParams.of(name, param))}")
        }
        if (ShaderActions.sources(context).none { it.id == id }) return error("No value '$id' — 'shader values' lists them")
        ShaderParams.setFollow(context, name, param, id)
        info("$name: ${param.name} follows $id")
    }

    private fun values() {
        ShaderActions.sources(context).forEach { s ->
            out(
                s.id,
                LogContext.of(
                    LogContext.DESC to listOfNotNull(s.value?.let { ShaderValues.shown(s.id, it) } ?: "no value yet", s.label.takeIf { s.id in ShaderValues.BUILT_IN })
                        .joinToString(" · "),
                ) + LogContext.ITEM,
            )
        }
        info("'shader follow <name> <parameter> <value>' makes a shader's parameter follow one")
    }

    /** The parameters of [name]. Null, and it has said so, when there are none. */
    private fun paramsOf(name: String): List<ShaderParam>? {
        if (!Shaders.exists(name)) {
            error("No shader '$name' — 'shader' lists them")
            return null
        }
        val params = Shaders.params(context, name)
        if (params.isEmpty()) info("$name has no parameters")
        return params.takeIf { it.isNotEmpty() }
    }

    private fun List<ShaderParam>.find(name: String) = firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun line(shader: String, param: ShaderParam) = out(
        "${param.name} ${param.text(ShaderParams.now(shader, param, ShaderActions.phone(context)))}",
        LogContext.of(
            LogContext.COMMAND to "shader set $shader ${param.name} ",
            LogContext.DESC to "${param.title} · ${param.range} · default ${param.text(param.default)}" +
                    ShaderParams.follow(shader, param)?.let { " · follows $it" }.orEmpty(),
        ) + LogContext.ITEM,
    )

    private fun check(name: String) {
        if (!Shaders.exists(name)) return error("No shader '$name' — 'shader' lists them")
        val check = Shaders.check(context, name) ?: return error("Shader $name can't be read")
        check.problem?.let { return error("Shader $name: $it") }
        val (first, rest) = checkLines(check).let { it.first() to it.drop(1) }
        info("Shader $name $first")
        rest.forEach { out(it) }
    }

    /** Reads the shader files again: the script folder's, when one is picked, and Kolahal's own. */
    private fun reload() = background("shader") {
        val (found, skipped) = readAgain(context, folder)
        info(found)
        skipped?.let { error(it) }
    }

    // ---- shared shaders --------------------------------------------------------------------

    /**
     * `shader get [<name>|<source>/<name>] [--refresh] [--replace]`, as `script get`:
     * - without a name, lists the shaders of every source, with the source of those not from
     *   kolahal-scripts. A source that can't be read says why; the others still list.
     * - with a name, installs or updates that shader. It isn't turned on: a tap on the line does that.
     */
    private fun getShared(args: List<String>) {
        val refresh = "--refresh" in args
        // `name`, `name.agsl` or `source/name`: the shader's name in lowercase, without its extension.
        val ref = args.firstOrNull { !it.startsWith("--") }?.let { word ->
            val name = word.substringAfterLast('/').lowercase().substringBefore('.')
            if ('/' in word) "${word.substringBeforeLast('/')}/$name" else name
        } ?: return listShared(refresh)
        val sources = catalog.sources()
        if ('/' in ref && ScriptSources.find(sources, ref.substringBeforeLast('/')) == null) {
            return error("No source '${ref.substringBeforeLast('/')}' — 'script sources' lists them")
        }
        fun find(fresh: Boolean): SharedShader? =
            ScriptSources.pick(catalog.listings(fresh).flatMap { it.shaders }, ref, sources)

        val entry = find(refresh) ?: (if (refresh) null else find(true))
        ?: return error("No shared shader '$ref' — 'shader get' lists them")
        when (store.state(entry)) {
            ScriptCatalog.State.NEEDS_NEWER -> error("${entry.name} needs Kolahal ${entry.requires} or newer")
            ScriptCatalog.State.NAME_TAKEN -> error(store.taken(entry))
            ScriptCatalog.State.INSTALLED -> info("${entry.name} is installed and up to date")
            ScriptCatalog.State.MODIFIED ->
                if ("--replace" in args) install(entry, replace = true)
                else error("You edited ${entry.name}: 'shader get $ref --replace' replaces it ('shader copy ${entry.name}' keeps a copy)")

            ScriptCatalog.State.NOT_INSTALLED, ScriptCatalog.State.UPDATE_AVAILABLE -> install(entry)
        }
    }

    private fun listShared(refresh: Boolean) {
        val listings = catalog.listings(refresh)
        val entries = listings.flatMap { it.shaders }
        info("Shared shaders from ${listings.joinToString(", ") { it.source.name }} — 'shader get <name>' installs one")
        listings.forEach { l -> l.error?.let { error("Can't read ${l.source.name}: $it") } }
        if (entries.isEmpty() && listings.none { it.error != null }) info("None yet")
        entries.forEach { e ->
            val state = when (store.state(e)) {
                ScriptCatalog.State.NOT_INSTALLED -> null
                ScriptCatalog.State.INSTALLED -> StyledText.tag(StyledText.ColorRole.GOOD, "installed")
                ScriptCatalog.State.UPDATE_AVAILABLE -> StyledText.tag(StyledText.ColorRole.WARN, "update")
                ScriptCatalog.State.MODIFIED -> StyledText.tag(StyledText.ColorRole.MUTED, "edited")
                ScriptCatalog.State.NAME_TAKEN -> StyledText.tag(
                    StyledText.ColorRole.BAD,
                    store.installedFrom(e.name)?.takeIf { it != e.source }?.let { "installed from $it" }
                        ?: "name taken",
                )

                ScriptCatalog.State.NEEDS_NEWER ->
                    StyledText.tag(StyledText.ColorRole.BAD, "needs Kolahal ${e.requires}")
            }
            val desc = listOfNotNull(
                e.source.takeIf { it != ScriptCatalog.SOURCE }?.let { "from $it" },
                e.title,
                e.version.ifBlank { null }?.let { "v$it" },
                e.author.ifBlank { null }?.let { "by $it" },
                e.desc.ifBlank { null },
            )
            out(
                listOfNotNull(e.name, state).joinToString(" "),
                LogContext.of(
                    LogContext.COMMAND to "shader get ${ScriptSources.ref(entries, e)}",
                    LogContext.DESC to desc.joinToString(" · "),
                ) + LogContext.ITEM,
            )
        }
    }

    /**
     * `shader update [<name>] [--replace]`: reads the lists again, then updates the shader named, or
     * every shader installed from a list that has a newer version. One the user edited is replaced
     * only with its name and `--replace`.
     */
    private fun update(args: List<String>) {
        val name = args.firstOrNull { !it.startsWith("--") }?.lowercase()
        if (name != null && name !in Shaders.userNames) return error("No shader '$name' of yours — 'shader' lists them")
        if (name != null && store.installedFrom(name) == null) return error("$name wasn't installed with 'shader get', so there is nothing to update it from")
        val installed = if (name != null) listOf(name) else Shaders.userNames.filter { store.installedFrom(it) != null }
        if (installed.isEmpty()) return info("No shader was installed with 'shader get'")
        val listings = catalog.listings(refresh = true)
        listings.forEach { l -> l.error?.let { error("Can't read ${l.source.name}: $it") } }
        val latest = listings.flatMap { it.shaders }
        // How many have a newer version, updated or not.
        var newer = 0
        for (n in installed) {
            val e = store.entry(n, latest) ?: continue
            val copy = Shaders.own(context, n)?.let { store.copy(n, it, latest) } ?: continue
            if (!copy.newer) continue
            newer++
            val replace = copy.edited && name != null && "--replace" in args
            if (copy.edited && !replace) {
                error("You edited $n: 'shader update $n --replace' replaces it ('shader copy $n' keeps a copy)")
                continue
            }
            // One that fails doesn't keep the others from being updated.
            runCatching { install(e, replace) }.onFailure { error("$n isn't updated: ${it.reason()}") }
        }
        // A list that couldn't be read has said so: nothing is known of its shaders.
        if (newer == 0 && listings.none { it.error != null }) info(if (name != null) "$name is up to date" else "The installed shaders are up to date")
    }

    /** Downloads and installs [e]. It stays off, unless the one that is on is updated. */
    private fun install(e: SharedShader, replace: Boolean = false) {
        val update = e.name in Shaders.userNames
        store.install(e, store.download(e), replace)
        if (update) info("Updated ${e.name}" + e.version.ifBlank { null }?.let { " to $it" }.orEmpty())
        else info(
            "Installed ${e.name} from ${e.source} · tap to use it, or 'shader ${e.name}'",
            LogContext.of(LogContext.RUN to "shader use ${e.name}"),
        )
    }

    // ---- your own --------------------------------------------------------------------------

    private fun copy(name: String) {
        val source = Shaders.source(context, name) ?: return error("No shader '$name' — 'shader' lists them")
        if (folder.uri == null) return fix("Copying a shader needs a script folder — tap to pick one", "script dir")
        background("shader") {
            val path = folder.exportShader(name, source)
            info("Copied $name to $path in the script folder · edit it there, then run 'shader reload'")
        }
    }

    private fun remove(name: String) {
        if (name !in Shaders.userNames) {
            return error(
                if (name in ShaderCatalog.BUNDLED) "$name comes with Kolahal: there is nothing of yours to remove"
                else "No shader '$name' of yours — 'shader' lists them"
            )
        }
        if (!Shaders.remove(context, name)) return error("Shader $name can't be removed")
        info("Removed your $name" + if (name in ShaderCatalog.BUNDLED) " · the one that comes with Kolahal is used again" else "")
    }

    /** What to do on a phone too old for shaders. */
    private fun needs() = info("${Shaders.NEEDS}; this phone has Android ${Build.VERSION.RELEASE}")

    companion object {
        val SUBCOMMANDS =
            listOf("list", "use", "on", "off", "set", "reset", "follow", "values", "play", "get", "update", "check", "reload", "copy", "remove")

        /** The subcommands that take a shader's name: those that take one offer names when typed. */
        val TAKE_NAME = setOf("use", "set", "reset", "follow", "play", "get", "update", "check", "copy", "remove", "rm")

        /**
         * What completes the last word of `shader …`: the subcommands and shaders, then the shaders of
         * the subcommand, then a shader's parameters and what they can be. [shared]: the shared shaders, as
         * far as they are known ([ScriptSources.ref]). [values]: the values to follow. [params]: a shader's parameters.
         */
        fun completions(
            parts: List<String>,
            shared: List<String> = emptyList(),
            values: () -> List<String> = { emptyList() },
            params: (String) -> List<ShaderParam> = { emptyList() },
        ): List<String> = when {
            parts.size == 4 && parts[1] in setOf("set", "reset", "follow") -> params(parts[2]).map { it.name }
            parts.size == 5 && parts[1] == "follow" ->
                if (params(parts[2]).any { it.name.equals(parts[3], ignoreCase = true) }) values() + ShaderCatalog.OFF else emptyList()

            parts.size == 5 && parts[1] == "set" ->
                params(parts[2]).firstOrNull { it.name.equals(parts[3], ignoreCase = true) }
                    ?.let { it.choices.ifEmpty { listOf(it.text(it.min), it.text(it.default), it.text(it.max)).distinct() } }.orEmpty()

            parts.size == 2 -> SUBCOMMANDS + Shaders.names
            parts.size != 3 -> emptyList()
            parts[1] == "get" -> shared
            parts[1] in setOf("update", "remove", "rm") -> Shaders.userNames
            parts[1] in TAKE_NAME -> Shaders.names
            else -> emptyList()
        }

        /** What [check] found in a shader without problems, in lines: "compiles" first, for after its name. */
        internal fun checkLines(check: Shaders.Check): List<String> = listOfNotNull(
            if (Shaders.supported) "compiles" else "has no problems · ${Shaders.NEEDS} to compile it",
            check.sets.takeIf { it.isNotEmpty() }?.let { "Kolahal sets: ${it.joinToString()}" },
            check.unset.takeIf { it.isNotEmpty() }?.let { "Not set by Kolahal, so 0: ${it.joinToString()}" },
            check.params.takeIf { it.isNotEmpty() }
                ?.let { all -> "Parameters: ${all.joinToString { "${it.name} ${it.range}" }}" },
            if (check.animated) "Animated: redrawn ${ShaderClock.FPS} times a second" else null,
        )

        /**
         * Reads the shader files again: Kolahal's own and, when one is picked, the script [folder]'s.
         * What it found, and what was skipped (null: nothing).
         */
        internal fun readAgain(context: Context, folder: ScriptFolder): Pair<String, String?> {
            Shaders.load(context)
            if (folder.uri == null) return "Shaders read again" to null
            val found = folder.importShaders()
            val text =
                if (found.names.isEmpty()) "No shaders in the script folder · put .agsl files in its ${Shaders.DIR} folder"
                else "Read from the script folder: ${found.names.joinToString()}"
            return text to found.skipped.takeIf { it.isNotEmpty() }?.let { "Skipped ${it.joinToString()}" }
        }
    }
}
