/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.data.HomeEntry
import com.mtwabpin.kolahal.data.HomeOrder
import com.mtwabpin.kolahal.ui.home.bandCutouts
import com.mtwabpin.kolahal.ui.home.lineZones
import com.mtwabpin.kolahal.ui.theme.LocalTopCorners
import com.mtwabpin.kolahal.ui.theme.Term
import com.mtwabpin.kolahal.ui.theme.windowTopCorners
import com.mtwabpin.kolahal.widgets.WidgetPage
import kotlin.math.abs
import kotlin.math.floor

/**
 * Where the strip at the top of the widgets pages goes, and what it shows ([WidgetsStrip]). Pure, so it
 * can be unit tested; every size is in pixels.
 */
internal object WidgetsStripLayout {
    /** The two places the strip can be. */
    enum class Case {
        /** The status bar is hidden and the phone keeps a strip for its camera: the line sits in it, beside the camera. */
        CAMERA,

        /** The status bar shows, or there is no camera strip: the line sits under the bar's inset, or at the top edge. */
        BELOW_BAR,
    }

    /** The strip: its [case], its [top] in the window and its [height]. A widgets page's cards start at its [bottom]. */
    data class Span(val case: Case, val top: Int, val height: Int) {
        val bottom get() = top + height
    }

    /**
     * Which place the strip has, from the status bar's top inset ([barTop], 0 while the bar is hidden)
     * and the camera cutout's ([cutoutTop]). It doesn't follow `header.below-bar`: on, that setting
     * keeps the bar's height empty even while the bar is hidden.
     */
    fun case(barTop: Int, cutoutTop: Int): Case =
        if (barTop == 0 && cutoutTop > 0) Case.CAMERA else Case.BELOW_BAR

    /**
     * The strip for these insets ([case]). [line] is the strip's height under the bar, the least it
     * is beside the camera: 20 dp, or one line of its text if that is taller.
     * - Beside the camera: from the window's top, as tall as the camera's strip.
     * - Under the bar: under the status bar's inset and the cutout's, whichever is lower (the top edge
     *   with neither).
     */
    fun span(barTop: Int, cutoutTop: Int, line: Int): Span = when (case(barTop, cutoutTop)) {
        Case.CAMERA -> Span(Case.CAMERA, 0, maxOf(cutoutTop, line))
        Case.BELOW_BAR -> Span(Case.BELOW_BAR, maxOf(barTop, cutoutTop, 0), line)
    }

    /** Whether home's page at [place] is a widgets page: the strip shows over those only. */
    fun isWidgets(order: HomeOrder, place: Int): Boolean = order.at(place) is HomeEntry.Widgets

    /** Less than this from a page, the pager is on it. */
    private const val SETTLED = 0.001f

    /**
     * The page the strip speaks for while the pager is at [position] (its page plus the offset): the
     * nearest page when it is a widgets page; during a swipe between a widgets page and the log or
     * the Assistant's page, the widgets page; null while the pager is on a page that isn't one. So the
     * name and the lit mark switch at the halfway point between two widgets pages.
     */
    fun shownPlace(order: HomeOrder, position: Float): Int? {
        val near = floor(position + 0.5f).toInt()
        if (isWidgets(order, near)) return near
        val off = position - near
        if (abs(off) < SETTLED) return null
        val other = if (off > 0) near + 1 else near - 1
        return other.takeIf { isWidgets(order, it) }
    }

    /**
     * How much the strip shows while the pager is at [position]: fully over a widgets page, not at all
     * over the others, and in between as one slides over the other.
     */
    fun alpha(order: HomeOrder, position: Float): Float {
        val from = floor(position).toInt()
        val f = position - from
        fun shows(place: Int) = if (isWidgets(order, place)) 1f else 0f
        return ((1 - f) * shows(from) + f * shows(from + 1)).coerceIn(0f, 1f)
    }

    /** What a screen reader hears: "Daily, page 3 of 4", for the page called [name] at [place] of [count]. */
    fun description(name: String, place: Int, count: Int): String = "$name, page ${place + 1} of $count"

    /** Where the name and the marks go along the line: their x in the window's direction, and the width the name has. */
    data class Places(val nameX: Int, val nameWidth: Int, val marksX: Int)

    private fun room(zone: IntRange) = zone.last - zone.first + 1

