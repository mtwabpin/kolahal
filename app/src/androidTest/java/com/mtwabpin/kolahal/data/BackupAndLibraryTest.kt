package com.mtwabpin.kolahal.data

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dokar.quickjs.QuickJs
import com.mtwabpin.kolahal.calls.CallEvents
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.script.ScriptLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import party.iroiro.luajava.lua54.Lua54
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupAndLibraryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val config = Config.get(context)
    private val prefs = AppPrefs.get(context)
    private val scripts = ScriptRepository.get(context)

    @After fun tearDown() {
        Keys.ALL.forEach(config::reset)
        prefs.setAlias("bk", null)
        listOf("bktest", "bknew").forEach { n -> scripts.byName(n)?.let { scripts.delete(it.id) } }
        // What the round trip adds, also when it fails half way.
        val todos = com.mtwabpin.kolahal.productivity.TodoStore.get(context)
        todos.all().filter { it.text == "bk todo" }.forEach { todos.remove(it.id) }
        val notes = com.mtwabpin.kolahal.productivity.NoteStore.get(context)
        notes.all().filter { it.text == "bk note" }.forEach { notes.remove(it.id) }
        val feeds = com.mtwabpin.kolahal.feeds.FeedRepository.get(context)
        feeds.all().filter { it.url.contains("bk-feed") }.forEach { feeds.remove(it.id) }
        prefs.setPinned("bk/pin#0", false)
        java.io.File(context.cacheDir, "kolahal-full-backup-test.json").delete()
    }

    /** The path that broke in release builds: a set() must bump the version immediately. */
    @Test fun configChangeBumpsVersion() {
        val before = config.version.value
        assertTrue(config.set(Keys.THEME, "amber"))
        assertEquals("amber", config[Keys.THEME])
        assertTrue(config.version.value > before)
        val mid = config.version.value
        config.reset(Keys.THEME)
        assertTrue(config.version.value > mid)
    }

    /** Settings saved before 0.3.0 move to today's names when Config starts. */
    @Test fun savedSettingsMoveToNewNames() {
        val saved = context.getSharedPreferences("config", android.content.Context.MODE_PRIVATE)
        val old = listOf("theme", "keyboard.onhome", Keys.OLD_SWIPE_UP_ON, "gesture.swipeup")
        try {
            saved.edit().putString("theme", "dracula").putString("keyboard.onhome", "on")
                .putString(Keys.OLD_SWIPE_UP_ON, "on").putString("gesture.swipeup", "quick").commit()
            val before = config.version.value
            config.migrate()
            assertEquals("dracula", config[Keys.THEME])
            assertTrue(config[Keys.FOCUS_ON_HOME])
            assertEquals("quick", config[Keys.GESTURE_SWIPE_UP])
            assertTrue("the screens redraw with the moved values", config.version.value > before)
            assertTrue(saved.all.keys.toString(), old.none(saved::contains))
        } finally {
            saved.edit().apply { old.forEach(::remove) }.commit()
        }
    }

    /** A backup from before 0.3.0 has the old setting names; they land on today's settings. */
    /** On the phone: the full header's items become the line's two once, and its other settings go. */
    @Test fun theOldHeaderItemsCarryOnce() {
        val saved = context.getSharedPreferences("config", android.content.Context.MODE_PRIVATE)
        try {
            saved.edit().putString("header.unlocks", "off").putString("header.alarm", "off").putString("header.compact.date", "on").commit()
            config.migrate()
            assertEquals("todo", config[Keys.HEADER_ITEM_1])
            assertEquals("settings", config[Keys.HEADER_ITEM_2])
            assertFalse(saved.contains("header.unlocks") || saved.contains("header.compact.date"))
            // Changed since, it stays changed.
            config.set(Keys.HEADER_ITEM_2, "network")
            config.migrate()
            assertEquals("network", config[Keys.HEADER_ITEM_2])
        } finally {
            listOf(Keys.HEADER_ITEM_1, Keys.HEADER_ITEM_2).forEach(config::reset)
        }
    }

    /** A backup with the full header's settings lands on the line's two items, and leaves the rest out. */
    @Test fun aBackupWithTheOldHeaderCarriesItsItems() {
        val file = File(context.cacheDir, "kolahal-backup-old-header.json")
        try {
            file.writeText("""{"kolahal":3,"config":{"header.unlocks":"off","header.network":"on","header.compact.battery":"off"}}""")
            Backup(context).import(Uri.fromFile(file))
            assertEquals("network", config[Keys.HEADER_ITEM_1])
            assertEquals("alarm", config[Keys.HEADER_ITEM_2])
        } finally {
            file.delete()
            listOf(Keys.HEADER_ITEM_1, Keys.HEADER_ITEM_2).forEach(config::reset)
        }
    }

    @Test fun oldBackupNamesImport() {
        val file = File(context.cacheDir, "kolahal-backup-old-names.json")
        file.writeText("""{"kolahal":1,"config":{"theme":"amber","keybar2":"TAB","log.lineheight":"30","gesture.swipeup.on":"on"}}""")
        val r = Backup(context).import(Uri.fromFile(file))
        assertEquals("amber", config[Keys.THEME])
        assertEquals("TAB", config[Keys.KEYBAR2])
        assertEquals(30, config[Keys.LOG_LINE_HEIGHT])
        assertEquals("apps", config[Keys.GESTURE_SWIPE_UP])
        assertTrue(r.skipped.toString(), r.skipped.isEmpty())
        file.delete()
    }

    /** Themes made in the theme builder travel in backups, ahead of settings: the one in use comes back in use. */
    @Test fun themesTravelInBackups() {
        val colours = com.mtwabpin.kolahal.ui.Palettes.colorsOf(com.mtwabpin.kolahal.ui.Palettes.gruvbox)
        CustomThemes.save(context, "bk-theme", colours)
        config.set(Keys.THEME, "bk-theme")
        val file = File(context.cacheDir, "kolahal-backup-themes.json")
        try {
            Backup(context).export(Uri.fromFile(file))
            CustomThemes.delete(context, "bk-theme")
            config.reset(Keys.THEME)
            Backup(context).import(Uri.fromFile(file))
            assertEquals(colours, CustomThemes.colors("bk-theme"))
            assertEquals("bk-theme", config[Keys.THEME])
        } finally {
            CustomThemes.delete(context, "bk-theme")
            file.delete()
        }
    }

    /** A restored line may not run a command on a tap, nor open a contact URI from anywhere but the contacts app. */
    @Test fun restoredLinesKeepOnlySafeContext() {
        val ctx = LogContext.of(
            LogContext.COMMAND to "brightness 0", LogContext.RUN to "clear all", LogContext.SHORTCUT to "x",
            LogContext.URL to "https://example.com/", LogContext.PHONE to "+15550100", LogContext.CONTACT to "upi://pay?pa=x",
            LogContext.PKG to "com.example", LogContext.COPY to "text",
        )
        assertEquals(setOf(LogContext.URL, LogContext.PHONE, LogContext.PKG, LogContext.COPY), FullBackup.safeCtx(ctx).values.keys)
        val contact = "content://com.android.contacts/contacts/lookup/abc"
        assertEquals(contact, FullBackup.safeCtx(LogContext.of(LogContext.CONTACT to contact)).values[LogContext.CONTACT])
    }

    @Test fun backupRoundTrip() {
        config.set(Keys.THEME, "gruvbox")
        config.set(Keys.LOG_SPACING, "20")
        prefs.setAlias("bk", "echo backup")
        val existing = scripts.create("bktest", ScriptLang.LUA, "return 1")
        scripts.setInterval(existing.id, 30)

        val file = File(context.cacheDir, "kolahal-backup-test.json")
        val uri = Uri.fromFile(file)
        assertEquals(scripts.all().size, Backup(context).export(uri))

        // Change everything, then add a new script only present in the file.
        config.reset(Keys.THEME)
        config.reset(Keys.LOG_SPACING)
        prefs.setAlias("bk", null)
        val json = file.readText()
        val withNew = json.replace(
            "\"scripts\":[",
            "\"scripts\":[{\"name\":\"bknew\",\"lang\":\"lua\",\"code\":\"return 2\",\"every\":5,\"enabled\":true},",
        ).replace("\"aliases\":{", "\"aliases\":{\"help\":\"clear all\",")
        file.writeText(withNew)

        val r = Backup(context).import(uri)
        assertEquals("gruvbox", config[Keys.THEME])
        assertEquals(20, config[Keys.LOG_SPACING])
        assertEquals("echo backup", prefs.alias("bk"))
        assertNull("built-in names must not become aliases", prefs.alias("help"))
        assertTrue(r.skipped.any { it.contains("help") })

        val imported = scripts.byName("bknew")
        assertNotNull(imported)
        assertFalse("new scripts from a backup arrive disabled", imported!!.enabled)
        assertEquals("interval below the minimum is clamped", 15, imported.intervalMin)
        assertEquals(30, scripts.byName("bktest")!!.intervalMin)
        file.delete()
    }

    /** "Backup everything": todos, notes, feeds, script stores and pins survive export → wipe → import. */
    @Test fun fullBackupRoundTrip() {
        val todos = com.mtwabpin.kolahal.productivity.TodoStore.get(context)
        val notes = com.mtwabpin.kolahal.productivity.NoteStore.get(context)
        val feeds = com.mtwabpin.kolahal.feeds.FeedRepository.get(context)
        val todoId = todos.add("bk todo")
        notes.add("bk note")
        val feed = feeds.add("https://example.com/bk-feed.xml")
        val script = scripts.create("bktest", ScriptLang.LUA, "return 1")
        KvStore(context, ScriptRepository.scopeOf(script.id)).set("seen", "42")
        prefs.setPinned("bk/pin#0", true)

        val file = File(context.cacheDir, "kolahal-full-backup-test.json")
        val uri = Uri.fromFile(file)
        Backup(context).export(uri)

        // Wipe: the script comes back with a new id, so its store must follow it by name.
        todos.remove(todoId)
        notes.all().filter { it.text == "bk note" }.forEach { notes.remove(it.id) }
        feeds.remove(feed.id)
        scripts.delete(script.id)
        prefs.setPinned("bk/pin#0", false)

        val r = Backup(context).import(uri)
        assertTrue(r.full)
        assertTrue(todos.all().any { it.text == "bk todo" })
        assertTrue(notes.all().any { it.text == "bk note" })
        assertTrue(feeds.all().any { it.url == "https://example.com/bk-feed.xml" })
        assertTrue(AppPrefs.get(context).isPinned("bk/pin#0"))
        val restored = scripts.byName("bktest")!!
        assertEquals("42", KvStore(context, ScriptRepository.scopeOf(restored.id)).get("seen"))

        todos.all().filter { it.text == "bk todo" }.forEach { todos.remove(it.id) }
        notes.all().filter { it.text == "bk note" }.forEach { notes.remove(it.id) }
        feeds.all().filter { it.url.contains("bk-feed") }.forEach { feeds.remove(it.id) }
        prefs.setPinned("bk/pin#0", false)
        file.delete()
    }

    /** Your own script whose name an app also has comes back from a backup (switched off, as all do). */
    @Test fun aScriptNamedLikeAnAppComesBack() {
        val apps = com.mtwabpin.kolahal.apps.AppRepository.get(context)
        apps.refresh()
        val until = System.currentTimeMillis() + 5_000
        while (apps.apps.value.isEmpty() && System.currentTimeMillis() < until) Thread.sleep(50)
        val name = apps.apps.value.map { it.label.lowercase().replace(Regex("[^a-z0-9]"), "") }
            .first { it.length in 3..40 && it !in com.mtwabpin.kolahal.terminal.CommandCatalog.RESERVED_NAMES && scripts.byName(it) == null }
        assertNotNull(com.mtwabpin.kolahal.script.ScriptNames.clash(context, name))
        val file = File(context.cacheDir, "kolahal-backup-app-name.json")
        try {
            scripts.create(name, ScriptLang.LUA, "return 1")
            Backup(context).export(Uri.fromFile(file))
            scripts.byName(name)?.let { scripts.delete(it.id) }
            val r = Backup(context).import(Uri.fromFile(file))
            assertTrue(r.skipped.toString(), r.skipped.none { name in it })
            assertFalse(scripts.byName(name)!!.enabled)
        } finally {
            scripts.byName(name)?.let { scripts.delete(it.id) }
            file.delete()
        }
    }

    /** Updating from 0.4.1 deletes the old `── name · 22:40 ──` lines, and only those. */
    @Test fun oldRunHeadersGoOnUpdate() {
        val db = android.database.sqlite.SQLiteDatabase.create(null)
        try {
            db.execSQL("CREATE TABLE logs (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, kind TEXT NOT NULL, source TEXT, text TEXT NOT NULL, ctx TEXT)")
            fun add(kind: LogKind, text: String, ctx: LogContext) = db.execSQL(
                "INSERT INTO logs (ts, kind, text, ctx) VALUES (0, ?, ?, ?)", arrayOf<Any?>(kind.key, text, ctx.toJson()),
            )
            add(LogKind.INFO, "── hn · 1:00 PM ──", LogContext.of(LogContext.SCRIPT to "hn", LogContext.RUN_HEADER to "1"))
            add(LogKind.INFO, "── Feeds · 1:00 PM ──", LogContext.of(LogContext.RUN_HEADER to "1"))
            // A line that only mentions the word, in its text or a value, stays.
            add(LogKind.INFO, "runheader", LogContext.of(LogContext.COPY to "\"runheader\""))
            add(LogKind.SCRIPT, "a story", LogContext.of(LogContext.SCRIPT to "hn"))
            assertEquals(2, Db.dropRunHeaders(db))
            val left = db.rawQuery("SELECT text FROM logs ORDER BY id", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            assertEquals(listOf("runheader", "a story"), left)
        } finally {
            db.close()
        }
    }

    /** A backup from 0.4.1 or before brings its log back without the old `── name · 22:40 ──` lines. */
    @Test fun oldRunHeadersStayBehindOnRestore() {
        val logs = LogRepository.get(context)
        val header = logs.add(LogKind.INFO, "── bk · 1:00 PM ──", "bk", LogContext.of(LogContext.RUN_HEADER to "1"))
        val line = logs.add(LogKind.SCRIPT, "bk line", "bk")
        val file = File(context.cacheDir, "kolahal-backup-headers.json")
        try {
            Backup(context).export(Uri.fromFile(file))
            Backup(context).import(Uri.fromFile(file))
            val back = logs.recent(limit = 50).filter { it.source == "bk" }.map { it.text }
            assertEquals(listOf("bk line"), back)
        } finally {
            logs.delete(header)
            logs.delete(line)
            logs.recent(limit = 50).filter { it.source == "bk" }.forEach { logs.delete(it.id) }
            file.delete()
        }
    }

    /** Exports and imports as on a phone whose log is new: its ids go on from the newest line restored. */
    private fun restoreOnANewPhone() {
        val file = File(context.cacheDir, "kolahal-backup-new-phone.json")
        try {
            Backup(context).export(Uri.fromFile(file))
            Db.get(context).writableDatabase.delete("sqlite_sequence", "name = 'logs'", null)
            Backup(context).import(Uri.fromFile(file))
        } finally {
            file.delete()
        }
    }

    /** A line that stays out of backups, as notification lines do. */
    private fun LogRepository.addPrivate() =
        add(LogKind.SCRIPT, "bk notification", "bk", LogContext.of(com.mtwabpin.kolahal.notifications.KolahalNotificationListener.CTX_KEY to "1"))

    /** Hidden up to a line that stays behind: what was hidden stays hidden, and the next line shows. */
    @Test fun aHidePastTheRestoredLinesKeepsNewLinesInView() {
        val logs = LogRepository.get(context)
        try {
            val old = logs.add(LogKind.OUTPUT, "bk old")
            logs.hideScreen(logs.addPrivate())
            restoreOnANewPhone()
            assertTrue(logs.isHidden(old))
            val new = logs.add(LogKind.OUTPUT, "bk new")
            assertFalse(logs.isHidden(new))
            assertTrue(logs.recent(limit = 50).any { it.id == new })
        } finally {
            logs.clearAll()
        }
    }

    /** A hide within the restored lines comes back as it was: the lines up to it hidden, the rest in view. */
    @Test fun aHideWithinTheRestoredLinesStaysWhereItWas() {
        val logs = LogRepository.get(context)
        try {
            val hidden = logs.add(LogKind.OUTPUT, "bk hidden")
            val shown = logs.add(LogKind.OUTPUT, "bk shown")
            logs.hideScreen(hidden)
            restoreOnANewPhone()
            assertTrue(logs.isHidden(hidden))
            assertFalse(logs.isHidden(shown))
            assertEquals(listOf(shown), logs.recent(limit = 50).map { it.id }.filter { it == hidden || it == shown })
        } finally {
            logs.clearAll()
        }
    }

    /** A cleared screen leaves only its commands: they stay out of view and in ↑ history, and the next line shows. */
    @Test fun aClearPastTheRestoredLinesKeepsNewLinesInView() {
        val logs = LogRepository.get(context)
        try {
            val command = logs.add(LogKind.COMMAND, "bk command")
            logs.add(LogKind.OUTPUT, "bk output")
            logs.purgeCleared(logs.clearScreen().upTo)
            restoreOnANewPhone()
            assertTrue(logs.recent(limit = 50).none { it.id == command })
            assertTrue("bk command" in logs.commandHistory())
            val new = logs.add(LogKind.OUTPUT, "bk new")
            assertTrue(logs.recent(limit = 50).any { it.id == new })
        } finally {
            logs.clearAll()
        }
    }

    /** ↑ history emptied at a line that stays behind: it stays empty of the old commands, and new ones are recalled. */
    @Test fun historyEmptiedPastTheRestoredLinesRecallsNewCommands() {
        val logs = LogRepository.get(context)
        try {
            logs.add(LogKind.COMMAND, "bk old command")
            logs.addPrivate()
            logs.clearHistory()
            restoreOnANewPhone()
            assertFalse("bk old command" in logs.commandHistory())
            logs.add(LogKind.COMMAND, "bk new command")
            assertTrue("bk new command" in logs.commandHistory())
        } finally {
            logs.clearAll()
        }
    }

    /** Seen up to a line that stays behind: the seen mark, which `log.fresh` hides up to, comes down with it. */
    @Test fun whatWasSeenStaysWithinTheRestoredLines() {
        val logs = LogRepository.get(context)
        try {
            logs.add(LogKind.OUTPUT, "bk old")
            val notification = logs.addPrivate()
            logs.seenUpTo = notification
            restoreOnANewPhone()
            assertTrue(logs.seenUpTo < notification)
        } finally {
            logs.clearAll()
        }
    }

    /** Other people's messages and numbers, and a private space's apps, stay on the phone. */
    @Test fun privateLinesStayOutOfBackups() {
        val logs = LogRepository.get(context)
        val before = logs.maxId()
        val file = File(context.cacheDir, "kolahal-backup-private.json")
        val kept = listOf("Launched bkline-plain", "Launched bkline-work [W]", "bkline-word [Sunday] plans", "bkline-brackets [S]a")
        kept.forEach { logs.add(LogKind.INFO, it) }
        logs.add(LogKind.INFO, "Launched bkline-launched [S]")
        logs.add(LogKind.INFO, "Updated bkline-updated [S] ↻\ncom.example\n1.0", "apps")
        logs.add(LogKind.INFO, "Uninstalled bkline-gone [S] −", "apps")
        logs.add(LogKind.OUTPUT, "Call from bkline-call", CallEvents.SOURCE)
        logs.add(LogKind.COMMAND, "call bkline-typed")
        logs.add(LogKind.COMMAND, "text bkline-sms running late")
        logs.add(LogKind.COMMAND, "todo bkline-command")
        logs.add(LogKind.OUTPUT, "bkline-grid, Maps", ctx = LogContext.of(LogContext.APPS to "[]", LogContext.COPY to "bkline-grid [S]\nMaps"))
        logs.add(LogKind.INFO, "Dialing bkline-number", ctx = LogContext.of(LogContext.PHONE to "5550100"))
        try {
            Backup(context).export(Uri.fromFile(file))
            val text = file.readText()
            for (line in kept) assertTrue(line, text.contains(line))
            assertTrue(text.contains("bkline-command"))
            for (word in listOf("launched", "updated", "gone", "call", "number", "typed", "sms", "grid")) assertFalse(word, text.contains("bkline-$word"))
        } finally {
            logs.recent(50).filter { it.id > before }.forEach { logs.delete(it.id) }
            file.delete()
        }
    }

    /** Its apps' pins, labels, hidden flags and launches too, known by the serial in their keys. */
    @Test fun aPrivateSpacesAppsStayOutOfTheirSettings() {
        val theirs = "com.bank/.Main#11"
        val mine = "com.maps/.Main#0"
        val entries = mapOf(
            "hidden:$theirs" to mapOf("b" to true), "hidden:$mine" to mapOf("b" to true),
            "label:$theirs" to mapOf("s" to "x"), "launch:$theirs" to mapOf("s" to "1:2"),
            "pinned" to mapOf("s" to "$theirs\n$mine"),
        )
        val kept = FullBackup.withoutPrivateApps(entries, setOf(11L))
        assertEquals(setOf("hidden:$mine", "pinned"), kept.keys)
        assertEquals(mapOf("s" to mine), kept["pinned"])
        assertEquals(entries, FullBackup.withoutPrivateApps(entries, emptySet()))
    }

    /** What `http.cached` keeps is only a cache, and one from before 0.4.9 can hold a key. */
    @Test fun cachedResponsesStayOutOfBackups() {
        val repo = ScriptRepository.get(context)
        val script = repo.create("bkcache", ScriptLang.LUA, "log('x')")
        try {
            KvStore(context, ScriptRepository.scopeOf(script.id)).apply {
                set("http.cache:https://example.com/", "a response")
                set("mine", "kept")
            }
            val stores = FullBackup.export(context)["scriptStores"] as Map<*, *>
            assertEquals(mapOf("mine" to "kept"), stores["bkcache"])
        } finally {
            repo.delete(script.id)
        }
    }

    /** A private space's line that names no [S]: its profile's serial says so. */
    @Test fun aPrivateProfilesSerialCountsToo() {
        val ctx = LogContext.of(LogContext.USER to "11").toJson()
        val row = mapOf("text" to "Opened something", "source" to null, "ctx" to ctx)
        assertTrue(FullBackup.private(row, setOf(11L)))
        assertFalse(FullBackup.private(row, setOf(12L)))
        assertFalse(FullBackup.private(row, emptySet()))
    }

    /** A repeat this version can't read must not come back to crash the app when it's due. */
    @Test fun aReminderRepeatItCantReadComesBackWithoutIt() {
        val store = com.mtwabpin.kolahal.productivity.ReminderStore.get(context)
        val good = store.add(System.currentTimeMillis() + 3_600_000, "bkrem-good", "day")
        val bad = store.add(System.currentTimeMillis() + 3_600_000, "bkrem-bad", "day")
        try {
            @Suppress("UNCHECKED_CAST") val data = FullBackup.export(context).toMutableMap()
            @Suppress("UNCHECKED_CAST") val tables = (data["tables"] as Map<String, Any?>).toMutableMap()
            @Suppress("UNCHECKED_CAST") val rows = tables["reminders"] as List<Map<String, Any?>>
            tables["reminders"] = rows.map { if (it["text"] == "bkrem-bad") it + ("repeat_rule" to "biweekly") else it }
            data["tables"] = tables
            FullBackup.importRaw(context, data)
            val back = store.all()
            assertEquals("day", back.single { it.text == "bkrem-good" }.repeat)
            assertNull(back.single { it.text == "bkrem-bad" }.repeat)
        } finally {
            store.all().filter { it.text.startsWith("bkrem-") }.forEach { store.remove(it.id) }
        }
    }

    /** The boot `@on boot` last fired for is this phone's: restored, an older one would read as a new boot. */
    @Test fun theLastBootStaysOutOfBackups() {
        val triggers = context.getSharedPreferences("triggers", android.content.Context.MODE_PRIVATE)
        val was = triggers.getString("boot", null)
        triggers.edit().putString("boot", "count:7").commit()
        try {
            assertFalse("triggers" in (FullBackup.export(context)["prefs"] as Map<*, *>))
            // A backup from before this version has it: it stays out of the restore.
            @Suppress("UNCHECKED_CAST") val data = FullBackup.export(context).toMutableMap()
            data["prefs"] = (data["prefs"] as Map<String, Any?>) + ("triggers" to mapOf("boot" to mapOf("s" to "count:6")))
            FullBackup.importRaw(context, data)
            assertEquals("count:7", triggers.getString("boot", null))
        } finally {
            triggers.edit().putString("boot", was).commit()
        }
    }

    @Test fun thisPhonesRecordsStayOutOfBackups() {
        val packages = KvStore(context, "packages")
        packages.set("bk-device", "this phone")
        val file = File(context.cacheDir, "kolahal-backup-device.json")
        try {
            Backup(context).export(Uri.fromFile(file))
            assertFalse(file.readText().contains("bk-device"))
            Backup(context).import(Uri.fromFile(file))
            assertEquals("this phone", packages.get("bk-device"))
        } finally {
            packages.set("bk-device", null)
            file.delete()
        }
    }

    /** What an export leaves out stays out of a restore, whatever the file holds: the notification copy, a store by id. */
    @Test fun aBackupCantBringInTheNotificationCopyOrAStoreById() {
        val script = scripts.create("bktest", ScriptLang.LUA, "log('x')")
        val scope = com.mtwabpin.kolahal.notifications.KolahalNotificationListener.MIRROR_SCOPE
        val key = com.mtwabpin.kolahal.notifications.KolahalNotificationListener.MIRROR_KEY
        val mirror = KvStore(context, scope)
        val was = mirror.get(key)
        val store = KvStore(context, ScriptRepository.scopeOf(script.id))
        store.set("mine", "kept")
        try {
            @Suppress("UNCHECKED_CAST") val data = FullBackup.export(context).toMutableMap()
            @Suppress("UNCHECKED_CAST") val tables = (data["tables"] as Map<String, Any?>).toMutableMap()
            @Suppress("UNCHECKED_CAST") val kv = tables["kv"] as List<Map<String, Any?>>
            tables["kv"] = kv + listOf(
                mapOf("scope" to scope, "k" to key, "v" to "[{\"title\":\"planted\"}]"),
                mapOf("scope" to ScriptRepository.scopeOf(script.id), "k" to "mine", "v" to "planted"),
            )
            data["tables"] = tables
            FullBackup.importRaw(context, data)
            assertEquals(was, mirror.get(key))
            assertEquals("kept", store.get("mine"))
        } finally {
            mirror.set(key, was)
        }
    }

    /** A library copy nobody edited follows the app's library; an edited one stays as it is. */
    @Test fun uneditedLibraryCopiesFollowTheApp() {
        val lib = ScriptLibrary(context)
        val prefs = context.getSharedPreferences("script_library", android.content.Context.MODE_PRIVATE)
        val (kept, edited) = listOf("countdown", "charge-reminder").map { name -> lib.entries.single { it.name == name } }
        try {
            for (e in listOf(kept, edited)) lib.install(e)
            // As an older Kolahal installed them: an older copy, recorded as installed.
            val older = "-- an older copy\nreturn 1"
            for (e in listOf(kept, edited)) {
                scripts.saveCode(scripts.byName(e.name)!!.id, older)
                prefs.edit().putString("installed:${e.name}", older).commit()
            }
            scripts.setInterval(scripts.byName(kept.name)!!.id, 45)
            // Then edited, by you.
            scripts.saveCode(scripts.byName(edited.name)!!.id, older + "\n-- mine", byUser = true)
            prefs.edit().remove("library").commit()

            assertEquals(listOf(kept.name), lib.updateUnedited())
            assertEquals(lib.code(kept), scripts.byName(kept.name)!!.code)
            assertEquals("its timers stay", 45, scripts.byName(kept.name)!!.intervalMin)
            assertEquals(ScriptLibrary.State.MODIFIED, lib.state(edited, scripts.all()))
            assertEquals(older + "\n-- mine", scripts.byName(edited.name)!!.code)
            assertTrue(LogRepository.get(context).recent(20).any { it.text == "Library scripts updated with Kolahal: ${kept.name}" })
            // Once per library: the next start does nothing.
            assertEquals(emptyList<String>(), lib.updateUnedited())
        } finally {
            for (e in listOf(kept, edited)) {
                scripts.byName(e.name)?.let { scripts.delete(it.id) }
                lib.forget(e.name)
            }
            LogRepository.get(context).recent(20).filter { it.text.startsWith("Library scripts updated") }.forEach { LogRepository.get(context).delete(it.id) }
        }
    }

    @Test fun libraryLuaScriptsCompile() {
        val lib = ScriptLibrary(context)
        val lua = lib.entries.filter { it.lang == ScriptLang.LUA }
        assertTrue(lua.isNotEmpty())
        for (e in lua) {
            val state = Lua54()
            try {
                state.load(lib.code(e)) // throws LuaException on a syntax error
            } catch (t: Throwable) {
                throw AssertionError("${e.file}: ${t.message}", t)
            } finally {
                state.close()
            }
        }
    }

    @Test fun libraryJsScriptsCompile() = runBlocking {
        val lib = ScriptLibrary(context)
        val js = lib.entries.filter { it.lang == ScriptLang.JS }
        assertTrue(js.isNotEmpty())
        val engine = QuickJs.create(Dispatchers.Default)
        try {
            for (e in js) {
                // Same wrapping as JsEngine, so top-level await and `return` are valid.
                runCatching { engine.compile("__kolahal_done(await (async () => {\n${lib.code(e)}\n})());", "${e.name}.js") }
                    .onFailure { throw AssertionError("${e.file}: ${it.message}", it) }
            }
        } finally {
            engine.close()
        }
    }

    @Test fun libraryIndexMatchesFiles() {
        val lib = ScriptLibrary(context)
        for (e in lib.entries) {
            assertEquals("${e.name}.${e.lang.ext}", e.file)
            val code = lib.code(e)
            assertEquals("${e.file}: command flag", e.command, Regex("""^\s*(--|//)\s*@command\b""", RegexOption.MULTILINE).containsMatchIn(code))
        }
    }
}
