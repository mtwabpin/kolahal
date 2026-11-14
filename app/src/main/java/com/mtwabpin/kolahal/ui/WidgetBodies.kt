package com.mtwabpin.kolahal.ui

import android.os.SystemClock
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The content of the kinds with parts of their own to tap or to tick: buttons, toggles, clock,
 * agenda and media. [KindBody] calls them. The first six kinds are in WidgetKinds.kt.
 *
 * Rules for every body here:
 * - With a null `onTap`, nothing is clickable (the Widgets screen).
 * - Time comes from the header's clock ([rememberNow]). A tick every second runs only while the
 *   widgets page is in view ([LocalWidgetsShown]) and the body shows seconds.
 * - Nothing moves for ever, and nothing but drawing runs on the main thread.
 */

// ---- shared by buttons and toggles ----------------------------------------------------------

/** A button or a switch grows with its cell up to this height. A taller cell leaves room around it. */
private val MAX_CONTROL = 64.dp

/** From this width, a button has its icon and its label side by side. */
private val SIDE_BY_SIDE = 104.dp

/** From this height, a button has its icon over its label, or a label of two lines. */
private val TWO_LINES = 56.dp

/**
 * What a Material button inside a card does with a press that is held. A Material button takes the
 * press before the card does and has no long-press of its own. So a long-press on a card full of
 * buttons would do nothing.
 * - [held] is true after a press was held for the long-press time. The card's long-press has run
 *   then, and the click that follows the release must do nothing: [click] swallows it.
 */
private class CardPress {
    var held = false

    /** Runs [action] for a click, unless the click ends a long-press. */
    fun click(action: () -> Unit) {
        if (held) held = false else action()
    }
}

/**
 * Watches the presses of the button that uses [source]. A press held for the long-press time opens
 * what the card's long-press opens ([LocalCardLongPress]), as a long-press on a list's row does.
 * Nothing runs until a finger is on the button, and the wait ends when the finger lifts or leaves.
 */
@Composable
private fun cardPress(source: InteractionSource): CardPress {
    val press = remember { CardPress() }
    val longPress = LocalCardLongPress.current
    if (longPress != null) {
        val onLongPress by rememberUpdatedState(longPress)
        val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
        val haptics = LocalHapticFeedback.current
        LaunchedEffect(source, timeout) {
            // collectLatest: a release or a cancel arrives as the next interaction, and ends the wait.
            source.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        press.held = false
                        delay(timeout)
                        press.held = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                    // The press ended with no click (the finger left, or another screen opened): there is no click to swallow.
                    is PressInteraction.Cancel -> press.held = false
                    else -> {}
                }
            }
        }
    }
    return press
}

/** What a screen reader says a tap does ("double-tap to …"), from the action's `label`. The button keeps its own click. */
private fun Modifier.tapLabel(label: String?): Modifier = if (label == null) this else semantics { onClick(label = label, action = null) }

/**
 * Lays [count] buttons or switches out as a grid ([WidgetWords.grid]): fewer of them in a small
 * box, never smaller ones. [cell] draws item `index` to fill a cell `width` by `height`.
 * - The grid sits in the middle of the box.
 * - A box lower than one row (a large font makes the card's title line taller) does not press the
 *   row under 48 dp. The row keeps its height and reaches down into the card's padding.
 * - With nothing to lay out it says "Nothing to show": a built-in card can have no buttons yet.
 */
@Composable
private fun ControlGrid(count: Int, maxColumns: Int, cell: @Composable (index: Int, width: Dp, height: Dp) -> Unit) {
    if (count <= 0) {
        QuietLine(WidgetWords.NOTHING)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val grid = WidgetWords.grid(count, maxWidth.value.toInt(), maxHeight.value.toInt(), maxColumns)
        val gap = WidgetWords.CONTROL_GAP_DP.dp
        val width = (maxWidth - gap * (grid.columns - 1)) / grid.columns
        val height = ((maxHeight - gap * (grid.rows - 1)) / grid.rows).coerceIn(WidgetWords.CONTROL_DP.dp, MAX_CONTROL)
        val all = height * grid.rows + gap * (grid.rows - 1)
        // Measured with no limit on its height, and kept at the top when it is taller than the box.
        Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).height(all), verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in 0 until grid.rows) {
                Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (column in 0 until grid.columns) {
                        val index = row * grid.columns + column
                        // An empty cell keeps the last row's buttons as wide as the others.
                        Box(Modifier.weight(1f).fillMaxHeight()) { if (index < grid.shown) key(index) { cell(index, width, height) } }
                    }
                }
            }
        }
    }
}

