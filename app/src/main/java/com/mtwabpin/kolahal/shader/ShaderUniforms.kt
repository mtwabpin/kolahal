/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.shader

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDateTime

/** What one frame of a shader is drawn with. Reused from frame to frame. */
class ShaderFrame {
    var width = 1f
    var height = 1f

    /** The grid's columns and where they start, in px, when they are known; otherwise what fits, in the middle. */
    var cols = 0
    var left = 0

    /** The shader's own clock. */
    var seconds = 0f

    /** The last touch, in px; below 0 when there was none. */
    var touchX = -1f
    var touchY = -1f
    var touchAge = NEVER
    var pressed = false

    var battery = 1f
    var charging = 0f
    var tiltX = 0f
    var tiltY = 0f

    companion object {
        /** `touchAge` before any touch: longer than any ring or fade. */
        const val NEVER = 1000f
    }
}

/**
 * Sets the uniforms of a compiled shader: those it declares, with the types Kolahal knows ([ShaderMeta.KNOWN]).
 * Home and the live wallpaper draw shaders with it. [colors]: the theme's, by role ([ShaderMeta.COLORS]).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class ShaderUniforms(
    private val meta: ShaderMeta,
    private val runtime: RuntimeShader,
    private val cellW: Float,
    private val cellH: Float,
    private val colors: Map<String, Int>,
    private val dark: Boolean,
    /** How long a one-off shader runs, in seconds. Where it isn't played it starts again after a second. */
    private val once: Float? = meta.once,
    /** It is the live wallpaper, with no text over it. */
    private val wallpaper: Boolean = false,
) {
    private val declared = meta.uniforms.associateBy { it.name }

    // What a shader doesn't declare isn't read, so what can't change it doesn't draw it again.
    val usesTouch = "touch" in declared || "touchAge" in declared
    val usesClock = meta.animated && ("time" in declared || "touchAge" in declared || ShaderMeta.PROGRESS in declared)
    val usesDate = declared[ShaderDate.NAME]?.floats == ShaderDate.FLOATS
    val usesTilt = declared[ShaderTilt.NAME]?.floats == ShaderTilt.FLOATS
    val usesPressed = ShaderMeta.PRESSED in declared
    val usesBattery = ShaderMeta.BATTERY in declared
    val usesCharging = ShaderMeta.CHARGING in declared

    /** Sets them all for [f]; [param] is the value of the header's parameter at a place. Throws when Android refuses one. */
    fun set(f: ShaderFrame, param: (Int) -> Float) {
        float1("time", f.seconds)
        if (once != null) float1(ShaderMeta.PROGRESS, ((f.seconds % (once + 1f)) / once).coerceAtMost(1f))
        float2("resolution", f.width, f.height)
        float2("cell", cellW, cellH)
        val columns = if (f.cols > 0) f.cols else (f.width / cellW).toInt()
        float2("grid", columns.toFloat(), (f.height / cellH).toInt().toFloat())
        float2("origin", if (f.cols > 0) f.left.toFloat() else (f.width - columns * cellW) / 2f, 0f)
        float1("dark", if (dark) 1f else 0f)
        float1(ShaderMeta.WALLPAPER, if (wallpaper) 1f else 0f)
        float2("touch", f.touchX, f.touchY)
        float1("touchAge", f.touchAge)
        float1(ShaderMeta.PRESSED, if (f.pressed) 1f else 0f)
        float1(ShaderMeta.BATTERY, f.battery)
        float1(ShaderMeta.CHARGING, f.charging)
        float2(ShaderTilt.NAME, f.tiltX, f.tiltY)
        if (usesDate) {
            val now = ShaderDate.of(LocalDateTime.now())
            runtime.setFloatUniform(ShaderDate.NAME, now[0], now[1], now[2], now[3])
        }
        meta.params.forEachIndexed { i, p -> float1(p.name, param(i)) }
        for (name in ShaderMeta.COLORS) colors[name]?.let { color(name, it) }
    }

    private fun float1(name: String, value: Float) {
        if (declared[name]?.floats == 1) runtime.setFloatUniform(name, value)
    }

    private fun float2(name: String, x: Float, y: Float) {
        if (declared[name]?.floats == 2) runtime.setFloatUniform(name, x, y)
    }

    private fun color(name: String, argb: Int) {
        val d = declared[name] ?: return
        if (d.color) runtime.setColorUniform(name, argb)
        else if (d.floats == 4) runtime.setFloatUniform(
            name,
            ((argb shr 16) and 0xFF) / 255f, ((argb shr 8) and 0xFF) / 255f, (argb and 0xFF) / 255f, (argb ushr 24) / 255f,
        )
    }
}
