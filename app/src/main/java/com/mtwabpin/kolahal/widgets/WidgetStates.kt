package com.mtwabpin.kolahal.widgets

import android.content.Context
import android.content.Intent
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.Singleton
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.script.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What a script's widget has to show.
 * - [spec]: the description, or null when there is none or it can't be read.
 * - [hasContent]: something is stored. With a null [spec], the stored text can't be read ("widget unavailable").
 * - [setAt]: when `widget.show` last set it. [triedAt]: when a run last ended. Both in milliseconds; 0 for never.
 * - [error]: the last failed run's error, already masked. Null after a run that worked.
 */
data class WidgetState(val spec: WidgetSpec?, val hasContent: Boolean, val setAt: Long = 0, val triedAt: Long = 0, val error: String? = null)

/**
 * The store of what script widgets show, in two key-value scopes:
 * - [Stores.WIDGET]: the description as JSON ([WidgetJson]).
 * - [Stores.WIDGET_META]: `{"set":ms,"tried":ms,"error":"…"}`. Kolahal writes it, never the script.
 *
 * Each widget is kept by its key ([WidgetRef.key]). That is the `name` every function here takes:
 * the script's name for its first widget (`weather`), and the name with an id for a further one
 * (`weather#2`). Only [forgetScript] and [placedIds] take a script's name, and say so.
 *
 * Both processes use it. The :script process writes while a script runs, and tells the main process
 * with a message ([ACTION_CHANGED]) of its own, not the log's: the log's would make home read the
 * log again. The main process raises [version], and the cards read their state again.
 *
 * A run sends one message, when it ends, for everything it changed: it writes `quietly`, then calls
 * [announce] once. Every other write (a test's, the main process's) tells the cards itself.
 *
 * It is a cache: it stays on this phone and out of backups. Nothing here throws.
 */
object WidgetStates {
    /** Sent inside the app when a widget's state changed in the :script process. LauncherApp receives it. */
    const val ACTION_CHANGED = "com.mtwabpin.kolahal.WIDGETS_CHANGED"

    /** The longest error kept. A script can make its error text as long as it likes. */
    const val MAX_ERROR = 300

    private const val SET = "set"
    private const val TRIED = "tried"
    private const val ERROR = "error"

    /** The name of the widget that changed, in [ACTION_CHANGED]. Absent: any of them may have changed. */
    const val EXTRA_NAME = "name"

    private val _version = MutableStateFlow(0)
    /** Increases when any widget's state changes. */
    val version: StateFlow<Int> = _version.asStateFlow()

    /** Moves when a change named no widget, so a card with no entry in [versions] yet reads again too. */
    private val everyone = MutableStateFlow(0)

    private val _versions = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** For each widget, by its key, a number that increases when its state changes. */
    val versions: StateFlow<Map<String, Int>> = _versions.asStateFlow()

    /** [name]'s number in [versions]: a card collects only its own, and reads again only when it moves. */
    fun versionOf(name: String): Flow<Int> = combine(versions, everyone) { all, any -> (all[name] ?: 0) + any }.distinctUntilChanged()

    /** [name]'s state changed; null when it isn't known which widget's did (then every widget's number moves). */
    fun bump(name: String? = null) {
        _version.update { it + 1 }
        _versions.update { all ->
            if (name != null) all + (name to (all[name] ?: 0) + 1)
            else all.mapValues { it.value + 1 }
        }
        if (name == null) everyone.update { it + 1 }
    }


    /**
     * Stores [spec] as [name]'s widget, for `widget.show`.
     * - A description equal to the stored one is not written again.
     * - The content's time moves either way, and the last error is cleared.
     * - [quietly]: the cards are not told. A run stores this way, and tells them once when it ends ([announce]).
     */
    fun put(context: Context, name: String, spec: WidgetSpec, quietly: Boolean = false) {
        runCatching {
            val json = WidgetJson.encode(spec)
            val content = KvStore(context, Stores.WIDGET)
            if (content.get(name) != json) content.set(name, json)
            writeMeta(context, name, meta(context, name).copy(set = System.currentTimeMillis(), error = null))
        }
        if (!quietly) announce(context, name)
    }

    /** Removes [name]'s content, for `widget.clear`. Its times and its error stay. [quietly]: as for [put]. */
    fun clear(context: Context, name: String, quietly: Boolean = false) {
        runCatching { KvStore(context, Stores.WIDGET).set(name, null) }
        if (!quietly) announce(context, name)
    }

