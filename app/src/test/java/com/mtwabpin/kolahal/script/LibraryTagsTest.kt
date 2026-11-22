package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.SearchWords
import com.mtwabpin.kolahal.script.LibraryTag.COMMAND
import com.mtwabpin.kolahal.script.LibraryTag.EVENT
import com.mtwabpin.kolahal.script.LibraryTag.INTERNET
import com.mtwabpin.kolahal.script.LibraryTag.OFFLINE
import com.mtwabpin.kolahal.script.LibraryTag.TEMPLATE
import com.mtwabpin.kolahal.script.LibraryTag.TIMER
import com.mtwabpin.kolahal.script.LibraryTag.WIDGET
import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tags of the library's scripts ([LibraryTags.of]), and what the library page's search and
 * chips keep.
 * - The tags are checked on small scripts written here, and on every real file in assets/library.
 *   So a new library script must get tags that make sense.
 * - The search and the chips are checked on five scripts written here.
 */
class LibraryTagsTest {
    // ---- The tags of a script ----

    private fun tags(name: String, code: String) = LibraryTags.of(name, code).toList()

    @Test
    fun eachTagComesFromWhatTheScriptDeclares() {
        assertEquals(listOf(OFFLINE), tags("plain", "log('hello')"))
        assertEquals(listOf(TEMPLATE, OFFLINE), tags("template-plain", "log('hello')"))
        assertEquals(
            listOf(WIDGET, OFFLINE),
            tags("clock", "-- @widget clock 2x2\nwidget.show({})")
        )
        assertEquals(listOf(COMMAND, OFFLINE), tags("hello", "// @command\nlog('hello')"))
        assertEquals(listOf(TIMER, OFFLINE), tags("hourly", "-- @every 1h\nlog('hello')"))
        assertEquals(listOf(TIMER, OFFLINE), tags("daily", "// @at 07:00, 18:30\nlog('hello')"))
        assertEquals(
            listOf(EVENT, OFFLINE),
            tags("charger", "-- @on charger, unplug\nlog(event.name)")
        )
        assertEquals(listOf(INTERNET), tags("fetch", "local r = http.get('https://example.com')"))
    }

    /** The tags are in the order of the chips, whatever the order of the lines. */
    @Test
    fun aScriptCanHaveSeveralTags() {
        val code = "// @every 15m\n// @widget stat 4x1\n// @command\nconst r = await http.get(u);"
        assertEquals(listOf(WIDGET, COMMAND, TIMER, INTERNET), tags("cricket", code))
        assertEquals(
            listOf(TEMPLATE, WIDGET, COMMAND, TIMER, INTERNET),
            tags("template-cricket", code)
        )
    }

    /** Only the start of the file's name makes a template: not the word somewhere in it, and not a `@tags` line. */
    @Test
    fun aTemplateIsNamedSo() {
        assertFalse(TEMPLATE in LibraryTags.of("my-template", "-- @tags template\nlog(1)"))
        assertFalse(TEMPLATE in LibraryTags.of("templates", "log(1)"))
        assertTrue(TEMPLATE in LibraryTags.of("template-js", "log(1)"))
    }

    /** The directives are read as everywhere else: one that Kolahal doesn't use gives no tag. */
    @Test
    fun aLineKolahalDoesNotUseGivesNoTag() {
        // A widget of a kind Kolahal doesn't draw, an event it doesn't know, a timer that is off.
        assertEquals(
            listOf(OFFLINE),
            tags("odd", "-- @widget hologram\n-- @on earthquake\n-- @every off\nlog(1)")
        )
        // The words in the text of the script, not in a directive.
        assertEquals(
            listOf(OFFLINE),
            tags("words", "-- It has no @widget line, no @command line and no timer.\nlog(1)")
        )
    }

    /**
     * Internet is what gives a script its Internet switch ([ScriptAccess]): a web request, a feed,
     * or a link or a picture on one of its lines. Every script is Internet or Offline, never both.
     */
    @Test
    fun internetIsTheScriptsInternetSwitch() {
        for (code in listOf(
            "http.get(u)",
            "rss.fresh(u)",
            "log('x', { url = 'https://example.com' })",
            "log('x', { image = u })",
            "log('offline')"
        )) {
            val switch = ScriptAccess.INTERNET in ScriptAccess.items(code)
            assertEquals(code, switch, INTERNET in LibraryTags.of("x", code))
            assertEquals(code, !switch, OFFLINE in LibraryTags.of("x", code))
        }
    }

    // ---- The real library ----

    private val files = sourceFile("src/main/assets/library").listFiles().orEmpty()
        .mapNotNull { f -> ScriptLang.of(f.extension)?.let { f to it } }.sortedBy { it.first.name }

