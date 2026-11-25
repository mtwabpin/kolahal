/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import android.content.Context
import androidx.core.content.edit
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.Stores

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
 * - [WIDGET], on the widgets page: a script with a `@widget` line has it only while its widget is
 *   placed there. Kolahal sets it from the placed list ([com.mtwabpin.kolahal.widgets.WidgetHost.syncAccess]),
 *   and nothing else does: the list is the truth, and the item is its copy ([following]).
 *   While it is off, `widget.show` stores nothing.
 * - Each `@on` event: on unless switched off.
 * When an item is off, the script gets an empty answer where that is honest (no apps, no
 * notifications, no clipboard, no todos, notes, reminders, events or feed items). Where it isn't
 * honest (internet), the script stops.
 * Posting notifications has its own switch ([com.mtwabpin.kolahal.data.Script.notify]). It is shown
 * for a script that calls `notify` ([ScriptMeta.postsNotifications]).
 */
object ScriptAccess {
    const val INTERNET = "internet"
    const val HEADER = "header"

    /** Its widget is placed on the widgets page. Off until you place it there. */
    const val WIDGET = "widget"

    /** Which of [INTERNET], [HEADER] and [WIDGET] the [code] uses. */
    internal fun base(code: String): List<String> = listOfNotNull(
        INTERNET.takeIf { ScriptMeta.parse(code).usesInternet },
        HEADER.takeIf { ScriptMeta.parse(code).setsStatus },
        WIDGET.takeIf { ScriptMeta.parse(code).widget != null },
    )

    /**
     * What a shared script asks for, named before `script get` switches it on: the internet when
     * its [code] uses it, then what it declares with `@uses`. Empty when it asks for none of that:
     * then it may arrive switched on. (Pinning is off until you pin, and events only start runs.)
     */
    fun asksFor(code: String): List<String> =
        listOfNotNull(INTERNET.takeIf { ScriptMeta.parse(code).usesInternet }) + ScriptMeta.USES.filter {
            it in ScriptMeta.parse(
                code
            ).uses
        }

    /** Everything a script with [code] can be switched for, in display order. Only what it uses is included. */
    fun items(code: String): List<String> =
        base(code) + ScriptMeta.parse(code).uses + ScriptMeta.parse(code).events

    /**
     * The items a user switches by hand: the script's sheet and card, `script access` and
     * `script allow|deny`. [WIDGET] isn't one: placing the widget turns it on, and removing it turns
     * it off ([com.mtwabpin.kolahal.widgets.WidgetHost.placeScript]).
     */
    fun switchable(code: String): List<String> = items(code) - WIDGET

    fun label(item: String): String = when (item) {
        INTERNET -> "Internet"
        HEADER -> "Pinned at the top"
        WIDGET -> "On the widgets page"
        "notifications" -> "Your notifications"
        "apps" -> "Your app list"
        "clipboard" -> "Clipboard"
        "commands" -> "Run commands"
        "dnd" -> "Do Not Disturb"
        "work" -> "Work profile"
        "todos" -> "Your to-dos"
        "notes" -> "Your notes"
        "reminders" -> "Your reminders"
        "calendar" -> "Your calendar"
        "feeds" -> "Your feeds"
        "usage" -> "Your app usage"
        "screen" -> "Full screen"
        "effects" -> "Effects"
        else -> "Runs on $item"
    }

