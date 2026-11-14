package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.script.ApiDocs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** What `widget.show` accepts and refuses: the description a script hands over, read by [WidgetParser]. */
class WidgetParserTest {
    private fun parse(raw: Map<String, Any?>, kind: WidgetKind = WidgetKind.STAT) = WidgetParser.parse(raw, kind, { it }, { it })

    private fun refused(raw: Map<String, Any?>, kind: WidgetKind = WidgetKind.STAT): String? =
        assertThrows(IllegalArgumentException::class.java) { parse(raw, kind) }.message

    private fun stat(vararg fields: Pair<String, Any?>) = parse(mapOf("value" to "1") + fields) as WidgetSpec.Stat

    private fun list(vararg items: Any?) = parse(mapOf("items" to items.toList()), WidgetKind.LIST) as WidgetSpec.Rows

    // ---- the kinds ------------------------------------------------------------------------

    @Test fun aStatWithAllItsFields() {
        val spec = parse(
            mapOf(
                "title" to "Pune", "icon" to "☀️", "color" to "good", "staleAfter" to 7200, "refresh" to 30,
                "value" to "24°C", "label" to "Sunny", "unit" to "°C", "delta" to "+2.4%", "trend" to "up",
                "more" to listOf(mapOf("value" to "36%", "label" to "humidity"), mapOf("value" to "9 km/h")),
                "progress" to 0.5, "moving" to true,
            ),
        )
        assertEquals(
            WidgetSpec.Stat(
                WidgetBase(title = "Pune", icon = "☀️", color = StyledText.Colour.GOOD, staleAfter = 7200, refresh = 30),
                value = "24°C", label = "Sunny", unit = "°C", delta = "+2.4%", trend = "up",
                more = listOf(WidgetSpec.Small("36%", "humidity"), WidgetSpec.Small("9 km/h", null)),
                progress = 0.5f, moving = true,
            ),
            spec,
        )
    }

    @Test fun aStatNeedsOnlyItsValue() {
        assertEquals(WidgetSpec.Stat(WidgetBase(), value = "7"), parse(mapOf("value" to "7")))
        assertEquals("widget: value is needed: the big value, as text or a number", refused(mapOf("label" to "unlocks")))
    }

    @Test fun aTextKeepsItsLineBreaks() {
        val spec = parse(mapOf("text" to "  one\r\ntwo  ", "caption" to "a\nb"), WidgetKind.TEXT)
        assertEquals(WidgetSpec.Text(WidgetBase(), text = "one\ntwo", caption = "a b"), spec)
        assertEquals("widget: text is needed: the text to show", refused(mapOf("caption" to "x"), WidgetKind.TEXT))
    }

    @Test fun aListOfRows() {
        val spec = parse(
            mapOf(
                "items" to listOf(
                    mapOf("title" to "Story", "detail" to "by pg", "trailing" to 120, "icon" to "Y", "color" to "warn", "tap" to mapOf("url" to "https://example.com/1")),
                    mapOf("title" to "Another"),
                ),
                "empty" to "Nothing new",
            ),
            WidgetKind.LIST,
        )
        assertEquals(
            WidgetSpec.Rows(
                WidgetBase(),
                items = listOf(
                    WidgetSpec.Row("Story", detail = "by pg", trailing = "120", icon = "Y", color = StyledText.Colour.WARN, tap = WidgetTap(url = "https://example.com/1")),
                    WidgetSpec.Row("Another"),
                ),
                empty = "Nothing new",
            ),
            spec,
        )
    }

    @Test fun aListNeedsItsItemsAndEachItemItsTitle() {
        assertTrue(refused(emptyMap(), WidgetKind.LIST)!!.startsWith("widget: items is needed: a list of rows"))
        assertEquals("widget: item 2 of items: title is needed: the row's text", refused(mapOf("items" to listOf(mapOf("title" to "a"), mapOf("detail" to "b"))), WidgetKind.LIST))
        assertEquals("widget: items must be a list of { title, detail, trailing, icon, color, tap, done }, not 'rows'", refused(mapOf("items" to "rows"), WidgetKind.LIST))
        assertEquals("widget: item 1 of items: must be { title, detail, trailing, icon, color, tap, done }, not 'a row'", refused(mapOf("items" to listOf("a row")), WidgetKind.LIST))
    }

    /** An empty Lua table arrives as an empty map: it has no keys to tell a list from a table. */
    @Test fun anEmptyLuaTableIsAnEmptyList() {
        assertEquals(WidgetSpec.Rows(WidgetBase(), items = emptyList()), parse(mapOf("items" to emptyMap<String, Any?>()), WidgetKind.LIST))
        assertEquals(emptyList<WidgetSpec.Small>(), stat("more" to emptyMap<String, Any?>()).more)
        // A table with keys is not a list.
        assertEquals("widget: more must be a list of { value, label }, not a table", refused(mapOf("value" to 1, "more" to mapOf("value" to 2))))
    }

    /** `done` on a row is true, false, or left out. Left out means "no checkbox", so false must stay false. */
    @Test fun aRowCanHaveACheckbox() {
        val rows = list(mapOf("title" to "Buy milk", "done" to false), mapOf("title" to "Call home", "done" to true), mapOf("title" to "Plain"), mapOf("title" to "Nil", "done" to null))
        assertEquals(listOf(false, true, null, null), rows.items.map { it.done })
        assertEquals("widget: item 1 of items: done must be true or false, not 'yes'", refused(mapOf("items" to listOf(mapOf("title" to "a", "done" to "yes"))), WidgetKind.LIST))
        assertEquals("widget: item 1 of items: done must be true or false, not 1", refused(mapOf("items" to listOf(mapOf("title" to "a", "done" to 1))), WidgetKind.LIST))
    }

    // ---- buttons --------------------------------------------------------------------------

    private fun buttons(vararg items: Any?) = parse(mapOf("items" to items.toList()), WidgetKind.BUTTONS) as WidgetSpec.Buttons

    private fun button(label: String, args: String = label.lowercase()) = mapOf("label" to label, "tap" to mapOf("args" to args))

    @Test fun buttonsWithAllTheirFields() {
        val spec = parse(
            mapOf(
                "title" to "Lights", "icon" to "💡", "color" to "warn",
                "items" to listOf(
                    mapOf("label" to "On", "icon" to "☀", "color" to "good", "tap" to mapOf("args" to "on", "label" to "Switch on")),
                    mapOf("label" to "Docs", "tap" to mapOf("url" to "https://example.com")),
                    mapOf("label" to 7, "tap" to mapOf("args" to "")),
                ),
            ),
            WidgetKind.BUTTONS,
        )
        assertEquals(
            WidgetSpec.Buttons(
                WidgetBase(title = "Lights", icon = "💡", color = StyledText.Colour.WARN),
                items = listOf(
                    WidgetSpec.Button("On", WidgetTap(args = "on", label = "Switch on"), icon = "☀", color = StyledText.Colour.GOOD),
                    WidgetSpec.Button("Docs", WidgetTap(url = "https://example.com")),
                    WidgetSpec.Button("7", WidgetTap(args = "")),
                ),
            ),
            spec,
        )
    }