/**
 * The inside of a button or a switch, by the room its cell has:
 * - No icon: the label, on two lines in a tall cell.
 * - A wide cell: the icon and the label side by side.
 * - A tall cell: the icon over the label.
 * - Any other cell: the icon alone. A screen reader still hears the label.
 * The label gets smaller, down to 10 sp, before it is cut with "…".
 *
 * With [working], a sign that something is going on takes the icon's place ([WorkingSign]). A
 * switch shows it while it waits for its script.
 */
@Composable
private fun ControlContent(icon: String?, label: String, width: Dp, height: Dp, working: Boolean = false) {
    val lead: (@Composable () -> Unit)? = when {
        working -> { { WorkingSign() } }
        icon != null -> { { Text(icon, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false) } }
        else -> null
    }
    when {
        lead == null -> ControlLabel(label, lines = if (height >= TWO_LINES) 2 else 1)
        width >= SIDE_BY_SIDE -> Row(verticalAlignment = Alignment.CenterVertically) {
            lead()
            Spacer(Modifier.width(6.dp))
            ControlLabel(label, lines = 1, Modifier.weight(1f, fill = false))
        }
        height >= TWO_LINES -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            lead()
            ControlLabel(label, lines = 1)
        }
        else -> Box(Modifier.semantics { contentDescription = label }) { lead() }
    }
}

/**
 * The sign of a switch that waits for its script: the loading shape, as tall as an icon.
 * - The shape changes form for as long as it is drawn. So it is drawn only while the widgets page
 *   is in view, and a switch waits for 10 seconds at most.
 * - It is no item for a screen reader: the switch says "working" itself.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WorkingSign() {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (LocalWidgetsShown.current) LoadingIndicator(Modifier.fillMaxSize().clearAndSetSemantics {}, color = LocalContentColor.current)
        else Text("…", style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

private val LABEL_SIZE = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp, stepSize = 1.sp)

@Composable
private fun ControlLabel(label: String, lines: Int, modifier: Modifier = Modifier) {
    Text(
        label,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        autoSize = LABEL_SIZE,
        maxLines = lines,
        softWrap = lines > 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

/** Less padding than a Material button has: a cell can be as narrow as 48 dp. */
private val CONTROL_PADDING = PaddingValues(horizontal = 6.dp, vertical = 2.dp)

// ---- buttons --------------------------------------------------------------------------------

/**
 * `buttons`: a grid of buttons, each with a label and an icon.
 * - 2 fit in a card of 2×1, 4 in 2×2 and in 4×1, 8 in 4×2. A button is never under 48 dp, so a
 *   smaller card shows the first ones only.
 * - A button's own colour wins over the card's. With neither, it has the theme's tonal colours.
 * - A tap hands the button's action to [onTap]. A long-press is the card's long-press.
 */
@Composable
internal fun ButtonsBody(spec: WidgetSpec.Buttons, onTap: ((WidgetTap) -> Unit)?) {
    ControlGrid(spec.items.size, maxColumns = 4) { index, width, height ->
        val button = spec.items[index]
        CardButton(button, (button.color ?: spec.base.color)?.let { colourOf(it) }, width, height, onTap)
    }
}

