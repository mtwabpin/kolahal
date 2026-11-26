/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.ui.IconLabel
import com.mtwabpin.kolahal.widgets.MediaArt
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import kotlinx.coroutines.delay

// ---- media ----------------------------------------------------------------------------------

private val PREV = WidgetTap(args = WidgetWords.MEDIA_PREV)
private val TOGGLE = WidgetTap(args = WidgetWords.MEDIA_TOGGLE)
private val NEXT = WidgetTap(args = WidgetWords.MEDIA_NEXT)
private val RESUME = WidgetTap(args = WidgetWords.MEDIA_RESUME)

/** What play or pause hands over: `toggle`, or `resume` with nothing playing ([WidgetSpec.Media.lastPlayed]). */
private fun playTap(spec: WidgetSpec.Media): WidgetTap = if (spec.lastPlayed) RESUME else TOGGLE

/** How many of the three buttons a box has ([WidgetWords.mediaButtons]). With nothing playing, Play alone. */
private fun buttonCount(spec: WidgetSpec.Media, widthDp: Int, besideText: Boolean): Int =
    if (spec.lastPlayed) 1 else WidgetWords.mediaButtons(widthDp, besideText)

/** In a large box, the bar needs this much height, and the two times under it this much. */
private val MEDIA_BAR_FROM = 104.dp
private val MEDIA_TIMES_FROM = 122.dp

/** Over the track's cover on play or pause ([ArtFace]): dark enough for the white icon on any picture. */
internal val ART_SCRIM = Color.Black.copy(alpha = 0.35f)

/**
 * Whether the placed widget being drawn has album art as background ([PlacedWidget.artBackground],
 * [MediaArt]). [SourceWidget] provides it.
 */
val LocalArtBackground = staticCompositionLocalOf { false }

/** True inside a media card whose ground is the track's cover ([MediaArtGround]): its texts and tiles take the colors that read over it ([mediaColors]). */
private val LocalOverArt = staticCompositionLocalOf { false }

/** The tag of the cover drawn as the card's ground ([MediaArtGround]), for tests. */
internal const val MEDIA_ART_GROUND_TAG = "media-art-ground"

/**
 * The colors of a media card in [look] with the color [role], as [lookColors] has them. Over the
 * track's cover ([LocalOverArt]), behind its scrim ([artScrim]):
 * - The quieter texts (the artist, the times, "Last played") take the text color: the scrim keeps
 *   the main text readable, not a dimmer one.
 * - A tile is solid, as it looks on the card: Tonal's previous and next, which are a tint of the
 *   text color, would otherwise show the picture through them.
 */
@Composable
private fun mediaColors(
    look: WidgetLook = LocalWidgetLook.current,
    role: Color = MaterialTheme.colorScheme.primary
): LookColors {
    val looks = lookColors(look, role)
    if (!LocalOverArt.current) return looks
    return looks.copy(muted = looks.content, tile = looks.tile.compositeOver(looks.card))
}

/**
 * The scrim over the track's cover, in [color] (the card's, or Split's tile's), as a stat's scene
 * has it ([sceneScrim]): the card's own text colors read over the picture as over the card.
 * - Small: play or pause alone, on a ground of its own: the scene's lighter end, [SCRIM_END], all over.
 * - Wide: the scene's own, strong at the start where the track is, lighter toward the buttons at the end.
 * - Large: the track's words, the bar and its times reach across the card: the strong part, [SCRIM_START], all over.
 */
@Composable
private fun artScrim(color: Color, box: WidgetSizes.Box): Brush {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember(color, box, rtl) {
        when (box) {
            WidgetSizes.Box.SMALL -> SolidColor(color.copy(alpha = SCRIM_END))
            WidgetSizes.Box.WIDE -> sceneScrim(color, rtl)
            WidgetSizes.Box.LARGE -> SolidColor(color.copy(alpha = SCRIM_START))
        }
    }
}

/**
 * The track's cover as the card's ground, with album art as background ([MediaArt.ground]): cropped
 * to fill, never stretched, under a scrim in the card's color [card] ([artScrim]). The card's frame
 * draws it under the body ([WidgetCard]), so it reaches the card's edges and its corners cut it.
 * - Split: under all the tiles, cut to the card's outer corners. Each tile with words takes the
 *   scrim over its own part ([SplitMedia]); the buttons are tiles of their own.
 * - Nothing without a cover: the card is as it is with the choice off.
 * - The picture says nothing to a screen reader: the card's description says the track.
 * The bitmap is the one the card has, wrapped once for it, never scaled or copied.
 */
@Composable
internal fun MediaArtGround(spec: WidgetSpec.Media, look: WidgetLook, box: WidgetSizes.Box, card: Color) {
    val cover = spec.cover ?: return
    if (!MediaArt.ground(LocalArtBackground.current, cover = true, look, box)) return
    val image = remember(cover) { cover.asImageBitmap() }
    val split = look == WidgetLook.SPLIT
    Image(
        image,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .then(if (split) Modifier.clip(SplitCorners.inColumn(0, 1)) else Modifier)
            .testTag(MEDIA_ART_GROUND_TAG),
        contentScale = ContentScale.Crop,
    )
    if (!split) Box(
        Modifier
            .fillMaxSize()
            .background(artScrim(card, box))
    )
}

/** A bar that moves the track is this tall ([SeekBar]), so a finger finds it. */
private val SEEK_HEIGHT = 48.dp

/**
 * The slim slider ([MediaBar]), where a card has no room for [SEEK_HEIGHT]: the bar's place and a
 * little room each side of it, 10 dp more than the bar it replaces. Only this band takes touches.
 */
private val SEEK_SLIM = 20.dp

/** The slim slider's handle, which fits its band. */
private val SEEK_SLIM_HANDLE = DpSize(4.dp, 14.dp)

