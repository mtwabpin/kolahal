package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptMetaTest {
    @Test fun luaSingleEvent() =
        assertEquals(listOf("unlock"), ScriptMeta.events("-- @on unlock\nlog('hi')"))

    @Test fun jsCommaSeparated() =
        assertEquals(listOf("charger", "unplug"), ScriptMeta.events("// @on charger, unplug\nlog('x');"))

    @Test fun multipleLinesMergeInCanonicalOrder() =
        assertEquals(
            listOf("unlock", "wifi", "notification"),
            ScriptMeta.events("-- @on notification\n  -- @on wifi unlock\n-- @on Unlock"),
        )

    @Test fun unknownEventsIgnoredButReported() {
        val code = "-- @on unlock, sunrise"
        assertEquals(listOf("unlock"), ScriptMeta.events(code))
        assertEquals(listOf("sunrise"), ScriptMeta.unknownEvents(code))
    }

    @Test fun noDirective() {
        assertTrue(ScriptMeta.events("-- @command\n-- @desc d\nlocal on = 1").isEmpty())
        assertTrue(ScriptMeta.events("// @online\n").isEmpty())
        assertTrue(ScriptMeta.events("log('-- @on unlock')").isEmpty())
    }

    @Test fun otherDirectivesStillParse() {
        val code = "-- @command\n-- @desc hello world\n-- @on boot"
        assertTrue(ScriptMeta.isCommand(code))
        assertEquals("hello world", ScriptMeta.description(code))
        assertEquals(listOf("boot"), ScriptMeta.events(code))
        assertFalse(ScriptMeta.isCommand("-- @on boot"))
        assertTrue("below the first line", ScriptMeta.isCommand("-- @desc d\n-- @command"))
    }

    @Test fun anEmptyDescTakesNothingFromTheNextLine() {
        assertNull(ScriptMeta.description("-- @desc\nlocal x = 1"))
        assertNull(ScriptMeta.description("-- @desc   \nlocal x = 1"))
        assertEquals("tabs too", ScriptMeta.description("--\t@desc\ttabs too"))
    }

    @Test fun allEventsAccepted() =
        assertEquals(ScriptMeta.EVENTS, ScriptMeta.events("// @on " + ScriptMeta.EVENTS.reversed().joinToString(",")))

    @Test fun usageFromDirective() {
        assertEquals("xkcd [random|<number>]", ScriptMeta.usage("// @usage [random|<number>]\nlog(argline);", "xkcd"))
        assertEquals("the name in front is optional", "xkcd random", ScriptMeta.usage("-- @usage xkcd random", "xkcd"))
        assertEquals("empty: no arguments", "quote", ScriptMeta.usage("// @usage\nif (args.length === 0) notify('a', 'b');", "quote"))
        assertEquals("doesn't run into the next line", "ip", ScriptMeta.usage("-- @usage\n-- @desc public IP", "ip"))
    }

    @Test fun optionsDirective() {
        val code = """
            -- @option city London The city to show
            -- @option units metric|imperial Units for temperatures
            -- @option greeting "good morning" How it says hello
            -- @option empty "" Left blank
            -- @option bare
            -- @option city Paris A second line of a name is ignored
            -- @option 9lives x Not a name
        """.trimIndent()
        assertEquals(
            listOf(
                ScriptMeta.Option("city", "London", emptyList(), "The city to show"),
                ScriptMeta.Option("units", "metric", listOf("metric", "imperial"), "Units for temperatures"),
                ScriptMeta.Option("greeting", "good morning", emptyList(), "How it says hello"),
                ScriptMeta.Option("empty", "", emptyList(), "Left blank"),
                ScriptMeta.Option("bare", "", emptyList(), ""),
            ),
            ScriptMeta.options(code),
        )
        assertEquals(listOf("x"), ScriptMeta.options("// @option x 1").map { it.name })
    }

    @Test fun usesDirective() {
        assertEquals(setOf("notifications", "clipboard"), ScriptMeta.uses("-- @uses notifications, clipboard\nlog(1)"))
        assertEquals(setOf("dnd"), ScriptMeta.uses("// @uses DND camera\n"))
        assertTrue("gone: an old script's line means nothing", ScriptMeta.uses("-- @uses lan\nlog(1)").isEmpty())
        assertTrue("none declared", ScriptMeta.uses("-- @command\nlog(1)").isEmpty())
    }

    @Test fun usageWithoutDirective() {
        assertEquals("never reads its arguments", "ip", ScriptMeta.usage("-- @command\nlog(http.get('x').body)", "ip"))
        assertNull("reads them: unknown", ScriptMeta.usage("-- @command\nlog(argline)", "w"))
        assertNull(ScriptMeta.usage("// @command\nlog(args[0]);", "w"))
    }

    /** A new or changed `@every` line sets the interval, one taken out takes back what it had set; else it stays yours. */
    @Test fun theIntervalFollowsChangesToItsLine() {
        val hourly = "-- @every 1h\nreturn 1"
        val none = "return 1"
        assertEquals("only the rest changed", 120, ScriptMeta.intervalAfter(hourly, "-- @every 1h\nreturn 2", 120))
        assertEquals("a timer you switched off", 0, ScriptMeta.intervalAfter(hourly, "-- @every 1h\nreturn 2", 0))
        assertEquals("a new line", 60, ScriptMeta.intervalAfter(none, hourly, 120))
        assertEquals("a changed line", 30, ScriptMeta.intervalAfter(hourly, "-- @every 30m\nreturn 1", 120))
        assertEquals("taken out, what it set", 0, ScriptMeta.intervalAfter(hourly, none, 60))
        assertEquals("taken out, a timer of yours", 120, ScriptMeta.intervalAfter(hourly, none, 120))
        assertEquals("@every off is no line", 0, ScriptMeta.intervalAfter(hourly, "-- @every off\nreturn 1", 60))
        assertEquals("no line either way", 120, ScriptMeta.intervalAfter(none, "return 2", 120))
    }

    /** Android's minimum: a line under it says the minimum, wherever that line is. */
    @Test fun anIntervalLineCountsAsWhatItCanSet() {
        assertEquals(15, ScriptMeta.intervalAfter("return 1", "-- @every 5m", 0))
        assertEquals(0, ScriptMeta.intervalAfter("-- @every 5m", "return 1", 15))
        assertEquals("the minimum both times", 120, ScriptMeta.intervalAfter("-- @every 5m", "-- @every 10m", 120))
    }

    /** The same for `@at`, however the times on the line are written. */
    @Test fun theDailyTimesFollowChangesToTheirLine() {
        val daily = "-- @at 07:00, 18:30\nreturn 1"
        val none = "return 1"
        val yours = listOf("09:00")
        assertEquals("only the rest changed", yours, ScriptMeta.timesAfter(daily, "-- @at 07:00, 18:30\nreturn 2", yours))
        assertEquals("times you took off", emptyList<String>(), ScriptMeta.timesAfter(daily, "-- @at 07:00, 18:30\nreturn 2", emptyList()))
        assertEquals("written another way", yours, ScriptMeta.timesAfter(daily, "-- @at 18:30,7:00\nreturn 1", yours))
        assertEquals("a new line", listOf("08:00"), ScriptMeta.timesAfter(none, "-- @at 8:00", yours))
        assertEquals("a changed line", listOf("08:00"), ScriptMeta.timesAfter(daily, "-- @at 08:00", yours))
        assertEquals("taken out, what it set", emptyList<String>(), ScriptMeta.timesAfter(daily, none, listOf("18:30", "07:00")))
        assertEquals("taken out, times of yours", yours, ScriptMeta.timesAfter(daily, none, yours))
        assertEquals("no line either way", yours, ScriptMeta.timesAfter(none, "return 2", yours))
    }

    /** Do Not Disturb and the work profile are only for scripts that ask. */
    @Test fun dndAndWorkAreAskedFor() {
        assertEquals(setOf("dnd", "work"), ScriptMeta.uses("-- @uses dnd, work\n"))
        assertEquals(setOf("dnd"), ScriptMeta.uses("// @uses dnd\n"))
    }

    /** A timed run waits for a network only when the code makes requests or reads feeds. */
    @Test fun goingOnlineIsHttpOrRss() {
        assertTrue(ScriptMeta.goesOnline("local r = http.get(url)"))
        assertTrue(ScriptMeta.goesOnline("const f = await rss.fetch(u);"))
        assertFalse(ScriptMeta.goesOnline("-- night-dnd.lua — No network.\nif device.dnd() == 'off' then device.setDnd('on') end"))
        assertFalse(ScriptMeta.goesOnline("log(\"see https://example.com\")"))
    }

    /** The internet for requests, feeds, links and pictures; pinning for status; notifications for notify. */
    @Test fun whatTheCodeUsesIsReadFromIt() {
        assertTrue(ScriptMeta.usesInternet("local r = http.get(u)"))
        assertTrue(ScriptMeta.usesInternet("local h = http\nh.get(u)"))
        assertTrue(ScriptMeta.usesInternet("const f = await rss.fetch(u);"))
        assertTrue(ScriptMeta.usesInternet("log(t, nil, { url = link })"))
        assertTrue(ScriptMeta.usesInternet("log(t, \"\", { image: pic });"))
        assertFalse(ScriptMeta.usesInternet("-- @uses dnd\ndevice.setDnd('on')\nlog('see https://example.com')"))
        // Commands can hand words to a site (a search).
        assertTrue(ScriptMeta.usesInternet("-- @uses commands\nlauncher.run('yt ' .. args[1])"))

        assertTrue(ScriptMeta.setsStatus("status(\"HN: 3 new\")"))
        assertTrue(ScriptMeta.setsStatus("status(nil)"))
        assertTrue(ScriptMeta.setsStatus("if (line) status(`Sun ${'$'}{rise}`);"))
        assertFalse(ScriptMeta.setsStatus("local r = http.get(u)\nif r.status ~= 200 then return end"))
        assertFalse(ScriptMeta.setsStatus("const ok = res.status === 200;"))
        assertTrue(ScriptMeta.setsStatus("status(\"NZ 136/8\", { progress = 0.66, refresh = 30 })"))

        assertTrue(ScriptMeta.postsNotifications("notify(\"Battery\", \"low\")"))
        assertFalse(ScriptMeta.postsNotifications("-- @notify\nlog(\"no notification\")"))
        assertFalse(ScriptMeta.postsNotifications("if s.notify then log(1) end"))
    }

    /** `script get` names the internet and each `@uses` before a shared script is switched on; events and pinning don't count. */
    @Test fun aSharedScriptNamesWhatItAsksFor() {
        assertEquals(emptyList<String>(), ScriptAccess.asksFor("-- @on unlock\nlog('hi')\nstatus('ok')"))
        assertEquals(listOf("clipboard"), ScriptAccess.asksFor("-- @uses clipboard\nlog(clipboard.get())"))
        assertEquals(listOf(ScriptAccess.INTERNET), ScriptAccess.asksFor("local r = http.get(u)\nlog(r.body)"))
        assertEquals(
            listOf(ScriptAccess.INTERNET, "notifications", "apps"),
            ScriptAccess.asksFor("-- @uses apps, notifications\nlocal r = http.get(u)"),
        )
    }

    /** Only what the code uses has a switch, and only that is allowed: no internet or pinning for one that uses neither. */
    @Test fun aScriptMayOnlyDoWhatItUses() {
        val offline = "-- @uses dnd\n-- @on unlock\ndevice.setDnd('on')"
        assertEquals(listOf("dnd", "unlock"), ScriptAccess.items(offline))
        val quiet = ScriptAccess.grants(offline, mapOf("dnd" to true))
        assertFalse(quiet.allows(ScriptAccess.INTERNET))
        assertFalse(quiet.allows(ScriptAccess.HEADER))
        assertTrue(quiet.allows("dnd"))
        assertTrue(quiet.allows("unlock"))

        val online = "local r = http.get(u)\nstatus(r.body)"
        assertEquals(listOf(ScriptAccess.INTERNET, ScriptAccess.HEADER), ScriptAccess.items(online))
        assertTrue(ScriptAccess.grants(online, emptyMap()).allows(ScriptAccess.INTERNET))
        assertFalse(ScriptAccess.grants(online, mapOf(ScriptAccess.INTERNET to false)).allows(ScriptAccess.INTERNET))
        // Its status line shows at the top only once you pin it.
        assertFalse(ScriptAccess.grants(online, emptyMap()).allows(ScriptAccess.HEADER))
        assertTrue(ScriptAccess.grants(online, mapOf(ScriptAccess.HEADER to true)).allows(ScriptAccess.HEADER))
    }

    /** `@widget <kind> [<size>]`: the script has a widget. Without a size, the kind's own is used. */
    @Test fun theWidgetLineNamesAKindAndASize() {
        assertEquals(ScriptMeta.Widget(WidgetKind.STAT, WidgetSize.S2X1), ScriptMeta.widget("-- @title Weather\n-- @widget stat 2x1\nwidget.show{}"))
        assertEquals(ScriptMeta.Widget(WidgetKind.LIST, WidgetSize.S4X4), ScriptMeta.widget("// @widget list 4x4\nwidget.show({})"))
        // Any case, tabs, and the × people type.
        assertEquals(ScriptMeta.Widget(WidgetKind.CHART, WidgetSize.S4X2), ScriptMeta.widget("--  @widget\tChart   4×2  "))
        // Each kind has a size of its own.
        assertEquals(WidgetSize.S2X1, ScriptMeta.widget("-- @widget stat")!!.size)
        assertEquals(WidgetSize.S2X2, ScriptMeta.widget("-- @widget text")!!.size)
        assertEquals(WidgetSize.S4X2, ScriptMeta.widget("-- @widget list")!!.size)
        assertEquals(WidgetSize.S2X1, ScriptMeta.widget("-- @widget progress")!!.size)
        assertEquals(WidgetSize.S2X2, ScriptMeta.widget("-- @widget chart")!!.size)
        assertEquals(WidgetSize.S2X1, ScriptMeta.widget("-- @widget countdown")!!.size)
        assertEquals(ScriptMeta.Widget(WidgetKind.BUTTONS, WidgetSize.S4X1), ScriptMeta.widget("-- @widget buttons"))
        assertEquals(ScriptMeta.Widget(WidgetKind.TOGGLES, WidgetSize.S4X1), ScriptMeta.widget("// @widget toggles"))
        assertEquals(ScriptMeta.Widget(WidgetKind.CLOCK, WidgetSize.S2X1), ScriptMeta.widget("-- @widget clock"))
        assertEquals(ScriptMeta.Widget(WidgetKind.AGENDA, WidgetSize.S4X2), ScriptMeta.widget("// @widget agenda"))
        assertEquals(ScriptMeta.Widget(WidgetKind.CLOCK, WidgetSize.S4X1), ScriptMeta.widget("-- @widget clock 4x1"))
        // A wrong size does not hide the widget.
        assertEquals(ScriptMeta.Widget(WidgetKind.TEXT, WidgetSize.S2X2), ScriptMeta.widget("-- @widget text 9x9"))
        // No line, no kind, a kind Kolahal doesn't draw, another directive, or a call in the code.
        assertNull(ScriptMeta.widget("log('hi')"))
        assertNull(ScriptMeta.widget("-- @widget"))
        assertNull(ScriptMeta.widget("-- @widget hologram 2x2"))
        assertNull(ScriptMeta.widget("-- @widgets stat"))
        assertNull(ScriptMeta.widget("widget.show{ value = 1 }"))
        // A kind only Kolahal fills: a script that names it has no widget.
        assertNull(ScriptMeta.widget("-- @widget media"))
        assertNull(ScriptMeta.widget("// @widget Media 4x1"))
    }

    @Test fun sizesAndKindsHaveNames() {
        assertEquals(listOf("1x1", "2x1", "2x2", "4x1", "4x2", "4x4"), WidgetSize.entries.map { it.id })
        assertEquals(listOf(3, 6, 6, 12, 12, 12), WidgetSize.entries.map { it.span })
        assertEquals(listOf(1, 1, 2, 1, 2, 4), WidgetSize.entries.map { it.rows })
        assertEquals(listOf("stat", "text", "list", "progress", "chart", "countdown", "buttons", "toggles", "clock", "agenda", "media"), WidgetKind.entries.map { it.id })
        assertEquals(WidgetSize.S4X1, WidgetSize.named(" 4X1 "))
        assertNull(WidgetSize.named("3x1"))
        assertNull(WidgetSize.named(null))
        assertEquals(WidgetKind.COUNTDOWN, WidgetKind.named("Countdown"))
        assertEquals(WidgetKind.BUTTONS, WidgetKind.named("buttons"))
        assertEquals(WidgetKind.AGENDA, WidgetKind.named(" AGENDA "))
        assertNull(WidgetKind.named("image"))
        assertNull(WidgetKind.named(null))
    }

    /** `media` is a kind only Kolahal fills: a script can't name it, and no list for scripts has it. */
    @Test fun aScriptCantNameAKindOnlyKolahalFills() {
        assertEquals(listOf("stat", "text", "list", "progress", "chart", "countdown", "buttons", "toggles", "clock", "agenda"), WidgetKind.forScripts.map { it.id })
        assertEquals(listOf(WidgetKind.MEDIA), WidgetKind.entries.filterNot { it.script })
        assertNull(WidgetKind.named("media"))
        assertNull(WidgetKind.named("MEDIA"))
        // Every kind for scripts is found by its name, and no other is.
        WidgetKind.entries.forEach { assertEquals(it.takeIf { k -> k.script }, WidgetKind.named(it.id)) }
        // The reference lists what a script can write, and no more.
        val line = ApiDocs.directives.single { it.name == "widget" }.doc
        assertTrue(line, line.contains("Kinds: stat text list progress chart countdown buttons toggles clock agenda."))
        assertFalse(line, line.contains("media"))
        val show = ApiDocs.all.first { it.name == "widget.show" }.doc
        WidgetKind.forScripts.forEach { assertTrue("${it.id} in: $show", show.contains("${it.id}: ")) }
        assertFalse(show, show.contains("media"))
    }

    /** The widgets page item is off until the widget is placed, and nothing asks for it: placing is the user's act. */
    @Test fun theWidgetItemIsOffUntilPlaced() {
        val code = "-- @widget stat\nwidget.show{ value = 1 }"
        assertEquals(listOf(ScriptAccess.WIDGET), ScriptAccess.items(code))
        assertEquals("On the widgets page", ScriptAccess.label(ScriptAccess.WIDGET))
        assertEquals(emptyList<String>(), ScriptAccess.asksFor(code))
        // Not placed: undecided or off. An event is on unless switched off; this item is not.
        assertFalse(ScriptAccess.grants(code, emptyMap()).allows(ScriptAccess.WIDGET))
        assertFalse(ScriptAccess.grants(code, mapOf(ScriptAccess.WIDGET to false)).allows(ScriptAccess.WIDGET))
        assertTrue(ScriptAccess.grants(code, mapOf(ScriptAccess.WIDGET to true)).allows(ScriptAccess.WIDGET))
        // Switching the script on allows what it declares with @uses, and not this.
        assertEquals(emptyMap<String, Boolean>(), ScriptAccess.allowUndecided(code, emptyMap()))
        assertTrue(ScriptAccess.grants(code, emptyMap()).undecided.isEmpty())
        // A script without the line never has it, even with a stored "on".
        val plain = "log('hi')"
        assertEquals(emptyList<String>(), ScriptAccess.items(plain))
        assertFalse(ScriptAccess.grants(plain, mapOf(ScriptAccess.WIDGET to true)).allows(ScriptAccess.WIDGET))
        // It comes after the internet and the pin, before @uses and events.
        assertEquals(
            listOf(ScriptAccess.INTERNET, ScriptAccess.HEADER, ScriptAccess.WIDGET, "apps", "unlock"),
            ScriptAccess.items("-- @widget list\n-- @uses apps\n-- @on unlock\nstatus(http.get(u).body)"),
        )
    }
}
