/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.wallpaper

import com.mtwabpin.kolahal.shader.ShaderDate
import com.mtwabpin.kolahal.shader.ShaderFrame
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderParam
import com.mtwabpin.kolahal.shader.ShaderParams
import com.mtwabpin.kolahal.shader.ShaderValues
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A size a picture can be drawn at ([PictureMath.sizes]): its [id], its [label] (This screen, 2K, 4K), its pixels,
 * and why it can't be drawn on this phone ([why], null when it can).
 */
data class PictureSize(val id: String, val label: String, val width: Int, val height: Int, val why: String? = null) {
    /** Its pixels as people read them: 2160 × 4800. */
    val pixels: String get() = "$width × $height"
}

/**
 * What a picture is drawn with ([PictureShader]), beside its size: the theme's [colors] by role ([ShaderMeta.COLORS])
 * and whether they are the [dark] ones, the value of each parameter in its header's order ([params]), the moment
 * ([time], in seconds of `time`), the hour ([date], [ShaderDate]; null for now), the arrangement ([seed]), and the
 * screen it is a copy of: its width in pixels ([screenW]) and the text cell's size there ([cellW], [cellH]). The phone's
 * battery as it was when the picture was made ([battery], [charging]).
 */
data class PictureLook(
    val colors: Map<String, Int>,
    val dark: Boolean,
    val params: List<Float>,
    val time: Float,
    val date: FloatArray?,
    val seed: Float,
    val screenW: Int,
    val cellW: Float,
    val cellH: Float,
    val battery: Float = 1f,
    val charging: Boolean = false,
) {
    // A FloatArray compares by its values here, so a look made again with the same choices is equal.
    override fun equals(other: Any?): Boolean = other is PictureLook && colors == other.colors && dark == other.dark &&
        params == other.params && time == other.time && date.contentEquals(other.date) && seed == other.seed &&
        screenW == other.screenW && cellW == other.cellW && cellH == other.cellH && battery == other.battery &&
        charging == other.charging

    override fun hashCode(): Int = listOf(colors, dark, params, time, date?.toList(), seed, screenW, cellW, cellH, battery, charging).hashCode()
}

/** What Lab's pictures work out without Android: the sizes, the file's name, the uniforms of a frame and which controls show. */
object PictureMath {
    /** The short side of a 2K and a 4K picture, in pixels. */
    const val SHORT_2K = 1440
    const val SHORT_4K = 2160

    const val SCREEN = "screen"
    const val K2 = "2k"
    const val K4 = "4k"

    /** How far the Frame control goes, in seconds: as far as `@still` may say ([ShaderMeta.MAX_STILL]). */
    const val MAX_TIME = ShaderMeta.MAX_STILL

    /** The arrangements Shuffle picks from: 0 is the scene's own, as on home. */
    const val MAX_SEED = 999

    /**
     * The sizes offered for a screen of [screenW] × [screenH] pixels: This screen, then 2K and 4K, each at the
     * screen's proportions with that short side. One that is no larger than the screen is left out. One with a side
     * longer than [maxSide] (the GPU's largest texture, null when it isn't known) is there with the reason it can't be drawn.
     */
    fun sizes(screenW: Int, screenH: Int, maxSide: Int?): List<PictureSize> {
        val w = screenW.coerceAtLeast(1)
        val h = screenH.coerceAtLeast(1)
        val short = minOf(w, h)
        fun at(id: String, label: String, side: Int): PictureSize {
            val (pw, ph) = if (w <= h) side to Math.round(side.toDouble() * h / w).toInt() else Math.round(side.toDouble() * w / h).toInt() to side
            val why = if (maxSide != null && maxOf(pw, ph) > maxSide) "This phone draws up to $maxSide px a side" else null
            return PictureSize(id, label, pw, ph, why)
        }
        return listOfNotNull(
            at(SCREEN, "This screen", short),
            at(K2, "2K", SHORT_2K).takeIf { SHORT_2K > short },
            at(K4, "4K", SHORT_4K).takeIf { SHORT_4K > short },
        )
    }

    /** The size offered at first: This screen, or the first that can be drawn. */
    fun firstSize(sizes: List<PictureSize>): PictureSize? = sizes.firstOrNull { it.why == null }

