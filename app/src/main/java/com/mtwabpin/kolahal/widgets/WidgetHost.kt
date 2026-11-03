package com.mtwabpin.kolahal.widgets

import android.annotation.SuppressLint
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
 * Home-screen widget support: one [AppWidgetHost] for the whole app, the list of placed widgets
 * (SharedPreferences, see [WidgetCodec]) and helpers for sizes, providers and backups.
 * MainActivity calls [startListening] / [stopListening] from onStart / onStop.
 */
class WidgetHost private constructor(context: Context) {
    private val app = context.applicationContext
    val manager: AppWidgetManager = AppWidgetManager.getInstance(app)
    private val userManager = app.getSystemService(UserManager::class.java)
    private val launcherApps = app.getSystemService(LauncherApps::class.java)
    private val prefs: SharedPreferences = app.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    private val density get() = app.resources.displayMetrics.density

    val host: AppWidgetHost = object : AppWidgetHost(app, HOST_ID) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
            KolahalWidgetView(context)

        // A provider was removed, updated or (un)masked: drop widgets that no longer exist.
        override fun onProvidersChanged() {
            forgetInfos()
            checks.execute { runCatching { prune() } }
            _providersVersion.value++
        }
    }

    /**
     * What [cachedInfo] read for each id. Checked again at each start by [prune] (providers may have
     * changed while nothing listened), read again when providers change and for an id placed or let go.
     */
    private val infos = HashMap<Int, AppWidgetProviderInfo>()

    /**
     * The same details as far as a widget's view goes: the provider, its profile, its first layout
     * and the install it comes from (an update installs the app in a new folder). Before Android 12
     * the install can't be told, and details read again count as new.
     */
    private fun sameAs(a: AppWidgetProviderInfo, b: AppWidgetProviderInfo) =
        Build.VERSION.SDK_INT >= 31 && a.provider == b.provider && a.profile == b.profile && a.initialLayout == b.initialLayout &&
            a.activityInfo?.applicationInfo?.sourceDir == b.activityInfo?.applicationInfo?.sourceDir

    /**
     * Views by id, kept for a return to home: home is built again after a full-screen page (Settings,
     * Widgets), and a kept view saves asking the widget's app for its content and inflating it again.
     * Only a view made in the same activity, for the same details and the same configuration (dark
     * mode, density, orientation, font size and language, which the activity handles itself), is used again.
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
    /** Every placed widget, pending re-adds included. */
    val widgets: StateFlow<List<PlacedWidget>> = _widgets.asStateFlow()

    private val _providersVersion = MutableStateFlow(0)
    /** Bumps when installed providers change, so the picker reloads. */
    val providersVersion: StateFlow<Int> = _providersVersion.asStateFlow()

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    /**
     * Short results ("Added Clock", errors) the Widgets screen or the picker hasn't shown yet. They
     * wait for one: adding from the picker moves to the Widgets screen just as "Added" comes.
     */
    val messages: StateFlow<List<String>> = _messages.asStateFlow()

    /** Ids allocated by a bind/configure flow that is still running; [prune] leaves them alone. */
    private val inFlight = HashSet<Int>()
    private var listening = false

    fun message(text: String) = _messages.update { (it + text).takeLast(4) }

    /** The oldest message not shown yet, taken off the list by the screen that shows it. */
    fun takeMessage(): String? {
        var taken: String? = null
        _messages.update { taken = it.firstOrNull(); it.drop(1) }
        return taken
    }

    /**
     * Where [prune] runs for a start or a change of providers: it asks Android about each widget, one
     * call each, and home's return shouldn't wait for them. One at a time.
     */
    private val checks = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun startListening() {
        if (listening) return
        listening = runCatching { host.startListening() }.isSuccess
        checks.execute { runCatching { prune() } }
    }

    fun stopListening() {
        if (!listening) return
        runCatching { host.stopListening() }
        listening = false
    }

    private fun save(list: List<PlacedWidget>) {
        _widgets.value = list
        prefs.edit { putString(KEY, WidgetCodec.encode(list)) }
    }

    private fun update(change: (List<PlacedWidget>) -> List<PlacedWidget>) = synchronized(this) { save(change(_widgets.value)) }

    // ---- placing --------------------------------------------------------------------------

    fun allocate(): Int = host.allocateAppWidgetId().also { synchronized(inFlight) { inFlight += it } }

    /** An id whose flow resumes after the process was stopped (WidgetBinder.restore): [prune] leaves it alone again. */
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
     * Records a bound (and configured) widget. [replacing] is the key of a pending entry it
     * restores: that entry's size and order carry over.
     */
    fun place(id: Int, info: AppWidgetProviderInfo, replacing: String? = null) {
        synchronized(inFlight) { inFlight -= id }
        forget(id)
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
        _widgets.value.firstOrNull { it.id == id }?.let { applySize(id, it.heightDp, it.span) }
    }

    fun remove(key: String) {
        val w = _widgets.value.firstOrNull { it.key == key } ?: return
        w.id?.let { forget(it); runCatching { host.deleteAppWidgetId(it) } }
        update { all -> all.filterNot { it.key == key } }
    }

    fun move(key: String, direction: Int) = update { WidgetOrder.move(it, key, direction) }


    fun resize(key: String, deltaDp: Int) {
        val w = _widgets.value.firstOrNull { it.key == key } ?: return
        setHeight(w, w.heightDp + deltaDp)
    }

    /** Wider or narrower by [delta] columns of the page. */
    fun resizeWidth(key: String, delta: Int) {
        val w = _widgets.value.firstOrNull { it.key == key } ?: return
        val span = WidgetLayout.resizeSpan(w.span, delta)
        if (span != w.span) update { all -> all.map { if (it.key == key) it.copy(span = span) else it } }
    }

    /** As tall as it is wide on the widgets page (or as close as the widget allows). */
    fun square(key: String) {
        val w = _widgets.value.firstOrNull { it.key == key } ?: return
        setHeight(w, WidgetLayout.widthDp(pageWidthDp(), w.span))
    }

    private fun setHeight(w: PlacedWidget, heightDp: Int) {
        val min = w.id?.let { cachedInfo(it) }?.let { minHeightDp(it) } ?: WidgetLayout.MIN_DP
        val h = WidgetLayout.resize(heightDp, 0, min)
        if (h != w.heightDp) update { all -> all.map { if (it.key == w.key) it.copy(heightDp = h) else it } }
    }

    // ---- providers ------------------------------------------------------------------------

    fun info(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    /** [info] as last read for [id], checked at each start: screens ask while they're built. */
    fun cachedInfo(id: Int): AppWidgetProviderInfo? = synchronized(infos) {
        // A missing one isn't kept: it's asked for again, as an app being updated comes back.
        infos[id] ?: info(id)?.also { infos[id] = it }
    }

    /**
     * The view for widget [id] in [context] (an activity): the one kept from the last time home or the
     * Widgets page showed it, or a new one from the host, which then gets the widget's updates.
     */
    fun view(context: Context, id: Int, info: AppWidgetProviderInfo): AppWidgetHostView = synchronized(views) {
        val config = configOf(context)
        views[id]?.let { kept ->
            if (kept.view.context === context && kept.info === info && kept.config == config) {
                // Still inside the holder of the screen that showed it last, which is gone.
                (kept.view.parent as? android.view.ViewGroup)?.removeView(kept.view)
                return kept.view
            }
        }
        host.createView(context, id, info).also { views[id] = Kept(it, info, config) }
    }

    /** Lets go of the views made in [context], an activity being destroyed. */
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

    /** The provider for a backup entry: same profile if it still exists, else the personal one, else any. */
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

    /** Size options handed to bind, so the provider lays out for the place it will live in. */
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

    /** The widgets page's width inside its margins, as last laid out (0 before it ever was). */
    @Volatile var measuredPageDp: Int = 0

    /** The widgets page's width inside its margins: as measured, else worked out from the screen. */
    private fun pageWidthDp(): Int =
        measuredPageDp.takeIf { it > 0 } ?: (app.resources.configuration.screenWidthDp - 2 * WidgetLayout.GAP_DP).coerceAtLeast(120)

    /** Tells the provider the size before a view exists (the view reports its real size once laid out). */
    private fun applySize(id: Int, heightDp: Int, span: Int) {
        runCatching { manager.updateAppWidgetOptions(id, options(heightDp, span)) }
    }

    // ---- housekeeping ---------------------------------------------------------------------

    /**
     * Drops widgets whose provider is gone (uninstalled, or its profile removed) and frees host
     * ids nothing uses any more (left over from a flow interrupted by process death).
     * Widgets of a paused or locked profile are kept. The details it reads are kept for [cachedInfo],
     * unless they're the same as the kept ones: a view made for those is used again.
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
            // Its app still offers it: the id was lost (Android's own backup restored the list on a
            // new phone, where ids differ). It waits on the Widgets page, one tap to add again.
            val offered = runCatching { providers().map { it.flat to it.serial } }.getOrDefault(emptyList()).toSet()
            val (lost, gone) = dead.partition { (it.provider to it.profileSerial) in offered }
            val goneKeys = gone.map { it.key }.toSet()
            val lostKeys = lost.map { it.key }.toSet()
            update { all -> all.filterNot { it.key in goneKeys }.map { if (it.key in lostKeys) it.copy(id = null) else it } }
            // In the log: this runs as Kolahal starts (after a restore on a new phone), on home, where no widget screen would show it.
            val logs = LogRepository.get(app)
            if (gone.isNotEmpty()) logs.add(LogKind.INFO, "Removed ${gone.size} widget${if (gone.size == 1) "" else "s"} whose app was removed")
            if (lost.isNotEmpty()) {
                logs.add(
                    LogKind.INFO,
                    "${lost.size} widget${if (lost.size == 1) " needs" else "s need"} adding again — tap to open Settings → Widgets → Arrange widgets",
                    ctx = LogContext.of(LogContext.RUN to "widgets"),
                )
            }
        }
        val used = _widgets.value.mapNotNull { it.id }.toSet() + synchronized(inFlight) { inFlight.toSet() }
        runCatching { host.appWidgetIds }.getOrNull()?.filter { it !in used }?.forEach { runCatching { host.deleteAppWidgetId(it) } }
    }

    // ---- backup ---------------------------------------------------------------------------

    /** For Backup: provider, profile serial, size and order of every placed widget. */
    fun exportWidgets(): List<Map<String, Any?>> = WidgetCodec.export(_widgets.value)

    /**
     * For Backup: widget ids can't be restored, so imported entries become "pending re-add"
     * rows on the Widgets page, each with a one-tap "Add again".
     */
    fun importWidgets(list: List<Map<String, Any?>>) = update { WidgetCodec.mergePending(it, WidgetOrder.onePage(WidgetCodec.fromExport(list))) }

    companion object {
        /** "KO". */
        const val HOST_ID = 0x4B4F
        private const val KEY = "placed"

        @SuppressLint("StaticFieldLeak") // application context only
        @Volatile private var instance: WidgetHost? = null

        fun get(context: Context): WidgetHost = instance ?: synchronized(this) {
            instance ?: WidgetHost(context).also { instance = it }
        }
    }
}