/**
 * How far under its band the slim slider takes touches: into the card's padding, or over the times, where
 * nothing else takes one. With [SEEK_SLIM] that is 32 dp for a finger.
 */
private val SEEK_SLIM_REACH = 12.dp

/**
 * The room a slider needs where it is drawn: its height, less the pixel or two that rounding can
 * take from a box. A 4×2 card without a cover fits it exactly.
 */
private val SEEK_ROOM = SEEK_HEIGHT - 2.dp

/** The slider's handle: Material's thin bar, short enough to keep clear of the times under it. */
private val SEEK_HANDLE = DpSize(4.dp, 20.dp)

/** The times go along the slider's foot while their line is this tall at most: under its handle, clear of it. */
private val SEEK_FOOT = 18.dp

/** How long a place chosen on the bar stays when the player says nothing back, in milliseconds. */
private const val SEEK_HOLD_MS = 3_000L

/**
 * `media`: what is playing, with its controls. Only Kolahal makes this kind.
 * - Small: play or pause alone.
 * - Wide: the title in bold, the artist and the app, with a bar for the position under them, and the
 *   buttons at the end. A narrow box has fewer buttons, never smaller ones ([WidgetWords.mediaButtons]).
 *   Play or pause is wider than the others where there is room.
 * - Large: the same in rows, with the position and the length under the bar.
 * - The track's cover, when the player gives one, fills play or pause ([ArtFace]).
 * - Where the player can seek, the bar is a slider wherever it shows: [SeekBar] where the card has
 *   room for its 48 dp, else a slim one in the bar's place ([MediaBar]).
 * - With nothing playing ([WidgetSpec.Media.lastPlayed]): "Last played" over the last track, and
 *   Play alone, which hands `resume` over. A large card has Play with its word, at the start.
 * - The three buttons hand `prev`, `toggle` and `next` to [onTap]. A tap elsewhere is the card's.
 * - The position moves every second only while something plays, its length is known and the page
 *   is in view. While something plays, the bar is a wave that moves on every frame while the card is
 *   seen ([LevelBar]); paused, it is flat.
 * - Tonal: play or pause in the card's color, the other two on the look's tile.
 * - Split and Shape have layouts of their own ([SplitMedia], [ShapeMedia]).
 * - With album art as background ([MediaArt]) and a cover, the cover is the card's ground
 *   ([MediaArtGround]) and play or pause has none of its own; the texts and tiles take the colors
 *   that read over it ([mediaColors]).
 */
@Composable
internal fun MediaBody(
    spec: WidgetSpec.Media,
    box: WidgetSizes.Box,
    onTap: ((WidgetTap) -> Unit)?
) {
    val overArt = MediaArt.ground(LocalArtBackground.current, spec.cover != null, LocalWidgetLook.current, box)
    // The cover is behind the whole card: the same picture on play or pause too would read badly.
    val shown = remember(spec, overArt) { if (overArt) spec.copy(cover = null) else spec }
    CompositionLocalProvider(LocalOverArt provides overArt) { MediaContent(shown, box, onTap) }
}

