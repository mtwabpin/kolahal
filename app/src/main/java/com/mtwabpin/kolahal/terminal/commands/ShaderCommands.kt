/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

import android.content.Context
import android.os.Build
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.notifications.NotificationActions
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptFolder
import com.mtwabpin.kolahal.script.ScriptSources
import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.ShaderClock
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderParam
import com.mtwabpin.kolahal.shader.ShaderParams
import com.mtwabpin.kolahal.shader.ShaderPlay
import com.mtwabpin.kolahal.shader.ShaderSafety
import com.mtwabpin.kolahal.shader.ShaderStore
import com.mtwabpin.kolahal.shader.ShaderValues
import com.mtwabpin.kolahal.shader.ShaderWords
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.shader.SharedShader
import com.mtwabpin.kolahal.shader.UsageValues
import com.mtwabpin.kolahal.shader.WallpaperChoice
import com.mtwabpin.kolahal.terminal.ShaderActions
import com.mtwabpin.kolahal.terminal.TerminalUi

/**
 * The `shader` command: the shader over home and the full-screen screens (`screen.shader`).
 * - `shader` lists them; `shader <name>` (or `use`) turns one on, if it compiles: the one that is on
 *   stays when it doesn't.
 * - `shader off` always works: the prompt takes the keys even when nothing can be read. `shader on`
 *   turns the last one on again, also after it was turned off for being slow.
 * - `set` changes a shader's parameters and `reset` puts them back ([ShaderParam]).
 * - `follow` makes a parameter follow a value, and `values` lists the values ([ShaderValues]).
 * - `play` plays a one-off shader ([ShaderPlay]), and `words` sets the words that play it ([ShaderWords]).
 * - `get` lists the shared shaders and installs or updates one; `get --update` updates every one installed.
 * - `edit` opens a shader in the editor ([TerminalUi.openShaderEditor]). One that comes with Kolahal
 *   becomes the user's own when it is saved ([ShaderCatalog.Edit.COPY]).
 * - `check`, `reload`, `save` and `rm` work with the user's own files. `save` of a template's
 *   name ([ShaderCatalog.TEMPLATES]) makes a shader of the user's from it.
 *
 * The live wallpaper's shader is set by `wallpaper shader <name>` ([SystemCommands.wallpaper]).
 *
 * On Android 12 and older, shaders are listed but can't be used.
 */