    fun help(item: String): String = when (item) {
        INTERNET -> "Web requests and feeds to any address, including your local network, and links and images in its output; when off, a run that needs a request stops"
        HEADER -> "Its status line at the top of home, in turns with what's playing and other pinned scripts; when off, it shows in its card and in status"
        WIDGET -> "Its widget on the widgets page, on while the widget is placed there; when off, what it shows is not stored"
        "notifications" -> "Reads your recent notifications; when off, it sees none"
        "apps" -> "Lists your installed apps; when off, it sees none"
        "clipboard" -> "Reads and sets the clipboard; when off, it can't"
        "commands" -> "Runs terminal commands and other scripts when you start it from the prompt; when off, it can't"
        "dnd" -> "Reads and turns Do Not Disturb on and off; when off, it can't"
        "work" -> "Reads, pauses and resumes the work profile; when off, it can't"
        "todos" -> "Reads your to-do list, and marks to-dos done or open; when off, it sees none and changes none"
        "notes" -> "Reads your notes; when off, it sees none"
        "reminders" -> "Reads your reminders; when off, it sees none"
        "calendar" -> "Reads the events of the next days from the phone's calendar; when off, it sees none"
        "feeds" -> "Reads the unread items of the feeds you follow; when off, it sees none"
        "usage" -> "Reads your screen time per app, your data use and the apps you don't open; when off, it sees none"
        "screen" -> "Draws on the whole of home when you start it, and reads your taps and keys there until it closes; when off, it can't"
        "effects" -> "Plays a one-off shader, such as hearts, over home for some seconds; when off, it plays none"
        else -> "Runs when this happens (@on $item)"
    }

    /**
     * The help of the notification event for a script whose `@on notification` names apps
     * ([ScriptMeta.notificationApps]): [apps] are their names, or their packages when not installed.
     */
    fun notificationHelp(apps: List<String>): String =
        if (apps.isEmpty()) "Runs for no app: its @on notification line names no package"
        else NOTIFICATIONS_FROM + apps.joinToString(", ") + NOTIFICATIONS_LINE

    /** The words before and after the apps in [notificationHelp]. The script's sheet puts an installed app's icon beside its name. */
    const val NOTIFICATIONS_FROM = "Runs on notifications from "
    const val NOTIFICATIONS_LINE = " (@on notification)"

    /**
     * A script's access. It combines what its code declares with `@uses`, which of [INTERNET] and
     * [HEADER] it uses ([base]), and what the user decided. [HEADER] is off until pinned, and
     * [WIDGET] is off until its widget is placed.
     */
    class Grants(
        val declared: Set<String>,
        private val values: Map<String, Boolean>,
        private val base: Set<String> = setOf(INTERNET, HEADER)
    ) {
        fun allows(item: String): Boolean = when (item) {
            in ScriptMeta.USES -> item in declared && values[item] == true
            INTERNET -> item in base && values[item] != false
            HEADER -> item in base && values[item] == true
            // Its own case: the last one would make it "on unless switched off".
            WIDGET -> item in base && values[item] == true
            else -> values[item] != false
        }

        /** Items that are declared but not yet allowed or switched off. Switching the script on asks for these. */
        val undecided: List<String> get() = declared.filter { it !in values }
    }

    fun grants(code: String, values: Map<String, Boolean>) =
        Grants(ScriptMeta.parse(code).uses, values, base(code).toSet())

    /** A private item: the words the warning names it by, after "your" ([what]), and why it counts ([why]). */
    class Reads(val what: String, val why: String)

    /**
     * What a script can read about you. With [INTERNET] on as well, it can send that off the phone, so
     * the script's page, its card, the dialogs that grant access and `script access` warn of the pair
     * ([privateWithInternet], [warning]). They warn and block nothing.
     * - [INTERNET] is the access itself: Kolahal doesn't look at addresses, so every address counts.
     * - Each access is here or in [NOT_PRIVATE], with why (ScriptPrivacyTest fails on one in neither).
     */
    val PRIVATE: Map<String, Reads> = linkedMapOf(
        "notifications" to Reads("notifications", "The text of the notifications you get from every app: messages, mail, sign-in codes"),
        "clipboard" to Reads("clipboard", "What you copied last, which may be a password, an address or a message"),
        "apps" to Reads("app list", "The apps you installed, which can tell your bank, your health and your habits"),
        "work" to Reads("work profile status", "Whether you have a work profile, and when you pause it"),
        "todos" to Reads("to-dos", "Your own list, in your words"),
        "notes" to Reads("notes", "Your own notes, in your words"),
        "reminders" to Reads("reminders", "What you set to be reminded of, and when"),
        "calendar" to Reads("calendar", "Your events: where you will be, when and with whom"),
        "feeds" to Reads("feeds", "The sites you follow, and what you haven't read"),
        "usage" to Reads("app usage", "Your screen time per app, your data use and the apps you don't open"),
    )

