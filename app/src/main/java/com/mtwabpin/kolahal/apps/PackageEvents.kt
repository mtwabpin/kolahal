package com.mtwabpin.kolahal.apps

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.script.Json
import java.util.concurrent.Executors

/**
 * Install / update / uninstall log.
 *
 * Android reports an update in several ways (removed + added with "replacing", changed, or a real
 * uninstall followed by a reinstall), so events are never logged as they arrive. Instead, every
 * signal schedules a settle-and-diff: the installed apps (per profile: version, update time,
 * label) are compared with the last snapshot. A package that is still there with a new version or
 * update time is one "updated" line; one that disappeared and came back within [GRACE_MS] is also
 * an update. The snapshot persists, so changes made while the launcher wasn't running
 * show up on the next start.
 */
class PackageEvents private constructor(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val store = KvStore(context, "packages")
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    /** While an install / update burst is settling (the short delay), [recheck] must not cut it short. */
    @Volatile private var settlingUntil = 0L
    /** When the pending diff runs (0: none pending). */
    @Volatile private var dueAt = 0L
    private val settle = Runnable { dueAt = 0L; io.execute { runCatching { diff() } } }

    /** [removedAt]: when it went missing (0 = installed); kept for a grace period before "uninstalled". */
    data class Pkg(val label: String, val version: String, val code: Long, val updated: Long, val profile: String, val removedAt: Long = 0L)

    fun start() {
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = schedule()
            override fun onPackageAdded(packageName: String, user: UserHandle) = schedule()
            override fun onPackageChanged(packageName: String, user: UserHandle) = schedule()
            override fun onPackagesAvailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = schedule()
            override fun onPackagesUnavailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = Unit
        }, main)
        // Catch up on anything that changed while the launcher was not running.
        schedule(delayMs = 2_000)
    }

    /**
     * Waits for the burst of callbacks an install or update produces to finish, then diffs once.
     * [keepSooner]: a diff already due earlier stays (it re-arms the grace check itself).
     */
    private fun schedule(delayMs: Long = SETTLE_MS, keepSooner: Boolean = false) {
        val now = System.currentTimeMillis()
        if (keepSooner && dueAt in now..now + delayMs) return
        if (delayMs <= SETTLE_MS && !keepSooner) settlingUntil = now + delayMs
        dueAt = now + delayMs
        main.removeCallbacks(settle)
        main.postDelayed(settle, delayMs)
    }

    /**
     * Called when home comes back on screen. The grace-period re-check is a timer in this process,
     * which a restart, kill or freeze can lose or delay; this catches up straight away. A settle
     * already waiting (an install in progress) is left alone. Every app is listed again only when
     * an uninstall is waiting out its grace period, or after an hour (a callback missed while frozen).
     */
    fun recheck() {
        val now = System.currentTimeMillis()
        if (!removalPending && now - diffedAt < RECHECK_AFTER_MS) return
        if (now >= settlingUntil) schedule(delayMs = 500, keepSooner = true)
    }

    /** An app went missing and its uninstall line is waiting for the grace period. */
    @Volatile private var removalPending = true
    @Volatile private var diffedAt = 0L

    /**
     * Installed apps, the serials of paused profiles (whose apps are hidden, not gone), and the
     * serials of every profile the launcher can see, paused or not.
     */
    private fun current(): Triple<Map<String, Pkg>, Set<Long>, Set<Long>> {
        val out = HashMap<String, Pkg>()
        val paused = HashSet<Long>()
        val present = HashSet<Long>()
        val me = android.os.Process.myUserHandle()
        for (user in launcherApps.profiles) {
            present += userManager.getSerialNumberForUser(user)
            // Paused profiles report no apps; keep their last known state instead of "uninstalled".
            if (runCatching { userManager.isQuietModeEnabled(user) }.getOrDefault(false)) {
                paused += userManager.getSerialNumberForUser(user)
                continue
            }
            val serial = userManager.getSerialNumberForUser(user)
            val profile = if (user == me) "" else AppRepository.kindOf(context, launcherApps, user, me).tag
            val activities = runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
            for (a in activities) {
                val key = "${a.componentName.packageName}#$serial"
                if (key in out) continue
                // Comes with the activity: no extra call to the system per app.
                val info = a.applicationInfo
                // Version details are only readable for this profile: don't ask for the others.
                val pi = if (user != me) null else runCatching {
                    context.packageManager.getPackageInfo(a.componentName.packageName, 0)
                }.getOrNull()
                out[key] = Pkg(
                    label = a.label.toString(),
                    version = pi?.versionName.orEmpty(),
                    code = pi?.longVersionCode ?: 0L,
                    // Other profiles: the app's install-time stamp is not readable; the APK's
                    // file time changes on every update, which is what we need.
                    updated = pi?.lastUpdateTime ?: info.sourceDir?.let { java.io.File(it).lastModified() } ?: 0L,
                    profile = profile,
                )
            }
        }
        return Triple(out, paused, present)
    }

    /** [key]'s app is installed, only not in the launcher (disabled, or it hid its icon). */
    private fun installedWithoutIcon(key: String): Boolean {
        val user = key.substringAfter('#').toLongOrNull()?.let(userManager::getUserForSerialNumber) ?: return false
        return runCatching { launcherApps.getApplicationInfo(key.substringBefore('#'), 0, user) != null }.getOrDefault(false)
    }

    @Synchronized
    private fun diff() {
        val (now, paused, present) = current()
        // Every profile, even the personal one, missing at once is a glitch, not a removal.
        if (present.isEmpty()) return
        val before = load()
        if (before == null) {
            // First run: remember what is installed without logging every app as "installed".
            save(now)
            saveSeen(serials(now.keys))
            return
        }
        // Before profiles were tracked: the ones with apps in the snapshot.
        val seen = loadSeen() ?: serials(before.keys)
        val plan = plan(before, now, paused, present, seen, System.currentTimeMillis(), ::installedWithoutIcon)
        if (plan.next != before) save(plan.next)
        removalPending = plan.checkInMs != null
        diffedAt = System.currentTimeMillis()
        if (plan.seen != seen) saveSeen(plan.seen)
        // Check again once the grace period is over, so a real uninstall is still logged.
        plan.checkInMs?.let { main.post { schedule(it + 500, keepSooner = true) } }
        val events = plan.events
        if (events.isEmpty()) return
        val logs = LogRepository.get(context)
        val pending = events.map { (kind, key, p) ->
            val old = before[key]
            val tag = if (p.profile.isNotEmpty()) " [${p.profile}]" else ""
            val ver = when {
                kind == "updated" && old != null && old.version.isNotEmpty() && old.version != p.version -> "\n${old.version} → ${p.version}"
                kind != "uninstalled" && p.version.isNotEmpty() -> "\n${p.version}"
                else -> ""
            }
            val glyph = when (kind) { "installed" -> "+"; "updated" -> "↻"; else -> "−" }
            val pkg = key.substringBefore('#')
            val ctx = if (kind == "uninstalled") LogContext.of(LogContext.COPY to pkg)
            else LogContext.of(LogContext.PKG to pkg, LogContext.USER to key.substringAfter('#'), LogContext.COPY to pkg)
            // Detail lines (dimmed by the log view): the package, then the version.
            LogRepository.Pending(LogKind.INFO, "${kind.replaceFirstChar { it.uppercase() }} ${p.label}$tag $glyph\n$pkg$ver", "apps", ctx, System.currentTimeMillis())
        }
        logs.addAll(pending)
        // Short history for the `installs` command.
        val history = (Json.parse(store.get(HISTORY) ?: "[]") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        val added = pending.map { mapOf("t" to it.ts, "text" to it.text) }
        store.set(HISTORY, Json.stringify((added.reversed() + history).take(MAX_HISTORY)))
    }

    /** Newest first: (time, line). */
    fun history(): List<Pair<Long, String>> =
        (Json.parse(store.get(HISTORY) ?: "[]") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().mapNotNull {
            val t = (it["t"] as? Number)?.toLong() ?: return@mapNotNull null
            t to (it["text"]?.toString() ?: return@mapNotNull null)
        }

    private fun load(): Map<String, Pkg>? {
        val raw = store.get(SNAPSHOT) ?: return null
        val map = Json.parse(raw) as? Map<*, *> ?: return null
        return map.entries.mapNotNull { (k, v) ->
            val m = v as? Map<*, *> ?: return@mapNotNull null
            k.toString() to Pkg(
                label = m["l"]?.toString().orEmpty(),
                version = m["v"]?.toString().orEmpty(),
                code = (m["c"] as? Number)?.toLong() ?: 0L,
                updated = (m["u"] as? Number)?.toLong() ?: 0L,
                profile = m["p"]?.toString().orEmpty(),
                removedAt = (m["r"] as? Number)?.toLong() ?: 0L,
            )
        }.toMap()
    }

    private fun loadSeen(): Set<Long>? =
        (Json.parse(store.get(SEEN) ?: return null) as? List<*>)?.mapNotNull { (it as? Number)?.toLong() }?.toSet()

    private fun saveSeen(serials: Set<Long>) = store.set(SEEN, Json.stringify(serials.sorted()))

    private fun save(map: Map<String, Pkg>) {
        store.set(SNAPSHOT, Json.stringify(map.mapValues { (_, p) ->
            mapOf("l" to p.label, "v" to p.version, "c" to p.code, "u" to p.updated, "p" to p.profile, "r" to p.removedAt)
        }))
    }

    companion object {
        /** An uninstall + reinstall (or the remove/add pair of an update) inside this window is one update. */
        private const val SETTLE_MS = 4_000L
        /** A missing app that reappears within this window was reinstalled: logged as an update. */
        private const val GRACE_MS = 20_000L
        /** [Pkg.removedAt] of an app still installed but not in the launcher (disabled). */
        internal const val HIDDEN = -1L
        private const val SNAPSHOT = "snapshot"
        private const val HISTORY = "history"
        /** Serials of the profiles whose apps are in the snapshot. */
        private const val SEEN = "profiles"
        private const val MAX_HISTORY = 200
        private const val RECHECK_AFTER_MS = 3_600_000L

        private fun serial(key: String) = key.substringAfter('#').toLongOrNull()
        private fun serials(keys: Collection<String>) = keys.mapNotNull(::serial).toSet()

        /** What one comparison found: lines to log (kind, key, app), what to keep, and when to look again. */
        internal data class Plan(
            val events: List<Triple<String, String, Pkg>>,
            val next: Map<String, Pkg>,
            val seen: Set<Long>,
            val checkInMs: Long?,
        )

        /**
         * Compares the snapshot [before] with the apps installed [now]. [paused]: profiles that are
         * off (their apps are hidden, not gone); [present]: every profile the launcher can see;
         * [seen]: profiles already in the snapshot.
         *
         * A profile seen switched on for the first time (off when Kolahal first ran, a new work
         * profile, a private space unlocked for the first time) goes into the snapshot without a
         * line per app. A profile that is gone altogether (deleted, or a private space Kolahal can
         * no longer see) is forgotten without uninstall lines, and is new again if it comes back.
         */
        internal fun plan(
            before: Map<String, Pkg>,
            now: Map<String, Pkg>,
            paused: Set<Long>,
            present: Set<Long>,
            seen: Set<Long>,
            t: Long,
            /** Whether a missing app is still installed, only without a launcher icon. */
            installed: (String) -> Boolean = { false },
        ): Plan {
            val fresh = serials(now.keys) - seen
            val gone = serials(before.keys).filterTo(HashSet()) { it !in present }
            val events = ArrayList<Triple<String, String, Pkg>>()
            val next = HashMap(now)
            for ((key, p) in now) {
                val old = before[key]
                when {
                    old == null && serial(key) in fresh -> Unit
                    old == null -> events += Triple("installed", key, p)
                    // Gone a moment ago and back again: an uninstall + reinstall is one update.
                    old.removedAt > 0 -> events += Triple("updated", key, p)
                    old.code != p.code || old.version != p.version || old.updated != p.updated -> events += Triple("updated", key, p)
                }
            }
            // Soonest moment a missing app has been gone for the whole grace period.
            var nextCheck = Long.MAX_VALUE
            for ((key, p) in before) {
                if (key in now) continue
                val s = serial(key)
                when {
                    s in gone -> Unit
                    // A paused / locked profile hides its apps: they are not uninstalled.
                    s in paused -> next[key] = p
                    // Kept while it is installed: no "uninstalled" now, no "installed" when its icon is back.
                    p.removedAt == HIDDEN -> if (installed(key)) next[key] = p else events += Triple("uninstalled", key, p)
                    p.removedAt == 0L -> { next[key] = p.copy(removedAt = t); nextCheck = minOf(nextCheck, GRACE_MS) }
                    t - p.removedAt < GRACE_MS -> { next[key] = p; nextCheck = minOf(nextCheck, GRACE_MS - (t - p.removedAt)) }
                    installed(key) -> next[key] = p.copy(removedAt = HIDDEN)
                    else -> events += Triple("uninstalled", key, p)
                }
            }
            return Plan(events, next, seen - gone + fresh, nextCheck.takeIf { it != Long.MAX_VALUE })
        }

        @SuppressLint("StaticFieldLeak") // application context only

        @Volatile
        private var instance: PackageEvents? = null

        fun get(context: Context): PackageEvents = instance ?: synchronized(this) {
            instance ?: PackageEvents(context.applicationContext).also { instance = it }
        }
    }
}
