package com.mtwabpin.kolahal.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.control.NowPlaying
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.StyledText.Colour
import com.mtwabpin.kolahal.productivity.AgendaEvent
import com.mtwabpin.kolahal.productivity.Note
import com.mtwabpin.kolahal.productivity.Reminder
import com.mtwabpin.kolahal.productivity.Todo
import com.mtwabpin.kolahal.status.Status
import com.mtwabpin.kolahal.terminal.ProductivityCommands
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.Shortcut
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetLayout
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetPlacement
import com.mtwabpin.kolahal.widgets.WidgetSize
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSource
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A picture of every kind of card and of every ready-made widget, as the real code draws them.
 * It is a tool: the pictures are for looking at the cards beside a design mockup. It checks only
 * that each picture was made.
 *
 * - The cards have the example content of the widgets mockup (docs/widgets-design.org), as far as
 *   a card's description can carry it.
 * - A built-in card is made by the code that makes it on the page ([OwnCards], [BatteryCards]),
 *   so it has what the real card has, and no more.
 * - Each card has the size the widgets page gives it on this screen ([WidgetSizes], [WidgetLayout]),
 *   in the Gruvbox Material dark theme, with 24-hour times.
 * - A clock shows the time of the run. Countdowns and events are set from the time of the run.
 * - No script runs, nothing is stored, and nothing is fetched.
 *
 * The pictures go to the app's own files on the device, with a list of them (`manifest.json`):
 *
 *     adb pull /sdcard/Android/data/com.mtwabpin.kolahal.debug/files/gallery
 */
