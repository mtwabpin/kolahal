/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

import android.content.Context
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.FontFiles
import com.mtwabpin.kolahal.data.HomeEntry
import com.mtwabpin.kolahal.data.HomeOrder
import com.mtwabpin.kolahal.data.HomeStyles
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.MaterialYou
import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.ShaderSafety
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.terminal.ScreenSaver
import com.mtwabpin.kolahal.terminal.TerminalUi
import com.mtwabpin.kolahal.widgets.WidgetHost

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
                val tty = HomeStyles.ttyNow(conf)
                // A setting that does nothing in the home style (log.item-spacing in tty) isn't listed.
                Keys.ALL.filter { HomeStyles.applies(it, tty) }.forEach { k ->
                    val marker = if (conf.isDefault(k)) "" else " *"
                    val first = k.key.substringBefore(".") != group
                    group = k.key.substringBefore(".")
                    out(
                        "${k.key} = ${shown(k)}$marker",
                        LogContext.of(
                            LogContext.COMMAND to "config set ${k.key} ",
                            LogContext.DESC to k.help
                        ) + if (first) LogContext.ITEM else emptyMap(),
                    )
                }
                info(
                    "Tap a line to edit it\n* = changed from default\n'config reset <key|all>' puts back the default",
                    LogContext.of(LogContext.COPY to "config reset"),
                )
            }

            sub == "get" -> withKey(args.getOrNull(1)) { out("${it.key} = ${shown(it)}") }
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
            else -> error("Usage: config <key> <value>\nOr: config ls, config get <key>, config reset <key|all>")
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
        val hasCustom = FontFiles.customFile(context) != null
        when (sub) {
            null, "ls", "list" -> {
                val current = conf[Keys.FONT]
                FontFiles.names.forEach { name ->
                    if (name == FontFiles.CUSTOM && !hasCustom) return@forEach
                    val mark = if (name == current) "*" else " "
                    out(
                        "$mark ${name.padEnd(16)} ${FontFiles.label(name)}",
                        LogContext.of(LogContext.RUN to "font $name") + LogContext.ALIGNED
                    )
                }
                info("Tap a font to use it\n'font import' adds your own .ttf or .otf\n'config font.size <n>' sets its size")
                info("System font: ${conf[Keys.FONT_SANS]}\n'font sans prompt' uses it for the prompt\n'font sans all' uses it for everything")
            }

            "import" -> ui.requestFontImport()
            "sans" -> args.getOrNull(1)?.let { setKey(Keys.FONT_SANS.key, it, "") }
                ?: info("font.sans = ${conf.display(Keys.FONT_SANS)}\nUsage: font sans off|prompt|all")

            FontFiles.CUSTOM if !hasCustom ->
                fix("No custom font yet\nTap to import a .ttf or .otf", "font import")

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

            name?.lowercase() in Keys.REMOVED -> name!!.lowercase()
                .let { error("$it was removed\n${Keys.REMOVED.getValue(it).replaceFirstChar(Char::uppercase)}") }

            else -> error("Unknown setting '${name.orEmpty()}'\n'config' shows the settings", LogContext.of(LogContext.COPY to "config"))
        }
    }

    private fun setKey(name: String?, parsed: String, raw: String): Unit = withKey(name) { key ->
        // For text settings, take the value exactly as typed after the name (old names too), so quotes stay.
        val at =
            if (key is ConfigKey.Text && name != null) raw.indexOf(name, ignoreCase = true) else -1
        var value = if (at >= 0) raw.substring(at + name!!.length).trim() else parsed
        // `config set keybar.row2 ""` sets an empty text, for settings whose help allows one.
        if (key is ConfigKey.Text && (value == "\"\"" || value == "''")) value = ""
        else if (value.isBlank()) return error("${key.key} = ${conf.display(key)}  (${hint(key)})")
        // Home can start on the widgets page only with the app selector on: there it is the way to open apps.
        if (key == Keys.WIDGETS_START && Keys.WIDGETS_START.parse(value) == true && !conf[Keys.SELECTOR_RAIL]) {
            return error(
                "widgets.start needs the app selector\n'config set selector.rail on' turns it on",
                LogContext.of(LogContext.COPY to "config set selector.rail on"),
            )
        }
        // home.pages names pages there are: an id of none is refused, with the ids there are.
        if (key == Keys.HOME_PAGES) {
            val there = listOf(HomeEntry.Log.id) +
                    WidgetHost.get(context).pages.value.map { HomeOrder.idOf(it.location) } +
                    listOfNotNull(HomeEntry.Assistant.id.takeIf { BuildConfig.ASSISTANT })
            val ids = HomeOrder.parse(value)?.let(HomeOrder::listed)
            if (ids == null || ids.any { it !in there }) return error("Invalid value for ${key.key}: ${there.joinToString(", ")}")
        }
        if (conf.set(key, value)) {
            info("${key.key} = ${shown(key)}")
            // Android 9 to 11 has no wallpaper colors for material-you to take. Say what it shows instead.
            if (key == Keys.THEME && MaterialYou.isOne(conf[Keys.THEME]) && !MaterialYou.supported) info(
                MaterialYou.note(conf[Keys.THEME])
            )
            // A shader turned off for being slow may be tried again, as with `shader on`.
            if (key == Keys.SCREEN_SHADER && conf[Keys.SCREEN_SHADER] != ShaderCatalog.OFF) {
                ShaderSafety.get(context).turnedOn()
                if (!Shaders.supported) info("${Shaders.NEEDS}\nNothing is drawn on this phone")
            }
        } else error("Invalid value for ${key.key}: ${hint(key)}")
    }

    /**
     * [key]'s value as `config` shows it, with a note where the home style sets it aside: a
     * screensaver the gui style doesn't run ([ScreenSaver.note]), or a setting that does nothing in
     * the tty style ([HomeStyles.GUI_ONLY]).
     */
    private fun shown(key: ConfigKey<*>): String {
        val tty = HomeStyles.ttyNow(conf)
        val note = when {
            key == Keys.SCREEN_SAVER -> ScreenSaver.note(conf[Keys.SCREEN_SAVER], tty)
            !HomeStyles.applies(key, tty) -> "does nothing in the tty style"
            else -> null
        }
        return conf.display(key) + (note?.let { " ($it)" } ?: "")
    }

    private fun hint(key: ConfigKey<*>) = when (key) {
        is ConfigKey.Bool -> "on|off"
        is ConfigKey.Num -> "${key.range.first}..${key.range.last}"
        is ConfigKey.Choice -> key.choices.joinToString("|")
        is ConfigKey.Text -> key.help
    }
}
