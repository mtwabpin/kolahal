package com.mtwabpin.kolahal.ui.wallpaper

import com.mtwabpin.kolahal.shader.ShaderFrame
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderParam
import com.mtwabpin.kolahal.shader.ShaderValues
import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** What Lab's pictures work out without Android ([PictureMath]). */
class PictureMathTest {
    private fun meta(name: String) = ShaderMeta.parse(sourceFile("src/main/assets/shaders/$name.agsl").readText())

    private fun look(time: Float = 41.5f, date: FloatArray? = floatArrayOf(2026f, 11f, 27f, 67200f), seed: Float = 17f) = PictureLook(
        colors = mapOf("bg" to 0xFF101010.toInt()), dark = true, params = listOf(5f), time = time, date = date, seed = seed,
        screenW = 1080, cellW = 10f, cellH = 21f, battery = 0.6f, charging = true,
    )

    @Test
    fun theSizesAreTheScreenAnd2KAnd4KAtItsProportions() {
        val sizes = PictureMath.sizes(1080, 2400, maxSide = null)
        assertEquals(listOf("This screen", "2K", "4K"), sizes.map { it.label })
        assertEquals(listOf("1080 × 2400", "1440 × 3200", "2160 × 4800"), sizes.map { it.pixels })
        assertTrue(sizes.all { it.why == null })
        // On its side, the short side is the height.
        assertEquals(listOf("2400 × 1080", "3200 × 1440", "4800 × 2160"), PictureMath.sizes(2400, 1080, null).map { it.pixels })
    }

    @Test
    fun aSizeNoLargerThanTheScreenIsLeftOut() {
        assertEquals(listOf("This screen", "4K"), PictureMath.sizes(1440, 3120, null).map { it.label })
        assertEquals(listOf("This screen"), PictureMath.sizes(2160, 4800, null).map { it.label })
    }

    @Test
    fun aSizeTheGpuCantDrawSaysWhy() {
        val sizes = PictureMath.sizes(1080, 2400, maxSide = 4096)
        assertNull(sizes[0].why)
        assertNull(sizes[1].why)
        assertEquals("This phone draws up to 4096 px a side", sizes[2].why)
        assertEquals("screen", PictureMath.firstSize(sizes)?.id)
        // 16384, as on most phones now: all of them.
        assertTrue(PictureMath.sizes(1080, 2400, 16384).all { it.why == null })
        // A limit at exactly the long side still draws it.
        assertNull(PictureMath.sizes(1080, 2400, 4800)[2].why)
        // None that can be drawn: nothing to pick, and the buttons wait.
        assertNull(PictureMath.firstSize(PictureMath.sizes(1080, 2400, 2000)))
    }

    @Test
    fun theFileIsNamedForTheShaderAndTheMoment() {
        val at = LocalDateTime.of(2026, 11, 27, 19, 30, 5)
        assertEquals("kolahal-pond-20261127-193005.png", PictureMath.fileName("pond", at))
        assertEquals("kolahal-grove-autumn-20261127-193005.png", PictureMath.fileName("grove-autumn", at))
        assertEquals("kolahal-a-b-20261127-193005.png", PictureMath.fileName("a/b", at))
    }

    @Test
    fun aLargerPictureIsALargerCopyNotAFinerGrid() {
        assertEquals(20f to 42f, PictureMath.cell(10f, 21f, screenW = 1080, width = 2160))
        assertEquals(10f to 21f, PictureMath.cell(10f, 21f, screenW = 1080, width = 1080))
        assertEquals(5f to 10.5f, PictureMath.cell(10f, 21f, screenW = 1080, width = 540))
    }

    @Test
    fun theHourIsTheDateUniform() {
        assertArrayEquals(floatArrayOf(2026f, 11f, 27f, 67200f), PictureMath.date(LocalDate.of(2026, 11, 27), 18 * 60 + 40), 0f)
        assertArrayEquals(floatArrayOf(2026f, 11f, 27f, 0f), PictureMath.date(LocalDate.of(2026, 11, 27), -5), 0f)
        assertEquals("18:40", PictureMath.clock(18 * 60 + 40))
        assertEquals("07:05", PictureMath.clock(7 * 60 + 5))
    }