    /**
     * Notes that a run of [name] just ended: with [error] when it failed, with null when it worked.
     * [quietly]: as for [put].
     */
    fun ran(context: Context, name: String, error: String?, quietly: Boolean = false) {
        runCatching {
            writeMeta(context, name, meta(context, name).copy(tried = System.currentTimeMillis(), error = error?.take(MAX_ERROR)?.ifBlank { null }))
        }
        if (!quietly) announce(context, name)
    }

    /** Removes everything kept for the one widget [name]: when it is taken off the page. */
    fun forget(context: Context, name: String) {
        runCatching { KvStore(context, Stores.WIDGET).set(name, null) }
        runCatching { KvStore(context, Stores.WIDGET_META).set(name, null) }
        announce(context, name)
    }

    /**
     * Removes everything kept for every widget of the script [script]: when the script is deleted,
     * switched on or off, or changed from outside. What they showed came from the script as it was.
     * The widgets stay on the page, and their options stay.
     */
    fun forgetScript(context: Context, script: String) {
        for (scope in listOf(Stores.WIDGET, Stores.WIDGET_META)) runCatching {
            val store = KvStore(context, scope)
            store.set(script, null)
            // Every further widget's key starts with the name and the separator. A longer name does not.
            store.deleteStartingWith(WidgetRef.further(script))
        }
        // It is not known here which widgets the script has, so every card looks again.
        announce(context, null)
    }

    /**
     * The ids of [script]'s widgets on the widgets page, as the main process last copied them from
     * the placed list ([Stores.WIDGET_PLACED], [PlacedIds]). Null when the name has no row. The
     * `:script` process asks this, with [PlacedIds.holds], before it stores anything for a widget.
     */
    fun placedIds(context: Context, script: String): Set<String>? =
        PlacedIds.parse(runCatching { KvStore(context, Stores.WIDGET_PLACED).get(script) }.getOrNull())

    /**
     * [name]'s state. It reads the database, so call it on an IO thread. The stored description is
     * checked again ([WidgetJson.decode]): one that can't be read gives a state with content and no spec.
     */
    fun read(context: Context, name: String): WidgetState = runCatching {
        val raw = KvStore(context, Stores.WIDGET).get(name)
        val meta = meta(context, name)
        WidgetState(WidgetJson.decode(raw), raw != null, meta.set, meta.tried, meta.error)
    }.getOrDefault(WidgetState(null, false))

    private data class Meta(val set: Long = 0, val tried: Long = 0, val error: String? = null)

    private fun meta(context: Context, name: String): Meta = parseMeta(runCatching { KvStore(context, Stores.WIDGET_META).get(name) }.getOrNull())

    private fun writeMeta(context: Context, name: String, meta: Meta) = KvStore(context, Stores.WIDGET_META).set(name, encodeMeta(meta.set, meta.tried, meta.error))

    /** The meta text for these values. Internal for tests. */
    internal fun encodeMeta(set: Long, tried: Long, error: String?): String =
        Json.stringify(listOfNotNull(SET to set, TRIED to tried, error?.let { ERROR to it }).toMap())

    private fun parseMeta(text: String?): Meta {
        val map = text?.let { runCatching { Json.parse(it) as? Map<*, *> }.getOrNull() } ?: return Meta()
        return Meta(
            set = (map[SET] as? Number)?.toLong()?.coerceAtLeast(0) ?: 0,
            tried = (map[TRIED] as? Number)?.toLong()?.coerceAtLeast(0) ?: 0,
            error = (map[ERROR] as? String)?.take(MAX_ERROR)?.ifBlank { null },
        )
    }

    /**
     * Tells the cards that [name]'s state changed; with null, that any widget's may have. From the
     * :script process a message goes to the main one, as the log's does
     * ([com.mtwabpin.kolahal.data.LogRepository.ACTION_LOGS_CHANGED]). This process's own counter rises too.
     *
     * The writes above call it, unless they are asked to write quietly. Then the caller calls it,
     * once for all of them: a run does, when it ends.
     */
    fun announce(context: Context, name: String?) {
        if (Singleton.inScriptProcess) {
            // Without the name, the main process moves every widget's number ([bump]).
            runCatching { context.sendBroadcast(Intent(ACTION_CHANGED).setPackage(context.packageName).apply { if (name != null) putExtra(EXTRA_NAME, name) }) }
        }
        bump(name)
    }
}
