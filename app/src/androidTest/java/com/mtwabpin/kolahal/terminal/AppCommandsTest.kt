package com.mtwabpin.kolahal.terminal

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.CommandRunner
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.Profile
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.shell
import com.mtwabpin.kolahal.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What `apps`, `open`, `pin`, `hide`, `rename`, `usage` and the other app commands print, and what they change. */
@RunWith(AndroidJUnit4::class)
class AppCommandsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val logs = LogRepository.get(context)
    private val config = Config.get(context)
    private val prefs = AppPrefs.get(context)
    private val apps = AppRepository.get(context)
    // The lines go after the test by the rule, also when a wait in tearDown times out.
    @get:Rule val logLines = LogLinesSince()
    @get:Rule val configReset = ConfigReset()
    private val pinnedBefore = prefs.pinned()

    private val commands = CommandRunner(context)

    @Before fun setUp() {
        apps.refresh()
        waitUntil("the app list") { apps.apps.value.isNotEmpty() }
        pinnedBefore.forEach { prefs.setPinned(it, false) }
    }

    @After fun tearDown() {
        // `open` and `info` leave Settings or its App info page in front.
        shell("am force-stop $SETTINGS")
        prefs.pinned().forEach { prefs.setPinned(it, false) }
        pinnedBefore.forEach { prefs.setPinned(it, true) }
        apps.apps.value.firstOrNull { it.pkg == SETTINGS }?.let { s ->
            prefs.setHidden(s.key, false)
            prefs.setLabel(s.key, null)
            apps.refresh()
            waitUntil("Settings as it was") { apps.apps.value.any { it.key == s.key && !it.hidden && it.label == it.originalLabel } }
        }
    }

    /** The personal Settings app, which every test phone has. */
    private fun settings(): AppEntry {
        val s = apps.apps.value.firstOrNull { it.pkg == SETTINGS && it.profile.kind == ProfileKind.PERSONAL }
        assumeTrue("Settings is installed", s != null)
        assumeTrue("Settings is called Settings", s!!.originalLabel == "Settings")
        return s
    }

    /** Runs [line] and returns the lines it printed: the ones marked as its output. */
    private fun lines(line: String) = commands.ownLines(line)

    private fun run(line: String): List<String> = lines(line).map { it.text }

    /** The packages a grid line's chips stand for, in order. */
    private fun gridPackages(e: LogEntry): List<String> =
        (Json.parse(e.ctx.values.getValue(LogContext.APPS)) as List<*>).map { (it as Map<*, *>)["pkg"].toString() }

    /** `apps` is one grid of every visible app and a count; `-l` is a line per app, each one tappable. */
    @Test fun appsListsEveryVisibleApp() {
        val visible = apps.apps.value.filter { !it.hidden }
        assumeTrue("one profile: with more, each has its own heading", visible.map { it.profile.kind }.distinct() == listOf(ProfileKind.PERSONAL))
        val listed = lines("apps")
        assertEquals(2, listed.size)
        assertEquals(visible.joinToString(", ") { it.label }, listed[0].text)
        assertEquals(visible.map { it.pkg }, gridPackages(listed[0]))
        assertEquals(visible.joinToString("\n") { it.label }, listed[0].ctx.copy)
        assertEquals("${visible.size} apps · personal [P]", listed[1].text)
        assertEquals(run("apps"), run("ls"))
        assertEquals(run("apps"), run("apps personal"))
        assertEquals(run("apps"), run("apps -p"))

        val long = lines("apps -l")
        assertEquals(visible.map { it.label } + "${visible.size} apps · personal [P]", long.map { it.text })
        val s = long.single { it.ctx.pkg == SETTINGS }
        assertEquals(settings().component.className, s.ctx.cls)
        assertEquals(settings().profile.serial, s.ctx.userSerial)
    }

    /** A profile the phone doesn't have is named in what `apps`, `work` and `private` say. */
    @Test fun aProfileThePhoneLacksIsSaidToBeMissing() {
        val kinds = apps.profiles.value.map { it.kind }.toSet()
        assumeTrue("no work, private or clone profile", kinds == setOf(ProfileKind.PERSONAL))
        assertEquals(listOf("No apps in work profile"), run("apps work"))
        assertEquals(listOf("No apps in work profile"), run("apps -w"))
        assertEquals(listOf("No apps in private profile"), run("apps private"))
        assertEquals(listOf("No apps in private profile"), run("apps --private"))
        assertEquals(listOf("No apps in clone profile"), run("apps -c"))
        assertEquals(listOf("No work profile on this device"), run("work"))
        assertEquals(listOf("No work profile on this device"), run("work off"))
        assertEquals(listOf("No private profile on this device"), run("private status"))
        assertEquals(listOf("No app matches 'Settings'"), run("open Settings -w"))
    }

    /** A hidden app is out of `apps` and `open`, in `apps hidden` and `apps -a`, until `unhide`. */
    @Test fun hideTakesAnAppOutOfListsUntilUnhide() {
        val s = settings()
        val hidden = lines("hide Settings").single()
        assertEquals("Hidden Settings", hidden.text)
        assertEquals(SETTINGS, hidden.ctx.pkg)
        assertEquals(s.component.className, hidden.ctx.cls)
        assertTrue(prefs.isHidden(s.key))
        waitUntil("the list without it") { apps.apps.value.single { it.key == s.key }.hidden }

        assertFalse(gridPackages(lines("apps").first()).contains(SETTINGS))
        val onlyHidden = apps.apps.value.filter { it.hidden }
        val hiddenListed = run("apps hidden")
        assertEquals(onlyHidden.joinToString(", ") { it.label }, hiddenListed.first())
        val count = if (onlyHidden.size == 1) "1 app" else "${onlyHidden.size} apps"
        assertEquals(listOf("$count · personal [P]"), hiddenListed.drop(1))
        assertTrue(gridPackages(lines("apps -a").first()).contains(SETTINGS))
        assertEquals(listOf("No app matches 'Settings'"), run("open Settings"))

        assertEquals(listOf("Unhidden Settings"), run("unhide Settings"))
        assertFalse(prefs.isHidden(s.key))
        waitUntil("the list with it again") { !apps.apps.value.single { it.key == s.key }.hidden }
        assertTrue(gridPackages(lines("apps").first()).contains(SETTINGS))
    }

    /** `rename` gives an app the name you type, `rename <app> =` gives it its own name back, and a line without `=` says how. */
    @Test fun renameNamesAnAppAndAnEmptyNameRestoresIt() {
        val s = settings()
        val renamed = lines("rename Settings = Llat Panel").single()
        assertEquals("Settings → Llat Panel", renamed.text)
        assertEquals(SETTINGS, renamed.ctx.pkg)
        assertEquals("Llat Panel", prefs.label(s.key))
        waitUntil("the new name") { apps.apps.value.single { it.key == s.key }.label == "Llat Panel" }
        assertTrue(run("apps -l").contains("Llat Panel"))

        assertEquals(listOf("Settings → Settings"), run("rename Llat Panel ="))
        assertEquals(null, prefs.label(s.key))
        waitUntil("its own name") { apps.apps.value.single { it.key == s.key }.label == "Settings" }

        val usage = listOf("Usage: rename <app> [-w] = <new name>")
        assertEquals(usage, run("rename Settings"))
        assertEquals(usage, run("rename = Llat"))
        assertEquals(listOf("No app matches 'zzqqxx'"), run("rename zzqqxx = Llat"))
    }

    /** Pinning says where the app now shows, as the settings that place it are on or off. */
    @Test fun pinSaysWhereAPinnedAppShows() {
        val s = settings()
        assertEquals(listOf("Pinned Settings — pinned apps come first on an empty prompt with config set suggestions.idle on"), run("pin Settings"))
        assertTrue(prefs.isPinned(s.key))
        assertEquals(listOf("Unpinned Settings"), run("unpin Settings"))
        assertFalse(prefs.isPinned(s.key))

        config.set(Keys.SUGGEST_IDLE, "on")
        assertEquals(listOf("Pinned Settings — it comes first in suggestions on an empty prompt"), run("fav Settings"))
        config.set(Keys.SELECTOR_RAIL, "on")
        assertEquals(
            listOf("Pinned Settings — it comes first in suggestions on an empty prompt, and it is a ★ favourite in the app selector"),
            run("pin Settings"),
        )
        config.reset(Keys.SUGGEST_IDLE)
        assertEquals(listOf("Pinned Settings — it is a ★ favourite in the app selector"), run("pin Settings"))
        assertEquals(listOf(s.key), prefs.pinned())
        assertEquals(listOf("Unpinned Settings"), run("unfav Settings"))
        assertEquals(listOf("No app matches 'zzqqxx'"), run("pin zzqqxx"))
    }

    /**
     * `pins` is the pinned apps as one grid, in the order they were pinned, or how to pin one. Settings
     * is pinned first and an app whose name sorts before it second, so a list by name would differ.
     */
    @Test fun pinsListsThePinnedAppsInOrder() {
        val s = settings()
        assertEquals(listOf("No pinned apps — pin <app>, or long-press an app → Pin"), run("pins"))
        val other = apps.apps.value.firstOrNull {
            !it.hidden && it.pkg != SETTINGS && it.profile.kind == ProfileKind.PERSONAL && it.label.lowercase() < "settings"
        }
        assumeTrue("an app whose name sorts before Settings", other != null)
        run("pin Settings")
        prefs.setPinned(other!!.key, true)
        val listed = lines("pins")
        assertEquals("Settings, ${other.label}", listed[0].text)
        assertEquals(listOf(SETTINGS, other.pkg), gridPackages(listed[0]))
        assertEquals("2 pinned · unpin <app> to remove", listed[1].text)
        assertEquals(run("pins"), run("pin"))
        assertEquals(run("pins"), run("favs"))
        assertEquals(listOf(s.key, other.key), prefs.pinned())
    }

    /**
     * `open` brings the app to the front, counts the launch and says so; `launch` says it on the same
     * line, however often; `info` says it opened the app's page.
     */
    @Test fun openLaunchesAnAppAndInfoOpensItsPage() {
        val s = settings()
        val usage = context.getSharedPreferences(AppPrefs.USAGE_FILE, Context.MODE_PRIVATE)
        val launchesBefore = usage.getString("launch:${s.key}", null)
        try {
            shell("am force-stop $SETTINGS")
            val score = prefs.frecency(s.key)
            val launched = lines("open Settings").single()
            assertEquals("Launched Settings", launched.text)
            assertEquals(LogKind.INFO, launched.kind)
            assertEquals(SETTINGS, launched.ctx.pkg)
            assertTrue("$score, then ${prefs.frecency(s.key)}", prefs.frecency(s.key) > score + 0.5)
            val resumed = Regex("ResumedActivity.{0,120}${Regex.escape(SETTINGS)}/")
            waitUntil("Settings in front", ms = 10_000) { resumed.containsMatchIn(shell("dumpsys activity activities")) }
            assertEquals(listOf("Launched Settings"), run("launch Settings"))
            assertEquals(1, logs.recent(400, afterId = logLines.start).count { it.text == "Launched Settings" })
            assertEquals(listOf("App info: Settings"), run("info Settings"))
            assertEquals(listOf("No app matches 'zzqqxx'"), run("open zzqqxx"))
            assertEquals(listOf("No app matches 'zzqqxx'"), run("launch zzqqxx -p"))
            assertEquals(listOf("No app matches 'zzqqxx'"), run("info zzqqxx"))
        } finally {
            usage.edit().putString("launch:${s.key}", launchesBefore).commit()
        }
    }

    /** `open app:shortcut` names the shortcut it couldn't find, but an app whose own name has a colon opens as itself. */
    @Test fun aNameWithAColonIsAnAppBeforeAShortcut() {
        val s = settings()
        val usage = context.getSharedPreferences(AppPrefs.USAGE_FILE, Context.MODE_PRIVATE)
        val launchesBefore = usage.getString("launch:${s.key}", null)
        try {
            assertEquals(listOf("Settings has no shortcut matching 'zzqq' — 'shortcuts Settings'"), run("open Settings:zzqq"))
            assertEquals(listOf("No app matches 'zzqqxx'"), run("open zzqqxx:thing"))
            run("rename Settings = Llat: Panel")
            waitUntil("the new name") { apps.apps.value.single { it.key == s.key }.label == "Llat: Panel" }
            assertEquals(listOf("Launched Llat: Panel"), run("open Llat: Panel"))
        } finally {
            usage.edit().putString("launch:${s.key}", launchesBefore).commit()
        }
    }

    /** A paused profile's app offers to resume it, with the right flag to tap; an app that can't start says why. */
    @Test fun anAppThatCantOpenSaysWhatToDo() {
        val me = Process.myUserHandle()
        val component = ComponentName("com.example.kolahal.llat", "com.example.kolahal.llat.Main")
        fun launch(app: AppEntry): LogEntry {
            val before = logs.maxId()
            commands.processor.launch(app)
            return logs.recent(50).single { it.id > before && app.label in it.text }
        }
        val work = launch(AppEntry("Llat Work", component, Profile(me, 4242, ProfileKind.WORK, quiet = true)))
        assertEquals(LogKind.INFO, work.kind)
        assertEquals("Work profile is paused — tap to resume it and open Llat Work", work.text)
        assertEquals("open Llat Work -w --resume", work.ctx.values[LogContext.RUN])
        val private = launch(AppEntry("Llat Private", component, Profile(me, 4243, ProfileKind.PRIVATE, quiet = true)))
        assertEquals("Private profile is paused — tap to resume it and open Llat Private", private.text)
        assertEquals("open Llat Private -s --resume", private.ctx.values[LogContext.RUN])

        val personal = apps.profiles.value.first { it.kind == ProfileKind.PERSONAL }
        val gone = launch(AppEntry("Llat Gone", component, personal))
        assertEquals(LogKind.ERROR, gone.kind)
        assertTrue(gone.text, gone.text.startsWith("Can't launch Llat Gone: "))
    }

    /** `shortcuts` lists an app's shortcuts, or says Kolahal must be the default launcher to read them. */
    @Test fun shortcutsNeedTheDefaultLauncher() {
        settings()
        val listed = lines("shortcuts Settings")
        if (!SetupChecks.isDefaultLauncher(context)) {
            assertEquals(listOf("Shortcuts need Kolahal as the default launcher — run 'home'"), listed.map { it.text })
        } else if (listed.singleOrNull()?.text != "Settings has no shortcuts") {
            assertTrue(listed.toString(), listed.all { it.text.startsWith("Settings › ") && it.ctx.values[LogContext.SHORTCUT] != null })
        }
        assertEquals(listOf("No app matches 'zzqqxx'"), run("shortcuts zzqqxx"))
    }

    /** `uninstall` hands the app to Android's uninstall screen and prints nothing itself. */
    @Test fun uninstallAsksAndroidToRemoveTheApp() {
        settings()
        val opened = mutableListOf<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_DELETE) return null
                opened += intent
                return Instrumentation.ActivityResult(0, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            assertEquals(emptyList<String>(), run("uninstall Settings"))
            assertEquals("package:$SETTINGS", opened.single().dataString)
            assertEquals(listOf("No app matches 'zzqqxx'"), run("uninstall zzqqxx"))
            assertEquals(1, opened.size)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    /** Runs [block] with usage access set to [mode], then puts it back as it was. */
    private fun withUsageAccess(mode: String, block: () -> Unit) {
        val op = "appops get ${context.packageName} GET_USAGE_STATS"
        val accessBefore = Regex("GET_USAGE_STATS: (\\w+)").find(shell(op))?.groupValues?.get(1) ?: "default"
        try {
            shell("appops set ${context.packageName} GET_USAGE_STATS $mode")
            block()
        } finally {
            shell("appops set ${context.packageName} GET_USAGE_STATS $accessBefore")
        }
    }

    /** Without usage access `usage` says to allow it, with a tap that opens the page. */
    @Test fun usageNeedsAccess() = withUsageAccess("ignore") {
        val denied = lines("usage").single()
        assertEquals("Screen time needs usage access — tap to allow it", denied.text)
        assertEquals(LogKind.ERROR, denied.kind)
        assertEquals("usage-access", denied.ctx.values[LogContext.RUN])
    }

    /**
     * With usage access, screen time per app, longest first, with a bar each, for a day or up to 30;
     * with none recorded it says so. A fresh emulator often has none: then the test shows as skipped.
     */
    @Test fun usageShowsScreenTime() = withUsageAccess("allow") {
        val duration = "(\\d+h )?\\d+m"
        var listed = false
        for ((typed, said) in listOf("usage" to "today", "screentime 7" to "last 7 days", "usage 99" to "last 30 days")) {
            val shown = lines(typed)
            val first = shown.first().text
            if (first.startsWith("No app usage recorded")) {
                assertEquals(typed, 1, shown.size)
                assertEquals(typed, if (said == "today") "No app usage recorded today" else "No app usage recorded in ${said.removePrefix("last ")}", first)
                continue
            }
            listed = true
            assertTrue(first, Regex("Screen time $said: $duration").matches(first))
            val perApp = shown.drop(1)
            assertTrue(typed, perApp.size in 1..12)
            assertTrue(perApp.toString(), perApp.all { Regex(duration).matches(it.ctx.values[LogContext.DESC].orEmpty()) })
            assertTrue(perApp.toString(), perApp.none { it.ctx.pkg == context.packageName })
            // A drawn bar on one scale (LogContext.BAR, "0,share,"): the most-used app fills its bar.
            val bars = perApp.map { it.ctx.values.getValue(LogContext.BAR).split(",")[1].toDouble() }
            assertEquals(typed, 1.0, bars.first(), 0.0)
            assertEquals(typed, bars.sortedDescending(), bars)
        }
        assumeTrue("no app usage recorded in 30 days, so no list to check", listed)
    }

    /**
     * `installs` lists the recorded installs and updates in the order the history keeps them (newest
     * first, as the test writes them: the recording isn't run here), each with its time, or says none
     * are recorded; `updates 1` only the first.
     */
    @Test fun installsListsWhatChangedNewestFirst() {
        val store = KvStore(context, "packages")
        val historyBefore = store.get("history")
        try {
            store.set("history", null)
            assertEquals(listOf("No app installs, updates or uninstalls recorded yet"), run("installs"))
            val newer = 1_767_268_800_000L
            val older = newer - 86_400_000L
            store.set(
                "history",
                Json.stringify(
                    listOf(
                        mapOf("t" to newer, "text" to "Updated Llat One ↻\ncom.example.one\n1.0 → 1.1"),
                        mapOf("t" to older, "text" to "Installed Llat Two +\ncom.example.two\n2.0"),
                    ),
                ),
            )
            val fmt = java.text.SimpleDateFormat("EEE d MMM HH:mm", java.util.Locale.getDefault())
            val listed = lines("installs")
            assertEquals(listOf("Updated Llat One ↻\ncom.example.one\n1.0 → 1.1", "Installed Llat Two +\ncom.example.two\n2.0"), listed.map { it.text })
            assertEquals(listOf(fmt.format(java.util.Date(newer)), fmt.format(java.util.Date(older))), listed.map { it.ctx.values[LogContext.DESC] })
            assertEquals(listOf("Updated Llat One ↻\ncom.example.one\n1.0 → 1.1"), run("updates 1"))
        } finally {
            store.set("history", historyBefore)
        }
    }

    private companion object {
        const val SETTINGS = "com.android.settings"
    }
}
