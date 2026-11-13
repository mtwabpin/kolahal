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
import com.mtwabpin.kolahal.data.Singleton
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

/** The profile an app belongs to. [tag] is the short marker printed in the terminal. */
enum class ProfileKind(val tag: String, val label: String) {
    PERSONAL("P", "personal"),
    WORK("W", "work"),
    PRIVATE("S", "private"),
    /** App-clone profile ("dual apps"). Never treated as the work profile. */
    CLONE("C", "clone"),
}

private const val USER_TYPE_PROFILE_CLONE = "android.os.usertype.profile.CLONE"

/** Immutable, so a row that shows it (a suggestion) is skipped when it's the same. */
@androidx.compose.runtime.Immutable
data class Profile(
    val user: UserHandle,
    val serial: Long,
    val kind: ProfileKind,
    /** True when the profile is paused ("work apps off") or the private space is locked. */
    val quiet: Boolean,
)

@androidx.compose.runtime.Immutable
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

    /** For search: lowercase and without accents. Computed once per list load, not on every key press. */
    internal val lower: String = Fuzzy.fold(label)
    internal val words: List<String> = lower.split(' ', '-', '_', '.')
    /** Names that a typing mistake is checked against ([Fuzzy.slip]): the label, then its words. */
    internal val names: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) { listOf(lower) + words }

    /** Its package and package/activity in lowercase. A search can also match these. Computed at the first search. */
    internal val lowerPkg: String by lazy(LazyThreadSafetyMode.PUBLICATION) { pkg.lowercase() }
    internal val lowerComponent: String by lazy(LazyThreadSafetyMode.PUBLICATION) { component.flattenToShortString().lowercase() }
}

