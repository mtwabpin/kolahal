package com.mtwabpin.kolahal.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.apps.Profile
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.status.Status
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** What a tap on a header item does. */
enum class HeaderTarget { CLOCK, UNLOCKS, BATTERY, NETWORK, ALARM, SETTINGS, TODO }

/**
 * The clock home shows, kept once for all of it: the time a minute at a time as the system ticks it
 * (on waking too, where a delay that slept with the phone wouldn't), today's date, and a count of
 * time-zone changes, all new at once when the clock or the time zone is set. One set of receivers
 * while something on screen reads it ([use]); the headers swap as the keyboard opens and the log
 * scrolls, and each used to register its own and remove them again. MainActivity holds it while
 * started, so it is current whenever home is built.
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
    fun refresh() {
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

    /** Holds the clock while the screen reading it is started, and makes it current on return. */
    @Composable
    fun use() {
        val context = androidx.compose.ui.platform.LocalContext.current
        androidx.lifecycle.compose.LifecycleStartEffect(context) {
            acquire(context)
            onStopOrDispose { release(context) }
        }
        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
            refresh()
            onPauseOrDispose { }
        }
    }
}

/** Counts the phone's time-zone changes, for formatters to be made again in the new zone. */
@Composable
internal fun rememberZoneChanges(): Int {
    HomeClock.use()
    return HomeClock.zone
}

/**
 * Today's date, new at midnight: checked at each minute's tick, when the clock or the time zone is
 * set, and back on screen. Only a new day changes it, so what reads it is drawn again only then.
 */
@Composable
internal fun rememberToday(): java.time.LocalDate {
    HomeClock.use()
    return HomeClock.today
}

/**
 * The time, a minute at a time ([HomeClock]). The second value counts time-zone changes
 * ([rememberZoneChanges]).
 */
@Composable
private fun rememberNow(): Pair<Date, Int> {
    HomeClock.use()
    return HomeClock.now to HomeClock.zone
}

/**
 * One-line header while the log is in use, with what `header.compact.*` chooses. What doesn't fit on
 * the line is left out, and with nothing to show it takes no room.
 */
