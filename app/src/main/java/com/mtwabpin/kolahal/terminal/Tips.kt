/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.commands.CommandsOff
import com.mtwabpin.kolahal.commands.InstantRun

/**
 * Shows one tip per day in the log for the first two weeks after install (`log.tips`).
 * Each tip is about a feature that you haven't used yet. We check this against the commands you typed.
 * When you tap the tip's line, its example goes into the prompt.
 */
object Tips {
    /**
     * - [example]: a line to try.
     * - [used]: the command names that show you already know this feature. A synonym counts as its command.
     */
    data class Tip(val id: String, val text: String, val example: String, val used: Set<String>)

    val ALL = listOf(
        Tip(
            "todo",
            "todo milk adds a todo\n'todo' lists them, and a tap ticks one off",
            "todo milk",
            setOf("todo")
        ),
        Tip(
            "remind",
            "remind 10m tea sets a reminder\nremind fri 8am standup and remind every mon 19:00 bins work too",
            "remind 10m tea",
            setOf("remind", "reminders")
        ),
        Tip(
            "calc",
            "= 15% of 80 works it out as you type\n= 5 km in mi converts",
            "= 15% of 80",
            setOf("=")
        ),
        Tip(
            "note",
            "note buy stamps saves a note\n'notes' lists them",
            "note buy stamps",
            setOf("note", "notes")
        ),
        Tip(
            "help",
            "help lists every command\n'help todo' shows just one",
            "help todo",
            setOf("help")
        ),
        Tip(
            "alias",
            "alias m = open maps\nIt makes m a command of your own",
            "alias m = open maps",
            setOf("alias")
        ),
        Tip(
            "theme",
            "theme dracula changes the colors\n'settings' has all of them",
            "theme dracula",
            setOf("theme")
        ),
        Tip(
            "search",
            "/word searches the log\n'search word' searches the web",
            "search kolahal",
            setOf("search", "yt", "wiki")
        ),
        Tip(
            "feed",
            "feed add <a site's address>\nIt brings the site's new posts to the log",
            "feed add ",
            setOf("feed", "feeds")
        ),
        Tip(
            "script",
            "script lib lists ready-made scripts\nWeather, news, a battery log",
            "script lib",
            setOf("script", "scripts")
        ),
        Tip(
            "date",
            "date tokyo new york\nIt shows the time there, and 'cal' shows the month",
            "date tokyo",
            setOf("date", "cal")
        ),
        Tip(
            "backup",
            "backup every weekly\nIt saves a backup of everything to a folder you pick",
            "backup every weekly",
            setOf("backup")
        ),
        Tip(
            "toggles",
            "torch, dnd, ring silent, volume 50\nThey change the phone from here\n'toggles' lists them",
            "toggles",
            setOf("toggles", "torch", "dnd", "ring", "volume", "brightness")
        ),
    )

    /** The days after install (counted in whole days) on which tips are shown. */
    val DAYS = 1..14

    /** Returns the first tip that is not shown yet and whose commands are not in [history]. Returns null when no tip is left. */
    fun next(history: List<String>, shown: Set<String>, off: Set<String> = emptySet()): Tip? {
        val typed = history.mapTo(HashSet()) { line ->
            line.trim()
                .let { if (it.startsWith("=")) "=" else InstantRun.canonical(it.substringBefore(' ')) }
        }
        // Not a tip whose example runs a command turned off (commands.off).
        return ALL.firstOrNull {
            it.id !in shown && it.used.none { name -> name in typed } && !CommandsOff.isOff(
                it.example.substringBefore(' '),
                off
            )
        }
    }
}