/**
 * One button. With a null [onTap] it takes no tap, and looks as it does on the page: the Widgets
 * screen shows the card as it is.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CardButton(button: WidgetSpec.Button, colour: Color?, width: Dp, height: Dp, onTap: ((WidgetTap) -> Unit)?) {
    val source = remember { MutableInteractionSource() }
    val press = cardPress(source)
    val tonal = ButtonDefaults.filledTonalButtonColors()
    val container = colour?.copy(alpha = 0.2f) ?: tonal.containerColor
    val content = colour ?: tonal.contentColor
    FilledTonalButton(
        onClick = { press.click { onTap?.invoke(button.tap) } },
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxSize().tapLabel(button.tap.label),
        enabled = onTap != null,
        colors = tonal.copy(containerColor = container, contentColor = content, disabledContainerColor = container, disabledContentColor = content),
        contentPadding = CONTROL_PADDING,
        interactionSource = source,
    ) {
        ControlContent(button.icon, button.label, width, height)
    }
}

// ---- toggles --------------------------------------------------------------------------------

/** The switch that waits for its script: which one, and the card's stamp when it was tapped. */
private data class Waiting(val index: Int, val stamp: Long)

/**
 * `toggles`: switches, each with a label, an icon and a state. Kolahal never guesses the new
 * state: the script changes the thing and reports it.
 * 1. A tap hands the switch's action to [onTap], and the switch shows "working". Its state stays
 *    as the script last reported it.
 * 2. The script runs and shows the new state. The card's [stamp] moves when the run ends, whether
 *    it showed something or failed, and the switch stops waiting. [stamp] is watched and not the
 *    description, because a run that reports the same state stores an equal description.
 * 3. With no answer in 10 seconds ([WidgetWords.WORKING_MS]) the switch stops waiting too. It
 *    shows the state it had, and a failed run is in the card's footer.
 *
 * - A switch that waits takes no second tap. And a tap that comes within 2 seconds of the last one
 *   with the same arguments does nothing: the view model starts no run for it and does not say so,
 *   so "working" would wait for a run that never comes.
 * - Up to six in a row and two rows, each at least 48 dp: a small card shows the first ones only.
 */
@Composable
internal fun TogglesBody(spec: WidgetSpec.Toggles, onTap: ((WidgetTap) -> Unit)?, stamp: Long) {
    var waiting by remember { mutableStateOf<Waiting?>(null) }
    val lastTaps = remember { HashMap<String, Long>() }
    // The run ended: nothing waits any more.
    LaunchedEffect(stamp) { waiting = null }
    LaunchedEffect(waiting) {
        if (waiting != null) {
            delay(WidgetWords.WORKING_MS)
            waiting = null
        }
    }
    val working = waiting?.takeIf { it.stamp == stamp }?.index
    ControlGrid(spec.items.size, maxColumns = 6) { index, width, height ->
        val toggle = spec.items[index]
        val onClick: (() -> Unit)? = onTap?.let { tap ->
            {
                val args = toggle.tap.args.orEmpty()
                val at = SystemClock.elapsedRealtime()
                if (working != index && !WidgetWords.tooSoon(lastTaps[args], at)) {
                    lastTaps[args] = at
                    waiting = Waiting(index, stamp)
                    tap(toggle.tap)
                }
            }
        }
        CardToggle(toggle, working == index, width, height, onClick)
    }
}

/**
 * One switch: a Material toggle button, filled while on.
 * - With [working] the loading shape takes its icon's place, and a screen reader hears "working".
 * - With a null [onClick] it takes no tap, and looks as it does on the page.
 * - A long-press is the card's long-press.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CardToggle(toggle: WidgetSpec.Toggle, working: Boolean, width: Dp, height: Dp, onClick: (() -> Unit)?) {
    val source = remember { MutableInteractionSource() }
    val press = cardPress(source)
    val colors = ToggleButtonDefaults.colors().let {
        // A switch that takes no tap keeps the colours of its state.
        it.copy(
            disabledContainerColor = if (toggle.on) it.checkedContainerColor else it.containerColor,
            disabledContentColor = if (toggle.on) it.checkedContentColor else it.contentColor,
        )
    }
    ToggleButton(
        checked = toggle.on,
        onCheckedChange = { press.click { onClick?.invoke() } },
        modifier = Modifier.fillMaxSize().tapLabel(toggle.tap.label).semantics {
            role = Role.Switch
            if (working) stateDescription = "working"
        },
        enabled = onClick != null,
        colors = colors,
        contentPadding = CONTROL_PADDING,
        interactionSource = source,
    ) {
        ControlContent(toggle.icon, toggle.label, width, height, working)
    }
}

// ---- clock ----------------------------------------------------------------------------------

/** The room under a clock's time for its place, in dp. */
private const val PLACE_DP = 16

