package com.mtwabpin.kolahal.widgets

import android.content.Context
import androidx.core.content.edit
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.data.takeWhole
import com.mtwabpin.kolahal.script.Json

/**
 * One button of the built-in Shortcuts widget.
 * - [label]: what the button says.
 * - [command]: what a tap runs, as if you had typed it and pressed Enter.
 * - [icon]: an emoji or a letter, or null.
 */
data class Shortcut(val label: String, val command: String, val icon: String? = null)

/**
 * The list of the Shortcuts widget: the commands you picked, in the order of the buttons.
 * - You change it in the Widgets screen, and nowhere else. A tap on a button runs its command as a
 *   gesture runs the command you set for it, so the command may be a sensitive one (`torch`).
 * - No command and no script function writes the list, so a script can't plant a command behind a
 *   button. ShortcutsWriteTest checks that.
 * - It is a JSON list under one key of home's own preferences ([Stores.LAUNCHER]): no new store.
 *   A backup carries it with that file.
 *
 * [decode], [encode] and [clean] are pure.
 */
object Shortcuts {
    /** The most buttons the widget has. */
    const val MAX = 8
    const val MAX_LABEL = 24
    /** A command is as long as the arguments of a widget's tap may be. */
    const val MAX_COMMAND = 200
    const val MAX_ICON = 4

    /** Only this file names the key. */
    private const val KEY = "widget.shortcuts"

    private val BREAKS = Regex("""\s*[\r\n]+\s*""")

    /** [text] on one line, without spaces at its ends. */
    private fun oneLine(text: String): String = text.replace(BREAKS, " ").trim()

    /**
     * [list] as it is stored and shown.
     * - A shortcut without a command is left out. Only the first [MAX] of the others are kept.
     * - A command is one line, at most [MAX_COMMAND] characters. The cut adds no "…": that would be
     *   part of what runs.
     * - A label is one line, cut at [MAX_LABEL] with "…". Without a label, the command is the label.
     * - An icon is at most [MAX_ICON] characters, and never ends in half an emoji. An empty one is null.
     */
    fun clean(list: List<Shortcut>): List<Shortcut> = list.mapNotNull { s ->
        val command = oneLine(s.command).takeWhole(MAX_COMMAND).trim()
        if (command.isEmpty()) return@mapNotNull null
        val label = oneLine(s.label).ifEmpty { command }.let { if (it.length <= MAX_LABEL) it else it.takeWhole(MAX_LABEL - 1).trimEnd() + "…" }
        val icon = s.icon?.let(::oneLine)?.takeWhole(MAX_ICON)?.takeIf { it.isNotEmpty() }
        Shortcut(label, command, icon)
    }.take(MAX)

    /**
     * The list stored as [text]. Anything that isn't a list gives an empty list, and an item that
     * isn't a shortcut is skipped: it never throws. What it returns is [clean].
     */
    fun decode(text: String?): List<Shortcut> {
        if (text.isNullOrBlank()) return emptyList()
        // Throwable, not Exception: deeply nested rubbish ends the reader with a StackOverflowError.
        val items = try {
            Json.parse(text) as? List<*>
        } catch (_: Throwable) {
            null
        }
        return clean(
            items.orEmpty().mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val command = m["command"] as? String ?: return@mapNotNull null
                Shortcut(label = m["label"] as? String ?: "", command = command, icon = m["icon"] as? String)
            },
        )
    }

    /** The JSON for [list], after [clean]: `[{"label":"Torch","command":"torch","icon":"🔦"}]`. An icon that is null is left out. */
    fun encode(list: List<Shortcut>): String = Json.stringify(
        clean(list).map { s -> buildMap { put("label", s.label); put("command", s.command); s.icon?.let { put("icon", it) } } },
    )

    /** The stored list. It reads a preferences file, so call it off the main thread. */
    fun read(context: Context): List<Shortcut> = decode(Stores.LAUNCHER.open(context).getString(KEY, null))

    /**
     * Stores [list], after [clean]. Only the Widgets screen's editor calls this, through the view
     * model. Never call it for a command or for a script.
     */
    fun write(context: Context, list: List<Shortcut>) {
        Stores.LAUNCHER.open(context).edit { putString(KEY, encode(list)) }
    }
}