@RunWith(AndroidJUnit4::class)
class WidgetGalleryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val config = Config.get(context)
    private val paletteBefore = Term.palette
    private val zone: ZoneId = ZoneId.systemDefault()
    private val nowMs = System.currentTimeMillis()
    private val now = nowMs / 1000
    private val today: LocalDate = LocalDate.now(zone)

    /** The widgets page's width inside its margins, as the page counts it for a new widget. */
    private val pageDp = (context.resources.configuration.screenWidthDp - 2 * WidgetLayout.GAP_DP).coerceAtLeast(120)

    @Before fun setUp() {
        Term.palette = Palettes.gruvboxMaterial
        // Shown, not saved: nothing is left behind.
        config.preview(Keys.CLOCK_24H, "on")
        pictureFile(HILLS).outputStream().use { hills().compress(Bitmap.CompressFormat.PNG, 100, it) }
        pictureFile(COMIC).outputStream().use { comic().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @After fun tearDown() {
        Term.palette = paletteBefore
        config.preview(Keys.CLOCK_24H, null)
        pictureFile(HILLS).delete()
        pictureFile(COMIC).delete()
        LogImages.trim()
    }

    /**
     * One picture: [spec]'s card at [size], under [title], saved as `real-<name>-<size>.png`.
     * With [inView] the card is drawn as on the page in view: a wave moves and a picture loads.
     * With [taps] the card takes taps, as on the page: a media card's bar is then a slider.
     */
    private data class Shot(val name: String, val size: WidgetSize, val title: String, val spec: WidgetSpec, val inView: Boolean = false, val prefix: String = "real", val look: WidgetLook = WidgetLook.PLAIN, val taps: Boolean = false) {
        val file get() = "$prefix-$name-${size.id}.png"
    }

    private var shown by mutableStateOf<Shot?>(null)

    /** Every card is drawn, one after the other, and each is saved as a picture. */
    @Test fun everyCardIsDrawnAndSaved() = draw(kinds() + readyMade(), "gallery")

    /**
     * Every card in each look ([WidgetLook]), saved in `files/gallery-looks` as
     * `<look>-<name>-<size>.png`. Instrumentation arguments narrow it down: `looks` (a comma list
     * such as `tonal,shape`; all looks without it) and `names` (a comma list of card names, as in the
     * file names; all cards without it). With `light` set to `true` the cards are in the theme's
     * light version.
     *
     *     adb pull /sdcard/Android/data/com.mtwabpin.kolahal.debug/files/gallery-looks
     */
    @Test fun everyLookIsDrawnAndSaved() {
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("light") == "true") Term.palette = Palettes.gruvboxMaterialLight
        val looks = args.getString("looks")?.split(',')?.mapNotNull { WidgetLook.named(it) }?.ifEmpty { null } ?: WidgetLook.entries
        val names = args.getString("names")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
        val cards = (kinds() + readyMade()).filter { names == null || it.name in names }
        draw(looks.flatMap { look -> cards.map { it.copy(prefix = look.id, look = look) } }, "gallery-looks")
    }

    /**
     * The website's pictures of the scripting guide's widget examples: each example's Lua code runs
     * as a script ([DocsExamples]), and its card is drawn from what it handed to `widget.show`. They
     * go to `files/gallery-docs`, as `docs-<name>-<size>.png`, with the ready-made
     * cards as `real-<name>-<size>.png`: all in Kolahal's default theme (green), as the website's
     * other screenshots are.
     */
    @Test fun theGuidesExamplesAreDrawnAndSaved() {
        // The website shows Kolahal's default theme, as its other screenshots do.
        Term.palette = Palettes.green
        pictureFile(DocsExamples.PICTURE).outputStream().use { hills().compress(Bitmap.CompressFormat.PNG, 100, it) }
        try {
            val shots = DocsExamples.widgets.map { ex ->
                val spec = DocsExamples.spec(context, ex)
                // Without a title in the code, the card has the script's @title.
                Shot(ex.name, ex.size, spec.base.title ?: ex.title, spec, inView = ex.kind == com.mtwabpin.kolahal.widgets.WidgetKind.IMAGE, prefix = "docs")
            }
            // The ready-made cards too, for the library and widgets pages.
            draw(shots + readyMade(), "gallery-docs")
        } finally {
            pictureFile(DocsExamples.PICTURE).delete()
        }
    }

    /**
     * The website's picture of the widgets page (site/src/assets/parts/widgets-page.png): the page's
     * title row, and a few ready-made cards laid out as the page lays them out. It goes to
     * `files/gallery-page/widgets-page.png`.
     */
    @Test fun thePageIsDrawnAndSaved() {
        Term.palette = Palettes.green
        val out = checkNotNull(context.getExternalFilesDir("gallery-page")) { "no external files" }
        out.listFiles()?.forEach { it.delete() }
        val ready = readyMade()
        val cards = listOf("weather" to WidgetSize.S2X1, "battery" to WidgetSize.S2X1, "todos" to WidgetSize.S2X2, "habit" to WidgetSize.S2X2, "shortcuts" to WidgetSize.S4X1)
            .map { (name, size) -> ready.single { it.name == name && it.size == size } }
        // Each card's place, as a page from 0.5.14 gets them at its first start.
        val places = WidgetPlacement.placeMissing(
            cards.mapIndexed { i, shot ->
                PlacedWidget(null, WidgetSource.script("gallery$i"), 0L, WidgetSizes.heightDp(shot.size, pageDp), i, span = shot.size.span)
            },
        )
        compose.setContent {
            KolahalTheme {
                GuiTheme {
                    androidx.compose.foundation.layout.Column(
                        Modifier.testTag(SHOT).width((pageDp + 2 * WidgetLayout.GAP_DP).dp).background(Term.bg).padding(vertical = 8.dp),
                    ) {
                        WidgetsTitleRow(onAdd = {}, onEdit = {})
                        WidgetGrid(
                            WidgetLayout.GAP_DP.dp,
                            Modifier.fillMaxWidth().padding(horizontal = WidgetLayout.GAP_DP.dp, vertical = 4.dp),
                        ) {
                            cards.forEachIndexed { i, shot ->
                                Box(Modifier.gridPlace(places[i])) {
                                    WidgetCard(
                                        title = shot.title,
                                        height = WidgetSizes.heightDp(shot.size, pageDp).dp,
                                        spec = shot.spec,
                                        note = null,
                                        description = WidgetWords.description(shot.title, shot.spec, now, zone, clock24 = true),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val file = File(out, "widgets-page.png")
        file.outputStream().use { compose.onNodeWithTag(SHOT).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("${file.name} is empty", file.length() > 0)
    }

    /** Draws each of [all] and saves it in the app's files, in [dir], with a list of them. */
    private fun draw(all: List<Shot>, dir: String) {
        val out = checkNotNull(context.getExternalFilesDir(dir)) { "no external files" }
        out.listFiles()?.forEach { it.delete() }
        assertEquals("two pictures have one name", all.size, all.map { it.file }.toSet().size)

        // A wave moves for as long as it is drawn. So the test moves the clock itself.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KolahalTheme {
                GuiTheme {
                    val shot = shown
                    if (shot != null) key(shot) {
                        CompositionLocalProvider(LocalWidgetsShown provides shot.inView, LocalWidgetLook provides shot.look) {
                            // The page's colour shows around the card's round corners.
                            Box(Modifier.testTag(SHOT).background(Term.bg).padding(MARGIN_DP.dp)) {
                                Box(Modifier.width(WidgetLayout.widthDp(pageDp, shot.size.span).dp)) {
                                    WidgetCard(
                                        title = shot.title,
                                        height = WidgetSizes.heightDp(shot.size, pageDp).dp,
                                        spec = shot.spec,
                                        note = null,
                                        description = WidgetWords.description(shot.title, shot.spec, now, zone, clock24 = true),
                                        onTap = if (shot.taps) ({ _: WidgetTap -> }) else null,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val cards = ArrayList<String>()
        for (shot in all) {
            shown = shot
            // A wave grows to its full height in about half a second.
            compose.mainClock.advanceTimeBy(if (shot.inView) 1_200 else 200)
            if (shot.spec is WidgetSpec.Image) waitForPicture(shot)
            compose.waitForIdle()
            val picture = compose.onNodeWithTag(SHOT).captureToImage().asAndroidBitmap()
            val file = File(out, shot.file)
            file.outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue("${shot.file} is empty", file.length() > 0)
            val w = WidgetLayout.widthDp(pageDp, shot.size.span)
            val h = WidgetSizes.heightDp(shot.size, pageDp)
            cards += """{"name":"${shot.name}-${shot.size.id}","w":$w,"h":$h}"""
        }
        val density = context.resources.displayMetrics.density
        File(out, "manifest.json").writeText("""{"density":$density,"pageDp":$pageDp,"margin":$MARGIN_DP,"cards":[${cards.joinToString(",")}]}""")
        assertEquals(all.size + 1, out.listFiles()?.size)
    }

    /** Waits until the card in view draws its picture. The file is read off the main thread. */
    private fun waitForPicture(shot: Shot) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (compose.onAllNodes(hasTestTag("widget-picture"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
            assertTrue("${shot.file}: the picture didn't load", SystemClock.uptimeMillis() < deadline)
            Thread.sleep(20)
            compose.mainClock.advanceTimeByFrame()
        }
    }

    // ---- what the cards hold ------------------------------------------------------------------

    private fun base(title: String? = null, icon: String? = null, colour: Colour? = null) = WidgetBase(title = title, icon = icon, color = colour)

    private fun link(n: Int) = WidgetTap(url = "https://example.com/$n")

    /** A time of day on the day [daysOn] days from today, in seconds. */
    private fun at(daysOn: Long, hour: Int, minute: Int): Long = today.plusDays(daysOn).atTime(hour, minute).atZone(zone).toEpochSecond()

    private val media = OwnCards.media(
        NowPlaying(
            pkg = "com.example.music", app = "Music", title = "Gymnopédie No. 1", artist = "Erik Satie",
            // Speed 0: the position stays at 1:12 while the picture is taken.
            state = PlaybackState.STATE_PLAYING, position = 72_000, positionAt = nowMs, speed = 0f, duration = 185_000, idleSince = 0,
        ),
    )

    /** The same track from a player that gives its cover and can seek: on the page its bar is a slider. */
    private val mediaCover = media.copy(canSeek = true, cover = album())

    /** The card with nothing playing: the player that played last, with Play. */
    private val mediaLast = OwnCards.lastPlayed(com.mtwabpin.kolahal.control.LastPlayer("com.example.music", "Music", "Clair de lune", "Claude Debussy"))

    private val room = listOf(23.5f, 23.1f, 22.7f, 22.4f, 22.6f, 23.3f, 24.2f, 25.1f, 25.8f, 26.0f, 25.5f, 24.6f)

    /** Five weeks of a habit, the oldest day first: one day in seven missed, and two more. The last five, today too, are done. */
    private val habitDays = List(35) { if (it % 7 == 1 || it == 12 || it == 26) 0f else 1f }

    /** Unlocks a day, as the mockup has them, for dots that are as large as their value. */
    private val unlockDays = listOf(
        44f, 38f, 52f, 47f, 61f, 30f, 28f, 46f, 41f, 55f, 49f, 58f, 33f, 26f, 40f, 45f, 50f,
        43f, 62f, 35f, 31f, 47f, 39f, 53f, 48f, 57f, 29f, 27f, 45f, 40f, 51f, 44f, 42f,
    )

    /** The habit card as habit.lua fills it, done today: five days in a row. */
    private val habit = WidgetSpec.Chart(base("Exercise", "check", Colour.GOOD).copy(note = "Done today"), habitDays, style = "dots", value = "5", label = "Days in a row")

    /** Three days as forecast.js sends them: each with its sky, its low and high, and a bar from the one to the other. */
    private val forecast = WidgetSpec.Rows(
        base("Pune", "sun", Colour.INFO),
        listOf(
            WidgetSpec.Row("Sat", detail = "Clear", trailing = "22–31°", icon = "sun", range = WidgetSpec.Range(22f, 31f)),
            WidgetSpec.Row("Sun", detail = "Overcast", trailing = "21–30°", icon = "cloud", range = WidgetSpec.Range(21f, 30f)),
            WidgetSpec.Row("Mon", detail = "Rain", trailing = "20–27°", icon = "rain", range = WidgetSpec.Range(20f, 27f)),
        ),
    )

    /** One or two cards for each kind, with the mockup's examples. */
    private fun kinds(): List<Shot> = listOf(
        Shot(
            "kind-stat", WidgetSize.S2X2, "Site visits",
            WidgetSpec.Stat(
                base("Site visits", "up"), value = "1,284", label = "today", delta = "+2.4%", trend = "up",
                more = listOf(WidgetSpec.Small("312", "new"), WidgetSpec.Small("4.1", "minutes"), WidgetSpec.Small("38%", "mobile")),
            ),
        ),
        Shot("kind-stat", WidgetSize.S1X1, "Weather", WidgetSpec.Stat(base("Weather"), value = "27°", label = "Sunny")),
        Shot(
            "kind-text", WidgetSize.S4X1, "Weather note",
            WidgetSpec.Text(base("Weather note"), "Carry an umbrella after 4 pm. Rain is likely until late evening, then the sky clears.", "Today · from your weather script"),
        ),
        Shot(
            "kind-list", WidgetSize.S4X2, "Parcels",
            WidgetSpec.Rows(
                base("Parcels", "parcel"),
                listOf(
                    WidgetSpec.Row("Keyboard", "Out for delivery", "Today", color = Colour.GOOD),
                    WidgetSpec.Row("Books", "In transit · Nagpur", "Mon", color = Colour.INFO),
                    WidgetSpec.Row("Charger", "Delayed by 2 days", "Wed", color = Colour.WARN),
                    WidgetSpec.Row("Headphones", "Shipped", "Thu"),
                    WidgetSpec.Row("Desk lamp", "Ordered", "Fri"),
                    WidgetSpec.Row("USB cable", "Ordered", "Sat"),
                ),
            ),
        ),
        Shot("kind-progress", WidgetSize.S1X1, "Goal", WidgetSpec.Progress(base("Goal"), value = 72f, max = 100f, label = "Goal")),
        Shot(
            "kind-progress", WidgetSize.S2X1, "Usage",
            WidgetSpec.Progress(
                base("Usage"), value = 0f,
                items = listOf(WidgetSpec.Bar("Storage", 41f, 64f, "41 / 64 GB", null), WidgetSpec.Bar("Mobile data", 4.1f, 5f, "4.1 / 5 GB", Colour.WARN)),
            ),
        ),
        Shot(
            "kind-chart", WidgetSize.S2X2, "Room",
            WidgetSpec.Chart(base("Room", "temp", Colour.INFO), room, value = "24.6°", label = "now", labels = listOf("24 h ago", "now")),
        ),
        Shot("kind-chart", WidgetSize.S2X1, "Sensor", WidgetSpec.Chart(base("Sensor", colour = Colour.INFO), room, value = "24.6°", label = "Room")),
        // Dots: a habit's days done and missed, today still open in the smaller card; and values by size.
        Shot("kind-dots", WidgetSize.S4X2, "Exercise", habit),
        Shot("kind-dots", WidgetSize.S2X1, "Exercise", WidgetSpec.Chart(base("Exercise", "check"), habitDays.dropLast(1) + 0f, style = "dots", value = "4", label = "Days in a row")),
        Shot("kind-dots", WidgetSize.S2X2, "Unlocks", WidgetSpec.Chart(base("Unlocks", "unlock", Colour.INFO).copy(note = "42 today"), unlockDays, style = "dots")),
        Shot(
            "kind-countdown", WidgetSize.S2X1, "Match starts",
            // 3:25:10 to go, and 72% of the wait is over.
            WidgetSpec.Countdown(base("Match starts", "bell", Colour.WARN), to = now + 12_310, from = now - 31_654, style = "clock"),
        ),
        Shot(
            "kind-countdown", WidgetSize.S1X1, "Exam",
            WidgetSpec.Countdown(base("Exam", colour = Colour.WARN), to = now + 12 * 86_400 + 3_600, label = "exam", style = "days"),
        ),
        Shot(
            "kind-buttons", WidgetSize.S2X2, "Timer",
            WidgetSpec.Buttons(
                base("Timer"),
                listOf("5 min" to "5", "10 min" to "10", "25 min" to "25", "Stop" to "stop").map { (label, args) -> WidgetSpec.Button(label, WidgetTap(args = args)) },
            ),
        ),
        Shot(
            "kind-toggles", WidgetSize.S2X1, "Home",
            WidgetSpec.Toggles(
                base("Home"),
                listOf(WidgetSpec.Toggle("Lamp", on = true, tap = WidgetTap(args = "lamp")), WidgetSpec.Toggle("Fan", on = false, tap = WidgetTap(args = "fan"))),
            ),
        ),
        // One switch on a wide card: it sits in the middle, no wider than a switch gets.
        Shot(
            "kind-toggle-one", WidgetSize.S4X1, "Home",
            WidgetSpec.Toggles(base("Home"), listOf(WidgetSpec.Toggle("Do Not Disturb", on = true, tap = WidgetTap(args = "dnd")))),
        ),
        Shot(
            "kind-clock", WidgetSize.S2X1, "Clocks",
            WidgetSpec.Clock(base("Clocks", colour = Colour.LINK), listOf(WidgetSpec.Zone("Asia/Kolkata", "Pune"), WidgetSpec.Zone("Europe/London", "London"))),
        ),
        Shot(
            "kind-agenda", WidgetSize.S2X2, "Matches",
            // No ends: the events stay for the whole day, at whatever hour the test runs.
            WidgetSpec.Agenda(
                base("Matches", "trophy", Colour.INFO),
                listOf(WidgetSpec.Event("IND v NZ", at(0, 19, 30)), WidgetSpec.Event("AUS v ENG", at(1, 14, 0)), WidgetSpec.Event("SA v PAK", at(1, 19, 30))),
            ),
        ),
        Shot("kind-media", WidgetSize.S4X1, "Now playing", media),
        Shot("kind-media-cover", WidgetSize.S4X1, "Now playing", mediaCover, taps = true),
        Shot(
            "kind-image", WidgetSize.S2X2, "Photo of the day",
            WidgetSpec.Image(base("Photo of the day"), image = HILLS, alt = "Hills at sunrise", caption = "Sunrise over the ghats"),
            inView = true,
        ),
    )

    /** The ready-made widgets of the mockup: the library scripts' cards, and Kolahal's own. */
    private fun readyMade(): List<Shot> {
        val weather = WidgetSpec.Stat(
            base("Pune", "sun"), value = "27°", label = "Sunny",
            more = listOf(WidgetSpec.Small("62%", "humidity"), WidgetSpec.Small("9 km/h", "wind")),
        )
        val aqi = WidgetSpec.Progress(base("US AQI · Pune", "wind", Colour.WARN), value = 118f, max = 500f, text = "118", label = "Unhealthy for some")
        // Its label as countdown.lua writes it: "to 20 December".
        val goaDay = today.plusDays(37)
        val goa = WidgetSpec.Countdown(
            base("Goa trip", "calendar", Colour.LINK), to = goaDay.atStartOfDay(zone).toEpochSecond(),
            label = "to ${goaDay.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale.ENGLISH))}", style = "days", reached = "Today",
        )
        val headlines = listOf(
            "Show HN: A terminal launcher for Android" to "412", "SQLite as a document database" to "368",
            "The unreasonable effectiveness of plain text" to "301", "Why your battery percentage lies" to "274",
            "A font made of one line" to "253", "Notes on writing a small Lua VM" to "197", "The last floppy disk factory" to "166", "Ask HN: What do you self-host?" to "140",
        )
        val feed = listOf("Release notes 2.4", "Faster test runs", "Lua tables guide", "A week with tiling", "Pixel fonts", "Small tools", "Reading logs", "Old phones")
        val battery = Status(batteryPct = 68, charging = true, network = "wifi")
        val week = listOf(41f, 58f, 37f, 64f, 52f, 71f, 46f)
        val ago = listOf("6 days ago", "today")
        val todos = listOf("Pay electricity bill", "Book train tickets", "Call the plumber", "Renew the passport", "Water the plants")
        val calendar = listOf(
            AgendaEvent(1, "Team call", at(0, 10, 0) * 1000, at(0, 10, 30) * 1000, allDay = false, where = "Meet", calendar = 1),
            AgendaEvent(2, "Dentist", at(0, 15, 30) * 1000, at(0, 16, 15) * 1000, allDay = false, where = "MG Road", calendar = 2),
            // The calendar keeps an all-day event at midnight UTC.
            AgendaEvent(3, "Mum's birthday", today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), today.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), allDay = true, calendar = 3),
        )
        // As read at the start of the day. Without the ends, no event drops out or says "now" at the hour of the run.
        val agenda = OwnCards.agenda(calendar, zone, today.atStartOfDay(zone).toInstant().toEpochMilli(), ProductivityCommands.DEFAULT_AGENDA_DAYS).let { a -> a.copy(events = a.events.map { it.copy(ends = null) }) }
        val reminders = listOf(Reminder(1, nowMs + 42 * 60_000, "Call the bank"), Reminder(2, at(1, 7, 0) * 1000, "Water plants"), Reminder(3, at(3, 9, 0) * 1000, "Pay rent"))
        val shortcuts = listOf(Shortcut("torch", "torch", "torch"), Shortcut("dnd", "dnd", "moon"), Shortcut("todo", "todo", "check"), Shortcut("maps", "maps", "pin"))
        return listOf(
            Shot("weather", WidgetSize.S2X1, "Pune", weather),
            Shot("weather", WidgetSize.S1X1, "Pune", weather),
            Shot("aqi", WidgetSize.S2X2, "US AQI · Pune", aqi),
            Shot("aqi", WidgetSize.S1X1, "US AQI · Pune", aqi),
            Shot("forecast", WidgetSize.S2X2, "Pune", forecast),
            Shot("forecast", WidgetSize.S4X2, "Pune", forecast),
            // The larger card has a start, for its bar: 55% of the wait is over.
            Shot("countdown-date", WidgetSize.S2X2, "Goa trip", goa.copy(from = now - 44 * 86_400)),
            Shot("countdown-date", WidgetSize.S2X1, "Goa trip", goa),
            Shot("countdown-date", WidgetSize.S1X1, "Goa trip", goa),
            Shot(
                "hn", WidgetSize.S4X2, "Hacker News",
                WidgetSpec.Rows(base("Hacker News", "news", Colour.WARN), headlines.mapIndexed { i, (title, points) -> WidgetSpec.Row(title, trailing = points, tap = link(i)) }),
            ),
            Shot("feed", WidgetSize.S2X2, "My feed", WidgetSpec.Rows(base("My feed", "feed", Colour.LINK), feed.mapIndexed { i, title -> WidgetSpec.Row(title, tap = link(i)) })),
            Shot(
                "quote", WidgetSize.S2X2, "Quote of the day",
                WidgetSpec.Text(base("Quote of the day", "quote"), "Simplicity is prerequisite for reliability.", "Edsger W. Dijkstra"),
            ),
            Shot("battery", WidgetSize.S2X1, BatteryCards.title(battery), BatteryCards.spec(battery)),
            Shot("battery", WidgetSize.S1X1, BatteryCards.title(battery), BatteryCards.spec(battery)),
            Shot(
                "unlocks", WidgetSize.S2X2, "Unlocks",
                WidgetSpec.Chart(base("Unlocks", "unlock", Colour.INFO), week, style = "bars", min = 0f, max = 71f, value = "46", label = "unlocks today", labels = ago),
            ),
            Shot(
                "webnumber", WidgetSize.S2X1, "Living room",
                WidgetSpec.Stat(base("Living room", "bolt", Colour.INFO), value = "21.4", unit = "°C", label = "sensor at home"),
            ),
            Shot("webnumber", WidgetSize.S1X1, "Stars", WidgetSpec.Stat(base("Stars", "bolt", Colour.INFO), value = "847", label = "stars")),
            Shot(
                "crypto", WidgetSize.S4X2, "Crypto",
                WidgetSpec.Rows(
                    base("Crypto", "chart"),
                    listOf(
                        WidgetSpec.Row("Bitcoin", "+1.8% today", "$97,420", color = Colour.GOOD),
                        WidgetSpec.Row("Ethereum", "−0.6% today", "$3,310", color = Colour.BAD),
                        WidgetSpec.Row("Solana", "+3.1% today", "$212", color = Colour.GOOD),
                    ),
                ),
            ),
            Shot("shortcuts", WidgetSize.S4X1, "Shortcuts", checkNotNull(OwnCards.shortcuts(shortcuts))),
            Shot("nowplaying", WidgetSize.S4X2, "Now playing", media),
            Shot("nowplaying-cover", WidgetSize.S4X2, "Now playing", mediaCover, taps = true),
            Shot("nowplaying-seek", WidgetSize.S4X2, "Now playing", media.copy(canSeek = true), taps = true),
            Shot("nowplaying-last", WidgetSize.S4X2, "Now playing", mediaLast, taps = true),
            Shot("nowplaying-last", WidgetSize.S4X1, "Now playing", mediaLast, taps = true),
            Shot("todos", WidgetSize.S2X2, "Todos", OwnCards.todos(todos.mapIndexed { i, text -> Todo(i + 1L, text, done = false) })),
            Shot("agenda", WidgetSize.S4X2, "Agenda", agenda),
            Shot("reminders", WidgetSize.S2X2, "Reminders", OwnCards.reminders(reminders, nowMs, zone, clock24 = true)),
            Shot("notes", WidgetSize.S2X1, "Notes", checkNotNull(OwnCards.notes(listOf(Note(1, "Parcel comes Tuesday. The gate code is with the guard.", nowMs))))),
            Shot(
                "worldclocks", WidgetSize.S2X2, "World clocks",
                WidgetSpec.Clock(
                    base("World clocks", "globe", Colour.LINK),
                    listOf(WidgetSpec.Zone("Asia/Kolkata", "Kolkata"), WidgetSpec.Zone("Europe/London", "London"), WidgetSpec.Zone("America/New_York", "New York")),
                ),
            ),
            // At 4×2 the places have their day strips.
            Shot(
                "worldclocks", WidgetSize.S4X2, "World clocks",
                WidgetSpec.Clock(
                    base("World clocks", "globe", Colour.LINK),
                    listOf(WidgetSpec.Zone("Asia/Kolkata", "Kolkata"), WidgetSpec.Zone("Europe/London", "London"), WidgetSpec.Zone("America/New_York", "New York")),
                ),
            ),
            Shot("habit", WidgetSize.S2X2, "Exercise", habit),
            Shot(
                "quicktoggles", WidgetSize.S4X1, "Quick toggles",
                WidgetSpec.Toggles(
                    base("Quick toggles"),
                    listOf(
                        WidgetSpec.Toggle("Do Not Disturb", on = true, tap = WidgetTap(args = "dnd"), icon = "moon"),
                        WidgetSpec.Toggle("Work profile", on = false, tap = WidgetTap(args = "work"), icon = "work"),
                    ),
                ),
            ),
            Shot(
                "currency", WidgetSize.S2X2, "1 USD",
                WidgetSpec.Rows(base("1 USD", "money", Colour.INFO), listOf("INR" to "84.40", "EUR" to "0.92", "GBP" to "0.79").map { (to, rate) -> WidgetSpec.Row(to, trailing = rate) }),
            ),
            Shot(
                "releases", WidgetSize.S4X2, "Releases",
                WidgetSpec.Rows(
                    base("Releases", "code", Colour.LINK),
                    listOf(Triple("neovim/neovim", "3 days ago", "v0.11.4"), Triple("junegunn/fzf", "1 week ago", "v0.65"), Triple("lua/lua", "2 weeks ago", "v5.4.8"))
                        .mapIndexed { i, (repo, age, tag) -> WidgetSpec.Row(repo, age, tag, tap = link(i)) },
                ),
            ),
            Shot(
                "livescore", WidgetSize.S4X1, "NZ v IND · live",
                WidgetSpec.Stat(base("NZ v IND · live", "cricket", Colour.BAD), value = "NZ 136/8", label = "31.2 of 50 overs", progress = 0.62f, moving = true),
                inView = true,
            ),
            Shot(
                "comic", WidgetSize.S2X2, "xkcd",
                WidgetSpec.Image(base("xkcd"), image = COMIC, alt = "Two stick figures talk", caption = "Today's comic", fit = "fit"),
                inView = true,
            ),
        )
    }

    // ---- the pictures of the image cards ------------------------------------------------------

    /** Where [LogImages] keeps [url]'s download: put there, nothing is fetched. */
    private fun pictureFile(url: String): File {
        val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "log-images").apply { mkdirs() }, name)
    }

    /** Hills at sunrise, in the theme's colours. */
    private fun hills(): Bitmap {
        val p = Palettes.gruvboxMaterial
        val picture = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(picture)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(lerp(p.surface, p.link, 0.34f).toArgb())
        paint.color = p.script.toArgb()
        canvas.drawCircle(430f, 215f, 70f, paint)
        paint.color = lerp(p.surface, p.info, 0.6f).toArgb()
        canvas.drawPath(hill(310f, 190f, 250f), paint)
        paint.color = lerp(p.bg, p.good, 0.78f).toArgb()
        canvas.drawPath(hill(380f, 330f, 400f), paint)
        return picture
    }

    /** A hill across the picture: it starts at [left], and bends through [top] to [right]. */
    private fun hill(left: Float, top: Float, right: Float) = Path().apply {
        moveTo(0f, left)
        quadTo(320f, top - (left + right) / 2 + top, 640f, right)
        lineTo(640f, 480f)
        lineTo(0f, 480f)
        close()
    }

    /** An album's cover, as a player sends it: a disc on a field of colour, at 512 pixels. */
    private fun album(): Bitmap {
        val p = Palettes.gruvboxMaterial
        val picture = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(picture)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(lerp(p.surface, p.warn, 0.7f).toArgb())
        paint.color = lerp(p.bg, p.surface, 0.5f).toArgb()
        canvas.drawCircle(256f, 256f, 180f, paint)
        paint.color = lerp(p.surface, p.error, 0.8f).toArgb()
        canvas.drawCircle(256f, 256f, 60f, paint)
        return picture
    }

    /** A strip with two stick figures, as a comic has them. */
    private fun comic(): Bitmap {
        val p = Palettes.gruvboxMaterial
        val picture = Bitmap.createBitmap(720, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(picture)
        canvas.drawColor(lerp(p.surface, p.fg, 0.08f).toArgb())
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.fg.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
        }
        for (x in listOf(230f, 490f)) {
            canvas.drawCircle(x, 110f, 26f, ink)
            canvas.drawLine(x, 136f, x, 215f, ink)
            canvas.drawLine(x - 34f, 172f, x + 34f, 160f, ink)
            canvas.drawLine(x, 215f, x - 26f, 270f, ink)
            canvas.drawLine(x, 215f, x + 26f, 270f, ink)
        }
        // What one of them says.
        canvas.drawLine(280f, 50f, 440f, 50f, ink)
        canvas.drawLine(300f, 72f, 400f, 72f, ink)
        canvas.drawRect(12f, 12f, 708f, 288f, ink)
        return picture
    }

    private companion object {
        const val SHOT = "gallery-shot"

        /** Room around a card in its picture, in dp. */
        const val MARGIN_DP = 4
        const val HILLS = "https://example.com/widget-gallery-hills.png"
        const val COMIC = "https://example.com/widget-gallery-comic.png"
    }
}
