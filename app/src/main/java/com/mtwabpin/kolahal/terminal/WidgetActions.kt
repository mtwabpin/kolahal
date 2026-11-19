package com.mtwabpin.kolahal.terminal

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.productivity.Agenda
import com.mtwabpin.kolahal.productivity.AgendaEvent
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.widgets.WidgetRef
import com.mtwabpin.kolahal.widgets.WidgetTap
import kotlinx.coroutines.flow.StateFlow

/**
 * A script as the widget screens see it: the cards, and the picker's rows. It is read from the
 * script's code off the main thread ([WidgetScriptReader]), so nothing reads code while it is drawn.
 * - [title]: its `@title`, or null.
 * - [enabled]: the script is on.
 * - [widget]: its `@widget` line, or null when it has none.
 * - [hasOptions]: its code has an `@option` line. Then each of its widgets has options to set.
 */
data class WidgetScript(
    val name: String,
    val title: String?,
    val enabled: Boolean,
    val widget: ScriptMeta.Widget?,
    val hasOptions: Boolean = false,
)

/**
 * Makes [WidgetScript]s from the script list. The list changes at every run of a script, but code
 * changes rarely: each script's details are read once per code ([ScriptMeta.parse]). One thread at a
 * time uses it.
 */
internal class WidgetScriptReader {
    private var known: Map<String, WidgetScript> = emptyMap()

    /** [scripts] by name. A script that did not change keeps the object it had, so an equal list gives the same map's objects. */
    fun read(scripts: List<Script>): Map<String, WidgetScript> = scripts.associate { s ->
        val p = ScriptMeta.parse(s.code)
        val fresh = WidgetScript(s.name, p.title, s.enabled, p.widget, hasOptions = p.options.isNotEmpty())
        s.name to (known[s.name]?.takeIf { it == fresh } ?: fresh)
    }.also { known = it }
}

/**
 * What a card reads from the view model, so that no card reads the database for it.
 * - [scripts]: each script as the widget screens see it, by name. Null until the scripts are read.
 *   After that, a name that is not in it has no script.
 * - [own]: Kolahal's own data, for the built-in widgets ([OwnFeed]). Null for a feed made without it, in a test.
 */
class WidgetFeed(val scripts: StateFlow<Map<String, WidgetScript>?>, val own: OwnFeed? = null)

/**
 * What a card on the widgets page can ask for. The card calls these, and the view model answers with
 * [WidgetActions]. A card with no actions (the Widgets screen, while arranging) takes no taps.
 */
interface WidgetCardActions {
    /**
     * Runs the script of the widget [widget] again, quietly, to fill that one widget. [widget] is
     * the widget's key ([WidgetRef.key]): `weather`, or `weather#2` for a script's further widget.
     * The page asks for this, so the run is not the user's.
     */
    fun refresh(widget: String)

    /**
     * A tap on the card of the widget [widget] (its key, as for [refresh]), or on one of its rows.
     * A [tap] with no arguments and no link means "refresh".
     */
    fun tap(widget: String, tap: WidgetTap)

    /** A tap on the Battery and network card: opens Android's battery screen, as the header's battery does. */
    fun openBattery()

    /**
     * A tap on a card whose script can't fill it as it is: the script is switched off, or has lost
     * its `@widget` line. Opens the Scripts screen, where it is switched on or edited.
     */
    fun openScripts()

    /** A long-press on a card: opens the screen to arrange widgets. */
    fun longPress()

    /**
     * A tap inside the card of the built-in widget [id] (`todos`, `media`). [what] is one of that
     * widget's few words ([OwnTaps]): `open`, `done #12`, `next`. Other words do nothing. A command
     * is never among them.
     */
    fun own(id: String, what: String) {}

    /**
     * The card of the built-in widget [id] came into view. What can be old is read again: the
     * calendar, at most once in a minute, and a list that something wrote past its store.
     */
    fun ownShown(id: String) {}
}

