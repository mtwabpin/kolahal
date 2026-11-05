package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.apps.Profile
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.status.Status
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What a tap on a header item does. */
enum class HeaderTarget { CLOCK, UNLOCKS, BATTERY, NETWORK, ALARM, SETTINGS, TODO }

/**
 * The clock home shows, kept once for all of it: the time a minute at a time as the system ticks it
 * (on waking too, where a delay that slept with the phone wouldn't), today's date, and a count of
 * time-zone changes, all new at once when the clock or the time zone is set. One set of receivers
 * while something on screen reads it ([Hold]), for the header, the pinned header and the log's day
 * dividers together, rather than one each. MainActivity holds it while started, so it is current
 * whenever home is built.
 */
internal object HomeClock {
    private var nowState by androidx.compose.runtime.mutableStateOf(Date())
    private var todayState by androidx.compose.runtime.mutableStateOf(java.time.LocalDate.now())
    private var zoneState by androidx.compose.runtime.mutableIntStateOf(0)
    private var users = 0
    private var receiver: android.content.BroadcastReceiver? = null
    /** The zone last seen: one changed while nothing listened (home stopped) counts on return. */
    private var lastZone = java.util.TimeZone.getDefault().id

    // With nothing holding it, its time may be old: what's read then is the time now.
    val now: Date get() = nowState.let { if (users == 0) Date() else it }
    val today: java.time.LocalDate get() = todayState.let { if (users == 0) java.time.LocalDate.now() else it }
    val zone: Int get() = zoneState

    /** Main thread. The time now, whether or not a tick came while away. */
    private fun refresh() {
        nowState = Date()
        todayState = java.time.LocalDate.now()
    }

    /** Main thread. The first holder registers for the system's ticks. */
    fun acquire(context: android.content.Context) {
        if (users++ > 0) return
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, intent: android.content.Intent) {
                if (intent.action == android.content.Intent.ACTION_TIMEZONE_CHANGED) zoneChanged()
                refresh()
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_TIME_TICK)
            addAction(android.content.Intent.ACTION_TIME_CHANGED)
            addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
        }
        androidx.core.content.ContextCompat.registerReceiver(context.applicationContext, r, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = r
        if (java.util.TimeZone.getDefault().id != lastZone) zoneChanged()
        refresh()
    }

    private fun zoneChanged() {
        lastZone = java.util.TimeZone.getDefault().id
        zoneState++
    }

    /** Main thread. The last holder unregisters. */
    fun release(context: android.content.Context) {
        if (users == 0 || --users > 0) return
        receiver?.let { runCatching { context.applicationContext.unregisterReceiver(it) } }
        receiver = null
    }

    /**
     * Holds the clock while the screen reading it is started. The first holder makes it current
     * ([acquire]); one coming while it's held finds it current, as ticks keep it, so it doesn't set
     * the time again and redraw what already reads it.
     */
    @Composable
    fun Hold() {
        val context = androidx.compose.ui.platform.LocalContext.current
        androidx.lifecycle.compose.LifecycleStartEffect(context) {
            acquire(context)
            onStopOrDispose { release(context) }
        }
    }
}

/** Counts the phone's time-zone changes, for formatters to be made again in the new zone. */
@Composable
internal fun rememberZoneChanges(): Int {
    HomeClock.Hold()
    return HomeClock.zone
}

/**
 * Today's date, new at midnight: checked at each minute's tick, when the clock or the time zone is
 * set, and back on screen. Only a new day changes it, so what reads it is drawn again only then.
 */
@Composable
internal fun rememberToday(): java.time.LocalDate {
    HomeClock.Hold()
    return HomeClock.today
}

/**
 * The time, a minute at a time ([HomeClock]). The second value counts time-zone changes
 * ([rememberZoneChanges]).
 */
@Composable
internal fun rememberNow(): Pair<Date, Int> {
    HomeClock.Hold()
    return HomeClock.now to HomeClock.zone
}

/** Height of the header with no band to fill: a line of text with room to tap it. */
private val HEADER_MIN = 32.dp

/** Room kept clear on each side of the camera's cutout. */
private val CUTOUT_GAP = 4.dp

/** Space between the header's parts. */
private val HEADER_SPACING = 4.dp