/** [MediaBody]'s card, as it is drawn: a cover in [spec] is play or pause's face. */
@Composable
private fun MediaContent(
    spec: WidgetSpec.Media,
    box: WidgetSizes.Box,
    onTap: ((WidgetTap) -> Unit)?
) {
    val color = roleColor(spec.base)
    when (LocalWidgetLook.current) {
        WidgetLook.SPLIT -> return SplitMedia(spec, box, color, onTap)
        WidgetLook.SHAPE -> return ShapeMedia(spec, box, color, onTap)
        else -> {}
    }
    if (box == WidgetSizes.Box.SMALL) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MediaButtons(
                1,
                WidgetWords.mediaPlayDp(maxWidth.value.toInt(), besideText = false, count = 1),
                spec,
                onTap,
                color
            )
        }
        return
    }
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val muted = mediaColors().muted
    val title = WidgetWords.mediaTitle(spec)
    val line = WidgetWords.mediaLine(spec)
    val now = rememberMillis(seconds = spec.playing && spec.duration > 0)
    val seek = rememberSeek(spec, onTap)
    // The bar moves in steps, so it is drawn again only when it has moved one.
    val step by remember(spec) {
        derivedStateOf {
            WidgetWords.mediaFraction(spec, now.value)?.let(WidgetWords::barStep)
        }
    }
    // How tall the texts are with the phone's font size, for what fits under them.
    val labelHeight = linesHeight(type.labelSmall)
    val lineHeight = if (line.isNotEmpty()) linesHeight(type.bodySmall) else 0.dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fraction = step
        val width = maxWidth.value.toInt()
        val tall = maxHeight
        if (box == WidgetSizes.Box.WIDE) {
            val count = buttonCount(spec, width, besideText = true)
            val playDp = WidgetWords.mediaPlayDp(width, besideText = true, count)
            val texts = linesHeight(type.titleSmall) + lineHeight
            // A card one row tall has no room for the 48 dp slider under the texts, so it has the slim one.
            val seeks =
                seek != null && fraction != null && onTap != null && tall >= texts + SEEK_ROOM
            // The buttons are measured first, so the texts get what is left and are cut, not the buttons.
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (spec.lastPlayed && tall >= labelHeight + texts) LastPlayed(muted)
                    Text(
                        title,
                        style = type.titleSmallEmphasized,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (line.isNotEmpty()) Text(
                        line,
                        style = type.bodySmall,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (seek != null && fraction != null && onTap != null && seeks) SeekBar(
                        spec,
                        fraction,
                        seek,
                        color,
                        onTap
                    )
                    // The last thing in the texts' column, so the card's bottom padding under it takes its touches too.
                    else if (fraction != null && tall >= TWO_LINES) MediaBar(
                        spec,
                        fraction,
                        seek,
                        color,
                        onTap,
                        top = 4.dp
                    )
                }
                Spacer(Modifier.width(WidgetWords.CONTROL_GAP_DP.dp))
                MediaButtons(count, playDp, spec, onTap, color)
            }
        } else {
            // "Last played" over the track while it leaves room for Play under them, also with a large font.
            val label =
                spec.lastPlayed && tall >= labelHeight + linesHeight(type.titleMedium) + lineHeight + CONTROL_MIN + 4.dp
            val texts =
                (if (label) labelHeight else 0.dp) + linesHeight(type.titleMedium) + lineHeight
            // The slider over the buttons, each 48 dp tall, only where both fit under the track. The
            // times go along the slider's foot, or under it with a large font.
            val seeks =
                seek != null && fraction != null && onTap != null && tall >= texts + SEEK_ROOM + CONTROL_MIN
            val timesAlong = labelHeight <= SEEK_FOOT
            val timesUnder =
                seeks && !timesAlong && tall >= texts + SEEK_ROOM + labelHeight + CONTROL_MIN
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (label) LastPlayed(muted)
                    Text(
                        title,
                        style = type.titleMediumEmphasized,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (line.isNotEmpty()) Text(
                        line,
                        style = type.bodySmall,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (seek != null && fraction != null && onTap != null && seeks) {
                        val times: @Composable () -> Unit = { MediaTimes(spec, now, seek) }
                        Spacer(Modifier.weight(1f))
                        SeekBar(
                            spec,
                            fraction,
                            seek,
                            color,
                            onTap,
                            under = times.takeIf { timesAlong })
                        if (timesUnder) times()
                    } else if (fraction != null && tall >= MEDIA_BAR_FROM) {
                        Spacer(Modifier.weight(1f))
                        MediaBar(spec, fraction, seek, color, onTap)
                        // The slim slider is 10 dp taller than the bar: the times need that much more.
                        if (tall >= MEDIA_TIMES_FROM + (if (seek != null) SEEK_SLIM - 10.dp else 0.dp)) MediaTimes(
                            spec,
                            now,
                            seek
                        )
                    }
                }
                if (spec.lastPlayed) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) { PlayWithWord(onTap, color) }
                } else {
                    // Under a slider its own 48 dp are the room above them.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = if (seeks) 0.dp else 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val count = WidgetWords.mediaButtons(width, besideText = false)
                        MediaButtons(
                            count,
                            WidgetWords.mediaPlayDp(width, besideText = false, count),
                            spec,
                            onTap,
                            color
                        )
                    }
                }
            }
        }
    }
}

/**
 * How tall these lines of text are with the phone's font size: for what fits under them.
 * The clock has its own, `linesDp` in WidgetClock.kt, which counts 1.25 × the font size for a style with no line height.
 */
@Composable
private fun linesHeight(vararg styles: TextStyle): Dp {
    val density = LocalDensity.current
    return styles.fold(0.dp) { sum, style -> sum + with(density) { (if (style.lineHeight.isSp) style.lineHeight else 20.sp).toDp() } }
}

/**
 * The track's cover ([WidgetSpec.Media.cover]) as the face of play or pause ([MediaButton]): the
 * whole button, which cuts it to its shape as it cuts any content, darkened ([ART_SCRIM]) under its
 * [icon], which is white. The card's description says the track, so the picture is no item for a
 * screen reader.
 */
@Composable
private fun ArtFace(cover: Bitmap, icon: @Composable () -> Unit) {
    val image = remember(cover) { cover.asImageBitmap() }
    Box(
        Modifier
            .fillMaxSize()
            .testTag("media-cover"), contentAlignment = Alignment.Center
    ) {
        Image(
            image,
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier
                .matchParentSize()
                .background(ART_SCRIM)
        )
        CompositionLocalProvider(LocalContentColor provides Color.White) { icon() }
    }
}

