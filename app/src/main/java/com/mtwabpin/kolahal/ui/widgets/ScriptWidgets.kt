/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.status.DeviceStatus
import com.mtwabpin.kolahal.status.Status
import com.mtwabpin.kolahal.terminal.WidgetAge
import com.mtwabpin.kolahal.terminal.WidgetCardActions
import com.mtwabpin.kolahal.terminal.WidgetFeed
import com.mtwabpin.kolahal.terminal.WidgetScript
import com.mtwabpin.kolahal.ui.home.networkIcon
import com.mtwabpin.kolahal.ui.home.rememberNow
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetColors
import com.mtwabpin.kolahal.widgets.WidgetJson
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetParts
import com.mtwabpin.kolahal.widgets.WidgetRef
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSource
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetStates
import com.mtwabpin.kolahal.widgets.WidgetTap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * True while the widgets page is the page in view: home's pager has settled on it. Home keeps the
 * page composed beside the log, so "composed" does not mean "seen". A card is filled (its first run,
 * a run for old content) and its picture fetched only while this is true.
 */
val LocalWidgetsShown = compositionLocalOf { false }

/**
 * True while some of this card is on screen. The widgets page scrolls, so a card below the fold is
 * on the page that is in view, but nobody sees it. The page sets it for each card; elsewhere a card
 * counts as on screen.
 */
val LocalCardOnScreen = compositionLocalOf { true }

/**
 * True while this card can be seen: the widgets page is in view ([LocalWidgetsShown]) and the card
 * is on screen ([LocalCardOnScreen]). Things that move (the loading shape, a wave, the seconds of a
 * countdown, a clock or a song) and a card's runs (its first, one for old content, `refresh`) go on
 * only while this is true.
 */
@Composable
internal fun cardSeen(): Boolean = LocalWidgetsShown.current && LocalCardOnScreen.current

/**
 * The long-press of the card around a kind's content, or null when the card takes none. A part of
 * the content with a tap of its own (a list's row) takes the press before the card does, so it
 * hands a long-press on to this.
 */
internal val LocalCardLongPress = compositionLocalOf<(() -> Unit)?> { null }

/**
 * The card's title, given to its content. No card has a title line, so the content shows the title
 * small where it doesn't say what it is itself ([CardName]): a list, a text, a countdown, a number
 * with no label. A ring card writes it beside the ring. Null in a small Plain or Tonal box.
 */
internal val LocalUntitled = compositionLocalOf<String?> { null }

/**
 * When the card's content was set, in milliseconds since 1970, or 0 when not known. A chart of dots
 * takes its last day from it, so yesterday's days stay under their own weekdays until the next run
 * brings today's.
 */
internal val LocalContentSetAt = compositionLocalOf { 0L }

/** What a card shows when it has no content to draw. */
internal enum class CardNote {
    /** The stored state is not read yet. The card stays empty for a moment. */
    WAITING,

    /** Nothing is stored yet, and a run is on its way. */
    LOADING,

    /** The script showed nothing, or cleared its widget. */
    NOTHING,

    /** The last run failed and nothing is stored. The card shows the error's first line. */
    FAILED,

    /** The stored content can't be read, or nothing can fill this widget. */
    UNAVAILABLE,

    /** No script has the card's name any more: it was deleted. The card stays until you remove it. */
    REMOVED,

    /** The card's script is switched off. */
    OFF,

    /** The script's code has no `@widget` line any more, so `widget.show` stores nothing. */
    NO_WIDGET,
}

/**
 * The note of a card whose script can't fill it, or null for a script that can.
 * - [read] is false while the view model has not read the scripts yet. Nothing is known then, and
 *   the card says nothing about its script.
 * - A null [script] means that no script has the name.
 * - A script without its `@widget` line can't fill a widget.
 * Such a card asks for no runs: they would do nothing. A tap on it leads to where it is put right ([noteTap]).
 */
internal fun scriptNote(read: Boolean, script: WidgetScript?): CardNote? = when {
    !read -> null
    script == null -> CardNote.REMOVED
    !script.enabled -> CardNote.OFF
    script.widget == null -> CardNote.NO_WIDGET
    else -> null
}

/**
 * The card of a script widget, [height] tall and as wide as it is given. Call it inside [GuiTheme].
 * - It has its own opaque surface, so its text reads over any wallpaper.
 * - It draws [spec] for the box it gets ([WidgetSizes.box]), with no title line ([CardLayout]):
 *   the content names the card small where it needs to.
 * - With a [note], it shows that and not the content. [failure] is the text of [CardNote.FAILED].
 * - [footer] is the content's age, or "Failed 10:42": a large card's last line. Wide and small have
 *   no room for it.
 * - [description] is the whole card for a screen reader, in one item. Rows with their own tap are
 *   items of their own inside it.
 * - [onClick] is a tap on the card and [onLongClick] a long-press. With a long-press and no tap, a
 *   tap that misses the card's own items does nothing. [onTap] gets the own action of a
 *   row, a button, a switch or an event. With all three null the card takes no touches (the Widgets screen).
 * - [stamp] is a number that changes when the content was filled again, also with the same
 *   description: a script's card gives the time of its last run. A switch that waits for its script
 *   watches it ([KindBody]).
 * - [body] draws the content in place of [spec]'s kind, on a Plain card: one of Kolahal's own cards
 *   in a look of its own ([OwnBody]). [spec] is still what it says, and a [note] comes first.
 * - A color chosen for the placed card ([LocalChosenColor]) takes the place of [spec]'s own
 *   ([WidgetColors.drawn]); a [body] reads it from [LocalCardColor]. The parts it hides
 *   ([LocalHiddenParts]) are left out: the name is given to no body, the icon leaves [spec].
 */