    /** 1 to 8 buttons. More is an error and nothing is cut: a button that went missing is a tap you can't make. */
    @Test fun buttonsAreOneToEight() {
        assertEquals(8, buttons(*(1..8).map { button("B$it") }.toTypedArray()).items.size)
        assertEquals(1, buttons(button("Only")).items.size)
        assertEquals("widget: items has 9 buttons: give 8 at most", refused(mapOf("items" to (1..9).map { button("B$it") }), WidgetKind.BUTTONS))
        assertEquals("widget: items is empty: give 1 to 8 buttons", refused(mapOf("items" to emptyList<Any>()), WidgetKind.BUTTONS))
        assertEquals("widget: items is empty: give 1 to 8 buttons", refused(mapOf("items" to emptyMap<String, Any?>()), WidgetKind.BUTTONS))
        assertEquals("widget: items is needed: a list of buttons, each { label, icon, color, tap }", refused(emptyMap(), WidgetKind.BUTTONS))
        assertEquals("widget: items must be a list of { label, icon, color, tap }, not 'x'", refused(mapOf("items" to "x"), WidgetKind.BUTTONS))
        assertEquals("widget: item 1 of items: must be { label, icon, color, tap }, not 'x'", refused(mapOf("items" to listOf("x")), WidgetKind.BUTTONS))
    }

    @Test fun aButtonNeedsItsLabelAndItsTap() {
        assertEquals("widget: item 1 of items: label is needed: the button's text", refused(mapOf("items" to listOf(mapOf("tap" to mapOf("args" to "x")))), WidgetKind.BUTTONS))
        assertEquals("widget: item 2 of items: tap is needed: what the button does, { args } or { url }", refused(mapOf("items" to listOf(button("A"), mapOf("label" to "B"))), WidgetKind.BUTTONS))
        assertEquals("widget: item 1 of items: tap: give args or url", refused(mapOf("items" to listOf(mapOf("label" to "A", "tap" to mapOf("label" to "x")))), WidgetKind.BUTTONS))
        assertEquals("widget: item 1 of items: no field 'command'; use label, icon, color, tap", refused(mapOf("items" to listOf(button("A") + ("command" to "torch"))), WidgetKind.BUTTONS))
        // A command is no action: a description can't plant one.
        assertEquals("widget: item 1 of items: tap: no field 'command'; use args, url, label, page", refused(mapOf("items" to listOf(mapOf("label" to "A", "tap" to mapOf("command" to "torch")))), WidgetKind.BUTTONS))
        // The label is cut at 24.
        assertEquals("x".repeat(23) + "…", buttons(button("x".repeat(40), "a")).items.single().label)
    }

    // ---- toggles --------------------------------------------------------------------------

    private fun toggle(label: String, on: Boolean) = mapOf("label" to label, "on" to on, "tap" to mapOf("args" to "toggle ${label.lowercase()}"))

    @Test fun togglesWithAllTheirFields() {
        val spec = parse(
            mapOf(
                "title" to "Quick toggles",
                "items" to listOf(mapOf("label" to "Do Not Disturb", "icon" to "🌙", "on" to true, "tap" to mapOf("args" to "dnd off", "label" to "Turn off")), toggle("Work", false)),
            ),
            WidgetKind.TOGGLES,
        )
        assertEquals(
            WidgetSpec.Toggles(
                // The thing can change elsewhere, so the content is old after a minute unless the script says otherwise.
                WidgetBase(title = "Quick toggles", staleAfter = 60),
                items = listOf(
                    WidgetSpec.Toggle("Do Not Disturb", on = true, tap = WidgetTap(args = "dnd off", label = "Turn off"), icon = "🌙"),
                    WidgetSpec.Toggle("Work", on = false, tap = WidgetTap(args = "toggle work")),
                ),
            ),
            spec,
        )
        assertEquals(600, parse(mapOf("staleAfter" to 600, "items" to listOf(toggle("A", true))), WidgetKind.TOGGLES).base.staleAfter)
    }

    @Test fun togglesAreOneToSix() {
        assertEquals(6, (parse(mapOf("items" to (1..6).map { toggle("T$it", it % 2 == 0) }), WidgetKind.TOGGLES) as WidgetSpec.Toggles).items.size)
        assertEquals("widget: items has 7 switches: give 6 at most", refused(mapOf("items" to (1..7).map { toggle("T$it", true) }), WidgetKind.TOGGLES))
        assertEquals("widget: items is empty: give 1 to 6 switches", refused(mapOf("items" to emptyList<Any>()), WidgetKind.TOGGLES))
        assertEquals("widget: items is needed: a list of switches, each { label, icon, on, tap }", refused(emptyMap(), WidgetKind.TOGGLES))
    }

    /** A switch needs its label, its state and a tap that runs the script. Kolahal never guesses the state. */
    @Test fun aToggleNeedsItsLabelItsStateAndATapWithArgs() {
        fun no(vararg fields: Pair<String, Any?>) = refused(mapOf("items" to listOf(mapOf(*fields))), WidgetKind.TOGGLES)
        val tap = "tap" to mapOf("args" to "x")
        assertEquals("widget: item 1 of items: label is needed: the switch's text", no("on" to true, tap))
        assertEquals("widget: item 1 of items: on is needed: true or false, the state now", no("label" to "A", tap))
        assertEquals("widget: item 1 of items: on is needed: true or false, the state now", no("label" to "A", "on" to null, tap))
        assertEquals("widget: item 1 of items: on must be true or false, not 1", no("label" to "A", "on" to 1, tap))
        assertEquals("widget: item 1 of items: on must be true or false, not 'on'", no("label" to "A", "on" to "on", tap))
        assertEquals("widget: item 1 of items: tap is needed: { args }, the arguments that switch it", no("label" to "A", "on" to true))
        assertEquals(
            "widget: item 1 of items: tap must be { args }, not { url }: a switch runs this script again",
            no("label" to "A", "on" to false, "tap" to mapOf("url" to "https://example.com")),
        )
        assertEquals("widget: item 1 of items: no field 'color'; use label, icon, on, tap", no("label" to "A", "on" to true, tap, "color" to "good"))
    }

    // ---- clock ----------------------------------------------------------------------------

    private fun clock(vararg fields: Pair<String, Any?>) = parse(mapOf(*fields), WidgetKind.CLOCK) as WidgetSpec.Clock

    @Test fun aClockWithAllItsFields() {
        assertEquals(
            WidgetSpec.Clock(WidgetBase(title = "World"), zones = listOf(WidgetSpec.Zone("Asia/Kolkata", "Pune"), WidgetSpec.Zone("UTC"), WidgetSpec.Zone("+05:30")), seconds = true),
            clock("title" to "World", "zones" to listOf(mapOf("zone" to " Asia/Kolkata ", "label" to "Pune"), mapOf("zone" to "UTC"), mapOf("zone" to "+05:30")), "seconds" to true),
        )
        assertEquals(false, clock("zones" to listOf(mapOf("zone" to "Europe/Paris"))).seconds)
    }

    /** The first 4 places are kept. A place past the fourth is not looked at. */
    @Test fun aClockKeepsItsFirstFourZones() {
        val six = listOf("Asia/Kolkata", "Europe/Paris", "America/New_York", "Asia/Tokyo", "Australia/Sydney", "Not/AZone").map { mapOf("zone" to it) }
        assertEquals(listOf("Asia/Kolkata", "Europe/Paris", "America/New_York", "Asia/Tokyo"), clock("zones" to six).zones.map { it.zone })
        // A label is cut at 24.
        assertEquals("x".repeat(23) + "…", clock("zones" to listOf(mapOf("zone" to "UTC", "label" to "x".repeat(30)))).zones.single().label)
    }

