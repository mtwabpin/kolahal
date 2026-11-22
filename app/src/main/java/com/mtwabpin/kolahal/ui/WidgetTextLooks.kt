/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec

// ---- text ------------------------------------------------------------------------------------

/** `text` in Bold, Split or Shape ([TextBody] draws Plain and Tonal). A text's card is never one shape. */
@Composable
internal fun TextLook(spec: WidgetSpec.Text, box: WidgetSizes.Box, look: WidgetLook) {
    when (look) {
        WidgetLook.BOLD -> BoldTextBody(spec, box)
        WidgetLook.SPLIT -> SplitTextBody(spec, box)
        else -> ShapeTextBody(spec, box)
    }
}

/** Bold's text: the quote large in Bold's narrow type, and its caption (or the title) in a chip of the role's container. */
@Composable
private fun BoldTextBody(spec: WidgetSpec.Text, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val role = roleColor(spec.base)
    val c = lookColors(WidgetLook.BOLD, role)
    val largest = when (box) {
        WidgetSizes.Box.LARGE -> 34.sp
        WidgetSizes.Box.WIDE -> 24.sp
        WidgetSizes.Box.SMALL -> 20.sp
    }
    val style = remember(largest) {
        boldValueStyle(largest, FontWeight.ExtraBold).copy(
            lineHeight = 1.05.em,
            letterSpacing = (-0.01).em,
            fontFeatureSettings = null
        )
    }
    val chip = spec.caption ?: LocalUntitled.current
    Column(Modifier.fillMaxSize()) {
        FittedText(
            cardText(spec.text),
            style,
            largest,
            c.content,
            Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        if (box == WidgetSizes.Box.LARGE && chip != null) {
            Text(
                cardText(chip),
                style = type.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = c.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(roleContainer(role))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** The first letter of [text] on a cookie of the [role] color, [side] across: who a quote is by. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LetterCookie(text: String, role: Color, side: Dp) {
    ShapedBox(MaterialShapes.Cookie9Sided, role, side) {
        Text(
            WidgetWords.badgeLetter(text).orEmpty(),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = lookColors().onAccent,
            maxLines = 1
        )
    }
}

/** Split's text: the text in a big tile, over a tile with its caption (or the title) after its first letter on a cookie. */
@Composable
private fun SplitTextBody(spec: WidgetSpec.Text, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val role = roleColor(spec.base)
    val c = lookColors(WidgetLook.SPLIT, role)
    val title = LocalUntitled.current
    val who = spec.caption ?: title
    val gap = SplitCorners.GAP
    val quote = cardText(spec.text)
    when (box) {
        WidgetSizes.Box.LARGE -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            Tile(
                SplitCorners.inColumn(0, if (who != null) 2 else 1),
                c.tile,
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                FittedText(
                    quote,
                    type.titleLarge.copy(
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    20.sp,
                    c.content,
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                )
            }
            if (who != null) BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                // The title too, at the end, where the tile has room for both.
                val both = spec.caption != null && title != null && maxWidth >= 260.dp
                Tile(SplitCorners.inColumn(1, 2), c.tile, Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(start = 12.dp, end = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LetterCookie(who, role, 30.dp)
                        Text(
                            cardText(who),
                            style = type.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = c.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .weight(1f)
                        )
                        if (both) Text(
                            title,
                            style = type.labelSmall,
                            color = c.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .widthIn(max = 140.dp)
                        )
                    }
                }
            }
        }

        WidgetSizes.Box.WIDE -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            Tile(
                SplitCorners.inColumn(0, if (who != null) 2 else 1),
                c.tile,
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                FittedText(
                    quote,
                    type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    16.sp,
                    c.content,
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            if (who != null) Tile(
                SplitCorners.inColumn(1, 2),
                c.tile,
                Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            ) {
                Text(
                    cardText(who),
                    style = type.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = c.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 14.dp)
                )
            }
        }

        WidgetSizes.Box.SMALL -> Tile(SplitCorners.inColumn(0, 1), c.tile, Modifier.fillMaxSize()) {
            FittedText(
                quote,
                type.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                14.sp,
                c.content,
                Modifier
                    .align(Alignment.Center)
                    .padding(10.dp)
            )
        }
    }
}

/** The card's icon, or else the first letter of its title, in the [role] color on a clover of the role's container. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TextMark(icon: String?, title: String?, role: Color, side: Dp) {
    ShapedBox(MaterialShapes.Clover4Leaf, roleContainer(role), side) {
        if (icon != null) WidgetIconText(
            icon,
            MaterialTheme.typography.titleMedium,
            role,
            size = side * 0.46f
        )
        else Text(
            title?.let(WidgetWords::badgeLetter).orEmpty(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = role
        )
    }
}

/** Shape's text: the card's icon on a clover, the text, and its caption under it. A small card has the text alone. */
@Composable
private fun ShapeTextBody(spec: WidgetSpec.Text, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val role = roleColor(spec.base)
    val c = lookColors(WidgetLook.SHAPE, role)
    val title = LocalUntitled.current
    val quote = cardText(spec.text)
    when (box) {
        WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
            TextMark(spec.base.icon, title, role, 48.dp)
            FittedText(
                quote,
                type.titleLarge.copy(
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold
                ),
                20.sp,
                c.content,
                Modifier
                    .padding(top = 12.dp)
                    .weight(1f)
                    .fillMaxWidth()
            )
            spec.caption?.let {
                Text(
                    cardText(it),
                    style = type.bodySmall,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        WidgetSizes.Box.WIDE -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val side = min(maxHeight, 56.dp)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                TextMark(spec.base.icon, title, role, side)
                Column(
                    Modifier
                        .padding(start = 12.dp)
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    FittedText(
                        quote,
                        type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        16.sp,
                        c.content,
                        Modifier.weight(1f, fill = false)
                    )
                    spec.caption?.let {
                        Text(
                            cardText(it),
                            style = type.labelSmall,
                            color = c.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        WidgetSizes.Box.SMALL -> FittedText(
            quote,
            type.bodySmall,
            13.sp,
            c.content,
            Modifier.fillMaxSize()
        )
    }
}
