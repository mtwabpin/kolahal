/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

import android.content.Context
import com.mtwabpin.kolahal.commands.LogFilter
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.terminal.TerminalUi

/** The commands about the log: `log only`, `clear` and `history`, and `crashes`, the crash reports kept on this phone. */
class LogCommands(context: Context, private val ui: TerminalUi) : CommandGroup(context) {

    fun history() {
        val cmds = logs.commandHistory(30).reversed()
        cmds.forEach { out(it, LogContext.of(LogContext.COMMAND to it)) }
        if (cmds.isEmpty()) info("History is empty")
    }

    /** `crashes [share|clear] [n]`: manages the crash reports kept on this phone ([com.mtwabpin.kolahal.CrashReports]). */
    fun crashes(args: List<String>) {
        val reports = com.mtwabpin.kolahal.CrashReports.list(context)
        when (args.firstOrNull()?.lowercase()) {
            null, "ls" -> {
                if (reports.isEmpty()) return info("No crash reports\nIf Kolahal stops unexpectedly, a report is kept here until you share or clear it")
                reports.forEachIndexed { i, r ->
                    out(
                        "#${i + 1} ${r.kind.label} ${
                            com.mtwabpin.kolahal.CrashReports.whenText(
                                context,
                                r.time
                            )
                        }\n${r.summary.take(160)}",
                        LogContext(mapOf(LogContext.RUN to "crashes share ${r.id}") + LogContext.ITEM),
                    )
                }
                info("Tap one to share it\n'crashes clear' deletes them all", LogContext.of(LogContext.COPY to "crashes clear"))
            }

            "share" -> {
                val which = args.getOrNull(1)
                val r = when {
                    which == null -> reports.firstOrNull()
                    which.toIntOrNull() != null -> reports.getOrNull(which.toInt() - 1)
                    else -> reports.firstOrNull { it.id == which }
                }
                    ?: return if (reports.isEmpty()) error("No crash reports")
                    else error("No crash report $which\n'crashes' lists them", LogContext.of(LogContext.COPY to "crashes"))
                startActivity(com.mtwabpin.kolahal.CrashReports.shareIntent(r.text()))
            }

            "clear" -> com.mtwabpin.kolahal.CrashReports.clear(context).let { n ->
                info(if (n == 1) "Deleted 1 crash report" else "Deleted $n crash reports")
            }

            else -> usage("crashes")
        }
    }

    /**
     * `log only xkcd` · `log only feeds`: the log shows only those lines. This lasts until `log all`,
     * ✕, Back, or another command.
     */
    fun logFilter(args: List<String>) {
        when (args.firstOrNull()) {
            "only" -> ui.filterLog(
                LogFilter.parse(args.drop(1).joinToString(" "))
                    ?: return error("Usage: log only <name|kind>\nName: a script, feed or app\nKind: commands, scripts, feeds, notifications, apps, calls, reminders, starred")
            )

            "all" -> ui.filterLog(null)
            else -> usage("log")
        }
    }

    /**
     * - `clear` clears the screen.
     * - `clear history` clears the ↑ recall history.
     * - `clear all` clears everything (log and history).
     */
    fun clear(args: List<String>) {
        when (args.firstOrNull()) {
            null, "screen" -> ui.clearScreen()
            "history" -> {
                logs.clearHistory()
                info("Command history cleared")
            }

            "all", "log" -> logs.clearAll()
            else -> usage("clear")
        }
    }
}