/** "Last played", small over the last track, with nothing playing ([WidgetSpec.Media.lastPlayed]). */
@Composable
private fun LastPlayed(color: Color) {
    Text(
        WidgetWords.LAST_PLAYED,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * [count] of the three buttons, in a row: play or pause alone, then with next, then with previous
 * too. Play or pause is [playDp] wide ([WidgetWords.mediaPlayDp]), and hands over [playTap]. [role]
 * is the card's color, for the looks that use it.
 */
@Composable
private fun MediaButtons(
    count: Int,
    playDp: Int,
    spec: WidgetSpec.Media,
    onTap: ((WidgetTap) -> Unit)?,
    role: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (count >= 3) MediaButton(
            PREV,
            R.drawable.ic_skip_next,
            "Previous",
            onTap,
            role,
            mirrored = true
        )
        MediaButton(
            playTap(spec),
            playIcon(spec.playing),
            playWord(spec.playing),
            onTap,
            role,
            widthDp = playDp,
            strong = true,
            art = spec.cover
        )
        if (count >= 2) MediaButton(NEXT, R.drawable.ic_skip_next, "Next", onTap, role)
    }
}

private fun playIcon(playing: Boolean) = if (playing) R.drawable.ic_pause else R.drawable.ic_play
private fun playWord(playing: Boolean) = if (playing) "Pause" else "Play"

/**
 * One of the media buttons, 48 dp tall and [widthDp] wide, or as [size] says. [said] is its name
 * for a screen reader.
 * - [strong]: the filled button, for play or pause. Its corners are squarer: a wide one is not a pill.
 *   The others are tonal and round.
 * - [mirrored]: the icon is turned to point back. "Previous" is the "next" icon, mirrored.
 * - With a null [onTap] it takes no tap, and looks as it does on the page.
 * - Tonal and Split: play or pause is in the card's [role] color, the others on the look's tile.
 *   Shape: the others are on the theme's highest surface. A [tile] shape makes it one of Split's tiles.
 * - [art]: the track's cover, the face of a [strong] button ([ArtFace]).
 */
@Composable
private fun MediaButton(
    tap: WidgetTap,
    icon: Int,
    said: String,
    onTap: ((WidgetTap) -> Unit)?,
    role: Color,
    widthDp: Int = WidgetWords.CONTROL_DP,
    strong: Boolean = false,
    mirrored: Boolean = false,
    size: Modifier? = null,
    tile: Shape? = null,
    art: Bitmap? = null,
) {
    val source = remember { MutableInteractionSource() }
    val press = cardPress(source)
    val onClick = { press.click { onTap?.invoke(tap) } }
    val modifier = size ?: Modifier.size(width = widthDp.dp, height = WidgetWords.CONTROL_DP.dp)
    val glyph: @Composable () -> Unit = {
        Icon(
            painterResource(icon),
            said,
            if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier
        )
    }
    val drawn: @Composable () -> Unit = if (strong && art != null) {
        { ArtFace(art, glyph) }
    } else glyph
    val look = LocalWidgetLook.current
    val looks = mediaColors(look, role)
    val own = look == WidgetLook.TONAL || look == WidgetLook.SPLIT || look == WidgetLook.SHAPE
    if (strong) {
        val colors = IconButtonDefaults.filledIconButtonColors().let {
            if (own) it.copy(
                containerColor = looks.accent,
                contentColor = looks.onAccent
            ) else it
        }
            .let {
                it.copy(
                    disabledContainerColor = it.containerColor,
                    disabledContentColor = it.contentColor
                )
            }
        val shapes = IconButtonDefaults.shapes(shape = tile ?: IconButtonDefaults.mediumSquareShape)
        FilledIconButton(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = onTap != null,
            colors = colors,
            interactionSource = source,
            content = drawn
        )
    } else {
        val container =
            if (look == WidgetLook.SHAPE) MaterialTheme.colorScheme.surfaceContainerHighest else looks.tile
        val colors = IconButtonDefaults.filledTonalIconButtonColors().let {
            if (own) it.copy(
                containerColor = container,
                contentColor = looks.content
            ) else it
        }
            .let {
                it.copy(
                    disabledContainerColor = it.containerColor,
                    disabledContentColor = it.contentColor
                )
            }
        val shapes =
            if (tile != null) IconButtonDefaults.shapes(shape = tile) else IconButtonDefaults.shapes()
        FilledTonalIconButton(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = onTap != null,
            colors = colors,
            interactionSource = source,
            content = drawn
        )
    }
}

/**
 * Play with its word, at the foot of a large card with nothing playing: it hands `resume` over.
 * It is filled as play or pause is, in the card's [role] color on Tonal. A press held on it opens
 * what the card's long-press opens, as on the other buttons ([cardPress]).
 */
@Composable
private fun PlayWithWord(onTap: ((WidgetTap) -> Unit)?, role: Color) {
    val source = remember { MutableInteractionSource() }
    val press = cardPress(source)
    val look = LocalWidgetLook.current
    val looks = mediaColors(look, role)
    val colors = ButtonDefaults.buttonColors().let {
        if (look == WidgetLook.TONAL) it.copy(
            containerColor = looks.accent,
            contentColor = looks.onAccent
        ) else it
    }
        .let {
            it.copy(
                disabledContainerColor = it.containerColor,
                disabledContentColor = it.contentColor
            )
        }
    Button(
        onClick = { press.click { onTap?.invoke(RESUME) } },
        shapes = ButtonDefaults.shapes(),
        enabled = onTap != null,
        colors = colors,
        contentPadding = ButtonDefaults.contentPaddingFor(
            ButtonDefaults.MinHeight,
            hasStartIcon = true
        ),
        interactionSource = source,
    ) {
        IconLabel(R.drawable.ic_play, "Play")
    }
}

/**
 * A media bar that moves the track ([SeekBar]): its slider, and the place a finger chose, kept
 * until the player says where the track is. The times on the card read it too, so they follow
 * the finger.
 */
@Stable
private class Seek {
    val slider = SliderState()

    /** Where the last drag or tap put the track, from 0 to 1, until the player answers. */
    var held by mutableStateOf<Float?>(null)

    /** The place a finger has chosen, from 0 to 1, or null while the card shows where the player is. */
    val chosen: Float? get() = if (slider.isDragging) slider.value else held
}

/**
 * The [Seek] of a media card whose bar moves the track ([WidgetWords.mediaSeeks]). Null for a bar
 * that only shows the position, and on the Widgets screen, where [onTap] is null. A new state from
 * the player ends a held place, and so does a player that says nothing back in [SEEK_HOLD_MS].
 */
@Composable
private fun rememberSeek(spec: WidgetSpec.Media, onTap: ((WidgetTap) -> Unit)?): Seek? {
    if (onTap == null || !WidgetWords.mediaSeeks(spec)) return null
    val seek = remember { Seek() }
    LaunchedEffect(spec) { seek.held = null }
    val held = seek.held
    if (held != null) LaunchedEffect(held) {
        delay(SEEK_HOLD_MS)
        seek.held = null
    }
    return seek
}

/**
 * The media bar as a slider, where the player can seek: its track is the card's bar ([LevelBar]),
 * a wave while the track plays, with Material's thin handle at the position.
 * - It is [SEEK_HEIGHT] tall, so a finger finds it. A drag that starts on it is its own: the page
 *   doesn't turn, and the card takes no tap.
 * - It follows a finger. On release it hands `seek <ms>` to [onTap] ([WidgetWords.mediaSeek]), and
 *   stays at that place until the player answers ([Seek.held]). A tap on it moves the track there too.
 * - A screen reader hears a slider at "1:12 of 3:05", the place the times show, which it can move.
 * - [under] is drawn along its foot, under the bar: the times.
 * - [slim]: [SEEK_SLIM] tall with a shorter handle, in the bar's place on a card with no room for
 *   48 dp ([MediaBar]). Upwards its touches end exactly at its band, so the texts over it stay the
 *   card's; downwards they reach [SEEK_SLIM_REACH] further.
 */
@Composable
private fun SeekBar(
    spec: WidgetSpec.Media,
    fraction: Float,
    seek: Seek,
    role: Color,
    onTap: (WidgetTap) -> Unit,
    modifier: Modifier = Modifier,
    under: (@Composable () -> Unit)? = null,
    slim: Boolean = false,
) {
    val slider = seek.slider
    // Where the player is, or the place chosen, while no finger moves it. Material's own slider sets its value so.
    if (!slider.isDragging) slider.value = seek.held ?: fraction
    val source = remember { MutableInteractionSource() }
    // A press held on the bar opens what the card's long-press opens, as on the card's buttons, and then seeks nothing.
    val press = cardPress(source)
    // For a screen reader: the place the times under the bar show, the player's or the finger's. The bar's own
    // value is rounded to its step, which on a long track is seconds off. Read only by the semantics.
    val now = rememberMillis(seconds = spec.playing && spec.duration > 0)
    val colors = SliderDefaults.colors(thumbColor = role)
    val duration = spec.duration
    // The slider itself is only as tall as its handle, and Compose widens its touches to 48 dp around
    // it: over the texts above a slim one, which are the card's. So a slim one sits in a clip that
    // starts at its band's top and ends its reach under the band. A clip under 48 dp is widened too,
    // unless its own target is exact ([ExactTouches]); the slider in it keeps Compose's 48 dp.
    val config = LocalViewConfiguration.current
    val touches = remember(config, slim) { if (slim) ExactTouches(config) else config }
    // A slim band keeps its height in the card: only the clip is taller, downwards.
    val band = if (slim) Modifier
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            layout(placeable.width, SEEK_SLIM.roundToPx()) { placeable.place(0, 0) }
        }
        .height(SEEK_SLIM + SEEK_SLIM_REACH)
        .clipToBounds() else Modifier.height(SEEK_HEIGHT)
    CompositionLocalProvider(LocalViewConfiguration provides touches) {
        Box(
            modifier
                .fillMaxWidth()
                .then(band)
        ) {
            CompositionLocalProvider(LocalViewConfiguration provides config) {
                Box(Modifier.fillMaxWidth().height(if (slim) SEEK_SLIM else SEEK_HEIGHT)) {
                    Slider(
                        state = slider,
                        onValueChange = { if (!press.held) slider.value = it },
                        onValueChangeFinished = {
                            press.click {
                                seek.held = slider.value
                                onTap(
                                    WidgetTap(
                                        args = WidgetWords.mediaSeek(
                                            WidgetWords.seekTarget(
                                                slider.value,
                                                duration
                                            )
                                        )
                                    )
                                )
                            }
                        },
                        // An item of its own inside the card, which says where the track is.
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics(mergeDescendants = true) {
                                stateDescription = "${
                                    WidgetWords.mediaTime(
                                        positionAt(
                                            spec,
                                            now.value,
                                            seek
                                        )
                                    )
                                } of ${WidgetWords.mediaTime(duration)}"
                            },
                        colors = colors,
                        interactionSource = source,
                        thumb = {
                            SliderDefaults.Thumb(
                                source,
                                isVertical = false,
                                colors = colors,
                                thumbSize = if (slim) SEEK_SLIM_HANDLE else SEEK_HANDLE
                            )
                        },
                        track = { LevelBar({ slider.value }, moving = spec.playing, role) },
                    )
                }
            }
            if (under != null) Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
            ) { under() }
        }
    }
}