    /**
     * Places the name ([name] wide as it would like) and the marks ([marks] wide) on a line [width]
     * wide, in its free [zones] (left to right, [lineZones]):
     * - With the camera between two zones and room in each: the name centered in the one at the
     *   start, the marks centered in the one at the end.
     * - Otherwise (no camera, a camera in a corner, a side too narrow): both together, the name first
     *   and [gap] between them, centered in the widest zone. A name too long for it is cut.
     * [rtl]: the start is the right, as the pager's pages and the marks run.
     */
    fun place(width: Int, zones: List<IntRange>, name: Int, marks: Int, gap: Int, rtl: Boolean): Places {
        val z = if (!rtl) zones else zones.map { (width - 1 - it.last)..(width - 1 - it.first) }.reversed()
        val p = placeFromStart(width, z, name, marks, gap)
        return if (!rtl) p else Places(width - p.nameX - p.nameWidth, p.nameWidth, width - p.marksX - marks)
    }

    /** [place], measured from the start. */
    private fun placeFromStart(width: Int, zones: List<IntRange>, name: Int, marks: Int, gap: Int): Places {
        fun centered(zone: IntRange, w: Int) = zone.first + (room(zone) - w) / 2
        if (zones.size >= 2 && name <= room(zones.first()) && marks <= room(zones.last()))
            return Places(centered(zones.first(), name), name, centered(zones.last(), marks))
        val zone = zones.maxByOrNull { room(it) } ?: 0..<width
        val between = if (name > 0 && marks > 0) gap else 0
        val shown = name.coerceAtMost((room(zone) - marks - between).coerceAtLeast(0))
        val sep = if (shown > 0 && marks > 0) gap else 0
        val total = shown + sep + marks
        val start = zone.first + ((room(zone) - total) / 2).coerceAtLeast(0)
        return Places(start, shown, start + shown + sep)
    }
}

/** The strip's height under the status bar, and the least it is beside the camera. */
private val STRIP_HEIGHT = 20.dp

/** Space between the page's name and the marks when they sit together. */
private val NAME_GAP = 8.dp

/** The page's name in the strip. */
private fun nameStyle(): TextStyle = Term.small.copy(color = Term.prompt, fontWeight = FontWeight.Bold)

/** The strip for the window's insets now ([WidgetsStripLayout.span]). */
@Composable
internal fun rememberStripSpan(): WidgetsStripLayout.Span {
    val density = LocalDensity.current
    val barTop = WindowInsets.statusBars.getTop(density)
    val cutoutTop = WindowInsets.displayCutout.getTop(density)
    val line = with(density) { maxOf(STRIP_HEIGHT.roundToPx(), nameStyle().lineHeight.roundToPx()) }
    return WidgetsStripLayout.span(barTop, cutoutTop, line)
}

/** The insets at the sides that a widgets page and its strip keep clear: the status bar's and the camera's. */
@Composable
private fun sideInsets() = WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)

/**
 * A widgets page's room for the strip ([WidgetsStrip]): the strip's height at the top, under it the
 * cards; and the side insets.
 */
@Composable
internal fun Modifier.belowWidgetsStrip(): Modifier {
    val span = rememberStripSpan()
    val top = with(LocalDensity.current) { span.bottom.toDp() }
    return windowInsetsPadding(sideInsets()).padding(top = top)
}

/**
 * The line at the top of the widgets pages: the page's name and the marks for home's pages
 * ([pageMarks], when home has more than one), and nothing else. One overlay above home's [pager] in
 * [order], so it stays put while the pages move:
 * - It shows over the widgets pages and fades out over the log and the Assistant's page
 *   ([WidgetsStripLayout.alpha]). The name and the lit mark switch at the halfway point between two
 *   widgets pages ([WidgetsStripLayout.shownPlace]).
 * - With the status bar hidden on a phone with a camera strip, it sits in that strip, the name on one
 *   side of the camera and the marks on the other, clear of the cutout and the screen's rounded
 *   corners as the log's header measures them. Otherwise it is 20 dp under the status bar
 *   ([WidgetsStripLayout.span]). Each widgets page keeps its height free ([belowWidgetsStrip]).
 * - It takes no touches. A screen reader hears one node, "Daily, page 3 of 4", with Add a widget
 *   ([onAdd]) and Arrange widgets ([onArrange]) as its actions, each given the page's location.
 * - [fade]: how far the app selector has faded the widgets out; the strip fades with them.
 * [pages] are the widgets pages, for their names ([HomeOrder.name]).
 * The pager's position is read in a derived state and in the draw phase: a swipe redraws the strip,
 * and composes it again only when the page it speaks for changes.
 */
