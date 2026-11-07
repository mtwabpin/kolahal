package com.mtwabpin.kolahal.script

import android.content.Context
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script

/**
 * What a script may do, as the user set it. The user sets this with the script's Access switches in
 * the Scripts screen, or with `script allow|deny <name> <what>`. There are four kinds of items:
 * - [ScriptMeta.USES]: the script declares them with `@uses`. Each is off until allowed. Switching
 *   a script on allows the ones not decided yet (the Scripts screen asks first, with everything on).
 *   If code from outside declares more, the new ones stay off.
 * - [INTERNET]: a script whose code uses it ([ScriptMeta.usesInternet]) has it unless it is switched
 *   off. A script that doesn't use it never has it, so the switches shown are all it may do.
 * - [HEADER], pinned at the top: a script that sets a status line ([ScriptMeta.setsStatus]) shows it
 *   at the top of home only after you pin it (`script pin`, or its menus). No script pins itself.
 * - Each `@on` event: on unless switched off.
 * When an item is off, the script gets an empty answer where that is honest (no apps, no
 * notifications, no clipboard). Where it isn't honest (internet), the script stops.
 * Posting notifications has its own switch ([com.mtwabpin.kolahal.data.Script.notify]). It is shown
 * for a script that calls `notify` ([ScriptMeta.postsNotifications]).
 */
object ScriptAccess {
    const val INTERNET = "internet"
    const val HEADER = "header"

    /** Which of [INTERNET] and [HEADER] the [code] uses. */
    internal fun base(code: String): List<String> =
        listOfNotNull(INTERNET.takeIf { ScriptMeta.usesInternet(code) }, HEADER.takeIf { ScriptMeta.setsStatus(code) })

    /** Everything a script with [code] can be switched for, in display order. Only what it uses is included. */
    fun items(code: String): List<String> = base(code) + ScriptMeta.uses(code) + ScriptMeta.events(code)

    fun label(item: String): String = when (item) {
        INTERNET -> "Internet"
        HEADER -> "Pinned at the top"
        "notifications" -> "Your notifications"
        "apps" -> "Your app list"
        "clipboard" -> "Clipboard"
        "commands" -> "Run commands"
        "dnd" -> "Do Not Disturb"
        "work" -> "Work profile"
        else -> "Runs on $item"
    }

    fun help(item: String): String = when (item) {
        INTERNET -> "Web requests and feeds to any address, including your local network, and links and images in its output; when off, a run that needs a request stops"
        HEADER -> "Its status line at the top of home, in turns with what's playing and other pinned scripts; when off, it shows in its card and in status"
        "notifications" -> "Reads your recent notifications; when off, it sees none"
        "apps" -> "Lists your installed apps; when off, it sees none"
        "clipboard" -> "Reads and sets the clipboard; when off, it can't"
        "commands" -> "Runs terminal commands and other scripts when you start it from the prompt; when off, it can't"
        "dnd" -> "Reads and turns Do Not Disturb on and off; when off, it can't"
        "work" -> "Reads, pauses and resumes the work profile; when off, it can't"
        else -> "Runs when this happens (@on $item)"
    }

    /**
     * A script's access. It combines what its code declares with `@uses`, which of [INTERNET] and
     * [HEADER] it uses ([base]), and what the user decided. [HEADER] is off until pinned.
     */
    class Grants(val declared: Set<String>, private val values: Map<String, Boolean>, private val base: Set<String> = setOf(INTERNET, HEADER)) {
        fun allows(item: String): Boolean = when (item) {
            in ScriptMeta.USES -> item in declared && values[item] == true
            INTERNET -> item in base && values[item] != false
            HEADER -> item in base && values[item] == true
            else -> values[item] != false
        }

        /** Items that are declared but not yet allowed or switched off. Switching the script on asks for these. */
        val undecided: List<String> get() = declared.filter { it !in values }
    }

    fun grants(code: String, values: Map<String, Boolean>) = Grants(ScriptMeta.uses(code), values, base(code).toSet())

    /** [values], plus everything [code] declares that nobody has decided yet, set to allowed. */
    fun allowUndecided(code: String, values: Map<String, Boolean>): Map<String, Boolean> =
        values + ScriptMeta.uses(code).filter { it !in values }.associateWith { true }

    fun parse(raw: String?): Map<String, Boolean> =
        (raw?.let { runCatching { Json.parse(it) as? Map<*, *> }.getOrNull() }).orEmpty()
            .mapNotNull { (k, v) -> (v as? Boolean)?.let { k.toString() to it } }.toMap()

    fun encode(values: Map<String, Boolean>): String? = values.takeIf { it.isNotEmpty() }?.let(Json::stringify)

    /**
     * For a switched-on script, logs a line for each `@secret` that has no usable value. The line asks
     * the user to set it. A value that was set for other sites than the ones the line names now must
     * be set again.
     */
    fun askForSecrets(context: Context, s: Script) {
        if (!s.enabled) return
        val store = SecretStore(context)
        for (d in ScriptMeta.secrets(s.code)) {
            if (store.isSet(s.name, d)) continue
            val text = if (store.hostsChanged(s.name, d)) "${s.name} now sends ${d.name} to ${d.hosts.joinToString()} — tap to set it again"
            else "${s.name} needs ${d.name}" + (if (d.desc.isBlank()) "" else " (${d.desc})") + " — tap to set it"
            LogRepository.get(context).add(LogKind.INFO, text, s.name, LogContext.of(LogContext.RUN to "script secret ${s.name} ${d.name}"))
        }
    }

    /**
     * For a script switched on without the user's approval of what it declares (for example a timer
     * set in its sheet, or an import). Undecided items stay off, and a line asks the user to allow them.
     */
    fun askForNew(context: Context, s: Script) {
        askForSecrets(context, s)
        if (s.enabled) askToAllow(context, s.name, s.grants.undecided)
    }

    /** Logs "[name] asks for [items] — tap to allow". One tap allows them all. [what] is the verb in the line. */
    fun askToAllow(context: Context, name: String, items: List<String>, what: String = "requests") {
        if (items.isEmpty()) return
        LogRepository.get(context).add(
            LogKind.INFO, "$name $what ${items.joinToString(", ")} — tap to allow", name,
            LogContext.of(LogContext.RUN to "script allow $name ${items.joinToString(" ")}"),
        )
    }
}