/** [config], with no touch target larger than a node's own bounds: for the clip around the slim slider ([SeekBar]). */
private class ExactTouches(config: ViewConfiguration) : ViewConfiguration by config {
    override val minimumTouchTargetSize: DpSize get() = DpSize.Zero
}

/**
 * The media bar where a card has no room for [SeekBar]'s 48 dp. Where the player can seek ([seek]),
 * a slim slider in the bar's place, so the bar seeks wherever it shows; else the bar, [top] under
 * what is over it.
 */
@Composable
private fun MediaBar(
    spec: WidgetSpec.Media,
    fraction: Float,
    seek: Seek?,
    role: Color,
    onTap: ((WidgetTap) -> Unit)?,
    top: Dp = 0.dp
) {
    if (seek != null && onTap != null) SeekBar(spec, fraction, seek, role, onTap, slim = true)
    else Box(
        Modifier
            .padding(top = top)
            .testTag("media-bar")
    ) {
        LevelBar(
            fraction,
            moving = spec.playing,
            role
        )
    }
}

/** Where the track is at [now], in milliseconds, or where a finger put it on the bar ([Seek.chosen]). */
private fun positionAt(spec: WidgetSpec.Media, now: Long, seek: Seek?): Long =
    seek?.chosen?.let { WidgetWords.seekTarget(it, spec.duration) } ?: WidgetWords.mediaPosition(
        spec,
        now
    )

/** Where the song is and how long it is, under a large card's bar: "1:05" at the start, "5:12" at the end. */
@Composable
private fun MediaTimes(spec: WidgetSpec.Media, now: State<Long>, seek: Seek?) {
    Row(Modifier.fillMaxWidth()) {
        MediaPosition(spec, now, seek)
        Spacer(Modifier.weight(1f))
        Text(
            WidgetWords.mediaTime(spec.duration),
            style = MaterialTheme.typography.labelSmall,
            color = mediaColors().muted,
            maxLines = 1
        )
    }
}

