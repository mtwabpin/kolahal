package com.mtwabpin.kolahal.terminal

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.StatusLine
import com.mtwabpin.kolahal.data.StatusLines
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.widgets.WidgetTap
import kotlinx.coroutines.flow.StateFlow

/**
 * A script as the widget screens see it: the cards, and the picker's rows. It is read from the
 * script's code off the main thread ([WidgetScriptReader]), so nothing reads code while it is drawn.
 * - [title]: its `@title`, or null.
 * - [enabled]: the script is on.
 * - [widget]: its `@widget` line, or null when it has none.
 * - [setsStatus]: its code sets a status line.
 */
data class WidgetScript(val name: String, val title: String?, val enabled: Boolean, val widget: ScriptMeta.Widget?, val setsStatus: Boolean)

/**
 * Makes [WidgetScript]s from the script list. The list changes at every run of a script, but code
 * changes rarely. So the reader keeps what it read: a script whose code did not change is not read
 * again. One thread at a time uses it.
 */
internal class WidgetScriptReader {
    private var codes: Map<String, String> = emptyMap()
    private var known: Map<String, WidgetScript> = emptyMap()

    /** [scripts] by name. A script that did not change keeps the object it had, so an equal list gives an equal map. */
    fun read(scripts: List<Script>): Map<String, WidgetScript> {
        val next = scripts.associate { s ->
            val before = known[s.name]?.takeIf { codes[s.name] == s.code }
            s.name to when {
                before == null -> WidgetScript(s.name, ScriptMeta.title(s.code), s.enabled, ScriptMeta.widget(s.code), ScriptMeta.setsStatus(s.code))
                before.enabled != s.enabled -> before.copy(enabled = s.enabled)
                else -> before
            }
        }
        codes = scripts.associate { it.name to it.code }
        known = next
        return next
    }
}

/**
 * What a card reads from the view model, so that no card reads the database for it.
 * - [scripts]: each script as the widget screens see it, by name. Null until the scripts are read.
 *   After that, a name that is not in it has no script.
 * - [lines]: every stored status line, as the pinned header gets them. They are read before
 *   [scripts] gets its first value.
 */
class WidgetFeed(val scripts: StateFlow<Map<String, WidgetScript>?>, val lines: StateFlow<List<StatusLine>>)

/**
 * What a card on the widgets page can ask for. The card calls these, and the view model answers with
 * [WidgetActions]. A card with no actions (the Widgets screen, while arranging) takes no taps.
 */
interface WidgetCardActions {
    /** Runs [script] again, quietly, to fill its widget. The page asks for this, so the run is not the user's. */
    fun refresh(script: String)

    /** A tap on the card of [script], or on one of its rows. A [tap] with no arguments and no link means "refresh". */
    fun tap(script: String, tap: WidgetTap)

    /** A tap on the Status card of [script]: runs the script, as a tap on its pinned line does. */
    fun runStatus(script: String)

    /**
     * The Status card of [script] is in view, and its line asks to run again every [every] seconds
     * (`refresh`). Runs the script again, quietly, if that is due now. Returns how long the card
     * waits before it asks again, in milliseconds.
     *
     * The pinned header asks for the same runs, and both go by one table of last runs ([StatusTurns]).
     * So a script that is pinned and also has a card runs once in each period, not twice.
     */
    fun refreshStatus(script: String, every: Int): Long

    /** A tap on the Battery and network card: opens Android's battery screen, as the header's battery does. */
    fun openBattery()

    /**
     * A tap on a card whose script can't fill it: the script is gone, switched off, or has lost its
     * `@widget` line. Opens the Scripts screen, where it is switched on, added again or edited.
     */
    fun openScripts()

