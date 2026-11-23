package com.mtwabpin.kolahal.ui.wallpaper

import com.mtwabpin.kolahal.shader.ShaderClock
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the live wallpaper draws, and when it asks the phone ([WallpaperDraws]). */
class WallpaperDrawsTest {
    private val state = WallpaperState(
        name = "dunes", code = "uniform shader content;", set = mapOf("wind" to "1.5"), follows = emptyMap(),
        formulas = emptyMap(), given = mapOf("weather.rain" to "0.25"),
        dark = mapOf("bg" to 0xFF0B0D0B.toInt()), light = mapOf("bg" to 0xFFF3F7F1.toInt()),
        alwaysDark = false, cellW = 22.5f, cellH = 48f,
    )

    /** It wakes a little before each 30th of a second, so it never waits for the screen's frame after. */
    @Test
    fun theLoopSleepsBetweenFrames() {
        assertEquals(1_000L / ShaderClock.FPS, WallpaperDraws.FRAME_MS)
        assertEquals(WallpaperDraws.FRAME_MS - 4, WallpaperDraws.untilNextFrame(0L))
        assertEquals(WallpaperDraws.FRAME_MS - 9, WallpaperDraws.untilNextFrame(5L))
        // A slow frame: the next as soon as the screen has one.
        assertEquals(0L, WallpaperDraws.untilNextFrame(40L))
        // A clock that went back never makes a longer sleep than a frame.
        assertEquals(WallpaperDraws.FRAME_MS, WallpaperDraws.untilNextFrame(-100L))
        // On screens of 60, 90, 120 and 144 Hz, the first refresh after the wake is at most a little over a 30th of a second.
        for (hz in listOf(60, 90, 120, 144)) {
            val refresh = 1_000.0 / hz
            val first = Math.ceil(WallpaperDraws.untilNextFrame(0L) / refresh) * refresh
            assertTrue("$hz Hz: $first", first <= 1_000.0 / 30 + refresh / 2)
        }
    }

    /** A shader with `@fps 10` sleeps a tenth of a second between frames, waking a little before. */
    @Test
    fun theLoopSleepsAsLongAsTheHeaderSays() {
        assertEquals(96L, WallpaperDraws.untilNextFrame(0L, 100L))
        assertEquals(86L, WallpaperDraws.untilNextFrame(10L, 100L))
        assertEquals(0L, WallpaperDraws.untilNextFrame(150L, 100L))
        assertEquals(100L, WallpaperDraws.untilNextFrame(-100L, 100L))
        // Without the header's line, as before.
        assertEquals(WallpaperDraws.untilNextFrame(5L), WallpaperDraws.untilNextFrame(5L, WallpaperDraws.FRAME_MS))
    }

    private fun pace(vararg lines: String, uniforms: String) =
        ShaderClock.Pace.of(ShaderMeta.parse(lines.joinToString("\n") + "\nuniform shader content; $uniforms"))

    /** A touch on a slow shader that reads it wakes the loop at once; nothing else does. */
    @Test
    fun aTouchHurriesASlowLoop() {
        val sand = pace("// @animated", "// @fps 10", "// @settles 5", uniforms = "uniform float time; uniform float2 touch;")
        assertTrue(WallpaperDraws.touchHurries(sand, moving = true, frameMs = 100L))
        // Already at 30 a second, or not moving: the touch's own frame, or [touchAsks], see to it.
        assertFalse(WallpaperDraws.touchHurries(sand, moving = true, frameMs = WallpaperDraws.FRAME_MS))
        assertFalse(WallpaperDraws.touchHurries(sand, moving = false, frameMs = 100L))
        // One that doesn't read the touch stays at its pace.
        val mist = pace("// @animated", "// @fps 10", uniforms = "uniform float time;")
        assertFalse(WallpaperDraws.touchHurries(mist, moving = true, frameMs = 100L))
        // Without the lines, never.
        assertFalse(WallpaperDraws.touchHurries(ShaderClock.Pace.DEFAULT, moving = true, frameMs = WallpaperDraws.FRAME_MS))
    }

    /** One that stopped because its touch settled starts again at the next touch, however soon it comes. */
    @Test
    fun aTouchStartsASettledShaderAtOnce() {
        assertTrue(WallpaperDraws.touchAsks(movesWithClock = true, leans = false, leaning = false, moving = false, sinceAskedMs = 0L, settled = true))
        assertFalse(WallpaperDraws.touchAsks(movesWithClock = true, leans = false, leaning = false, moving = false, sinceAskedMs = 0L, settled = false))
        assertFalse(WallpaperDraws.touchAsks(movesWithClock = true, leans = false, leaning = false, moving = true, sinceAskedMs = 0L, settled = true))
    }