    @Test
    fun aFrameGetsTheMomentTheHourAndTheSeedAndNoTouch() {
        val old = ShaderFrame().apply {
            touchX = 5f; touchY = 6f; touchAge = 0.2f; pressed = true; tiltX = 0.3f; corner = 40f; cutout[1] = 12f; cols = 40
        }
        val f = PictureMath.frame(look(), 2160, 4800, old)
        assertEquals(2160f, f.width)
        assertEquals(4800f, f.height)
        assertEquals(41.5f, f.seconds)
        assertArrayEquals(floatArrayOf(2026f, 11f, 27f, 67200f), f.date, 0f)
        assertEquals(17f, f.seed)
        assertEquals(-1f, f.touchX)
        assertEquals(-1f, f.touchY)
        assertEquals(ShaderFrame.NEVER, f.touchAge)
        assertFalse(f.pressed)
        assertEquals(0f, f.tiltX)
        assertEquals(0f, f.corner)
        assertArrayEquals(FloatArray(4), f.cutout, 0f)
        assertEquals(0, f.cols)
        assertEquals(0.6f, f.battery)
        assertEquals(1f, f.charging)
    }

    @Test
    fun homeAndTheLiveWallpaperGetSeedZeroAndNow() {
        val f = ShaderFrame()
        assertEquals(0f, f.seed)
        assertNull(f.date)
    }

    @Test
    fun aLookMadeAgainWithTheSameChoicesIsTheSame() {
        assertEquals(look(), look())
        assertEquals(look().hashCode(), look().hashCode())
        assertFalse(look() == look(seed = 18f))
        assertFalse(look() == look(date = floatArrayOf(2026f, 11f, 27f, 0f)))
    }

    @Test
    fun aControlShowsOnlyWhereTheSceneHasWhatItNeeds() {
        val pond = meta("pond")
        val dunes = meta("dunes")
        // Pond moves, follows the hour and declares seed; it starts at its @still.
        assertTrue(PictureMath.hasFrame(pond))
        assertTrue(PictureMath.hasHour(pond, listOf(null)))
        assertTrue(PictureMath.hasSeed(pond))
        assertEquals(41.5f, PictureMath.startTime(pond))
        // Dunes stands still: no Frame.
        assertFalse(PictureMath.hasFrame(dunes))
        assertTrue(PictureMath.hasHour(dunes, listOf(null)))
        assertTrue(PictureMath.hasSeed(dunes))
        assertEquals(0f, PictureMath.startTime(dunes))
        // Snowfall follows no hour and has no seed.
        val snow = meta("snowfall")
        assertFalse(PictureMath.hasHour(snow, snow.params.map { null }))
        assertFalse(PictureMath.hasSeed(snow))
        // A parameter that follows the time of day is enough for Time of day.
        assertTrue(PictureMath.hasHour(snow, listOf("night")))
        assertFalse(PictureMath.hasHour(snow, listOf("battery")))
    }

    @Test
    fun theParametersAreThePicturesOwn() {
        val birds = ShaderParam.read("birds 0..7 = 5 Birds")!!
        val phone = ShaderValues.Phone(0.5f, false, LocalTime.NOON)
        assertEquals(listOf(5f), PictureMath.values(listOf(birds), emptyMap(), emptyMap(), emptyMap(), phone))
        assertEquals(listOf(2f), PictureMath.values(listOf(birds), mapOf("birds" to "2"), emptyMap(), emptyMap(), phone))
    }

    @Test
    fun theQuestionSaysTheOldWallpaperIsGone() {
        assertEquals("Replace your wallpaper?", PictureMath.QUESTION)
        assertTrue(PictureMath.replaces(live = false).contains("can't bring the old one back"))
        assertFalse(PictureMath.replaces(live = false).contains("live wallpaper"))
        assertTrue(PictureMath.replaces(live = true).endsWith("Kolahal's live wallpaper is replaced too."))
    }
}
