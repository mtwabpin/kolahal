package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.data.Stores
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.SizeF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.content.edit
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.Singleton
import com.mtwabpin.kolahal.data.ThisInstall
import com.mtwabpin.kolahal.script.ScriptOptions
import com.mtwabpin.kolahal.script.ScriptRun
import com.mtwabpin.kolahal.script.ScriptScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.roundToInt

/** An installed widget provider, for the picker. */
data class ProviderEntry(val info: AppWidgetProviderInfo, val user: UserHandle, val serial: Long) {
    val packageName: String get() = info.provider.packageName
    val flat: String get() = info.provider.flattenToShortString()
}

/**
 * Home-screen widget support:
 * - one [AppWidgetHost] for the whole app
 * - the list of placed widgets (SharedPreferences, see [WidgetCodec])
 * - helpers for sizes, providers and backups
 *
 * MainActivity calls [startListening] / [stopListening] from onStart / onStop.
 */
class WidgetHost private constructor(context: Context) {
    private val app = context.applicationContext
    val manager: AppWidgetManager = AppWidgetManager.getInstance(app)
    private val userManager = app.getSystemService(UserManager::class.java)
    private val launcherApps = app.getSystemService(LauncherApps::class.java)
    private val prefs: SharedPreferences = Stores.WIDGETS.open(app)
    private val density get() = app.resources.displayMetrics.density

    val host: AppWidgetHost = object : AppWidgetHost(app, HOST_ID) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
            KolahalWidgetView(context)