    /** An unknown time zone is an error. It is never cut and never guessed. */
    @Test fun aClockRefusesAZoneItDoesNotKnow() {
        fun no(vararg fields: Pair<String, Any?>) = refused(mapOf(*fields), WidgetKind.CLOCK)
        assertEquals("widget: item 1 of zones: no time zone 'Asia/Poona'; use a name such as Asia/Kolkata or UTC", no("zones" to listOf(mapOf("zone" to "Asia/Poona"))))
        assertEquals("widget: item 2 of zones: no time zone 'IST'; use a name such as Asia/Kolkata or UTC", no("zones" to listOf(mapOf("zone" to "UTC"), mapOf("zone" to "IST"))))
        assertEquals("widget: item 1 of zones: no time zone ''; use a name such as Asia/Kolkata or UTC", no("zones" to listOf(mapOf("zone" to ""))))
        assertEquals("widget: item 1 of zones: zone must be a time zone name, such as Asia/Kolkata, not 5", no("zones" to listOf(mapOf("zone" to 5))))
        assertEquals("widget: item 1 of zones: zone is needed: a time zone name, such as Asia/Kolkata", no("zones" to listOf(mapOf("label" to "Pune"))))
        assertEquals("widget: zones is needed: a list of places, each { zone, label }", no())
        assertEquals("widget: zones is needed: a list of places, each { zone, label }", no("zones" to emptyList<Any>()))
        assertEquals("widget: zones must be a list of { zone, label }, not 'UTC'", no("zones" to "UTC"))
        assertEquals("widget: seconds must be true or false, not 1", no("zones" to listOf(mapOf("zone" to "UTC")), "seconds" to 1))
    }

    // ---- agenda ---------------------------------------------------------------------------

    private fun agenda(vararg fields: Pair<String, Any?>) = parse(mapOf(*fields), WidgetKind.AGENDA) as WidgetSpec.Agenda

    @Test fun anAgendaWithAllItsFields() {
        assertEquals(
            WidgetSpec.Agenda(
                WidgetBase(title = "Matches"),
                events = listOf(
                    WidgetSpec.Event("Final", starts = 1_800_000_000, ends = 1_800_007_200, where = "Pune", color = StyledText.Colour.GOOD, tap = WidgetTap(url = "https://example.com/final")),
                    WidgetSpec.Event("Holiday", starts = 1_800_050_000, allDay = true),
                    // A fraction is dropped, as for a countdown.
                    WidgetSpec.Event("Launch", starts = 1_800_100_000),
                ),
                days = 5,
                empty = "Nothing planned",
            ),
            agenda(
                "title" to "Matches", "days" to 5, "empty" to "Nothing planned",
                "events" to listOf(
                    mapOf("title" to "Final", "starts" to 1_800_000_000, "ends" to 1_800_007_200, "where" to "Pune", "color" to "good", "tap" to mapOf("url" to "https://example.com/final")),
                    mapOf("title" to "Holiday", "starts" to 1_800_050_000, "allDay" to true),
                    mapOf("title" to "Launch", "starts" to 1_800_100_000.9),
                ),
            ),
        )
        // No events is fine: the card says `empty`. Three days is the default.
        assertEquals(WidgetSpec.Agenda(WidgetBase(), events = emptyList(), days = 3), agenda("events" to emptyList<Any>()))
    }

    /** The first 20 events are kept, and the texts are cut: a title at 80, a place at 60. */
    @Test fun anAgendaKeepsItsFirstTwentyEvents() {
        val many = (1..25).map { mapOf("title" to "E$it", "starts" to 1_800_000_000 + it) }
        assertEquals((1..20).map { "E$it" }, agenda("events" to many).events.map { it.title })
        val long = agenda("events" to listOf(mapOf("title" to "t".repeat(100), "starts" to 1, "where" to "w".repeat(100)))).events.single()
        assertEquals("t".repeat(79) + "…", long.title)
        assertEquals("w".repeat(59) + "…", long.where)
    }

    /** `days` is held between 1 and 7. */
    @Test fun anAgendasDaysAreOneToSeven() {
        fun days(v: Any?) = agenda("events" to emptyList<Any>(), "days" to v).days
        assertEquals(1, days(1))
        assertEquals(7, days(7))
        assertEquals(1, days(0))
        assertEquals(1, days(-3))
        assertEquals(7, days(30))
        assertEquals(2, days(2.9))
        assertEquals(3, days(null))
        assertEquals("widget: days must be a number of days, from 1 to 7, not 'week'", refused(mapOf("events" to emptyList<Any>(), "days" to "week"), WidgetKind.AGENDA))
    }

    @Test fun anAgendaRefusesWhatItCantRead() {
        fun no(vararg fields: Pair<String, Any?>) = refused(mapOf(*fields), WidgetKind.AGENDA)
        fun event(vararg fields: Pair<String, Any?>) = no("events" to listOf(mapOf(*fields)))
        assertEquals("widget: events is needed: a list of events, each { title, starts, ends, allDay, where, color, tap }", no())
        assertEquals("widget: events must be a list of { title, starts, ends, allDay, where, color, tap }, not 'x'", no("events" to "x"))
        assertEquals("widget: item 1 of events: title is needed: the event's name", event("starts" to 1))
        assertTrue(event("title" to "a")!!.startsWith("widget: item 1 of events: starts is needed: when it starts, as a time in seconds"))
        assertEquals("widget: item 1 of events: starts must be a time in seconds, not 'noon'", event("title" to "a", "starts" to "noon"))
        // The time is in seconds. A time in milliseconds is an error that says so, as for a countdown.
        assertEquals("widget: item 1 of events: starts must be a time in seconds, not in milliseconds: divide it by 1000", event("title" to "a", "starts" to 1_800_000_000_000_000))
        assertEquals("widget: item 1 of events: ends must be a time in seconds, not in milliseconds: divide it by 1000", event("title" to "a", "starts" to 1, "ends" to 1_800_000_000_000_000))
        assertEquals("widget: item 1 of events: ends must not be before starts", event("title" to "a", "starts" to 100, "ends" to 99))
        assertEquals("widget: item 1 of events: allDay must be true or false, not 'yes'", event("title" to "a", "starts" to 1, "allDay" to "yes"))
        assertEquals("widget: item 1 of events: no field 'location'; use title, starts, ends, allDay, where, color, tap", event("title" to "a", "starts" to 1, "location" to "x"))
    }

    // ---- image ----------------------------------------------------------------------------

    private fun image(vararg fields: Pair<String, Any?>) = parse(mapOf(*fields), WidgetKind.IMAGE) as WidgetSpec.Image

    private fun noImage(vararg fields: Pair<String, Any?>) = refused(mapOf(*fields), WidgetKind.IMAGE)

    @Test fun anImageWithAllItsFields() {
        assertEquals(
            WidgetSpec.Image(WidgetBase(title = "xkcd"), image = "https://example.com/a.png", alt = "A graph", caption = "Monday", fit = "fit"),
            image("title" to "xkcd", "image" to " https://example.com/a.png ", "alt" to " A graph ", "caption" to "Monday", "fit" to " FIT "),
        )
        // Only the link and the words for it are needed. Without `fit` the card crops.
        assertEquals(WidgetSpec.Image(WidgetBase(), image = "https://example.com/a.png", alt = "A graph"), image("image" to "https://example.com/a.png", "alt" to "A graph"))
        assertEquals("crop", image("image" to "https://example.com/a.png", "alt" to "a", "fit" to "crop").fit)
        // The texts are cut as any text is. A line break in them becomes a space.
        val long = image("image" to "https://example.com/a.png", "alt" to "a".repeat(300), "caption" to "c\n" + "c".repeat(100))
        assertEquals("a".repeat(199) + "…", long.alt)
        assertEquals("c " + "c".repeat(57) + "…", long.caption)
    }

