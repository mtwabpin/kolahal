/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.commands.CommandCatalog
import com.mtwabpin.kolahal.commands.CommandCatalog.HELP_SECTIONS
import com.mtwabpin.kolahal.commands.CommandParsing
import com.mtwabpin.kolahal.commands.CommandSpec
import com.mtwabpin.kolahal.commands.CommandsOff
import com.mtwabpin.kolahal.commands.InstantRun
import com.mtwabpin.kolahal.commands.Pipes
import com.mtwabpin.kolahal.commands.SkillText
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.script.ScriptMeta

/** The `help` command: every command, in sections, or one command in full. And `skill.md`, the guide for an AI assistant. */
class HelpCommands(
    context: Context,
    /** The built-in commands turned off (commands.off). */
    private val off: () -> Set<String>,
) : CommandGroup(context) {
    private val scripts = ScriptRepository.get(context)
    private val prefs = AppPrefs.get(context)

    /**
     * Prints a command's row in `help`: its name, in a column as wide as [column] (the longest name
     * in the list), what it does, and its arguments muted under that. A tap puts its name in the prompt.
     */
    private fun helpLine(spec: CommandSpec, column: String = spec.name) = out(
        spec.name,
        LogContext.of(
            LogContext.COMMAND to spec.name + " ",
            LogContext.DESC to spec.short,
            LogContext.NAME_COL to column,
            LogContext.ARGS to spec.args.ifEmpty { null },
        ),
    )

    /** Prints a script command's row in `help`, in the same form as a built-in's: its @desc, and its @usage under it. */
    private fun helpLine(s: Script, column: String = s.name) {
        val desc = ScriptMeta.description(s.code) ?: "${s.lang.ext} script"
        val args = ScriptMeta.usage(s.code, s.name)?.removePrefix(s.name)?.trim()?.ifEmpty { null }
        out(
            s.name,
            LogContext.of(
                LogContext.COMMAND to s.name + " ",
                LogContext.SCRIPT to s.name,
                LogContext.DESC to desc,
                LogContext.NAME_COL to column,
                LogContext.ARGS to args,
            ),
        )
    }

    /** A section's heading in `help`: its name, bold in the accent color, and how many commands it has. */
    private fun helpHeading(name: String, count: Int) = info(
        StyledText.color(
            StyledText.ColorRole.ACCENT,
            StyledText.wrap(StyledText.Style.BOLD, name)
        ) + StyledText.color(StyledText.ColorRole.MUTED, " · $count"),
        LogContext(LogContext.ITEM),
    )

    /**
     * `help`: every command, in sections, one row each (name, what it does, its arguments).
     * Each built-in command is in a section of [HELP_SECTIONS]; the script commands come last.
     */
    fun help() {
        val off = off()
        val offered = CommandCatalog.OFFERED.filter { it.name !in off }
        val byName = offered.associateBy { it.name }
        val commandScripts = scripts.all().filter { ScriptMeta.isCommand(it.code) }
        // One column for the whole list, so the descriptions line up.
        val column = (offered.map { it.name } + commandScripts.map { it.name }).maxBy { it.length }
        val placed = HashSet<String>()
        for ((section, names) in HELP_SECTIONS) {
            val specs = names.mapNotNull(byName::get)
            if (specs.isEmpty()) continue
            helpHeading(section.replaceFirstChar { it.uppercase() }, specs.size)
            specs.forEach { spec ->
                placed += spec.name
                helpLine(spec, column)
            }
        }
        // None should be left, but a command missing from HELP_SECTIONS still shows.
        offered.filter { it.name !in placed }.forEach { helpLine(it, column) }
        if (commandScripts.isNotEmpty()) helpHeading("Your script commands", commandScripts.size)
        commandScripts.forEach { helpLine(it, column) }
        val muted = { text: String -> StyledText.color(StyledText.ColorRole.MUTED, text) }
        val key = { text: String -> StyledText.tag(StyledText.ColorRole.TEXT, text) }
        info(muted("Anything else opens the app it matches"), LogContext(LogContext.ITEM))
        if (off.isNotEmpty()) info(muted("Turned off: ") + CommandsOff.CHOICES.filter { it in off }
            .joinToString(", ") + muted(" · Settings → Commands"), LogContext(LogContext.ITEM))
        info(key("tap") + muted(" a line to use it · ") + key("long-press") + muted(" for more"))
        // No-break spaces keep the example on one line. A line may still break after "|" there; the word joiner stops it.
        info(muted("Pipes: ") + Pipes.SHOWN.joinToString(" ") { key(it) } + muted(" · help\u00A0|\u2060\u00A0grep\u00A0feed"))
    }

    /**
     * `help todo`: that command in full. Its name large, what it does, then each way to use it
     * ([CommandSpec.uses], or its usage line), its other names, and a link to more in the guide.
     */
    fun commandHelp(word: String) {
        val typed = word.lowercase()
        val off = off()
        if (CommandsOff.isOff(
                typed,
                off
            )
        ) return info("${InstantRun.canonical(typed)} is turned off: its name opens the app it matches · Settings → Commands turns it on")
        val offered = CommandCatalog.OFFERED.filter { it.name !in off }
        offered.firstOrNull { it.name == InstantRun.canonical(typed) }?.let { spec ->
            out(
                StyledText.stats(listOf(spec.name to "")),
                LogContext.of(LogContext.COMMAND to spec.name + " ")
            )
            out(spec.help)
            info(StyledText.color(StyledText.ColorRole.MUTED, "Use"), LogContext(LogContext.ITEM))
            if (spec.uses.isEmpty()) {
                out(
                    StyledText.wrap(StyledText.Style.BOLD, spec.usage),
                    LogContext.of(LogContext.COMMAND to spec.name + " ")
                )
            } else {
                val column = spec.uses.maxBy { it.first.length }.first
                spec.uses.forEach { (use, does) ->
                    out(
                        use,
                        LogContext.of(
                            LogContext.COMMAND to useCommand(use),
                            LogContext.DESC to StyledText.color(StyledText.ColorRole.MUTED, does),
                            LogContext.NAME_COL to column,
                        ),
                    )
                }
            }
            val also = spec.aka
            if (also.isNotEmpty()) info(
                "Also known as ${also.joinToString(", ")}",
                LogContext(LogContext.ITEM)
            )
            info(
                "More in the guide's command list",
                LogContext.of(LogContext.URL to com.mtwabpin.kolahal.Links.GUIDE + "reference/commands/") + if (also.isEmpty()) LogContext.ITEM else emptyMap()
            )
            return
        }
        prefs.alias(typed)?.let {
            return info(
                "$typed is your alias for: $it",
                LogContext.of(LogContext.COMMAND to "$typed ")
            )
        }
        scripts.all().firstOrNull { it.name == typed && ScriptMeta.isCommand(it.code) }
            ?.let { return helpLine(it) }
        // Not a command. Show the commands it may be the start of, or a typo of.
        val near =
            offered.filter { it.name.startsWith(typed) || CommandParsing.isNear(typed, it.name) }
        if (near.isEmpty()) return error("No command called '$word' · help lists them all")
        info("No command called '$word'; nearest:")
        near.take(8).let { shown ->
            shown.forEach {
                helpLine(
                    it,
                    shown.maxBy { s -> s.name.length }.name
                )
            }
        }
    }

    /**
     * `skill.md`: copies the guide for an AI assistant ([SkillText]) to the clipboard, whole, and
     * says in the log what was copied, how long it is, and what to do with it.
     */
    fun skill() {
        val text = context.assets.open(SkillText.NAME).use { it.readBytes().toString(Charsets.UTF_8) }
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(SkillText.NAME, text))
        info(SkillText.copied(text.length))
    }
}

/**
 * What a tap on a use in `help <command>` puts in the prompt: its words up to the first one to fill
 * in or choose. `todo done <n>` gives "todo done ", `script on|off <name>` gives "script ".
 */
internal fun useCommand(use: String): String =
    use.split(" ").takeWhile { w -> w.none { it in "<[|" } }.joinToString(" ") + " "