    /** The library's entries, made as [ScriptLibrary.entries] makes them. */
    private val library =
        files.map { (f, lang) -> ScriptLibrary.Entry.of(f.name, lang, f.readText()) }

    private fun named(tag: LibraryTag) = library.filter { tag in it.tags }.map { it.name }

    private fun tagsOf(name: String) = library.single { it.name == name }.tags.toList()

    /** The numbers on the chips when the page opens. A new script changes them: check that its tags are right, then change them here. */
    @Test
    fun theChipsOfTheLibrary() {
        assertEquals(
            mapOf(
                TEMPLATE to 16,
                WIDGET to 27,
                COMMAND to 24,
                TIMER to 26,
                EVENT to 5,
                INTERNET to 31,
                OFFLINE to 18
            ),
            LibraryTags.counts(library, emptyList()),
        )
        // The chips are in the order of LibraryTag.
        assertEquals(
            LibraryTag.entries.toList(),
            LibraryTags.counts(library, emptyList()).keys.toList()
        )
    }

    @Test
    fun someScriptsAndTheirTags() {
        assertEquals(listOf(WIDGET, COMMAND, TIMER, INTERNET), tagsOf("weather"))
        assertEquals(listOf(WIDGET, COMMAND, TIMER, OFFLINE), tagsOf("countdown"))
        assertEquals(listOf(WIDGET, TIMER, INTERNET), tagsOf("hn"))
        assertEquals(listOf(COMMAND, INTERNET), tagsOf("wikipedia"))
        assertEquals(listOf(EVENT, OFFLINE), tagsOf("charge-reminder"))
        assertEquals(listOf(WIDGET, EVENT, OFFLINE), tagsOf("unlock-chart"))
        assertEquals(listOf(WIDGET, OFFLINE), tagsOf("world-clocks"))
        assertEquals(listOf(TIMER, OFFLINE), tagsOf("night-dnd"))
        assertEquals(listOf(TEMPLATE, COMMAND, INTERNET), tagsOf("template-js"))
        assertEquals(listOf(TEMPLATE, TIMER, INTERNET), tagsOf("template-timer"))
        assertEquals(listOf(TEMPLATE, EVENT, OFFLINE), tagsOf("template-event-js"))
        assertEquals(listOf(TEMPLATE, WIDGET, INTERNET), tagsOf("template-widget"))
        assertEquals(listOf(TEMPLATE, WIDGET, TIMER, OFFLINE), tagsOf("template-widget-list-js"))
    }

    /** A template says so in its title too: "Template: timer". */
    @Test
    fun theTemplatesAreTheScriptsTitledTemplate() {
        assertEquals(
            library.filter { it.title.startsWith("Template") }.map { it.name },
            named(TEMPLATE)
        )
        assertEquals(16, named(TEMPLATE).size)
    }

    @Test
    fun theScriptsThatRunOnAnEvent() {
        assertEquals(
            listOf(
                "charge-reminder",
                "effect-on-word",
                "template-event-js",
                "template-event",
                "unlock-chart"
            ), named(EVENT)
        )
    }

    /**
     * The scripts that never use the internet. Each one whose first line says "No network" is
     * here; the others are templates, which say "Nothing leaves the phone" or name no site.
     */
    @Test
    fun theScriptsThatWorkOffline() {
        val offline = listOf(
            "charge-reminder",
            "countdown",
            "counter",
            "data-month",
            "effect-on-word",
            "habit",
            "night-dnd",
            "night-work",
            "quick-toggles",
            "screen-time-months",
            "screen-time",
            "template-event-js",
            "template-event",
            "template-widget-list-js",
            "template-widget-list",
            "unlock-chart",
            "unused-apps",
            "world-clocks",
        )
        assertEquals(offline, named(OFFLINE))
        val saysNoNetwork = files.filter { (f, _) -> "No network" in f.readLines().first() }
            .map { it.first.nameWithoutExtension }
        assertEquals(emptyList<String>(), saysNoNetwork - offline.toSet())
        assertEquals(14, saysNoNetwork.size)
    }

    /** A script that names the site it talks to has the Internet tag. */
    @Test
    fun aScriptThatTalksToASiteIsInternet() {
        val talks = files.filter { (f, _) -> "Talks to:" in f.readText() }
            .map { it.first.nameWithoutExtension }
        assertTrue("some scripts name their site", talks.size > 10)
        assertEquals(emptyList<String>(), talks - named(INTERNET).toSet())
    }

    @Test
    fun everyScriptIsInternetOrOffline() {
        for (e in library) assertEquals(e.name, 1, e.tags.count { it == INTERNET || it == OFFLINE })
    }

