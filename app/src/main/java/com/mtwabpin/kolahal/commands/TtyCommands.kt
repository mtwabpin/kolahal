/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.commands

import com.mtwabpin.kolahal.script.ScriptMeta

/**
 * The commands that belong to the tty home style (`home.style tty`). The full-screen screens draw a
 * character grid, so the style that draws home on one offers them, and the gui style doesn't.
 * - In the tty style, the screens' commands ([SCREENS]) and `screen` are offered as any other, and
 *   so is a script that draws a screen of its own (`@uses screen`, [ScriptMeta.isScreen]).
 * - In the gui style none of them is offered: not suggested, not in `help`, Settings → Commands or
 *   any list to pick a command from, not run by instant launch. The [SCREENS] names are not commands
 *   there at all: `clock` opens what it matches, an alias, a script or an app. `screen <name>` and a
 *   screen script still run when typed in full, as [CommandCatalog.HIDDEN] does.
 *
 * Every function takes [tty]: whether the style is tty now (`HomeStyles.ttyNow`, in data). The style
 * can change while the app runs, so callers read it at each use.
 */
object TtyCommands {
    /** The screens' own commands: each opens the full-screen screen of its name. Commands only in the tty style. */
    val SCREENS: Set<String> = setOf("matrix", "pipes", "sl", "clock", "top", "decode", "boot")

    /** Offered only in the tty style: [SCREENS], and `screen`, which the gui style runs only when typed in full. */
    val ONLY: Set<String> = SCREENS + "screen"

    /** The built-in commands offered in the gui style. */
    private val OFFERED_GUI: List<CommandSpec> = CommandCatalog.OFFERED.filter { it.name !in ONLY }
    private val OFFERED_GUI_NAMES: Set<String> = OFFERED_GUI.mapTo(LinkedHashSet()) { it.name }
    private val HIDDEN_GUI: Set<String> = CommandCatalog.HIDDEN + "screen"

    /**
     * Names that no script or alias of yours can lose to a built-in, in either style: the command
     * names and their other names, but [SCREENS]. What is read back (an import from the folder or a
     * backup) checks these, so a script or an alias named `clock` is never left out.
     */
    val ALWAYS_RESERVED: Set<String> = CommandCatalog.RESERVED_NAMES - SCREENS

    /** The built-in commands offered in the style: [CommandCatalog.OFFERED], less [ONLY] in the gui style. */
    fun offered(tty: Boolean): List<CommandSpec> = if (tty) CommandCatalog.OFFERED else OFFERED_GUI

    /** The names of [offered]. */
    fun offeredNames(tty: Boolean): Set<String> =
        if (tty) CommandCatalog.OFFERED_NAMES else OFFERED_GUI_NAMES

    /** Commands offered to nobody in the style, that still run when typed in full: [CommandCatalog.HIDDEN], and `screen` in the gui style. */
    fun hidden(tty: Boolean): Set<String> = if (tty) CommandCatalog.HIDDEN else HIDDEN_GUI

    /** Whether [name] (a first word, in lowercase) is a built-in command in the style: a [SCREENS] name is one only in the tty style. */
    fun isCommand(name: String, tty: Boolean): Boolean =
        name in CommandCatalog.RESERVED_NAMES && (tty || name !in SCREENS)

    /**
     * The names a new or renamed script or alias can't take in the style: [CommandCatalog.RESERVED_NAMES],
     * less [SCREENS] in the gui style. One made in the gui style keeps its name in the tty style,
     * where the command wins, as for any name that became a command.
     */
    fun reserved(tty: Boolean): Set<String> = if (tty) CommandCatalog.RESERVED_NAMES else ALWAYS_RESERVED

    /** The commands turned off ([off], `commands.off`) that count in the style: a [SCREENS] name is no command in the gui style, so its switch counts for nothing there. */
    fun off(off: Set<String>, tty: Boolean): Set<String> = if (tty) off else off - SCREENS

    /** Whether a script with [code] is offered as a command in the style: one that draws a full-screen screen ([ScriptMeta.isScreen]) only in the tty style. */
    fun offersScript(code: String, tty: Boolean): Boolean = tty || !ScriptMeta.isScreen(code)

    /** The command scripts ([commandNames]) offered in the style: in the gui style, all but [screenScripts], the ones that draw a screen. */
    fun offeredScripts(commandNames: List<String>, screenScripts: Set<String>, tty: Boolean): List<String> =
        if (tty || screenScripts.isEmpty()) commandNames else commandNames.filter { it !in screenScripts }
}