    /** The picture's file name: `kolahal-pond-20261127-193005.png`, for shader [shader] saved [at]. */
    fun fileName(shader: String, at: LocalDateTime): String =
        "kolahal-${shader.replace(Regex("[^A-Za-z0-9_-]"), "-")}-${at.format(STAMP)}.png"

    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /**
     * The text cell's size in a picture [width] pixels wide, of a screen [screenW] wide where it is [cellW] × [cellH]:
     * as much larger, so a larger picture is a larger copy of the screen and not a finer grid.
     */
    fun cell(cellW: Float, cellH: Float, screenW: Int, width: Int): Pair<Float, Float> {
        val scale = if (screenW > 0) width.toFloat() / screenW else 1f
        return cellW * scale to cellH * scale
    }

    /** The `date` uniform of [day] at [minute] of the day (0 to 1439). */
    fun date(day: LocalDate, minute: Int): FloatArray {
        val m = minute.coerceIn(0, MINUTES - 1)
        return ShaderDate.of(day.atTime(m / 60, m % 60))
    }

    /** The question before Kolahal sets a picture as the wallpaper itself, where the phone has no screen of its own for it. */
    const val QUESTION = "Replace your wallpaper?"

    /** What the question says: the old one can't come back, and Kolahal's live wallpaper goes too where it is on ([live]). */
    fun replaces(live: Boolean): String =
        "The picture replaces your wallpaper where you pick. Kolahal can't bring the old one back." +
            if (live) " Kolahal's live wallpaper is replaced too." else ""

    /** Minutes in a day: the hour control goes over them. */
    const val MINUTES = 24 * 60

    /** A minute of the day as a clock shows it: 18:40. */
    fun clock(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

    /**
     * [frame] filled for one picture [width] × [height] of [look]: its moment, hour and arrangement, the phone's
     * battery, no touch, level, and no corner or cutout (a picture isn't the screen). The same frame, larger,
     * is the same picture larger.
     */
    fun frame(look: PictureLook, width: Int, height: Int, frame: ShaderFrame = ShaderFrame()): ShaderFrame = frame.apply {
        this.width = width.toFloat()
        this.height = height.toFloat()
        cols = 0
        left = 0
        seconds = look.time
        touchX = -1f
        touchY = -1f
        touchAge = ShaderFrame.NEVER
        pressed = false
        battery = look.battery
        charging = if (look.charging) 1f else 0f
        tiltX = 0f
        tiltY = 0f
        corner = 0f
        cutout.fill(0f)
        date = look.date
        seed = look.seed
    }

    /** Where the Frame control starts: the header's `@still`, or 0. */
    fun startTime(meta: ShaderMeta): Float = meta.still ?: 0f

    /** Whether the picture has a moment to pick (Frame): the shader moves by itself and reads `time`. */
    fun hasFrame(meta: ShaderMeta): Boolean = meta.animated && meta.once == null && meta.uniforms.any { it.name == "time" && it.floats == 1 }

    /**
     * Whether the picture has an hour to pick (Time of day): the shader reads `date`, or a parameter follows a value
     * that moves with the time of day ([follows]: what each parameter follows, null for nothing).
     */
    fun hasHour(meta: ShaderMeta, follows: List<String?>): Boolean =
        meta.uniforms.any { it.name == ShaderDate.NAME && it.floats == ShaderDate.FLOATS } || follows.any { it in WallpaperDraws.TIME }

    /** Whether the picture has an arrangement to pick (Shuffle): the shader declares `uniform float seed;`. */
    fun hasSeed(meta: ShaderMeta): Boolean = meta.uniforms.any { it.name == ShaderMeta.SEED && it.floats == 1 }

    /**
     * Each parameter's value in a picture: what it follows, worked out for the [phone], or else what is [set], as
     * home works them out ([ShaderParams]). [followed] and [formulas] are the picture's own, by parameter.
     */
    fun values(
        params: List<ShaderParam>,
        set: Map<String, String>,
        followed: Map<String, String>,
        formulas: Map<String, String>,
        phone: ShaderValues.Phone,
    ): List<Float> = params.map { p ->
        ShaderParams.follow(followed, p)
            ?.let { ShaderParams.followed(p, it, ShaderParams.formula(formulas, followed, p), phone) }
            ?: ShaderParams.of(set, p)
    }
}
