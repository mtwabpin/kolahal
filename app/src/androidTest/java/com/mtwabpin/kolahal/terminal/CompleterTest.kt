package com.mtwabpin.kolahal.terminal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.productivity.Todo
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** What the suggestion strip offers for what is typed (with the real app list and settings). */
@RunWith(AndroidJUnit4::class)
class CompleterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val config = Config.get(context)
    private val apps = AppRepository.get(context)

    private var history = emptyList<String>()
    private var todos = emptyList<Todo>()
    private val completer = Completer(context, object : Completer.Data {
        override val history get() = this@CompleterTest.history
        override val scripts = emptyList<Script>()
        override val commandNames = emptyList<String>()
        override val scriptUsages = emptyMap<String, String>()
        override val statusNames = emptySet<String>()
        override val feeds = emptyList<com.mtwabpin.kolahal.feeds.FeedInfo>()
        override val todos get() = this@CompleterTest.todos
        override val notes = emptyList<com.mtwabpin.kolahal.productivity.Note>()
        override val reminders = emptyList<com.mtwabpin.kolahal.productivity.Reminder>()
    })

    /** `log only …` suggests the kinds (and the log's sources, when there are any). */
    @Test fun logOnlySuggestsTheKinds() {
        val all = completer.suggestions("log only ").map { (it as Suggestion.Completion).replacement }
        assertTrue(all.toString(), "log only feeds" in all && "log only scripts" in all)
        assertEquals(listOf("log only feeds"), completer.suggestions("log only fe").map { (it as Suggestion.Completion).replacement })
    }

    @Before fun setUp() {
        apps.refresh()
        val deadline = System.currentTimeMillis() + 5_000
        while (apps.apps.value.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
    }

    @After fun tearDown() = Keys.ALL.forEach(config::reset)

    private fun labels(text: String) = completer.suggestions(text).map { it.label }
    private fun lines(text: String) = completer.suggestions(text).filterIsInstance<Suggestion.Completion>().map { it.replacement.trim() }

    /** Two apps of one name (one renamed to the other's): each completes to its package, and that names it. */
    @Test fun twoAppsOfOneNameCompleteApart() {
        val personal = apps.apps.value.filter { it.profile.kind == com.mtwabpin.kolahal.apps.ProfileKind.PERSONAL && !it.hidden }
        val (a, b) = personal.groupBy { it.pkg }.values.map { it.first() }.take(2)
        val prefs = com.mtwabpin.kolahal.apps.AppPrefs.get(context)
        try {
            prefs.setLabel(b.key, a.label)
            apps.refresh()
            val deadline = System.currentTimeMillis() + 5_000
            while (apps.apps.value.count { it.label == a.label } < 2 && System.currentTimeMillis() < deadline) Thread.sleep(50)
            val got = lines("uninstall ${a.label}")
            assertTrue(got.toString(), "uninstall ${a.pkg}" in got && "uninstall ${b.pkg}" in got)
            // And a package names its app exactly.
            assertEquals(b.key, apps.search(b.pkg).first().key)
        } finally {
            prefs.setLabel(b.key, null)
            apps.refresh()
        }
    }

    @Test fun firstWordOffersCommands() {
        assertTrue(labels("hel").toString(), "help" in labels("hel"))
        assertTrue(labels("cras").toString(), "crashes" in labels("cras"))
    }

    @Test fun aWholeCommandOffersItsOptions() {
        assertTrue(labels("restart").toString(), "restart scripts" in labels("restart"))
        assertTrue(lines("crashes ").toString(), lines("crashes ").containsAll(listOf("crashes share", "crashes clear")))
        assertTrue(lines("apps w").toString(), "apps work" in lines("apps w"))
    }

    /** `[days]` or `[n]` in a usage line is a value to type, not a word to offer. */
    @Test fun placeholdersAreNotOffered() {
        for (cmd in listOf("unlocks", "usage", "calls", "installs", "agenda")) {
            val offered = labels(cmd) + lines("$cmd ")
            assertFalse("$cmd offers $offered", offered.any { it.endsWith(" days") || it.endsWith(" n") })
        }
    }

    @Test fun configKeysThenValues() {
        assertTrue("font.size" in labels("config set font.s"))
        assertTrue(lines("config set clock.24h ").containsAll(listOf("config set clock.24h on", "config set clock.24h off")))
        assertTrue("amber" in labels("theme am"))
        assertTrue(labels("config set no.such.key ").isEmpty())
    }

    @Test fun fontOffersSansAndItsModes() {
        assertTrue(lines("font s").toString(), "font sans" in lines("font s"))
        assertTrue(lines("font sans p").toString(), "font sans prompt" in lines("font sans p"))
        assertTrue(lines("font sans ").containsAll(listOf("font sans off", "font sans prompt", "font sans all")))
    }

    @Test fun historyContinuesTheLine() {
        history = listOf("search kotlin flow", "search compose", "note buy milk")
        assertTrue(lines("search ko").toString(), "search kotlin flow" in lines("search ko"))
        assertFalse("search compose" in lines("search ko"))
    }

    @Test fun todoDoneOffersOnlyOpenItems() {
        val now = System.currentTimeMillis()
        todos = listOf(Todo(1, "milk", false), Todo(2, "bread", true))
        assertTrue(lines("todo done ").toString(), "todo done 1" in lines("todo done "))
        assertFalse("todo done 2" in lines("todo done "))
        assertTrue("todo undo 2" in lines("todo undo "))
    }

    /** Commands and settings by what they do: `flash` finds torch, `silent` ring, `24` the 24-hour clock. */
    @Test fun commandsAndSettingsByWhatTheyDo() {
        assertTrue(labels("flash").toString(), "torch" in labels("flash"))
        assertTrue(labels("silent").toString(), "ring" in labels("silent"))
        assertTrue(labels("music").toString(), "media" in labels("music"))
        assertTrue(lines("24").toString(), "config set clock.24h" in lines("24"))
        // A command's own name doesn't bring in others that mention it.
        assertTrue(labels("clear").none { it == "crashes" || it == "notifs" })
        assertTrue(labels("fl").none { it == "torch" })
    }

    /** `= …` shows the answer while you type; a tap puts it in the prompt. */
    @Test fun theAnswerShowsAsYouType() {
        val first = completer.suggestions("= 2^10").first() as Suggestion.Completion
        assertEquals("= 1024", first.label)
        assertEquals("= 1024", first.replacement)
        assertEquals("5 km = 3.1069 mi", completer.suggestions("= 5 km in mi").first().label)
        assertTrue(completer.suggestions("= 2 +").isEmpty())
    }

    @Test fun theTypedLineIsNotOfferedAgain() {
        history = listOf("echo hi")
        assertFalse(lines("echo hi").contains("echo hi"))
    }

    @Test fun appsAreSuggestedByName() {
        val app = apps.apps.value.first { it.profile.kind == ProfileKind.PERSONAL && it.label.length >= 4 }
        val found = completer.suggestions(app.label.take(4)).filterIsInstance<Suggestion.App>()
        assertTrue("${app.label} not in ${found.map { it.label }}", found.any { it.app.key == app.key })
    }

    /** Two letters swapped still find the app, when nothing matches as typed; it never launches as you type. */
    @Test fun aSlipStillFindsTheApp() {
        val (app, slip) = apps.apps.value.asSequence()
            .filter { it.profile.kind == ProfileKind.PERSONAL && it.label.length >= 5 && it.label.all(Char::isLetter) }
            .map { it to it.label.lowercase().let { l -> "" + l[1] + l[0] + l.drop(2) } }
            .first { (_, slip) -> apps.searchRanked(slip).all { it.second == 5 } }
        assertTrue("$slip should find ${app.label}", completer.suggestions(slip).any { it is Suggestion.App && it.app.key == app.key })
        assertTrue(InstantRun.target(slip, InstantRun.APPS, 2, rankedApps = { apps.searchRanked(it) }, appLabel = { it.label },
            commands = emptyMap(), aliases = emptyMap(), scriptCommands = emptyList()) == null)
    }

    @Test fun emptyPromptFollowsSuggestionsIdle() {
        assertTrue(completer.suggestions("").isEmpty())
        config.set(Keys.SUGGEST_IDLE, "on")
        // Most-used apps, never more than `suggestions`.
        val app = apps.apps.value.first { it.profile.kind == ProfileKind.PERSONAL }
        com.mtwabpin.kolahal.apps.AppPrefs.get(context).recordLaunch(app.key)
        val idle = completer.suggestions("")
        assertTrue(idle.toString(), idle.size <= config[Keys.SUGGESTIONS] && idle.all { it is Suggestion.App })
        assertTrue(idle.toString(), idle.any { (it as Suggestion.App).app.key == app.key })
    }
}