    /** A long-press on a card: opens the screen to arrange widgets. */
    fun longPress()
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
 * When a status line's `refresh` is due for a Status card. Pure, like [PinnedTurns]. Times are in
 * milliseconds on one clock.
 *
 * The cards and the pinned header share one table of last runs, by script name (the view model's).
 * [PinnedTurns.due] reads it for the header, and the view model writes it. [due] reads and writes
 * it for a card. Whoever asks first runs the script, and the other one then waits a whole period.
 */
internal object StatusTurns {
    /**
     * How long to wait before the script of a line with `refresh = every` (seconds) runs again.
     * 0 means now: it has not run for this yet, or its last such run ([lastRun]) is that old.
     * A pinned line is due by the same rule.
     */
    fun waitMs(every: Int, lastRun: Long?, now: Long): Long {
        if (lastRun == null) return 0L
        val gap = every.coerceIn(StatusLines.MIN_REFRESH, StatusLines.MAX_REFRESH) * 1000L
        return (gap - (now - lastRun)).coerceIn(0L, gap)
    }

    /**
     * A card asks at [now] whether [name]'s script runs again. True when it is due. [runs] then has
     * [now] as its last run, so the next question, from the card or the header, waits a whole period.
     */
    fun due(runs: MutableMap<String, Long>, name: String, every: Int, now: Long): Boolean {
        if (waitMs(every, runs[name], now) > 0L) return false
        runs[name] = now
        return true
    }
}

/**
 * Taps and refresh requests from the cards Kolahal draws on the widgets page. The view model holds
 * one as `widgetActions`.
 * - A refresh is a quiet run with the event "widget". It is never two at once for one script.
 * - A tap with arguments runs the same script again, as a button under a log line does. It counts
 *   as a run the user started.
 * - A tap with a link opens it, as a link in a log line does.
 * - A tap on a Status card runs its script and says so in the log, as a tap on a pinned line does.
 * - A tap on the Battery and network card opens Android's battery screen.
 *
 * [taps]: one item starts at most one run in 2 seconds. The header and the log's buttons share this gate.
 * [statusRuns]: when each status line last ran again for its `refresh`, in [SystemClock.elapsedRealtime]
 * time. It is the pinned header's own table ([StatusTurns]). Only the main thread uses it.
 */
class WidgetActions(
    private val context: Context,
    private val taps: TapGate,
    private val statusRuns: MutableMap<String, Long>,
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
    }

    private val scripts = ScriptRepository.get(context)
    private val logs = LogRepository.get(context)

    override fun refresh(script: String) = host.io {
        // Without its `@widget` line a run stores nothing for the card, so there is nothing to run for.
        scripts.byName(script)?.takeIf { it.enabled && ScriptMeta.widget(it.code) != null }?.let { ScriptScheduler.refreshWidget(context, it) }
    }

    override fun tap(script: String, tap: WidgetTap) {
        val url = tap.url
        val args = tap.args
        when {
            url != null -> host.openUrl(url)
            args != null -> {
                if (!taps.allow("widget:$script:$args")) return
                host.io {
                    val s = scripts.byName(script)
                    when {
                        s == null -> logs.add(LogKind.ERROR, "No script named '$script' — 'script ls'")
                        !s.enabled -> logs.add(LogKind.INFO, "$script is off: script on $script", ctx = LogContext.of(LogContext.COMMAND to "script on $script"))
                        else -> ScriptScheduler.runNow(context, s, words(args), event(tap.label))
                    }
                }
                if (tap.toLog) host.showLog()
            }
            // No action on the card: a tap refreshes it.
            else -> if (taps.allow("widget:$script")) refresh(script)
        }
    }

    override fun runStatus(script: String) {
        // The key of a pinned line's tap. So the line and its card start one run in 2 seconds, not two.
        if (!taps.allow("pin:$script")) return
        host.io {
            val s = scripts.byName(script)
            when {
                s == null -> logs.add(LogKind.ERROR, "No script named '$script' — 'script ls'")
                !s.enabled -> logs.add(LogKind.INFO, "$script is off: script on $script", ctx = LogContext.of(LogContext.COMMAND to "script on $script"))
                else -> logs.add(LogKind.INFO, runNowWithText(context, s), ctx = LogContext.of(LogContext.SCRIPT to script))
            }
        }
    }

    override fun refreshStatus(script: String, every: Int): Long {
        val now = SystemClock.elapsedRealtime()
        if (StatusTurns.due(statusRuns, script, every, now)) host.io {
            scripts.byName(script)?.takeIf { it.enabled }?.let { ScriptScheduler.refreshPinned(context, it) }
        }
        // Just after a run this is the whole period, so the card's loop never spins.
        return StatusTurns.waitMs(every, statusRuns[script], now)
    }

    override fun openBattery() {
        // Some phones have no such screen. Then the log says so, as for any screen that can't open.
        runCatching { context.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { host.io { logs.add(LogKind.ERROR, "Can't open: ${it.message}") } }
    }

    override fun openScripts() = host.openScreen(Screen.SCRIPTS)

    override fun longPress() = host.openScreen(Screen.WIDGETS)

    companion object {
        /** The arguments of a tap, split at spaces, as a line button's are. */
        internal fun words(args: String): List<String> = args.split(Regex("\\s+")).filter { it.isNotEmpty() }

        /** The `event` of a run that a tap on a widget starts: a line button's event, marked as a widget's. */
        internal fun event(label: String?): Map<String, Any?> = buildMap {
            put("name", "action")
            if (label != null) put("label", label)
            put("widget", true)
        }
    }
}