/**
 * Where the header's parts go along its line, in pixels, apart from Compose so it can be tested.
 * The line runs [pad] in from each end; a camera cutout across it ([cutouts], x ranges) splits it.
 */
internal object HeaderLayout {
    /** The stretches of the line free for text, left to right: each cutout kept [gap] clear. */
    fun zones(width: Int, pad: Int, cutouts: List<IntRange>, gap: Int): List<IntRange> {
        var zones = listOf(pad..(width - pad - 1))
        for (c in cutouts) {
            zones = zones.flatMap { z ->
                val from = c.first - gap
                val to = c.last + gap
                if (to < z.first || from > z.last) listOf(z)
                else listOf(z.first..(from - 1), (to + 1)..z.last).filterNot { it.isEmpty() }
            }
        }
        return zones
    }

    /** Where each part goes: null for one left out. */
    class Places(val time: Int?, val date: Int?, val items: List<Int?>)

    /**
     * The time and date ([time], [date] wide) at the start of the first stretch with room for them,
     * the date left out if only the time fits; the items ([items], widths in the order they show)
     * against the end of the last stretch. Items that don't fit are left out from the one before
     * the last backwards, the last (the battery) last of all.
     */
    fun place(zones: List<IntRange>, time: Int, date: Int, items: List<Int>, spacing: Int): Places {
        if (zones.isEmpty()) return Places(null, null, items.map { null })
        fun room(z: IntRange) = z.last - z.first + 1
        val both = time + spacing + date
        var withDate = true
        var lz = zones.indexOfFirst { room(it) >= both }
        if (lz < 0) {
            withDate = false
            lz = zones.indexOfFirst { room(it) >= time }.takeIf { it >= 0 } ?: zones.indices.maxBy { room(zones[it]) }
        }
        val timeX = zones[lz].first
        val last = zones.lastIndex
        val start = if (lz == last) timeX + (if (withDate) both else time) + spacing else zones[last].first
        val space = zones[last].last + 1 - start
        val kept = items.indices.toMutableList()
        val dropOrder = (items.size - 2 downTo 0) + listOf(items.size - 1)
        var next = 0
        fun width() = kept.sumOf { items[it] } + spacing * (kept.size - 1).coerceAtLeast(0)
        while (kept.isNotEmpty() && width() > space) kept.remove(dropOrder[next++])
        val xs = MutableList<Int?>(items.size) { null }
        var x = zones[last].last + 1
        for (i in kept.reversed()) {
            x -= items[i]
            xs[i] = x
            x -= spacing
        }
        return Places(timeX, if (withDate) timeX + time + spacing else null, xs)
    }
}

/**
 * The header: one line at the top of home, Kolahal's own status bar. The time and date on the
 * left; on the right the battery, and before it the two items you chose (`header.item-1`,
 * `header.item-2`). In full screen it sits in the band the camera's cutout leaves across the top,
 * clear of the cutout itself, so it takes no room from the log; with the status bar shown, it sits
 * under it. What doesn't fit is left out: your second item first, then your first, the date when
 * only the time fits, the battery last. A tap on a part does what it says; swiping down on the line
 * pulls the shade.
 */
