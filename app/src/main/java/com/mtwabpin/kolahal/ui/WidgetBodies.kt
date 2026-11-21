/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

/*
 * The content of the kinds with parts of their own to tap or to tick: buttons, toggles, clock,
 * agenda and media. [KindBody] calls them. The first six kinds are in WidgetKinds.kt.
 *
 * Where they are: WidgetControls.kt (buttons and toggles, with the grids and controls they share),
 * WidgetClock.kt, WidgetAgenda.kt, WidgetMedia.kt, and WidgetTime.kt (the time the clock, media and
 * countdown read).
 *
 * Rules for every body here:
 * - With a null `onTap`, nothing is clickable (the Widgets screen).
 * - Time comes from the header's clock ([rememberNow]). A tick every second runs only while the
 *   widgets page is in view ([LocalWidgetsShown]) and the body shows seconds.
 * - Nothing moves for ever, and nothing but drawing runs on the main thread.
 * - Each body draws the card's look ([LocalWidgetLook]). Plain is the card as it was. Tonal puts
 *   the parts on the look's tile; Bold, Split and Shape have layouts of their own, and the card's
 *   frame leaves its title to them ([LocalUntitled]). A shape that morphs runs only while it does,
 *   and only its drawing runs again.
 */