    @Test fun anImageNeedsAnHttpsLinkAndItsWords() {
        val link = "https://example.com/a.png"
        assertEquals("widget: image is needed: a link to a picture that starts with https://", noImage("alt" to "a"))
        assertEquals("widget: image must be a link to a picture that starts with https://, not 3", noImage("image" to 3, "alt" to "a"))
        assertEquals("widget: image must be a link to a picture that starts with https://, not an empty text", noImage("image" to "  ", "alt" to "a"))
        // Plain http is refused, and the error does not repeat the link: it could hold a secret.
        assertEquals("widget: image must start with https://: a widget's picture is not fetched over plain http", noImage("image" to "http://example.com/a.png?key=K", "alt" to "a"))
        assertEquals("widget: image must start with https://: a widget's picture is not fetched over plain http", noImage("image" to "data:image/png;base64,AAAA", "alt" to "a"))
        // A link is never cut: 2000 characters pass, one more is an error.
        val longest = link + "?" + "x".repeat(2000 - link.length - 1)
        assertEquals(longest, image("image" to longest, "alt" to "a").image)
        assertEquals("widget: image is over 2000 characters", noImage("image" to longest + "x", "alt" to "a"))
        // The words are needed, and they can't be empty: the card shows them when it has no picture.
        assertEquals("widget: alt is needed: what the picture shows, in words, for a screen reader", noImage("image" to link))
        assertEquals("widget: alt is needed: what the picture shows, in words, for a screen reader", noImage("image" to link, "alt" to "  "))
        assertEquals("widget: alt must be text, not a table", noImage("image" to link, "alt" to mapOf("en" to "a")))
        assertEquals("widget: fit must be crop or fit, not 'stretch'", noImage("image" to link, "alt" to "a", "fit" to "stretch"))
        assertEquals("widget: an image widget has no field 'src'; use kind, title, icon, color, tap, staleAfter, refresh, image, alt, caption, fit", noImage("src" to link, "alt" to "a"))
    }

    /** The picture's link goes through the host, as a tap's link does: what the host returns is checked and stored. */
    @Test fun anImagesLinkGoesThroughTheHost() {
        val links = ArrayList<String>()
        fun up(link: (String) -> String, vararg fields: Pair<String, Any?>) = WidgetParser.parse(mapOf(*fields), WidgetKind.IMAGE, text = { it.uppercase() }, link = link)
        assertEquals(
            WidgetSpec.Image(WidgetBase(), image = "https://example.com/a.png?checked", alt = "A", caption = "C"),
            up({ links += it; "$it?checked" }, "image" to "https://example.com/a.png", "alt" to "a", "caption" to "c"),
        )
        assertEquals(listOf("https://example.com/a.png"), links)
        // The host's own error stops the run.
        val refusedByHost = assertThrows(IllegalArgumentException::class.java) { up({ throw IllegalArgumentException("widget: not a web link") }, "image" to "tel:112", "alt" to "a") }
        assertEquals("widget: not a web link", refusedByHost.message)
        // What the host returns is what counts: a link it turned into plain http is refused, and a long one too.
        val turned = assertThrows(IllegalArgumentException::class.java) { up({ it.replace("https://", "http://") }, "image" to "https://example.com/a.png", "alt" to "a") }
        assertEquals("widget: image must start with https://: a widget's picture is not fetched over plain http", turned.message)
        val grown = assertThrows(IllegalArgumentException::class.java) { up({ it + "x".repeat(2000) }, "image" to "https://example.com/a.png", "alt" to "a") }
        assertEquals("widget: image is over 2000 characters", grown.message)
    }

    // ---- a kind only Kolahal fills ----------------------------------------------------------

    /** `media` is not a kind for scripts: not as `kind`, and not as the kind of the `@widget` line. */
    @Test fun aScriptCantFillAMediaWidget() {
        assertEquals("widget: no kind 'media'; use stat, text, list, progress, chart, countdown, buttons, toggles, clock, agenda, image", refused(mapOf("kind" to "media", "title" to "Song")))
        assertEquals("widget: no kind 'Media'; use stat, text, list, progress, chart, countdown, buttons, toggles, clock, agenda, image", refused(mapOf("kind" to "Media")))
        assertEquals("widget: a script can't fill a media widget", refused(mapOf("title" to "Song"), WidgetKind.MEDIA))
        assertEquals("widget: a script can't fill a media widget", refused(emptyMap(), WidgetKind.MEDIA))
    }

    @Test fun theKindFieldWinsOverTheWidgetLine() {
        assertEquals(WidgetSpec.Text(WidgetBase(), text = "hello"), parse(mapOf("kind" to "text", "text" to "hello"), WidgetKind.STAT))
        assertEquals(WidgetSpec.Stat(WidgetBase(), value = "1"), parse(mapOf("kind" to null, "value" to 1), WidgetKind.STAT))
        assertEquals("widget: no kind 'graph'; use stat, text, list, progress, chart, countdown, buttons, toggles, clock, agenda, image", refused(mapOf("kind" to "graph")))
        assertEquals("widget: no kind 3; use stat, text, list, progress, chart, countdown, buttons, toggles, clock, agenda, image", refused(mapOf("kind" to 3)))
    }

    // ---- progress --------------------------------------------------------------------------

    private fun progress(vararg fields: Pair<String, Any?>) = parse(mapOf(*fields), WidgetKind.PROGRESS) as WidgetSpec.Progress

    @Test fun aProgressRingOrBar() {
        assertEquals(
            WidgetSpec.Progress(WidgetBase(color = StyledText.Colour.WARN), value = 68f, max = 100f, text = "68%", label = "Moderate", style = "ring", moving = true),
            progress("value" to 68, "max" to 100, "text" to "68%", "label" to "Moderate", "style" to "ring", "moving" to true, "color" to "warn"),
        )
        // Without a max, the value is out of 1.
        assertEquals(WidgetSpec.Progress(WidgetBase(), value = 0.4f), progress("value" to 0.4))
        // The text in the middle can be a number.
        assertEquals("68", progress("value" to 68, "max" to 100, "text" to 68).text)
    }

    @Test fun aProgressValueStaysFromZeroToItsMax() {
        assertEquals(100f, progress("value" to 250, "max" to 100).value)
        assertEquals(0f, progress("value" to -3, "max" to 100).value)
        assertEquals(1f, progress("value" to 8).value)
    }

    @Test fun whatAProgressRefuses() {
        fun no(vararg fields: Pair<String, Any?>) = refused(mapOf(*fields), WidgetKind.PROGRESS)
        assertEquals("widget: value is needed: how far along it is, from 0 to max. Or give items, for bars", no("label" to "x"))
        assertEquals("widget: value is needed: how far along it is, from 0 to max. Or give items, for bars", no("items" to emptyMap<String, Any?>()))
        assertEquals("widget: value must be a number, not '68%'", no("value" to "68%"))
        assertEquals("widget: max must be a number above 0, not 0", no("value" to 1, "max" to 0))
        assertEquals("widget: max must be a number above 0, not 'ten'", no("value" to 1, "max" to "ten"))
        assertEquals("widget: style must be ring or bar, not 'wave'", no("value" to 1, "style" to "wave"))
        assertEquals("widget: item 2 of items: value is needed: how far along this bar is", no("items" to listOf(mapOf("value" to 1), mapOf("label" to "b"))))
        assertEquals("widget: item 1 of items: max must be a number above 0, not -1", no("items" to listOf(mapOf("value" to 1, "max" to -1))))
        assertEquals("widget: item 1 of items: no field 'title'; use label, value, max, text, color", no("items" to listOf(mapOf("title" to "a", "value" to 1))))
    }

