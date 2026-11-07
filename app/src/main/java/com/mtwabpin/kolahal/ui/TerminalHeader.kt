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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
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
import com.mtwabpin.kolahal.data.headerSide
import com.mtwabpin.kolahal.status.Status
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What a tap on a header item does. */
enum class HeaderTarget { CLOCK, UNLOCKS, BATTERY, NETWORK, ALARM, SETTINGS, TODO }

/**
 * The clock that home shows, kept once for the whole app. It holds:
 * - the time, updated each minute when the system ticks (also right after the phone wakes; a
 *   delay that slept with the phone would be late);
 * - today's date;
 * - a count of time-zone changes.
 * All three update together when the user sets the clock or the time zone. Only one set of
 * receivers runs while something on screen reads the clock ([Hold]). The header, the pinned header
 * and the log's day dividers share it. MainActivity holds it while started, so it is current
 * whenever home is built.
 */
internal object HomeClock {
    private var nowState by androidx.compose.runtime.mutableStateOf(Date())
    private var todayState by androidx.compose.runtime.mutableStateOf(java.time.LocalDate.now())
    private var zoneState by androidx.compose.runtime.mutableIntStateOf(0)
    private var users = 0
    private var receiver: android.content.BroadcastReceiver? = null
    /** The last time zone seen. If the zone changed while nothing listened (home stopped), it counts when home returns. */
    private var lastZone = java.util.TimeZone.getDefault().id

    // When nothing holds the clock, the stored time may be old, so these return the current time.
    val now: Date get() = nowState.let { if (users == 0) Date() else it }
    val today: java.time.LocalDate get() = todayState.let { if (users == 0) java.time.LocalDate.now() else it }
    val zone: Int get() = zoneState

    /** Main thread. Sets the current time, even if no tick came while home was away. */
    private fun refresh() {
        nowState = Date()
        todayState = java.time.LocalDate.now()
    }

    /** Main thread. The first holder registers for the system's time ticks. */
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
     * Holds the clock while the screen that reads it is started.
     * - The first holder makes the clock current ([acquire]).
     * - A later holder finds it already current, because ticks keep it up to date. So it does not
     *   set the time again and redraw what already reads it.
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

/** Counts the phone's time-zone changes, so formatters can be made again for the new zone. */
@Composable
internal fun rememberZoneChanges(): Int {
    HomeClock.Hold()
    return HomeClock.zone
}

/**
 * Today's date. It changes at midnight. It is checked at each minute's tick, when the user sets the
 * clock or the time zone, and when home comes back on screen. Only a new day changes the value, so
 * readers redraw only then.
 */
@Composable
internal fun rememberToday(): java.time.LocalDate {
    HomeClock.Hold()
    return HomeClock.today
}

/**
 * The time, updated once a minute ([HomeClock]). The second value counts time-zone changes
 * ([rememberZoneChanges]).
 */
@Composable
internal fun rememberNow(): Pair<Date, Int> {
    HomeClock.Hold()
    return HomeClock.now to HomeClock.zone
}

/** Room kept clear on each side of the camera's cutout. */
private val CUTOUT_GAP = 4.dp

/** Space between the header's parts. */
private val HEADER_SPACING = 4.dp

/**
 * Computes where the header's parts go along its line, in pixels. It is separate from Compose so
 * it can be tested. The line starts [pad] in from each end. A camera cutout across the line
 * ([cutouts], as x ranges) splits it.
 */
internal object HeaderLayout {
    /** The parts of the line that are free for text, left to right. Each cutout keeps [gap] clear on both sides. */
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

    /** The x of each part on each side, in order. A part that is left out has null. */
    class Places(val left: List<Int?>, val right: List<Int?>)

