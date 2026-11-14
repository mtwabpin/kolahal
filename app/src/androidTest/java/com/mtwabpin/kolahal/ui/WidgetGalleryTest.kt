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
import com.mtwabpin.kolahal.data.StatusLine
import com.mtwabpin.kolahal.data.StyledText.Colour
import com.mtwabpin.kolahal.productivity.AgendaEvent
import com.mtwabpin.kolahal.productivity.Note
import com.mtwabpin.kolahal.productivity.Reminder
import com.mtwabpin.kolahal.productivity.Todo
import com.mtwabpin.kolahal.status.Status
import com.mtwabpin.kolahal.terminal.OwnData
import com.mtwabpin.kolahal.widgets.Shortcut
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetLayout
import com.mtwabpin.kolahal.widgets.WidgetSize
import com.mtwabpin.kolahal.widgets.WidgetSizes
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
 * - A built-in card is made by the code that makes it on the page ([OwnCards], [BatteryCards],
 *   [StatusCards]), so it has what the real card has, and no more.
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
     */
    private class Shot(val name: String, val size: WidgetSize, val title: String, val spec: WidgetSpec, val inView: Boolean = false) {
        val file get() = "real-$name-${size.id}.png"
    }

    private var shown by mutableStateOf<Shot?>(null)

    /** Every card is drawn, one after the other, and each is saved as a picture. */
    @Test fun everyCardIsDrawnAndSaved() {
        val out = checkNotNull(context.getExternalFilesDir("gallery")) { "no external files" }
        out.listFiles()?.forEach { it.delete() }
        val all = kinds() + readyMade()
        assertEquals("two pictures have one name", all.size, all.map { it.file }.toSet().size)

        // A wave moves for as long as it is drawn. So the test moves the clock itself.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KolahalTheme {
                GuiTheme {
                    val shot = shown
                    if (shot != null) key(shot) {
                        CompositionLocalProvider(LocalWidgetsShown provides shot.inView) {
                            // The page's colour shows around the card's round corners.
                            Box(Modifier.testTag(SHOT).background(Term.bg).padding(MARGIN_DP.dp)) {
                                Box(Modifier.width(WidgetLayout.widthDp(pageDp, shot.size.span).dp)) {
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

    private val room = listOf(23.5f, 23.1f, 22.7f, 22.4f, 22.6f, 23.3f, 24.2f, 25.1f, 25.8f, 26.0f, 25.5f, 24.6f)

    /** One or two cards for each kind, with the mockup's examples. */
    private fun kinds(): List<Shot> = listOf(
        Shot(
            "kind-stat", WidgetSize.S2X2, "Site visits",
            WidgetSpec.Stat(
                base("Site visits", "📈"), value = "1,284", label = "today", delta = "+2.4%", trend = "up",
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
                base("Parcels", "P"),
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
            WidgetSpec.Chart(base("Room", "📈", Colour.INFO), room, value = "24.6°", label = "now", labels = listOf("24 h ago", "now")),
        ),
        Shot("kind-chart", WidgetSize.S2X1, "Sensor", WidgetSpec.Chart(base("Sensor", colour = Colour.INFO), room, value = "24.6°", label = "Room")),
        Shot(
            "kind-countdown", WidgetSize.S2X1, "Match starts",
            // 3:25:10 to go, and 72% of the wait is over.
            WidgetSpec.Countdown(base("Match starts", "🔔", Colour.WARN), to = now + 12_310, from = now - 31_654, style = "clock"),
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
        Shot(
            "kind-clock", WidgetSize.S2X1, "Clocks",
            WidgetSpec.Clock(base("Clocks", colour = Colour.LINK), listOf(WidgetSpec.Zone("Asia/Kolkata", "Pune"), WidgetSpec.Zone("Europe/London", "London"))),
        ),
        Shot(
            "kind-agenda", WidgetSize.S2X2, "Matches",
            // No ends: the events stay for the whole day, at whatever hour the test runs.
            WidgetSpec.Agenda(
                base("Matches", "M", Colour.INFO),
                listOf(WidgetSpec.Event("IND v NZ", at(0, 19, 30)), WidgetSpec.Event("AUS v ENG", at(1, 14, 0)), WidgetSpec.Event("SA v PAK", at(1, 19, 30))),
            ),
        ),
        Shot("kind-media", WidgetSize.S4X1, "Now playing", media),
        Shot(
            "kind-image", WidgetSize.S2X2, "Photo of the day",
            WidgetSpec.Image(base("Photo of the day"), image = HILLS, alt = "Hills at sunrise", caption = "Sunrise over the ghats"),
            inView = true,
        ),
    )

    /** The ready-made widgets of the mockup: the library scripts' cards, and Kolahal's own. */
    private fun readyMade(): List<Shot> {
        val weather = WidgetSpec.Stat(
            base("Pune", "☀️"), value = "27°", label = "Sunny",
            more = listOf(WidgetSpec.Small("62%", "humidity"), WidgetSpec.Small("9 km/h", "wind")),
        )
        val aqi = WidgetSpec.Progress(base("US AQI · Pune", "A", Colour.WARN), value = 118f, max = 500f, text = "118", label = "Unhealthy for some")
        val goa = WidgetSpec.Countdown(base("Goa trip", "📅", Colour.LINK), to = now + 36 * 86_400 + 43_200, label = "20 Dec", style = "days", reached = "Today")
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
            AgendaEvent(1, "Team call", at(0, 10, 0) * 1000, at(0, 10, 30) * 1000, allDay = false),
            AgendaEvent(2, "Dentist", at(0, 15, 30) * 1000, at(0, 16, 15) * 1000, allDay = false),
            // The calendar keeps an all-day event at midnight UTC.
            AgendaEvent(3, "Mum's birthday", today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), today.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), allDay = true),
        )
        // As read at the start of the day. Without the ends, no event drops out or says "now" at the hour of the run.
        val agenda = OwnCards.agenda(calendar, zone, today.atStartOfDay(zone).toInstant().toEpochMilli(), OwnData.AGENDA_DAYS).let { a -> a.copy(events = a.events.map { it.copy(ends = null) }) }
        val reminders = listOf(Reminder(1, nowMs + 42 * 60_000, "Call the bank"), Reminder(2, at(1, 7, 0) * 1000, "Water plants"), Reminder(3, at(3, 9, 0) * 1000, "Pay rent"))
        val shortcuts = listOf(Shortcut("torch", "torch", "🔦"), Shortcut("dnd", "dnd", "🌙"), Shortcut("todo", "todo", "✅"), Shortcut("maps", "maps", "📍"))
        return listOf(
            Shot("status", WidgetSize.S4X1, "Live cricket", checkNotNull(StatusCards.spec("Live cricket", StatusLine("cricket", "NZ 136/8 · 31.2 overs", progress = 0.6f)))),
            Shot("weather", WidgetSize.S2X1, "Pune", weather),
            Shot("weather", WidgetSize.S1X1, "Pune", weather),
            Shot("aqi", WidgetSize.S2X2, "US AQI · Pune", aqi),
            Shot("aqi", WidgetSize.S1X1, "US AQI · Pune", aqi),
            Shot(
                "forecast", WidgetSize.S2X2, "Pune",
                WidgetSpec.Rows(
                    base("Pune", "3", Colour.INFO),
                    listOf(
                        WidgetSpec.Row("Sat", trailing = "22–31°", icon = "☀️"),
                        WidgetSpec.Row("Sun", trailing = "21–30°", icon = "☁️"),
                        WidgetSpec.Row("Mon", trailing = "20–27°", icon = "🌧️"),
                    ),
                ),
            ),
            // The larger card has a start, for its bar: 55% of the wait is over.
            Shot("countdown-date", WidgetSize.S2X2, "Goa trip", goa.copy(from = now - 44 * 86_400)),
            Shot("countdown-date", WidgetSize.S2X1, "Goa trip", goa),
            Shot("countdown-date", WidgetSize.S1X1, "Goa trip", goa),
            Shot(
                "hn", WidgetSize.S4X2, "Hacker News",
                WidgetSpec.Rows(base("Hacker News", "Y", Colour.WARN), headlines.mapIndexed { i, (title, points) -> WidgetSpec.Row(title, trailing = points, tap = link(i)) }),
            ),
            Shot("feed", WidgetSize.S2X2, "My feed", WidgetSpec.Rows(base("My feed", "F", Colour.LINK), feed.mapIndexed { i, title -> WidgetSpec.Row(title, tap = link(i)) })),
            Shot(
                "quote", WidgetSize.S2X2, "Quote of the day",
                WidgetSpec.Text(base("Quote of the day", "“"), "“Simplicity is prerequisite for reliability.”", "Edsger W. Dijkstra"),
            ),
            Shot("battery", WidgetSize.S2X1, BatteryCards.title(battery), BatteryCards.spec(battery)),
            Shot("battery", WidgetSize.S1X1, BatteryCards.title(battery), BatteryCards.spec(battery)),
            Shot(
                "unlocks", WidgetSize.S2X2, "Unlocks",
                WidgetSpec.Chart(base("Unlocks", "🔒", Colour.INFO), week, style = "bars", min = 0f, max = 71f, value = "46", label = "unlocks today", labels = ago),
            ),
            Shot(
                "webnumber", WidgetSize.S2X1, "Living room",
                WidgetSpec.Stat(base("Living room", "#", Colour.INFO), value = "21.4", unit = "°C", label = "sensor at home"),
            ),
            Shot("webnumber", WidgetSize.S1X1, "Stars", WidgetSpec.Stat(base("Stars", "#", Colour.INFO), value = "847", label = "stars")),
            Shot(
                "crypto", WidgetSize.S4X2, "Crypto",
                WidgetSpec.Rows(
                    base("Crypto", "C"),
                    listOf(
                        WidgetSpec.Row("Bitcoin", "+1.8% today", "$97,420", color = Colour.GOOD),
                        WidgetSpec.Row("Ethereum", "−0.6% today", "$3,310", color = Colour.BAD),
                        WidgetSpec.Row("Solana", "+3.1% today", "$212", color = Colour.GOOD),
                    ),
                ),
            ),
            Shot("shortcuts", WidgetSize.S4X1, "Shortcuts", checkNotNull(OwnCards.shortcuts(shortcuts))),
            Shot("nowplaying", WidgetSize.S4X2, "Now playing", media),
            Shot("todos", WidgetSize.S2X2, "Todos", OwnCards.todos(todos.mapIndexed { i, text -> Todo(i + 1L, text, done = false) })),
            Shot("agenda", WidgetSize.S4X2, "Agenda", agenda),
            Shot("reminders", WidgetSize.S2X2, "Reminders", OwnCards.reminders(reminders, nowMs, zone, clock24 = true)),
            Shot("notes", WidgetSize.S2X1, "Notes", checkNotNull(OwnCards.notes(listOf(Note(1, "Parcel comes Tuesday. The gate code is with the guard.", nowMs))))),
            Shot(
                "worldclocks", WidgetSize.S2X2, "World clocks",
                WidgetSpec.Clock(
                    base("World clocks", "W", Colour.LINK),
                    listOf(WidgetSpec.Zone("Asia/Kolkata", "Kolkata"), WidgetSpec.Zone("Europe/London", "London"), WidgetSpec.Zone("America/New_York", "New York")),
                ),
            ),
            Shot(
                "habit", WidgetSize.S2X2, "Exercise",
                WidgetSpec.Chart(base("Exercise", "✅", Colour.GOOD), listOf(1f, 0f, 1f, 1f, 1f, 1f, 1f), style = "bars", min = 0f, max = 1f, value = "5", label = "days in a row", labels = ago),
            ),
            Shot(
                "quicktoggles", WidgetSize.S4X1, "Quick toggles",
                WidgetSpec.Toggles(
                    base("Quick toggles"),
                    listOf(
                        WidgetSpec.Toggle("Do Not Disturb", on = true, tap = WidgetTap(args = "dnd"), icon = "🌙"),
                        WidgetSpec.Toggle("Work profile", on = false, tap = WidgetTap(args = "work"), icon = "💼"),
                    ),
                ),
            ),
            Shot(
                "currency", WidgetSize.S2X2, "1 USD",
                WidgetSpec.Rows(base("1 USD", "$", Colour.INFO), listOf("INR" to "84.40", "EUR" to "0.92", "GBP" to "0.79").map { (to, rate) -> WidgetSpec.Row(to, trailing = rate) }),
            ),
            Shot(
                "releases", WidgetSize.S4X2, "Releases",
                WidgetSpec.Rows(
                    base("Releases", "R", Colour.LINK),
                    listOf(Triple("neovim/neovim", "3 days ago", "v0.11.4"), Triple("junegunn/fzf", "1 week ago", "v0.65"), Triple("lua/lua", "2 weeks ago", "v5.4.8"))
                        .mapIndexed { i, (repo, age, tag) -> WidgetSpec.Row(repo, age, tag, tap = link(i)) },
                ),
            ),
            Shot(
                "livescore", WidgetSize.S4X1, "NZ v IND · live",
                WidgetSpec.Stat(base("NZ v IND · live", "🏏", Colour.BAD), value = "NZ 136/8", label = "31.2 of 50 overs", progress = 0.62f, moving = true),
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
