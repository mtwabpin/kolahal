/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.ui.home.colorOf
import com.mtwabpin.kolahal.widgets.CardCorners
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap

/** The Notes card's Stack look of [s], as a card draws it in place of its text ([WidgetCard]'s `body`). */
internal fun noteStackBody(s: OwnDesigns.NoteStack): OwnBody = { box, onTap -> NoteStackBody(s, box, onTap) }

/** The corners of a sheet of the stack with the cards' own corners. */
private const val SHEET_DP = 18

/** How far in from the card's edge the sheets are: the card's room ([ownRoom]) and the stack's own 4 dp. */
private const val SHEET_INSET_DP = 16

/**
 * The corners of a sheet: [SHEET_DP], or with widgets.corners set, the card's corner less the room
 * around the sheets ([CardCorners.inner]). A sheet is the card in another form: Square is square here too.
 */
@Composable
private fun sheetShape(): Shape = RoundedCornerShape(CardCorners.inner(LocalCardCorners.current, SHEET_INSET_DP, SHEET_DP).dp)

/** How far the sheets behind are turned, in degrees, on a sheet about 160 dp wide; a wider one is turned less. */
private const val TURN_BACK = -8f
private const val TURN_MIDDLE = 5f

/**
 * The Notes card in the Stack look ([com.mtwabpin.kolahal.widgets.WidgetLook.STACK]): the newest
 * note readable on a sheet, on top of a sheet for each other note, two at most, turned a little
 * and tinted. A tap on the card runs `notes`, as in the other looks. + puts `note ` in the prompt
 * and opens the keyboard: a new note in one tap.
 * - 2×2 and larger: the stack, with "3 notes" and + under it.
 * - 4×1 and 2×1: the note with "Tue 24 Nov 18:48 · 3 notes" under it, and + at the end.
 * - 1×1: the stack alone, with the start of the note.
 */
@Composable
private fun NoteStackBody(s: OwnDesigns.NoteStack, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
    val type = MaterialTheme.typography
    val muted = lookColors().muted
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(ownRoom(box))
    ) {
        // Copied first: a Row or Column inside can't read them.
        val width = maxWidth
        val height = maxHeight
        when {
            box == WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
                Stack(
                    s,
                    width,
                    type.titleSmall,
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        OwnDesigns.countWords(s.count),
                        style = type.labelMedium,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    NewNoteShape(40.dp, onTap)
                }
            }

            box == WidgetSizes.Box.SMALL && width < 150.dp ->
                Stack(s, width, type.bodySmall, Modifier.fillMaxSize(), time = false)

            else -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        cardText(s.text),
                        style = if (box == WidgetSizes.Box.WIDE) type.bodyLarge.copy(fontWeight = FontWeight.Medium) else type.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${s.at} · ${OwnDesigns.countWords(s.count)}",
                        style = type.labelMedium,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                NewNoteShape(
                    min(height, if (box == WidgetSizes.Box.WIDE) 56.dp else 44.dp),
                    onTap,
                    Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}

/**
 * The stack, [width] wide: a sheet for each of [OwnDesigns.NoteStack.behind] turned a little, in
 * the accent's and the link's tint, and on top the newest note in [style] with, with [time], when
 * it was written. The sheets are tints of the card, so they read in a light and a dark theme.
 */
@Composable
private fun Stack(
    s: OwnDesigns.NoteStack,
    width: Dp,
    style: TextStyle,
    modifier: Modifier = Modifier,
    time: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val c = lookColors()
    val dark = scheme.surface.luminance() < 0.5f
    // A wide sheet turned as far would stand out of the card at its ends.
    val turn = (160.dp / width).coerceAtMost(1f)
    val sheet = sheetShape()
    Box(modifier.padding(4.dp)) {
        if (s.behind >= 2) Sheet(
            sheet,
            lerp(c.card, colorOf(StyledText.ColorRole.ACCENT), if (dark) 0.16f else 0.2f),
            Modifier
                .matchParentSize()
                .offset(x = (-3).dp)
                .rotate(TURN_BACK * turn)
        )
        if (s.behind >= 1) Sheet(
            sheet,
            lerp(c.card, colorOf(StyledText.ColorRole.LINK), if (dark) 0.16f else 0.2f),
            Modifier
                .matchParentSize()
                .offset(x = 3.dp)
                .rotate(TURN_MIDDLE * turn)
        )
        Column(
            Modifier
                .matchParentSize()
                .shadow(2.dp, sheet)
                .clip(sheet)
                .background(lerp(c.card, scheme.onSurface, if (dark) 0.13f else 0.16f))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                cardText(s.text),
                style = style,
                color = scheme.onSurface,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            if (time) Text(
                s.at,
                style = MaterialTheme.typography.labelMedium,
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** One sheet behind the top one, in [color], cut to [shape]. */
@Composable
private fun Sheet(shape: Shape, color: Color, modifier: Modifier) {
    Box(
        modifier
            .shadow(1.dp, shape)
            .clip(shape)
            .background(color)
    )
}

/**
 * The + that starts a new note: a sun in the card's color's container with + in that color (the
 * accent unless one was chosen, [cardRoleColor]), moved just far enough to read on it
 * ([valueInk]), [side] across. A tap puts `note ` in the prompt ([OwnDesigns.NEW_NOTE]); a
 * long-press arranges the widgets, as on the rest of the card. A screen reader hears "New note".
 * Smaller than 48 dp, its touches still reach 48 dp (Compose's minimum), as [opensList]'s do.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NewNoteShape(side: Dp, onTap: ((WidgetTap) -> Unit)?, modifier: Modifier = Modifier) {
    val role = cardRoleColor()
    Box(
        modifier
            .size(side)
            .clip(MaterialShapes.Sunny.toShape())
            .background(roleContainer(role))
            .then(rowPress(NEW_NOTE_ROW, onTap))
            .semantics { contentDescription = "New note" }
            .testTag("note-new"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_add),
            contentDescription = null,
            tint = valueInk(role, roleContainer(role)),
            modifier = Modifier.size(side * 0.5f)
        )
    }
}

/** The + as a row with a tap, for [rowPress]: a button for a screen reader, labeled with what it does. */
private val NEW_NOTE_ROW = WidgetSpec.Row("New note", tap = OwnDesigns.NEW_NOTE)