    @Test
    fun aTouchDrawsOnlyWhatReadsIt() {
        assertFalse(WallpaperDraws.touchDraws(readsTouch = false, readsPressed = false, moving = false))
        assertTrue(WallpaperDraws.touchDraws(readsTouch = true, readsPressed = false, moving = false))
        assertTrue(WallpaperDraws.touchDraws(readsTouch = false, readsPressed = true, moving = false))
        // While it moves, its next frame shows the touch.
        assertFalse(WallpaperDraws.touchDraws(readsTouch = true, readsPressed = true, moving = true))
    }

    @Test
    fun aTouchAsksThePhoneSeldom() {
        val long = WallpaperDraws.CHECK_MS
        // One that moves and stopped starts again at a touch.
        assertTrue(WallpaperDraws.touchAsks(movesWithClock = true, leans = false, leaning = false, moving = false, sinceAskedMs = long))
        assertFalse(WallpaperDraws.touchAsks(movesWithClock = true, leans = false, leaning = false, moving = true, sinceAskedMs = long))
        // A sensor that stopped too.
        assertTrue(WallpaperDraws.touchAsks(movesWithClock = false, leans = true, leaning = false, moving = false, sinceAskedMs = long))
        assertFalse(WallpaperDraws.touchAsks(movesWithClock = false, leans = true, leaning = true, moving = false, sinceAskedMs = long))
        // A still one without tilt never asks.
        assertFalse(WallpaperDraws.touchAsks(movesWithClock = false, leans = false, leaning = false, moving = false, sinceAskedMs = long))
        // Not at each touch of a drag.
        assertFalse(WallpaperDraws.touchAsks(movesWithClock = true, leans = true, leaning = false, moving = false, sinceAskedMs = long - 1))
    }

    @Test
    fun theBatteryDrawsWhatReadsIt() {
        assertTrue(WallpaperDraws.batteryShows(readsBattery = true, readsCharging = false, levelChanged = true, chargingChanged = false))
        assertFalse(WallpaperDraws.batteryShows(readsBattery = true, readsCharging = false, levelChanged = false, chargingChanged = true))
        assertTrue(WallpaperDraws.batteryShows(readsBattery = false, readsCharging = true, levelChanged = false, chargingChanged = true))
        assertFalse(WallpaperDraws.batteryShows(readsBattery = false, readsCharging = false, levelChanged = true, chargingChanged = true))
    }

    @Test
    fun theMinuteIsForTheTimeOfDay() {
        assertTrue(ShaderValues.BUILT_IN.keys.containsAll(WallpaperDraws.TIME))
        assertTrue(WallpaperDraws.followsTime(readsDate = true, follows = emptyList()))
        assertTrue(WallpaperDraws.followsTime(readsDate = false, follows = listOf(null, "night")))
        assertTrue(WallpaperDraws.followsTime(readsDate = false, follows = listOf("day")))
        assertFalse(WallpaperDraws.followsTime(readsDate = false, follows = listOf(null, "battery", "charging", "weather.rain")))
        assertFalse(WallpaperDraws.followsTime(readsDate = false, follows = emptyList()))
    }

    @Test
    fun androidsColorsFollowTheCodeAndTheTheme() {
        // The first time Android asks for them itself.
        assertFalse(WallpaperDraws.colorsChange(null, true, state, true))
        // A script's value, the cell: they change on their own, and Android keeps its colors.
        val same = state.copy(given = mapOf("weather.rain" to "0.4"), cellW = 30f)
        assertFalse(WallpaperDraws.colorsChange(state, false, same, false))
        // What the user set a parameter to, or made it follow.
        assertTrue(WallpaperDraws.colorsChange(state, false, state.copy(set = state.set + ("wind" to "0.5")), false))
        assertTrue(WallpaperDraws.colorsChange(state, false, state.copy(follows = state.follows + ("wind" to "battery")), false))
        assertTrue(WallpaperDraws.colorsChange(state, false, state.copy(formulas = state.formulas + ("wind" to "battery / 20")), false))
        // The colors of the theme that isn't in use.
        assertFalse(WallpaperDraws.colorsChange(state, false, state.copy(dark = mapOf("bg" to 0)), false))
        assertTrue(WallpaperDraws.colorsChange(state, false, state.copy(light = mapOf("bg" to 0)), false))
        assertTrue(WallpaperDraws.colorsChange(state, false, state.copy(code = "uniform shader content; // other"), false))
        assertTrue(WallpaperDraws.colorsChange(state, false, state, true))
        assertTrue(WallpaperDraws.colorsChange(state, false, null, false))
    }
}
