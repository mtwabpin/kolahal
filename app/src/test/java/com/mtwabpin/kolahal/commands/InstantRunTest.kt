package com.mtwabpin.kolahal.commands

import com.mtwabpin.kolahal.commands.InstantRun.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstantRunTest {
    private val apps = listOf("Groww", "Grove", "Settings", "Calendar", "Camera", "YouTube", "youtube", "Google Maps")
    private val commands = mapOf(
        "settings" to "settings", "setup" to "setup [text]", "call" to "call <name|number>",
        "help" to "help", "history" to "history", "home" to "home", "theme" to "theme <name>", "system" to "system",
    )

    /** As app search ranks them ([AppRepository.tierOf]), with slips only when nothing else matched. */
    private fun ranked(q: String): List<Pair<String, Int>> {
        fun words(l: String) = l.split(' ', '-', '_', '.')
        val matched = apps.mapNotNull { a ->
            val l = com.mtwabpin.kolahal.apps.Fuzzy.fold(a)
            com.mtwabpin.kolahal.apps.AppRepository.tierOf(l, words(l), "", "", q)?.let { a to it }
        }
        return matched.ifEmpty {
            apps.filter { a -> com.mtwabpin.kolahal.apps.Fuzzy.fold(a).let { com.mtwabpin.kolahal.apps.Fuzzy.slip(q, listOf(it) + words(it)) } }.map { it to 5 }
        }.sortedBy { it.second }
    }

    private fun target(text: String, mode: String = InstantRun.ALL, aliases: Map<String, String> = emptyMap()) =
        InstantRun.target(text, mode, 2, ::ranked, { it }, commands, aliases, listOf("weather"))

    @Test fun uniqueAppPrefixLaunches() {
        assertEquals(Target.App("Grove"), target("grov"))
        assertEquals(Target.App("Groww"), target("grow"))
        assertNull("gro is ambiguous", target("gro"))
        assertEquals(Target.App("YouTube"), target("yo", InstantRun.APPS))
    }

    @Test fun minimumLength() {
        assertNull(target("y"))
    }

    @Test fun commandWinsOverSameNamedApp() {
        assertEquals(Target.Command("settings"), target("setti"))
        assertNull("settings and setup", target("set"))
    }

    @Test fun appsModeIgnoresCommandsAndNeverLaunchesOverThem() {
        assertNull(target("setti", InstantRun.APPS))
        assertNull(target("hel", InstantRun.APPS))
    }

    @Test fun offDoesNothing() {
        assertNull(target("grov", InstantRun.OFF))
    }

    @Test fun sensitiveAndArgumentCommandsNeedEnter() {
        assertNull("call is sensitive", target("call"))
        assertNull("theme needs an argument", target("them"))
        assertEquals(Target.Command("help"), target("he"))
        // Leaves the launcher: never on a prefix typed on the way to something else.
        assertNull("system opens Android's settings", target("sy"))
        assertNull("battery opens a settings page", target("ba"))
    }

    @Test fun aliasesFollowTheirExpansion() {
        assertEquals(Target.Command("st"), target("st", aliases = mapOf("st" to "settings")))
        assertFalse(InstantRun.canRun("mom", commands, mapOf("mom" to "call mom"), emptyList()))
        assertTrue(InstantRun.canRun("s", commands, mapOf("s" to "settings"), emptyList()))
        assertFalse("chained aliases are followed", InstantRun.canRun("a", commands, mapOf("a" to "b", "b" to "clear all"), emptyList()))
        assertFalse("alias cycles are refused", InstantRun.canRun("a", commands, mapOf("a" to "b", "b" to "a"), emptyList()))
    }

    @Test fun historyLines() {
        assertTrue(InstantRun.canRun("settings", commands, emptyMap(), emptyList()))
        assertFalse("changing the theme needs Enter", InstantRun.canRun("theme gruvbox", commands, emptyMap(), emptyList()))
        assertTrue(InstantRun.canRun("history", commands, emptyMap(), emptyList()))
        assertTrue("an app name", InstantRun.canRun("grove", commands, emptyMap(), emptyList()) { it == "grove" })
        assertFalse("unknown words are not apps by default", InstantRun.canRun("grove", commands, emptyMap(), emptyList()))
        assertFalse("synonym of a sensitive command", InstantRun.canRun("cls", commands, emptyMap(), emptyList()))
        assertFalse("synonym of a sensitive command", InstantRun.canRun("reboot", commands, emptyMap(), emptyList()))
        assertFalse("optional-argument commands wait for Enter", InstantRun.canRun("setup", commands, emptyMap(), emptyList()))
        assertFalse(InstantRun.canRun("call mom", commands, emptyMap(), emptyList()))
        // Tapped in the log (a contact, a history line) a call runs: the dialer still waits for Call.
        assertTrue(InstantRun.canRun("call mom", commands, emptyMap(), emptyList(), tapped = true))
        assertTrue(InstantRun.canRun("mom", commands, mapOf("mom" to "call mom"), emptyList(), tapped = true))
        assertFalse("only a call", InstantRun.canRun("sms mom hi", commands, emptyMap(), emptyList(), tapped = true))
        assertFalse(InstantRun.canRun("work off", commands, emptyMap(), emptyList()))
        assertFalse(InstantRun.canRun("weather london", commands, emptyMap(), listOf("weather")))
    }

    /** What a script may not send through launcher.run, though a tap in the log runs a call. */
    @Test fun clearingTheFeedAndCallingAreSensitive() {
        assertTrue(InstantRun.isSensitive("notifications-feed clear"))
        assertTrue("a script may not open the dialer", InstantRun.isSensitive("call mom"))
    }

    @Test fun scriptCommandsWithAKnownUsage() {
        // Script usages sit under the built-ins in the map the view model passes.
        val withScripts = mapOf("quote" to "quote", "xkcd" to "xkcd [random|<number>]") + commands
        val scripts = listOf("quote", "xkcd", "weather")
        fun target(text: String) = InstantRun.target(text, InstantRun.ALL, 2, ::ranked, { it }, withScripts, emptyMap(), scripts)
        assertEquals("no arguments: runs as typed", Target.Command("quote"), target("quo"))
        assertNull("optional arguments wait for Enter", target("xkcd"))
        assertNull("unknown usage waits for Enter", target("weat"))
        assertTrue(InstantRun.canRun("xkcd random", withScripts, emptyMap(), scripts))
        assertFalse(InstantRun.canRun("weather london", withScripts, emptyMap(), scripts) { true })
    }

    @Test fun wholeLinesWithNothingLeftToChoose() {
        val usages = mapOf(
            "torch" to "torch [on|off]", "dnd" to "dnd [on|off|alarms|none|access]", "volume" to "volume [0-100|up|down|mute]",
            "apps" to "apps [work|personal|private|hidden] [-l]", "work" to "work [on|off|status]",
            "feed" to "feed [add <url>|rm|fetch] [id]", "media" to "media [play|pause|toggle|next|prev|stop]",
            "xkcd" to "xkcd [random|<number>]", "ring" to "ring [normal|on|once]",
        )
        fun target(text: String, mode: String = InstantRun.ALL, aliases: Map<String, String> = emptyMap()) =
            InstantRun.target(text, mode, 2, ::ranked, { it }, usages, aliases, listOf("xkcd"))
        assertEquals(Target.Command("torch on"), target("torch on"))
        assertEquals(Target.Command("dnd off"), target("dnd off"))
        assertEquals(Target.Command("apps work -l"), target("apps work -l"))
        assertEquals(Target.Command("media pause"), target("media pause"))
        assertNull("not finished", target("torch o"))
        assertNull("-l may follow", target("apps work"))
        assertNull("a number could go there", target("volume up"))
        assertNull("a number could go there", target("xkcd random"))
        assertNull("rm needs no value but the id may follow", target("feed rm"))
        assertNull("'once' starts with 'on'", target("ring on"))
        assertEquals(Target.Command("ring once"), target("ring once"))
        assertNull("profile switches stay sensitive", target("work off"))
        assertNull("only in all mode", target("torch on", InstantRun.APPS))
        assertNull("aliases wait for Enter", target("torch on", aliases = mapOf("torch" to "work")))
        assertFalse("not by the history rule", InstantRun.canRun("torch on", usages, emptyMap(), emptyList()))
        assertTrue("but a tapped line with nothing left to choose runs", InstantRun.wholeLine("Torch on", usages, emptyMap()))
        assertFalse(InstantRun.wholeLine("work off", usages, emptyMap()))
    }

    @Test fun sameAppInTwoProfilesThatAreOn() {
        data class App(val label: String, val on: Boolean)
        val personal = App("Gmail", on = true)
        fun target(work: App) = InstantRun.target(
            "gm", InstantRun.ALL, 2, { q -> listOf(personal, work).filter { it.label.lowercase().startsWith(q) }.map { it to 1 } },
            { it.label }, commands, emptyMap(), emptyList(), isOn = { it.on },
        )
        assertNull("work profile on: both Gmails are a choice", target(App("Gmail", on = true)))
        assertEquals("work profile paused: launches as before", Target.App(personal), target(App("Gmail", on = false)))
    }

    /** `help` takes a command (`help todo`): it waits for Enter, and so does `help t` on the way to `help todo`. */
    @Test fun helpWaitsForItsWord() {
        assertFalse(InstantRun.canRun("help", CommandCatalog.COMMAND_USAGES, emptyMap(), emptyList()))
        assertFalse(InstantRun.wholeLine("help t", CommandCatalog.COMMAND_USAGES, emptyMap()))
    }

    @Test fun calculatorLinesAreSensitive() {
        assertTrue("no space after =", InstantRun.isSensitive("=notify('Bank', 'x')"))
        assertTrue(InstantRun.isSensitive("= 6*7"))
        assertTrue(InstantRun.isSensitive("pin gpay"))
    }

    @Test fun anyKindOfSpaceAfterTheNameStillCounts() {
        assertTrue(InstantRun.isSensitive("clear\tall"))
        assertTrue(InstantRun.isSensitive("clear\u00A0all"))
        assertTrue(InstantRun.isSensitive("c\tall", mapOf("c" to "clear")))
    }

    @Test fun feedFetchesAndCrashReportsAreSensitive() {
        assertTrue(InstantRun.isSensitive("rss https://example.com/feed"))
        assertTrue(InstantRun.isSensitive("crashes clear"))
        // Opens the browser: typing `so` on the way to something else mustn't.
        assertTrue(InstantRun.isSensitive("source"))
        assertTrue(InstantRun.isSensitive("wallpaper match --yes"))
    }

    @Test fun scriptsAndStatusAreSensitive() {
        assertTrue(InstantRun.isSensitive("scripts rm hn"))
        assertTrue(InstantRun.isSensitive("status clear all"))
    }

    /** What a script may not do through launcher.run, beyond the first word. */
    @Test fun shortcutsResumesPermissionsAndPanelsAreSensitive() {
        assertTrue(InstantRun.isSensitive("open phone:mom"))
        assertTrue(InstantRun.isSensitive("launch maps -w --resume"))
        assertTrue(InstantRun.isSensitive("contacts --allow"))
        assertTrue(InstantRun.isSensitive("calls --settings"))
        assertTrue(InstantRun.isSensitive("wifi"))
        assertTrue(InstantRun.isSensitive("bluetooth"))
        assertTrue(InstantRun.isSensitive("info maps"))
        assertTrue(!InstantRun.isSensitive("open maps"))
        assertTrue(!InstantRun.isSensitive("search how to resume"))
    }

    /** `torch|head` runs `torch`, spaces or not. */
    @Test fun aPipeCantCarryASensitiveCommandPastTheCheck() {
        for (line in listOf("torch|head", "dnd|head", "restart|head", "wifi|head", "lock|head", "TORCH|head", "clear all|head")) {
            assertTrue(line, InstantRun.isSensitive(line))
        }
        for (line in listOf("contacts --allow|head", "calls --settings|head", "open maps -w --resume|head", "open phone:mom|head")) {
            assertTrue(line, InstantRun.isSensitive(line))
        }
        assertTrue(InstantRun.isSensitive("c|head", mapOf("c" to "clear")))
        assertTrue(InstantRun.isSensitive("a", mapOf("a" to "c|head", "c" to "clear")))
        assertFalse(InstantRun.isSensitive("help|grep feed"))
        assertFalse(InstantRun.isSensitive("w|head", mapOf("w" to "weather")))
    }

    @Test fun aliasesCantCarrySensitiveCommandsPastTheCheck() {
        val aliases = mapOf("c" to "clear", "x" to "c all", "w" to "weather")
        assertTrue(InstantRun.isSensitive("c", aliases))
        assertTrue(InstantRun.isSensitive("x", aliases))
        assertFalse(InstantRun.isSensitive("w london", aliases))
        assertFalse(InstantRun.isSensitive("help", aliases))
        // A cycle deeper than the dispatcher expands is refused.
        assertTrue(InstantRun.isSensitive("a", mapOf("a" to "b", "b" to "a")))
    }

    /**
     * Only prefix matches launch: a word of the name (`maps` for Google Maps) does, but a part of
     * one (`ndar`), letters in order (`cmr`) or a slip (`cmaera`) only suggest.
     */
    @Test fun onlyPrefixMatchesLaunch() {
        assertEquals(listOf("Google Maps" to 2), ranked("maps"))
        assertEquals(Target.App("Google Maps"), target("maps"))
        assertEquals(listOf("Calendar" to 3), ranked("ndar"))
        assertNull(target("ndar"))
        assertEquals(listOf("Camera" to 4), ranked("cmr"))
        assertNull(target("cmr"))
        assertEquals(listOf("Camera" to 5), ranked("cmaera"))
        assertNull(target("cmaera"))
    }
}