@Composable
internal fun WidgetCard(
    title: String,
    height: Dp,
    spec: WidgetSpec?,
    note: CardNote?,
    description: String,
    modifier: Modifier = Modifier,
    failure: String? = null,
    footer: WidgetWords.Footer? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onTap: ((WidgetTap) -> Unit)? = null,
    stamp: Long = 0,
    body: (@Composable (box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) -> Unit)? = null,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val widthDp = maxWidth.value.toInt()
        val heightDp = maxHeight.value.toInt()
        val box = WidgetSizes.box(widthDp, heightDp)
        // The tap and the description are on one node, so a screen reader finds one item.
        val press = if (onClick != null) {
            Modifier.combinedClickable(
                onClickLabel = onClickLabel,
                onLongClickLabel = if (onLongClick != null) "arrange widgets" else null,
                onLongClick = onLongClick,
                onClick = onClick,
            )
        } else if (onLongClick != null) {
            // No tap of its own (Todos, Shortcuts with buttons): a tap that misses a row or a button
            // does nothing, and the long-press still arranges the widgets.
            val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
            val longPress: () -> Unit = onLongClick
            Modifier
                .semantics(mergeDescendants = true) {
                    this.onLongClick(label = "arrange widgets") { longPress(); true }
                }
                .pointerInput(longPress) {
                    detectTapGestures(onLongPress = {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        longPress()
                    })
                }
        } else {
            Modifier.semantics(mergeDescendants = true) {}
        }
        // The card's color: the one chosen for it on its settings page, else its own ([WidgetColors.drawn]).
        // The parts hidden there ([WidgetParts]): the icon leaves the description, the name the body.
        val chosenColor = LocalChosenColor.current
        val hidden = LocalHiddenParts.current
        val battery = LocalBattery.current != null
        val shown = remember(spec, chosenColor, battery, hidden) {
            spec?.let {
                WidgetParts.applied(
                    WidgetColors.recolored(it, WidgetColors.drawn(it.base.color, chosenColor, battery)),
                    hidden
                )
            }
        }
        val named = title.takeUnless { WidgetParts.NAME in hidden }
        // A built-in card's look of its own ([body]) is drawn on a Plain card.
        val look = if (body != null) WidgetLook.PLAIN else drawnLook(LocalWidgetLook.current, shown, note, footer)
        val colors = lookColors(look, roleColor(shown?.base))
        // In the Shape look, the card's shape at this size: the one chosen for it, else the one its
        // script names, else its own ([cardShapeOf]). The settings page and the toolbar show the same.
        val chosenShape = LocalChosenShape.current
        val shape = remember(look, shown, chosenShape, battery, widthDp, heightDp) {
            cardShapeOf(look, shown, chosenShape, battery, widthDp, heightDp)
        }
        // The Shape look in a square box: the card is one Material shape, and the body sits in it.
        val polygon = if (shape != null && squareBox(widthDp, heightDp)) cardPolygon(shown, shape) else null
        // Split's tiles and Shape's shape are the card: the frame itself draws nothing there.
        val bare = look == WidgetLook.SPLIT || polygon != null
        val side = min(maxWidth, maxHeight)
        Surface(
            color = if (bare) Color.Transparent else colors.card,
            contentColor = colors.content,
            shape = if (bare) RectangleShape else RoundedCornerShape(if (box == WidgetSizes.Box.SMALL) 24.dp else 28.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("widget-card"),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(press)
                    .semantics { contentDescription = description }) {
                // A stat's scene is the card's ground, under its body ([StatScene]).
                if (shown is WidgetSpec.Stat && note == null && polygon == null) StatScene(shown, look, colors.card)
                // The bodies inside draw in the look the card has, which may be plainer than the one chosen.
                CompositionLocalProvider(
                    LocalCardLongPress provides onLongClick,
                    LocalWidgetLook provides look,
                    LocalCardShape provides shape,
                    LocalCardColor provides shown?.base?.color
                ) {
                    if (body != null && note == null && shown != null) {
                        body(box, onTap)
                    } else if (polygon != null && shown != null) {
                        ShapedCard(named, shown, polygon, side, box, onTap, stamp)
                    } else {
                        // Bold, Split and Shape have no line for the content's age: they leave it out, and TalkBack still says it.
                        CardLayout(
                            named,
                            shown,
                            note,
                            failure,
                            footer.takeUnless { look.untitled() },
                            box,
                            onTap,
                            stamp,
                            look,
                            colors
                        )
                    }
                }
            }
        }
    }
}

