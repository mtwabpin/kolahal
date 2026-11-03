package com.mtwabpin.kolahal.apps

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** Which profile an app lives in. [tag] is the short indicator printed in the terminal. */
enum class ProfileKind(val tag: String, val label: String) {
    PERSONAL("P", "personal"),
    WORK("W", "work"),
    PRIVATE("S", "private"),
    /** App-clone profile ("dual apps"). Never treated as the work profile. */
    CLONE("C", "clone"),
}

private const val USER_TYPE_PROFILE_CLONE = "android.os.usertype.profile.CLONE"

data class Profile(
    val user: UserHandle,
    val serial: Long,
    val kind: ProfileKind,
    /** True when the profile is paused ("work apps off") or the private space is locked. */
    val quiet: Boolean,
)

data class AppEntry(
    val label: String,
    val component: ComponentName,
    val profile: Profile,
    /** Label reported by the system, before any user rename. */
    val originalLabel: String = label,
    val hidden: Boolean = false,
) {
    val pkg: String get() = component.packageName
    val key: String by lazy(LazyThreadSafetyMode.PUBLICATION) { keyOf(component, profile) }

    /** For search, worked out once per list load instead of on every keystroke: lowercase, accents aside. */
    internal val lower: String = Fuzzy.fold(label)
    internal val words: List<String> = lower.split(' ', '-', '_', '.')
    /** What a slip is checked against ([Fuzzy.slip]): the label, then its words. */
    internal val names: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) { listOf(lower) + words }

    /** Its package and package/activity in lowercase, which also name it in a search: worked out at the first. */
    internal val lowerPkg: String by lazy(LazyThreadSafetyMode.PUBLICATION) { pkg.lowercase() }
    internal val lowerComponent: String by lazy(LazyThreadSafetyMode.PUBLICATION) { component.flattenToShortString().lowercase() }
}

/**
 * Lists launchable activities across every profile visible to the launcher
 * (personal, managed work profile, Android 15 private space) and keeps the list fresh.
 */