/** `widgets ls`: one (text, detail) pair per widget, in page order, pending re-adds last. */
fun WidgetHost.listing(context: Context): List<Pair<String, String>> {
    val all = widgets.value
    val ordered = WidgetOrder.inLocation(all, WidgetLocation.PANEL) + WidgetOrder.sorted(all.filter { it.pending })
    return ordered.mapIndexed { i, w ->
        val label = w.id?.let { info(it) }?.loadLabel(context.packageManager) ?: w.provider.substringAfterLast('.')
        val state = when {
            w.pending -> "pending re-add"
            isPaused(w.profileSerial) -> "paused"
            else -> null
        }
        "${i + 1}. $label" to listOfNotNull(state, "${WidgetLayout.spanLabel(w.span)} × ${w.heightDp}dp", w.packageName).joinToString(" · ")
    }
}

/**
 * Host view that reports its real size to the provider and turns a long-press anywhere on the
 * widget into [onLongPress] (cancelling the touch for the widget's own views).
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
        // Settled sizes only: holding − or + changes the size every few frames.
        removeCallbacks(report)
        pending = wDp to hDp
        postDelayed(report, 150)
    }

    /** A settled size not reported yet: a view kept for later ([WidgetHost.view]) reports it when shown again. */
    private var pending: Pair<Float, Float>? = null
    private val report = Runnable { pending?.let { (w, h) -> pending = null; reportSize(w, h) } }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (pending != null) postDelayed(report, 150)
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
