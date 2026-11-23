/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.ui.home.rememberNow
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import java.time.ZoneId

/**
 * `countdown`: the time to a moment, or since it. Kolahal does the ticking: the script gave the
 * moment once, and no run is needed as time passes.
 * - The time moves by the minute, with the header's clock ([rememberNow]).
 * - It moves by the second ([EverySecond]) only while the text shows seconds and the card can be seen.
 *   A tick draws the number again ([CountdownValue]), not the whole card.
 * - The number is big and its unit small: "36 days" ([WidgetWords.Count]).
 * - With a start (`from`), a bar runs from the start to the moment. In a wide box the bar takes the
 *   label's place. A small box has no bar. A large box has the time at its top and the bar at its
 *   bottom, with the start and the moment under its ends: "1 Oct" and "20 Dec"
 *   ([WidgetWords.countdownEnds]).
 * - The bar is a wave that stands still ([LevelBar] with `wavy`). A countdown that waits is not at
 *   work. A wave that moves is drawn again on every frame, for days; a still one costs nothing.
 * - The bar moves in steps ([WidgetWords.barStep]), so a clock that ticks does not draw it again
 *   every second.
 */
@Composable
internal fun CountdownBody(spec: WidgetSpec.Countdown, box: WidgetSizes.Box) {
    val (minute, zoneChanges) = rememberNow()
    val zone = remember(zoneChanges) { ZoneId.systemDefault() }
    // The time now, in seconds. Only what changes with it reads it: the count and the bar's step.
    val now = remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    // The minute's tick. It also comes when home is back on screen, and when the clock is set.
    LaunchedEffect(minute) { now.longValue = System.currentTimeMillis() / 1000 }
    val count = remember(spec.to, spec.style, spec.reached, zone) {
        derivedStateOf {
            WidgetWords.countdown(
                spec.to,
                now.longValue,
                spec.style,
                spec.reached,
                zone
            )
        }
    }
    val ticks by remember(count) { derivedStateOf { count.value.ticks } }
    EverySecond(ticks) { now.longValue = it / 1000 }
    val small = box == WidgetSizes.Box.SMALL
    val large = box == WidgetSizes.Box.LARGE
    val type = MaterialTheme.typography
    val look = LocalWidgetLook.current
    val muted = lookColors(look).muted
    // Shape draws the wait as a ring around a small card too.
    val barless = small && look != WidgetLook.SHAPE
    val bar by remember(barless, spec.from, spec.to) {
        derivedStateOf {
            if (barless) null else WidgetWords.countdownFraction(
                spec.from,
                spec.to,
                now.longValue
            )?.let(WidgetWords::barStep)
        }
    }
    val clock24 = rememberClock24()
    // A large box writes the start and the moment under the bar's ends. They change at most once a day.
    // Bold, Split and Shape write the moment in any box, also without a start: then it is the day
    // ("27 Dec"), or the time on the day itself.
    val from = spec.from
    val day by remember(zone) { derivedStateOf { WidgetWords.localDay(now.longValue, zone) } }
    val ends = if ((large && from != null) || look.untitled()) {
        remember(from, spec.to, zone, clock24, day) {
            val at = Snapshot.withoutReadObservation { now.longValue }
            WidgetWords.countdownEnds(from ?: at, spec.to, at, zone, clock24)
        }
    } else {
        null
    }
    if (look.untitled()) {
        CountdownLook(spec, box, look, count, bar, ends?.second, ends.takeIf { from != null }, now)
        return
    }
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = if (large) Arrangement.Top else Arrangement.Center,
        horizontalAlignment = if (small) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        // With no label, the card's name says what the wait is for: "Goa trip".
        val label = spec.label ?: LocalUntitled.current
        if (!small && !large && bar != null && label != null) {
            // A wide card's bar takes the line under the number: the label goes at the end of the number's line.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { CountdownValue(count, large = false, box) }
                Text(
                    label,
                    style = type.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .widthIn(max = 160.dp)
                )
            }
        } else {
            CountdownValue(count, large, box)
        }
        if (bar == null || large) label?.let {
            // A small box has room for two short lines: "to 20 / December".
            Text(
                it,
                style = if (large) type.bodyMedium else type.bodySmall,
                color = muted,
                maxLines = if (small) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (small) TextAlign.Center else TextAlign.Start
            )
        }
        bar?.let { fraction ->
            // A large box has the bar at its bottom.
            if (large) Spacer(Modifier.weight(1f))
            // A wave that stands still: a countdown waits, it is not at work.
            LevelBar(
                fraction,
                moving = false,
                roleColor(spec.base),
                Modifier.padding(top = 4.dp),
                wavy = true
            )
            ends?.let { (start, end) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                ) {
                    Text(start, style = type.bodySmall, color = muted, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    Text(
                        end,
                        style = type.bodySmall,
                        color = muted,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

/** A countdown's number big and its unit small: "36 days". It alone is drawn again at each tick. */
@Composable
private fun CountdownValue(count: State<WidgetWords.Count>, large: Boolean, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val now = count.value
    Row {
        BigText(
            StyledText.capitalized(now.value),
            if (large) type.displayMedium else valueStyle(box),
            Modifier
                .weight(1f, fill = false)
                .alignByBaseline()
        )
        now.unit?.let {
            val unitStyle =
                if (large) type.titleLarge.copy(fontWeight = FontWeight.Bold) else type.titleMedium
            Text(
                it,
                style = unitStyle,
                color = lookColors().muted,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .alignByBaseline()
            )
        }
    }
}