/**
 * Which widgets are old, and so worth one quiet run when the page shows them. Pure, like
 * [PinnedTurns]. All times are in milliseconds, on the wall clock.
 */
internal object WidgetAge {
    /** How long content counts as current when its description has no `staleAfter`: one hour. */
    const val DEFAULT_STALE_S = 3600

    /** The last time anything happened to the widget: its content was set, or a run was tried. 0 means never. */
    fun last(setAt: Long, triedAt: Long): Long = maxOf(setAt, triedAt)

    private fun limit(staleAfter: Int?): Long = (staleAfter ?: DEFAULT_STALE_S).coerceAtLeast(1) * 1000L

    /** True when content set at [setAt] is older than [staleAfter] seconds at [now]. The card then says its age. */
    fun old(setAt: Long, staleAfter: Int?, now: Long): Boolean = now - setAt >= limit(staleAfter)

    /**
     * True when the page should ask for one run at [now].
     * - A widget that nothing has filled or tried yet is due at once.
     * - After that, the last content or the last try must be [staleAfter] seconds old. So a run that
     *   failed, or that showed nothing, is not asked for again and again.
     * - A time in the future (the clock was set back) counts as old.
     */
    fun due(setAt: Long, triedAt: Long, staleAfter: Int?, now: Long): Boolean {
        val age = now - last(setAt, triedAt)
        return age < 0 || age >= limit(staleAfter)
    }
}

/**
 * Taps and refresh requests from the cards Kolahal draws on the widgets page. The view model holds
 * one as `widgetActions`.
 * - A refresh is a quiet run with the event "widget", for one widget. It is never two at once for one widget,
 *   and one script's runs go one after another.
 * - A tap with arguments runs the same script again, as a button under a log line does. It counts
 *   as a run the user started.
 * - A tap with a link opens it, as a link in a log line does.
 * - A tap on the Battery and network card opens Android's battery screen.
 * - A tap inside a built-in card ([own]) does one of the few things in [OwnTaps]. None of them runs a script.
 *
 * [taps]: one item starts at most one run in 2 seconds. The header and the log's buttons share this gate.
 * [ownData]: Kolahal's own data, which the built-in cards show and their taps change.
 */
