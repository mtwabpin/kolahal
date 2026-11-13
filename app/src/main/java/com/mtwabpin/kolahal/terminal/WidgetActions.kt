package com.mtwabpin.kolahal.terminal

import android.content.Context
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.widgets.WidgetTap

/**
 * What a script widget's card can ask for. The card calls these, and the view model answers with
 * [WidgetActions]. A card with no actions (the Widgets screen, while arranging) takes no taps.
 */
interface WidgetCardActions {
    /** Runs [script] again, quietly, to fill its widget. The page asks for this, so the run is not the user's. */
    fun refresh(script: String)

    /** A tap on the card of [script], or on one of its rows. A [tap] with no arguments and no link means "refresh". */
    fun tap(script: String, tap: WidgetTap)

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
 * Taps and refresh requests from the script widgets on the widgets page. The view model holds one
 * as `widgetActions`.
 * - A refresh is a quiet run with the event "widget". It is never two at once for one script.
 * - A tap with arguments runs the same script again, as a button under a log line does. It counts
 *   as a run the user started.
 * - A tap with a link opens it, as a link in a log line does.
 *
 * [taps]: one item starts at most one run in 2 seconds. The header and the log's buttons share this gate.
 */
class WidgetActions(private val context: Context, private val taps: TapGate, private val host: Host) : WidgetCardActions {
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
        scripts.byName(script)?.takeIf { it.enabled }?.let { ScriptScheduler.refreshWidget(context, it) }
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
