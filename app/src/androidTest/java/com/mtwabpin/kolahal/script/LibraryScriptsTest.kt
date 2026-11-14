package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.Stores
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
import org.junit.Assert.assertTrue
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
    private val status = KvStore(context, Stores.STATUS)

    /** [options]: the ones you set; the rest are the script's defaults. [before]: code to run first, such as a stand-in for the network. */
    private fun run(file: String, argline: String = "", event: Map<String, Any?>? = null, options: Map<String, String> = emptyMap(), before: String = "") {
        lines().forEach { logs.delete(it.id) }
        val entry = library.entries.single { it.file == file }
        val declared = ScriptMeta.options(library.code(entry)).associate { it.name to it.default }
        // Its @secret lines, none set yet, as after an install.
        val secrets = HostApi.Secrets(ScriptMeta.secrets(library.code(entry)), emptyMap())
        // Its @widget line, as a run has it. The widget isn't placed here, so widget.show checks and stores nothing.
        val host = HostApi(context, name, scriptId = id, notifyEnabled = false, options = declared + options, secrets = secrets, widget = ScriptMeta.widget(library.code(entry)))
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
        assertEquals(Triple("Usage: countdown YYYY-MM-DD | add [yearly] YYYY-MM-DD label | rm N", null, StyledText.Colour.MUTED), only())

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

    /**
     * A birthday or an anniversary comes every year: it counts to its next day, never into the past,
     * and says which one it is (25th). A date without a year comes every year too.
     */
    @Test fun countdownYearlyCountsToTheNextOne() {
        val today = LocalDate.now()
        fun next(d: LocalDate) = d.withYear(today.year).let { if (it < today) d.withYear(today.year + 1) else it }
        fun days(d: LocalDate) = java.time.temporal.ChronoUnit.DAYS.between(today, next(d))
        fun every(d: LocalDate) = "every ${d.dayOfMonth} " + d.format(java.time.format.DateTimeFormatter.ofPattern("MMM", java.util.Locale.ENGLISH))
        val yearly = "Saved · every year · countdown lists them"

        val wedding = today.plusDays(10).minusYears(25)
        run("countdown.lua", "add yearly $wedding Wedding")
        val (title, body) = plainOnly()
        assertEquals("Wedding · ${next(wedding).year - wedding.year}th", title)
        assertTrue(body, body!!.startsWith("${days(wedding)} days to go") && body.endsWith(yearly))

        // Its day has passed this year: next year's, the 31st.
        val birthday = today.minusDays(3).minusYears(30)
        run("countdown.lua", "add yearly $birthday Asha")
        assertEquals("Asha · 31st", plainOnly().first)
        assertTrue(plainOnly().second, plainOnly().second!!.startsWith("${days(birthday)} days to go"))

        // No year: every year, with no count.
        val party = today.plusDays(2)
        run("countdown.lua", "add ${party.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd"))} Party")
        assertEquals(Triple("Party", "2 days to go · ${party.dayOfMonth} ${party.format(java.time.format.DateTimeFormatter.ofPattern("MMM EEEE", java.util.Locale.ENGLISH))}\n$yearly", StyledText.Colour.WARN), plainOnly())

        // Listed by when each comes next, with "every" and the day as the date.
        run("countdown.lua")
        assertEquals(
            listOf(
                "1\tParty\t${every(party)}\tin 2 days",
                "2\tWedding · 25th\t${every(wedding)}\tin ${days(wedding)} days",
                "3\tAsha · 31st\t${every(birthday)}\tin ${days(birthday)} days",
            ),
            StyledText.plain(lines().single().ctx.body!!).lines().filter { '\t' in it && it.first().isDigit() },
        )
        assertEquals("Party: in 2 days", status.get(name))
        run("countdown.lua", "rm 1")
        assertEquals("Removed Party", lines().single().shownText.lines().first())

        // A one-time date over a year ago says how to have it every year.
        val old = today.minusYears(5)
        run("countdown.lua", "add $old Met")
        assertTrue(plainOnly().second, plainOnly().second!!.endsWith("Every year instead: countdown add yearly $old Met"))
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
     * Code to run first: it keeps what the script hands to `widget.show` in the script's store, as
     * JSON, and passes it on. The widget isn't placed here, so `widget.show` checks the description
     * with the real parser and stores nothing: a field it refuses ends the run with an error.
     */
    private val jsKeepsShown = "const widget = { show: (d) => { store.set('_shown', JSON.stringify(d)); return globalThis.widget.show(d); } };\n"

    /** [jsKeepsShown] for a Lua script. */
    private val luaKeepsShown = "do local show = widget.show; widget = { show = function(d) store.set('_shown', json.encode(d)); return show(d) end } end\n"

    /** What the last run handed to `widget.show`, with every number as a double. Null when it handed nothing. It is forgotten once read. */
    private fun shown(): Any? = store.get("_shown")?.let {
        store.set("_shown", null)
        doubles(Json.parse(it))
    }

    private fun doubles(v: Any?): Any? = when (v) {
        is Map<*, *> -> v.mapValues { doubles(it.value) }
        is List<*> -> v.map(::doubles)
        is Number -> v.toDouble()
        else -> v
    }

    /** The event of a run that the widgets page asks for. */
    private val forWidget = mapOf("name" to "widget")

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

    /** The big numbers of a line's body: each value with its label, as they read. */
    private fun bigNumbers(body: String) = StyledText.lines(body).first { it.stats != null }.stats!!.map { s ->
        s.value.joinToString("") { it.text } to s.label.joinToString("") { it.text }
    }

    /** The command template, in Lua and in JavaScript: the same lines from the same words. */
    @Test fun theCommandTemplatesSayHello() {
        for (file in listOf("template-command.lua", "template-command-js.js")) {
            run(file, "Ada Lovelace")
            val titles = lines().map { StyledText.plain(it.text) }
            assertEquals(file, "Hello, Ada Lovelace!", titles.first())
            assertEquals(file, "You gave 2 words", lines().first().ctx.body?.let(StyledText::plain))
            // The returned "It's 10:42" is the run's result: this test runs the code, without logging it.
            assertEquals("$file: $titles", "The scripting guide", titles.last())
        }
    }

    /** The event template, in Lua and in JavaScript: plugged in at 40%, unplugged at 75%, it says what the charge added. */
    @Test fun theEventTemplatesSayWhatAChargeAdded() {
        for (file in listOf("template-event.lua", "template-event-js.js")) {
            run(file, event = mapOf("name" to "charger", "level" to 40, "plug" to "usb"))
            assertEquals(file, "Charging from 40%", status.get(name))
            run(file, event = mapOf("name" to "unplug", "level" to 75))
            val (title, body, _) = plainOnly()
            assertEquals(file, "Charged +35%", title)
            assertEquals(file, "40% → 75% in 0m", body)
        }
    }

    /**
     * The logging template, in Lua and in JavaScript: every part logs its line, from 1 to 21 (the result),
     * with the table, the bars and the big numbers as rich bodies, and the line that line.set changed.
     */
    @Test fun theLogsTemplatesShowEveryKindOfLine() {
        for (file in listOf("template-logs.lua", "template-logs-js.js")) {
            run(file)
            val shown = lines()
            val titles = shown.map { StyledText.plain(it.text) }
            for (n in 1..19) assertTrue("$file: part $n in $titles", titles.any { it.startsWith("$n · ") })
            assertTrue("$file: $titles", "19 · Done: line.set changed this line in place" in titles)
            assertTrue("$file: $titles", titles.none { it.startsWith("19 · Working") || it.startsWith("19 · Step") })
            fun body(title: String) = StyledText.lines(shown.first { StyledText.plain(it.text) == title }.ctx.body!!)
            assertTrue(file, body("11 · A table").isNotEmpty())
            assertTrue(file, body("13 · Big numbers").any { it.stats != null })
            assertEquals(file, "https://imgs.xkcd.com/comics/exploits_of_a_mom.png", shown.first { StyledText.plain(it.text) == "14 · A picture" }.ctx.values["image"])
        }
    }

    /**
     * Wiktionary: the word in your language, then one line per meaning, its sub-meanings apart and an
     * example under it; with no entry in your language, the languages that have one.
     */
    @Test fun wiktionaryShowsMeaningsInYourLanguage() {
        val answer = """{"other":[{"partOfSpeech":"Noun","language":"Alemannic German","definitions":[{"definition":"dog"}]}],""" +
            """"de":[{"partOfSpeech":"Noun","language":"German","definitions":[{"definition":"<a href=\"/wiki/dog\">dog</a>, hound"},""" +
            """{"definition":"scoundrel","parsedExamples":[{"example":"ein gemeiner <b>Hund</b>","translation":"a mean dog"}]}]}],""" +
            """"en":[{"partOfSpeech":"Verb","language":"English","definitions":[{"definition":"To move swiftly.\n<ol><li>To move forward quickly","examples":["Run!"]}]}]}"""
        run("wiktionary.js", "Hund", options = mapOf("lang" to "German"), before = standIns(answer))
        assertEquals(listOf("Hund  German", "noun  dog, hound", "noun  scoundrel", "2 more languages on Wiktionary"), lines().map { StyledText.plain(it.text) })
        assertEquals("“ein gemeiner Hund” (a mean dog)", lines()[2].ctx.body?.let(StyledText::plain))
        assertEquals("https://en.wiktionary.org/wiki/Hund#German", lines()[2].ctx.url)
        assertEquals("https://en.wiktionary.org/wiki/Hund", lines().last().ctx.url)

        run("wiktionary.js", "Hund", options = mapOf("lang" to "English"), before = standIns(answer))
        assertEquals("verb  To move swiftly.", StyledText.plain(lines()[1].text))
        assertEquals(null, lines()[1].ctx.body)

        run("wiktionary.js", "Hund", options = mapOf("lang" to "Hindi"), before = standIns(answer))
        val shown = lines().map { StyledText.plain(it.text) }
        assertEquals("No Hindi entry for 'Hund'. Other languages:", shown.first())
        // The languages with a code come before "other": German before Alemannic German.
        assertEquals(listOf("Hund  German", "Hund  English", "Hund  Alemannic German"), shown.filter { it.startsWith("Hund") })
    }

    /** One coin: its name, then its price large and the day's change in its colour. */
    @Test fun cryptoOneCoinShowsBigNumbers() {
        run("crypto.js", "bitcoin", before = standIns("""{"bitcoin":{"usd":67120.4,"usd_24h_change":-1.234}}"""))
        val e = lines().single()
        assertEquals("Bitcoin", e.text)
        assertEquals(listOf("67,120.40" to "BTC in USD", "▼ 1.2%" to "24 hours"), bigNumbers(e.ctx.body!!))
        assertEquals(StyledText.Colour.BAD, StyledText.lines(e.ctx.body!!).first().stats!![1].value.single().colour)
        assertEquals("https://www.coingecko.com/en/coins/bitcoin", e.ctx.url)
    }

    /** One currency: the value large, then the rate; the line copies the value. */
    @Test fun exchangeOneCurrencyShowsBigNumbers() {
        run("exchange.js", "100 usd inr", before = standIns("""{"result":"success","rates":{"INR":83.42,"USD":1}}"""))
        val e = lines().single()
        assertEquals("100 USD", e.text)
        assertEquals(listOf("8,342.00" to "INR", "83.420" to "1 USD in INR"), bigNumbers(e.ctx.body!!))
        assertEquals("8342.00", e.ctx.copy)
    }

    /**
     * Exchange's widget: one unit of its from option in each of its to options. A run for the
     * widget logs nothing. A run with arguments leaves the widget alone.
     */
    @Test fun exchangeFillsItsWidgetWithItsOwnCurrencies() {
        val rates = standIns("""{"result":"success","rates":{"USD":1,"EUR":0.92,"GBP":0.79,"INR":83.42,"JPY":149.5}}""") + jsKeepsShown
        fun row(code: String, rate: String) = mapOf("title" to code, "trailing" to rate)
        fun card(title: String, vararg rows: Map<String, String>) = mapOf(
            "title" to title, "items" to rows.toList(), "empty" to "No rates",
            "tap" to mapOf("args" to "", "page" to "log"), "staleAfter" to 43_200.0,
        )
        run("exchange.js", event = forWidget, before = rates)
        assertEquals(emptyList<LogEntry>(), lines())
        assertEquals(card("1 USD", row("EUR", "0.92000"), row("GBP", "0.79000"), row("INR", "83.420"), row("JPY", "149.50")), shown())
        assertEquals("USD: EUR 0.92000 · GBP 0.79000 · INR 83.420 · JPY 149.50", status.get(name))

        // Its options pick the currencies. One that isn't known is left out, and doesn't stop the run.
        run("exchange.js", event = forWidget, options = mapOf("from" to "usd", "to" to "inr, xxx"), before = rates)
        assertEquals(emptyList<LogEntry>(), lines())
        assertEquals(card("1 USD", row("INR", "83.420")), shown())

        // On its timer, and after a tap on the card, it fills the widget and logs the table too.
        run("exchange.js", before = rates)
        assertEquals("1 USD", lines().single().text)
        assertEquals(4, (shown() as Map<*, *>)["items"].let { it as List<*> }.size)

        // With arguments it converts, and the card keeps what it has.
        run("exchange.js", "100 usd inr", before = rates)
        assertEquals("100 USD", lines().single().text)
        assertNull(shown())
        run("exchange.js", "50", before = rates)
        assertNull(shown())
    }

    /** The index large in its band's colour, with the band under it; the 24-hour figure and PM2.5 beside it; the other readings in a table. */
    @Test fun aqiLeadsWithBigNumbers() {
        val hours = { v: Int -> List(24) { v }.joinToString(",", "[", "]") }
        val geo = """{"results":[{"name":"Pune","country_code":"IN","latitude":18.5,"longitude":73.8}]}"""
        val air = """{"current":{"european_aqi":60},"hourly":{"pm2_5":${hours(72)},"pm10":${hours(98)},"nitrogen_dioxide":${hours(31)},"sulphur_dioxide":${hours(5)},"carbon_monoxide":${hours(300)},"ozone":${hours(44)}}}"""
        val before = "const http = { get: async (url) => ({ ok: true, status: 200, body: url.includes('geocoding') ? ${com.mtwabpin.kolahal.script.Json.stringify(geo)} : ${com.mtwabpin.kolahal.script.Json.stringify(air)} }) };\n"
        run("aqi.js", "Pune", before = before)
        val e = lines().first { it.text.startsWith("Pune, IN") }
        val numbers = bigNumbers(e.ctx.body!!)
        assertEquals(listOf("last hour", "24 hours", "PM2.5 µg/m³"), numbers.map { it.second.substringAfter(" · ") })
        assertEquals("72", numbers[2].first)
        assertTrue(numbers[0].second, numbers[0].second.startsWith("Unhealthy"))
        val lines = StyledText.lines(e.ctx.body!!)
        assertTrue(lines.any { it.meter != null })
        assertEquals(listOf("PM10", "NO₂", "O₃"), lines.mapNotNull { it.row }.map { r -> r.cells[0].spans.joinToString("") { it.text } })
    }

    /** A live chase: the batting side's score large, then the runs to win and the balls left, which the summary said. */
    @Test fun cricketLiveShowsTheScoreBig() {
        val panel = """{"scores":[{"leagues":[{"id":"8000"}],"events":[{"id":"1","name":"India v New Zealand","date":"2026-11-08T08:00Z",
            "status":{"summary":"India require 64 runs with 60 balls remaining","type":{"state":"in","detail":"40 ov"}},
            "competitions":[{"description":"3rd ODI","class":{"internationalClassId":"2"},"competitors":[
              {"team":{"displayName":"India","shortDisplayName":"India","abbreviation":"IND"},"score":"237/4 (40/50 ov, target 300)"},
              {"team":{"displayName":"New Zealand","shortDisplayName":"New Zealand","abbreviation":"NZ"},"score":"299/8"}]}]}]}]}"""
        run("cricket.js", "India", before = standIns(panel.replace("\n", "")))
        val e = lines().single()
        assertEquals(listOf("237/4" to "India · 40 overs", "64" to "to win", "60" to "balls left"), bigNumbers(e.ctx.body!!))
        // The summary said the same, so it goes.
        assertTrue(e.ctx.body!!, !StyledText.plain(e.ctx.body!!).contains("require"))
    }

    /** A live match: the teams' names as the title, then the score large and the minute. */
    @Test fun footballLiveShowsTheScoreBig() {
        val board = """{"events":[{"id":"9","name":"Chelsea at Arsenal","date":"2026-11-08T15:00Z","status":{"displayClock":"67'","type":{"state":"in","shortDetail":"67'"}},
            "competitions":[{"competitors":[{"homeAway":"home","team":{"abbreviation":"ARS","displayName":"Arsenal"},"score":"2"},
            {"homeAway":"away","team":{"abbreviation":"CHE","displayName":"Chelsea"},"score":"1"}]}]}]}"""
        run("football.js", "Arsenal", before = standIns(board.replace("\n", "")))
        val e = lines().single()
        assertEquals("LIVE Arsenal v Chelsea", StyledText.plain(e.text))
        assertEquals(listOf("2–1" to "ARS v CHE", "67'" to "minute"), bigNumbers(e.ctx.body!!))
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
            // That day is over here, so the big number is its length, with "of daylight today" under it.
            val first = StyledText.lines(only().second!!).first().stats!!.first()
            return listOf(first.value, first.label).joinToString(" ") { s -> s.joinToString("") { it.text } }
        }
        assertEquals("11h 0m of daylight today", length(39_570))
        assertEquals("10h 50m of daylight today", length(39_000))
        assertEquals("10h 59m of daylight today", length(39_569))
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

    /**
     * Releases' widget: a row for each repo, with its newest release's tag and how old it is. It is
     * read with rss.fetch, so a release on the card is still new to the check. A run for the widget
     * checks nothing and logs nothing.
     */
    @Test fun releasesFillsItsWidgetAndLeavesTheCheckAlone() {
        val today = LocalDate.now()
        val ages = mapOf("mtwabpin/kolahal" to 3L, "x/today" to 0L, "x/yesterday" to 1L, "x/months" to 75L, "x/years" to 800L)
        // Each repo's newest release is as old as [ages] says, and has an older one after it. neovim
        // has none yet, termux can't be read, and x/undated has no date and no tag in its link.
        // A check (rss.fresh) leaves a mark in the store.
        val feeds = """
            local dates = { ${ages.entries.joinToString { (repo, days) -> "[\"$repo\"] = \"${today.minusDays(days)}T10:00:00Z\"" }} }
            local function feed(url)
              local repo = url:match("github%.com/(.-)/releases%.atom")
              if repo == "termux/termux-app" then error("HTTP 404") end
              if repo == "neovim/neovim" then return { items = {} } end
              if repo == "x/undated" then return { items = { { title = "First", link = "https://github.com/x/undated/releases/1" } } } end
              return { items = {
                { title = "Newest", link = "https://github.com/" .. repo .. "/releases/tag/v2.0", date = dates[repo] },
                { title = "Older", link = "https://github.com/" .. repo .. "/releases/tag/v1.0", date = "2020-01-01T10:00:00Z" },
              } }
            end
            rss = { fetch = feed, fresh = function(url) store.set("_checked", "1"); return feed(url) end }
            notify = function(title, body) log('notified ' .. title .. ' ' .. body) end
        """.trimIndent() + "\n" + luaKeepsShown
        val empty = "No repos: releases add owner/repo"
        fun release(repo: String, age: String?) = listOfNotNull(
            "title" to repo, "trailing" to "v2.0", age?.let { "detail" to it }, "tap" to mapOf("url" to "https://github.com/$repo/releases/tag/v2.0"),
        ).toMap()

        run("releases.lua", event = forWidget, before = feeds)
        assertEquals(emptyList<LogEntry>(), lines())
        assertNull(store.get("_checked"))
        assertEquals(
            mapOf(
                "items" to listOf(
                    release("mtwabpin/kolahal", "3 days ago"),
                    mapOf("title" to "neovim/neovim", "detail" to "no releases yet"),
                    mapOf("title" to "termux/termux-app", "detail" to "couldn't read its releases", "color" to "bad"),
                ),
                "empty" to empty, "staleAfter" to 21_600.0,
            ),
            shown(),
        )

        // Each age in its own words. A release with no date has no second line, and one with no tag has its name.
        for (repo in listOf("neovim/neovim", "termux/termux-app")) run("releases.lua", "rm $repo")
        for (repo in listOf("x/today", "x/yesterday", "x/months", "x/years", "x/undated")) run("releases.lua", "add $repo")
        run("releases.lua", event = forWidget, before = feeds)
        assertEquals(
            listOf(
                release("mtwabpin/kolahal", "3 days ago"), release("x/today", "today"), release("x/yesterday", "yesterday"),
                release("x/months", "2 months ago"), release("x/years", "2 years ago"),
                mapOf("title" to "x/undated", "trailing" to "First", "tap" to mapOf("url" to "https://github.com/x/undated/releases/1")),
            ),
            (shown() as Map<*, *>)["items"],
        )

        // No feed can be read: the run fails, and shows nothing, so the card keeps what it has.
        val offline = "rss = { fetch = function() error('offline') end }\n" + luaKeepsShown
        val failed = runCatching { run("releases.lua", event = forWidget, before = offline) }
        assertEquals(true, failed.exceptionOrNull()?.message?.contains("Couldn't read the releases"))
        assertNull(shown())

        // On its timer it fills the widget and then checks. What the card showed is still new to the check.
        run("releases.lua", before = feeds)
        assertEquals(6, ((shown() as Map<*, *>)["items"] as List<*>).size)
        assertEquals("1", store.get("_checked"))
        assertEquals("mtwabpin/kolahal latest Newest", StyledText.plain(lines().first().text))
        assertEquals(6, lines().size)

        // No repo is watched: the card says how to add one, and so does the log in a run by hand.
        for (repo in ages.keys + "x/undated") run("releases.lua", "rm $repo")
        run("releases.lua", event = forWidget, before = feeds)
        assertEquals(emptyList<LogEntry>(), lines())
        assertEquals(empty, (shown() as Map<*, *>)["empty"])
        run("releases.lua", before = feeds)
        assertEquals(Triple("No repos watched: releases add owner/repo", null, StyledText.Colour.MUTED), only())
    }

    /**
     * Habit: the streak is the days in a row up to today, or up to yesterday while today is open.
     * A tap on its widget marks today done and logs nothing. Run by hand, it logs the streak and the week.
     */
    @Test fun habitCountsTheDaysInARow() {
        val today = LocalDate.now()
        fun days(vararg back: Long) = back.joinToString(" ") { today.minusDays(it).toString() }
        // The week's line in the log: each day's name with a mark, the oldest first.
        fun week(vararg done: Int) = (6 downTo 0).joinToString("  ") { back ->
            val day = if (back == 0) "Today" else today.minusDays(back.toLong()).format(java.time.format.DateTimeFormatter.ofPattern("EEE", java.util.Locale.ENGLISH))
            "$day ${if (back in done) "✓" else "·"}"
        }
        fun card() = shown() as Map<*, *>
        // A tap on the widget, as the widgets page starts it.
        val tap = mapOf("name" to "action", "label" to "Done today", "widget" to true)

        // Nothing is done yet.
        run("habit.lua", before = luaKeepsShown)
        assertEquals(Triple("Exercise", "0 days in a row\n${week()}", null), plainOnly())
        assertEquals(listOf(0.0, List(7) { 0.0 }, null), card().let { listOf(it["value"], it["points"], it["color"]) })

        // Two days in a row up to yesterday, and one before a missed day: the streak is 2 while today is open.
        store.set("days", days(4, 2, 1))
        run("habit.lua", event = forWidget, before = luaKeepsShown)
        assertEquals(emptyList<LogEntry>(), lines())
        val first = today.minusDays(6).format(java.time.format.DateTimeFormatter.ofPattern("EEE", java.util.Locale.ENGLISH))
        assertEquals(
            mapOf(
                "title" to "Exercise", "value" to 2.0, "label" to "days in a row",
                "points" to listOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0), "style" to "bars", "min" to 0.0, "max" to 1.0,
                "labels" to listOf(first, "Today"),
                "summary" to (6 downTo 0).joinToString(", ") { back ->
                    val day = if (back == 0) "Today" else today.minusDays(back.toLong()).format(java.time.format.DateTimeFormatter.ofPattern("EEE", java.util.Locale.ENGLISH))
                    day + if (back in listOf(4, 2, 1)) " done" else if (back == 0) " not yet" else " missed"
                },
                "tap" to mapOf("args" to "done", "label" to "Done today"),
            ),
            card(),
        )

        // The tap marks today done: the streak is 3, in the good colour, and nothing is logged.
        run("habit.lua", "done", event = tap, before = luaKeepsShown)
        assertEquals(emptyList<LogEntry>(), lines())
        assertEquals(listOf(3.0, 1.0, "good"), card().let { listOf(it["value"], (it["points"] as List<*>).last(), it["color"]) })
        assertEquals(days(4, 2, 1, 0), store.get("days"))
        // A second tap changes nothing.
        run("habit.lua", "done", event = tap, before = luaKeepsShown)
        assertEquals(3.0, card()["value"])
        assertEquals(days(4, 2, 1, 0), store.get("days"))

        // Typed, it marks and unmarks today too, and logs the streak and the week.
        run("habit.lua", "undo", before = luaKeepsShown)
        assertEquals(Triple("Exercise", "2 days in a row\n${week(4, 2, 1)}", null), plainOnly())
        assertEquals(listOf(2.0, null), card().let { listOf(it["value"], it["color"]) })
        assertEquals(days(4, 2, 1), store.get("days"))
        run("habit.lua", "done", options = mapOf("name" to "Read"))
        assertEquals(Triple("Read", "3 days in a row\n${week(4, 2, 1, 0)}", null), plainOnly())
        run("habit.lua", "skip")
        assertEquals(Triple("Usage: habit [done|undo]", null, StyledText.Colour.MUTED), only())

        // Yesterday was missed: the streak starts again, with "day" for one.
        store.set("days", days(40, 3, 2))
        run("habit.lua", before = luaKeepsShown)
        assertEquals("0 days in a row", plainOnly().second!!.lines().first())
        run("habit.lua", "done", event = tap, before = luaKeepsShown)
        assertEquals(listOf(1.0, "day in a row"), card().let { listOf(it["value"], it["label"]) })
        // Only the streak's days and the last 7 days are kept: the day 40 days ago is gone.
        assertEquals(days(3, 2, 0), store.get("days"))

        // At its daily time it starts the widget's new day, and logs nothing.
        run("habit.lua", event = at("00:01"), before = luaKeepsShown)
        assertEquals(emptyList<LogEntry>(), lines())
        assertEquals(1.0, card()["value"])
    }

    @Test fun chargeReminderSumsUpASession() {
        run("charge-reminder.lua")
        assertEquals(Triple("No charging session yet: plug in the charger", null, StyledText.Colour.MUTED), only())

        run("charge-reminder.lua", event = mapOf("name" to "charger", "level" to 15, "plug" to "usb"))
        assertEquals(emptyList<LogEntry>(), lines())
        assertEquals(true, status.get(name)?.startsWith("Charging since "))
        // Unplugged still below 30%: the warning colour; under it, big numbers (the battery, what it added, how long) and the bar.
        run("charge-reminder.lua", event = mapOf("name" to "unplug", "level" to 20, "plug" to "usb"))
        assertEquals(Triple("Unplugged at 20%", "20% battery · +5% added · 0m charging\n15%\t20%", StyledText.Colour.WARN), plainOnly())
        // A bar under it, in the same colour, from where the charge started (15%) to where it is (20%).
        val bar = StyledText.lines(lines().single().ctx.body!!).last().meter!!
        assertEquals(0.15f, bar.from, 0.001f)
        assertEquals(0.2f, bar.to, 0.001f)
        assertEquals(StyledText.Colour.WARN, bar.colour)
        assertNull(status.get(name))
        run("charge-reminder.lua", event = mapOf("name" to "charger", "level" to 20, "plug" to "ac"))
        run("charge-reminder.lua", event = mapOf("name" to "unplug", "level" to 90, "plug" to "ac"))
        assertEquals(Triple("Unplugged at 90%", "90% battery · +70% added · 0m charging\n20%\t90%", StyledText.Colour.GOOD), plainOnly())

        run("charge-reminder.lua")
        val (title, body) = only()
        assertEquals("Last charge", title)
        assertEquals(true, body?.endsWith(" · Unplugged at 90% (+70% in 0m)"))

        // Its low option: 20% is fine above 10%.
        run("charge-reminder.lua", event = mapOf("name" to "charger", "level" to 15, "plug" to "usb"), options = mapOf("low" to "10"))
        run("charge-reminder.lua", event = mapOf("name" to "unplug", "level" to 20, "plug" to "usb"), options = mapOf("low" to "10"))
        assertEquals(Triple("Unplugged at 20%", "20% battery · +5% added · 0m charging\n15%\t20%", StyledText.Colour.GOOD), plainOnly())
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