/** Where the song is: "1:05". It reads the time itself, so a tick draws this text again, not the card. */
@Composable
private fun MediaPosition(spec: WidgetSpec.Media, now: State<Long>, seek: Seek?) {
    Text(
        WidgetWords.mediaTime(positionAt(spec, now.value, seek)),
        style = MaterialTheme.typography.labelSmall,
        color = mediaColors().muted,
        maxLines = 1
    )
}

/** Where the song is, of how long it is: "2:41 / 5:12", or the position alone when the length is not known. It reads the time itself. */
@Composable
private fun MediaPositionOf(spec: WidgetSpec.Media, now: State<Long>, seek: Seek?, color: Color) {
    val at = WidgetWords.mediaTime(positionAt(spec, now.value, seek))
    val text = if (spec.duration > 0) "$at / ${WidgetWords.mediaTime(spec.duration)}" else at
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
        color = color,
        maxLines = 1,
        softWrap = false
    )
}

/** The height of Split's row of media buttons, as the mockup has it. */
private val SPLIT_MEDIA_ROW = 56.dp

/**
 * Split's media card.
 * - Large: a tile with the card's title and the position at its top, the song and the artist, and
 *   the bar at its foot; under it a row of three tiles: previous, play or pause (wider, in the
 *   card's [role] color) and next.
 * - Wide: the song's tile at the start, with the bar under the texts, and the button tiles beside
 *   it, as many as fit.
 * - Small: play or pause is the whole card.
 * - The track's cover fills play or pause's tile ([ArtFace]). Where the player can seek, the bar is
 *   a slider: down to the tile's edge where the tile has room ([SeekBar]), else slim ([MediaBar]).
 * - With nothing playing, "Last played" is where the position is, or over the song, and Play is
 *   the one button tile.
 * - Over the track's cover ([MediaArtGround]), the tile with the words is a scrim over its part of
 *   the picture ([artScrim]); the button tiles keep their own grounds.
 */
@Composable
private fun SplitMedia(
    spec: WidgetSpec.Media,
    box: WidgetSizes.Box,
    role: Color,
    onTap: ((WidgetTap) -> Unit)?
) {
    val type = MaterialTheme.typography
    val looks = mediaColors(WidgetLook.SPLIT, role)
    val gap = SplitCorners.GAP
    if (box == WidgetSizes.Box.SMALL) {
        MediaButton(
            playTap(spec),
            playIcon(spec.playing),
            playWord(spec.playing),
            onTap,
            role,
            strong = true,
            size = Modifier.fillMaxSize(),
            tile = SplitCorners.inColumn(0, 1),
            art = spec.cover
        )
        return
    }
    // Over the track's cover ([MediaArtGround]), a tile with words is the scrim over its part of the picture.
    val wordsGround: Brush =
        if (LocalOverArt.current) artScrim(looks.tile, box) else SolidColor(looks.tile)
    val title = WidgetWords.mediaTitle(spec)
    val line = WidgetWords.mediaLine(spec)
    val now = rememberMillis(seconds = spec.playing && spec.duration > 0)
    val seek = rememberSeek(spec, onTap)
    val step by remember(spec) {
        derivedStateOf {
            WidgetWords.mediaFraction(spec, now.value)?.let(WidgetWords::barStep)
        }
    }
    val labelHeight = linesHeight(type.labelSmall)
    val lineHeight = if (line.isNotEmpty()) linesHeight(type.bodySmall) else 0.dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fraction = step
        val boxHeight = maxHeight
        if (box == WidgetSizes.Box.WIDE) {
            val count = buttonCount(spec, maxWidth.value.toInt(), besideText = true)
            val tiles = count + 1
            val texts = linesHeight(type.titleSmall) + lineHeight
            // The tile reaches the card's edges: the 48 dp slider fits under the texts in a card made taller than one row, the slim one in one row.
            val seeks =
                seek != null && fraction != null && onTap != null && boxHeight >= texts + SEEK_ROOM + 16.dp
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(SplitCorners.inRow(0, tiles))
                        .background(wordsGround)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        if (spec.lastPlayed && boxHeight >= labelHeight + texts + 16.dp) LastPlayed(
                            looks.muted
                        )
                        Text(
                            title,
                            style = type.titleSmallEmphasized,
                            color = looks.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (line.isNotEmpty()) Text(
                            line,
                            style = type.bodySmall,
                            color = looks.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (seek != null && fraction != null && onTap != null && seeks) SeekBar(
                            spec,
                            fraction,
                            seek,
                            role,
                            onTap
                        )
                        else if (fraction != null && boxHeight >= TWO_LINES + 16.dp) MediaBar(
                            spec,
                            fraction,
                            seek,
                            role,
                            onTap,
                            top = 6.dp
                        )
                    }
                }
                var at = 1
                if (count >= 3) MediaButton(
                    PREV,
                    R.drawable.ic_skip_next,
                    "Previous",
                    onTap,
                    role,
                    mirrored = true,
                    size = Modifier
                        .width(56.dp)
                        .fillMaxHeight(),
                    tile = SplitCorners.inRow(at++, tiles)
                )
                MediaButton(
                    playTap(spec),
                    playIcon(spec.playing),
                    playWord(spec.playing),
                    onTap,
                    role,
                    strong = true,
                    size = Modifier
                        .width(72.dp)
                        .fillMaxHeight(),
                    tile = SplitCorners.inRow(at++, tiles),
                    art = spec.cover
                )
                if (count >= 2) MediaButton(
                    NEXT,
                    R.drawable.ic_skip_next,
                    "Next",
                    onTap,
                    role,
                    size = Modifier
                        .width(56.dp)
                        .fillMaxHeight(),
                    tile = SplitCorners.inRow(at, tiles)
                )
            }
            return@BoxWithConstraints
        }
        val count = buttonCount(spec, maxWidth.value.toInt(), besideText = false)
        // The top line, the song 4 dp under it, and the artist.
        val texts = labelHeight + 4.dp + linesHeight(type.titleMedium) + lineHeight
        // The slider takes the tile's bottom padding, 14 dp, into its 48.
        val seeks =
            seek != null && fraction != null && onTap != null && boxHeight - SPLIT_MEDIA_ROW - gap - 14.dp >= texts + SEEK_ROOM
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(
                        SplitCorners.tile(
                            topStart = true,
                            topEnd = true,
                            bottomEnd = false,
                            bottomStart = false
                        )
                    )
                    .background(wordsGround)
                    .padding(
                        start = 18.dp,
                        top = 14.dp,
                        end = 18.dp,
                        bottom = if (seeks) 0.dp else 14.dp
                    ),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LocalUntitled.current?.let {
                            Text(
                                it,
                                style = type.labelSmallEmphasized,
                                color = looks.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        } ?: Spacer(Modifier.weight(1f))
                        if (spec.lastPlayed) LastPlayed(looks.muted)
                        else if (spec.duration > 0 || spec.playing) MediaPositionOf(
                            spec,
                            now,
                            seek,
                            looks.muted
                        )
                    }
                    Text(
                        title,
                        style = type.titleMediumEmphasized,
                        color = looks.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (line.isNotEmpty()) Text(
                        line,
                        style = type.bodySmall,
                        color = looks.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.weight(1f))
                if (seek != null && fraction != null && onTap != null && seeks) SeekBar(
                    spec,
                    fraction,
                    seek,
                    role,
                    onTap
                )
                else if (fraction != null) MediaBar(spec, fraction, seek, role, onTap)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(SPLIT_MEDIA_ROW),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                val bottom = { first: Boolean, last: Boolean ->
                    SplitCorners.tile(
                        topStart = false,
                        topEnd = false,
                        bottomEnd = last,
                        bottomStart = first
                    )
                }
                if (count >= 3) MediaButton(
                    PREV,
                    R.drawable.ic_skip_next,
                    "Previous",
                    onTap,
                    role,
                    mirrored = true,
                    size = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    tile = bottom(true, false)
                )
                MediaButton(
                    playTap(spec),
                    playIcon(spec.playing),
                    playWord(spec.playing),
                    onTap,
                    role,
                    strong = true,
                    size = Modifier
                        .weight(1.6f)
                        .fillMaxHeight(),
                    tile = bottom(count < 3, count < 2),
                    art = spec.cover
                )
                if (count >= 2) MediaButton(
                    NEXT,
                    R.drawable.ic_skip_next,
                    "Next",
                    onTap,
                    role,
                    size = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    tile = bottom(false, true)
                )
            }
        }
    }
}