@Composable
internal fun CompactHeader(
    status: Status,
    statusCount: Int,
    config: Config,
    version: Int,
    onExpand: () -> Unit,
    mediaPlaying: Boolean = false,
    unlocks: Int = 0,
    profiles: List<Profile> = emptyList(),
    todoCount: Int = 0,
    onOpenSettings: () -> Unit = {},
) {
    val (now, zone) = rememberNow()
    val is24h = remember(version) { config[Keys.CLOCK_24H] }
    val timeFmt = remember(is24h, zone) { SimpleDateFormat(if (is24h) "HH:mm" else "h:mm a", Locale.getDefault()) }
    val dateFmt = remember(zone) { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    val alarmFmt = remember(is24h, zone) { SimpleDateFormat(if (is24h) "EEE HH:mm" else "EEE h:mm a", Locale.getDefault()) }
    val small = Term.text.copy(fontSize = (Term.fontSize - 1).sp)
    val useIcons = config[Keys.HEADER_ICONS]
    val iconSize = (Term.fontSize + 2).dp

    val clock = config[Keys.COMPACT_CLOCK]
    val date = config[Keys.COMPACT_DATE]
    val device = config[Keys.COMPACT_DEVICE]
    val showUnlocks = config[Keys.COMPACT_UNLOCKS]
    val pct = status.batteryPct?.takeIf { config[Keys.COMPACT_BATTERY] }
    val network = config[Keys.COMPACT_NETWORK]
    val alarm = status.nextAlarm?.takeIf { config[Keys.COMPACT_ALARM] }
    val todos = config[Keys.COMPACT_TODO] && todoCount > 0
    val gear = config[Keys.COMPACT_SETTINGS]
    val shownProfiles = if (config[Keys.COMPACT_PROFILES]) profiles.filter(::hasHeaderItem) else emptyList()
    if (
        !clock && !date && !device && !showUnlocks && pct == null && !network && alarm == null && !todos && !mediaPlaying &&
        statusCount == 0 && !gear && shownProfiles.isEmpty()
    ) return

    /** An item: its icon (header.icons) or a text label, then its value. */
    @Composable
    fun Part(label: String, value: String, color: Color = Term.dim, icon: @Composable () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (useIcons) icon()
            val text = if (!useIcons) "$label $value" else if (value.isEmpty()) "" else " $value"
            if (text.isNotEmpty()) Text(text, style = small.copy(color = color), maxLines = 1)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("header-compact")
            .clickable(onClick = onExpand)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > 24.dp.toPx()) onExpand() },
                ) { _, dy -> total += dy }
            }
            .heightIn(min = HEADER_ROW)
            .padding(horizontal = 12.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        ) {
            if (clock) Text(timeFmt.format(now), style = small.copy(color = Term.fg, fontWeight = FontWeight.Bold))
            if (date) Text(dateFmt.format(now), style = small.copy(color = Term.dim), maxLines = 1)
            if (showUnlocks) Part("unlocks", unlocks.toString()) { TintedIcon(R.drawable.ic_lock_open, Term.dim, iconSize) }
            pct?.let {
                val color = when {
                    status.charging -> Term.prompt
                    it <= 15 -> Term.error
                    else -> Term.dim
                }
                Part("bat", "$it%" + if (status.charging && !useIcons) "+" else "", color) { BatteryGlyph(it, status.charging, color, iconSize) }
            }
            if (network) {
                val color = if (status.network == "offline") Term.error else Term.dim
                val res = networkIcon(status.network)
                Part("net", if (useIcons && res != null) "" else status.network, color) { res?.let { TintedIcon(it, color, iconSize) } }
            }
            alarm?.let { Part("alarm", alarmFmt.format(Date(it))) { TintedIcon(R.drawable.ic_alarm, Term.dim, iconSize) } }
            if (todos) Part("todo", todoCount.toString()) { TintedIcon(R.drawable.ic_check, Term.dim, iconSize) }
            if (mediaPlaying) {
                if (useIcons) TintedIcon(R.drawable.ic_music_note, Term.prompt, iconSize) else Text("♪", style = small.copy(color = Term.prompt))
            }
            if (statusCount > 0) Text("· $statusCount status", style = small.copy(color = Term.dim), maxLines = 1)
            shownProfiles.forEach { p ->
                val color = if (p.quiet) Term.dim else Term.profileColor(p.kind)
                val res = if (p.kind == ProfileKind.WORK) R.drawable.ic_work else R.drawable.ic_lock
                Part(p.kind.label, if (p.quiet) "off" else "on", color) { TintedIcon(res, color, iconSize) }
            }
            if (device) DeviceName(config, small)
            // Its own tap: Settings, where the rest of the line expands the header.
            if (gear) Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.clickable(onClick = onOpenSettings).heightIn(min = HEADER_ROW).widthIn(min = 44.dp),
            ) {
                if (useIcons) TintedIcon(R.drawable.ic_settings, Term.dim, iconSize, "Settings") else Text("⚙", style = small.copy(color = Term.dim))
            }
        }
        TintedIcon(R.drawable.ic_expand_more, Term.dim, (Term.fontSize + 6).dp)
    }
}

/** `header.media-always`: with the header off, its now playing line where the header would be, under the same rule. */
@Composable
internal fun NowPlayingBar(nowPlaying: com.mtwabpin.kolahal.control.NowPlaying, config: Config, version: Int, onToggle: () -> Unit, onNext: () -> Unit) {
    // `now` ticks each minute, so a paused session drops out after 30 minutes.
    val (now, _) = rememberNow()
    val always = remember(version) { config[Keys.HEADER_MEDIA_ALWAYS] }
    if (always && nowPlaying.showInHeader(now.time)) Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        NowPlayingLine(nowPlaying, onToggle, onNext)
    }
}

/** name@device. */
@Composable
private fun DeviceName(config: Config, style: TextStyle, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Term.prompt, fontWeight = FontWeight.Bold)) { append(config[Keys.USER_NAME]) }
            withStyle(SpanStyle(color = Term.dim)) { append("@" + Build.MODEL.lowercase().replace(' ', '-')) }
        },
        style = style,
        maxLines = 1,
        modifier = modifier,
    )
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

