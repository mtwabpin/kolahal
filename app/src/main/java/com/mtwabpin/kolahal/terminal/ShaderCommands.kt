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
import com.mtwabpin.kolahal.script.ScriptFolder
import com.mtwabpin.kolahal.script.ShaderCatalog
import com.mtwabpin.kolahal.script.ShaderClock
import com.mtwabpin.kolahal.script.ShaderMeta
import com.mtwabpin.kolahal.script.ShaderSafety
import com.mtwabpin.kolahal.script.Shaders

/**
 * The `shader` command: the shader over the grid screens and the tty home (`tty.shader`).
 * - `shader` lists them; `shader <name>` (or `use`) turns one on, if it compiles: the one that is on
 *   stays when it doesn't.
 * - `shader off` always works: the prompt takes the keys even when nothing can be read. `shader on`
 *   turns the last one on again, also after it was turned off for being slow.
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

    fun run(args: List<String>) {
        val sub = args.firstOrNull()?.lowercase().orEmpty()
        val name = args.getOrNull(1)?.lowercase()
        when (sub) {
            "", "list", "ls" -> list()
            "use" -> use(name ?: return usage("shader"))
            "on" -> on()
            ShaderCatalog.OFF -> off()
            "check" -> check(name ?: conf[Keys.TTY_SHADER].takeUnless { it == ShaderCatalog.OFF } ?: return usage("shader"))
            "reload" -> reload()
            "copy" -> copy(name ?: return usage("shader"))
            "remove", "rm" -> remove(name ?: return usage("shader"))
            else -> use(sub)
        }
    }

    private fun list() {
        if (!Shaders.supported) needs()
        val current = conf[Keys.TTY_SHADER]
        val tripped = ShaderSafety.get(context).tripped()
        Shaders.entries().forEach { e ->
            val source = Shaders.source(context, e.name)
            val description = source?.let { ShaderMeta.parse(it).description }
            val tags = buildList {
                if (e.name == current) add(StyledText.tag(StyledText.ColorRole.ACCENT, "on"))
                if (source != null && tripped == ShaderSafety.id(e.name, source)) {
                    add(StyledText.tag(StyledText.ColorRole.WARN, "turned off"))
                }
                if (e.yours) add(StyledText.tag(StyledText.ColorRole.MUTED, "yours"))
                if (!Shaders.supported) add(StyledText.tag(StyledText.ColorRole.MUTED, "needs Android 13"))
            }
            out(
                (listOf(e.name) + tags).joinToString(" "),
                LogContext.of(
                    LogContext.RUN to "shader use ${e.name}",
                    LogContext.DESC to (description ?: if (e.yours) "Your shader" else ""),
                ) + LogContext.ITEM,
            )
        }
        info("Tap a shader to use it · 'shader off' turns it off · your own: put .agsl files in the script folder's ${Shaders.DIR} folder, then run 'script import'")
    }

    private fun use(name: String) {
        if (name == ShaderCatalog.OFF) return off()
        if (!Shaders.exists(name)) return error("No shader '$name' — 'shader' lists them")
        if (!Shaders.supported) return needs()
        problem(name)?.let { return error("Shader $name: $it") }
        val safety = ShaderSafety.get(context)
        safety.turnedOn()
        safety.last = name
        conf.set(Keys.TTY_SHADER, name)
        val only = if (conf[Keys.HOME_STYLE] == "tty") "" else " · it shows on the full-screen screens (matrix, clock…)"
        info("Shader: $name$only · 'shader off' turns it off")
    }

    private fun on() {
        if (!Shaders.supported) return needs()
        val safety = ShaderSafety.get(context)
        val current = conf[Keys.TTY_SHADER]
        val name = if (current != ShaderCatalog.OFF) current else safety.last
            ?: return error("No shader was on yet — 'shader use <name>'")
        use(name)
    }

    private fun off() {
        val was = conf[Keys.TTY_SHADER]
        if (was == ShaderCatalog.OFF) return info("No shader is on")
        ShaderSafety.get(context).last = was
        conf.set(Keys.TTY_SHADER, ShaderCatalog.OFF)
        info("Shader off · 'shader on' turns $was on again")
    }

    private fun check(name: String) {
        if (!Shaders.exists(name)) return error("No shader '$name' — 'shader' lists them")
        val source = Shaders.source(context, name) ?: return error("Shader $name can't be read")
        val meta = ShaderMeta.parse(source)
        val problem = problem(name)
        if (problem != null) return error("Shader $name: $problem")
        info(if (Shaders.supported) "Shader $name compiles" else "Shader $name has no problems · ${Shaders.NEEDS} to compile it")
        val read = meta.uniforms.map { it.name }.filter { it in ShaderMeta.KNOWN }
        if (read.isNotEmpty()) out("Kolahal sets: ${read.joinToString()}")
        val unset = meta.unset()
        if (unset.isNotEmpty()) out("Not set by Kolahal, so 0: ${unset.joinToString()}")
        if (meta.animated) out("Animated: redrawn ${ShaderClock.FPS} times a second")
    }

    /** What keeps the shader [name] from being used: the first problem ([ShaderMeta.problems]) or the compiler's error. */
    private fun problem(name: String): String? {
        val source = Shaders.source(context, name) ?: return "can't be read"
        ShaderMeta.problems(source).firstOrNull()?.let { return it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Shaders.compileError(source)?.let { return it.toString() }
        }
        return null
    }

    /** Reads the shader files again: the script folder's, when one is picked, and Kolahal's own. */
    private fun reload() = background("shader") {
        Shaders.load(context)
        if (folder.uri == null) info("Shaders read again")
        else {
            val found = folder.importShaders()
            if (found.names.isEmpty()) info("No shaders in the script folder · put .agsl files in its ${Shaders.DIR} folder")
            else info("Read from the script folder: ${found.names.joinToString()}")
            if (found.skipped.isNotEmpty()) error("Skipped ${found.skipped.joinToString()}")
        }
    }

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
    private fun needs() = info("${Shaders.NEEDS}; this phone has Android ${Build.VERSION.RELEASE} · 'config set tty.crt on' looks like an old CRT on any Android")

    companion object {
        val SUBCOMMANDS = listOf("list", "use", "on", "off", "check", "reload", "copy", "remove")

        /** The subcommands that take a shader's name: those that take one offer names when typed. */
        val TAKE_NAME = setOf("use", "check", "copy", "remove", "rm")

        /** What completes the last word of `shader …`: the subcommands and shaders, then the shaders of the subcommand. */
        fun completions(parts: List<String>): List<String> = when {
            parts.size == 2 -> SUBCOMMANDS + Shaders.names
            parts.size != 3 -> emptyList()
            parts[1] in setOf("remove", "rm") -> Shaders.userNames
            parts[1] in TAKE_NAME -> Shaders.names
            else -> emptyList()
        }
    }
}
