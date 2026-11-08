package com.mtwabpin.kolahal.terminal

import android.content.Context
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.ui.TermFonts

/** The `config` / `set`, `theme` and `font` commands. Like the other command groups, they live outside [CommandProcessor]. */
class ConfigCommands(context: Context, private val ui: TerminalUi) : CommandGroup(context) {
    private val conf = Config.get(context)


    /** [raw] is the argument text before it is split into words. This keeps free-text values intact, for example a key bar with quotes. */
    fun config(args: List<String>, raw: String = "") {
        val sub = args.firstOrNull()
        when {
            sub == null || sub == "ls" || sub == "list" -> {
                // A gap above each group of keys (log.*, header.*, …), not above every key: a list this
                // long would get twice as long.
                var group: String? = null
                Keys.ALL.forEach { k ->
                    val marker = if (conf.isDefault(k)) "" else " *"
                    val first = k.key.substringBefore(".") != group
                    group = k.key.substringBefore(".")
                    out(
                        "${k.key} = ${conf.display(k)}$marker",
                        LogContext.of(LogContext.COMMAND to "config set ${k.key} ", LogContext.DESC to k.help) + if (first) LogContext.ITEM else emptyMap(),
                    )
                }
                info("Tap a line to edit it · * = changed from default · 'config reset <key|all>'")
            }
            sub == "get" -> withKey(args.getOrNull(1)) { out("${it.key} = ${conf.display(it)}") }
            sub == "reset" -> {
                if (args.getOrNull(1) == "all") {
                    Keys.ALL.forEach(conf::reset)
                    info("All settings reset")
                } else withKey(args.getOrNull(1)) {
                    conf.reset(it)
                    info("${it.key} = ${conf.display(it)} (default)")
                }
            }
            sub == "set" -> setKey(args.getOrNull(1), args.drop(2).joinToString(" "), raw)
            Keys.byName(sub) != null -> setKey(sub, args.drop(1).joinToString(" "), raw)
            else -> error("Usage: config [ls|get|set|reset] <key> [value]")
        }
    }

    /**
     * - `font` lists the fonts. Tap one to use it.
     * - `font <name>` sets the font.
     * - `font import` opens the file picker.
     * - `font sans <mode>` sets font.sans.
     */
    fun font(args: List<String>) {
        val sub = args.firstOrNull()?.lowercase()
        val hasCustom = TermFonts.customFile(context) != null
        when (sub) {
            null, "ls", "list" -> {
                val current = conf[Keys.FONT]
                TermFonts.names.forEach { name ->
                    if (name == TermFonts.CUSTOM && !hasCustom) return@forEach
                    val mark = if (name == current) "*" else " "
                    out("$mark ${name.padEnd(16)} ${TermFonts.label(name)}", LogContext.of(LogContext.RUN to "font $name") + LogContext.ALIGNED)
                }
                info("Tap a font to use it · 'font import' adds your own .ttf/.otf · size: config font.size")
                info("System font: ${conf[Keys.FONT_SANS]} · font sans prompt uses it for the prompt, font sans all for everything")
            }
            "import" -> ui.requestFontImport()
            "sans" -> args.getOrNull(1)?.let { setKey(Keys.FONT_SANS.key, it, "") }
                ?: info("font.sans = ${conf.display(Keys.FONT_SANS)} · font sans off|prompt|all")
            TermFonts.CUSTOM if !hasCustom ->
                logs.add(LogKind.ERROR, "No custom font yet — tap to import a .ttf/.otf", ctx = LogContext.of(LogContext.RUN to "font import"))
            else -> setKey(Keys.FONT.key, sub, "")
        }
    }

    private inline fun withKey(name: String?, action: (ConfigKey<*>) -> Unit) {
        val key = name?.let(Keys::byName)
        when {
            key != null -> {
                action(key)
                // Old names (from before 0.3.0) still work. We tell the user the new name.
                if (name.lowercase() != key.key) info("${name.lowercase()} is now called ${key.key}")
            }
            name?.lowercase() in Keys.REMOVED -> name!!.lowercase().let { error("$it was removed: ${Keys.REMOVED[it]}") }
            else -> error("Unknown setting '${name.orEmpty()}' — run 'config'")
        }
    }

    private fun setKey(name: String?, parsed: String, raw: String): Unit = withKey(name) { key ->
        // For text settings, take the value exactly as typed after the name (old names too), so quotes stay.
        val at = if (key is ConfigKey.Text && name != null) raw.indexOf(name, ignoreCase = true) else -1
        var value = if (at >= 0) raw.substring(at + name!!.length).trim() else parsed
        // `config set keybar.row2 ""` sets an empty text, for settings whose help allows one.
        if (key is ConfigKey.Text && (value == "\"\"" || value == "''")) value = ""
        else if (value.isBlank()) return error("${key.key} = ${conf.display(key)}  (${hint(key)})")
        if (conf.set(key, value)) info("${key.key} = ${conf.display(key)}")
        else error("Invalid value for ${key.key}: ${hint(key)}")
    }

    private fun hint(key: ConfigKey<*>) = when (key) {
        is ConfigKey.Bool -> "on|off"
        is ConfigKey.Num -> "${key.range.first}..${key.range.last}"
        is ConfigKey.Choice -> key.choices.joinToString("|")
        is ConfigKey.Text -> key.help
    }
}