/**
 * Lists launchable activities in every profile the launcher can see (personal, managed work
 * profile, Android 15 private space) and keeps the list up to date.
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

    /** Set when this start's own load has arrived. From then on, the saved list ([saved]) is never shown. */
    private var loaded = false
    private val loadLock = Any()
    private val firstLoad = java.util.concurrent.CountDownLatch(1)

    /**
     * Waits up to [ms] for this start's first load of the list. Until then the list holds what was
     * saved last time ([saved]), or nothing on a first start. Returns false when it didn't arrive.
     */
    fun awaitLoaded(ms: Long): Boolean = firstLoad.await(ms, java.util.concurrent.TimeUnit.MILLISECONDS)

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
        // This receiver stays registered between visits to home, and each of these events reloads the
        // list. App names depend on the language.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_LOCALE_CHANGED)
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
            // From Android 14: any profile added or removed, a clone too.
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
        // The list as last loaded, at once, until this start's own load arrives (up to a second for
        // every app's name). Before, app names were "not found" and suggested nothing meanwhile.
        scope.launch {
            val kept = runCatching { saved() }.getOrDefault(emptyList())
            if (kept.isNotEmpty()) synchronized(loadLock) { if (!loaded) _apps.value = kept }
        }
        refresh()
    }

    /** Requests a reload. Requests are merged and run one at a time, so results never race. */
    fun refresh() {
        refreshRequests.trySend(Unit)
    }

    private var soon: kotlinx.coroutines.Job? = null

    private val _packageChanges = MutableStateFlow(0)

    /** Increases when an app is installed, updated or removed, because its icon may have changed. */
    val packageChanges: StateFlow<Int> = _packageChanges.asStateFlow()

    /** The packages that changed [packageChanges] since the last [takeChanged] call. */
    private val changed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Returns the packages installed, updated or removed since the last call. Their icons may have changed. */
    fun takeChanged(): Set<String> = changed.toSet().also { changed.removeAll(it) }

    /** An install or update sends several callbacks in a row. Reload once, after they stop. */
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
                        // Remove styling: an app's name must not style a log line.
                        val original = com.mtwabpin.kolahal.data.StyledText.strip(info.label.toString())
                        val key = keyOf(info.componentName, profile)
                        AppEntry(
                            label = prefs.label(key) ?: original,
                            component = info.componentName,
                            profile = profile,
                            originalLabel = original,
                            hidden = prefs.isHidden(key),
                        )
                    }
            }.let(::sorted)
            synchronized(loadLock) {
                loaded = true
                _profiles.value = profiles
                _apps.value = apps
            }
            firstLoad.countDown()
            runCatching { save(apps) }
        }
    }

    /** [apps] A–Z by name, each profile's after the personal one's. */
    private fun sorted(apps: List<AppEntry>): List<AppEntry> = apps.map { it to it.label.lowercase() }
        // Lowercase each label once, not at every comparison of the sort.
        .sortedWith(compareBy({ it.second }, { it.first.profile.kind.ordinal }))
        .map { it.first }

    // ---- the list as last loaded, for the next start ------------------------------------------

    /**
     * Where the list as last loaded is kept: one app per line, its profile's serial, kind and quiet
     * flag, its component and its name. In noBackupFilesDir: it belongs to this phone, so backups and
     * transfers leave it out. Private space apps aren't kept, so which apps are in it isn't in a file.
     */
    private val savedFile get() = java.io.File(context.noBackupFilesDir, "apps.tsv")

    /** What [savedFile] holds, so an unchanged list isn't written again. */
    @Volatile private var savedText: String? = null

    private fun save(apps: List<AppEntry>) {
        val text = apps.filter { it.profile.kind != ProfileKind.PRIVATE }.joinToString("\n") { app ->
            listOf(
                app.profile.serial, app.profile.kind.name, if (app.profile.quiet) "1" else "0",
                app.component.flattenToString(), app.originalLabel.replace(FIELD_BREAKS, " "),
            ).joinToString("\t")
        }
        if (text == savedText) return
        val tmp = java.io.File(savedFile.path + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(savedFile)) tmp.delete()
        savedText = text
    }

    /** The list [save] kept last, with today's names and hidden apps. Apps of a profile that is gone are left out. */
    internal fun saved(): List<AppEntry> {
        val file = savedFile
        if (!file.exists()) return emptyList()
        val text = file.readText()
        savedText = text
        val profiles = HashMap<Long, Profile?>()
        return sorted(text.lineSequence().mapNotNull { line ->
            val f = line.split('\t')
            if (f.size != 5) return@mapNotNull null
            val serial = f[0].toLongOrNull() ?: return@mapNotNull null
            val kind = ProfileKind.entries.firstOrNull { it.name == f[1] } ?: return@mapNotNull null
            val profile = profiles.getOrPut(serial) {
                runCatching { userManager.getUserForSerialNumber(serial) }.getOrNull()?.let { Profile(it, serial, kind, quiet = f[2] == "1") }
            } ?: return@mapNotNull null
            val component = ComponentName.unflattenFromString(f[3]) ?: return@mapNotNull null
            val key = keyOf(component, profile)
            AppEntry(label = prefs.label(key) ?: f[4], component = component, profile = profile, originalLabel = f[4], hidden = prefs.isHidden(key))
        }.toList())
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

    /**
     * Ranked fuzzy match: exact > prefix > word prefix > substring > subsequence. A typing mistake
     * counts only when nothing else matches.
     */
    fun search(query: String, kind: ProfileKind? = null, includeHidden: Boolean = false): List<AppEntry> =
        searchRanked(query, kind, includeHidden).map { it.first }

    /** The last search. Instant run and the suggestions ask the same search on every key press. */
    private class Memo(
        val q: String, val kind: ProfileKind?, val hidden: Boolean, val apps: List<AppEntry>, val version: Int,
        val result: List<Pair<AppEntry, Int>>,
    )
    @Volatile private var memo: Memo? = null

    /** Matches with their tier: 0 exact, 1 prefix, 2 word prefix, 3 substring, 4 subsequence, 5 typing mistake. */
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
        // Check typing mistakes only when nothing matches as typed. So they never push out a real
        // match, or stop the only match from launching (launch.single).
        return (candidates.mapNotNull { app -> score(app, q)?.let { Ranked(app, it) } }.toList()
            .ifEmpty { candidates.filter { Fuzzy.slip(q, it.names) }.map { Ranked(it, SLIP) }.toList() })
            // Read frecency once per match, not on every comparison.
            .onEach { it.frecency = prefs.frecency(it.app.key, now) }
            .sortedWith(
                compareBy<Ranked> { it.tier }
                    .thenByDescending { it.frecency }
                    .thenBy { it.app.label.length }
                    .thenBy { it.app.profile.kind.ordinal }
            )
            .map { it.app to it.tier }
    }

    /** Pinned apps (in pin order), then the visible apps with the highest frecency. For the idle suggestion strip. */
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
     * The app's launcher shortcuts (static, dynamic and pinned). Only the default launcher can read
     * them. Otherwise it returns an empty list.
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

    /**
     * The apps with at least one launcher shortcut, as (package, profile serial), in one query per
     * profile. Null when Kolahal may not read shortcuts: only the default launcher can.
     */
    fun appsWithShortcuts(): Set<Pair<String, Long>>? {
        if (runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false) != true) return null
        val query = LauncherApps.ShortcutQuery().setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
        )
        return _profiles.value.filter { !it.quiet }.flatMap { profile ->
            runCatching { launcherApps.getShortcuts(query, profile.user).orEmpty() }.getOrDefault(emptyList())
                .filter { it.isEnabled }.map { it.`package` to profile.serial }
        }.toSet()
    }

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
            // Packages in other profiles can be removed only from their own settings page.
            openAppInfo(app)
        }
    }

    /**
     * Pauses or resumes the work profile, or locks or unlocks the private space.
     * Returns false when not supported. It needs API 29+ and Kolahal as the default launcher.
     */
    fun setQuietMode(profile: Profile, quiet: Boolean): Boolean {
        return Build.VERSION.SDK_INT >= 29 && runCatching {
            userManager.requestQuietModeEnabled(quiet, profile.user)
            true
        }.getOrDefault(false).also { refresh() }
    }

    companion object : Singleton<AppRepository>(::AppRepository) {
        /**
         * Returns how [q] matches an app. It checks the folded label ([lower]), the label's [words],
         * the package and package/activity. Tiers: 0 exact, 1 prefix, 2 word prefix, 3 substring,
         * 4 subsequence, null no match. A typing mistake ([Fuzzy.slip], tier 5) is tried only when
         * nothing matched in these ways.
         */
        internal fun tierOf(lower: String, words: List<String>, lowerPkg: String, lowerComponent: String, q: String): Int? = when {
            lower == q -> 0
            // The package, or package/activity, names one app exactly. This tells apart two apps with the same name.
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

        /** The tier of a match that is only a typing mistake ([Fuzzy.slip]). */
        private const val SLIP = 5

        /**
         * Returns the kind of profile [user] is, as seen from [me] (this app's user). Any profile that
         * is not personal, a private space or a clone counts as work.
         */
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
         * Below Android 15, a launcher can't ask what kind a profile is. But Android labels a clone's
         * apps as clones: "Clone Chrome", while a work profile's apps are "Work Chrome" (app cloning,
         * Android 14). Only that wording counts. A profile with any other label stays work, as before.
         */
        @SuppressLint("DiscouragedApi") // the badge's string isn't public: only its name finds it
        private fun labelledAsClone(context: Context, user: UserHandle): Boolean = runCatching {
            val res = android.content.res.Resources.getSystem()
            val clone = res.getIdentifier("clone_profile_label_badge", "string", "android")
            clone != 0 && context.packageManager.getUserBadgedLabel(PROBE_LABEL, user).toString() == res.getString(clone, PROBE_LABEL)
        }.getOrDefault(false)

        /** Any app name works. Only the text that Android adds to it for a profile matters. */
        private const val PROBE_LABEL = "Kolahal"
    }
}

/** Tabs and line breaks in an app's name: the saved list ([AppRepository.saved]) separates its fields with them. */
private val FIELD_BREAKS = Regex("[\\t\\r\\n]")

/**
 * An app's key, "<component>#<serial>": the same app in two profiles has two keys. Package keys
 * ([PackageEvents]) are "<package>#<serial>". [nameOf] and [serialOf] read either kind back.
 */
internal fun keyOf(component: ComponentName, profile: Profile) =
    "${component.flattenToShortString()}#${profile.serial}"

/** The component or package of a key ([keyOf]). */
internal fun nameOf(key: String) = key.substringBeforeLast('#')

/** The profile's serial in a key ([keyOf]), or null for a key without one. */
internal fun serialOf(key: String): Long? = key.substringAfterLast('#', "").toLongOrNull()