    /**
     * Every script says what it is or how it runs: a template, a widget, a command, a timer or an
     * event. One with none of these could only be run by hand from its sheet, and no chip but
     * Internet or Offline would find it.
     */
    @Test
    fun everyScriptHasAChipThatSaysWhatItIs() {
        val kinds = setOf(TEMPLATE, WIDGET, COMMAND, TIMER, EVENT)
        assertEquals(
            emptyList<String>(),
            library.filter { e -> e.tags.none { it in kinds } }.map { it.name })
    }

    /** The tags agree with what the row and the install show: the command, the timer, the events and the widget. */
    @Test
    fun theTagsAgreeWithTheEntry() {
        for ((f, _) in files) {
            val code = f.readText()
            val e = library.single { it.file == f.name }
            assertEquals(e.name, e.command, COMMAND in e.tags)
            assertEquals(
                e.name,
                e.suggestedEvery > 0 || ScriptMeta.at(code).isNotEmpty(),
                TIMER in e.tags
            )
            assertEquals(e.name, ScriptMeta.events(code).isNotEmpty(), EVENT in e.tags)
            assertEquals(e.name, ScriptAccess.WIDGET in ScriptAccess.items(code), WIDGET in e.tags)
            assertEquals(
                e.name,
                ScriptAccess.INTERNET in ScriptAccess.items(code),
                INTERNET in e.tags
            )
        }
    }

    /** Every script is found by its name and by each word of its title, alone and with one of its own chips on. */
    @Test
    fun everyScriptIsFoundByItsNameAndItsTitle() {
        for (e in library) for (word in listOf(e.name) + SearchWords.of(e.title)) {
            assertTrue("$word doesn't find ${e.name}", LibraryTags.found(e, listOf(word)))
            assertTrue(
                "$word doesn't find ${e.name} with ${e.tags.first()}",
                LibraryTags.shows(e, listOf(word), setOf(e.tags.first()))
            )
        }
    }

    // ---- The search and the chips ----

    private fun entry(name: String, title: String, description: String, vararg tags: LibraryTag) =
        ScriptLibrary.Entry(
            "$name.lua",
            name,
            ScriptLang.LUA,
            title,
            description,
            command = COMMAND in tags,
            suggestedEvery = 0,
            tags = tags.toSet()
        )

    private val sample = listOf(
        entry(
            "charge-reminder",
            "Charge reminder",
            "Charging session summary; warns when unplugged below 30%",
            EVENT,
            OFFLINE
        ),
        entry(
            "countdown",
            "Countdowns",
            "Days until a date, or the next birthday or anniversary",
            WIDGET,
            COMMAND,
            TIMER,
            OFFLINE
        ),
        entry(
            "hn",
            "Hacker News",
            "Hacker News front page: new stories since the last run",
            WIDGET,
            TIMER,
            INTERNET
        ),
        entry(
            "template-timer",
            "Template: timer",
            "A starting point for a script on a timer",
            TEMPLATE,
            TIMER,
            INTERNET
        ),
        entry(
            "weather",
            "Weather",
            "Current weather: weather [city]",
            WIDGET,
            COMMAND,
            TIMER,
            INTERNET
        ),
    )

    /** The names of the scripts the page lists with [query] typed and [chosen] on. */
    private fun shown(query: String, vararg chosen: LibraryTag) =
        sample.filter { LibraryTags.shows(it, SearchWords.of(query), chosen.toSet()) }
            .map { it.name }

    private val all = sample.map { it.name }

    @Test
    fun withNoSearchAndNoChipEveryScriptShows() {
        assertEquals(all, shown(""))
        assertEquals(all, shown("  "))
    }

    @Test
    fun aScriptIsFoundByItsNameItsTitleOrItsDescription() {
        assertEquals(listOf("hn"), shown("hn"))
        assertEquals(listOf("hn"), shown("hacker"))
        assertEquals(listOf("countdown"), shown("birthday"))
        // Part of a word is enough.
        assertEquals(listOf("charge-reminder"), shown("remind"))
        assertEquals(listOf("template-timer"), shown("templ"))
        assertEquals(emptyList<String>(), shown("zzqx"))
    }

    @Test
    fun caseAndSpacesAroundDontMatter() {
        assertEquals(listOf("hn"), shown("  HACKER "))
        assertEquals(listOf("weather"), shown("\tWeather"))
    }

    /** Each word can be in another part of the row: `countdown` is the name, `birthday` is in the description. */
    @Test
    fun everyWordMustBeFound() {
        assertEquals(listOf("countdown"), shown("countdown birthday"))
        assertEquals(listOf("hn"), shown("news  stories"))
        assertEquals(emptyList<String>(), shown("weather birthday"))
    }

