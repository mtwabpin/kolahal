package com.mtwabpin.kolahal.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.shownText
import com.mtwabpin.kolahal.shell
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** The bundled scripts that need no network, run as Kolahal runs them: what each one logs. */
@RunWith(AndroidJUnit4::class)
class LibraryScriptsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)
    private val library = ScriptLibrary(context)
    private val name = "lib${System.nanoTime()}"
    // A script id of its own, so the script's store starts empty.
    private val id = 900_000_000L + System.nanoTime() % 1_000_000
    private val store = KvStore(context, ScriptRepository.scopeOf(id))
    private val status = KvStore(context, HostApi.STATUS_SCOPE)

    /** [options]: the ones you set; the rest are the script's defaults. [before]: code to run first, such as a stand-in for the network. */
    private fun run(file: String, argline: String = "", event: Map<String, Any?>? = null, options: Map<String, String> = emptyMap(), before: String = "") {
        lines().forEach { logs.delete(it.id) }
        val entry = library.entries.single { it.file == file }
        val declared = ScriptMeta.options(library.code(entry)).associate { it.name to it.default }
        val host = HostApi(context, name, scriptId = id, notifyEnabled = false, options = declared + options)
        val args = argline.split(' ').filter(String::isNotEmpty)
        runBlocking { ScriptEngine.forLang(entry.lang).run(before + library.code(entry), name, host, 10_000, false, args, event) }
        host.flush()
    }

    private fun lines(): List<LogEntry> = logs.recent(limit = 200).filter { it.source == name }

    /** The one line logged: its title as shown, its body, and the colour of all of its title (null: none, or mixed). */
    private fun only(): Triple<String, String?, StyledText.Colour?> = lines().single().let { Triple(it.text.let(StyledText::plain), it.ctx.body, colourOf(it.text)) }

    private fun colourOf(text: String) = StyledText.lines(text).single().spans.map { it.colour }.distinct().singleOrNull()

    /** [only], with its body as plain text: without a bar, which reads as nothing. */
    private fun plainOnly() = only().let { (title, body, colour) -> Triple(title, body?.let(StyledText::plain), colour) }

    @After fun tearDown() {
        lines().forEach { logs.delete(it.id) }
        store.all().keys.forEach { store.set(it, null) }
        status.set(name, null)
    }

    @Test fun countdownSavesListsAndRemoves() {
        val today = LocalDate.now()
        run("countdown.lua")
        assertEquals(Triple("No countdowns yet: countdown add 2026-12-25 Christmas", null, StyledText.Colour.MUTED), only())

        // A date as the script shows it, under the days: "25 Dec Friday", with the year when it isn't this one.
        fun shown(d: LocalDate) = d.format(java.time.format.DateTimeFormatter.ofPattern(if (d.year == today.year) "d MMM" else "d MMM yyyy", java.util.Locale.ENGLISH)) +
            " " + d.format(java.time.format.DateTimeFormatter.ofPattern("EEEE", java.util.Locale.ENGLISH))
        val saved = "Saved · countdown lists them"
        // Its name is the title, coloured by how near: this week's in the warning colour, today's in
        // the good one, past ones muted. Under it, big numbers: the days, then the date.
        run("countdown.lua", "add ${today.plusDays(3)} Soon")
        assertEquals(Triple("Soon", "3 days to go · ${shown(today.plusDays(3))}\n$saved", StyledText.Colour.WARN), plainOnly())
        val big = StyledText.lines(lines().single().ctx.body!!).first().stats!!
        assertEquals(listOf("3", "days to go"), big.first().let { s -> listOf(s.value, s.label).map { p -> p.joinToString("") { it.text } } })
        assertEquals(StyledText.Colour.WARN, big.first().value.single().colour)
        run("countdown.lua", "add $today Now")
        assertEquals(Triple("Now", "Today · ${shown(today)}\n$saved", StyledText.Colour.GOOD), plainOnly())
        run("countdown.lua", "add ${today.minusDays(10)} Past")
        assertEquals(Triple("Past", "10 days ago · ${shown(today.minusDays(10))}\n$saved", StyledText.Colour.MUTED), plainOnly())
        run("countdown.lua", "add ${today.plusDays(30)} Trip")
        assertEquals(Triple("Trip", "30 days to go · ${shown(today.plusDays(30))}\n$saved", null), plainOnly())
        run("countdown.lua", today.plusDays(1).toString())
        assertEquals("1 day to go · ${shown(today.plusDays(1))}", plainOnly().second)

        run("countdown.lua")
        // The next one in big numbers, then a table: the number `rm` takes, the countdown, its date and when it is.
        val list = lines().single()
        assertEquals("Countdowns", list.text)
        assertEquals(
            listOf(
                "Today Now · ${shown(today)}",
                "#\tCountdown\tDate\tWhen",
                "1\tPast\t${today.minusDays(10)}\t10 days ago",
                "2\tNow\t$today\ttoday",
                "3\tSoon\t${today.plusDays(3)}\tin 3 days",
                "4\tTrip\t${today.plusDays(30)}\tin 30 days",
            ),
            StyledText.plain(list.ctx.body!!).lines(),
        )
        // "When" is coloured by how near it is, as a single countdown is; a past one is muted throughout.
        val rows = StyledText.lines(list.ctx.body!!).mapNotNull { it.row }
        assertEquals(
            listOf(StyledText.Colour.MUTED, StyledText.Colour.GOOD, StyledText.Colour.WARN, null),
            rows.drop(1).map { it.cells[3].spans.single().colour },
        )
        assertEquals(StyledText.Colour.MUTED, rows[1].cells[1].spans.single().colour)
        // The nearest one not past is its status line.
        assertEquals("Now: today", status.get(name))

        run("countdown.lua", "rm 1")
        val removed = lines().single()
        assertEquals("Removed Past", removed.shownText.lines().first())
        // The line has a body (the date), so it is a bold heading already: the label needs no bold of its own.
        assertEquals(listOf(null), StyledText.lines(removed.text).single().spans.map { it.styles.ifEmpty { null } })
        run("countdown.lua", "rm 9")
        assertEquals(Triple("No countdown #9: countdown lists them", null, StyledText.Colour.MUTED), only())
        run("countdown.lua", "add someday Trip")
        assertEquals(Triple("Usage: countdown YYYY-MM-DD | add YYYY-MM-DD label | rm N", null, StyledText.Colour.MUTED), only())

        // A date on its own is counted, not saved.
        run("countdown.lua", "${today.plusDays(5)}")
        assertEquals(Triple("${today.plusDays(5)}", "5 days to go · ${shown(today.plusDays(5))}", StyledText.Colour.WARN), plainOnly())
        // Not saved: the list still has the three left.
        run("countdown.lua")
        assertEquals(3, StyledText.lines(lines().single().ctx.body!!).count { it.row?.header == false })
    }

    /** 5 November comes before the 20th, and is the nearest, however it was typed. */
    @Test fun countdownSortsByTheDayNotTheText() {
        val year = LocalDate.now().year + 1
        run("countdown.lua", "add $year-11-20 Later")
        run("countdown.lua", "add $year-11-5 Sooner")
        run("countdown.lua")
        // The table's rows, after the big numbers and the header.
        assertEquals(listOf("1\tSooner", "2\tLater"), StyledText.plain(lines().single().ctx.body!!).lines().filter { '\t' in it && it.first().isDigit() }.map { it.split('\t').take(2).joinToString("\t") })
        assertEquals(true, status.get(name).orEmpty().contains("Sooner"))
    }

    /** A network that answers with [body], and a notify that says so in the log. */
    private fun standIns(body: String) =
        "const http = { get: async () => ({ ok: true, status: 200, body: ${com.mtwabpin.kolahal.script.Json.stringify(body)} }) };\n" +
            "const notify = (title) => log('notified ' + title);\n"

    /** [standIns] for a Lua script: a network that answers with [body], and a notify that says so in the log. */
    private fun luaStandIns(body: String) =
        "http = { get = function() return { ok = true, status = 200, body = [==[$body]==] } end }\n" +
            "notify = function(title) log('notified ' .. title) end\n"

    /**
     * Lobsters: each new story once, with its tags, score and a link to its comments; a text post's
     * line opens its discussion; a story under the score option waits.
     */
    @Test fun lobstersShowsEachNewStoryOnce() {
        val body = """[
            {"short_id":"aaa","title":"A story","url":"https://a.example/x","score":28,"comment_count":37,"tags":["security","rust","web"],"comments_url":"https://lobste.rs/s/aaa/a_story","short_id_url":"https://lobste.rs/s/aaa"},
            {"short_id":"bbb","title":"Ask: tools","url":"","score":61,"comment_count":1,"tags":["ask"],"comments_url":"https://lobste.rs/s/bbb/ask","short_id_url":"https://lobste.rs/s/bbb"},
            {"short_id":"ccc","title":"Too new","url":"https://c.example","score":2,"comment_count":0,"tags":[],"comments_url":"https://lobste.rs/s/ccc/new","short_id_url":"https://lobste.rs/s/ccc"}
        ]"""
        run("lobsters.lua", before = luaStandIns(body))
        val (story, ask, notified) = lines()
        assertEquals("A story security rust · 28 points · 37 comments", StyledText.plain(story.text))
        assertEquals("https://a.example/x", story.ctx.url)
        assertEquals(listOf("37 comments" to "https://lobste.rs/s/aaa/a_story"), StyledText.linkTexts(story.text))
        assertEquals(listOf("security", "rust"), StyledText.lines(story.text).single().spans.filter { StyledText.Style.TAG in it.styles }.map { it.text })
        assertEquals("Ask: tools ask · 61 points · 1 comment", StyledText.plain(ask.text))
        assertEquals("https://lobste.rs/s/bbb/ask", ask.ctx.url)
        assertEquals(emptyList<Pair<String, String>>(), StyledText.linkTexts(ask.text))
        assertEquals("notified Lobsters", notified.text)
        assertEquals(3, lines().size)
        // The same front page again: nothing new.
        run("lobsters.lua", before = luaStandIns(body))
        assertEquals(emptyList<LogEntry>(), lines())
        // The waiting one, once it has the score.
        run("lobsters.lua", before = luaStandIns(body.replace("\"score\":2,", "\"score\":9,")))
        assertEquals(listOf("Too new · 9 points · 0 comments", "notified Lobsters"), lines().map { StyledText.plain(it.text) })
    }

    /** Weather: the place as the title, then big numbers; a timer run keeps "Pune: ☀️ 25°C" as its status line. */
    @Test fun weatherShowsBigNumbers() {
        val answer = "Pune|☀️ |Sunny|+25°C|36%|←9km/h|3"
        run("weather.lua", "Pune", before = luaStandIns(answer))
        val e = lines().single()
        assertEquals("Pune", e.text)
        val stats = StyledText.lines(e.ctx.body!!).single().stats!!
        assertEquals(listOf("25°C", "36%", "← 9 km/h", "3"), stats.map { s -> s.value.joinToString("") { it.text } })
        assertEquals(listOf("☀️ Sunny", "humidity", "wind", "UV"), stats.map { s -> s.label.joinToString("") { it.text } })
        assertNull(status.get(name))
        run("weather.lua", before = luaStandIns(answer.replace("+25", "-3")))
        assertEquals("Pune: ☀️ -3°C", status.get(name))
        // Not the line it expects: a failure that says so.
        run("weather.lua", "Pune", before = luaStandIns("Pune: ☀️ +25°C"))
        assertEquals("No weather", only().first)
    }

    /** Typed, the quote is on screen; the notification is for its daily time. */
    @Test fun quoteNotifiesOnlyAtItsTime() {
        val before = standIns("""[{"q":"Be kind","a":"Someone"}]""")
        run("quote.js", before = before)
        assertEquals(listOf("“Be kind”"), lines().map { it.text.let(StyledText::plain) })
        run("quote.js", event = at("08:00"), before = before)
        assertEquals(setOf("“Be kind”", "notified Someone"), lines().map { it.text.let(StyledText::plain) }.toSet())
    }

    /** 10 hours and 59.5 minutes reads as 11 hours, not "10h 60m". */
    @Test fun sunsDayLengthRoundsAsAWhole() {
        fun length(secs: Int): String {
            run("sun.js", "12.97 77.59", before = standIns("""{"daily":{"sunrise":["2026-10-16T06:12"],"sunset":["2026-10-16T17:12"],"daylight_duration":[$secs]}}"""))
            // The body's first line: a bar of the daylight gone comes under it.
            return only().second.orEmpty().let(StyledText::plain).lineSequence().first().substringAfterLast(" · ")
        }
        assertEquals("11h 0m of daylight", length(39_570))
        assertEquals("10h 50m of daylight", length(39_000))
        assertEquals("10h 59m of daylight", length(39_569))
    }

    @Test fun releasesWatchesAndListsRepos() {
        run("releases.lua", "list")
        val defaults = listOf("mtwabpin/kolahal", "neovim/neovim", "termux/termux-app")
        assertEquals(defaults, lines().map { it.text })
        assertEquals(defaults.map { "https://github.com/$it/releases" }, lines().map { it.ctx.url })
        // Each repo a line of its own: no body under one.
        assertEquals(listOf(null, null, null), lines().map { it.ctx.body })

        run("releases.lua", "add sharkdp/fd")
        assertEquals(Triple("Watching sharkdp/fd", "4 repos", null), only())
        assertEquals("https://github.com/sharkdp/fd/releases", lines().single().ctx.url)
        run("releases.lua", "add SharkDP/fd")
        assertEquals(Triple("SharkDP/fd is watched already", null, StyledText.Colour.MUTED), only())
        run("releases.lua", "add fd")
        assertEquals(Triple("Usage: releases add owner/repo", null, StyledText.Colour.MUTED), only())
        run("releases.lua", "rm neovim/neovim")
        assertEquals(Triple("Stopped watching neovim/neovim", "3 repos left", null), only())
        run("releases.lua", "rm neovim/neovim")
        assertEquals(Triple("neovim/neovim isn't watched", null, StyledText.Colour.MUTED), only())
        run("releases.lua", "list")
        assertEquals(listOf("mtwabpin/kolahal", "termux/termux-app", "sharkdp/fd"), lines().map { it.text })
        run("releases.lua", "check")
        assertEquals(Triple("Usage: releases [add|rm owner/repo | list]", null, StyledText.Colour.MUTED), only())
    }

    /** A first check cut short isn't one done: the next run shows each repo's latest release again, and notifies none of the old ones. */
    @Test fun releasesFirstCheckCutShortIsntOneDone() {
        // Every repo has three releases, and a notification says so in the log.
        val feed = "rss = { fresh = function(url) return { items = { { title = 'v3', link = url }, { title = 'v2', link = url }, { title = 'v1', link = url } } } end }\n" +
            "notify = function(title, body) log('notified ' .. title .. ' ' .. body) end\n"
        // The run stops at its second line, as a timeout or Android would stop it.
        val cut = runCatching {
            run("releases.lua", before = feed + "local shown, logged = 0, log\nlog = function(...) shown = shown + 1; if shown == 2 then error('cut short') end; return logged(...) end\n")
        }
        assertEquals(true, cut.exceptionOrNull()?.message?.contains("cut short"))

        run("releases.lua", before = feed)
        // One line a repo, with no body: a list in normal weight, not a heading for each.
        assertEquals(
            listOf("mtwabpin/kolahal latest v3", "neovim/neovim latest v3", "termux/termux-app latest v3"),
            lines().map { StyledText.plain(it.text) },
        )
        assertEquals(listOf(null), lines().map { it.ctx.body }.distinct())
    }

    @Test fun chargeReminderSumsUpASession() {
        run("charge-reminder.lua")
        assertEquals(Triple("No charging session yet: plug in the charger", null, StyledText.Colour.MUTED), only())

        run("charge-reminder.lua", event = mapOf("name" to "charger", "level" to 15, "plug" to "usb"))
        assertEquals(emptyList<LogEntry>(), lines())
        assertEquals(true, status.get(name)?.startsWith("Charging since "))
        // Unplugged still below 30%: the warning colour, and how much it added under it.
        run("charge-reminder.lua", event = mapOf("name" to "unplug", "level" to 20, "plug" to "usb"))
        assertEquals(Triple("Unplugged at 20%", "+5% in 0m\n15%\t20%", StyledText.Colour.WARN), plainOnly())
        // A bar under it, in the same colour, from where the charge started (15%) to where it is (20%).
        val bar = StyledText.lines(lines().single().ctx.body!!).last().meter!!
        assertEquals(0.15f, bar.from, 0.001f)
        assertEquals(0.2f, bar.to, 0.001f)
        assertEquals(StyledText.Colour.WARN, bar.colour)
        assertNull(status.get(name))
        run("charge-reminder.lua", event = mapOf("name" to "charger", "level" to 20, "plug" to "ac"))
        run("charge-reminder.lua", event = mapOf("name" to "unplug", "level" to 90, "plug" to "ac"))
        assertEquals(Triple("Unplugged at 90%", "+70% in 0m\n20%\t90%", StyledText.Colour.GOOD), plainOnly())

        run("charge-reminder.lua")
        val (title, body) = only()
        assertEquals("Last charge", title)
        assertEquals(true, body?.endsWith(" · Unplugged at 90% (+70% in 0m)"))

        // Its low option: 20% is fine above 10%.
        run("charge-reminder.lua", event = mapOf("name" to "charger", "level" to 15, "plug" to "usb"), options = mapOf("low" to "10"))
        run("charge-reminder.lua", event = mapOf("name" to "unplug", "level" to 20, "plug" to "usb"), options = mapOf("low" to "10"))
        assertEquals(Triple("Unplugged at 20%", "+5% in 0m\n15%\t20%", StyledText.Colour.GOOD), plainOnly())
    }

    private val nm = context.getSystemService(android.app.NotificationManager::class.java)

    private fun at(time: String) = mapOf("name" to "at", "time" to time)

    /**
     * Do Not Disturb as you set it ([mode]: off, priority, alarms), waiting for Android to apply it.
     * From the shell: since Android 15 an app's own changes are a mode of its own, beside yours.
     */
    private fun youSet(mode: String, filter: Int) {
        shell("cmd notification set_dnd $mode")
        dndIs(filter)
    }

    private fun dndIs(filter: Int) {
        val until = System.currentTimeMillis() + 3_000
        while (nm.currentInterruptionFilter != filter && System.currentTimeMillis() < until) Thread.sleep(50)
        assertEquals(filter, nm.currentInterruptionFilter)
    }

    /** Night Do Not Disturb: on at its evening time and off in the morning, but only what it turned on. */
    @Test fun nightDndTurnsOffOnlyWhatItTurnedOn() {
        val had = nm.isNotificationPolicyAccessGranted
        shell("cmd notification allow_dnd ${context.packageName}")
        val all = android.app.NotificationManager.INTERRUPTION_FILTER_ALL
        val priority = android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
        val alarms = android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS
        try {
            // Kolahal's own off too: one left on would keep it on, whatever the shell sets.
            nm.setInterruptionFilter(all)
            youSet("off", all)
            run("night-dnd.lua", event = at("23:00"))
            dndIs(priority)
            assertEquals("Do Not Disturb on until 07:00", only().first)
            // A second run the same night changes nothing.
            run("night-dnd.lua", event = at("23:00"))
            assertEquals(0, lines().size)
            run("night-dnd.lua", event = at("07:00"))
            dndIs(all)
            assertEquals("Do Not Disturb off", only().first)
            assertNull(store.get("on"))

            // On already at night: yours, left alone at both times.
            youSet("alarms", alarms)
            run("night-dnd.lua", event = at("23:00"))
            run("night-dnd.lua", event = at("07:00"))
            dndIs(alarms)
            assertEquals(0, lines().size)

            // Run by hand: what it will do.
            run("night-dnd.lua")
            assertEquals("Do Not Disturb alarms", only().first)
        } finally {
            nm.setInterruptionFilter(all)
            shell("cmd notification set_dnd off")
            // Do Not Disturb access as it was.
            if (!had) shell("cmd notification disallow_dnd ${context.packageName}")
        }
    }

    /**
     * Night work profile, with `device.work` and `device.setWork` standing in for a work profile
     * (the emulator has none), its state kept in the script's store between runs.
     */
    private fun runWork(event: Map<String, Any?>?, canSet: Boolean = true) {
        lines().forEach { logs.delete(it.id) }
        val entry = library.entries.single { it.file == "night-work.lua" }
        val stub = """
            device.work = function() return store.get("_work") end
            device.setWork = function(on)
              if not $canSet then return false end
              store.set("_work", on and "on" or "paused")
              return true
            end
        """.trimIndent()
        val host = HostApi(context, name, scriptId = id, notifyEnabled = false)
        runBlocking { ScriptEngine.forLang(entry.lang).run(stub + "\n" + library.code(entry), name, host, 10_000, false, emptyList(), event) }
        host.flush()
    }

    @Test fun nightWorkResumesOnlyWhatItPaused() {
        store.set("_work", "on")
        runWork(at("23:00"))
        assertEquals("paused", store.get("_work"))
        assertEquals("Work profile paused until 07:00", only().first)
        runWork(at("07:00"))
        assertEquals("on", store.get("_work"))
        assertEquals("Work profile on", only().first)

        // Paused already at night: yours, left alone at both times.
        store.set("_work", "paused")
        runWork(at("23:00"))
        runWork(at("07:00"))
        assertEquals("paused", store.get("_work"))
        assertEquals(0, lines().size)

        // No work profile: nothing to do.
        store.set("_work", null)
        runWork(at("23:00"))
        assertEquals(0, lines().size)
        runWork(null)
        assertEquals("No work profile on this phone", only().first)

        // Android said no: it says why, and remembers nothing.
        store.set("_work", "on")
        runWork(at("23:00"), canSet = false)
        assertEquals(Triple("Couldn't pause the work profile", "Kolahal must be the default launcher (type home), with Alarms & reminders allowed", StyledText.Colour.BAD), only())
        assertNull(store.get("paused"))
    }

    /** The emulator has no work profile: device.work() says so with nil. */
    @Test fun noWorkProfileIsNil() {
        val host = HostApi(context, name, scriptId = id, notifyEnabled = false)
        runBlocking { ScriptEngine.forLang(com.mtwabpin.kolahal.data.ScriptLang.LUA).run("log(tostring(device.work()))", name, host, 10_000, false, emptyList(), null) }
        host.flush()
        assertEquals("nil", only().first)
    }
}