    /**
     * Places both sides of the line:
     * - The left side's parts ([left], widths in order from the left) start at the first free stretch
     *   with room for them.
     * - The right side's parts ([right]) end at the end of the last stretch.
     * Parts that don't fit are dropped from the middle outward: the left side loses its last part
     * first, the right side its first part. So the outermost parts stay longest. The left side gets
     * room first, and its first part always shows.
     */
    fun place(zones: List<IntRange>, left: List<Int>, right: List<Int>, spacing: Int): Places {
        if (zones.isEmpty()) return Places(left.map { null }, right.map { null })
        fun room(z: IntRange) = z.last - z.first + 1
        fun width(ws: List<Int>) = ws.sum() + spacing * (ws.size - 1).coerceAtLeast(0)
        var keep = left.size
        var lz = -1
        while (keep > 0) {
            lz = zones.indexOfFirst { room(it) >= width(left.take(keep)) }
            if (lz >= 0) break
            keep--
        }
        // Not even the first part fits anywhere. Show it in the widest stretch anyway.
        if (keep == 0 && left.isNotEmpty()) {
            keep = 1
            lz = zones.indices.maxBy { room(zones[it]) }
        }
        val leftXs = MutableList<Int?>(left.size) { null }
        var x = if (keep > 0) zones[lz].first else 0
        for (i in 0 until keep) {
            leftXs[i] = x
            x += left[i] + spacing
        }
        val last = zones.lastIndex
        val start = if (keep > 0 && lz == last) x else zones[last].first
        val space = zones[last].last + 1 - start
        var from = 0
        while (from < right.size && width(right.drop(from)) > space) from++
        val rightXs = MutableList<Int?>(right.size) { null }
        x = zones[last].last + 1
        for (i in right.indices.reversed()) {
            if (i < from) break
            x -= right[i]
            rightXs[i] = x
            x -= spacing
        }
        return Places(leftXs, rightXs)
    }
}

/**
 * The header: one line at the top of home, Kolahal's own status bar. It shows the parts the user
 * chose for each side, in their order:
 * - `header.left`: by default the time and date;
 * - `header.right`: by default the alarm, todos and battery.
 * Placement:
 * - In full screen it sits in the band beside the camera cutout at the top, clear of the cutout,
 *   so it takes no room from the log.
 * - When the status bar is shown, it sits under the status bar.
 * - With `header.below-bar` it sits under the status bar's space and the camera, even in full screen.
 * Parts that don't fit are dropped from the middle outward (HeaderLayout.place), so the outermost
 * parts stay longest. Tapping a part opens what it shows. Swiping down on the line pulls down the
 * notification shade. When [preview] is true, Settings draws it (HeaderPreview): it has no status
 * bar or camera to avoid and no swipe.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    preview: Boolean = false,
) {
    val (now, zone) = rememberNow()
    val is24h = remember(version) { config[Keys.CLOCK_24H] }
    val timeFmt = remember(is24h, zone) { SimpleDateFormat(if (is24h) "HH:mm" else "h:mm a", Locale.getDefault()) }
    val dateFmt = remember(zone) { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    // The alarm time has no AM or PM: the user knows their own next alarm, and the line beside a camera is short.
    val alarmFmt = remember(is24h, zone) { SimpleDateFormat(if (is24h) "HH:mm" else "h:mm", Locale.getDefault()) }
    val dayFmt = remember(zone) { SimpleDateFormat("EEE ", Locale.getDefault()) }
    // A part listed on both sides shows only on the left. Only `config` can list it on both.
    val leftKinds = remember(version) { headerSide(config[Keys.HEADER_LEFT]) }
    val rightKinds = remember(version) { headerSide(config[Keys.HEADER_RIGHT]).filter { it !in leftKinds } }
    val belowBar = remember(version) { config[Keys.HEADER_BELOW_BAR] }
    val textPadding = remember(version) { config[Keys.HEADER_PADDING].dp }
    val bold = Term.small.copy(color = Term.fg, fontWeight = FontWeight.Bold)
    val iconSize = (Term.fontSize + 1).dp

    // The band beside the camera: the cutout's height when the status bar is hidden.
    val density = LocalDensity.current
    val barTop = WindowInsets.statusBars.getTop(density)
    val band = if (preview || belowBar) 0 else (WindowInsets.displayCutout.getTop(density) - barTop).coerceAtLeast(0)
    // Below the bar: keep the status bar's space (shown or hidden) and the camera's space clear above the line.
    val topInsets = if (belowBar) WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout) else WindowInsets.statusBars
    // One line of text (or an icon, if taller) with `header.padding` above and below it; beside the
    // camera, at least the band's height.
    val height = with(density) { maxOf(band.toDp(), maxOf(Term.small.lineHeight.toDp(), iconSize) + textPadding * 2) }
    val view = LocalView.current
    // The cutout's x ranges across the line. When the status bar is shown, the bar avoids the cutout, so there are none.
    val cutouts = remember(band, barTop, view) {
        if (barTop > 0 || band == 0) emptyList()
        else view.rootWindowInsets?.displayCutout?.boundingRects.orEmpty().filter { it.top < band }.map { it.left..it.right }
    }
    // The line's x in the window, used to shift the cutout zones. It is known only after placement,
    // so the measure below reads it as state and measures again when it changes. The first measure sees 0.
    val originX = remember { mutableFloatStateOf(0f) }

    /** One part of the line: as tall as the line, with room to tap. */
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

