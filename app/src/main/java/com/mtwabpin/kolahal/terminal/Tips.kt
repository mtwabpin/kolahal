package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.commands.InstantRun
import com.mtwabpin.kolahal.commands.CommandsOff

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
        Tip("todo", "todo milk adds a todo; todo lists them, and a tap ticks one off", "todo milk", setOf("todo")),
        Tip("remind", "remind 10m tea, remind fri 8am standup or remind every mon 19:00 bins: sets a reminder", "remind 10m tea", setOf("remind", "reminders")),
        Tip("calc", "= 15% of 80 works it out as you type; = 5 km in mi converts", "= 15% of 80", setOf("=")),
        Tip("note", "note buy stamps saves a note; notes lists them", "note buy stamps", setOf("note", "notes")),
        Tip("help", "help lists every command; help todo shows just one", "help todo", setOf("help")),
        Tip("alias", "alias m = open maps makes m a command of your own", "alias m = open maps", setOf("alias")),
        Tip("theme", "theme dracula changes the colours; settings has all of them", "theme dracula", setOf("theme")),
        Tip("search", "/word searches the log; search word searches the web", "search kolahal", setOf("search", "yt", "wiki")),
        Tip("feed", "feed add <a site's address> brings its new posts to the log", "feed add ", setOf("feed", "feeds")),
        Tip("script", "script lib lists ready-made scripts: weather, news, a battery log", "script lib", setOf("script", "scripts")),
        Tip("date", "date tokyo new york shows the time there; cal shows the month", "date tokyo", setOf("date", "cal")),
        Tip("backup", "backup every weekly saves a backup of everything to a folder you pick", "backup every weekly", setOf("backup")),
        Tip("toggles", "torch, dnd, ring silent and volume 50 change the phone from here; toggles lists them", "toggles", setOf("toggles", "torch", "dnd", "ring", "volume", "brightness")),
    )

    /** The days after install (counted in whole days) on which tips are shown. */
    val DAYS = 1..14

    /** Returns the first tip that is not shown yet and whose commands are not in [history]. Returns null when no tip is left. */
    fun next(history: List<String>, shown: Set<String>, off: Set<String> = emptySet()): Tip? {
        val typed = history.mapTo(HashSet()) { line ->
            line.trim().let { if (it.startsWith("=")) "=" else InstantRun.canonical(it.substringBefore(' ')) }
        }
        // Not a tip whose example runs a command turned off (commands.off).
        return ALL.firstOrNull { it.id !in shown && it.used.none { name -> name in typed } && !CommandsOff.isOff(it.example.substringBefore(' '), off) }
    }
}