@Composable
internal fun Header(
    status: Status,
    unlocks: Int,
    profiles: List<Profile>,
    todoCount: Int,
    config: Config,
    version: Int,
    onTarget: (HeaderTarget) -> Unit,
    onProfileTap: (Profile) -> Unit,
    onSwipeDown: () -> Unit,
) {
    val (now, zone) = rememberNow()
    val is24h = remember(version) { config[Keys.CLOCK_24H] }
    val timeFmt = remember(is24h, zone) { SimpleDateFormat(if (is24h) "HH:mm" else "h:mm a", Locale.getDefault()) }
    val dateFmt = remember(zone) { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    // The alarm's time without AM or PM: the next alarm is yours, and the line is short beside a camera.
    val alarmFmt = remember(is24h, zone) { SimpleDateFormat(if (is24h) "HH:mm" else "h:mm", Locale.getDefault()) }
    val dayFmt = remember(zone) { SimpleDateFormat("EEE ", Locale.getDefault()) }
    val chosen = remember(version) { listOf(config[Keys.HEADER_ITEM_1], config[Keys.HEADER_ITEM_2]) }
    val bold = Term.small.copy(color = Term.fg, fontWeight = FontWeight.Bold)
    val iconSize = (Term.fontSize + 1).dp

    // The band beside the camera: the cutout's height, with the status bar hidden.
    val density = LocalDensity.current
    val barTop = WindowInsets.statusBars.getTop(density)
    val band = (WindowInsets.displayCutout.getTop(density) - barTop).coerceAtLeast(0)
    val height = with(density) { maxOf(band.toDp(), HEADER_MIN) }
    val view = LocalView.current
    // Where the cutout is across the line; under a status bar shown, it's the bar's to avoid.
    val cutouts = remember(band, barTop, view) {
        if (barTop > 0 || band == 0) emptyList()
        else view.rootWindowInsets?.displayCutout?.boundingRects.orEmpty().filter { it.top < band }.map { it.left..it.right }
    }
    // The line's x in the window, for the cutout's zones: known once it's placed, so read in the
    // measure below as state, which measures again when it changes (the first measure has 0).
    val originX = remember { mutableFloatStateOf(0f) }

    /** One part of the line: as tall as it, with room to tap. */
    @Composable
    fun Part(tag: String, label: String, description: String? = null, onClick: () -> Unit, content: @Composable () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxHeight()
                .testTag("header-$tag")
                .clickable(onClickLabel = label, onClick = onClick)
                .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
                .padding(horizontal = 3.dp),
        ) { content() }
    }

    // The items, in the order they show, each a composable or nothing (no alarm set, no todos).
    val items: List<@Composable () -> Unit> = buildList {
        for (kind in chosen) when (kind) {
            "alarm" -> status.nextAlarm?.let { at ->
                // A day ahead or more, with its day.
                val text = (if (at - now.time >= 24 * 3_600_000L) dayFmt.format(Date(at)) else "") + alarmFmt.format(Date(at))
                add { Part("alarm", "alarms", onClick = { onTarget(HeaderTarget.ALARM) }) { TintedIcon(R.drawable.ic_alarm, Term.dim, iconSize); Text(text, style = bold, maxLines = 1) } }
            }
            "todo" -> if (todoCount > 0) add {
                Part("todo", "todo", onClick = { onTarget(HeaderTarget.TODO) }) { TintedIcon(R.drawable.ic_check, Term.dim, iconSize); Text("$todoCount", style = bold) }
            }
            "network" -> add {
                val color = if (status.network == "offline") Term.error else Term.fg
                val res = networkIcon(status.network)
                Part("network", "network", description = "network ${status.network}", onClick = { onTarget(HeaderTarget.NETWORK) }) {
                    if (res != null) TintedIcon(res, color, iconSize) else Text(status.network, style = bold.copy(color = color))
                }
            }
            "unlocks" -> add {
                Part("unlocks", "unlocks", onClick = { onTarget(HeaderTarget.UNLOCKS) }) { TintedIcon(R.drawable.ic_lock_open, Term.dim, iconSize); Text("$unlocks", style = bold) }
            }
            "profiles" -> profiles.filter(::hasHeaderItem).forEach { p ->
                add {
                    val color = if (p.quiet) Term.dim else Term.profileColor(p.kind)
                    val res = if (p.kind == ProfileKind.WORK) R.drawable.ic_work else R.drawable.ic_lock
                    Part("profile-${p.kind.label}", p.kind.label, description = "${p.kind.label} ${if (p.quiet) "off" else "on"}", onClick = { onProfileTap(p) }) { TintedIcon(res, color, iconSize) }
                }
            }
            "settings" -> add {
                Part("settings", "settings", description = "Settings", onClick = { onTarget(HeaderTarget.SETTINGS) }) { TintedIcon(R.drawable.ic_settings, Term.dim, iconSize) }
            }
        }
        status.batteryPct?.let { pct ->
            val color = when {
                status.charging -> Term.prompt
                pct <= 15 -> Term.error
                else -> Term.fg
            }
            add { Part("battery", "battery", onClick = { onTarget(HeaderTarget.BATTERY) }) { BatteryGlyph(pct, status.charging, color, iconSize); Text("$pct%", style = bold.copy(color = color)) } }
        }
    }

    Layout(
        content = {
            Part("time", "clock", onClick = { onTarget(HeaderTarget.CLOCK) }) { Text(timeFmt.format(now), style = bold, maxLines = 1) }
            Part("date", "clock", onClick = { onTarget(HeaderTarget.CLOCK) }) { Text(dateFmt.format(now), style = Term.small, maxLines = 1) }
            items.forEach { it() }
        },
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            .testTag("header")
            .height(height)
            .onPlaced { originX.floatValue = it.positionInWindow().x }
            // Swiping down on the line pulls the notification shade.
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > 24.dp.toPx()) onSwipeDown() },
                ) { _, dy -> total += dy }
            },
    ) { measurables, constraints ->
        val h = constraints.maxHeight
        val parts = measurables.map { it.measure(Constraints(minHeight = h, maxHeight = h)) }
        val shift = originX.floatValue.toInt()
        val zones = HeaderLayout.zones(constraints.maxWidth, 8.dp.roundToPx(), cutouts.map { (it.first - shift)..(it.last - shift) }, CUTOUT_GAP.roundToPx())
        val places = HeaderLayout.place(zones, parts[0].width, parts[1].width, parts.drop(2).map { it.width }, HEADER_SPACING.roundToPx())
        layout(constraints.maxWidth, h) {
            places.time?.let { parts[0].place(it, 0) }
            places.date?.let { parts[1].place(it, 0) }
            places.items.forEachIndexed { i, x -> x?.let { parts[i + 2].place(it, 0) } }
        }
    }
}