/** Height of a header row: a fair touch target that keeps the header tight. */
private val HEADER_ROW = 40.dp

/**
 * Clock, date and the status items in one flowing line that wraps as whole items. Items are tappable
 * (alarms, unlock history, battery, network, profile toggle); swipe down opens notifications.
 */
@Composable
internal fun Header(
    unlocks: Int,
    profiles: List<Profile>,
    status: Status,
    statuses: Map<String, String>,
    config: Config,
    version: Int,
    onStatusTap: (String) -> Unit,
    onSwipeDown: () -> Unit,
    onTarget: (HeaderTarget) -> Unit,
    onProfileTap: (Profile) -> Unit,
    nowPlaying: com.mtwabpin.kolahal.control.NowPlaying? = null,
    onMediaToggle: () -> Unit = {},
    onMediaNext: () -> Unit = {},
    todoCount: Int = 0,
    /** Long-press on a script's line: its menu (run, hide from header, edit). */
    onStatusLongPress: (String) -> Unit = {},
) {
    val (now, zone) = rememberNow()
    val is24h = remember(version) { config[Keys.CLOCK_24H] }
    val timeFmt = remember(is24h, zone) { SimpleDateFormat(if (is24h) "HH:mm" else "h:mm a", Locale.getDefault()) }
    val dateFmt = remember(zone) { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    val alarmFmt = remember(is24h, zone) { SimpleDateFormat(if (is24h) "EEE HH:mm" else "EEE h:mm a", Locale.getDefault()) }
    val small = Term.text.copy(fontSize = (Term.fontSize - 1).sp)

    val useIcons = config[Keys.HEADER_ICONS]
    val iconSize = (Term.fontSize + 3).dp

    /** A header item: an icon (header.icons) or a dim text label, then the value. */
    @Composable
    fun Item(
        label: String,
        value: String,
        valueColor: Color = Term.fg,
        icon: (@Composable () -> Unit)? = null,
        /** What a screen reader says for an item its icon alone shows (⚙, the network kind). */
        description: String? = null,
        onClick: () -> Unit,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                // Comfortable touch target even for short items like ⚙, without spreading the header out.
                .clickable(onClickLabel = label.ifEmpty { null }, onClick = onClick)
                .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
                .heightIn(min = HEADER_ROW)
                .widthIn(min = 44.dp)
                .padding(horizontal = 6.dp),
        ) {
            if (useIcons && icon != null) {
                icon()
                if (value.isNotEmpty()) Spacer(Modifier.width(4.dp))
            }
            Text(
                buildAnnotatedString {
                    if (label.isNotEmpty() && !(useIcons && icon != null)) withStyle(SpanStyle(color = Term.dim)) { append("$label ") }
                    withStyle(SpanStyle(color = valueColor, fontWeight = FontWeight.Bold)) { append(value) }
                },
                style = small,
            )
        }
    }

    @Composable
    fun Glyph(res: Int, tint: Color = Term.dim) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(res),
            contentDescription = null,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint),
            modifier = Modifier.size(iconSize),
        )
    }

    val clock = config[Keys.HEADER_CLOCK]
    val date = config[Keys.HEADER_DATE]
    val pct = status.batteryPct
    val shownProfiles = if (config[Keys.HEADER_PROFILES]) profiles.filter(::hasHeaderItem) else emptyList()
    val playing = nowPlaying != null && nowPlaying.showInHeader(now.time)
    // With nothing to show, it takes no room.
    if (
        !clock && !date && !config[Keys.HEADER_DEVICE] && !config[Keys.HEADER_UNLOCKS] && !(config[Keys.HEADER_BATTERY] && pct != null) &&
        !config[Keys.HEADER_NETWORK] && !(config[Keys.HEADER_ALARM] && status.nextAlarm != null) && !(config[Keys.HEADER_TODO] && todoCount > 0) &&
        !config[Keys.HEADER_SETTINGS] && shownProfiles.isEmpty() && !playing && !(config[Keys.HEADER_STATUS] && statuses.isNotEmpty())
    ) return

    Column(
        Modifier
            .fillMaxWidth()
            .testTag("header")
            // Swipe down on the header pulls down the notification shade.
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > 48.dp.toPx()) onSwipeDown() },
                ) { _, dy -> total += dy }
            }
            .padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        // One flowing line: clock and date, then the status items beside them. Items wrap as whole
        // items onto a second line only when they don't fit.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
        if (clock || date) Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onTarget(HeaderTarget.CLOCK) }.heightIn(min = HEADER_ROW).padding(end = 6.dp),
        ) {
            if (clock) {
                Text(
                    timeFmt.format(now),
                    style = Term.text.copy(color = Term.fg, fontWeight = FontWeight.Bold, fontSize = (Term.fontSize + 6).sp),
                )
            }
            if (date) {
                Text(
                    (if (clock) "  " else "") + dateFmt.format(now),
                    style = small.copy(color = Term.dim),
                )
            }
        }
            if (config[Keys.HEADER_UNLOCKS]) {
                Item("unlocks", unlocks.toString(), icon = { Glyph(R.drawable.ic_lock_open) }) { onTarget(HeaderTarget.UNLOCKS) }
            }
            if (config[Keys.HEADER_BATTERY] && pct != null) {
                val color = when {
                    status.charging -> Term.prompt
                    pct <= 15 -> Term.error
                    else -> Term.fg
                }
                Item(
                    "bat", "$pct%" + if (status.charging && !useIcons) "+" else "", color,
                    icon = { BatteryGlyph(pct, status.charging, color, iconSize) },
                ) { onTarget(HeaderTarget.BATTERY) }
            }
            if (config[Keys.HEADER_NETWORK]) {
                val offline = status.network == "offline"
                val netIcon = networkIcon(status.network)
                // With an icon the kind is obvious; only unusual kinds (eth, vpn…) keep their name.
                Item(
                    "net", if (useIcons && netIcon != null) "" else status.network, if (offline) Term.error else Term.fg,
                    icon = netIcon?.let { res -> { Glyph(res, if (offline) Term.error else Term.fg) } },
                    description = "network ${status.network}",
                ) { onTarget(HeaderTarget.NETWORK) }
            }
            if (config[Keys.HEADER_ALARM] && status.nextAlarm != null) {
                Item("alarm", alarmFmt.format(Date(status.nextAlarm)), icon = { Glyph(R.drawable.ic_alarm) }) { onTarget(HeaderTarget.ALARM) }
            }
            if (config[Keys.HEADER_TODO] && todoCount > 0) {
                Item("todo", todoCount.toString(), icon = { Glyph(R.drawable.ic_check) }) { onTarget(HeaderTarget.TODO) }
            }
            if (config[Keys.HEADER_SETTINGS]) {
                Item("", if (useIcons) "" else "⚙", Term.dim, icon = { Glyph(R.drawable.ic_settings) }, description = "Settings") { onTarget(HeaderTarget.SETTINGS) }
            }
            shownProfiles.forEach { p ->
                val color = if (p.quiet) Term.dim else Term.profileColor(p.kind)
                val res = if (p.kind == ProfileKind.WORK) R.drawable.ic_work else R.drawable.ic_lock
                Item(p.kind.label, if (p.quiet) "off" else "on", color, icon = { Glyph(res, color) }) {
                    onProfileTap(p)
                }
            }
            if (config[Keys.HEADER_DEVICE]) DeviceName(config, small, Modifier.padding(horizontal = 6.dp))
        }
        // `now` ticks each minute, so a paused session drops out after 30 minutes.
        if (playing) NowPlayingLine(nowPlaying, onMediaToggle, onMediaNext)
        if (config[Keys.HEADER_STATUS]) {
            statuses.forEach { (name, text) ->
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Term.dim)) { append("$name ▸ ") }
                        withStyle(SpanStyle(color = Term.script)) { append(text) }
                    },
                    style = small,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // As tall as a log line (log.line-height), so a few status lines stay compact.
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(onLongClick = { onStatusLongPress(name) }) { onStatusTap(name) }
                        .heightIn(min = with(androidx.compose.ui.platform.LocalDensity.current) { Term.lineHeight.sp.toDp() })
                        .wrapContentHeight(),
                )
            }
        }
    }
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
