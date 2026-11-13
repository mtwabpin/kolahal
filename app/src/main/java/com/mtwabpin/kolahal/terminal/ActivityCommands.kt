package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.commands.CommandParsing
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository

/** One activity of an app that `activity` can open. */
data class ActivityItem(
    /** The full class name, such as `com.android.settings.Settings$WifiSettingsActivity`. */
    val className: String,
    /** The name shown in the list ([ActivityMatch.shownLabel]). */
    val label: String,
) {
    /** The class name without its package: `Settings$WifiSettingsActivity`. */
    val shortName: String get() = className.substringAfterLast('.')

    /** The inner class part: `WifiSettingsActivity`. The same as [shortName] when there is no `$`. */
    val innerName: String get() = shortName.substringAfterLast('$')
}

/**
 * The pure parts of `activity`: names, matching and splitting the arguments.
 * They need no Android, so unit tests check them.
 */
object ActivityMatch {
    /** What a name finds in an app's activities. */
    sealed interface Found {
        data class One(val item: ActivityItem) : Found
        /** Several match. The command lists them, so the user can pick one. */
        data class Many(val items: List<ActivityItem>) : Found
        data object None : Found
    }

    /**
     * The name to show for an activity. Many activities have no label of their own: Android then
     * gives the app's label. Such a label (or an empty one) says nothing, so the class name is shown.
     */
    fun shownLabel(label: String?, appLabel: String, className: String): String {
        val l = label?.trim().orEmpty()
        return if (l.isEmpty() || l.equals(appLabel.trim(), ignoreCase = true)) className.substringAfterLast('.') else l
    }

    /** The class name as shown under the label: `.Settings$Wifi` when it is in the app's package. */
    fun shownClass(pkg: String, className: String): String =
        if (className.startsWith("$pkg.")) className.removePrefix(pkg) else className

    /**
     * Finds the activity that [query] names. It is checked in this order:
     * 1. The whole class name, the short class name, the inner class name or the label, in any case.
     * 2. The start of the short class name, the inner class name or the label.
     * 3. A part of the short class name or the label.
     * The first step that finds anything decides. One match is [Found.One]; more are [Found.Many].
     */
    fun find(items: List<ActivityItem>, query: String): Found {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return Found.None
        fun names(i: ActivityItem) = listOf(i.className, i.shortName, i.innerName, i.label).map { it.lowercase() }
        val exact = items.filter { i -> names(i).any { it == q } }
        // An exact class name wins over a label that is the same: two activities can share a label.
        val exactClass = exact.filter { i -> names(i).take(3).any { it == q } }
        val steps = listOf(
            exactClass.ifEmpty { exact },
            items.filter { i -> names(i).drop(1).any { it.startsWith(q) } },
            items.filter { i -> i.shortName.lowercase().contains(q) || i.label.lowercase().contains(q) },
        )
        val hit = steps.firstOrNull { it.isNotEmpty() } ?: return Found.None
        return if (hit.size == 1) Found.One(hit[0]) else Found.Many(hit)
    }

    /**
     * The shortest name that finds [item] again among [items]: the inner class name, the short
     * class name, or else the whole class name. Copied commands use it, so they are short.
     */
    fun nameFor(item: ActivityItem, items: List<ActivityItem>): String =
        listOf(item.innerName, item.shortName).firstOrNull { name ->
            !name.any(Char::isWhitespace) && (find(items, name) as? Found.One)?.item == item
        } ?: item.className

    /**
     * Splits `activity <app> [<name>]` into the app's name and the activity's name. App names can
     * have spaces (`Play Store`), so this tries the words in this order:
     * 1. the most words that are an app's whole name;
     * 2. all the words, when they start an app's name (then there is no activity name);
     * 3. the most words (not all) that start an app's name;
     * 4. the first word, matched in any way.
     *
     * [tierOf] gives how well a text matches its best app: 0 the whole name, 1 a prefix, 2 the start
     * of a word, higher is looser, null is no app. Returns null when no app matches.
     */
    fun split(words: List<String>, tierOf: (String) -> Int?): Pair<String, String>? {
        if (words.isEmpty()) return null
        fun cut(n: Int) = words.take(n).joinToString(" ") to words.drop(n).joinToString(" ")
        val tiers = (words.size downTo 1).associateWith { tierOf(words.take(it).joinToString(" ")) }
        (words.size downTo 1).firstOrNull { tiers[it] == 0 }?.let { return cut(it) }
        if ((tiers[words.size] ?: 9) <= 2) return cut(words.size)
        (words.size - 1 downTo 1).firstOrNull { (tiers[it] ?: 9) <= 2 }?.let { return cut(it) }
        return if (tiers[1] != null) cut(1) else null
    }
}

/**
 * `activity <app> [<name>]`: lists an app's activities that other apps may open, or opens one.
 * This reaches screens that have no icon: a settings page deep in an app, a hidden screen.
 *
 * Only apps in the personal profile. Android doesn't let a launcher read or start the
 * non-launcher activities of an app in another profile.
 */
class ActivityCommands(context: Context) : CommandGroup(context) {
    private val apps = AppRepository.get(context)