@Composable
internal fun WidgetsStrip(
    pager: PagerState,
    order: HomeOrder,
    pages: List<WidgetPage>,
    fade: () -> Float,
    onAdd: (location: String) -> Unit,
    onArrange: (location: String) -> Unit,
) {
    fun position() = pager.currentPage + pager.currentPageOffsetFraction
    val shown by remember(pager, order) { derivedStateOf { WidgetsStripLayout.shownPlace(order, position()) } }
    val place = shown ?: return
    val entry = order.at(place) as? HomeEntry.Widgets ?: return
    val name = HomeOrder.name(entry, pages)
    val marks = remember(order, place) { if (order.count > 1) pageMarks(order, place) else emptyList() }
    val location = entry.page.location
    val description = WidgetsStripLayout.description(name, place, order.count)

    val density = LocalDensity.current
    val span = rememberStripSpan()
    val barTop = WindowInsets.statusBars.getTop(density)
    val cutoutTop = WindowInsets.displayCutout.getTop(density)
    val view = LocalView.current
    // The camera's x ranges across the line: only beside the camera.
    val cutouts = remember(span, barTop, cutoutTop, view) {
        if (span.case == WidgetsStripLayout.Case.CAMERA) bandCutouts(view, cutoutTop - barTop, barTop) else emptyList()
    }
    val givenCorners = LocalTopCorners.current
    val style = nameStyle()
    val lineHeight = with(density) { style.lineHeight.roundToPx() }
    // The line's x in the window, for the cutout's and the corners' x. Known after placement: the measure
    // reads it as state and measures again when it changes.
    val originX = remember { mutableFloatStateOf(0f) }
    Layout(
        content = {
            Text(name, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (marks.isNotEmpty()) WidgetPageDots(marks)
        },
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(sideInsets())
            .padding(top = with(density) { span.top.toDp() })
            .height(with(density) { span.height.toDp() })
            .graphicsLayer { alpha = WidgetsStripLayout.alpha(order, position()) * (1f - fade()) }
            .testTag("widgets-strip")
            .clearAndSetSemantics {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(ADD_WIDGET) { onAdd(location); true },
                    CustomAccessibilityAction(ARRANGE_WIDGETS) { onArrange(location); true },
                )
            }
            .onPlaced { originX.floatValue = it.positionInWindow().x },
    ) { measurables, constraints ->
        val h = constraints.maxHeight
        val width = constraints.maxWidth
        val marksPlaced = measurables.getOrNull(1)?.measure(Constraints())
        val nameWidth = measurables[0].maxIntrinsicWidth(h)
        val contentHeight = maxOf(lineHeight, marksPlaced?.height ?: 0)
        val corners = givenCorners ?: windowTopCorners(view)
        val zones = lineZones(width, originX.floatValue.toInt(), span.top, h, contentHeight, cutouts, corners)
        val places = WidgetsStripLayout.place(
            width,
            zones,
            nameWidth,
            marksPlaced?.width ?: 0,
            NAME_GAP.roundToPx(),
            rtl = layoutDirection == LayoutDirection.Rtl,
        )
        val namePlaced = measurables[0].measure(Constraints(maxWidth = places.nameWidth))
        layout(width, h) {
            if (places.nameWidth > 0) namePlaced.place(places.nameX, (h - namePlaced.height) / 2)
            marksPlaced?.place(places.marksX, (h - marksPlaced.height) / 2)
        }
    }
}

/**
 * The strip's line as it shows under the status bar: [name] and the [marks] together, centered, 20 dp
 * tall. For pictures of the widgets page, where there is no pager.
 */
@Composable
internal fun WidgetsStripLine(name: String, marks: List<PageMark>, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier
            .fillMaxWidth()
            .height(STRIP_HEIGHT),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
            NAME_GAP,
            androidx.compose.ui.Alignment.CenterHorizontally
        ),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(name, style = nameStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (marks.isNotEmpty()) WidgetPageDots(marks)
    }
}
