package com.mtwabpin.kolahal.ui

import androidx.compose.runtime.Composable
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap

/*
 * The content of the kinds with parts of their own to tap or to tick: buttons, toggles, clock,
 * agenda and media. [KindBody] calls them. The first six kinds are in WidgetKinds.kt.
 *
 * Rules for every body here:
 * - With a null `onTap`, nothing is clickable (the Widgets screen).
 * - Time comes from the header's clock ([rememberNow]). A tick every second runs only while the
 *   widgets page is in view ([LocalWidgetsShown]) and the body shows seconds.
 */

/** `buttons`: a grid of buttons. Not drawn yet. */
@Composable
internal fun ButtonsBody(spec: WidgetSpec.Buttons, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
    QuietLine(WidgetWords.UNAVAILABLE, small = box == WidgetSizes.Box.SMALL)
}

/** `toggles`: switches. [stamp] changes when the script's run ends. Not drawn yet. */
@Composable
internal fun TogglesBody(spec: WidgetSpec.Toggles, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?, stamp: Long) {
    QuietLine(WidgetWords.UNAVAILABLE, small = box == WidgetSizes.Box.SMALL)
}

/** `clock`: the time in several places. Not drawn yet. */
@Composable
internal fun ClockBody(spec: WidgetSpec.Clock, box: WidgetSizes.Box) {
    QuietLine(WidgetWords.UNAVAILABLE, small = box == WidgetSizes.Box.SMALL)
}

/** `agenda`: events by day. Not drawn yet. */
@Composable
internal fun AgendaBody(spec: WidgetSpec.Agenda, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
    QuietLine(WidgetWords.UNAVAILABLE, small = box == WidgetSizes.Box.SMALL)
}

/** `media`: what is playing, with its controls. Not drawn yet. */
@Composable
internal fun MediaBody(spec: WidgetSpec.Media, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
    QuietLine(WidgetWords.UNAVAILABLE, small = box == WidgetSizes.Box.SMALL)
}
