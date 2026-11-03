package com.mtwabpin.kolahal.terminal

import android.app.Instrumentation
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Process
import android.os.UserManager
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.feeds.FeedRepository
import com.mtwabpin.kolahal.feeds.FeedWorker
import com.mtwabpin.kolahal.notifications.KolahalNotificationListener
import com.mtwabpin.kolahal.notifications.NotifPrefs
import com.mtwabpin.kolahal.notifications.Notif
import com.mtwabpin.kolahal.notifications.NotificationStore
import com.mtwabpin.kolahal.productivity.Agenda
import com.mtwabpin.kolahal.script.Json
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** What a tap and a long-press on a log line do, as the line's context says. */
@RunWith(AndroidJUnit4::class)
class LogLineActionsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val logs = LogRepository.get(context)
    private val prefs = AppPrefs.get(context)
    private val apps = AppRepository.get(context)
    private val scripts = ScriptRepository.get(context)
    private val feeds = FeedRepository.get(context)
    private val notifPrefs = NotifPrefs.get(context)
    private val start = logs.maxId()
    private val pinnedBefore = prefs.pinned()
    private val mutedBefore = notifPrefs.muted.value
    private val storeBefore = NotificationStore.items.value
    private val intentsBefore = storeBefore.associate { it.key to NotificationStore.intent(it.key) }
    private val usage = context.getSharedPreferences(AppPrefs.USAGE_FILE, Context.MODE_PRIVATE)
    private val launchesBefore = usage.all.filterKeys { it.startsWith("launch:") }

    private val ui = object : TerminalUi {
        override fun pickScriptFolder() = Unit
        override fun requestFontImport() = Unit
        override fun openScreen(screen: Screen) = Unit
        override fun requestContactsPermission() = Unit
        override fun openEditor(script: Script) = Unit
        override fun askSecret(script: Script, name: String) = Unit
        override fun requestNotificationPermission() = Unit
        override fun clearScreen() = Unit
    }
    private val processor = CommandProcessor(context, ui)

    /** Home's side of a tap, written down: the prompt, what it asked of the screen, what it opened. */
    private class Home : LogLineActions.Host {
        var lines: List<LogEntry> = emptyList()
        var scriptList: List<Script> = emptyList()
        var instant = false
        var prompt: String? = null
        var submits = 0
        var collapses = 0
        var filter: LogFilter? = null
        val events = mutableListOf<UiEvent>()
        val commands = mutableListOf<String>()
        val intents = mutableListOf<Intent>()
        val editors = mutableListOf<Pair<String, Int?>>()

        override val entries get() = lines
        override val scripts get() = scriptList
        override fun io(block: suspend () -> Unit) = runBlocking { block() }
        override fun setText(text: String) { prompt = text }
        override fun submit() { submits++ }
        override fun emit(event: UiEvent) { events += event }
        override fun collapseHeader() { collapses++ }
        override fun runsOnTap(line: String) = instant
        override fun runCommand(cmd: String) { commands += cmd }
        override fun openEditorAt(script: Script, line: Int?) { editors += script.name to line }
        override fun startActivity(intent: Intent) { intents += intent }
        override fun filterLog(filter: LogFilter?) { this.filter = filter }

        fun forget() {
            prompt = null
            submits = 0
            collapses = 0
            filter = null
            events.clear()
            commands.clear()
            intents.clear()
            editors.clear()
        }
    }
    private val home = Home()
    private val actions = LogLineActions(context, processor, home)

    @Before fun setUp() {
        apps.refresh()
        waitUntil("the app list") { apps.apps.value.isNotEmpty() }
    }

    @After fun tearDown() {
        try {
            // Lines first: a wait below that times out mustn't leave them.
            while (true) {
                val mine = logs.recent(400, afterId = start)
                if (mine.isEmpty()) break
                mine.forEach { logs.delete(it.id) }
            }
        } finally {
            restore()
        }
    }

    private fun restore() {
        // A tap on an app's line leaves Settings in front.
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("am force-stop $SETTINGS")).use { it.readBytes() }
        prefs.setAlias(ALIAS, null)
        scripts.byName(SCRIPT)?.let { scripts.delete(it.id) }
        feeds.feeds().filter { it.url.startsWith(BASE) }.forEach { feeds.remove(it.id) }
        (notifPrefs.muted.value - mutedBefore).forEach(notifPrefs::unmute)
        NotificationStore.clear()
        storeBefore.asReversed().forEach { NotificationStore.put(it, intentsBefore[it.key]) }
        prefs.pinned().forEach { prefs.setPinned(it, false) }
        pinnedBefore.forEach { prefs.setPinned(it, true) }
        val launches = usage.edit()
        usage.all.keys.filter { it.startsWith("launch:") && it !in launchesBefore }.forEach { launches.remove(it) }
        launchesBefore.forEach { (k, v) -> launches.putString(k, v as String) }
        launches.commit()
        apps.apps.value.firstOrNull { it.pkg == SETTINGS }?.let { s ->
            prefs.setHidden(s.key, false)
            apps.refresh()
            waitUntil("Settings shown again") { apps.apps.value.any { it.key == s.key && !it.hidden } }
        }
    }

    private fun waitUntil(what: String, ms: Long = 5_000, check: () -> Boolean) {
        val until = System.currentTimeMillis() + ms
        while (!check() && System.currentTimeMillis() < until) Thread.sleep(50)
        assertTrue(what, check())
    }

    /** Logs a line and returns it as the log reads it back. */
    private fun line(kind: LogKind, text: String, vararg ctx: Pair<String, String?>, source: String? = null): LogEntry {
        val id = logs.add(kind, text, source, LogContext.of(*ctx))
        return logs.recent(400, afterId = id - 1).single { it.id == id }
    }

    private fun labels(e: LogEntry) = actions.actionsFor(e).map { it.label }

    private fun action(e: LogEntry, label: String) = actions.actionsFor(e).single { it.label == label }

    /** The personal Settings app, which every test phone has. */
    private fun settings(): AppEntry {
        val s = apps.apps.value.firstOrNull { it.pkg == SETTINGS && it.profile.kind == ProfileKind.PERSONAL }
        assumeTrue("Settings is installed", s != null)
        assumeTrue("Settings is called Settings", s!!.label == "Settings")
        return s
    }

    private fun appCtx(app: AppEntry) = arrayOf(
        LogContext.PKG to app.pkg, LogContext.CLS to app.component.className, LogContext.USER to app.profile.serial.toString(),
    )

    private val clipboard = context.getSystemService(ClipboardManager::class.java)

    /** Runs [block] with a window of Kolahal's in front: Android lets only the app in front read the clipboard. */
    private fun <T> inFront(block: () -> T): T = ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
        waitUntil("a window of ours in front") {
            var focused = false
            scenario.onActivity { focused = it.hasWindowFocus() }
            focused
        }
        block()
    }

    private fun clip(): ClipData = clipboard.primaryClip!!

    private fun clipText() = clip().getItemAt(0).text.toString()

    private fun clipSensitive() = clip().description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true

    /** Catches the uninstall screen, so none really opens. */
    private fun <T> catchingUninstall(opened: MutableList<Intent>, block: () -> T): T {
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_DELETE) return null
                opened += intent
                return Instrumentation.ActivityResult(0, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            return block()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    /**
     * A tap on a hint runs its command, as written: one that names an alias doesn't run the alias,
     * which typed would. On a command it fills the prompt, or runs it where taps run commands.
     */
    @Test fun tappingRunsOrRecallsTheLinesCommand() {
        val hint = line(LogKind.INFO, "Tap to say hi", LogContext.RUN to "echo llat hi")
        actions.onTap(hint)
        assertEquals(listOf(UiEvent.ScrollToBottom), home.events)
        assertNull(home.prompt)
        assertTrue(logs.recent(50, afterId = hint.id).any { it.kind == LogKind.OUTPUT && it.text == "llat hi" })

        prefs.setAlias(ALIAS, "echo llat expanded")
        val typedAlias = logs.maxId()
        processor.run(ALIAS)
        assertTrue("the alias, typed", logs.recent(50, afterId = typedAlias).any { it.text == "llat expanded" })
        val aliasHint = line(LogKind.INFO, "Tap for the alias", LogContext.RUN to ALIAS)
        actions.onTap(aliasHint)
        val after = logs.recent(50, afterId = aliasHint.id)
        assertTrue(after.joinToString { it.text }, after.isNotEmpty() && after.none { it.text == "llat expanded" })

        home.forget()
        val typed = line(LogKind.COMMAND, "echo llat again", LogContext.COMMAND to "echo llat again")
        actions.onTap(typed)
        assertEquals("echo llat again", home.prompt)
        assertEquals(listOf(UiEvent.FocusPrompt), home.events)
        assertTrue(logs.recent(50, afterId = typed.id).isEmpty())

        home.forget()
        home.instant = true
        actions.onTap(typed)
        assertNull(home.prompt)
        assertEquals(1, home.collapses)
        assertEquals(listOf(UiEvent.ScrollToBottom), home.events)
        assertTrue(logs.recent(50, afterId = typed.id).any { it.text == "llat again" })

        // A line that names a command to read more fills the prompt with it.
        home.forget()
        home.instant = false
        actions.onTap(line(LogKind.SCRIPT, "+3 more", LogContext.COMMAND to "feed read 1", source = "Llat Feed"))
        assertEquals("feed read 1", home.prompt)
    }

    /** A tap opens what the line is about: a reminder's cancel in the prompt, an event, a number to dial, a link, which marks its feed item read. */
    @Test fun tappingOpensWhatTheLineIsAbout() {
        actions.onTap(line(LogKind.INFO, "Llat tea at 5", LogContext.REMINDER to "7"))
        assertEquals("remind rm #7", home.prompt)
        assertEquals(listOf(UiEvent.FocusPrompt), home.events)

        home.forget()
        actions.onTap(line(LogKind.OUTPUT, "Llat meeting", LogContext.EVENT to "42", LogContext.BEGIN to "1000", LogContext.END to "2000"))
        val event = home.intents.single()
        assertEquals(Intent.ACTION_VIEW, event.action)
        assertEquals(Agenda.eventUri(42), event.data)
        assertEquals(1000L, event.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
        assertEquals(2000L, event.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0))

        home.forget()
        actions.onTap(line(LogKind.OUTPUT, "Llat Ann", LogContext.PHONE to "5550100"))
        assertEquals(Intent.ACTION_DIAL, home.intents.single().action)
        assertEquals("tel:5550100", home.intents.single().dataString)

        home.forget()
        val feed = feeds.add("${BASE}llat")
        val item = feeds.insertItems(feed.id, listOf(FeedRepository.NewItem("llat-post", "Llat post", "${BASE}post", null, null))).single()
        actions.onTap(line(LogKind.SCRIPT, "Llat post", LogContext.URL to item.link, FeedWorker.CTX_ITEM to item.id.toString(), source = "Llat Feed"))
        assertEquals(Intent.ACTION_VIEW, home.intents.single().action)
        assertEquals("${BASE}post", home.intents.single().dataString)
        assertEquals(0, feeds.unreadCount(feed.id))

        // A line with nothing to it: a tap does nothing.
        home.forget()
        val before = logs.maxId()
        actions.onTap(line(LogKind.OUTPUT, "Llat plain"))
        assertTrue(home.intents.isEmpty() && home.events.isEmpty() && home.prompt == null)
        assertEquals(1, logs.recent(50, afterId = before).size)
    }

    /** A tap on an app's line launches it; on a shortcut that can't start, it says so. */
    @Test fun tappingAnAppLineLaunchesIt() {
        val s = settings()
        val before = logs.maxId()
        actions.onTap(line(LogKind.INFO, "Llat app", *appCtx(s)))
        assertTrue(logs.recent(50, afterId = before).any { it.text == "Launched Settings" && it.ctx.pkg == SETTINGS })

        val shortcut = line(LogKind.OUTPUT, "Settings › Llat", *appCtx(s), LogContext.SHORTCUT to "llat-none")
        actions.onTap(shortcut)
        val failed = logs.recent(50, afterId = shortcut.id).single()
        assertEquals(LogKind.ERROR, failed.kind)
        assertTrue(failed.text, failed.text.startsWith("Shortcut unavailable: "))
    }

    /** A tap on a notification runs the notification's own tap action, not the app. */
    @Test fun tappingANotificationRunsItsOwnTapAction() {
        // With access, Android is asked for the notification, and this one isn't there. A listener
        // still bound just after access went can't ask, so the tap kept in the store is used.
        val listener = ComponentName(context, KolahalNotificationListener::class.java)
        assumeTrue("notification access is off", !context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(listener))
        val s = settings()
        val action = "com.mtwabpin.kolahal.test.LLAT_TAP"
        val tapped = CountDownLatch(2)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = tapped.countDown()
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            val pi = PendingIntent.getBroadcast(context, 0, Intent(action).setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
            val serial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())
            NotificationStore.put(Notif("0|llat|tap", SETTINGS, serial, "Settings", "Llat ping", null, System.currentTimeMillis()), pi)
            val ping = line(
                LogKind.SCRIPT, "Llat ping", *appCtx(s), KolahalNotificationListener.CTX_KEY to "0|llat|tap", source = "Settings",
            )
            actions.onTap(ping)
            action(ping, "Open notification").run()
            assertTrue("both taps reached the notification", tapped.await(5, TimeUnit.SECONDS))
            assertTrue(logs.recent(50, afterId = ping.id).none { it.text.startsWith("Launched") })
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    /** A tap on a note copies it; a swipe copies what a line shows, and a call's line as sensitive. */
    @Test fun copyingPutsWhatTheLineSaysOnTheClipboard() {
        inFront {
            actions.onTap(line(LogKind.OUTPUT, "#5 Llat idea · Tue", LogContext.NOTE to "5", LogContext.COPY to "Llat idea"))
            assertEquals("Llat idea", clipText())
            actions.swipeCopy(line(LogKind.SCRIPT, StyledText.wrap(StyledText.Style.BOLD, "Llat title"), LogContext.BODY to "Llat body", source = "llatx"))
            assertEquals("Llat title\nLlat body", clipText())
            assertFalse(clipSensitive())
            actions.swipeCopy(line(LogKind.INFO, "Llat missed call 5550100", source = com.mtwabpin.kolahal.calls.CallEvents.SOURCE))
            assertEquals("Llat missed call 5550100", clipText())
            assertTrue(clipSensitive())
        }
    }

    /** A command's long-press runs it again, edits it, copies its output or it, shows only commands, and deletes it with its output. */
    @Test fun aCommandsLongPressOffersItsCommandAndOutput() {
        val command = line(LogKind.COMMAND, "echo llat block", LogContext.COMMAND to "echo llat block")
        val one = line(LogKind.OUTPUT, "llat one", LogContext.FROM to command.id.toString())
        val two = line(LogKind.OUTPUT, "llat two", LogContext.FROM to command.id.toString())
        home.lines = listOf(command, one, two)
        assertEquals(
            listOf("Run again", "Edit in prompt", "Copy output", "Show only commands", "Copy text", "Delete command and output", "Delete line"),
            labels(command),
        )
        assertEquals(listOf(false, false, false, false, false, true, true), actions.actionsFor(command).map { it.destructive })

        action(command, "Run again").run()
        assertEquals("echo llat block", home.prompt)
        assertEquals(1, home.submits)
        home.forget()
        action(command, "Edit in prompt").run()
        assertEquals("echo llat block", home.prompt)
        assertEquals(listOf(UiEvent.FocusPrompt), home.events)
        assertEquals(0, home.submits)
        action(command, "Show only commands").run()
        assertEquals(LogFilter.OfKind(LogFilter.Kind.COMMANDS), home.filter)
        inFront {
            action(command, "Copy output").run()
            assertEquals("llat one\nllat two", clipText())
            action(command, "Copy text").run()
            assertEquals("echo llat block", clipText())
        }

        action(command, "Delete command and output").run()
        val ids = setOf(command.id, one.id, two.id)
        assertTrue(logs.recent(400, afterId = start).none { it.id in ids })
    }

    /** An output line's long-press offers the command that printed it, to run again or edit, and deletes just that line. */
    @Test fun anOutputLinesLongPressOffersTheCommandThatPrintedIt() {
        val command = line(LogKind.COMMAND, "echo llat x", LogContext.COMMAND to "echo llat x")
        val output = line(LogKind.OUTPUT, "llat x", LogContext.FROM to command.id.toString())
        home.lines = listOf(command, output)
        assertEquals(
            listOf("Run 'echo llat x' again", "Edit 'echo llat x' in prompt", "Show only commands", "Copy text", "Delete line"),
            labels(output),
        )
        action(output, "Run 'echo llat x' again").run()
        assertEquals("echo llat x", home.prompt)
        assertEquals(1, home.submits)
        home.forget()
        action(output, "Edit 'echo llat x' in prompt").run()
        assertEquals("echo llat x", home.prompt)
        assertEquals(listOf(UiEvent.FocusPrompt), home.events)
        action(output, "Delete line").run()
        val left = logs.recent(400, afterId = start).map { it.id }
        assertTrue(command.id in left && output.id !in left)
    }

    /** An app line's long-press launches, pins, hides, copies or uninstalls it; each label says which way it toggles. */
    @Test fun anAppLinesLongPressOffersTheAppsActions() {
        val s = settings()
        prefs.setPinned(s.key, false)
        val launched = line(LogKind.INFO, "Llat launched Settings", *appCtx(s))
        fun shown() = labels(launched).filterNot { it.startsWith("Settings › ") }
        assertEquals(
            listOf("Launch Settings [P]", "App info", "Copy package name", "Pin to favourites", "Hide from lists", "Show only commands", "Copy text", "Uninstall", "Delete line"),
            shown(),
        )
        assertEquals(listOf("Uninstall", "Delete line"), actions.actionsFor(launched).filter { it.destructive }.map { it.label })

        action(launched, "Pin to favourites").run()
        assertTrue(prefs.isPinned(s.key))
        action(launched, "Unpin from favourites").run()
        assertFalse(prefs.isPinned(s.key))

        action(launched, "Hide from lists").run()
        assertTrue(prefs.isHidden(s.key))
        waitUntil("the list without it") { apps.apps.value.single { it.key == s.key }.hidden }
        action(launched, "Unhide").run()
        assertFalse(prefs.isHidden(s.key))
        waitUntil("the list with it") { !apps.apps.value.single { it.key == s.key }.hidden }

        inFront {
            action(launched, "Copy package name").run()
            assertEquals(SETTINGS, clipText())
        }
        val opened = mutableListOf<Intent>()
        catchingUninstall(opened) { action(launched, "Uninstall").run() }
        assertEquals("package:$SETTINGS", opened.single().dataString)
    }

    /**
     * A notification's long-press offers to open, mute or dismiss it; muting it, showing only that
     * app's lines and copying it (as sensitive) are run here, the others only listed.
     */
    @Test fun aNotificationsLongPressOffersOpenMuteAndDismiss() {
        val s = settings()
        prefs.setPinned(s.key, false)
        val n = line(
            LogKind.SCRIPT, "Llat title — Llat body", *appCtx(s), LogContext.COPY to "Llat title — Llat body",
            KolahalNotificationListener.CTX_KEY to "0|llat|lp", source = "Settings",
        )
        assertEquals(
            listOf(
                "Launch Settings [P]", "App info", "Copy package name", "Pin to favourites", "Hide from lists",
                "Open notification", "Mute this app's notifications", "Show only Settings", "Copy text",
                "Uninstall", "Dismiss notification", "Delete line",
            ),
            labels(n).filterNot { it.startsWith("Settings › ") },
        )
        action(n, "Mute this app's notifications").run()
        assertTrue(SETTINGS in notifPrefs.muted.value)
        assertTrue(logs.recent(50, afterId = n.id).any { it.text == "Muted notifications from Settings" })
        action(n, "Show only Settings").run()
        assertEquals(LogFilter.Named("Settings"), home.filter)
        inFront {
            action(n, "Copy text").run()
            assertEquals("Llat title — Llat body", clipText())
            assertTrue(clipSensitive())
        }
    }

    /** A number's long-press calls it, texts it, opens the contact or copies the number. */
    @Test fun aNumbersLongPressCallsTextsOrCopies() {
        val contact = "content://com.android.contacts/contacts/lookup/llat/1"
        val ann = line(LogKind.OUTPUT, "Llat Ann", LogContext.PHONE to "5550100", LogContext.CONTACT to contact)
        assertEquals(
            listOf("Call 5550100", "Send a message", "Open contact", "Copy number", "Show only commands", "Copy text", "Delete line"),
            labels(ann),
        )
        for (label in listOf("Call 5550100", "Send a message", "Open contact")) action(ann, label).run()
        assertEquals(listOf(Intent.ACTION_DIAL, Intent.ACTION_SENDTO, Intent.ACTION_VIEW), home.intents.map { it.action })
        assertEquals(listOf("tel:5550100", "smsto:5550100", contact), home.intents.map { it.dataString })
        inFront {
            action(ann, "Copy number").run()
            assertEquals("5550100", clipText())
        }
    }

    /** A link's long-press opens, shares or copies it, and opens the picture under it. */
    @Test fun aLinksLongPressOpensSharesOrCopies() {
        val story = line(
            LogKind.SCRIPT, StyledText.wrap(StyledText.Style.BOLD, "Llat") + " story",
            LogContext.URL to "${BASE}story", LogContext.IMAGE to "${BASE}story.png", FeedWorker.CTX_FEED to "1", source = "Llat Feed",
        )
        assertEquals(listOf("Open link", "Share link", "Copy link", "Open image", "Show only Llat Feed", "Copy text", "Delete line"), labels(story))
        action(story, "Open link").run()
        action(story, "Share link").run()
        action(story, "Open image").run()
        val (open, share, image) = home.intents
        assertEquals(Intent.ACTION_VIEW, open.action)
        assertEquals("${BASE}story", open.dataString)
        assertEquals(Intent.ACTION_CHOOSER, share.action)
        @Suppress("DEPRECATION")
        val send = share.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("Llat story", send.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("${BASE}story", send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("${BASE}story.png", image.dataString)
        inFront {
            action(story, "Copy link").run()
            assertEquals("${BASE}story", clipText())
        }
        // A picture that is the link itself isn't offered twice.
        val same = line(LogKind.SCRIPT, "Llat picture", LogContext.URL to "${BASE}p.png", LogContext.IMAGE to "${BASE}p.png", source = "Llat Feed")
        assertTrue(labels(same).none { it == "Open image" })
    }

    /** A script's line opens the editor at its error when tapped; long-pressed, it runs, edits and switches the script. */
    @Test fun aScriptsLineRunsEditsAndSwitchesIt() {
        val s = scripts.create(SCRIPT, ScriptLang.LUA, "log('llat ran')")
        assertTrue(s.enabled)
        assertFalse(s.notify)
        home.scriptList = listOf(s)
        val error = line(LogKind.ERROR, "$SCRIPT: boom (tap to edit line 4)", LogContext.SCRIPT to SCRIPT, LogContext.LINE to "4", source = SCRIPT)
        actions.onTap(error)
        assertEquals(listOf(SCRIPT to 4), home.editors)
        assertEquals(
            listOf(
                "Run $SCRIPT now", "Edit $SCRIPT at line 4", "Edit $SCRIPT.lua", "Turn off $SCRIPT", "Notify on new items",
                "Show only $SCRIPT", "Copy text", "Delete line",
            ),
            labels(error),
        )

        home.forget()
        action(error, "Edit $SCRIPT at line 4").run()
        action(error, "Edit $SCRIPT.lua").run()
        assertEquals(listOf(SCRIPT to 4, SCRIPT to null), home.editors)

        action(error, "Run $SCRIPT now").run()
        waitUntil("the script's run", ms = 30_000) { logs.recent(100, afterId = error.id).any { it.text == "llat ran" } }

        fun said(text: String) = logs.recent(100, afterId = error.id).any { it.text == text && it.ctx.script == SCRIPT }
        action(error, "Turn off $SCRIPT").run()
        assertFalse(scripts.byId(s.id)!!.enabled)
        assertTrue(said("$SCRIPT: turned off"))
        home.scriptList = listOf(scripts.byId(s.id)!!)
        action(error, "Turn on $SCRIPT").run()
        assertTrue(scripts.byId(s.id)!!.enabled)
        assertTrue(said("$SCRIPT: turned on"))

        action(error, "Notify on new items").run()
        assertTrue(scripts.byId(s.id)!!.notify)
        assertTrue(said("$SCRIPT: notifications on"))
        assertTrue(UiEvent.RequestNotificationPermission in home.events)
        home.scriptList = listOf(scripts.byId(s.id)!!)
        home.events.clear()
        action(error, "Mute notifications").run()
        assertFalse(scripts.byId(s.id)!!.notify)
        assertTrue(said("$SCRIPT: notifications off"))
        assertTrue(home.events.isEmpty())
    }

    /** Todo, note, reminder and event lines offer their own actions, each run as the command it stands for. */
    @Test fun productivityLinesOfferTheirActions() {
        val todo = line(LogKind.OUTPUT, "[ ] Llat milk", LogContext.TODO to "3", LogContext.COPY to "Llat milk")
        assertEquals(listOf("Mark done", "Edit in prompt", "Show only commands", "Copy text", "Delete todo", "Delete line"), labels(todo))
        action(todo, "Mark done").run()
        action(todo, "Delete todo").run()
        assertEquals(listOf("todo done #3", "todo rm #3"), home.commands)
        action(todo, "Edit in prompt").run()
        assertEquals("todo edit #3 Llat milk", home.prompt)
        assertEquals(listOf(UiEvent.FocusPrompt), home.events)

        home.forget()
        action(line(LogKind.OUTPUT, "[x] Llat milk", LogContext.TODO to "3", LogContext.TODO_DONE to "1"), "Mark not done").run()
        val note = line(LogKind.OUTPUT, "#5 Llat idea", LogContext.NOTE to "5")
        assertTrue(action(note, "Delete note").destructive)
        action(note, "Delete note").run()
        val reminder = line(LogKind.OUTPUT, "Llat tea at 5", LogContext.REMINDER to "7")
        assertTrue(action(reminder, "Cancel reminder").destructive)
        action(reminder, "Cancel reminder").run()
        assertEquals(listOf("todo undo #3", "notes rm #5", "remind rm #7"), home.commands)

        action(line(LogKind.OUTPUT, "Llat meeting", LogContext.EVENT to "42"), "Open event").run()
        assertEquals(Agenda.eventUri(42), home.intents.single().data)
    }

    /** A grid line stands for its apps, the ones still installed; a chip of one has its app's actions and nothing to copy or delete. */
    @Test fun aGridLineNamesItsApps() {
        val s = settings()
        val other = apps.apps.value.first { !it.hidden && it.pkg != SETTINGS && it.profile.kind == ProfileKind.PERSONAL }
        val refs = listOf(s, other).map { mapOf("pkg" to it.pkg, "cls" to it.component.className, "user" to it.profile.serial) } +
            mapOf("pkg" to "com.example.kolahal.gone", "cls" to "x.Y", "user" to s.profile.serial)
        val grid = line(LogKind.OUTPUT, "Settings, ${other.label}", LogContext.APPS to Json.stringify(refs))
        assertEquals(listOf(s.key, other.key), actions.appsOf(grid).map { it.key })
        assertEquals(emptyList<AppEntry>(), actions.appsOf(line(LogKind.OUTPUT, "Llat broken grid", LogContext.APPS to "not json")))
        assertEquals(emptyList<AppEntry>(), actions.appsOf(line(LogKind.OUTPUT, "Llat no grid")))

        val chip = LogEntry(-1, System.currentTimeMillis(), LogKind.OUTPUT, null, s.label, LogContext.of(*appCtx(s)))
        assertEquals(actions.appActions(s).map { it.label }, labels(chip))
        assertTrue(labels(chip).none { it == "Copy text" || it == "Delete line" || it.startsWith("Show only") })
    }

    private companion object {
        const val SETTINGS = "com.android.settings"
        const val SCRIPT = "llatest"
        const val BASE = "http://127.0.0.1:9/"
        /** An alias no app's name is near, so run literally it launches nothing. */
        const val ALIAS = "zqllat"
    }
}