    fun run(args: List<String>) {
        if (args.any { it in OTHER_PROFILES }) return error("activity works only with apps in the personal profile")
        val words = args.filterNot { it.startsWith("-") }
        if (words.isEmpty()) return usage("activity")
        val (appName, name) = ActivityMatch.split(words) { tierOf(it) }
            ?: return error("No app matches '${words.joinToString(" ")}'")
        val app = apps.search(appName, ProfileKind.PERSONAL).firstOrNull() ?: return error("No app matches '$appName'")
        val items = activities(context, app)
        if (items.isEmpty()) return info("${app.label} has no activities that other apps may open")
        if (name.isEmpty()) {
            list(app, items)
            return info("${items.size} ${if (items.size == 1) "activity" else "activities"} in ${app.label} · tap one to open it")
        }
        when (val found = ActivityMatch.find(items, name)) {
            is ActivityMatch.Found.One -> open(app, found.item)
            is ActivityMatch.Found.Many -> {
                list(app, found.items)
                info("${found.items.size} activities in ${app.label} match '$name' · tap one to open it")
            }
            ActivityMatch.Found.None -> error("${app.label} has no activity matching '$name' · 'activity ${appToken(app)}' lists them")
        }
    }

    private fun tierOf(text: String): Int? = apps.searchRanked(text, ProfileKind.PERSONAL).firstOrNull()?.second

    /** One line for each activity: its name, with its class muted under it. A tap opens it. */
    private fun list(app: AppEntry, items: List<ActivityItem>) = items.forEach { item ->
        out("${item.label}\n${ActivityMatch.shownClass(app.pkg, item.className)}", lineCtx(app, item.className) + LogContext.ITEM)
    }

    private fun open(app: AppEntry, item: ActivityItem) {
        val failure = start(context, app.pkg, item.className, item.label) ?: return info("Opened ${item.label} · ${app.label}", lineCtx(app, item.className))
        error(failure)
    }

    private fun lineCtx(app: AppEntry, className: String) = LogContext.of(
        LogContext.PKG to app.pkg,
        LogContext.USER to app.profile.serial.toString(),
        LogContext.ACTIVITY to className,
    )

    companion object {
        /** Profile flags that `activity` can't serve. */
        private val OTHER_PROFILES = setOf("-w", "--work", "-s", "--private", "-c", "--clone")

        /**
         * The app's activities that any app may open: exported, enabled and with no permission.
         * Sorted by name. Empty when the app can't be read.
         *
         * Android 11+ shows a launcher only the packages its manifest `<queries>` asks for. Kolahal
         * asks for every app with a launcher icon, so each app that `activity` can name is visible.
         */
        fun activities(context: Context, app: AppEntry): List<ActivityItem> {
            val pm = context.packageManager
            val info = runCatching {
                if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(app.pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
                else @Suppress("DEPRECATION") pm.getPackageInfo(app.pkg, PackageManager.GET_ACTIVITIES)
            }.getOrNull() ?: return emptyList()
            return info.activities.orEmpty()
                .filter { it.exported && it.isEnabled && it.permission == null }
                .map { ActivityItem(it.name, ActivityMatch.shownLabel(label(pm, it), app.originalLabel, it.name)) }
                .distinctBy { it.className }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it: ActivityItem -> it.label }.thenBy { it.className })
        }

        private fun label(pm: PackageManager, info: ActivityInfo): String? = runCatching { info.loadLabel(pm).toString() }.getOrNull()

        /**
         * Starts the activity. Returns null when it started, or a line that says why it didn't.
         * Never throws: an app may remove or protect an activity at any time.
         */
        fun start(context: Context, pkg: String, className: String, label: String = className.substringAfterLast('.')): String? {
            val intent = Intent().setClassName(pkg, className).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                null
            } catch (_: ActivityNotFoundException) {
                "Can't open $label: the app no longer has it"
            } catch (_: SecurityException) {
                "Can't open $label: the app doesn't let other apps open it"
            } catch (e: RuntimeException) {
                "Can't open $label: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        /** Opens the activity of a log line (a tap). Only a failure is logged. */
        fun openFromLine(context: Context, pkg: String, className: String) {
            start(context, pkg, className)?.let { LogRepository.get(context).add(LogKind.ERROR, it) }
        }

        /**
         * How a command names [app]: its label, or its package when the label has quotes or would
         * find another app (or eat the activity's [name]). So `activity <app> <name>` finds it again.
         */
        fun appToken(context: Context, app: AppEntry, name: String = ""): String {
            val repo = AppRepository.get(context)
            val label = app.label
            if (label.isBlank() || label.any { it == '"' || it == '\'' }) return app.pkg
            val words = CommandParsing.tokenize(label) + CommandParsing.tokenize(name)
            val split = ActivityMatch.split(words) { repo.searchRanked(it, ProfileKind.PERSONAL).firstOrNull()?.second }
            val back = split?.let { repo.search(it.first, ProfileKind.PERSONAL).firstOrNull() }
            return if (back?.key == app.key && split.second == name) label else app.pkg
        }

        /** The command that opens this activity: `activity Settings WifiSettingsActivity`. For Copy, and later for aliases. */
        fun commandFor(context: Context, app: AppEntry, className: String): String {
            val items = activities(context, app)
            val item = items.firstOrNull { it.className == className } ?: ActivityItem(className, className.substringAfterLast('.'))
            val name = ActivityMatch.nameFor(item, items)
            return "activity ${appToken(context, app, name)} $name"
        }
    }

    private fun appToken(app: AppEntry) = appToken(context, app)
}