        // A provider was removed, updated, masked or unmasked. Drop widgets that no longer exist.
        override fun onProvidersChanged() {
            forgetInfos()
            checks.execute { runCatching { prune() } }
            _providersVersion.value++
        }
    }

    /**
     * What [cachedInfo] read for each id.
     * - [prune] checks it again at each start, because providers may have changed while nothing listened.
     * - It is read again when providers change, and for an id that is placed or given up.
     */
    private val infos = HashMap<Int, AppWidgetProviderInfo>()

    /** Each id's name as [label] last loaded it (loading reads the other app's resources). Kept and dropped with [infos], under its lock. */
    private val labels = HashMap<Int, String>()

    /**
     * True when the details are the same for a widget's view: the provider, its profile, its first
     * layout and its install (an update installs the app in a new folder). Before Android 12 the
     * install can't be compared, so details read again always count as new.
     */
    private fun sameAs(a: AppWidgetProviderInfo, b: AppWidgetProviderInfo) =
        Build.VERSION.SDK_INT >= 31 && a.provider == b.provider && a.profile == b.profile && a.initialLayout == b.initialLayout &&
            a.activityInfo.applicationInfo?.sourceDir == b.activityInfo.applicationInfo?.sourceDir

    /**
     * Views by id, kept for a return to home. Home is built again after a full-screen page (Settings,
     * Widgets). A kept view saves asking the widget's app for its content and inflating it again.
     * A view is used again only when it was made in the same activity, for the same details and the
     * same configuration (dark mode, density, orientation, font size and language, which the activity
     * handles itself).
     */
    private val views = HashMap<Int, Kept>()

    private class Kept(val view: AppWidgetHostView, val info: AppWidgetProviderInfo, val config: String)

    private fun configOf(context: Context) = context.resources.configuration.let {
        "${it.uiMode}:${it.densityDpi}:${it.orientation}:${it.fontScale}:${it.locales.toLanguageTags()}"
    }

    private fun forgetInfos() = synchronized(infos) { infos.clear(); labels.clear() }

    private fun forget(id: Int) {
        synchronized(infos) { infos.remove(id); labels.remove(id) }
        synchronized(views) { views.remove(id) }
    }

    /**
     * The thread where [prune] runs, on start or when providers change. It makes one call to Android
     * per widget, and the return to home shouldn't wait for them. It runs one task at a time.
     */
    private val checks = java.util.concurrent.Executors.newSingleThreadExecutor()

    private val _widgets = MutableStateFlow(
        WidgetCodec.start(prefs.getString(KEY, null)).let { start ->
            // The changes for older versions are saved at once, so they happen once ([WidgetCodec.start]).
            if (start.changed) prefs.edit { putString(KEY, WidgetCodec.encode(start.widgets)) }
            // The log is written on [checks]' thread: this may be the main thread.
            start.note?.let { note -> checks.execute { runCatching { LogRepository.get(app).add(LogKind.INFO, note) } } }
            start.widgets
        },
    )
    /** Every placed widget, including pending re-adds. */
    val widgets: StateFlow<List<PlacedWidget>> = _widgets.asStateFlow()

    private val _providersVersion = MutableStateFlow(0)
    /** Increases when installed providers change, so the picker reloads. */
    val providersVersion: StateFlow<Int> = _providersVersion.asStateFlow()

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    /**
     * Short results ("Added Clock", errors) that the Widgets screen or the picker hasn't shown yet.
     * They wait for a screen to show them, because adding from the picker switches to the Widgets
     * screen at the moment "Added" arrives.
     */
    val messages: StateFlow<List<String>> = _messages.asStateFlow()

    private val _toChoose = MutableStateFlow<String?>(null)
    /**
     * The key of the widget the Widgets screen should choose when it shows: one just placed, so it
     * can be dragged at once, or one long-pressed on the widgets page. Null when nothing is asked.
     * The screen chooses it, then calls [chosen].
     */
    val toChoose: StateFlow<String?> = _toChoose.asStateFlow()

    /** Asks the Widgets screen to choose the widget [key] when it shows ([toChoose]). */
    fun choose(key: String) {
        _toChoose.value = key
    }

    /** The Widgets screen chose the widget [toChoose] asked for. */
    fun chosen() {
        _toChoose.value = null
    }

    /** Ids allocated by a bind/configure flow that is still running. [prune] leaves them alone. */
    private val inFlight = HashSet<Int>()
    @Volatile private var listening = false

    fun message(text: String) = _messages.update { (it + text).takeLast(4) }

    /** Returns the oldest message not shown yet and removes it. The screen that shows it calls this. */
    fun takeMessage(): String? {
        var taken: String? = null
        _messages.update { taken = it.firstOrNull(); it.drop(1) }
        return taken
    }

    /** Whether home is showing: from [startListening] to [stopListening]. */
    @Volatile private var shown = false
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Home shows. The host listens only while a widget is placed: with none, there's nothing to update. */
    fun startListening() {
        shown = true
        listenIfNeeded()
        checks.execute { runCatching { prune() } }
    }

    /** Whether the host listens now. For tests. */
    internal val isListening: Boolean get() = listening

    /** Main thread. Listens if home shows and a widget is placed; the first widget added starts it. */
    private fun listenIfNeeded() {
        // Only an app's widget needs the host. A widget Kolahal draws itself gets no updates from it.
        if (listening || !shown || _widgets.value.none { it.source == null }) return
        listening = runCatching { host.startListening() }.isSuccess
    }

    fun stopListening() {
        shown = false
        if (!listening) return
        runCatching { host.stopListening() }
        listening = false
    }

    private fun save(list: List<PlacedWidget>) {
        _widgets.value = list
        prefs.edit { putString(KEY, WidgetCodec.encode(list)) }
        if (list.isNotEmpty() && !listening && shown) main.post { listenIfNeeded() }
    }

    private fun update(change: (List<PlacedWidget>) -> List<PlacedWidget>) = synchronized(this) { save(change(_widgets.value)) }

    // ---- placing --------------------------------------------------------------------------

    /**
     * Returns a new id for a flow that binds a widget. It uses the same lock as [prune]'s clean-up,
     * which runs on its own thread. So the clean-up never reads an id from the host before the flow
     * has marked it in flight.
     */
    fun allocate(): Int = synchronized(inFlight) { host.allocateAppWidgetId().also { inFlight += it } }

    /** Marks an id whose flow resumes after the process was stopped (WidgetBinder.restore), so [prune] leaves it alone again. */
    fun resume(id: Int) {
        synchronized(inFlight) { inFlight += id }
    }

    /** Gives up an id from an unfinished flow. */
    fun discard(id: Int) {
        synchronized(inFlight) { inFlight -= id }
        forget(id)
        runCatching { host.deleteAppWidgetId(id) }
    }

    /**
     * Records a bound (and configured) widget. [replacing] is the key of a pending entry that it
     * restores. That entry's size and order carry over.
     */
    fun place(id: Int, info: AppWidgetProviderInfo, replacing: String? = null) {
        forget(id)
        // Place it first, then remove it from in-flight, under the lock that [prune]'s clean-up takes.
        // So the clean-up always finds the id in one of the two, and keeps it.
        synchronized(inFlight) {
            update { all ->
                val old = replacing?.let { key -> all.firstOrNull { it.key == key } }
                val placed = PlacedWidget(
                    id = id,
                    provider = info.provider.flattenToShortString(),
                    profileSerial = serialOf(info.profile),
                    heightDp = old?.heightDp?.coerceAtLeast(minHeightDp(info)) ?: defaultHeightDp(info),
                    order = old?.order ?: WidgetOrder.nextOrder(all),
                    span = old?.span ?: WidgetLayout.COLUMNS,
                    column = old?.column ?: WidgetPlacement.UNPLACED,
                    topDp = old?.topDp ?: WidgetPlacement.UNPLACED,
                )
                val rest = all.filterNot { it.key == old?.key }
                // A restored entry keeps the place it had, and widgets in the way move down. A new one goes into the first free space.
                if (placed.positioned) WidgetPlacement.settle(rest + placed, placed.key) else WidgetPlacement.add(rest, placed)
            }
            inFlight -= id
        }
        _widgets.value.firstOrNull { it.id == id }?.let {
            applySize(id, it.heightDp, it.span)
            choose(it.key)
        }
    }

    /**
     * Removes a widget from the page. For one of a script's own widgets:
     * - The database's copies of the list follow ([follow]). The script's `widget` access goes off
     *   when this was its last widget.
     * - Everything kept for the widget is forgotten ([forgetWidget]).
     */
    fun remove(key: String) {
        val w = _widgets.value.firstOrNull { it.key == key } ?: return
        w.id?.let { forget(it); runCatching { host.deleteAppWidgetId(it) } }
        update { all -> all.filterNot { it.key == key } }
        val ref = WidgetSource.scriptRef(w.source) ?: return
        // After the copies are written: a run still on its way then finds the widget gone, and stores nothing more.
        follow(ref.script) { script -> forgetWidget(ref, script) }
    }

    /**
     * Forgets everything kept for the widget [ref], which left the page. [script] is its script,
     * or null when no script has its name.
     * - What it showed, and when.
     * - For a further widget also what was its own: the options set for it, and the error of its
     *   last quiet run ([ScriptRun.errorKey]). So the next widget that gets its id starts clean.
     * - The first widget's options are the script's own, and they stay. Its error key is the one
     *   the script's pinned line shares, and it stays too.
     */
    private fun forgetWidget(ref: WidgetRef, script: Script?) {
        WidgetStates.forget(app, ref.key)
        if (ref.first) return
        ScriptOptions(app).clear(ref.script, ref.id)
        if (script != null) KvStore(app, Stores.WORKER).set(ScriptRun.errorKey(script.id, ref.id), null)
    }

    // ---- widgets Kolahal draws itself -------------------------------------------------------

    /** The widgets of the script [script] that are on the page, in page order. */
    fun placedOf(script: String): List<WidgetRef> =
        WidgetOrder.sorted(_widgets.value).mapNotNull { WidgetSource.scriptRef(it.source) }.filter { it.script == script }

    /**
     * Places [script]'s first widget at the end of the page, at [size]: the one a timer's run fills,
     * with the script's own options. Returns false when it is already placed, when the script has
     * all the widgets it may have, or when its name can't be stored. Nothing changes then.
     * - The entry is on the page when this returns.
     * - Then, on [checks]' thread, the database's copies follow, and only after that a `"widget"`
     *   run is queued ([followAndRun]).
     */
    fun placeScript(script: Script, size: WidgetSize): Boolean = placeWidget(script, size) { "" } != null

    /**
     * Places one more widget of [script] at the end of the page, at [size]: a further widget, with
     * the smallest free id ([WidgetRef.nextId]). It follows the script's options until you set one
     * for it. Returns which widget it is, or null when the script has all the widgets it may have;
     * nothing changes then.
     *
     * What follows is as for [placeScript]: the copies, then a run for this widget only.
     */
    fun placeAnother(script: Script, size: WidgetSize): WidgetRef? = placeWidget(script, size) { taken -> WidgetRef.nextId(taken) }

    /**
     * Places one widget of [script] at the end of the page, at [size]. [id] picks which one, from
     * the ids of its widgets that are placed now. Returns the widget placed. Null, and no change,
     * when [id] gave none or one that is placed already, and when the script has
     * [WidgetRef.MAX_PER_SCRIPT] widgets: this is the one place that keeps that limit.
     */
    private fun placeWidget(script: Script, size: WidgetSize, id: (taken: Set<String>) -> String?): WidgetRef? {
        val source = placeNew(size) { all ->
            val taken = PlacedIds.of(all)[script.name].orEmpty()
            if (WidgetRef.roomFor(taken)) id(taken)?.let { WidgetSource.script(WidgetRef(script.name, it)) } else null
        }
        return WidgetSource.scriptRef(source)?.also(::followAndRun)
    }

    /**
     * Places the built-in widget [id] ([WidgetSource.BATTERY]) at the end of the page, at [size].
     * Returns false when it is already placed. Kolahal fills it from its own data, so it has no
     * script, no access item and no run.
     */
    fun placeBuiltin(id: String, size: WidgetSize): Boolean = placeNew(size) { WidgetSource.builtin(id) } != null

    /**
     * Adds an entry at the end of the page, at [size]. [pick] chooses its source from the list as
     * it is at that moment, under the list's lock, so two quick taps never pick the same one. Null
     * adds nothing. Returns the source added. Null, and no change, when [pick] gave none, or gave
     * one that is placed already or can't be stored.
     */
    private fun placeNew(size: WidgetSize, pick: (List<PlacedWidget>) -> String?): String? {
        var added: String? = null
        update { all ->
            val source = pick(all)?.takeIf { s -> WidgetSource.isSource(s) && all.none { it.source == s } } ?: return@update all
            added = source
            WidgetPlacement.add(
                all,
                PlacedWidget(
                    id = null,
                    provider = source,
                    profileSerial = runCatching { serialOf(Process.myUserHandle()) }.getOrDefault(0L),
                    heightDp = WidgetSizes.heightDp(size, pageWidthDp()).coerceIn(WidgetLayout.minDp(sourced = true), WidgetLayout.MAX_DP),
                    order = WidgetOrder.nextOrder(all),
                    span = size.span,
                ),
            )
        }
        // The Widgets screen opens next, with the new widget chosen.
        added?.let { source -> _widgets.value.firstOrNull { it.source == source }?.let { choose(it.key) } }
        return added
    }

    /**
     * Makes the database's copies for one script name say what the placed list says
     * ([ScriptRepository.setWidgetPlaced]): which widgets of that name are on the page, and with
     * that the script's `widget` access.
     * - It runs on [checks]' thread: the database is not written on the caller's thread, and the
     *   changes happen in the order they were asked for.
     * - It reads the list when it runs, not when it is asked. So after a quick remove and add, the last one wins.
     * - [then] runs after the copies are written, with the script as it is then, or null when no
     *   script has the name.
     *
     * The copies go one way: from the list to the database, never back. The script list makes the
     * change in one step of its own, so a switch you set at the same moment (`script deny … internet`)
     * is not lost.
     */
    private fun follow(script: String, then: (Script?) -> Unit = {}) = writeCopies { scripts ->
        then(scripts.setWidgetPlaced(script, PlacedIds.of(_widgets.value)[script].orEmpty()))
    }

    /**
     * What follows placing the widget [ref]: the database's copies ([follow]), and only after that
     * one `"widget"` run for this widget ([ScriptScheduler.refreshWidget]). The run reads the copies
     * from the database, so they must be there first. A widget removed again in the meantime gets no run.
     */
    private fun followAndRun(ref: WidgetRef) = follow(ref.script) { script ->
        if (script != null && ref in placedOf(ref.script)) ScriptScheduler.refreshWidget(app, script, ref.id)
    }

    /**
     * Writes the database's copies of the placed list, on [checks]' thread: [write] gets the script
     * list to write them with ([ScriptRepository.setWidgetPlaced], [ScriptRepository.followPlaced]).
     * Every such write goes through here, for one reason: [ThisInstall.check] comes first. On a
     * phone that got its database from another phone, that check clears the copies
     * ([Stores.THIS_PHONE]). It runs once, and elsewhere it starts later than this
     * ([com.mtwabpin.kolahal.apps.PackageEvents], [com.mtwabpin.kolahal.calls.CallEvents]). Without
     * it here, it could clear the rows written a moment before.
     */
    private fun writeCopies(write: (ScriptRepository) -> Unit) = checks.execute {
        runCatching {
            ThisInstall.check(app)
            write(ScriptRepository.get(app))
        }
    }

    /**
     * Makes the database follow the whole placed list: each name's row says which of its widgets
     * are on the page, and every script's `widget` access is on when one of its widgets is placed,
     * off otherwise. The placed list wins, because it is the one the user changes.
     * - It runs at start and after a backup is imported. A backup carries only the access you switched
     *   off, so without this a restored widget would stay empty.
     * - It writes only where the two differ, on [checks]' thread.
     */
    fun syncAccess() = writeCopies { it.followPlaced(PlacedIds.of(_widgets.value)) }

    /**
     * Waits until the changes to the database's copies asked for so far are written ([placeScript],
     * [placeAnother], [remove], [renameScript], [syncAccess]). For tests: a caller on the main thread
     * must not wait for the database.
     */
    @androidx.annotation.VisibleForTesting
    internal fun settle() {
        runCatching { checks.submit {}.get(10, java.util.concurrent.TimeUnit.SECONDS) }
    }

    /**
     * A script was renamed.
     * - Every one of its placed widgets keeps its place, size, order and id under the new name
     *   ([WidgetSource.renamed]).
     * - A widget may wait under the new name already: one a deleted script left on the page, or one
     *   from a backup. It is this script's widget now.
     * Either way the database's copies follow the list, for both names ([follow]).
     */
    fun renameScript(from: String, to: String) {
        val moved = placedOf(from).isNotEmpty()
        if (moved) {
            update { WidgetSource.renamed(it, from, to) }
            follow(from)
        }
        if (moved || placedOf(to).isNotEmpty()) follow(to)
    }

    /** Moves the widget [key] to [column] and [topDp] on the page. Widgets in the way move down ([WidgetPlacement.moveTo]). */
    fun moveTo(key: String, column: Int, topDp: Int) = update { WidgetPlacement.moveTo(it, key, column, topDp) }

    /**
     * Moves the widget [key] by [columns] sideways and [rows] up or down, a row being one cell of the
     * page: a screen reader's Move up, Move down, Move left and Move right.
     */
    fun nudge(key: String, columns: Int, rows: Int) = update { WidgetPlacement.nudge(it, key, columns, rows, rowDp()) }

    /** Moves every widget up as far as it goes: no space left between widgets ([WidgetPlacement.closeGaps]). */
    fun closeGaps() = update { WidgetPlacement.closeGaps(it) }

    /** Sets how Kolahal draws the widget [key]. An app's widget draws itself, so it keeps [WidgetLook.PLAIN]. */
    fun setLook(key: String, look: WidgetLook) = update { all ->
        all.map { if (it.key == key && it.source != null && it.look != look) it.copy(look = look) else it }
    }

    /** Gives the widget [key] one of the sizes a script widget starts at: 2×2 and so on. Widgets in the way move down. */
    fun setSize(key: String, size: WidgetSize) {
        val w = _widgets.value.firstOrNull { it.key == key } ?: return
        val span = size.span.coerceAtLeast(WidgetLayout.minSpan(w.source != null))
        val height = WidgetLayout.clampHeight(WidgetSizes.heightDp(size, pageWidthDp()), minHeightOf(w))
        update { WidgetPlacement.resize(it, key, span, height) }
    }

    /**
     * The size in [WidgetSize] that the widget [key] has now, if it is one of them on this page.
     * Null for any other size.
     */
    fun sizeOf(w: PlacedWidget): WidgetSize? {
        val page = pageWidthDp()
        return WidgetSize.entries.firstOrNull { it.span == w.span && WidgetSizes.heightDp(it, page) == w.heightDp }
    }

    /** One row of the page: as tall as one cell is wide ([WidgetSizes.heightDp] of a 1×1). */
    fun rowDp(): Int = WidgetSizes.heightDp(WidgetSize.S1X1, pageWidthDp())

    fun resize(key: String, deltaDp: Int) {
        val w = _widgets.value.firstOrNull { it.key == key } ?: return
        setHeight(w, w.heightDp + deltaDp)
    }

    /** Makes the widget wider or narrower by [delta] columns of the page. */
    fun resizeWidth(key: String, delta: Int) {
        val w = _widgets.value.firstOrNull { it.key == key } ?: return
        // A widget Kolahal draws itself is at least one cell wide.
        val span = WidgetLayout.resizeSpan(w.span, delta).coerceAtLeast(WidgetLayout.minSpan(w.source != null))
        if (span != w.span) update { WidgetPlacement.resize(it, key, span, w.heightDp) }
    }

    /**
     * Sets the widget [key]'s width and height at once, as a drag on its handles does: [span] in
     * columns, [heightDp] in dp. Both are kept within the widget's limits. Widgets in the way move down.
     */
    fun resizeTo(key: String, span: Int, heightDp: Int) {
        val w = _widgets.value.firstOrNull { it.key == key } ?: return
        val s = span.coerceIn(WidgetLayout.minSpan(w.source != null), WidgetLayout.COLUMNS)
        val h = WidgetLayout.clampHeight(heightDp, minHeightOf(w))
        if (s != w.span || h != w.heightDp) update { WidgetPlacement.resize(it, key, s, h) }
    }

    /** Makes the widget as tall as it is wide on the widgets page, or as close as the widget allows. */
    fun square(key: String) {
        val w = _widgets.value.firstOrNull { it.key == key } ?: return
        setHeight(w, WidgetLayout.widthDp(pageWidthDp(), w.span))
    }

    /**
     * The lowest [w] may be, in dp. An app's widget has its own minimum when its details are known.
     * A widget Kolahal draws itself has no id, so it gets [WidgetLayout.minDp].
     */
    fun minHeightOf(w: PlacedWidget): Int =
        w.id?.let { cachedInfo(it) }?.let { minHeightDp(it) } ?: WidgetLayout.minDp(w.source != null)

    private fun setHeight(w: PlacedWidget, heightDp: Int) {
        val h = WidgetLayout.clampHeight(heightDp, minHeightOf(w))
        if (h != w.heightDp) update { WidgetPlacement.resize(it, w.key, w.span, h) }
    }

    // ---- providers ------------------------------------------------------------------------

    fun info(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    /**
     * A placed widget's name: its provider's label, or the last part of the provider's name. The
     * first time, it reads the other app's resources: call it off the main thread. [loadedLabel] is
     * for a screen's first frame.
     */
    fun label(id: Int?, provider: String): String {
        id ?: return provider.substringAfterLast('.')
        synchronized(infos) { labels[id] }?.let { return it }
        val label = cachedInfo(id)?.loadLabel(app.packageManager)?.toString() ?: return provider.substringAfterLast('.')
        synchronized(infos) { labels[id] = label }
        return label
    }

    /** [label] if it was loaded already, without loading anything: right for the main thread. */
    fun loadedLabel(id: Int?, provider: String): String =
        id?.let { synchronized(infos) { labels[it] } } ?: provider.substringAfterLast('.')

    /** [info] as last read for [id], checked at each start. Screens ask for it while they are built. */
    fun cachedInfo(id: Int): AppWidgetProviderInfo? = synchronized(infos) {
        // Don't keep a missing one. Ask again next time, because an app being updated comes back.
        infos[id] ?: info(id)?.also { infos[id] = it }
    }

    /**
     * Returns the view for widget [id] in [context] (an activity). It is the view kept from the last time
     * home or the Widgets page showed it, or a new one from the host, which then gets the widget's updates.
     */
    fun view(context: Context, id: Int, info: AppWidgetProviderInfo): AppWidgetHostView = synchronized(views) {
        val config = configOf(context)
        views[id]?.let { kept ->
            if (kept.view.context === context && kept.info === info && kept.config == config) {
                // It is still inside the holder of the last screen that showed it, and that screen is gone.
                (kept.view.parent as? android.view.ViewGroup)?.removeView(kept.view)
                return kept.view
            }
        }
        host.createView(context, id, info).also { views[id] = Kept(it, info, config) }
    }

    /** Releases the views made in [context], an activity that is being destroyed. */
    fun releaseViews(context: Context) = synchronized(views) {
        views.values.removeAll { it.view.context === context }
    }

    fun serialOf(user: UserHandle): Long = userManager.getSerialNumberForUser(user)

    fun userFor(serial: Long): UserHandle? = userManager.getUserForSerialNumber(serial)

    /** True when [serial]'s profile is paused (work apps off) or locked (private space). */
    fun isPaused(serial: Long): Boolean {
        val user = userFor(serial) ?: return false
        return user != Process.myUserHandle() && runCatching { userManager.isQuietModeEnabled(user) || !userManager.isUserUnlocked(user) }.getOrDefault(false)
    }

    /** Home-screen providers in every profile the launcher can see. */
    fun providers(): List<ProviderEntry> = launcherApps.profiles.flatMap { user ->
        val serial = serialOf(user)
        runCatching { manager.getInstalledProvidersForProfile(user) }.getOrDefault(emptyList())
            .filter { it.widgetCategory == 0 || it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
            .map { ProviderEntry(it, user, serial) }
    }

    /** The provider for a backup entry: in the same profile if it still exists, else the personal one, else any. */
    fun findProvider(provider: String, serial: Long): ProviderEntry? {
        val component = ComponentName.unflattenFromString(provider) ?: return null
        val matches = providers().filter { it.info.provider == component }
        return matches.firstOrNull { it.serial == serial }
            ?: matches.firstOrNull { it.user == Process.myUserHandle() }
            ?: matches.firstOrNull()
    }

    fun minHeightDp(info: AppWidgetProviderInfo): Int = WidgetLayout.minHeight(
        minHeightDp = (info.minHeight / density).roundToInt(),
        minResizeHeightDp = (info.minResizeHeight / density).roundToInt(),
        resizableVertically = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
    )

    fun defaultHeightDp(info: AppWidgetProviderInfo): Int = WidgetLayout.defaultHeight(
        (info.minHeight / density).roundToInt(),
        if (Build.VERSION.SDK_INT >= 31) info.targetCellHeight else 0,
    )

    fun sizeLabel(info: AppWidgetProviderInfo): String = WidgetLayout.sizeLabel(
        (info.minWidth / density).roundToInt(), (info.minHeight / density).roundToInt(),
        if (Build.VERSION.SDK_INT >= 31) info.targetCellWidth else 0,
        if (Build.VERSION.SDK_INT >= 31) info.targetCellHeight else 0,
    )

    /** Size options passed to bind, so the provider lays out for the place where it will be shown. */
    fun options(heightDp: Int, span: Int = WidgetLayout.COLUMNS): Bundle {
        val widthDp = WidgetLayout.widthDp(pageWidthDp(), span)
        return Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
            if (Build.VERSION.SDK_INT >= 31) {
                putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
            }
        }
    }

    /** The widgets page's width inside its margins, as last laid out. 0 if it was never laid out. */
    @Volatile var measuredPageDp: Int = 0

    /** The widgets page's width inside its margins: the measured value, else computed from the screen. */
    private fun pageWidthDp(): Int =
        measuredPageDp.takeIf { it > 0 } ?: (app.resources.configuration.screenWidthDp - 2 * WidgetLayout.GAP_DP).coerceAtLeast(120)

    /** Tells the provider the size before a view exists. The view reports its real size after layout. */
    private fun applySize(id: Int, heightDp: Int, span: Int) {
        runCatching { manager.updateAppWidgetOptions(id, options(heightDp, span)) }
    }

    // ---- housekeeping ---------------------------------------------------------------------

    /**
     * Cleans up widgets and ids:
     * - Drops widgets whose provider is gone (uninstalled, or its profile removed).
     * - Frees host ids that nothing uses any more (left over from a flow stopped by process death).
     * - Keeps widgets of a paused or locked profile.
     *
     * The details it reads are saved for [cachedInfo], unless they are the same as the saved ones.
     * In that case, a view made for the saved details is used again.
     */
    fun prune() {
        val dead = _widgets.value.filter { w ->
            val id = w.id ?: return@filter false
            val fresh = info(id)
            synchronized(infos) {
                val old = infos[id]
                if (fresh == null) { infos.remove(id); labels.remove(id) } else if (old == null || !sameAs(old, fresh)) { infos[id] = fresh; labels.remove(id) }
            }
            if (fresh != null) return@filter false
            val user = userFor(w.profileSerial) ?: return@filter true
            val available = runCatching {
                userManager.isUserUnlocked(user) && (user == Process.myUserHandle() || !userManager.isQuietModeEnabled(user))
            }.getOrDefault(false)
            available
        }
        if (dead.isNotEmpty()) {
            dead.forEach { w -> w.id?.let { forget(it); runCatching { host.deleteAppWidgetId(it) } } }
            // If its app still offers it, only the id was lost (Android's own backup restored the list
            // on a new phone, where ids differ). It waits on the Widgets page, and one tap adds it again.
            val offered = runCatching { providers().map { it.flat to it.serial } }.getOrDefault(emptyList()).toSet()
            val (lost, gone) = dead.partition { (it.provider to it.profileSerial) in offered }
            val goneKeys = gone.map { it.key }.toSet()
            val lostKeys = lost.map { it.key }.toSet()
            update { all -> all.filterNot { it.key in goneKeys }.map { if (it.key in lostKeys) it.copy(id = null) else it } }
            // Report it in the log. This runs when Kolahal starts (after a restore on a new phone), on
            // home, where no widget screen would show it.
            val logs = LogRepository.get(app)
            if (gone.isNotEmpty()) logs.add(LogKind.INFO, "Removed ${gone.size} widget${if (gone.size == 1) "" else "s"} whose app was uninstalled")
            if (lost.isNotEmpty()) {
                logs.add(
                    LogKind.INFO,
                    "${lost.size} widget${if (lost.size == 1) " needs" else "s need"} adding again — tap to open Settings → Widgets → Arrange widgets",
                    ctx = LogContext.of(LogContext.RUN to "widgets"),
                )
            }
        }
        // Free ids that nothing uses. Read them and the used ids under one lock, because [allocate]
        // and [place] change them.
        synchronized(inFlight) {
            val used = _widgets.value.mapNotNull { it.id }.toSet() + inFlight
            runCatching { host.appWidgetIds }.getOrNull()?.filter { it !in used }?.forEach { runCatching { host.deleteAppWidgetId(it) } }
        }
    }

    // ---- backup ---------------------------------------------------------------------------

    /** For Backup: the provider, profile serial, size and order of every placed widget. */
    fun exportWidgets(): List<Map<String, Any?>> = WidgetCodec.export(_widgets.value)

    /**
     * For Backup: widget ids can't be restored. So imported entries become "pending re-add" rows on
     * the Widgets page, each with a one-tap "Add again". A widget Kolahal draws itself needs no id:
     * it is placed as it comes, and the scripts' `widget` access follows ([syncAccess]).
     */
    fun importWidgets(list: List<Map<String, Any?>>) {
        // Imported widgets without a place, or whose place is taken, go into the first free space.
        update { all -> WidgetPlacement.placeImported(all, WidgetCodec.mergePending(all, WidgetOrder.onePage(WidgetCodec.fromExport(list)))) }
        syncAccess()
    }

    companion object : Singleton<WidgetHost>(::WidgetHost) {
        /** "KO" in ASCII. */
        const val HOST_ID = 0x4B4F
        private const val KEY = "placed"
    }
}