/** The completions review (0.4.3): synonyms, ordering, case, what each argument offers, icons. */
@RunWith(AndroidJUnit4::class)
class CompleterReviewTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val config = Config.get(context)
    private val apps = AppRepository.get(context)

    private var history = emptyList<String>()
    private var todos = emptyList<Todo>()
    private var library = listOf("hn", "xkcd")
    private var shared = listOf("moon")
    private val completer = Completer(context, object : Completer.Data {
        override val history get() = this@CompleterReviewTest.history
        override val scripts = listOf(
            Script(1, "ona", com.mtwabpin.kolahal.data.ScriptLang.LUA, "-- @command\n-- @desc Says hi\nreturn 1", 0, true, false, null, null),
            Script(2, "offb", com.mtwabpin.kolahal.data.ScriptLang.LUA, "return 1", 0, false, false, null, null),
        )
        override val commandNames = listOf("ona")
        override val scriptUsages = emptyMap<String, String>()
        override val scriptDescriptions = mapOf("ona" to "Says hi")
        override val statusNames = emptySet<String>()
        override val feeds = emptyList<com.mtwabpin.kolahal.feeds.FeedInfo>()
        override val todos get() = this@CompleterReviewTest.todos
        override val notes = emptyList<com.mtwabpin.kolahal.productivity.Note>()
        override val reminders = emptyList<com.mtwabpin.kolahal.productivity.Reminder>()
        override val libraryNames get() = library
        override val sharedNames get() = shared
    })

    @Before fun setUp() {
        apps.refresh()
        val deadline = System.currentTimeMillis() + 5_000
        while (apps.apps.value.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
    }

    @After fun tearDown() = Keys.ALL.forEach(config::reset)

    private fun rows(text: String) = completer.suggestions(text).filterIsInstance<Suggestion.Completion>()
    private fun lines(text: String) = rows(text).map { it.replacement.trim() }
    private fun labels(text: String) = completer.suggestions(text).map { it.label }

    @Test fun synonymsCompleteLikeTheirCommandAndKeepTheirSpelling() {
        assertTrue(lines("ls wor").toString(), "ls work" in lines("ls wor"))
        assertTrue(lines("cls hi").toString(), "cls history" in lines("cls hi"))
        assertTrue(lines("screentime ").toString(), "screentime 7" in lines("screentime "))
    }

    /** Enter runs the command typed: it is the first row, marked, then its options, then longer commands. */
    @Test fun aWholeCommandsOptionsComeBeforeLongerCommands() {
        val all = completer.suggestions("feed")
        assertEquals("feed", all.first().label)
        assertTrue(all.first().enter)
        assertEquals("feed add", all[1].label)
        assertTrue(labels("feed").toString(), "feeds" in labels("feed"))
        assertTrue(all.drop(1).none { it.enter })
    }

    /** Only a row Enter acts on is marked: not `torch on` when Enter toggles, but the app `open` picks. */
    @Test fun theEnterRowIsWhatEnterDoes() {
        assertEquals("torch", labels("torch").first())
        assertTrue(labels("torch").toString(), "torch on" in labels("torch"))
        assertTrue(completer.suggestions("torch").first().enter)
        assertTrue(completer.suggestions("torch ").none { it.enter })
        // Part of a command's name, and no app's: Enter says it isn't one.
        val partial = listOf("torc", "restar", "crashe").first { apps.searchRanked(it).isEmpty() }
        assertTrue(completer.suggestions(partial).none { it.enter })
        // One word, and not a command's name, so Enter launches it.
        val app = apps.apps.value.first {
            it.profile.kind == ProfileKind.PERSONAL && it.label.length >= 4 && ' ' !in it.label &&
                it.label.lowercase() !in CommandCatalog.COMMAND_NAMES && it.label.lowercase() !in InstantRun.SYNONYMS
        }
        val best = apps.search(app.label.take(4)).first()
        val open = completer.suggestions("open ${app.label.take(4)}")
        assertTrue(open.toString(), (open.first() as Suggestion.App).app == best && open.first().enter)
        assertTrue(completer.suggestions(best.label).first().let { it is Suggestion.App && it.app == best && it.enter })
    }

    /** Among the commands; an app Enter would launch still comes before them. */
    @Test fun commandsUsedLatelyComeFirst() {
        history = listOf("todo milk", "help")
        assertEquals("todo", rows("t").first().label)
        history = emptyList()
        assertEquals("torch", rows("t").first().label)
    }

    @Test fun namesMatchInAnyCase() {
        assertTrue(labels("theme Am").toString(), "amber" in labels("theme Am"))
        assertTrue(labels("config set Font.S").toString(), "font.size" in labels("config set Font.S"))
        assertTrue(lines("script every ona ").toString(), "script every ona 1h" in lines("script every ona "))
    }

    @Test fun scriptSubcommandsOfferWhatTheyTake() {
        assertEquals(listOf("script install xkcd"), lines("script install x"))
        assertEquals(listOf("script get moon"), lines("script get m"))
        assertTrue(lines("script ls ").isEmpty())
        assertEquals(listOf("script on offb"), lines("script on "))
        assertEquals(listOf("script off ona"), lines("script off "))
        assertTrue(lines("script notify ona ").containsAll(listOf("script notify ona on", "script notify ona off")))
    }

    @Test fun valuesAfterAnId() {
        assertTrue(lines("feed notify 3 ").containsAll(listOf("feed notify 3 on", "feed notify 3 off")))
        assertTrue(lines("feed every 3 ").toString(), "feed every 3 30m" in lines("feed every 3 "))
        assertTrue(lines("config set gesture.double-tap ").containsAll(listOf("config set gesture.double-tap lock", "config set gesture.double-tap none")))
        assertTrue(lines("remind ").toString(), "remind exact" in lines("remind "))
        assertTrue(labels("agenda ").toString(), labels("agenda ").containsAll(listOf("today", "3 days")))
    }

    @Test fun staleNumberedLinesAndCoveredLinesAreLeftOut() {
        val now = System.currentTimeMillis()
        todos = listOf(Todo(1, "milk", false))
        history = listOf("todo done 3", "todo done 1")
        assertEquals(listOf("todo done 1"), lines("todo done "))
        val app = apps.apps.value.first { it.profile.kind == ProfileKind.PERSONAL && it.label.length >= 4 }
        history = listOf("open ${app.label.lowercase()}")
        assertTrue(lines("open ${app.label.take(4)}").none { it.equals("open ${app.label}", ignoreCase = true) })
    }

    /** Places and months complete; `time` like `date`, keeping its spelling. */
    @Test fun datePlacesAndCalMonthsComplete() {
        assertTrue(lines("date new y").toString(), "date New York" in lines("date new y"))
        assertTrue(lines("date london tok").toString(), "date london Tokyo" in lines("date london tok"))
        assertTrue(lines("time tok").toString(), "time Tokyo" in lines("time tok"))
        val month = java.time.Month.DECEMBER.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()).lowercase()
        assertTrue(lines("cal ${month.take(3)}").toString(), "cal $month" in lines("cal ${month.take(3)}"))
        assertTrue(lines("cal $month 2").isEmpty())
    }

    @Test fun personalAppsCarryNoTagAndRowsHaveIcons() {
        val app = apps.apps.value.first { it.profile.kind == ProfileKind.PERSONAL && it.label.length >= 4 }
        assertTrue(labels("info ${app.label.take(4)}").toString(), app.label in labels("info ${app.label.take(4)}"))
        assertTrue(rows("log only ").all { it.icon == com.mtwabpin.kolahal.R.drawable.ic_visibility })
        assertEquals("Says hi", rows("on").first { it.label == "ona" }.help)
        assertEquals(com.mtwabpin.kolahal.R.drawable.ic_code, rows("on").first { it.label == "ona" }.icon)
    }
}
