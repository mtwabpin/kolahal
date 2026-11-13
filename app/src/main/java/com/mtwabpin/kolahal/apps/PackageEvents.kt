package com.mtwabpin.kolahal.apps

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
import com.mtwabpin.kolahal.data.Singleton
import com.mtwabpin.kolahal.script.Json
import java.util.concurrent.Executors

/**
 * Logs installs, updates and uninstalls.
 *
 * Android reports an update in several ways: removed + added with "replacing", changed, or a real
 * uninstall followed by a reinstall. So events are never logged as they arrive. Instead, every
 * signal schedules a diff after a short wait. The diff compares the installed apps (per profile:
 * version, update time, label) with the last snapshot.
 * - A package that is still there with a new version or update time gets one "updated" line.
 * - A package that disappeared and came back within [GRACE_MS] is also an update.
 * - The snapshot is saved, so changes made while the launcher wasn't running show up on the next start.
 */
class PackageEvents private constructor(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val store = KvStore(context, "packages")
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    /** While a burst of install or update callbacks is settling (the short delay), [recheck] must not cut it short. */
    @Volatile private var settlingUntil = 0L
    /** When the pending diff runs. 0 means none is pending. */
    @Volatile private var dueAt = 0L
    private val settle = Runnable { dueAt = 0L; io.execute { runCatching { diff() } } }

    /**
     * [removedAt] is when the app went missing (0 means installed). A missing app is kept for a grace
     * period before it is logged as "uninstalled".
     */
    data class Pkg(val label: String, val version: String, val code: Long, val updated: Long, val profile: String, val removedAt: Long = 0L)

    fun start() {
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = told()
            override fun onPackageAdded(packageName: String, user: UserHandle) = told()
            override fun onPackageChanged(packageName: String, user: UserHandle) = told()
            override fun onPackagesAvailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = told()
            override fun onPackagesUnavailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = Unit
        }, main)
        // Catch up on anything that changed while the launcher was not running. It lists every app again,
        // so it waits until start-up is over: the first frame, the app list and the log's first read.
        Looper.getMainLooper().queue.addIdleHandler {
            schedule(delayMs = STARTUP_DIFF_MS)
            false
        }
    }

    /**
     * True until the catch-up diff that [start] sets runs. [recheck] leaves it to that diff: home's
     * first return comes before [start], which waits for the first frame.
     */
    @Volatile private var startupDiffDue = true

    /** True when a callback came since the last diff. The app list already reloaded for it ([AppRepository.start]). */
    @Volatile private var called = false

    private fun told() {
        called = true
        schedule()
    }

    /**
     * Waits until the burst of callbacks from an install or update ends, then diffs once.
     * With [keepSooner], a diff that is already due earlier stays as it is (it sets the grace check again itself).
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
     * Called when home comes back on screen. The grace-period check is a timer in this process, and a
     * restart, kill or freeze can lose or delay it. This call catches up right away.
     * - A wait that is already running (an install in progress) is left alone.
     * - Every app is listed again only when an uninstall is waiting for its grace period to end, or
     *   after an hour (in case a callback was missed while frozen).
     */
    fun recheck() {
        if (startupDiffDue) return
        val now = System.currentTimeMillis()
        if (!removalPending && now - diffedAt < RECHECK_AFTER_MS) return
        if (now >= settlingUntil) schedule(delayMs = 500, keepSooner = true)
    }

    /** True when an app went missing and its uninstall line is waiting for the grace period. */
    @Volatile private var removalPending = true
    @Volatile private var diffedAt = 0L

    /** What [current] last read from Android for one of this profile's apps, with the APK it read. */
    private class Asked(val apk: String, val time: Long, val version: String, val code: Long, val updated: Long)

    /**
     * Results by key, since this process started. The same APK file (path and time) means the same
     * version, so the hourly check and the check after an install ask only about apps that changed.
     * Used under [diff]'s lock.
     */
    private val asked = HashMap<String, Asked>()

    /**
     * Returns:
     * - the installed apps
     * - the serials of paused profiles (their apps are hidden, not gone)
     * - the serials of every profile the launcher can see, paused or not
     */
    private fun current(): Triple<Map<String, Pkg>, Set<Long>, Set<Long>> {
        val out = HashMap<String, Pkg>()
        val paused = HashSet<Long>()
        val present = HashSet<Long>()
        val me = android.os.Process.myUserHandle()
        for (user in launcherApps.profiles) {
            present += userManager.getSerialNumberForUser(user)
            // Paused profiles report no apps. Keep their last known state instead of logging "uninstalled".
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
                // This comes with the activity, so there is no extra system call per app.
                val info = a.applicationInfo
                val apkTime = info.sourceDir?.let { java.io.File(it).lastModified() } ?: 0L
                // Version details can be read only for this profile, so don't ask for the others.
                val pi = if (user != me) null else asked[key]?.takeIf { it.apk == info.sourceDir && it.time == apkTime } ?: runCatching {
                    context.packageManager.getPackageInfo(a.componentName.packageName, 0)
                }.getOrNull()?.let { Asked(info.sourceDir.orEmpty(), apkTime, it.versionName.orEmpty(), it.longVersionCode, it.lastUpdateTime) }
                    ?.also { if (info.sourceDir != null) asked[key] = it }
                out[key] = Pkg(
                    label = com.mtwabpin.kolahal.data.StyledText.strip(a.label.toString()),
                    version = pi?.version.orEmpty(),
                    code = pi?.code ?: 0L,
                    // For other profiles, the app's update time can't be read. The APK's file time
                    // changes on every update, which is all we need.
                    updated = pi?.updated ?: apkTime,
                    profile = profile,
                )
            }
        }
        asked.keys.retainAll(out.keys)
        return Triple(out, paused, present)
    }

    /** True when [key]'s app is installed but not in the launcher (disabled, or it hid its icon). */
    private fun installedWithoutIcon(key: String): Boolean {
        val user = key.substringAfter('#').toLongOrNull()?.let(userManager::getUserForSerialNumber) ?: return false
        return runCatching { launcherApps.getApplicationInfo(key.substringBefore('#'), 0, user) != null }.getOrDefault(false)
    }

    @Synchronized
    private fun diff() {
        startupDiffDue = false
        val afterCallbacks = called
        called = false
        val (now, paused, present) = current()
        // If every profile, even the personal one, is missing at once, it is a glitch, not a removal.
        if (present.isEmpty()) return
        // A database from another phone has that phone's apps: clear them first, so this is a first run.
        com.mtwabpin.kolahal.data.ThisInstall.check(context)
        val before = load()
        if (before == null) {
            // First run: save what is installed, without logging every app as "installed".
            save(now)
            saveSeen(serials(now.keys))
            return
        }
        // If profiles weren't tracked yet, use the profiles that have apps in the snapshot.
        val seen = loadSeen() ?: serials(before.keys)
        val plan = plan(before, now, paused, present, seen, System.currentTimeMillis(), ::installedWithoutIcon)
        if (plan.next != before) save(plan.next)
        removalPending = plan.checkInMs != null
        diffedAt = System.currentTimeMillis()
        if (plan.seen != seen) saveSeen(plan.seen)
        // Check again after the grace period ends, so a real uninstall is still logged.
        plan.checkInMs?.let { main.post { schedule(it + 500, keepSooner = true) } }
        val events = plan.events
        // This found a change that the callbacks missed (the process was asleep), so update the app
        // list. Profiles added or removed are rare, and a callback may not report them.
        if (plan.seen != seen || (events.isNotEmpty() && !afterCallbacks)) AppRepository.get(context).refresh()
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
            val glyph = when (kind) { "installed" -> "+"; "updated" -> "↑"; else -> "−" }
            val pkg = key.substringBefore('#')
            val ctx = if (kind == "uninstalled") LogContext.of(LogContext.COPY to pkg)
            else LogContext.of(LogContext.PKG to pkg, LogContext.USER to key.substringAfter('#'), LogContext.COPY to pkg)
            // Detail lines (the log view dims them): the package, then the version.
            LogRepository.Pending(LogKind.INFO, "${kind.replaceFirstChar { it.uppercase() }} ${p.label}$tag $glyph\n$pkg$ver", "apps", ctx, System.currentTimeMillis())
        }
        logs.addAll(pending)
        // A short history for the `installs` command.
        val history = (Json.parse(store.get(HISTORY) ?: "[]") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        val added = pending.map { mapOf("t" to it.ts, "text" to it.text) }
        store.set(HISTORY, Json.stringify((added.reversed() + history).take(MAX_HISTORY)))
    }

    /** Returns the history as (time, line) pairs, newest first. */
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

    companion object : Singleton<PackageEvents>(::PackageEvents) {
        /** Wait before a diff. An uninstall + reinstall (or the remove/add pair of an update) within it is one update. */
        private const val SETTLE_MS = 4_000L

        /** How long after start-up goes idle the catch-up diff runs. */
        private const val STARTUP_DIFF_MS = 3_000L
        /** A missing app that comes back within this time was reinstalled. It is logged as an update. */
        private const val GRACE_MS = 20_000L
        /** [Pkg.removedAt] value for an app that is still installed but not in the launcher (disabled). */
        internal const val HIDDEN = -1L
        private const val SNAPSHOT = "snapshot"
        private const val HISTORY = "history"
        /** Serials of the profiles whose apps are in the snapshot. */
        private const val SEEN = "profiles"
        private const val MAX_HISTORY = 200
        private const val RECHECK_AFTER_MS = 3_600_000L

        private fun serial(key: String) = key.substringAfter('#').toLongOrNull()
        private fun serials(keys: Collection<String>) = keys.mapNotNull(::serial).toSet()

        /** Result of one comparison: lines to log (kind, key, app), what to keep, and when to check again. */
        internal data class Plan(
            val events: List<Triple<String, String, Pkg>>,
            val next: Map<String, Pkg>,
            val seen: Set<Long>,
            val checkInMs: Long?,
        )

        /**
         * Compares the snapshot [before] with the apps installed [now].
         * - [paused]: profiles that are off. Their apps are hidden, not gone.
         * - [present]: every profile the launcher can see.
         * - [seen]: profiles already in the snapshot.
         *
         * A profile seen on for the first time goes into the snapshot without a line per app. For
         * example: it was off when Kolahal first ran, it is a new work profile, or it is a private
         * space unlocked for the first time.
         * A profile that is gone completely (deleted, or a private space Kolahal can no longer see) is
         * forgotten without uninstall lines. If it comes back, it is new again.
         */
        internal fun plan(
            before: Map<String, Pkg>,
            now: Map<String, Pkg>,
            paused: Set<Long>,
            present: Set<Long>,
            seen: Set<Long>,
            t: Long,
            /** True when a missing app is still installed, only without a launcher icon. */
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
                    // It was gone a moment ago and is back: an uninstall + reinstall is one update.
                    old.removedAt > 0 -> events += Triple("updated", key, p)
                    old.code != p.code || old.version != p.version || old.updated != p.updated -> events += Triple("updated", key, p)
                }
            }
            // The soonest time at which a missing app has been gone for the whole grace period.
            var nextCheck = Long.MAX_VALUE
            for ((key, p) in before) {
                if (key in now) continue
                val s = serial(key)
                when {
                    s in gone -> Unit
                    // A paused or locked profile hides its apps. They are not uninstalled.
                    s in paused -> next[key] = p
                    // Keep it while it is installed. Then there is no "uninstalled" line now, and no
                    // "installed" line when its icon comes back.
                    p.removedAt == HIDDEN -> if (installed(key)) next[key] = p else events += Triple("uninstalled", key, p)
                    p.removedAt == 0L -> { next[key] = p.copy(removedAt = t); nextCheck = minOf(nextCheck, GRACE_MS) }
                    t - p.removedAt < GRACE_MS -> { next[key] = p; nextCheck = minOf(nextCheck, GRACE_MS - (t - p.removedAt)) }
                    installed(key) -> next[key] = p.copy(removedAt = HIDDEN)
                    else -> events += Triple("uninstalled", key, p)
                }
            }
            return Plan(events, next, seen - gone + fresh, nextCheck.takeIf { it != Long.MAX_VALUE })
        }
    }
}