    /** Bars are on one scale: the widget's max, or else the largest value among them. A bar's own max wins for that bar. */
    @Test fun progressBarsShareOneScale() {
        val spec = progress(
            "items" to listOf(
                mapOf("label" to "Steps", "value" to 6000, "text" to "6k", "color" to "good"),
                mapOf("label" to "Water", "value" to 3),
                mapOf("value" to 2, "max" to 4),
            ),
        )
        assertEquals(
            listOf(
                WidgetSpec.Bar("Steps", 6000f, 6000f, "6k", StyledText.Colour.GOOD),
                WidgetSpec.Bar("Water", 3f, 6000f, null, null),
                WidgetSpec.Bar(null, 2f, 4f, null, null),
            ),
            spec.items,
        )
        // With bars, the widget needs no value of its own.
        assertEquals(0f, spec.value)
        val scaled = progress("max" to 10, "items" to listOf(mapOf("value" to 3), mapOf("value" to 30)))
        assertEquals(listOf(10f, 10f), scaled.items.map { it.max })
        assertEquals(listOf(3f, 10f), scaled.items.map { it.value })
        // Bars that are all at zero still have a scale.
        assertEquals(listOf(1f), progress("items" to listOf(mapOf("value" to 0))).items.map { it.max })
    }

    @Test fun aProgressKeepsItsFirstFourBars() {
        assertEquals(listOf(1f, 2f, 3f, 4f), progress("items" to (1..6).map { mapOf("value" to it) }).items.map { it.value })
    }

    // ---- chart -----------------------------------------------------------------------------

    private fun chart(vararg fields: Pair<String, Any?>) = parse(mapOf(*fields), WidgetKind.CHART) as WidgetSpec.Chart

    @Test fun aChartWithAllItsFields() {
        assertEquals(
            WidgetSpec.Chart(
                WidgetBase(), points = listOf(3f, 12f, 9.5f), style = "bars", min = 0f, max = 20f,
                value = "9", label = "unlocks today", labels = listOf("6 days ago", "today"), summary = "Fewer than yesterday",
            ),
            chart(
                "points" to listOf(3, 12L, 9.5), "style" to "bars", "min" to 0, "max" to 20,
                "value" to 9, "label" to "unlocks today", "labels" to listOf("6 days ago", "today"), "summary" to "Fewer than yesterday",
            ),
        )
        // A line, unless it says bars.
        assertEquals(WidgetSpec.Chart(WidgetBase(), points = listOf(1f)), chart("points" to listOf(1)))
        // No points yet: an empty Lua table is an empty list.
        assertEquals(emptyList<Float>(), chart("points" to emptyMap<String, Any?>()).points)
    }

    /** A script keeps its history in its store, and that grows. The newest points are the ones to show. */
    @Test fun aChartKeepsItsLastPoints() {
        val points = chart("points" to (1..100).toList()).points
        assertEquals(60, points.size)
        assertEquals(41f, points.first())
        assertEquals(100f, points.last())
        // What isn't kept isn't checked.
        assertEquals(60, chart("points" to (listOf<Any?>("old") + (1..60).toList())).points.size)
    }

    /** Two labels: under the first point and under the last. An empty first one keeps the second in its place. */
    @Test fun aChartHasTwoLabels() {
        assertEquals(listOf("Mon", "Tue"), chart("points" to listOf(1), "labels" to listOf("Mon", "Tue", "Wed")).labels)
        assertEquals(listOf("", "today"), chart("points" to listOf(1), "labels" to listOf("", "today")).labels)
        assertEquals(listOf("1", "7"), chart("points" to listOf(1), "labels" to listOf(1, 7.0)).labels)
        assertEquals(listOf("x".repeat(15) + "…"), chart("points" to listOf(1), "labels" to listOf("x".repeat(40))).labels)
    }

    @Test fun whatAChartRefuses() {
        fun no(vararg fields: Pair<String, Any?>) = refused(mapOf(*fields), WidgetKind.CHART)
        assertEquals("widget: points is needed: a list of numbers, the oldest first", no("value" to 1))
        assertEquals("widget: points must be a list of numbers, not 7", no("points" to 7))
        assertEquals("widget: points must be a list of numbers; one of them is 'x'", no("points" to listOf(1, "x")))
        assertEquals("widget: points must be a list of numbers; one of them is nothing", no("points" to listOf(1, null)))
        assertEquals("widget: min must be below max", no("points" to listOf(1), "min" to 5, "max" to 5))
        assertEquals("widget: min must be a number, not 'low'", no("points" to listOf(1), "min" to "low"))
        assertEquals("widget: style must be line or bars, not 'pie'", no("points" to listOf(1), "style" to "pie"))
        assertEquals("widget: labels must be a list of texts, not 'Mon'", no("points" to listOf(1), "labels" to "Mon"))
        assertEquals("widget: labels must be text, not true", no("points" to listOf(1), "labels" to listOf(true)))
    }

    // ---- countdown -------------------------------------------------------------------------

    private fun countdown(vararg fields: Pair<String, Any?>) = parse(mapOf(*fields), WidgetKind.COUNTDOWN) as WidgetSpec.Countdown

    @Test fun aCountdownWithAllItsFields() {
        assertEquals(
            WidgetSpec.Countdown(WidgetBase(title = "Goa trip"), to = 1_797_705_000, from = 1_790_000_000, label = "to go", style = "days", reached = "Today"),
            countdown("title" to "Goa trip", "to" to 1_797_705_000, "from" to 1_790_000_000, "label" to "to go", "style" to "days", "reached" to "Today"),
        )
        assertEquals(WidgetSpec.Countdown(WidgetBase(), to = 0), countdown("to" to 0))
        assertEquals("clock", countdown("to" to 60, "style" to "clock").style)
    }

    /** `os.time()` is whole seconds. `Date.now() / 1000` has a fraction, which is dropped. */
    @Test fun aCountdownTakesSecondsWithAFraction() {
        assertEquals(1_797_705_000L, countdown("to" to 1_797_705_000.75).to)
        assertEquals(1_797_705_000L, countdown("to" to 1_797_705_000L).to)
        assertEquals(-2L, countdown("to" to -1.5).to)
    }

    /** Any date with a four-digit year is a time in seconds: the last second of 9999 passes. */
    @Test fun aCountdownTakesAnyDateToTheYear9999() {
        assertEquals(253_402_300_799L, countdown("to" to 253_402_300_799).to)
        assertEquals("widget: to must be a time in seconds, not in milliseconds: divide it by 1000", refused(mapOf("to" to 253_402_300_800), WidgetKind.COUNTDOWN))
    }

    @Test fun whatACountdownRefuses() {
        fun no(vararg fields: Pair<String, Any?>) = refused(mapOf(*fields), WidgetKind.COUNTDOWN)
        assertEquals("widget: to is needed: the moment, as a time in seconds (os.time() in Lua, Date.now() / 1000 in JavaScript)", no("label" to "x"))
        // Date.now() without the division.
        assertEquals("widget: to must be a time in seconds, not in milliseconds: divide it by 1000", no("to" to 1_797_705_000_000))
        assertEquals("widget: from must be a time in seconds, not in milliseconds: divide it by 1000", no("to" to 100, "from" to 1_797_705_000_000))
        assertEquals("widget: to must be a time in seconds, not '2026-12-20'", no("to" to "2026-12-20"))
        assertEquals("widget: from must be before to", no("to" to 100, "from" to 100))
        assertEquals("widget: style must be days or clock, not 'hours'", no("to" to 100, "style" to "hours"))
    }

    // ---- typed ----------------------------------------------------------------------------

    /** Lua's numbers arrive as whole numbers or doubles; a whole double shows without its ".0". */
    @Test fun aNumberWhereTextIsExpectedBecomesText() {
        assertEquals("24", stat("value" to 24).value)
        assertEquals("24", stat("value" to 24L).value)
        assertEquals("24", stat("value" to 24.0).value)
        assertEquals("24.5", stat("value" to 24.5).value)
        assertEquals("7", stat("title" to 7).base.title)
        assertEquals("3", stat("more" to listOf(mapOf("value" to 3.0, "label" to 2))).more.single().value)
    }

