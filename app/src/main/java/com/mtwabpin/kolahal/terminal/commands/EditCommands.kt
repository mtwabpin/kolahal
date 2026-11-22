/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

import android.content.Context
import com.mtwabpin.kolahal.commands.CommandParsing
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.TimeFormats
import com.mtwabpin.kolahal.documents.Documents
import com.mtwabpin.kolahal.terminal.TerminalUi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * `edit`: opens a text file in the text editor. The editor is a window of its own, so this only
 * says which file: the picker's, a new one, or one of the recent files.
 * - `edit`: Android's file picker.
 * - `edit new [<name>]`: asks where to make a new file.
 * - `edit recent`: lists the recent files and those with unsaved text. A tap opens one.
 * - `edit <file>`: a file of that list, by its name, or any `content:` address.
 */
class EditCommands(context: Context, private val ui: TerminalUi) : CommandGroup(context) {
    fun run(args: List<String>) {
        val first = args.firstOrNull()?.lowercase()
        when {
            first == null -> ui.pickTextFile()
            first == "recent" && args.size == 1 -> recent()
            first == "new" -> ui.newTextFile(args.drop(1).joinToString(" ").ifBlank { NEW_NAME })
            // A name may have spaces: `edit my notes.txt`.
            else -> open(args.joinToString(" "))
        }
    }

    private fun open(target: String) {
        if (target.startsWith("content://")) return ui.openTextFile(target)
        val file = find(Documents.listed(context), target)
            ?: return fix("No recent file '$target' — tap to pick a file", "edit")
        ui.openTextFile(file.uri)
    }

    private fun recent() {
        val files = Documents.listed(context)
        if (files.isEmpty()) return fix("No recent files — tap to pick a file", "edit")
        val clock = TimeFormats.clockFormat(Config.get(context)[Keys.CLOCK_24H])
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        files.forEachIndexed { i, file ->
            val at = Instant.ofEpochMilli(file.time).atZone(zone)
            val time = "${TimeFormats.dayLabel(at.toLocalDate(), today)} ${at.format(clock)}"
            out(
                file.name,
                LogContext.of(
                    LogContext.RUN to tapCommand(files, i),
                    LogContext.DESC to if (file.unsaved) "unsaved changes · $time" else time
                )
            )
        }
    }

    companion object {
        /** The name that the picker suggests for `edit new`. */
        const val NEW_NAME = "untitled.txt"

        /**
         * The file of [files] (newest first) that [target] names:
         * - the newest with exactly that name;
         * - or with that name in another case;
         * - or the only one whose name starts with it.
         */
        internal fun find(files: List<Documents.Listed>, target: String): Documents.Listed? =
            files.firstOrNull { it.name == target }
                ?: files.firstOrNull { it.name.equals(target, ignoreCase = true) }
                ?: files.singleOrNull { it.name.startsWith(target, ignoreCase = true) }

        /**
         * The command that a tap on file [index] of the list runs: `edit <name>`, which reads well in
         * the log and in ↑. It is `edit <address>` instead when the name would not open this file:
         * - an earlier file in the list has the same name;
         * - the name doesn't read back as typed (two spaces in a row, a quote at its start, a `|`);
         * - the name is one of `edit`'s own words.
         */
        internal fun tapCommand(files: List<Documents.Listed>, index: Int): String {
            val file = files[index]
            val plain = CommandParsing.tokenize(file.name)
                .joinToString(" ") == file.name && '|' !in file.name &&
                    file.name.lowercase() !in setOf("new", "recent") && !file.name.lowercase()
                .startsWith("new ") && !file.name.startsWith("content://")
            return if (plain && find(
                    files,
                    file.name
                ) === file
            ) "edit ${file.name}" else "edit ${file.uri}"
        }
    }
}