/** `widgets ls`: one (text, detail) pair per widget, in page order, with pending re-adds last. */
fun WidgetHost.listing(context: Context): List<Pair<String, String>> {
    val all = widgets.value
    val ordered = WidgetOrder.inLocation(all, WidgetLocation.PANEL) + WidgetOrder.sorted(all.filter { it.pending })
    return ordered.mapIndexed { i, w ->
        val source = w.source
        // A widget Kolahal draws itself: its name ("weather", "Battery and network"), and what it is ("script") where an app's widget has its package.
        // A script's further widget has its id after the name: "weather 2".
        val label = source?.let(WidgetSource::label) ?: label(w.id, w.provider)
        val state = when {
            w.pending -> "Pending re-add"
            source == null && isPaused(w.profileSerial) -> "Paused"
            else -> null
        }
        "${i + 1}. $label" to listOfNotNull(state, "${WidgetLayout.spanLabel(w.span)} × ${w.heightDp}dp", source?.substringBefore(':') ?: w.packageName).joinToString(" · ")
    }
}

/**
 * Host view that reports its real size to the provider. A long-press anywhere on the widget calls
 * [onLongPress] and cancels the touch for the widget's own views.
 */
class KolahalWidgetView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var longPressed = false
    private var cancelSent = false
    private var lastSize = 0L

    private val check = Runnable {
        if (onLongPress != null) {
            longPressed = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPress?.invoke()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressed = false
                cancelSent = false
                downX = ev.x
                downY = ev.y
                if (onLongPress != null) postDelayed(check, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) removeCallbacks(check)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(check)
        }
        if (longPressed) {
            if (!cancelSent) {
                cancelSent = true
                val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
            }
            return true
        }
        val handled = super.dispatchTouchEvent(ev)
        // Keep receiving the gesture even over parts of the widget that don't take touches.
        return handled || (onLongPress != null && ev.actionMasked == MotionEvent.ACTION_DOWN)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0 || appWidgetId == 0) return
        val packed = (w.toLong() shl 32) or h.toLong()
        if (packed == lastSize) return
        lastSize = packed
        val d = resources.displayMetrics.density
        val wDp = w / d
        val hDp = h / d
        removeCallbacks(report)
        pending = wDp to hDp
        postDelayed(report, SETTLE_MS)
    }

    /** A settled size not reported yet. A view kept for later ([WidgetHost.view]) reports it when it shows again. */
    private var pending: Pair<Float, Float>? = null
    private val report = Runnable { pending?.let { (w, h) -> pending = null; reportSize(w, h) } }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (pending != null) postDelayed(report, SETTLE_MS)
    }

    private companion object {
        /** Report a size only after it stays the same this long, because holding − or + changes it every few frames. */
        const val SETTLE_MS = 150L
    }

    private fun reportSize(wDp: Float, hDp: Float) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                updateAppWidgetSize(Bundle(), listOf(SizeF(wDp, hDp)))
            } else {
                @Suppress("DEPRECATION")
                updateAppWidgetSize(null, wDp.toInt(), hDp.toInt(), wDp.toInt(), hDp.toInt())
            }
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(check)
        removeCallbacks(report)
        super.onDetachedFromWindow()
    }
}