private fun networkIcon(network: String): Int? = when (network) {
    "wifi" -> R.drawable.ic_wifi
    "cell" -> R.drawable.ic_signal
    "offline" -> R.drawable.ic_cloud_off
    else -> null
}

/** Whether the header has an item for this profile. */
private fun hasHeaderItem(profile: Profile) = profile.kind != ProfileKind.PERSONAL && profile.kind != ProfileKind.CLONE

/**
 * A Material icon from res/drawable, tinted. Icons are drawn rather than typed: symbols like ⚙, ♪
 * or ⏭ as text come from fallback fonts and look uneven next to them.
 */
@Composable
internal fun TintedIcon(res: Int, tint: Color, size: Dp, description: String? = null) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(res),
        contentDescription = description,
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint),
        modifier = Modifier.size(size),
    )
}

/**
 * A battery drawn to scale: outline, a fill proportional to [pct], and a bolt cut out of the
 * fill while charging: a hole, so whatever the glyph sits on shows through it (home's page, a
 * menu, the wallpaper). Sized to sit on a line of header text.
 */
@Composable
internal fun BatteryGlyph(pct: Int, charging: Boolean, color: Color, height: Dp) {
    androidx.compose.foundation.Canvas(
        Modifier
            .size(width = height * 1.6f, height = height)
            // Its own layer, so the cut clears only the glyph's pixels.
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen },
    ) {
        val stroke = 1.4.dp.toPx()
        val capW = size.width * 0.08f
        val bodyW = size.width - capW
        val bodyH = size.height * 0.62f
        val top = (size.height - bodyH) / 2
        val r = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
        drawRoundRect(color, Offset(stroke / 2, top), Size(bodyW - stroke, bodyH), r, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawRect(color, Offset(bodyW, top + bodyH * 0.3f), Size(capW, bodyH * 0.4f))
        val inset = stroke * 1.8f
        val fullW = bodyW - stroke - inset * 2
        val fillW = fullW * (pct.coerceIn(0, 100) / 100f)
        if (fillW > 0f) drawRect(color, Offset(stroke / 2 + inset, top + inset), Size(fillW, bodyH - inset * 2))
        if (charging) {
            // Cut out of the fill so it reads on top of it; over the empty part, drawn in the colour.
            val cx = stroke / 2 + inset + fullW / 2
            val h = bodyH - inset * 2
            val y0 = top + inset
            val bolt = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx + h * 0.15f, y0)
                lineTo(cx - h * 0.35f, y0 + h * 0.58f)
                lineTo(cx, y0 + h * 0.58f)
                lineTo(cx - h * 0.15f, y0 + h)
                lineTo(cx + h * 0.35f, y0 + h * 0.42f)
                lineTo(cx, y0 + h * 0.42f)
                close()
            }
            if (fillW > fullW / 2) drawPath(bolt, Color.Black, blendMode = androidx.compose.ui.graphics.BlendMode.Clear) else drawPath(bolt, color)
        }
    }
}