    @Test fun anythingElseIsAnErrorThatNamesTheField() {
        assertEquals("widget: value must be text, not true", refused(mapOf("value" to true)))
        assertEquals("widget: title must be text, not a table", refused(mapOf("value" to 1, "title" to mapOf("a" to 1))))
        assertEquals("widget: label must be text, not a list", refused(mapOf("value" to 1, "label" to listOf("a"))))
        assertEquals("widget: progress must be a number from 0 to 1, not 'half'", refused(mapOf("value" to 1, "progress" to "half")))
        assertEquals("widget: progress must be a number from 0 to 1, not NaN", refused(mapOf("value" to 1, "progress" to Double.NaN)))
        assertEquals("widget: moving must be true or false, not 1", refused(mapOf("value" to 1, "moving" to 1)))
        assertEquals("widget: trend must be up, down or flat, not 'rising'", refused(mapOf("value" to 1, "trend" to "rising")))
        assertEquals("widget: staleAfter must be a number of seconds, not '1h'", refused(mapOf("value" to 1, "staleAfter" to "1h")))
        assertEquals("widget: item 1 of more: value must be text, not false", refused(mapOf("value" to 1, "more" to listOf(mapOf("value" to false)))))
    }

    /** A field with no value is a field left out: JavaScript's `label: undefined` arrives as null. */
    @Test fun aFieldWithNoValueIsLeftOut() {
        val spec = stat("label" to null, "color" to null, "tap" to null, "more" to null, "progress" to null, "moving" to null, "refresh" to null)
        assertEquals(WidgetSpec.Stat(WidgetBase(), value = "1"), spec)
        assertEquals("widget: value is needed: the big value, as text or a number", refused(mapOf("value" to null)))
    }

    /** Text that is left out and text that is empty read the same, except where the text is needed. */
    @Test fun anEmptyTextIsNoText() {
        val spec = stat("title" to "  ", "label" to "", "icon" to "")
        assertNull(spec.base.title)
        assertNull(spec.label)
        assertNull(spec.base.icon)
        assertEquals("", parse(mapOf("value" to "")).let { it as WidgetSpec.Stat }.value)
    }

    @Test fun aColourIsARole() {
        assertEquals(StyledText.Colour.BAD, stat("color" to "bad").base.color)
        assertEquals(StyledText.Colour.GOOD, list(mapOf("title" to "a", "color" to "good")).items.single().color)
        val roles = "accent, link, info, muted, text, good, warn, bad"
        assertEquals("widget: no colour '#ff0000'; use $roles", refused(mapOf("value" to 1, "color" to "#ff0000")))
        assertEquals("widget: no colour 7; use $roles", refused(mapOf("value" to 1, "color" to 7)))
        assertEquals("widget: item 1 of items: no colour 'pink'; use $roles", refused(mapOf("items" to listOf(mapOf("title" to "a", "color" to "pink"))), WidgetKind.LIST))
    }

    // ---- closed ---------------------------------------------------------------------------

    @Test fun anUnknownFieldListsTheKnownOnes() {
        assertEquals(
            "widget: a stat widget has no field 'colour'; use kind, title, icon, color, tap, staleAfter, refresh, value, label, unit, delta, trend, more, progress, moving",
            refused(mapOf("value" to 1, "colour" to "good")),
        )
        // Another kind's field is unknown too.
        assertEquals(
            "widget: a text widget has no field 'value'; use kind, title, icon, color, tap, staleAfter, refresh, text, caption",
            refused(mapOf("text" to "a", "value" to 1), WidgetKind.TEXT),
        )
        assertEquals(
            "widget: item 2 of items: no field 'subtitle'; use title, detail, trailing, icon, color, tap, done",
            refused(mapOf("items" to listOf(mapOf("title" to "a"), mapOf("title" to "b", "subtitle" to "c"))), WidgetKind.LIST),
        )
        assertEquals("widget: item 1 of more: no field 'unit'; use value, label", refused(mapOf("value" to 1, "more" to listOf(mapOf("value" to 2, "unit" to "km")))))
        // A name with no value is still a name nobody knows.
        assertTrue(refused(mapOf("value" to 1, "colour" to null))!!.startsWith("widget: a stat widget has no field 'colour'"))
        // A list where a table is expected: its places are no fields.
        assertTrue(refused(mapOf("1" to "a", "2" to "b"))!!.startsWith("widget: a stat widget has no field '1', '2'"))
    }

    /** A script can't put a command behind a tap: there is no such field. */
    @Test fun aTapTakesNoCommand() {
        assertEquals("widget: tap: no field 'command'; use args, url, label, page", refused(mapOf("value" to 1, "tap" to mapOf("command" to "torch"))))
        assertEquals("widget: tap: no field 'run'; use args, url, label, page", refused(mapOf("value" to 1, "tap" to mapOf("args" to "", "run" to "clear all"))))
    }

    // ---- bounded --------------------------------------------------------------------------

    @Test fun textIsCutAtItsLimit() {
        val long = "x".repeat(600)
        val spec = stat("title" to long, "icon" to "abcdef", "tap" to mapOf("args" to long, "label" to long))
        assertEquals("x".repeat(39) + "…", spec.base.title)
        assertEquals("abc…", spec.base.icon)
        assertEquals("x".repeat(23) + "…", spec.base.tap!!.label)
        assertEquals("x".repeat(199) + "…", spec.base.tap!!.args)
        val text = parse(mapOf("text" to long, "caption" to long), WidgetKind.TEXT) as WidgetSpec.Text
        assertEquals("x".repeat(499) + "…", text.text)
        assertEquals("x".repeat(59) + "…", text.caption)
        // At the limit, nothing is cut.
        assertEquals("y".repeat(40), stat("title" to "y".repeat(40)).base.title)
        assertEquals("☀️🌧", stat("icon" to "☀️🌧").base.icon)
    }

    /** A cut never ends in half an emoji. */
    @Test fun aCutKeepsAnEmojiWhole() {
        assertEquals("ab…", stat("icon" to "ab🌧🌧").base.icon)
    }

    @Test fun aListKeepsItsFirstItems() {
        val rows = list(*(1..20).map { mapOf("title" to "row $it") }.toTypedArray())
        assertEquals((1..12).map { "row $it" }, rows.items.map { it.title })
        val more = stat("more" to (1..5).map { mapOf("value" to it) }).more
        assertEquals(listOf("1", "2", "3"), more.map { it.value })
        // What isn't kept isn't checked.
        assertEquals(12, list(*((1..12).map { mapOf("title" to "row $it") } + listOf("not a row")).toTypedArray()).items.size)
    }

    @Test fun staleAfterAndRefreshStayInTheirRange() {
        fun base(vararg fields: Pair<String, Any?>) = stat(*fields).base
        assertEquals(60, base("staleAfter" to 5).staleAfter)
        assertEquals(604_800, base("staleAfter" to 9_000_000).staleAfter)
        assertEquals(7200, base("staleAfter" to 7200.0).staleAfter)
        assertEquals(10, base("refresh" to 1).refresh)
        assertEquals(3600, base("refresh" to 86_400).refresh)
        assertEquals(30, base("refresh" to 30.9).refresh)
        // Zero or less asks for none.
        assertNull(base("refresh" to 0).refresh)
        assertNull(base("staleAfter" to -1).staleAfter)
    }

    @Test fun progressStaysFromZeroToOne() {
        assertEquals(1f, stat("progress" to 3).progress)
        assertEquals(0f, stat("progress" to -0.5).progress)
        assertEquals(0.25f, stat("progress" to 0.25).progress)
    }