    // A side's parts in display order. A part with nothing to show (no alarm set, no todos) is left out.
    fun side(kinds: List<String>): List<@Composable () -> Unit> = buildList {
        for (kind in kinds) when (kind) {
            "time" -> add { Part("time", "clock", onClick = { onTarget(HeaderTarget.CLOCK) }) { Text(timeFmt.format(now), style = bold, maxLines = 1) } }
            "date" -> add { Part("date", "clock", onClick = { onTarget(HeaderTarget.CLOCK) }) { Text(dateFmt.format(now), style = Term.small, maxLines = 1) } }
            "alarm" -> status.nextAlarm?.let { at ->
                // If the alarm is a day or more ahead, show its weekday too.
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
            "battery" -> status.batteryPct?.let { pct ->
                val color = when {
                    status.charging -> Term.prompt
                    pct <= 15 -> Term.error
                    else -> Term.fg
                }
                add { Part("battery", "battery", onClick = { onTarget(HeaderTarget.BATTERY) }) { BatteryGlyph(pct, status.charging, color, iconSize); Text("$pct%", style = bold.copy(color = color)) } }
            }
        }
    }
    val left = side(leftKinds)
    val right = side(rightKinds)

    Layout(
        content = {
            left.forEach { it() }
            right.forEach { it() }
        },
        modifier = Modifier
            .fillMaxWidth()
            .then(if (preview) Modifier else Modifier.windowInsetsPadding(topInsets.only(WindowInsetsSides.Top)))
            .testTag(if (preview) "header-preview-line" else "header")
            .height(height)
            .onPlaced { originX.floatValue = it.positionInWindow().x }
            // Swiping down on the line pulls the notification shade.
            .pointerInput(preview) {
                if (preview) return@pointerInput
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
        val n = left.size
        val places = HeaderLayout.place(zones, parts.take(n).map { it.width }, parts.drop(n).map { it.width }, HEADER_SPACING.roundToPx())
        layout(constraints.maxWidth, h) {
            places.left.forEachIndexed { i, x -> x?.let { parts[i].place(it, 0) } }
            places.right.forEachIndexed { i, x -> x?.let { parts[n + i].place(it, 0) } }
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
 * A tinted Material icon from res/drawable. We draw icons instead of typing symbols: symbols like
 * ⚙, ♪ or ⏭ as text come from fallback fonts and look uneven next to each other.
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
 * A battery drawn to scale, sized to fit a line of header text. It has:
 * - an outline;
 * - a fill proportional to [pct];
 * - while charging, a bolt cut out of the fill as a hole, so whatever is behind the glyph shows
 *   through (home's page, a menu, the wallpaper).
 */
@Composable
internal fun BatteryGlyph(pct: Int, charging: Boolean, color: Color, height: Dp) {
    androidx.compose.foundation.Canvas(
        Modifier
            .size(width = height * 1.6f, height = height)
            // Use its own layer, so the cut-out clears only the glyph's pixels.
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
            // When the fill is past the middle, cut the bolt out of it so it stays visible. Otherwise draw the bolt in the colour.
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