/**
 * The look [chosen] for a card, as it can be drawn. A look shows only on content: a card with a
 * [note] (loading, failed, off) or a failed run ("Failed 10:42") has that to say first, and draws
 * as Plain, or Tonal when that is the choice. Old content keeps the look: a card whose
 * content counts as old after a minute (Quick toggles) would change its look on most visits, until
 * its run fills it again. A look the kind doesn't offer draws Plain.
 */
private fun drawnLook(
    chosen: WidgetLook,
    spec: WidgetSpec?,
    note: CardNote?,
    footer: WidgetWords.Footer?
): WidgetLook = when {
    chosen == WidgetLook.PLAIN || chosen == WidgetLook.TONAL -> chosen
    note != null || spec == null || footer?.failed == true -> WidgetLook.PLAIN
    // A look this kind doesn't offer draws Plain ([WidgetLook.offeredFor]).
    else -> chosen.drawnFor(WidgetJson.kindOf(spec))
}

/**
 * A card that is one Material shape ([cardPolygon]), [side] across, centered in its box. The shape
 * is filled with the role's container; a single progress has an empty clover, which its body fills
 * up to the level. The body draws a few short lines in it ([LocalCardShaped]), with the title from
 * [LocalUntitled]. The shape clips the body, so a fill keeps to it.
 * - A new shape morphs from the one before and settles ([rememberSettle]): the battery's, which
 *   changes with its level ([batteryPolygon]), another kind's, or one chosen for the card ([LocalCardShape]).
 * - A countdown and a habit's week draw their own shape, which changes with what they show
 *   ([carriesShape]). The frame then draws none and cuts nothing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapedCard(
    title: String?,
    spec: WidgetSpec,
    polygon: RoundedPolygon,
    side: Dp,
    box: WidgetSizes.Box,
    onTap: ((WidgetTap) -> Unit)?,
    stamp: Long
) {
    val fill =
        if (spec is WidgetSpec.Progress) MaterialTheme.colorScheme.surfaceContainerHighest else lookColors(
            WidgetLook.SHAPE,
            roleColor(spec.base)
        ).shapeFill
    val own = carriesShape(spec)
    val settle = rememberSettle(batteryPolygon(spec) ?: polygon)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // A little smaller than the box: the shape's points stay clear of the next card.
        Box(
            Modifier
                .size(side - 8.dp)
                .then(if (own) Modifier else Modifier.settledShape(settle, fill))
        ) {
            CompositionLocalProvider(LocalCardShaped provides true, LocalUntitled provides title) {
                KindBody(spec, box, onTap, stamp)
            }
        }
    }
}

/**
 * The content and the footer, placed for [box]. No card has a title line: the content names the
 * card small where it needs to ([LocalUntitled]), as the looks without one have always done.
 * - Large: the content, and under it the foot line ([FootLine]): the script's note and the age of
 *   the content, or "Failed 10:42".
 * - Wide and small: the content alone. There is no room for the foot line; a screen reader still
 *   hears the note and the age.
 * - A cropped picture, large or wide, covers the whole card ([BleedImageCard]).
 */
@Composable
private fun CardLayout(
    title: String?,
    spec: WidgetSpec?,
    note: CardNote?,
    failure: String?,
    footer: WidgetWords.Footer?,
    box: WidgetSizes.Box,
    onTap: ((WidgetTap) -> Unit)?,
    stamp: Long,
    look: WidgetLook,
    colors: LookColors,
) {
    // A cropped picture covers the card. With a note (loading, failed) the card has its usual frame. Shape cuts it to a shape.
    if (spec is WidgetSpec.Image && spec.fit != "fit" && note == null && box != WidgetSizes.Box.SMALL && look != WidgetLook.SHAPE) {
        BleedImageCard(title, spec, footer, box)
        return
    }
    if (look.untitled()) {
        UntitledLayout(title, spec, note, failure, box, onTap, stamp, look)
        return
    }
    when (box) {
        WidgetSizes.Box.LARGE -> Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                CompositionLocalProvider(LocalUntitled provides title) {
                    CardBody(
                        spec,
                        note,
                        failure,
                        box,
                        onTap,
                        stamp
                    )
                }
            }
            FootLine(spec?.base?.takeIf { note == null }, footer, colors.muted)
        }

        WidgetSizes.Box.WIDE -> Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            CompositionLocalProvider(LocalUntitled provides title) {
                CardBody(
                    spec,
                    note,
                    failure,
                    box,
                    onTap,
                    stamp
                )
            }
        }
        // No room for a foot line: the content alone. A screen reader still hears the note and the age.
        WidgetSizes.Box.SMALL -> Box(
            Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            CardBody(
                spec,
                note,
                failure,
                box,
                onTap,
                stamp
            )
        }
    }
}

/**
 * A large card's last line: the script's note ([WidgetBase.note], "Done today", in the card's
 * color), then the content's age or "Failed 10:42" ([FooterLine]) in [muted]. Nothing without either.
 */
