package com.mtwabpin.kolahal.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.math.roundToInt

/** A web picture is decoded for the size it shows at ([pictureScale]), and each size has its own memory key ([pictureKey]). */
class LogImagesTest {
    private val log = LogImages.MAX_PIXELS
    private val noLimit = Int.MAX_VALUE

    /** The decoded size of a [w] by [h] picture. */
    private fun decoded(w: Int, h: Int, widthPx: Int, maxHeightPx: Int, maxPixels: Float = log): Pair<Int, Int> {
        val scale = pictureScale(w.toFloat(), h.toFloat(), widthPx, maxHeightPx, maxPixels)
        return (w * scale).roundToInt() to (h * scale).roundToInt()
    }

    @Test
    fun aTallPictureIsDecodedNoTallerThanItShows() {
        // 1000×4000 in a 1080-pixel log, at most 1800 tall: shown at 450×1800, so decoded at that.
        assertEquals(450 to 1800, decoded(1000, 4000, 1080, 1800))
        // Without a greatest height, only the width and the pixel count limit it.
        assertEquals(1000 to 4000, decoded(1000, 4000, 1080, noLimit))
    }

    @Test
    fun aWidePictureIsDecodedForTheLogsWidth() {
        assertEquals(540 to 135, decoded(2000, 500, 540, 1800))
        assertEquals(540 to 135, decoded(2000, 500, 540, noLimit))
    }

    @Test
    fun aSmallPictureIsNotScaledUp() {
        assertEquals(1f, pictureScale(100f, 50f, 1080, 1800, log))
    }

    @Test
    fun thePixelCountStillLimitsIt() {
        // 3000×3000 fits the width and the height, but has more than a card's 1 M pixels.
        assertEquals(1000 to 1000, decoded(3000, 3000, 4000, 4000, 1_000_000f))
        // A log picture never has more than MAX_PIXELS.
        val (w, h) = decoded(2000, 20000, 4000, noLimit)
        assertEquals(log, (w * h).toFloat(), 2_000f * 20f)
    }

    @Test
    fun eachSizeHasItsOwnKey() {
        val url = "https://example.com/a.png"
        // A log picture with no greatest height has the key it always had.
        assertEquals("$url@1080", pictureKey(url, 1080, log, noLimit))
        assertEquals("$url@1080/1000000", pictureKey(url, 1080, 1_000_000f, noLimit))
        assertEquals("$url@1080^1800", pictureKey(url, 1080, log, 1800))
        assertNotEquals(pictureKey(url, 1080, log, 1800), pictureKey(url, 1080, log, 2400))
        assertNotEquals(pictureKey(url, 1080, log, 1800), pictureKey(url, 1080, log, noLimit))
        assertNotEquals(pictureKey(url, 1080, log, 1800), pictureKey(url, 1080, 1_000_000f, 1800))
    }
}