    /** The accesses that read nothing about you, with why. Each access is here or in [PRIVATE]. */
    val NOT_PRIVATE: Map<String, String> = linkedMapOf(
        INTERNET to "The way out, not data: the warning is about it together with a private item",
        HEADER to "Shows its own status line at the top of home; reads nothing",
        WIDGET to "Shows its own widget on the widgets page; reads nothing",
        "notify" to "Posts notifications to you; reads none",
        "commands" to "Runs commands as typed; launcher.run answers only true or false, so no output comes back",
        "dnd" to "A setting of the phone (off, priority, alarms or none), not data about you",
        "screen" to "Draws a screen of its own; the taps and keys it reads are the ones you give it there",
        "effects" to "Plays a shader over home; reads nothing",
        "unlock" to "Starts a run when you unlock the phone; the event carries nothing",
        "charger" to "Starts a run on charging; the event carries the battery level",
        "unplug" to "Starts a run when unplugged; the event carries the battery level",
        "wifi" to "Starts a run on Wi-Fi; the event carries no network name",
        "offline" to "Starts a run when the phone goes offline; the event carries nothing",
        "notification" to "Starts a run on a notification, and only with notifications allowed: that item is the private one",
        "boot" to "Starts a run after a restart; the event carries the uptime",
        "headset" to "Starts a run when a headset connects; the event carries its kind and name",
    )

    /**
     * The private items ([PRIVATE]) that [grants] allow, when they allow [INTERNET] too: what the
     * script can read and send off the phone. Empty without the internet, or without a private item.
     * What counts is what is switched on now, not what the code declares.
     */
    fun privateWithInternet(grants: Grants): List<String> =
        if (grants.allows(INTERNET)) PRIVATE.keys.filter(grants::allows) else emptyList()

    /**
     * The same for [items] as if each one were allowed: what a dialog or a line is about to allow,
     * with what the script has on already.
     */
    fun privateWithInternet(items: Collection<String>): List<String> =
        if (INTERNET in items) PRIVATE.keys.filter { it in items } else emptyList()

    /** "can read your notifications and use the internet": what the [items] of [privateWithInternet] let a script do. */
    fun canRead(items: List<String>): String {
        val names = items.map { PRIVATE[it]?.what ?: it }
        val list =
            if (names.size == 1) names[0] else names.dropLast(1).joinToString(", ") + " and " + names.last()
        return "can read your $list" + (if (names.size > 1) ", and" else " and") + " use the internet"
    }

    /** What to do about the pair, after what the script can do. */
    const val TRUST = "Only allow both for a script you trust"

    /** The pair's name: the filter that finds such scripts, and their tag in `script ls`. */
    const val PAIR_NAME = "Internet and private data"

    /**
     * The line on a script's page, on its open card and in the dialogs that grant access. Null when
     * [items] ([privateWithInternet]) is empty: "This script can read your notifications and use the
     * internet. Only allow both for a script you trust."
     */
    fun warning(items: List<String>): String? =
        if (items.isEmpty()) null else "This script ${canRead(items)}. $TRUST."

    /** The same as hint lines, under a log line about the script. Empty when [items] is empty. */
    fun warningHint(items: List<String>): String =
        if (items.isEmpty()) "" else "\nIt ${canRead(items)}\n$TRUST"

    /** [values], plus everything [code] declares that nobody has decided yet, set to allowed. */
    fun allowUndecided(code: String, values: Map<String, Boolean>): Map<String, Boolean> =
        values + ScriptMeta.parse(code).uses.filter { it !in values }.associateWith { true }

