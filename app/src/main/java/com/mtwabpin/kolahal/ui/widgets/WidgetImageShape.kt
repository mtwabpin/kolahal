/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.mtwabpin.kolahal.ui.home.LogImages
import com.mtwabpin.kolahal.ui.home.pictureBackdrop
import com.mtwabpin.kolahal.ui.home.rememberWebImage
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec

/** From this many times as wide as it is tall, a large card has its picture at the start and the words beside it. */
private const val BESIDE_FROM = 1.6f

/**
 * `image` in the Shape look: the picture cut to one of Material's shapes, as a picture in a frame.
 * - Large (2×2): the picture in a cookie, with the caption under it and the card's title under
 *   that. A large card much wider than tall (4×2) has the cookie at the start and the words beside it.
 * - Wide (2×1): the picture in a clover at the start, the caption beside it over the title.
 * - Small: the picture in a cookie alone.
 * - A shape is always covered: the picture loses its edges, also with `fit = "fit"`.
 * - Until the picture is there, and when it fails, the shape is in the card's color with the
 *   card's icon in it, or the words the card says in its place ([WidgetWords.pictureNote]).
 * Home fetches the picture as for the other looks ([ImageBody]). The card's description says its
 * `alt` text.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ShapedImage(spec: WidgetSpec.Image, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SHAPE, roleColor(spec.base))
    val title = LocalUntitled.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        val wide = box == WidgetSizes.Box.WIDE ||
                (box == WidgetSizes.Box.LARGE && maxWidth >= maxHeight * BESIDE_FROM)
        when {
            box == WidgetSizes.Box.SMALL -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PictureInShape(spec, MaterialShapes.Cookie12Sided, minOf(boxWidth, boxHeight), small = true)
            }

            wide -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                val side = minOf(boxHeight, boxWidth / 2)
                PictureInShape(
                    spec,
                    if (box == WidgetSizes.Box.WIDE) MaterialShapes.Clover4Leaf else MaterialShapes.Cookie12Sided,
                    side
                )
                Column(
                    Modifier
                        .padding(start = 14.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    spec.caption?.let {
                        Text(
                            cardText(it),
                            style = type.titleSmall,
                            color = c.content,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    title?.let {
                        Text(
                            it,
                            style = type.bodySmall,
                            color = c.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            else -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    PictureInShape(spec, MaterialShapes.Cookie12Sided, minOf(maxWidth, maxHeight))
                }
                spec.caption?.let {
                    Text(
                        cardText(it),
                        style = type.titleSmall,
                        color = c.content,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                }
                title?.let {
                    Text(
                        it,
                        style = type.bodySmall,
                        color = c.muted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * [spec]'s picture cut to [polygon], [side] across, covering it. Without the picture, the shape in
 * the card's color, with the card's icon in its role's color, or else the words the card says in
 * the picture's place. [small] makes those words smaller.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PictureInShape(
    spec: WidgetSpec.Image,
    polygon: RoundedPolygon,
    side: Dp,
    small: Boolean = false
) {
    val inView = LocalWidgetsShown.current
    val role = roleColor(spec.base)
    val c = lookColors(WidgetLook.SHAPE, role)
    BoxWithConstraints(
        Modifier
            .size(side)
            .clip(polygon.toShape())
            .background(c.shapeFill),
        contentAlignment = Alignment.Center
    ) {
        val size = WidgetWords.pictureSize(constraints.maxWidth, constraints.maxHeight)
        val state = rememberWebImage(
            spec.image,
            size.widthPx,
            size.maxPixels,
            enabled = inView,
            httpsOnly = true
        )
        val picture = state?.getOrNull()
        val icon = spec.base.icon
        when {
            picture != null -> {
                // A see-through picture whose lines barely show on the card: on a backdrop (pictureBackdrop).
                val backdrop = pictureBackdrop(LogImages.inkOf(spec.image), c.shapeFill)
                Image(
                    picture,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (backdrop == null) Modifier else Modifier.background(backdrop))
                        .testTag("widget-picture"),
                )
            }

            icon != null -> WidgetIconText(
                icon,
                MaterialTheme.typography.headlineMedium,
                role,
                size = minOf(side * 0.3f, 40.dp)
            )

            else -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(side / 6)
            ) {
                QuietLine(
                    WidgetWords.pictureNote(
                        failed = state?.isFailure == true,
                        inView = inView,
                        alt = spec.alt
                    ),
                    small = small || side < 120.dp
                )
            }
        }
    }
}