/** From this width, a place has "tomorrow" beside its name. A narrower one has "+1". */
private val LONG_DAY = 120.dp

/**
 * `clock`: the time in up to four places. Kolahal does the ticking: the script gave the places
 * once, and no run is needed as time passes.
 * - Small, or one place: that place's time, large, over its name.
 * - Wide: the places side by side, as many as fit ([WidgetWords.clockColumns]).
 * - Large: one row for each place, its name at the start and its time at the end.
 * - A place whose day is not the day here says so: "tomorrow" or "yesterday", or "+1" and "-1"
 *   in little room.
 * - The time follows Kolahal's 24-hour setting. It moves by the minute, with the header's clock.
 *   With `seconds` it moves by the second, but only while the widgets page is in view.
 * - The size of the digits comes from the room, never from the text: a clock that ticks does not
 *   change its size, and no text is measured twice.
 */
@Composable
internal fun ClockBody(spec: WidgetSpec.Clock, box: WidgetSizes.Box) {
    val (_, zoneChanges) = rememberNow()
    val here = remember(zoneChanges) { ZoneId.systemDefault() }
    val clock24 = rememberClock24()
    val format = remember(clock24, spec.seconds) { DateTimeFormatter.ofPattern(WidgetWords.clockPattern(clock24, spec.seconds), Locale.getDefault()) }
    val now = rememberMillis(seconds = spec.seconds)
    val times = WidgetWords.zoneTimes(spec.zones, now, here, format)
    if (times.isEmpty()) {
        QuietLine(WidgetWords.NOTHING, small = box == WidgetSizes.Box.SMALL)
        return
    }
    val chars = WidgetWords.clockChars(clock24, spec.seconds)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth.value.toInt()
        val height = maxHeight.value.toInt()
        // The digits are as tall as the box allows over the place's name: a line is about 1.25 of its font size.
        val fits = (height - PLACE_DP) * 4 / 5
        when {
            box == WidgetSizes.Box.SMALL || times.size == 1 -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = if (box == WidgetSizes.Box.SMALL) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                ClockTime(times[0].time, WidgetWords.clockTextDp(width, chars, minOf(fits, 40)))
                PlaceLabel(times[0], long = width.dp >= LONG_DAY)
            }
            box == WidgetSizes.Box.WIDE -> {
                val columns = WidgetWords.clockColumns(times.size, width, chars)
                val gap = WidgetWords.CONTROL_GAP_DP
                val cell = (width - gap * (columns - 1)) / columns
                val size = WidgetWords.clockTextDp(cell, chars, minOf(fits, 28))
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap.dp), verticalAlignment = Alignment.CenterVertically) {
                    times.take(columns).forEachIndexed { i, time ->
                        key(i) {
                            Column(Modifier.weight(1f)) {
                                ClockTime(time.time, size)
                                PlaceLabel(time, long = cell.dp >= LONG_DAY)
                            }
                        }
                    }
                }
            }
            else -> {
                val rows = WidgetWords.clockRows(times.size, height)
                val row = (height / rows).coerceIn(WidgetWords.CLOCK_ROW_DP, 48)
                // The time gets up to half the width. The place's name has the rest.
                val size = WidgetWords.clockTextDp(width / 2, chars, minOf(row * 3 / 5, 24))
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    times.take(rows).forEachIndexed { i, time ->
                        key(i) {
                            Row(Modifier.fillMaxWidth().height(row.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f).padding(end = 8.dp)) { PlaceLabel(time, long = width >= 200, style = MaterialTheme.typography.bodyMedium) }
                                ClockTime(time.time, size)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A clock's time, with digits [sizeDp] tall whatever the phone's font size is: the size was made
 * to fit the box. The digits are all one width where the font can do that, so the text does not
 * shake as it ticks.
 */
@Composable
private fun ClockTime(time: String, sizeDp: Int) {
    val size = with(LocalDensity.current) { sizeDp.dp.toSp() }
    val base = MaterialTheme.typography.titleLarge
    val style = remember(base, size) { base.copy(fontSize = size, lineHeight = size * 1.25f, fontFeatureSettings = "tnum") }
    Text(time, style = style, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
}

/**
 * A place's name, and its day when that is not the day here. The name is cut with "…" before the
 * day is: the day says more.
 */
@Composable
private fun PlaceLabel(time: WidgetWords.ZoneTime, long: Boolean, style: TextStyle = MaterialTheme.typography.labelSmall) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val day = if (long) time.day?.let { " · $it" } else time.dayShort?.let { " $it" }
    Row {
        Text(time.label, style = style, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (day != null) Text(day, style = style, color = muted, maxLines = 1, softWrap = false)
    }
}

// ---- agenda ---------------------------------------------------------------------------------

/**
 * Whether Kolahal writes a time of day in 24-hour form: its own setting, which the header and the
 * log follow too. It is read again when a setting changes.
 */
@Composable
internal fun rememberClock24(): Boolean {
    val context = LocalContext.current.applicationContext
    val config = remember(context) { Config.get(context) }
    val version by config.version.collectAsStateWithLifecycle()
    return remember(config, version) { config[Keys.CLOCK_24H] }
}

/** Height of a day's heading and of the "+3 more" line, in dp. */
private const val AGENDA_HEAD_DP = 20
private const val AGENDA_MORE_DP = 20

/** Under this width an event has its time under its title, not beside it. */
private val AGENDA_NARROW = 120.dp

/**
 * `agenda`: events under "Today", "Tomorrow" and dates, as many as fit, then "+3 more".
 * - Kolahal writes the times, in the form the setting says, and drops an event when it has ended.
 *   That happens on the minute's tick ([rememberNow]), with no run of the script and no tick of
 *   its own.
 * - An event that can be tapped is 48 dp tall, and so is one with a place under its title. Other
 *   events are 32 dp.
 * - With nothing to show it says the description's `empty` text.
 */
@Composable
internal fun AgendaBody(spec: WidgetSpec.Agenda, onTap: ((WidgetTap) -> Unit)?) {
    val (minute, zoneChanges) = rememberNow()
    val zone = remember(zoneChanges) { ZoneId.systemDefault() }
    val clock24 = rememberClock24()
    val days = remember(spec, minute, zone, clock24) { WidgetWords.agenda(spec, minute.time / 1000, zone, clock24) }
    if (days.isEmpty()) {
        QuietLine(spec.empty ?: WidgetWords.NO_EVENTS)
        return
    }
    val today = roleColour(spec.base)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < AGENDA_NARROW
        val places = maxWidth >= 200.dp && days.any { day -> day.lines.any { it.event.where != null } }
        val rowDp = if (places || days.any { day -> day.lines.any { it.event.tap != null } }) 48 else 32
        val heightDp = maxHeight.value.toInt()
        val total = days.sumOf { it.lines.size }
        val shown = WidgetWords.agendaShown(days.map { it.lines.size }, heightDp, AGENDA_HEAD_DP, rowDp, AGENDA_MORE_DP)
        // The time column is as wide as the longest time in this form, so the titles line up.
        val timeWidth = if (clock24) 56.dp else 76.dp
        Column(Modifier.fillMaxSize()) {
            var left = shown
            var usedDp = 0
            for ((d, day) in days.withIndex()) {
                if (left <= 0) break
                val lines = day.lines.take(left)
                left -= lines.size
                usedDp += AGENDA_HEAD_DP + lines.size * rowDp
                key(d) {
                    Text(
                        day.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (day.label == WidgetWords.TODAY) today else muted,
                        maxLines = 1,
                        modifier = Modifier.height(AGENDA_HEAD_DP.dp).wrapContentHeight(),
                    )
                    lines.forEachIndexed { i, line -> key(i) { AgendaRow(line, rowDp.dp, places, narrow, timeWidth, onTap) } }
                }
            }
            if (shown < total && usedDp + AGENDA_MORE_DP <= heightDp) {
                Text("+${total - shown} more", style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1, modifier = Modifier.height(AGENDA_MORE_DP.dp).wrapContentHeight())
            }
        }
    }
}

/**
 * One event: its time, then its title, with its place under the title when [places]. Its colour
 * is on its time. A [narrow] row has the time under the title. An event with a tap takes the
 * press before the card does, so it hands a long-press on to the card, as a list's row does.
 */
@Composable
private fun AgendaRow(line: WidgetWords.AgendaLine, height: Dp, places: Boolean, narrow: Boolean, timeWidth: Dp, onTap: ((WidgetTap) -> Unit)?) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val event = line.event
    val tap = event.tap
    val timeColour = event.color?.let { colourOf(it) } ?: scheme.onSurfaceVariant
    val longPress = LocalCardLongPress.current
    val press = if (tap != null && onTap != null) {
        Modifier.combinedClickable(
            onClickLabel = tap.label,
            role = Role.Button,
            onLongClickLabel = if (longPress != null) "arrange widgets" else null,
            onLongClick = longPress,
            onClick = { onTap(tap) },
        )
    } else {
        Modifier
    }
    Row(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(12.dp)).then(press), verticalAlignment = Alignment.CenterVertically) {
        if (narrow) {
            Column(Modifier.weight(1f)) {
                Text(event.title, style = type.bodySmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(line.time, style = type.labelSmall, color = timeColour, maxLines = 1, softWrap = false)
            }
        } else {
            Text(line.time, style = type.labelLarge, color = timeColour, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = timeWidth).padding(end = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(event.title, style = type.bodyMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (places) event.where?.let { Text(it, style = type.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

// ---- the time, for media and the clock -------------------------------------------------------

/**
 * The time now, in milliseconds.
 * - It moves by the minute, with the header's clock ([rememberNow]). That tick also comes when
 *   home is back on screen and when the clock is set.
 * - With [seconds] it moves every second, but only while the widgets page is in view and home is
 *   on screen. A card nobody sees ticks for nothing.
 */
@Composable
private fun rememberMillis(seconds: Boolean): Long {
    val (minute, _) = rememberNow()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(minute) { now = System.currentTimeMillis() }
    if (seconds && LocalWidgetsShown.current) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val ms = System.currentTimeMillis()
                    now = ms
                    // Wake when the next second starts.
                    delay(1000 - ms % 1000)
                }
            }
        }
    }
    return now
}

// ---- media ----------------------------------------------------------------------------------

private val PREV = WidgetTap(args = WidgetWords.MEDIA_PREV)
private val TOGGLE = WidgetTap(args = WidgetWords.MEDIA_TOGGLE)
private val NEXT = WidgetTap(args = WidgetWords.MEDIA_NEXT)

/** In a large box, the bar needs this much height, and the two times under it this much. */
private val MEDIA_BAR_FROM = 104.dp
private val MEDIA_TIMES_FROM = 122.dp

/**
 * `media`: what is playing, with its controls. Only Kolahal makes this kind.
 * - Small: play or pause alone.
 * - Wide: the title, the artist and the app, with a bar for the position under them, and the
 *   buttons at the end. A narrow box has fewer buttons, never smaller ones ([WidgetWords.mediaButtons]).
 * - Large: the same in rows, with the position and the length under the bar.
 * - The three buttons hand `prev`, `toggle` and `next` to [onTap]. A tap elsewhere is the card's.
 * - The position moves every second only while something plays, its length is known and the page
 *   is in view. The bar stands still between ticks: it is no wave, because a wave is drawn on every frame.
 */
@Composable
internal fun MediaBody(spec: WidgetSpec.Media, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
    if (box == WidgetSizes.Box.SMALL) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { MediaButtons(1, spec.playing, onTap) }
        return
    }
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val colour = roleColour(spec.base)
    val title = WidgetWords.mediaTitle(spec)
    val line = WidgetWords.mediaLine(spec)
    val now = rememberMillis(seconds = spec.playing && spec.duration > 0)
    val fraction = WidgetWords.mediaFraction(spec, now)?.let(WidgetWords::barStep)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth.value.toInt()
        val tall = maxHeight
        if (box == WidgetSizes.Box.WIDE) {
            // The buttons are measured first, so the texts get what is left and are cut, not the buttons.
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = type.bodyMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (line.isNotEmpty()) Text(line, style = type.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (fraction != null && tall >= TWO_LINES) LevelBar(fraction, moving = false, colour, Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.width(WidgetWords.CONTROL_GAP_DP.dp))
                MediaButtons(WidgetWords.mediaButtons(width, besideText = true), spec.playing, onTap)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    Text(title, style = type.titleMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (line.isNotEmpty()) Text(line, style = type.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (fraction != null && tall >= MEDIA_BAR_FROM) {
                        Spacer(Modifier.weight(1f))
                        LevelBar(fraction, moving = false, colour)
                        if (tall >= MEDIA_TIMES_FROM) Row(Modifier.fillMaxWidth()) {
                            Text(WidgetWords.mediaTime(WidgetWords.mediaPosition(spec, now)), style = type.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
                            Spacer(Modifier.weight(1f))
                            Text(WidgetWords.mediaTime(spec.duration), style = type.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    MediaButtons(WidgetWords.mediaButtons(width, besideText = false), spec.playing, onTap)
                }
            }
        }
    }
}

/** [count] of the three buttons, in a row: play or pause alone, then with next, then with previous too. */
@Composable
private fun MediaButtons(count: Int, playing: Boolean, onTap: ((WidgetTap) -> Unit)?) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (count >= 3) MediaButton(PREV, R.drawable.ic_skip_next, "Previous", onTap, mirrored = true)
        MediaButton(TOGGLE, if (playing) R.drawable.ic_pause else R.drawable.ic_play, if (playing) "Pause" else "Play", onTap, strong = true)
        if (count >= 2) MediaButton(NEXT, R.drawable.ic_skip_next, "Next", onTap)
    }
}

/**
 * One of the media buttons, 48 dp across. [said] is its name for a screen reader.
 * - [strong]: the filled button, for play or pause. The others are tonal.
 * - [mirrored]: the icon is turned to point back. "Previous" is the "next" icon, mirrored.
 * - With a null [onTap] it takes no tap, and looks as it does on the page.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaButton(tap: WidgetTap, icon: Int, said: String, onTap: ((WidgetTap) -> Unit)?, strong: Boolean = false, mirrored: Boolean = false) {
    val source = remember { MutableInteractionSource() }
    val press = cardPress(source)
    val onClick = { press.click { onTap?.invoke(tap) } }
    val modifier = Modifier.size(WidgetWords.CONTROL_DP.dp)
    val drawn: @Composable () -> Unit = { Icon(painterResource(icon), said, if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier) }
    if (strong) {
        val colors = IconButtonDefaults.filledIconButtonColors().let { it.copy(disabledContainerColor = it.containerColor, disabledContentColor = it.contentColor) }
        FilledIconButton(onClick = onClick, shapes = IconButtonDefaults.shapes(), modifier = modifier, enabled = onTap != null, colors = colors, interactionSource = source, content = drawn)
    } else {
        val colors = IconButtonDefaults.filledTonalIconButtonColors().let { it.copy(disabledContainerColor = it.containerColor, disabledContentColor = it.contentColor) }
        FilledTonalIconButton(onClick = onClick, shapes = IconButtonDefaults.shapes(), modifier = modifier, enabled = onTap != null, colors = colors, interactionSource = source, content = drawn)
    }
}