    /**
     * [values] with the [WIDGET] item as the placed list says: on while the script's widget is on the
     * widgets page ([placed]), off when it is not. Null when it says so already: then nothing is written.
     * - A script whose widget was never placed gets no item. A missing item is off.
     * - Only this sets the item ([com.mtwabpin.kolahal.data.ScriptRepository.setWidgetPlaced]). It
     *   changes the access as stored at that moment, so a switch set beside it is kept.
     */
    fun following(values: Map<String, Boolean>, placed: Boolean): Map<String, Boolean>? =
        if ((values[WIDGET] == true) == placed) null else values + (WIDGET to placed)

    /**
     * [values] with the [WIDGET] item as it is in [now], the access stored at this moment. For a
     * write that puts back an older copy of the access (undo after an update): the widget may have
     * been placed or removed since the copy was taken, and the placed list decides the item.
     */
    fun keepingWidget(
        values: Map<String, Boolean>,
        now: Map<String, Boolean>
    ): Map<String, Boolean> =
        values - WIDGET + listOfNotNull(now[WIDGET]?.let { WIDGET to it })

    fun parse(raw: String?): Map<String, Boolean> =
        (raw?.let { runCatching { Json.parse(it) as? Map<*, *> }.getOrNull() }).orEmpty()
            .mapNotNull { (k, v) -> (v as? Boolean)?.let { k.toString() to it } }.toMap()

    fun encode(values: Map<String, Boolean>): String? =
        values.takeIf { it.isNotEmpty() }?.let(Json::stringify)

    /**
     * Once per install: each switched-on script with a secret value from before 0.4.0 (stored without
     * its sites) asks for it again ([askForSecrets]). Such a value is no longer used until then.
     */
    fun askForSecretsWithoutSites(context: Context) {
        val prefs = Stores.LAUNCHER.open(context)
        if (prefs.getBoolean(SECRET_SITES_ASKED, false)) return
        val store = SecretStore(context)
        com.mtwabpin.kolahal.data.ScriptRepository.get(context).all()
            .filter { s ->
                s.enabled && ScriptMeta.secrets(s.code).any { store.withoutSites(s.name, it) }
            }
            .forEach { askForSecrets(context, it) }
        prefs.edit { putBoolean(SECRET_SITES_ASKED, true) }
    }

    private const val SECRET_SITES_ASKED = "secret_sites_asked"

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
            val text = if (store.withoutSites(
                    s.name,
                    d
                )
            ) "${s.name}: set ${d.name} again\nThen it goes only to ${d.hosts.joinToString()}\nTap to set it"
            else if (store.hostsChanged(
                    s.name,
                    d
                )
            ) "${s.name}: set ${d.name} again\nIt now goes to ${d.hosts.joinToString()}\nTap to set it again"
            else "${s.name} needs ${d.name}" + (if (d.desc.isBlank()) "" else "\n${d.desc}") + "\nTap to set it"
            LogRepository.get(context).add(
                LogKind.INFO,
                text,
                s.name,
                LogContext.of(LogContext.RUN to "script secret ${s.name} ${d.name}")
            )
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

    /**
     * Logs "[name] requests [items]" and "Tap to allow" under it. One tap allows them all. [what] is the
     * verb in the line. When allowing them gives the script the internet together with private data
     * ([privateWithInternet]), the line says so before the tap ([warningHint]).
     */
    fun askToAllow(context: Context, name: String, items: List<String>, what: String = "requests") {
        if (items.isEmpty()) return
        val grants = com.mtwabpin.kolahal.data.ScriptRepository.get(context).byName(name)?.grants
        val pair = grants?.let { g ->
            privateWithInternet(items + (PRIVATE.keys + INTERNET).filter(g::allows))
        }.orEmpty().takeIf { p -> items.any { it == INTERNET || it in p } }.orEmpty()
        LogRepository.get(context).add(
            LogKind.INFO, "$name $what ${items.joinToString(", ")}\nTap to allow" + warningHint(pair), name,
            LogContext.of(LogContext.RUN to "script allow $name ${items.joinToString(" ")}"),
        )
    }
}