@Composable
private fun FootLine(base: WidgetBase?, footer: WidgetWords.Footer?, muted: Color) {
    val note = base?.note
    if (note == null && footer == null) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.labelMedium,
                color = roleColor(base),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        if (footer != null) FooterLine(
            footer,
            Modifier.padding(start = if (note != null) 8.dp else 0.dp),
            muted
        )
    }
}

/**
 * A Bold, Split or Shape card outside a square box. As no card has a title line, the body has the
 * title ([LocalUntitled]) and places it as small text where it needs to, and draws "+3 more" itself.
 * - Split: no room around the body. Its tiles reach the card's edges.
 * - Shape in a 1-row box: 8 dp around, so the main value's shape is nearly as tall as the card.
 * - The others: the room of a large Plain card.
 */
@Composable
private fun UntitledLayout(
    title: String?,
    spec: WidgetSpec?,
    note: CardNote?,
    failure: String?,
    box: WidgetSizes.Box,
    onTap: ((WidgetTap) -> Unit)?,
    stamp: Long,
    look: WidgetLook,
) {
    val room = when {
        look == WidgetLook.SPLIT -> PaddingValues(0.dp)
        // Poster's value reaches the card's edges, which crop it ([PosterCard]).
        look == WidgetLook.POSTER -> PaddingValues(0.dp)
        box == WidgetSizes.Box.SMALL -> PaddingValues(8.dp)
        look == WidgetLook.SHAPE && box == WidgetSizes.Box.WIDE -> PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 16.dp,
            bottom = 8.dp
        )

        box == WidgetSizes.Box.WIDE -> PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        else -> PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    }
    Box(Modifier.fillMaxSize()) {
        // Bold's progress: the card fills from the bottom up to the level, in the role's container.
        // A gauge fills in its value's zone, and stages to how far along the path they are.
        if (look == WidgetLook.BOLD && spec is WidgetSpec.Progress && spec.items.isEmpty()) {
            val level = ProgressStyles.level(spec)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(level)
                    .background(roleContainer(progressTint(spec)))
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(room)
        ) {
            CompositionLocalProvider(LocalUntitled provides title) {
                CardBody(
                    spec,
                    note,
                    failure,
                    box,
                    onTap,
                    stamp
                )
            }
        }
    }
}

/**
 * A picture that covers the whole card ([WidgetSpec.Image] with `fit = "crop"`, large or wide).
 * The card's own corners clip it. A chip over its lower start holds what the title line holds on
 * other cards: the caption (large only) over the title, and the footer. So "Failed 10:42" still
 * shows. Without the picture the body says its `alt` text, and the chip stays.
 */