class WidgetActions internal constructor(
    private val context: Context,
    private val taps: TapGate,
    private val ownData: OwnData,
    private val host: Host,
) : WidgetCardActions {
    /** What these actions need from the view model. */
    interface Host {
        fun io(block: suspend () -> Unit)
        /** Opens a web link. Anything else is ignored. */
        fun openUrl(url: String)
        fun openScreen(screen: Screen)
        /** Turns home to the log page. */
        fun showLog()
        /**
         * Runs [command] as if you had typed it and pressed Enter, as a gesture runs the command you
         * set for it, and turns home to the log. So it may be a sensitive command. Only for a command
         * that you chose yourself (a Shortcuts button), or a fixed one of Kolahal's own (`todo`).
         */
        fun runTyped(command: String)
        /** Asks Android for the calendar permission, as `agenda --allow` does. */
        fun requestCalendar()
        /** Asks whether to open Android's Notification access screen, as `notifs enable` does. */
        fun requestNotificationAccess()
    }

    private val scripts = ScriptRepository.get(context)
    private val logs = LogRepository.get(context)

    override fun refresh(widget: String) {
        val ref = WidgetRef.parse(widget) ?: return
        host.io {
            // Without its `@widget` line a run stores nothing for the card, so there is nothing to run for.
            scripts.byName(ref.script)?.takeIf { it.enabled && ScriptMeta.widget(it.code) != null }?.let { ScriptScheduler.refreshWidget(context, it, ref.id) }
        }
    }

    override fun tap(widget: String, tap: WidgetTap) {
        val ref = WidgetRef.parse(widget) ?: return
        val script = ref.script
        val url = tap.url
        val args = tap.args
        when {
            url != null -> host.openUrl(url)
            args != null -> {
                // The gate is by widget: the same button on two widgets of one script starts a run for each.
                if (!taps.allow("widget:$widget:$args")) return
                host.io {
                    val s = scripts.byName(script)
                    when {
                        s == null -> logs.add(LogKind.ERROR, "No script named '$script' — 'script ls'")
                        !s.enabled -> logs.add(LogKind.INFO, "$script is off: script on $script", ctx = LogContext.of(LogContext.COMMAND to "script on $script"))
                        else -> ScriptScheduler.runNow(context, s, words(args), event(tap.label, ref.id))
                    }
                }
                if (tap.toLog) host.showLog()
            }
            // No action on the card: a tap refreshes it.
            else -> if (taps.allow("widget:$widget")) refresh(widget)
        }
    }

    // Some phones have no such screen. Then the log says so, as for any screen that can't open.
    override fun openBattery() = start(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))

    /** Opens [intent] in its own task. When nothing can open it, the log says so. */
    private fun start(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { host.io { logs.add(LogKind.ERROR, "Can't open: ${it.message}") } }
    }

    override fun openScripts() = host.openScreen(Screen.SCRIPTS)

    override fun longPress() = host.openScreen(Screen.WIDGETS)

    override fun own(id: String, what: String) {
        when (val tap = OwnTaps.parse(id, what) ?: return) {
            // A checkbox and a media button answer every tap: no 2-second gate. Neither prints a line.
            is OwnTap.SetTodo -> ownData.setTodo(tap.id, tap.done)
            is OwnTap.Media -> ownData.media(tap.action)
            // A command prints in the log, so quick taps on one item run it once, as for a header item.
            is OwnTap.Typed -> if (taps.allow("own:$id:$what")) host.runTyped(tap.command)
            is OwnTap.RunShortcut -> {
                // The command is looked up now, in your list: the card's description never holds it.
                val shortcut = ownData.shortcut(tap.index) ?: return
                if (taps.allow("own:$id:$what")) host.runTyped(shortcut.command)
            }
            OwnTap.EditShortcuts -> {
                // The Widgets screen is where the list is changed. It opens with the editor.
                ownData.askShortcutsEditor()
                host.openScreen(Screen.WIDGETS)
            }
            OwnTap.OpenPlayer -> {
                val playing = ownData.playing ?: return
                val player = context.packageManager.getLaunchIntentForPackage(playing.pkg)
                if (player != null) start(player) else host.io { logs.add(LogKind.ERROR, "Can't open ${playing.app}") }
            }
            OwnTap.AllowNotifications -> host.requestNotificationAccess()
            OwnTap.AllowCalendar -> host.requestCalendar()
            is OwnTap.OpenEvent -> start(eventIntent(tap.id, ownData.event(tap.id, tap.begin)))
        }
    }

    override fun ownShown(id: String) = ownData.shown(id)

    companion object {
        /** The arguments of a tap, split at spaces, as a line button's are. */
        internal fun words(args: String): List<String> = args.split(Regex("\\s+")).filter { it.isNotEmpty() }

        /**
         * The `event` of a run that a tap on a widget starts: a line button's event, marked as a
         * widget's. [widget] is the id of the widget that was tapped ([WidgetRef.id]). A further
         * widget's event says which one it is ([ScriptScheduler.INSTANCE]), so the run fills that
         * widget with that widget's options. The first widget's event is as it always was.
         */
        internal fun event(label: String?, widget: String = ""): Map<String, Any?> = buildMap {
            put("name", "action")
            if (label != null) put("label", label)
            put("widget", true)
            if (widget.isNotEmpty()) put(ScriptScheduler.INSTANCE, widget)
        }

        /**
         * Opens the calendar event [id] in the calendar app. [event] is the event as the Agenda card
         * last read it, or null. Its times say which time of a repeating event to show.
         */
        internal fun eventIntent(id: Long, event: AgendaEvent?): Intent = Intent(Intent.ACTION_VIEW, Agenda.eventUri(id)).apply {
            if (event != null) {
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
            }
        }
    }
}
