package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import com.mtwabpin.kolahal.widgets.CardCorners
import org.junit.Assert.assertEquals
import org.junit.Test

/** Split's tiles take widgets.corners ([SplitCorners]): the card's corners outside, a fitting one inside, none too large for the tile. */
class SplitCornersTest {
    private val density = Density(1f)

    /** The corners of a tile [width] by [height] that touches the card's top start corner only: top start, top end, bottom end, bottom start. */
    private fun corners(chosen: CardCorners?, width: Float, height: Float): List<Float> {
        val shape = SplitCorners.tile(chosen, topStart = true, topEnd = false, bottomEnd = false, bottomStart = false) as CornerBasedShape
        val size = Size(width, height)
        return listOf(shape.topStart, shape.topEnd, shape.bottomEnd, shape.bottomStart).map { it.toPx(size, density) }
    }

    /** With the cards' own, a tile is as it has always been: 28 dp on the card's corner, 8 dp inside. */
    @Test
    fun itsOwnIsAsAlways() {
        assertEquals(listOf(28f, 8f, 8f, 8f), corners(null, 200f, 120f))
        assertEquals(listOf(28f, 8f, 8f, 8f), corners(null, 40f, 40f))
    }

    /** Square is square everywhere; each other step is its corner outside and that less the gap inside, 8 dp at most. */
    @Test
    fun eachStep() {
        assertEquals(listOf(0f, 0f, 0f, 0f), corners(CardCorners.SQUARE, 200f, 120f))
        assertEquals(listOf(8f, 4f, 4f, 4f), corners(CardCorners.SLIGHT, 200f, 120f))
        assertEquals(listOf(16f, 8f, 8f, 8f), corners(CardCorners.ROUNDED, 200f, 120f))
        assertEquals(listOf(40f, 8f, 8f, 8f), corners(CardCorners.VERY, 200f, 120f))
    }

    /** On a small tile Very rounded is half its shorter side at most: a round end, never a corner that swallows it. */
    @Test
    fun aSmallTileCapsIt() {
        assertEquals(listOf(30f, 8f, 8f, 8f), corners(CardCorners.VERY, 200f, 60f))
        assertEquals(listOf(16f, 8f, 8f, 8f), corners(CardCorners.ROUNDED, 200f, 60f))
    }
}
