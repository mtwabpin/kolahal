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
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.Singleton
import com.mtwabpin.kolahal.script.ScriptAccess
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

    private fun forgetInfos() = synchronized(infos) { infos.clear() }

    private fun forget(id: Int) {
        synchronized(infos) { infos.remove(id) }
        synchronized(views) { views.remove(id) }
    }

    private val _widgets = MutableStateFlow(
        WidgetCodec.decode(prefs.getString(KEY, null)).let { saved ->
            // Widgets placed on home before 0.3.1 move onto the page, once.
            WidgetOrder.onePage(saved).also { if (it != saved) prefs.edit { putString(KEY, WidgetCodec.encode(it)) } }
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

    /**
     * The thread where [prune] runs, on start or when providers change. It makes one call to Android
     * per widget, and the return to home shouldn't wait for them. It runs one task at a time.
     */
    private val checks = java.util.concurrent.Executors.newSingleThreadExecutor()

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
                )
                all.filterNot { it.key == old?.key } + placed
            }
            inFlight -= id
        }
        _widgets.value.firstOrNull { it.id == id }?.let { applySize(id, it.heightDp, it.span) }
    }

    /**
     * Removes a widget from the page. For a script's own widget, its `widget` access goes off and what
     * it showed is forgotten ([follow]).
     */
    fun remove(key: String) {
        val w = _widgets.value.firstOrNull { it.key == key } ?: return
        w.id?.let { forget(it); runCatching { host.deleteAppWidgetId(it) } }
        update { all -> all.filterNot { it.key == key } }
        WidgetSource.scriptName(w.source)?.let { follow(it) }
    }

    // ---- widgets Kolahal draws itself -------------------------------------------------------

    /** Whether the widget with this [source] is on the page. A widget with a source is placed once. */
    fun isPlaced(source: String): Boolean = _widgets.value.any { it.source == source }

    /**
     * Places [script]'s own widget at the end of the page, at [size]. Returns false when it is already
     * placed (or its name can't be stored), and changes nothing then.
     * - The entry is on the page when this returns.
     * - Then, on [checks]' thread, the script's `widget` access goes on, and only after that a
     *   `"widget"` run is queued ([ScriptScheduler.refreshWidget]): the run reads the access from the database.
     */
    fun placeScript(script: Script, size: WidgetSize): Boolean {
        val source = WidgetSource.script(script.name)
        if (!WidgetSource.isSource(source)) return false
        var added = false
        update { all ->
            added = all.none { it.source == source }
            if (!added) return@update all
            all + PlacedWidget(
                id = null,
                provider = source,
                profileSerial = runCatching { serialOf(Process.myUserHandle()) }.getOrDefault(0L),
                heightDp = WidgetSizes.heightDp(size, pageWidthDp()).coerceIn(WidgetSizes.MIN_HEIGHT_DP, WidgetLayout.MAX_DP),
                order = WidgetOrder.nextOrder(all),
                span = size.span,
            )
        }
        if (added) follow(script.name, run = true)
        return added
    }

    /**
     * Makes one script's `widget` access say what the placed list says, on [checks]' thread: the
     * database is not written on the caller's thread, and the changes happen in the order they were asked for.
     * - Placed: the access goes on. With [run], a `"widget"` run is queued after that.
     * - Not placed: the access goes off, and what the widget showed is forgotten.
     * It reads the list when it runs, not when it is asked. So after a quick remove and add, the last one wins.
     */
    private fun follow(name: String, run: Boolean = false) = checks.execute {
        runCatching {
            val scripts = ScriptRepository.get(app)
            val script = scripts.byName(name)
            val placed = isPlaced(WidgetSource.script(name))
            if (script != null && (script.access[ScriptAccess.WIDGET] == true) != placed) scripts.setAccess(script.id, ScriptAccess.WIDGET, placed)
            if (!placed) WidgetStates.forget(app, name)
            else if (run && script != null) ScriptScheduler.refreshWidget(app, script)
        }
    }

    /**
     * Makes every script's `widget` access follow the placed list: on for each placed `script:<name>`
     * entry, off for every other script. The placed list wins, because it is the one the user changes.
     * - It runs at start and after a backup is imported. A backup carries only the access you switched
     *   off, so without this a restored widget would stay empty.
     * - It writes only where the two differ, on [checks]' thread.
     */
    fun syncAccess() = checks.execute {
        runCatching {
            val scripts = ScriptRepository.get(app)
            val placed = _widgets.value.mapNotNull { WidgetSource.scriptName(it.source) }.toSet()
            scripts.all().forEach { s ->
                val on = s.name in placed
                if ((s.access[ScriptAccess.WIDGET] == true) != on) scripts.setAccess(s.id, ScriptAccess.WIDGET, on)
            }
        }
    }

    /**
     * Takes the script [name]'s widgets off the page (its own and its Status card), because the script
     * was deleted. So a new script that takes the name doesn't start out placed.
     */
    fun removeScript(name: String) {
        if (WidgetSource.ofScript(name).none(::isPlaced)) return
        update { WidgetSource.withoutScript(it, name) }
        follow(name)
    }

    /** A script was renamed: its placed widgets keep their place, size and order under the new name ([WidgetSource.renamed]). */
    fun renameScript(from: String, to: String) {
        if (WidgetSource.ofScript(from).none(::isPlaced)) return
        update { WidgetSource.renamed(it, from, to) }
        follow(from)
        follow(to)
    }

    fun move(key: String, direction: Int) = update { WidgetOrder.move(it, key, direction) }


    fun resize(key: String, deltaDp: Int) {
        val w = _widgets.value.firstOrNull { it.key == key } ?: return
        setHeight(w, w.heightDp + deltaDp)
    }

    /** Makes the widget wider or narrower by [delta] columns of the page. */
    fun resizeWidth(key: String, delta: Int) {
        val w = _widgets.value.firstOrNull { it.key == key } ?: return
        // A widget Kolahal draws itself is at least one cell wide.
        val span = WidgetLayout.resizeSpan(w.span, delta).coerceAtLeast(if (w.source != null) WidgetSizes.MIN_SPAN else WidgetLayout.MIN_SPAN)
        if (span != w.span) update { all -> all.map { if (it.key == key) it.copy(span = span) else it } }
    }

    /** Makes the widget as tall as it is wide on the widgets page, or as close as the widget allows. */
    fun square(key: String) {
        val w = _widgets.value.firstOrNull { it.key == key } ?: return
        setHeight(w, WidgetLayout.widthDp(pageWidthDp(), w.span))
    }

    private fun setHeight(w: PlacedWidget, heightDp: Int) {
        val min = if (w.source != null) WidgetSizes.MIN_HEIGHT_DP else w.id?.let { cachedInfo(it) }?.let { minHeightDp(it) } ?: WidgetLayout.MIN_DP
        val h = WidgetLayout.resize(heightDp, 0, min)
        if (h != w.heightDp) update { all -> all.map { if (it.key == w.key) it.copy(heightDp = h) else it } }
    }

    // ---- providers ------------------------------------------------------------------------

    fun info(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

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
                if (fresh == null) infos.remove(id) else if (old == null || !sameAs(old, fresh)) infos[id] = fresh
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
        update { WidgetCodec.mergePending(it, WidgetOrder.onePage(WidgetCodec.fromExport(list))) }
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
        // A widget Kolahal draws itself: its name ("weather"), and what it is ("script") where an app's widget has its package.
        val label = source?.substringAfter(':') ?: w.id?.let { info(it) }?.loadLabel(context.packageManager) ?: w.provider.substringAfterLast('.')
        val state = when {
            w.pending -> "pending re-add"
            source == null && isPaused(w.profileSerial) -> "paused"
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