    /** The tags are not searched: no row shows them as text. The chips are for them. */
    @Test
    fun theSearchDoesNotLookInTheTags() {
        assertEquals(emptyList<String>(), shown("offline"))
        assertEquals(emptyList<String>(), shown("widget"))
    }

    @Test
    fun aChipKeepsTheScriptsWithItsTag() {
        assertEquals(listOf("template-timer"), shown("", TEMPLATE))
        assertEquals(listOf("countdown", "hn", "weather"), shown("", WIDGET))
        assertEquals(listOf("charge-reminder"), shown("", EVENT))
        assertEquals(listOf("charge-reminder", "countdown"), shown("", OFFLINE))
    }

    /** Each chip adds its scripts: a script with the tag of any chip that is on shows. The list keeps its order. */
    @Test
    fun chipsAddUp() {
        assertEquals(listOf("charge-reminder", "template-timer"), shown("", TEMPLATE, EVENT))
        assertEquals(listOf("charge-reminder", "countdown", "weather"), shown("", COMMAND, EVENT))
        // A script with two of the tags shows once.
        assertEquals(listOf("countdown", "hn", "weather"), shown("", WIDGET, COMMAND))
        assertEquals(all, shown("", INTERNET, OFFLINE))
    }

    /** Together: the scripts the search finds among those the chips keep. */
    @Test
    fun theSearchAndTheChipsCombine() {
        assertEquals(listOf("countdown"), shown("date", WIDGET))
        assertEquals(listOf("hn"), shown("news", WIDGET, EVENT))
        assertEquals(listOf("template-timer"), shown("timer", TEMPLATE, EVENT))
        // The search finds weather, but it is no template.
        assertEquals(emptyList<String>(), shown("weather", TEMPLATE))
        // Taking the chip away, or the search, brings them back.
        assertEquals(listOf("weather"), shown("weather"))
        assertEquals(listOf("template-timer"), shown("", TEMPLATE))
    }

    /** With no search, a chip's number is how many scripts have its tag. */
    @Test
    fun eachChipCountsItsScripts() {
        assertEquals(
            mapOf(
                TEMPLATE to 1,
                WIDGET to 3,
                COMMAND to 2,
                TIMER to 4,
                EVENT to 1,
                INTERNET to 3,
                OFFLINE to 2
            ),
            LibraryTags.counts(sample, emptyList()),
        )
    }

    /** With a search, the numbers are for the scripts it found, so they agree with the list. A chip at 0 stays. */
    @Test
    fun theCountsFollowTheSearch() {
        assertEquals(
            mapOf(
                TEMPLATE to 0,
                WIDGET to 1,
                COMMAND to 0,
                TIMER to 1,
                EVENT to 0,
                INTERNET to 1,
                OFFLINE to 0
            ),
            LibraryTags.counts(sample, listOf("hacker")),
        )
        assertEquals(setOf(0), LibraryTags.counts(sample, listOf("zzqx")).values.toSet())
        for (tag in LibraryTag.entries) assertEquals(
            tag.label,
            shown("t", tag).size,
            LibraryTags.counts(sample, listOf("t"))[tag]
        )
    }

    /** A tag that no script has gets no chip. The search doesn't take a chip away. */
    @Test
    fun aTagThatNoScriptHasGetsNoChip() {
        val two = sample.filter { it.name == "hn" || it.name == "weather" }
        assertEquals(
            listOf(WIDGET, COMMAND, TIMER, INTERNET),
            LibraryTags.counts(two, emptyList()).keys.toList()
        )
        assertEquals(
            listOf(WIDGET, COMMAND, TIMER, INTERNET),
            LibraryTags.counts(two, listOf("zzqx")).keys.toList()
        )
        assertEquals(emptyMap<LibraryTag, Int>(), LibraryTags.counts(emptyList(), emptyList()))
    }

    /** The chips that are on are kept by id. One that this version doesn't have is left out. */
    @Test
    fun theChipsThatAreOnAreKeptById() {
        assertEquals(
            listOf(
                "template",
                "widget",
                "command",
                "timer",
                "event",
                "internet",
                "offline"
            ), LibraryTag.entries.map { it.id })
        assertEquals(setOf(WIDGET, OFFLINE), LibraryTags.known(listOf("offline", "gone", "widget")))
        assertEquals(
            listOf(WIDGET, OFFLINE),
            LibraryTags.known(listOf("offline", "widget")).toList()
        )
        assertEquals(emptySet<LibraryTag>(), LibraryTags.known(emptyList()))
    }
}