class AppRepository private constructor(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = AppPrefs.get(context)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private var started = false
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch { for (request in refreshRequests) runCatching { reload() } }
    }

    fun start() {
        if (started) return
        started = true
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = refreshSoon(listOf(packageName))
            override fun onPackageAdded(packageName: String, user: UserHandle) = refreshSoon(listOf(packageName))
            override fun onPackageChanged(packageName: String, user: UserHandle) = refreshSoon(listOf(packageName))
            override fun onPackagesAvailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = refreshSoon(names.asList())
            override fun onPackagesUnavailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = refreshSoon(names.asList())
        })
        // Kept between visits to home: what can change it reloads it. App names follow the language.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_LOCALE_CHANGED)
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
            // Any profile coming or going (a clone too): from Android 14.
            if (Build.VERSION.SDK_INT >= 34) {
                addAction(Intent.ACTION_PROFILE_ADDED)
                addAction(Intent.ACTION_PROFILE_REMOVED)
            }
            if (Build.VERSION.SDK_INT >= 35) {
                addAction(Intent.ACTION_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
            }
        }
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = refresh()
        }, filter, ContextCompat.RECEIVER_EXPORTED)
        refresh()
    }

    /** Requests a reload; requests are conflated and processed one at a time, so results never race. */
    fun refresh() {
        refreshRequests.trySend(Unit)
    }

    private var soon: kotlinx.coroutines.Job? = null

    private val _packageChanges = MutableStateFlow(0)

    /** Bumped when an app is installed, updated or removed: its icon may have changed. */
    val packageChanges: StateFlow<Int> = _packageChanges.asStateFlow()

    /** The packages behind [packageChanges] since [takeChanged] last asked. */
    private val changed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** The packages installed, updated or removed since the last call: their icons may have changed. */
    fun takeChanged(): Set<String> = changed.toSet().also { changed.removeAll(it) }

    /** An install or update sends several callbacks in a row: one reload once they stop. */
    private fun refreshSoon(packages: Collection<String>) {
        changed += packages
        _packageChanges.update { it + 1 }
        soon?.cancel()
        soon = scope.launch {
            kotlinx.coroutines.delay(300.milliseconds)
            refresh()
        }
    }

    private fun reload() {
        run {
            val profiles = loadProfiles()
            val apps = profiles.flatMap { profile ->
                runCatching { launcherApps.getActivityList(null, profile.user) }
                    .getOrDefault(emptyList())
                    .map { info ->
                        val original = info.label.toString()
                        val key = keyOf(info.componentName, profile)
                        AppEntry(
                            label = prefs.label(key) ?: original,
                            component = info.componentName,
                            profile = profile,
                            originalLabel = original,
                            hidden = prefs.isHidden(key),
                        )
                    }
            }.map { it to it.label.lowercase() }
                // Each label lowered once, not at every comparison of the sort.
                .sortedWith(compareBy({ it.second }, { it.first.profile.kind.ordinal }))
                .map { it.first }
            _profiles.value = profiles
            _apps.value = apps
        }
    }

    private fun loadProfiles(): List<Profile> {
        val me = Process.myUserHandle()
        return launcherApps.profiles.map { user ->
            Profile(
                user = user,
                serial = userManager.getSerialNumberForUser(user),
                kind = kindOf(context, launcherApps, user, me),
                quiet = user != me && runCatching { userManager.isQuietModeEnabled(user) }.getOrDefault(false),
            )
        }.sortedBy { it.kind.ordinal }
    }

    fun profileBySerial(serial: Long): Profile? = _profiles.value.firstOrNull { it.serial == serial }

    fun find(pkg: String, cls: String?, serial: Long?): AppEntry? = _apps.value.firstOrNull {
        it.pkg == pkg && (cls == null || it.component.className == cls) &&
            (serial == null || it.profile.serial == serial)
    }

    /** Ranked fuzzy match: exact > prefix > word prefix > substring > subsequence; a slip when nothing else matches. */
    fun search(query: String, kind: ProfileKind? = null, includeHidden: Boolean = false): List<AppEntry> =
        searchRanked(query, kind, includeHidden).map { it.first }

    /** The last search: instant run and the suggestions ask the same question on every keystroke. */
    private class Memo(
        val q: String, val kind: ProfileKind?, val hidden: Boolean, val apps: List<AppEntry>, val version: Int,
        val result: List<Pair<AppEntry, Int>>,
    )
    @Volatile private var memo: Memo? = null

    /** Matches with their tier: 0 exact, 1 prefix, 2 word prefix, 3 substring, 4 subsequence, 5 a slip. */
    fun searchRanked(query: String, kind: ProfileKind? = null, includeHidden: Boolean = false): List<Pair<AppEntry, Int>> {
        val q = Fuzzy.fold(query.trim())
        if (q.isEmpty()) return emptyList()
        val apps = _apps.value
        val version = prefs.version.value
        memo?.let { m -> if (m.q == q && m.kind == kind && m.hidden == includeHidden && m.apps === apps && m.version == version) return m.result }
        val result = rank(apps, q, kind, includeHidden)
        memo = Memo(q, kind, includeHidden, apps, version, result)
        return result
    }

    private fun rank(apps: List<AppEntry>, q: String, kind: ProfileKind?, includeHidden: Boolean): List<Pair<AppEntry, Int>> {
        val now = System.currentTimeMillis()
        val candidates = apps.asSequence().filter { (kind == null || it.profile.kind == kind) && (includeHidden || !it.hidden) }
        // Slips only when nothing matches as typed: they never crowd out a real match, or stop
        // the only match from launching (launch.single).
        return (candidates.mapNotNull { app -> score(app, q)?.let { Ranked(app, it) } }.toList()
            .ifEmpty { candidates.filter { Fuzzy.slip(q, it.names) }.map { Ranked(it, SLIP) }.toList() })
            // Frecency is read once per match, not on every comparison.
            .onEach { it.frecency = prefs.frecency(it.app.key, now) }
            .sortedWith(
                compareBy<Ranked> { it.tier }
                    .thenByDescending { it.frecency }
                    .thenBy { it.app.label.length }
                    .thenBy { it.app.profile.kind.ordinal }
            )
            .map { it.app to it.tier }
    }

    /** Pinned apps (in pin order), then the most frecent visible apps, for the idle suggestion strip. */
    fun top(n: Int): List<AppEntry> {
        val now = System.currentTimeMillis()
        val byKey = _apps.value.associateBy { it.key }
        val pinned = prefs.pinned().mapNotNull { byKey[it] }.filter { !it.hidden && !it.profile.quiet }
        val pinnedKeys = pinned.mapTo(HashSet()) { it.key }
        return pinned.take(n) + _apps.value.asSequence()
            .filter { !it.hidden && !it.profile.quiet && it.key !in pinnedKeys }
            .map { it to prefs.frecency(it.key, now) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take((n - pinned.size).coerceAtLeast(0))
            .map { it.first }
            .toList()
    }

    private class Ranked(val app: AppEntry, val tier: Int) {
        var frecency = 0.0
    }

    private fun score(app: AppEntry, q: String): Int? = tierOf(app.lower, app.words, app.lowerPkg, app.lowerComponent, q)

    fun launch(app: AppEntry) {
        launcherApps.startMainActivity(app.component, app.profile.user, null, null)
        prefs.recordLaunch(app.key)
    }

    /**
     * The app's launcher shortcuts (static, dynamic and pinned). Only available to the default
     * launcher; returns an empty list otherwise.
     */
    fun shortcuts(app: AppEntry): List<android.content.pm.ShortcutInfo> = runCatching {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(app.pkg)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        launcherApps.getShortcuts(query, app.profile.user).orEmpty()
            .filter { it.isEnabled }
            .sortedBy { it.rank }
    }.getOrDefault(emptyList())

    fun startShortcut(app: AppEntry, id: String) {
        launcherApps.startShortcut(app.pkg, id, null, null, app.profile.user)
        prefs.recordLaunch(app.key)
    }

    fun openAppInfo(app: AppEntry) {
        launcherApps.startAppDetailsActivity(app.component, app.profile.user, null, null)
    }

    fun uninstall(app: AppEntry) {
        if (app.profile.kind == ProfileKind.PERSONAL) {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.pkg, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } else {
            // Packages in other profiles can only be removed from their own settings page.
            openAppInfo(app)
        }
    }

    /**
     * Pause/resume the work profile (or lock/unlock private space).
     * Returns false when unsupported (needs API 29+ and being the default launcher).
     */
    fun setQuietMode(profile: Profile, quiet: Boolean): Boolean {
        return Build.VERSION.SDK_INT >= 29 && runCatching {
            userManager.requestQuietModeEnabled(quiet, profile.user)
            true
        }.getOrDefault(false).also { refresh() }
    }

    companion object {
        /**
         * How [q] matches an app by its folded label ([lower]), the label's [words], its package and
         * package/activity: 0 exact, 1 prefix, 2 word prefix, 3 substring, 4 subsequence, null not at
         * all. A slip ([Fuzzy.slip], tier 5) is tried only when nothing matched this way.
         */
        internal fun tierOf(lower: String, words: List<String>, lowerPkg: String, lowerComponent: String, q: String): Int? = when {
            lower == q -> 0
            // Its package, or package/activity, names one app exactly: how two apps of one name are told apart.
            lowerPkg == q || lowerComponent == q -> 0
            lower.startsWith(q) -> 1
            words.any { it.startsWith(q) } -> 2
            lower.contains(q) -> 3
            isSubsequence(q, lower) -> 4
            else -> null
        }

        private fun isSubsequence(q: String, s: String): Boolean {
            var i = 0
            for (ch in s) if (i < q.length && ch == q[i]) i++
            return i == q.length
        }

        /** The tier of a match that is only a slip of the thumb ([Fuzzy.slip]). */
        private const val SLIP = 5

        /** Which kind of profile [user] is, seen from [me] (this app's user): anything not personal, a private space or a clone is work. */
        fun kindOf(context: Context, launcherApps: LauncherApps, user: UserHandle, me: UserHandle): ProfileKind {
            if (user == me) return ProfileKind.PERSONAL
            if (Build.VERSION.SDK_INT >= 35) {
                val type = runCatching { launcherApps.getLauncherUserInfo(user)?.userType }.getOrNull()
                when (type) {
                    UserManager.USER_TYPE_PROFILE_PRIVATE -> return ProfileKind.PRIVATE
                    USER_TYPE_PROFILE_CLONE -> return ProfileKind.CLONE
                }
            } else if (labelledAsClone(context, user)) {
                return ProfileKind.CLONE
            }
            return ProfileKind.WORK
        }

        /**
         * Below Android 15 a launcher can't ask what a profile is, but Android names a clone's apps as
         * clones: "Clone Chrome", where a work profile's are "Work Chrome" (app cloning, Android 14).
         * Only that wording counts; a profile named any other way stays work, as before.
         */
        private fun labelledAsClone(context: Context, user: UserHandle): Boolean = runCatching {
            val res = android.content.res.Resources.getSystem()
            val clone = res.getIdentifier("clone_profile_label_badge", "string", "android")
            clone != 0 && context.packageManager.getUserBadgedLabel(PROBE_LABEL, user).toString() == res.getString(clone, PROBE_LABEL)
        }.getOrDefault(false)

        /** Any app name: what Android adds to it for a profile is what tells. */
        private const val PROBE_LABEL = "Kolahal"

        @SuppressLint("StaticFieldLeak") // application context only
        @Volatile
        private var instance: AppRepository? = null

        fun get(context: Context): AppRepository = instance ?: synchronized(this) {
            instance ?: AppRepository(context.applicationContext).also { instance = it }
        }
    }
}

internal fun keyOf(component: ComponentName, profile: Profile) =
    "${component.flattenToShortString()}#${profile.serial}"