class ShaderCommands(
    context: Context,
    private val ui: TerminalUi,
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
            "", "ls" -> list()
            "use" -> use(name ?: return usage("shader"))
            "on" -> on()
            ShaderCatalog.OFF -> off()
            "set" -> set(args.drop(1))
            "reset" -> reset(args.drop(1))
            "follow" -> follow(args.drop(1))
            "values" -> values()
            "words" -> words(name ?: return usage("shader"), args.drop(2).joinToString(" "))
            "play" -> play(name ?: return usage("shader"), args.getOrNull(2))
            // These use the network, so they run outside the serial command queue.
            "get" -> background("shader get") { getShared(args.drop(1)) }
            "check" -> check(name ?: conf[Keys.SCREEN_SHADER].takeUnless { it == ShaderCatalog.OFF } ?: return usage("shader"))
            "reload" -> reload()
            "edit" -> edit(name ?: return usage("shader"))
            "save" -> save(name ?: return usage("shader"))
            "rm", "delete" -> remove(name ?: return usage("shader"))
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
        info(
            "Tap a shader to use it\n'shader off' turns it off\n'shader get' lists shared ones\n" +
                "Your own: put .agsl files in the script folder's ${Shaders.DIR} folder, then run 'shader reload'"
        )
    }

    private fun use(name: String) {
        if (name == ShaderCatalog.OFF) return off()
        if (!Shaders.exists(name)) return noShader(name)
        if (!Shaders.supported) return needs()
        if (Shaders.once(context, name)) {
            return error("$name is a one-off shader\n'shader play $name' plays it", copy("shader play $name"))
        }
        Shaders.use(context, name)?.let { return error(Shaders.problemLine(name, it)) }
        info("Shader: $name\n'shader off' turns it off", copy("shader off"))
        ShaderActions.wallpaperWarning(context, name)?.let {
            info("$it\n'config set log.wallpaper 0' hides it", copy("config set log.wallpaper 0"))
        }
    }

    /** `shader words <name> <words>|off`: what plays it in a notification; without words, what they are. */
    private fun words(name: String, text: String) {
        if (!Shaders.exists(name)) return noShader(name)
        if (!Shaders.once(context, name)) return notOnce(name)
        when {
            text.isBlank() -> info(ShaderWords.words.value[name]?.let { "$name plays on: $it" } ?: "$name has no words")
            text.lowercase() == ShaderCatalog.OFF -> {
                ShaderWords.set(context, name, "")
                info("$name plays on no words")
            }

            else -> {
                ShaderWords.set(context, name, text)
                info("$name plays when a notification has: ${ShaderWords.words.value[name]}")
                if (!NotificationActions.isEnabled(context)) fix("Kolahal can't see your notifications\nTap to allow it", "allow read-notifications")
            }
        }
    }

    private fun play(name: String, time: String?) {
        if (!Shaders.exists(name)) return noShader(name)
        if (!Shaders.supported) return needs()
        if (!Shaders.once(context, name)) return notOnce(name)
        Shaders.problem(context, name)?.let { return error(Shaders.problemLine(name, it)) }
        val seconds = time?.let { it.toFloatOrNull()?.takeIf { s -> s > 0f } ?: return usage("shader") }
        if (!ShaderPlay.play(name, seconds)) info("A shader is playing")
    }

    private fun on() {
        if (!Shaders.supported) return needs()
        val safety = ShaderSafety.get(context)
        val current = conf[Keys.SCREEN_SHADER]
        val name = if (current != ShaderCatalog.OFF) current else safety.last
        if (name == null) {
            // No shader over home to turn on. One that was turned off without ever being over home is on again:
            // a one-off that stopped Kolahal while it played, whose line says 'shader on'.
            val was = safety.tripped()?.let(ShaderSafety::nameOf)
                ?: return error("No shader was on yet\n'shader use <name>' turns one on")
            safety.turnedOn()
            return info("Shader $was is on again")
        }
        use(name)
    }

    private fun off() {
        val was = Shaders.off(context) ?: return info("No shader is on")
        info("Shader off\n'shader on' turns $was on again", copy("shader on"))
    }

    /** `shader set <name> <parameter> <value>`; without a value it says what it is, without a parameter it lists them. */
    private fun set(args: List<String>) {
        val name = args.firstOrNull()?.lowercase() ?: return usage("shader")
        val params = paramsOf(name) ?: return
        if (args.size == 1) return params.forEach { line(name, it) }
        val param = params.find(args[1]) ?: return error("$name has no parameter '${args[1]}'\nIt has ${params.joinToString { it.name }}")
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
            params.find(it) ?: return error("$name has no parameter '$it'\nIt has ${params.joinToString { p -> p.name }}")
        }
        ShaderParams.reset(context, name, param)
        info(if (param != null) "$name: ${param.name} ${param.text(param.default)}" else "$name: parameters reset")
    }

    /** `shader follow <name> <parameter> <value or formula>|off`; without a value it says what it follows, without a parameter it lists them. */
    private fun follow(args: List<String>) {
        val name = args.firstOrNull()?.lowercase() ?: return usage("shader")
        val params = paramsOf(name) ?: return
        if (args.size == 1) return params.forEach { line(name, it) }
        val param = params.find(args[1]) ?: return error("$name has no parameter '${args[1]}'\nIt has ${params.joinToString { it.name }}")
        if (args.size == 2) return line(name, param)
        val text = args.drop(2).joinToString(" ")
        if (text.lowercase() == ShaderCatalog.OFF) {
            ShaderParams.setFollow(context, name, param, null)
            return info("$name: ${param.name} ${param.text(ShaderParams.of(name, param))}")
        }
        val (id, formula) = ShaderValues.parse(text, ShaderActions.sources(context).map { it.id })
            ?: return error("No value in '$text'\n'shader values' lists them", copy("shader values"))
        if (formula != null && ShaderValues.work(formula, id, "1") == null) return error("'$formula' can't be worked out\nTry $id / 20")
        ShaderParams.setFollow(context, name, param, id, formula)
        info("$name: ${param.name} follows ${formula ?: id}, ${param.text(ShaderParams.now(name, param, ShaderActions.phone(context)))} now")
    }

    /** `shader values`: one line for each value, and one for each row of days or blocks of the phone's use. */
    private fun values() {
        valueLines(ShaderActions.sources(context, fresh = true)).forEach { (id, desc) ->
            out(id, LogContext.of(LogContext.DESC to desc) + LogContext.ITEM)
        }
        info("Shader parameters can follow these values\nRun 'shader follow <name> <parameter> <value>'")
    }

    /** The parameters of [name]. Null, and it has said so, when there are none. */
    private fun paramsOf(name: String): List<ShaderParam>? {
        if (!Shaders.exists(name)) {
            noShader(name)
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
                    ShaderParams.follow(shader, param)?.let { " · follows ${ShaderParams.formula(shader, param) ?: it}" }.orEmpty(),
        ) + LogContext.ITEM,
    )

    private fun check(name: String) {
        if (!Shaders.exists(name)) return noShader(name)
        val check = Shaders.check(context, name) ?: return error("Shader $name can't be read")
        val problem = check.problem
        if (problem != null) error(Shaders.problemLine(name, problem))
        else {
            val (first, rest) = checkLines(check).let { it.first() to it.drop(1) }
            info(if (Shaders.supported) "Shader $name $first" else "Shader $name $NO_PROBLEMS\n$toCompile")
            rest.forEach { out(it) }
        }
        // Header lines that are left out: the shader works without them.
        check.warnings.forEach { error(it) }
    }

    /** `shader edit <name>`: the shader in the editor. */
    private fun edit(name: String) {
        if (ShaderCatalog.edit(name, Shaders.userNames) == null) return noShader(name)
        ui.openShaderEditor(name)
    }

    /** Reads the shader files again: the script folder's, when one is picked, and Kolahal's own. */
    private fun reload() = background("shader") {
        val (found, skipped) = readAgain(context, folder)
        info(found)
        skipped?.let { error(it) }
    }

    // ---- shared shaders --------------------------------------------------------------------

    /**
     * `shader get [<name>|<source>/<name>] [--refresh] [--replace] [--update]`, as `script get`:
     * - without a name, lists the shaders of every source, with the source of those not from
     *   kolahal-scripts. A source that can't be read says why; the others still list.
     * - with a name, installs or updates that shader. It isn't turned on: a tap on the line does that.
     * - with `--update` and no name, updates every shader installed from a list ([update]).
     */
    private fun getShared(args: List<String>) {
        val refresh = "--refresh" in args
        // `name`, `name.agsl` or `source/name`: the shader's name in lowercase, without its extension.
        val ref = args.firstOrNull { !it.startsWith("--") }?.let { word ->
            val name = word.substringAfterLast('/').lowercase().substringBefore('.')
            if ('/' in word) "${word.substringBeforeLast('/')}/$name" else name
        } ?: return if ("--update" in args) update() else listShared(refresh)
        val sources = catalog.sources()
        if ('/' in ref && ScriptSources.find(sources, ref.substringBeforeLast('/')) == null) {
            return error("No source '${ref.substringBeforeLast('/')}'\n'script sources' lists them", copy("script sources"))
        }
        fun find(fresh: Boolean): SharedShader? =
            ScriptSources.pick(catalog.listings(fresh).flatMap { it.shaders }, ref, sources)

        val entry = find(refresh) ?: (if (refresh) null else find(true))
        ?: return error("No shared shader '$ref'\n'shader get' lists them", copy("shader get"))
        when (store.state(entry)) {
            ScriptCatalog.State.NEEDS_NEWER -> error("${entry.name} needs Kolahal ${entry.requires} or newer")
            ScriptCatalog.State.NAME_TAKEN -> error(store.taken(entry))
            ScriptCatalog.State.INSTALLED -> info("${entry.name} is installed and up to date")
            ScriptCatalog.State.MODIFIED ->
                if ("--replace" in args) install(entry, replace = true)
                else edited(entry.name, ref)

            ScriptCatalog.State.NOT_INSTALLED, ScriptCatalog.State.UPDATE_AVAILABLE -> install(entry)
        }
    }

    private fun listShared(refresh: Boolean) {
        val listings = catalog.listings(refresh)
        val entries = listings.flatMap { it.shaders }
        info("Shared shaders from ${listings.joinToString(", ") { it.source.name }}\n'shader get <name>' installs one")
        listings.forEach { l -> l.error?.let { error("Can't read ${l.source.name}\n$it") } }
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
     * `shader get --update`: reads the lists again, then updates every shader installed from a list that has a
     * newer version. One the user edited stays: `shader get <name> --replace` replaces it.
     */
    private fun update() {
        val installed = Shaders.userNames.filter { store.installedFrom(it) != null }
        if (installed.isEmpty()) return info("No shader was installed with 'shader get'")
        val listings = catalog.listings(refresh = true)
        listings.forEach { l -> l.error?.let { error("Can't read ${l.source.name}\n$it") } }
        val latest = listings.flatMap { it.shaders }
        // How many have a newer version, updated or not.
        var newer = 0
        for (n in installed) {
            val e = store.entry(n, latest) ?: continue
            val copy = Shaders.own(context, n)?.let { store.copy(n, it, latest) } ?: continue
            if (!copy.newer) continue
            newer++
            if (copy.edited) {
                edited(n, n)
                continue
            }
            // One that fails doesn't keep the others from being updated.
            runCatching { install(e) }.onFailure { error("$n isn't updated\n${it.reason()}") }
        }
        // A list that couldn't be read has said so: nothing is known of its shaders.
        if (newer == 0 && listings.none { it.error != null }) info("The installed shaders are up to date")
    }

    /** Downloads and installs [e]. It stays off, unless the one that is on is updated. */
    private fun install(e: SharedShader, replace: Boolean = false) {
        val update = e.name in Shaders.userNames
        store.install(e, store.download(e), replace)
        if (update) info("Updated ${e.name}" + e.version.ifBlank { null }?.let { " to $it" }.orEmpty())
        else info(
            "Installed ${e.name} from ${e.source}\nTap to use it, or run 'shader ${e.name}'",
            LogContext.of(LogContext.RUN to "shader use ${e.name}", LogContext.COPY to "shader ${e.name}"),
        )
    }

    // ---- your own --------------------------------------------------------------------------

    /** `shader save <name>`: writes the shader's file into the script folder, to edit it there. */
    private fun save(name: String) {
        if (Shaders.isTemplate(name)) return background("shader") { startFrom(name) }
        val source = Shaders.source(context, name) ?: return noShader(name)
        if (folder.uri == null) return fix("Saving a shader needs a script folder\nTap to pick one", "script dir")
        background("shader") {
            val path = folder.exportShader(name, source)
            info("Saved $name to $path\nEdit it there, then run 'shader reload'", copy("shader reload"))
        }
    }

    /** `shader save template-…`: a shader of the user's from the template, to edit as a file. */
    private fun startFrom(template: String) {
        val (name, path) = fromTemplate(context, folder, template)
        val once = Shaders.once(context, name)
        info(
            "Added $name from $template\nTap to ${if (once) "play" else "use"} it",
            LogContext.of(LogContext.RUN to "shader ${if (once) "play" else "use"} $name"),
        )
        if (path != null) info("Its file is $path\nEdit it there, then run 'shader reload'", copy("shader reload"))
        else error(
            "Its file needs a script folder\nTap to pick one, then run 'shader save $name'",
            LogContext.of(LogContext.RUN to "script dir", LogContext.COPY to "shader save $name"),
        )
    }

    private fun remove(name: String) {
        if (name !in Shaders.userNames) {
            return error(
                if (name in ShaderCatalog.BUNDLED) "$name comes with Kolahal\nThere is nothing of yours to remove"
                else "No shader '$name' of yours\n'shader' lists them",
                if (name in ShaderCatalog.BUNDLED) LogContext.EMPTY else copy("shader"),
            )
        }
        if (!Shaders.remove(context, name)) return error("Shader $name can't be removed")
        info("Removed your $name" + if (name in ShaderCatalog.BUNDLED) "\nThe one that comes with Kolahal is used again" else "")
    }

    /** What to do on a phone too old for shaders. */
    private fun needs() = info("${Shaders.NEEDS}\nThis phone has Android ${Build.VERSION.RELEASE}")

    /** There is no shader [name]: the line says what lists them, and Copy gives that command. */
    private fun noShader(name: String) = error("No shader '$name'\n'shader' lists them", copy("shader"))

    /** [name] can't be played or given words: it isn't a one-off shader. */
    private fun notOnce(name: String) = error("$name isn't a one-off shader\nIt has no @once line")

    /** The user edited the installed shader [name], which `shader get [ref]` would replace. */
    private fun edited(name: String, ref: String) = error(
        "You edited $name\n'shader get $ref --replace' replaces it\n'shader save $name' keeps a copy"
    )

    /** A line's context where Copy gives [command], the one command the line names. */
    private fun copy(command: String) = LogContext.of(LogContext.COPY to command)

    companion object {
        /** What [checkLines] and `shader check` say of a shader without problems on a phone that can't compile it. */
        private const val NO_PROBLEMS = "has no problems"
        private val toCompile get() = "${Shaders.NEEDS} to compile it"

        val SUBCOMMANDS =
            listOf("ls", "use", "on", "off", "set", "reset", "follow", "values", "play", "words", "get", "check", "reload", "edit", "save", "rm")

        /** The subcommands that take a shader's name: those that take one offer names when typed. */
        val TAKE_NAME = setOf("use", "set", "reset", "follow", "play", "words", "get", "check", "edit", "save", "rm", "delete")

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
            parts[1] in setOf("rm", "delete") -> Shaders.userNames
            // A template's name makes a shader of the user's from it.
            parts[1] == "save" -> Shaders.names + ShaderCatalog.TEMPLATES.filter { it !in Shaders.names }
            parts[1] in TAKE_NAME -> Shaders.names
            else -> emptyList()
        }

        /**
         * What `shader values` lists for [sources]: each value's id, and what it is now with Kolahal's own names. A row of
         * days or blocks of the phone's use ([UsageValues.series]) is one line, `unlocks.d1 … unlocks.d7`, with its values in order.
         */
        internal fun valueLines(sources: List<ShaderValues.Source>): List<Pair<String, String>> {
            val byId = sources.associateBy { it.id }
            val listed = HashSet<UsageValues.Series>()
            return sources.mapNotNull { s ->
                val series = UsageValues.series(s.id)
                    ?: return@mapNotNull s.id to listOfNotNull(
                        s.value?.let { ShaderValues.shown(s.id, it) } ?: "no value yet",
                        s.label.takeIf { ShaderValues.own(s.id) },
                    ).joinToString(" · ")
                if (!listed.add(series)) return@mapNotNull null
                val values = series.ids.map { id -> byId[id]?.value?.let { ShaderValues.shown(id, it) } }
                val now = if (values.all { it == null }) "no value yet" else values.joinToString(" ") { it ?: "-" }
                "${series.ids.first()} … ${series.ids.last()}" to "$now · ${series.label}"
            }
        }

        /** What [check] found in a shader without problems, in lines: "compiles" first, for after its name. */
        internal fun checkLines(check: Shaders.Check): List<String> = listOfNotNull(
            if (Shaders.supported) "compiles" else "$NO_PROBLEMS · $toCompile",
            check.sets.takeIf { it.isNotEmpty() }?.let { "Kolahal sets: ${it.joinToString()}" },
            check.unset.takeIf { it.isNotEmpty() }?.let { "Not set by Kolahal, so 0: ${it.joinToString()}" },
            check.params.takeIf { it.isNotEmpty() }
                ?.let { all -> "Parameters: ${all.joinToString { "${it.name} ${it.range}" }}" },
            if (check.animated) "Animated: ${redrawn(check.pace)}" else null,
        )

        /** How often a shader that moves is drawn, as [checkLines] says it. */
        internal fun redrawn(pace: ShaderClock.Pace): String {
            val seconds = (pace.settlesMs / 1000f).let { if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString() }
            return when {
                pace.onlyAfterTouch -> "redrawn ${ShaderClock.FPS} times a second for $seconds s after a touch"
                pace.readsTouch && pace.fps < ShaderClock.FPS ->
                    "redrawn ${pace.fps} times a second, ${ShaderClock.FPS} for $seconds s after a touch"
                else -> "redrawn ${pace.fps} times a second"
            }
        }

        /**
         * The shader the live wallpaper draws while [chosen] is chosen: it, or [WallpaperChoice.DEFAULT] when none is
         * chosen or it [exists] no more, as the wallpaper's writer picks it.
         */
        internal fun wallpaperDrawn(chosen: String?, exists: (String) -> Boolean = Shaders::exists): String =
            chosen?.takeIf(exists) ?: WallpaperChoice.DEFAULT

        /**
         * After a save of the user's shader [name] with [code]: when the script [folder] has a file of it, the file
         * gets the same code, so `shader reload` reads what was saved. Why it couldn't, or null. Not on the main thread.
         */
        internal fun saveInFolder(folder: ScriptFolder, name: String, code: String): String? =
            if (folder.uri == null) null
            else saveInFolder(name, code, names = folder::shaderNames, write = { n, c -> folder.exportShader(n, c) })

        /** The same with the folder's [names] and its [write]r. */
        internal fun saveInFolder(name: String, code: String, names: () -> Set<String>, write: (String, String) -> Unit): String? =
            runCatching { if (name in names()) write(name, code) }.exceptionOrNull()
                ?.let { "not saved to the script folder: ${it.message ?: it.javaClass.simpleName}" }

        /**
         * A shader of the user's from [template] ([Shaders.fromTemplate]), under a name that no file in the
         * script [folder] has either, and written into its `shaders` folder when a folder is picked. Its
         * name, and its file's path in the folder (null: no folder). Not on the main thread.
         */
        internal fun fromTemplate(context: Context, folder: ScriptFolder, template: String): Pair<String, String?> {
            val picked = folder.uri != null
            val there = if (picked) folder.shaderNames() else emptySet()
            val (name, code) = Shaders.fromTemplate(context, template) { it in there }
            return name to if (picked) folder.exportShader(name, code) else null
        }

        /**
         * Reads the shader files again: Kolahal's own and, when one is picked, the script [folder]'s.
         * What it found, and what was skipped (null: nothing).
         */
        internal fun readAgain(context: Context, folder: ScriptFolder): Pair<String, String?> {
            Shaders.load(context)
            if (folder.uri == null) return "Shaders read again" to null
            val found = folder.importShaders()
            val text =
                if (found.names.isEmpty()) "No shaders in the script folder\nPut .agsl files in its ${Shaders.DIR} folder"
                else "Read from the script folder: ${found.names.joinToString()}"
            return text to found.skipped.takeIf { it.isNotEmpty() }?.let { "Skipped ${it.joinToString()}" }
        }
    }
}