    // ---- taps -----------------------------------------------------------------------------

    @Test fun aTapRunsTheScriptAgainOrOpensALink() {
        assertEquals(WidgetTap(args = "add 1", label = "Add a glass"), stat("tap" to mapOf("args" to "add 1", "label" to "Add a glass")).base.tap)
        assertEquals(WidgetTap(url = "https://example.com/"), stat("tap" to mapOf("url" to " https://example.com/ ")).base.tap)
        // Arguments are one line, and a number is text.
        assertEquals(WidgetTap(args = "a b"), stat("tap" to mapOf("args" to "a\nb")).base.tap)
        assertEquals(WidgetTap(args = "2"), stat("tap" to mapOf("args" to 2)).base.tap)
    }

    /** `{ args = "", page = "log" }` runs the script with no arguments and turns home to the log. */
    @Test fun emptyArgumentsAreArguments() {
        assertEquals(WidgetTap(args = "", toLog = true), stat("tap" to mapOf("args" to "", "page" to "log")).base.tap)
        assertEquals(WidgetTap(args = ""), stat("tap" to mapOf("args" to "")).base.tap)
    }

    @Test fun aTapIsExactlyOneThing() {
        fun tap(vararg fields: Pair<String, Any?>) = refused(mapOf("value" to 1, "tap" to mapOf(*fields)))
        assertEquals("widget: tap: give args or url, not both", tap("args" to "a", "url" to "https://example.com"))
        assertEquals("widget: tap: give args or url", tap("label" to "Open"))
        assertEquals("widget: tap: give args or url", refused(mapOf("value" to 1, "tap" to emptyMap<String, Any?>())))
        assertEquals("widget: tap: page goes with args, not with url", tap("url" to "https://example.com", "page" to "log"))
        assertEquals("widget: tap: page must be 'log', not 'widgets'", tap("args" to "", "page" to "widgets"))
        assertEquals("widget: tap: url must be a web link, not 7", tap("url" to 7))
        assertEquals("widget: tap must be { args } or { url }, not 'weather'", refused(mapOf("value" to 1, "tap" to "weather")))
        assertEquals("widget: item 1 of items: tap: give args or url", refused(mapOf("items" to listOf(mapOf("title" to "a", "tap" to mapOf("label" to "x")))), WidgetKind.LIST))
    }

    // ---- what the host checks -------------------------------------------------------------

    /** The host masks secrets and removes styles in every text, and checks every link. */
    @Test fun everyTextAndEveryLinkGoesThroughTheHost() {
        val texts = ArrayList<String>()
        val links = ArrayList<String>()
        val spec = WidgetParser.parse(
            mapOf(
                "title" to "t", "icon" to "i", "tap" to mapOf("url" to "https://example.com/card", "label" to "tl"),
                "items" to listOf(mapOf("title" to "rt", "detail" to "rd", "trailing" to "rr", "icon" to "ri", "tap" to mapOf("args" to "ra"))),
                "empty" to "e",
            ),
            WidgetKind.LIST,
            text = { texts += it; it.uppercase() },
            link = { links += it; "checked:$it" },
        ) as WidgetSpec.Rows
        assertEquals(setOf("t", "i", "tl", "rt", "rd", "rr", "ri", "ra", "e"), texts.toSet())
        assertEquals(listOf("https://example.com/card"), links)
        assertEquals("T", spec.base.title)
        assertEquals(WidgetTap(url = "checked:https://example.com/card", label = "TL"), spec.base.tap)
        assertEquals(WidgetSpec.Row("RT", detail = "RD", trailing = "RR", icon = "RI", tap = WidgetTap(args = "RA")), spec.items.single())
        assertEquals("E", spec.empty)

        val stat = WidgetParser.parse(
            mapOf("value" to "v", "label" to "l", "unit" to "u", "delta" to "d", "more" to listOf(mapOf("value" to "mv", "label" to "ml"))),
            WidgetKind.STAT, text = { it.uppercase() }, link = { it },
        )
        assertEquals(WidgetSpec.Stat(WidgetBase(), value = "V", label = "L", unit = "U", delta = "D", more = listOf(WidgetSpec.Small("MV", "ML"))), stat)
        val text = WidgetParser.parse(mapOf("text" to "x", "caption" to "c"), WidgetKind.TEXT, text = { it.uppercase() }, link = { it })
        assertEquals(WidgetSpec.Text(WidgetBase(), text = "X", caption = "C"), text)
    }

    @Test fun theTextsOfAProgressAChartAndACountdownGoThroughTheHostToo() {
        fun up(kind: WidgetKind, vararg fields: Pair<String, Any?>) = WidgetParser.parse(mapOf(*fields), kind, text = { it.uppercase() }, link = { it })
        assertEquals(
            WidgetSpec.Progress(WidgetBase(), value = 1f, text = "T", label = "L", items = listOf(WidgetSpec.Bar("BL", 1f, 1f, "BT", null))),
            up(WidgetKind.PROGRESS, "value" to 1, "text" to "t", "label" to "l", "items" to listOf(mapOf("label" to "bl", "value" to 1, "text" to "bt"))),
        )
        assertEquals(
            WidgetSpec.Chart(WidgetBase(), points = listOf(1f), value = "V", label = "L", labels = listOf("A", "B"), summary = "S"),
            up(WidgetKind.CHART, "points" to listOf(1), "value" to "v", "label" to "l", "labels" to listOf("a", "b"), "summary" to "s"),
        )
        assertEquals(
            WidgetSpec.Countdown(WidgetBase(), to = 5, label = "L", reached = "R"),
            up(WidgetKind.COUNTDOWN, "to" to 5, "label" to "l", "reached" to "r"),
        )
    }

    /** The newer kinds: every label, title and place goes through the host, and every link is checked. */
    @Test fun theTextsAndLinksOfButtonsTogglesClockAndAgendaGoThroughTheHostToo() {
        val links = ArrayList<String>()
        fun up(kind: WidgetKind, vararg fields: Pair<String, Any?>) =
            WidgetParser.parse(mapOf(*fields), kind, text = { it.uppercase() }, link = { links += it; "checked:$it" })
        assertEquals(
            WidgetSpec.Buttons(
                WidgetBase(),
                items = listOf(WidgetSpec.Button("BL", WidgetTap(args = "BA", label = "TL"), icon = "BI"), WidgetSpec.Button("DOCS", WidgetTap(url = "checked:https://example.com/b"))),
            ),
            up(
                WidgetKind.BUTTONS,
                "items" to listOf(
                    mapOf("label" to "bl", "icon" to "bi", "tap" to mapOf("args" to "ba", "label" to "tl")),
                    mapOf("label" to "docs", "tap" to mapOf("url" to "https://example.com/b")),
                ),
            ),
        )
        assertEquals(
            WidgetSpec.Toggles(WidgetBase(staleAfter = 60), items = listOf(WidgetSpec.Toggle("SL", on = true, tap = WidgetTap(args = "SA"), icon = "SI"))),
            up(WidgetKind.TOGGLES, "items" to listOf(mapOf("label" to "sl", "icon" to "si", "on" to true, "tap" to mapOf("args" to "sa")))),
        )
        // A zone's name goes through the host before it is looked up, so a secret can't be stored as one.
        assertEquals(
            WidgetSpec.Clock(WidgetBase(), zones = listOf(WidgetSpec.Zone("UTC", "ZL"))),
            up(WidgetKind.CLOCK, "zones" to listOf(mapOf("zone" to "utc", "label" to "zl"))),
        )
        val masked = assertThrows(IllegalArgumentException::class.java) {
            WidgetParser.parse(mapOf("zones" to listOf(mapOf("zone" to "Asia/Kolkata"))), WidgetKind.CLOCK, text = { "•••" }, link = { it })
        }
        assertEquals("widget: item 1 of zones: no time zone '•••'; use a name such as Asia/Kolkata or UTC", masked.message)
        assertEquals(
            WidgetSpec.Agenda(WidgetBase(), events = listOf(WidgetSpec.Event("ET", starts = 5, where = "EW", tap = WidgetTap(url = "checked:https://example.com/e"))), empty = "EE"),
            up(WidgetKind.AGENDA, "events" to listOf(mapOf("title" to "et", "starts" to 5, "where" to "ew", "tap" to mapOf("url" to "https://example.com/e"))), "empty" to "ee"),
        )
        assertEquals(listOf("https://example.com/b", "https://example.com/e"), links)
    }