@Composable
private fun BleedImageCard(
    title: String?,
    spec: WidgetSpec.Image,
    footer: WidgetWords.Footer?,
    box: WidgetSizes.Box
) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val caption = spec.caption.takeIf { box == WidgetSizes.Box.LARGE }
    Box(Modifier.fillMaxSize()) {
        ImageBody(spec, box, bleed = true)
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surfaceContainerHigh.copy(alpha = 0.92f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            caption?.let {
                Text(
                    cardText(it),
                    style = type.labelLargeEmphasized,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (title != null) Text(
                    title,
                    style = type.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (footer != null) FooterLine(footer, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/**
 * The card's small line: the age of its content, or "Failed 10:42". A failure is in the error
 * color, and its words say it too. An age is in the card's quiet text [color].
 */
@Composable
private fun FooterLine(
    footer: WidgetWords.Footer,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(
        footer.text,
        style = MaterialTheme.typography.labelSmall,
        color = if (footer.failed) MaterialTheme.colorScheme.error else color,
        maxLines = 1,
        modifier = modifier,
    )
}

/** The content, or what the card says without any. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CardBody(
    spec: WidgetSpec?,
    note: CardNote?,
    failure: String?,
    box: WidgetSizes.Box,
    onTap: ((WidgetTap) -> Unit)?,
    stamp: Long
) {
    val small = box == WidgetSizes.Box.SMALL
    when (note) {
        CardNote.LOADING -> Named(small) {
            // The shape changes form for as long as it is drawn. So it is drawn only while the card can be seen.
            // The card's description says "loading", so the shape itself is no item for a screen reader.
            if (cardSeen()) LoadingIndicator(
                Modifier
                    .size(if (box == WidgetSizes.Box.LARGE) 48.dp else 36.dp)
                    .clearAndSetSemantics {})
        }

        CardNote.FAILED -> NoteLine(failure ?: WidgetWords.NOTHING, small)
        CardNote.NOTHING -> NoteLine(WidgetWords.NOTHING, small)
        CardNote.UNAVAILABLE -> NoteLine(WidgetWords.UNAVAILABLE, small)
        CardNote.REMOVED -> NoteLine(WidgetWords.REMOVED, small)
        CardNote.OFF -> NoteLine(WidgetWords.OFF, small)
        CardNote.NO_WIDGET -> NoteLine(WidgetWords.NO_WIDGET, small)
        null -> if (spec != null) KindBody(spec, box, onTap, stamp)

        else -> {}
    }
}

/** What a card says in place of its content ([QuietLine]), under its name ([Named]). */
@Composable
private fun NoteLine(text: String, small: Boolean) {
    Named(small) { QuietLine(text, small) }
}

/**
 * [content], in the middle of the card, under the card's name ([CardName]) where there is room: no
 * title line says which card is loading or failed. A small card has the content alone.
 */
@Composable
private fun Named(small: Boolean, content: @Composable () -> Unit) = SplitWhole {
    if (small || LocalUntitled.current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    } else {
        Column(Modifier.fillMaxSize()) {
            CardName()
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) { content() }
        }
    }
}

/** A tap on a card whose description has no action: the view model reads it as "refresh". */
private val NO_TAP = WidgetTap()

/**
 * A tap on a card whose script can't fill it ([note], from [scriptNote]). A refresh would do nothing there.
 * - The script is gone: nothing fills the card again. As for a widget that is unavailable, a tap
 *   opens the screen where the widget is removed.
 * - The script is off, or has no `@widget` line: a tap opens the Scripts screen, where it is
 *   switched on or edited.
 */
private fun noteTap(note: CardNote, actions: WidgetCardActions): () -> Unit =
    if (note == CardNote.REMOVED) {
        { actions.longPress() }
    } else {
        { actions.openScripts() }
    }

/** What a screen reader says [noteTap] does. */
private fun noteTapLabel(note: CardNote): String =
    if (note == CardNote.REMOVED) "arrange widgets" else "open scripts"

/** How long a card with nothing stored shows the loading shape. After that it says "Nothing to show": no spinner turns for ever. */
private const val LOADING_MS = 20_000L

/** How often a card in view looks whether its content has grown old. */
private const val LOOK_AGAIN_MS = 60_000L

/** The shortest `refresh` of a description, in seconds, as for a status line. */
private const val MIN_REFRESH_S = 10

/**
 * Draws placed widget [w], which Kolahal draws itself ([PlacedWidget.source]), [height] tall.
 * - A script's widget draws what the script stored with `widget.show` ([WidgetStates]). The main
 *   process never waits for a script: the card comes from stored state, and a run fills it later.
 * - The built-in Battery and network widget draws the device's status, live ([DeviceStatus]).
 * - Every other built-in widget this version knows draws Kolahal's own data, live ([OwnWidget]):
 *   todos, reminders, notes. It reads them from [feed] ([WidgetFeed.own]).
 * - Any other source says "widget unavailable": a built-in widget that this version does not have,
 *   from a newer Kolahal's backup.
 * - [actions] takes taps, the long-press and refresh requests. With null the card takes no touches
 *   and asks for no runs: this is the Widgets screen.
 * - [feed] is what the view model knows of the scripts. A card reads its script from there, and
 *   never from the database.
 * It brings its own [GuiTheme], because the widgets page is drawn in the terminal's styles.
 */
@Composable
internal fun SourceWidget(
    w: PlacedWidget,
    height: Dp,
    actions: WidgetCardActions?,
    feed: WidgetFeed
) {
    // Which widget of which script: a script can have several on the page ([WidgetRef]).
    val ref = WidgetSource.scriptRef(w.source)
    val builtin = WidgetSource.builtinId(w.source)
    // Every card inside draws in the widget's own look ([LocalWidgetLook]). A built-in card draws
    // one it doesn't offer as Plain, as its Look row shows: Shape stored on Todos is Plain there.
    val look = if (builtin != null) w.look.drawnFor(WidgetSource.builtinKind(builtin), builtin) else w.look
    GuiTheme {
        CompositionLocalProvider(
            LocalWidgetLook provides look,
            LocalChosenShape provides w.shape,
            LocalChosenColor provides w.color,
            LocalHiddenParts provides w.hidden
        ) {
            if (ref != null) {
                ScriptWidget(ref, height, actions, feed)
            } else if (builtin == WidgetSource.BATTERY) {
                BatteryWidget(height, actions)
            } else if (builtin != null && OwnCards.draws(builtin)) {
                OwnWidget(builtin, height, actions, feed.own)
            } else {
                val what = w.source?.let(WidgetSource::label).orEmpty()
                // Nothing to tap here, so a tap opens the screen where the widget can be removed.
                val arrange: (() -> Unit)? =
                    remember(actions) { actions?.let { a -> { a.longPress() } } }
                WidgetCard(
                    title = what,
                    height = height,
                    spec = null,
                    note = CardNote.UNAVAILABLE,
                    description = "$what: ${WidgetWords.UNAVAILABLE}",
                    onClick = arrange,
                    onClickLabel = "arrange widgets",
                    onLongClick = arrange,
                )
            }
        }
    }
}

/**
 * What a card knows of its script, from the view model's [WidgetFeed].
 * - [read] is false until the view model has read the scripts.
 * - With [read], a null [script] means that no script has the name.
 */
private data class Known(val read: Boolean, val script: WidgetScript?)

/**
 * [name]'s script as [feed] has it. The feed changes when any script's title, switch or `@widget`
 * line changes. A card is drawn again only when what it knows of its own script changes.
 */
@Composable
private fun knownScript(feed: WidgetFeed, name: String): Known {
    val changes = remember(feed, name) {
        feed.scripts.map { all -> Known(all != null, all?.get(name)) }.distinctUntilChanged()
    }
    val known by changes.collectAsStateWithLifecycle(
        initialValue = remember(
            feed,
            name
        ) { feed.scripts.value.let { Known(it != null, it?.get(name)) } })
    return known
}

/**
 * The card of one widget of a script: [ref] says which script, and which of its widgets.
 * - It draws what the script stored for this widget with `widget.show`, and reads that again only
 *   when it changes. Everything it keeps and asks for is by the widget's key ([WidgetRef.key]), so
 *   two widgets of one script each have their own content, runs and taps.
 * - While it can be seen ([cardSeen]), it asks for a quiet run when its content is old, or as often
 *   as its `refresh` says.
 * - A card whose script is gone, off or without its `@widget` line says so ([scriptNote]). It asks
 *   for no runs. A tap on it opens the Scripts screen, or the Widgets screen when the script is gone.
 * - After a run that a switch stopped ([com.mtwabpin.kolahal.widgets.WidgetState.switchOff]), a
 *   tap that would run the script opens the Scripts screen.
 * - A description without a title has the script's title. A further widget's then has its id too:
 *   "Weather 2".
 */
@Composable
private fun ScriptWidget(
    ref: WidgetRef,
    height: Dp,
    actions: WidgetCardActions?,
    feed: WidgetFeed
) {
    val key = ref.key
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // It starts with the state read last time (WidgetStates.lastRead): a card drawn again shows in its
    // look at once, not as a loading card that then changes.
    var state by remember(key) { mutableStateOf(WidgetStates.lastRead(key)) }
    // The state is read once, and again when this widget's own number moves ([WidgetStates.versionOf]):
    // another widget's change reads nothing here. Only while home is on screen, and off the main thread.
    LaunchedEffect(key, lifecycle) {
        var seen: Int? = null
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            WidgetStates.versionOf(key).collectLatest { version ->
                // Home came back and the number did not move: the state in use is still right.
                if (version == seen) return@collectLatest
                val read = withContext(Dispatchers.IO) { WidgetStates.read(context, key) }
                seen = version
                // An equal state keeps the one in use, so the card is not drawn again.
                if (read != state) state = read
            }
        }
    }
    // The script's @title, for a description without a title of its own. The view model has read it.
    val known = knownScript(feed, ref.script)
    // The script is gone or off, or its code has lost the `@widget` line: the card says so, and asks
    // for no runs. Without the line a run stores nothing, so the card would ask again at every view.
    val missing = scriptNote(known.read, known.script)
    // A run is asked for only once the scripts are read and the script can run.
    val runnable = known.read && missing == null
    val shown = LocalWidgetsShown.current
    // The card can be seen: the page is in view and the card is on screen ([cardSeen]).
    val visible = cardSeen()
    if (actions != null && shown && runnable) {
        // The card came into view (the page did, home came back with the page in view, or the page
        // was scrolled to it): run the script once if it never filled the widget, or if its content
        // is old. A card below the fold waits until it is scrolled into view. A script that is
        // switched on again starts this again, so its card is filled at once.
        if (visible) LaunchedEffect(key, actions, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var asked = -1L
                snapshotFlow { state }.collectLatest { s ->
                    if (s == null) return@collectLatest
                    // One request for each state. A run that fails or shows nothing moves the state's
                    // time, and that state is then not old (WidgetAge.due).
                    val last = WidgetAge.last(s.setAt, s.triedAt)
                    while (last != asked) {
                        if (WidgetAge.due(
                                s.setAt,
                                s.triedAt,
                                s.spec?.base?.staleAfter,
                                System.currentTimeMillis()
                            )
                        ) {
                            asked = last
                            actions.refresh(key)
                        } else {
                            // Content can grow old while the card shows.
                            delay(LOOK_AGAIN_MS)
                        }
                    }
                }
            }
        }
        // `refresh` in the description: run again that often, only while the card can be seen. A card
        // that comes back into view after that time runs at once.
        val every = state?.spec?.base?.refresh
        val lastRun =
            rememberUpdatedState(state?.let { WidgetAge.last(it.setAt, it.triedAt) } ?: 0L)
        if (every != null && visible) LaunchedEffect(key, actions, lifecycle, every) {
            val period = every.coerceAtLeast(MIN_REFRESH_S) * 1000L
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    // A card with no run yet is being filled: its first run is on its way. A clock set back
                    // counts as no time passed, so the card waits one period, not the jump as well.
                    val since = lastRun.value.let {
                        if (it == 0L) 0L else (System.currentTimeMillis() - it).coerceIn(
                            0L,
                            period
                        )
                    }
                    if (since < period) delay(period - since)
                    actions.refresh(key)
                    // A run takes a while to end, and a request can be dropped: wait a period in any case.
                    delay(period)
                }
            }
        }
    }

    // What is stored counts only while the script can fill the card. Kolahal forgets it when a script goes or is switched.
    val s = state.takeIf { missing == null }
    val spec = s?.spec
    // Nothing stored and no run ended yet: the widget was just placed, and its first run is on its way.
    val fresh = s != null && !s.hasContent && s.setAt == 0L && s.triedAt == 0L
    var gaveUp by remember(key) { mutableStateOf(false) }
    // The wait starts when the card can be seen, as its first run does.
    LaunchedEffect(key, fresh, visible) {
        if (!fresh) {
            // The next first run gets its own wait: a script that was off and is on again.
            gaveUp = false
        } else if (visible && !gaveUp) {
            delay(LOADING_MS)
            gaveUp = true
        }
    }
    // The failed run's own words, for a card with nothing else to show. Without "tap to edit": a tap
    // here refreshes, or opens Scripts.
    val failure = remember(s?.error) { WidgetWords.cardError(s?.error) }
    // A switch that is off for the script stopped the last run, so a run asked for by a tap would
    // stop the same way. A tap that would run the script opens the Scripts screen, where the switch
    // is. A tap never turns the switch on itself. A link of the card's own still opens.
    val toScripts = s?.switchOff == true && spec?.base?.tap?.url == null
    val note = when {
        missing != null -> missing
        s == null -> CardNote.WAITING
        spec != null -> null
        // Something is stored, and it can't be read.
        s.hasContent -> CardNote.UNAVAILABLE
        failure != null -> CardNote.FAILED
        fresh && !gaveUp -> CardNote.LOADING
        else -> CardNote.NOTHING
    }
    val title = spec?.base?.title ?: ref.titled(known.script?.title ?: ref.script)
    // The minute's tick, from the header's clock: content grows old, and a countdown's words change.
    val (minute, zoneChanges) = rememberNow()
    val zone = remember(zoneChanges) { ZoneId.systemDefault() }
    val clock24 = rememberClock24()
    val footer = remember(s, minute, zone, clock24) {
        s?.let {
            WidgetWords.footer(
                it.hasContent,
                WidgetAge.saysAge(
                    it.setAt,
                    it.spec?.base?.staleAfter,
                    it.spec is WidgetSpec.Toggles,
                    minute.time
                ),
                it.setAt,
                it.triedAt,
                it.error != null,
                minute.time,
                zone,
                clock24
            )
        }
    }
    // Only the words of a kind that tells the time change with it: a countdown, a clock, an agenda.
    val now = if (WidgetWords.timed(spec)) minute.time / 1000 else 0L
    // A clock and an agenda say a time of day. A screen reader hears it in the form the card writes it.
    val description = remember(title, spec, note, failure, footer, now, zone, clock24) {
        val content = when {
            spec != null -> WidgetWords.description(title, spec, now, zone, clock24)
            note == CardNote.LOADING -> "$title: loading"
            note == CardNote.FAILED -> "$title: $failure"
            note == CardNote.NOTHING -> "$title: ${WidgetWords.NOTHING}"
            note == CardNote.UNAVAILABLE -> "$title: ${WidgetWords.UNAVAILABLE}"
            note == CardNote.REMOVED -> "$title: ${WidgetWords.REMOVED}"
            note == CardNote.OFF -> "$title: ${WidgetWords.OFF}"
            note == CardNote.NO_WIDGET -> "$title: ${WidgetWords.NO_WIDGET}"
            else -> title
        }
        if (footer == null) content else "$content. ${footer.said}"
    }
    val tap = spec?.base?.tap
    val onClick: (() -> Unit)? = remember(actions, key, tap, missing, toScripts) {
        actions?.let { a ->
            when {
                missing != null -> noteTap(missing, a)
                toScripts -> {
                    { a.openScripts() }
                }

                else -> {
                    { a.tap(key, tap ?: NO_TAP) }
                }
            }
        }
    }
    val onLongClick: (() -> Unit)? = remember(actions) { actions?.let { a -> { a.longPress() } } }
    val onTap: ((WidgetTap) -> Unit)? =
        remember(actions, key) { actions?.let { a -> { t: WidgetTap -> a.tap(key, t) } } }
    CompositionLocalProvider(LocalContentSetAt provides (s?.setAt ?: 0L)) {
        WidgetCard(
            title = title,
            height = height,
            spec = spec,
            note = note,
            description = description,
            modifier = Modifier.testTag("widget:$key"),
            failure = failure,
            footer = footer,
            onClick = onClick,
            // With no action of its own, a tap on the card refreshes it.
            onClickLabel = when {
                missing != null -> noteTapLabel(missing)
                toScripts -> "open scripts"
                tap == null -> "refresh"
                else -> tap.label
            },
            onLongClick = onLongClick,
            onTap = onTap,
            // The time of the last run that ended, whether it showed something or failed. A switch that
            // waits for its script stops waiting when this moves.
            stamp = s?.let { WidgetAge.last(it.setAt, it.triedAt) } ?: 0L,
        )
    }
}

/** The Battery and network card's pure parts: what it draws and says for the device's [Status]. */
internal object BatteryCards {
    /** At this level and under it, the ring is in the "bad" color, as the header's battery is. */
    const val LOW = 15

    /**
     * The network in words. [network] is [Status.network]: a kind (`wifi`, `cell`, `eth`, `vpn`,
     * `net`), with `?` after it while Android has not found the internet through it, or `offline`.
     * With [short], such a network is "No internet" alone, to fit beside the ring.
     */
    fun network(network: String, short: Boolean = false): String {
        val kind = network.trim().lowercase()
        val word = when (kind.removeSuffix("?")) {
            "wifi" -> "Wi-Fi"
            "cell" -> "Mobile data"
            "eth" -> "Ethernet"
            "vpn" -> "VPN"
            "offline", "" -> "Offline"
            else -> "Online"
        }
        return when {
            !kind.endsWith("?") || word == "Offline" -> word
            short -> "No internet"
            else -> "$word, no internet"
        }
    }

    /** The level, 0 to 100. Null when the phone does not say. */
    private fun level(status: Status): Int? = status.batteryPct?.coerceIn(0, 100)

    /**
     * The card's title says whether the phone is on power, because a line beside the ring has no room
     * for that and the network: "Charging", "Charged" at 100%, or "Battery".
     */
    fun title(status: Status): String = when {
        !status.charging -> "Battery"
        (level(status) ?: 0) >= 100 -> "Charged"
        else -> "Charging"
    }

    /**
     * The card: the level as a ring with the percent, and the network as its label, with its icon.
     * - The ring stands still, also while charging. A wave would say "working".
     * - The color says what a 1×1 card has no words for: good while on power, bad when low.
     */
    fun spec(status: Status): WidgetSpec.Progress {
        val level = level(status)
        val color = when {
            level == null -> StyledText.ColorRole.MUTED
            status.charging -> StyledText.ColorRole.GOOD
            level <= LOW -> StyledText.ColorRole.BAD
            else -> null
        }
        return WidgetSpec.Progress(
            base = WidgetBase(title = title(status), color = color),
            value = (level ?: 0).toFloat(),
            max = 100f,
            text = level?.let { "$it%" } ?: "–",
            label = network(status.network, short = true),
            // The network's icon before its name, as the header has it. A network with no internet keeps its icon.
            labelIcon = networkIcon(status.network.trim().lowercase().removeSuffix("?")),
            // A ring in every box. Without this a wide box draws a bar.
            style = "ring",
        )
    }

    /** The whole card for a screen reader: "Battery: 82%, charging, Wi-Fi". */
    fun said(status: Status): String {
        val level = level(status)
        val power = when {
            !status.charging -> null
            (level ?: 0) >= 100 -> "charged"
            else -> "charging"
        }
        return "Battery: " + listOfNotNull(level?.let { "$it%" } ?: "level unknown",
            power,
            network(status.network)).joinToString(", ")
    }
}

/**
 * The built-in Battery and network card. Kolahal fills it from [DeviceStatus], the flow the header
 * reads, so it is right when you look and needs no script and no run. A tap opens Android's battery
 * screen, as the header's battery does.
 */
@Composable
private fun BatteryWidget(height: Dp, actions: WidgetCardActions?) {
    val context = LocalContext.current.applicationContext
    val device = remember(context) { DeviceStatus.get(context) }
    val status by device.status.collectAsStateWithLifecycle()
    // The next alarm is in the status too. The card does not show it, so it is no reason to draw again.
    val shown = remember(
        status.batteryPct,
        status.charging,
        status.network
    ) { status.copy(nextAlarm = null) }
    val spec = remember(shown) { BatteryCards.spec(shown) }
    val description = remember(shown) { BatteryCards.said(shown) }
    val onClick: (() -> Unit)? = remember(actions) { actions?.let { a -> { a.openBattery() } } }
    val onLongClick: (() -> Unit)? = remember(actions) { actions?.let { a -> { a.longPress() } } }
    // The Shape look gives the battery a shape by its level (batteryPolygon).
    CompositionLocalProvider(LocalBattery provides shown) {
        WidgetCard(
            title = BatteryCards.title(shown),
            height = height,
            spec = spec,
            note = null,
            description = description,
            modifier = Modifier.testTag("widget:${WidgetSource.builtin(WidgetSource.BATTERY)}"),
            onClick = onClick,
            onClickLabel = "battery settings",
            onLongClick = onLongClick,
        )
    }
}

/**
 * A card on the widgets page, which says whether some of it is on screen ([LocalCardOnScreen]).
 * The page scrolls: a card below the fold neither moves nor refreshes ([cardSeen]).
 */
@Composable
internal fun OnScreenCard(content: @Composable () -> Unit) {
    var onScreen by remember { mutableStateOf(false) }
    Box(Modifier.onVisibilityChanged(minFractionVisible = 0f) { onScreen = it }) {
        CompositionLocalProvider(LocalCardOnScreen provides onScreen, content = content)
    }
}