/**
 * Shape's media card: play or pause is a big button in a cookie ([PlayShape]), with the song beside
 * it. A wavy bar shows the position while something plays (its wave moves on every frame while the
 * card is seen, [LevelBar]), a flat one while paused.
 * - Large: the cookie at the start; the card's title, the song, the artist, the bar, and previous
 *   and next with the position beside them. A narrow box has the cookie and the song on top.
 * - Wide: the cookie, the song, and previous and next at the end where they fit.
 * - Small: the cookie alone.
 * - The track's cover fills the cookie. Where the player can seek, the bar is a slider: [SeekBar]
 *   where the box has room for it, else slim ([MediaBar]).
 * - With nothing playing, "Last played" is over the song, and the cookie is the one button.
 */
@Composable
private fun ShapeMedia(
    spec: WidgetSpec.Media,
    box: WidgetSizes.Box,
    role: Color,
    onTap: ((WidgetTap) -> Unit)?
) {
    val type = MaterialTheme.typography
    val looks = mediaColors(WidgetLook.SHAPE, role)
    val labelHeight = linesHeight(type.labelSmall)
    val lineHeight =
        if (WidgetWords.mediaLine(spec).isNotEmpty()) linesHeight(type.bodySmall) else 0.dp
    val titleHeight = linesHeight(type.titleSmall)
    val songHeight = linesHeight(type.titleMedium)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        if (box == WidgetSizes.Box.SMALL) {
            PlayShape(
                spec.playing,
                minOf(maxWidth, maxHeight),
                role,
                onTap,
                playTap(spec),
                spec.cover
            )
            return@BoxWithConstraints
        }
        val title = WidgetWords.mediaTitle(spec)
        val line = WidgetWords.mediaLine(spec)
        val now = rememberMillis(seconds = spec.playing && spec.duration > 0)
        val seek = rememberSeek(spec, onTap)
        val step by remember(spec) {
            derivedStateOf {
                WidgetWords.mediaFraction(spec, now.value)?.let(WidgetWords::barStep)
            }
        }
        val fraction = step
        val bar: @Composable (Dp) -> Unit =
            { top -> if (fraction != null) MediaBar(spec, fraction, seek, role, onTap, top) }
        val prevNext: @Composable () -> Unit = {
            MediaButton(PREV, R.drawable.ic_skip_next, "Previous", onTap, role, mirrored = true)
            MediaButton(NEXT, R.drawable.ic_skip_next, "Next", onTap, role)
        }
        // With nothing playing, "Last played" takes the card's title's place over the song.
        val untitled = if (spec.lastPlayed) WidgetWords.LAST_PLAYED else LocalUntitled.current
        val songTitle = type.titleMediumEmphasized
        when {
            box == WidgetSizes.Box.WIDE -> Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val side = minOf(boxHeight, 72.dp)
                // Previous and next need 100 dp beside the cookie and at least 96 dp of song. Not with nothing playing.
                val both = !spec.lastPlayed && boxWidth >= 72.dp + 96.dp + 124.dp
                // The 48 dp slider fits under the texts in a card made taller than one row, the slim one in one row.
                val seeks =
                    seek != null && fraction != null && onTap != null && boxHeight >= titleHeight + lineHeight + SEEK_ROOM
                PlayShape(spec.playing, side, role, onTap, playTap(spec), spec.cover)
                Column(Modifier.weight(1f)) {
                    if (spec.lastPlayed && boxHeight >= labelHeight + titleHeight + lineHeight) LastPlayed(
                        looks.muted
                    )
                    Text(
                        title,
                        style = type.titleSmallEmphasized,
                        color = looks.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (line.isNotEmpty()) Text(
                        line,
                        style = type.bodySmall,
                        color = looks.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (seek != null && fraction != null && onTap != null && seeks) SeekBar(
                        spec,
                        fraction,
                        seek,
                        role,
                        onTap
                    )
                    else if (boxHeight >= TWO_LINES + 8.dp) bar(6.dp)
                }
                if (both) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { prevNext() }
            }

            maxWidth >= DATED_SIDE -> Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val side = minOf(boxHeight, 104.dp, boxWidth / 3)
                val texts = (if (untitled != null) labelHeight else 0.dp) + songHeight + lineHeight
                // The slider over previous and next, each 48 dp, with 8 dp between them.
                val seeks =
                    seek != null && fraction != null && onTap != null && boxHeight >= texts + SEEK_ROOM + 8.dp + CONTROL_MIN
                PlayShape(spec.playing, side, role, onTap, playTap(spec), spec.cover)
                Column(Modifier.weight(1f)) {
                    Column {
                        if (untitled != null) Text(
                            untitled,
                            style = type.labelSmallEmphasized,
                            color = looks.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            title,
                            style = songTitle,
                            color = looks.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (line.isNotEmpty()) Text(
                            line,
                            style = type.bodySmall,
                            color = looks.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (seek != null && fraction != null && onTap != null && seeks) SeekBar(
                        spec,
                        fraction,
                        seek,
                        role,
                        onTap
                    )
                    else bar(10.dp)
                    if (!spec.lastPlayed) Row(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        prevNext()
                        Spacer(Modifier.weight(1f))
                        if (spec.duration > 0 || spec.playing) MediaPositionOf(
                            spec,
                            now,
                            seek,
                            looks.muted
                        )
                    }
                }
            }

            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                // The song may take two lines beside the 64 dp cookie.
                val head = maxOf(
                    64.dp,
                    (if (spec.lastPlayed) labelHeight else 0.dp) + titleHeight * 2 + lineHeight
                )
                val seeks =
                    seek != null && fraction != null && onTap != null && boxHeight >= head + SEEK_ROOM + CONTROL_MIN
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PlayShape(spec.playing, 64.dp, role, onTap, playTap(spec), spec.cover)
                    Column(Modifier.weight(1f)) {
                        if (spec.lastPlayed) LastPlayed(looks.muted)
                        Text(
                            title,
                            style = type.titleSmallEmphasized,
                            color = looks.content,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (line.isNotEmpty()) Text(
                            line,
                            style = type.bodySmall,
                            color = looks.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (seek != null && fraction != null && onTap != null && seeks) SeekBar(
                    spec,
                    fraction,
                    seek,
                    role,
                    onTap
                ) else bar(0.dp)
                if (!spec.lastPlayed) Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    prevNext()
                    Spacer(Modifier.weight(1f))
                    if (spec.duration > 0 || spec.playing) MediaPositionOf(
                        spec,
                        now,
                        seek,
                        looks.muted
                    )
                }
            }
        }
    }
}

/**
 * Shape's play or pause: a filled button [side] across in the card's [role] color. Playing, it is
 * a cookie; paused, a rounded square. A change morphs one into the other and settles ([settleSpec]),
 * as Material's expressive player does, and only the drawing runs again while it does. Its icon is two fifths of its size.
 * It hands [tap] over: `toggle`, or `resume` with nothing playing. The track's cover, [art], fills
 * the shape, darkened under a white icon, as on the other looks' play or pause ([ArtFace]).
 */
@Composable
private fun PlayShape(
    playing: Boolean,
    side: Dp,
    role: Color,
    onTap: ((WidgetTap) -> Unit)?,
    tap: WidgetTap = TOGGLE,
    art: Bitmap? = null
) {
    val source = remember { MutableInteractionSource() }
    val press = cardPress(source)
    val looks = mediaColors(WidgetLook.SHAPE, role)
    val paused = animateFloatAsState(if (playing) 0f else 1f, settleSpec(), label = "play")
    val side48 = side.coerceAtLeast(CONTROL_MIN)
    val image = remember(art) { art?.asImageBitmap() }
    val content = if (image != null) Color.White else looks.onAccent
    val colors = IconButtonDefaults.filledIconButtonColors().copy(
        containerColor = Color.Transparent,
        contentColor = content,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = content,
    )
    FilledIconButton(
        onClick = { press.click { onTap?.invoke(tap) } },
        shapes = IconButtonDefaults.shapes(shape = CircleShape),
        modifier = Modifier
            .size(side48)
            .morphFill(PLAY_MORPH, looks.accent, image = image, settles = true) { paused.value }
            .then(if (image != null) Modifier.testTag("media-cover") else Modifier),
        enabled = onTap != null,
        colors = colors,
        interactionSource = source,
    ) {
        Icon(painterResource(playIcon(playing)), playWord(playing), Modifier.size(side48 * 0.4f))
    }
}