    /** A secret is masked before the cut. A cut first could leave half a secret, which nothing masks. */
    @Test fun aTextIsMaskedBeforeItIsCut() {
        val title = "x".repeat(36) + "SECRET"
        val spec = WidgetParser.parse(mapOf("value" to 1, "title" to title), WidgetKind.STAT, text = { it.replace("SECRET", "•••") }, link = { it })
        assertEquals("x".repeat(36) + "•••", spec.base.title)
    }

    /**
     * The styles go before the mask. The mask looks for a secret as it is written, so a style mark
     * in the middle of one would hide it, and taking the styles out afterwards would show it whole.
     */
    @Test fun aStyleCantHideASecretFromTheMask() {
        val mask = { s: String -> s.replace("SECRET", "•••") }
        val split = "key SEC" + StyledText.open(StyledText.Style.BOLD) + "RET" + StyledText.close(StyledText.Style.BOLD)
        val coloured = StyledText.colour(StyledText.Colour.BAD, "SEC") + "RET"
        val stat = WidgetParser.parse(
            mapOf("value" to split, "title" to coloured, "label" to split, "more" to listOf(mapOf("value" to coloured, "label" to split)), "tap" to mapOf("args" to split, "label" to coloured)),
            WidgetKind.STAT, text = mask, link = { it },
        ) as WidgetSpec.Stat
        assertEquals("key •••", stat.value)
        assertEquals("•••", stat.base.title)
        assertEquals("key •••", stat.label)
        assertEquals(listOf(WidgetSpec.Small("•••", "key •••")), stat.more)
        assertEquals(WidgetTap(args = "key •••", label = "•••"), stat.base.tap)
        val text = WidgetParser.parse(mapOf("text" to "a\n$split", "caption" to coloured), WidgetKind.TEXT, text = mask, link = { it })
        assertEquals(WidgetSpec.Text(WidgetBase(), text = "a\nkey •••", caption = "•••"), text)
        val picture = WidgetParser.parse(mapOf("image" to "https://example.com/a.png", "alt" to split, "caption" to coloured), WidgetKind.IMAGE, text = mask, link = { it })
        assertEquals(WidgetSpec.Image(WidgetBase(), image = "https://example.com/a.png", alt = "key •••", caption = "•••"), picture)
        // A zone's name too: the mask sees it without its styles, so the secret is not looked up or quoted.
        val zone = assertThrows(IllegalArgumentException::class.java) {
            WidgetParser.parse(mapOf("zones" to listOf(mapOf("zone" to coloured))), WidgetKind.CLOCK, text = mask, link = { it })
        }
        assertEquals("widget: item 1 of zones: no time zone '•••'; use a name such as Asia/Kolkata or UTC", zone.message)
        // The mask gets every text without a style mark in it.
        val seen = ArrayList<String>()
        WidgetParser.parse(mapOf("value" to StyledText.wrap(StyledText.Style.ITALIC, "v"), "unit" to StyledText.tag(StyledText.Colour.GOOD, "u")), WidgetKind.STAT, text = { seen += it; it }, link = { it })
        assertEquals(setOf("v", "u"), seen.toSet())
        assertTrue(seen.none(StyledText::has))
    }

    /** A refused link stops the run with the host's own error. */
    @Test fun aRefusedLinkIsTheHostsError() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            WidgetParser.parse(mapOf("value" to 1, "tap" to mapOf("url" to "tel:112")), WidgetKind.STAT, text = { it }, link = { throw IllegalArgumentException("widget: not a web link") })
        }
        assertEquals("widget: not a web link", e.message)
    }

    // ---- names ----------------------------------------------------------------------------

    /**
     * A script writes `{ title = … }`, so no field is a Lua keyword. And the words `status` and
     * `notify` in a script's code switch on access items, so no field has those names.
     */
    @Test fun noFieldIsALuaKeywordOrAnAccessWord() {
        assertEquals(emptySet<String>(), WidgetParser.FIELDS intersect (ApiDocs.luaKeywords + listOf("status", "notify")).toSet())
        assertTrue(WidgetParser.FIELDS.containsAll(listOf("kind", "title", "icon", "color", "tap", "staleAfter", "refresh", "args", "url", "label", "page", "value", "more", "text", "caption", "items", "empty", "detail", "trailing")))
        assertTrue(WidgetParser.FIELDS.containsAll(listOf("max", "style", "moving", "points", "min", "labels", "summary", "to", "from", "reached")))
        assertTrue(WidgetParser.FIELDS.containsAll(listOf("done", "on", "zones", "zone", "seconds", "events", "starts", "ends", "allDay", "where", "days")))
        assertTrue(WidgetParser.FIELDS.containsAll(listOf("image", "alt", "fit")))
        assertTrue(WidgetParser.FIELDS.all { Regex("[a-z][A-Za-z]*").matches(it) })
    }

    /** Every name an error lists as known is in FIELDS, so the test above covers them all. */
    @Test fun everyKnownFieldIsInFields() {
        val listed = Regex("; use (.+)$")
        val errors = listOf(
            refused(mapOf("nope" to 1), WidgetKind.STAT), refused(mapOf("nope" to 1), WidgetKind.TEXT), refused(mapOf("nope" to 1), WidgetKind.LIST),
            refused(mapOf("value" to 1, "tap" to mapOf("nope" to 1))), refused(mapOf("value" to 1, "more" to listOf(mapOf("nope" to 1)))),
            refused(mapOf("items" to listOf(mapOf("nope" to 1))), WidgetKind.LIST),
            refused(mapOf("nope" to 1), WidgetKind.PROGRESS), refused(mapOf("nope" to 1), WidgetKind.CHART), refused(mapOf("nope" to 1), WidgetKind.COUNTDOWN),
            refused(mapOf("items" to listOf(mapOf("nope" to 1))), WidgetKind.PROGRESS),
            refused(mapOf("nope" to 1), WidgetKind.BUTTONS), refused(mapOf("items" to listOf(mapOf("nope" to 1))), WidgetKind.BUTTONS),
            refused(mapOf("nope" to 1), WidgetKind.TOGGLES), refused(mapOf("items" to listOf(mapOf("nope" to 1))), WidgetKind.TOGGLES),
            refused(mapOf("nope" to 1), WidgetKind.CLOCK), refused(mapOf("zones" to listOf(mapOf("nope" to 1))), WidgetKind.CLOCK),
            refused(mapOf("nope" to 1), WidgetKind.AGENDA), refused(mapOf("events" to listOf(mapOf("nope" to 1))), WidgetKind.AGENDA),
            refused(mapOf("nope" to 1), WidgetKind.IMAGE),
        )
        val known = errors.flatMap { listed.find(it!!)!!.groupValues[1].split(", ") }.toSet()
        assertEquals(WidgetParser.FIELDS, known)
    }
}
