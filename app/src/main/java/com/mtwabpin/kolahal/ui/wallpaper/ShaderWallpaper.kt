/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.wallpaper

import android.app.WallpaperColors
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.BatteryManager
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.MotionEvent
import android.view.RoundedCorner
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import com.mtwabpin.kolahal.shader.ShaderClock
import com.mtwabpin.kolahal.shader.ShaderDate
import com.mtwabpin.kolahal.shader.ShaderFrame
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderParams
import com.mtwabpin.kolahal.shader.ShaderPhone
import com.mtwabpin.kolahal.shader.ShaderTilt
import com.mtwabpin.kolahal.shader.ShaderUniforms
import com.mtwabpin.kolahal.shader.ShaderValues
import com.mtwabpin.kolahal.ui.tty.OnGravity
import com.mtwabpin.kolahal.ui.tty.PhoneGravity
import java.io.File
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.exp

/**
 * A shader as the phone's live wallpaper, behind any launcher and on the lock screen. It runs in a process of
 * its own (`:wallpaper`) and draws what home handed over ([WallpaperState]), over a page of the theme's color.
 *
 * - It draws only while it can be seen: an app in front or the screen off stops it.
 * - A shader that moves is drawn [ShaderClock.FPS] times a second, or as often as its `@fps` and `@settles`
 *   say ([ShaderClock.frameMs]), under the same conditions as on home ([ShaderClock.moves]); one that stands
 *   still, when something it shows changes.
 * - A touch draws it again only when it reads the touch, at most once a frame; while it moves, its next
 *   frame shows the touch, at once when it waited longer than a touch wants ([WallpaperDraws]).
 * - A shader that crashed it [WallpaperGuard.MAX_TRIES] times in a row isn't tried again until its code
 *   changes: then the page shows alone.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class ShaderWallpaper : WallpaperService() {
    private val engines = LinkedHashSet<ShaderEngine>()
    private val main = Handler(Looper.getMainLooper())

    /** Home wrote another state. */
    private var watch: FileObserver? = null

    /** Shaders that crash the process, counted in a file of its own. */
    private val guard by lazy { WallpaperGuard(File(WallpaperState.dir(this), "trying")) }

    override fun onCreate() {
        super.onCreate()
        val dir = WallpaperState.dir(this).apply { mkdirs() }
        // Home writes the state under another name and renames it when it is whole. Only the rename counts:
        // until then the file holds the state before.
        watch = object : FileObserver(dir, MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == WallpaperState.FILE) main.post { engines.forEach { it.load() } }
            }
        }.also { it.startWatching() }
    }

    override fun onDestroy() {
        watch?.stopWatching()
        super.onDestroy()
    }

    /** The phone went dark or light. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        engines.forEach { it.load() }
    }

    override fun onCreateEngine(): Engine = ShaderEngine()

    private inner class ShaderEngine : Engine(), Choreographer.FrameCallback {
        private var state: WallpaperState? = null
        private var meta: ShaderMeta? = null
        private var runtime: RuntimeShader? = null
        private var uniforms: ShaderUniforms? = null
        private var dark = true
        private val frame = ShaderFrame()
        private val paint = Paint()

        private var seen = false
        private var ready = false

        /** It moves now, and is drawn again and again. */
        private var moving = false
        private var lastFrame = 0L
        private var checkedAt = 0L
        private var touchedAt = SystemClock.uptimeMillis()

        /** How often it is drawn while it moves, from its header ([ShaderClock.Pace]). */
        private var pace = ShaderClock.Pace.DEFAULT

        /** The time from the frame loop's last frame to its next. */
        private var frameEvery = FRAME_MS

        /** When the last touch came, by the phone's uptime; below 0 when there was none. Being seen isn't one. */
        private var lastTouch = -1L

        /** It stopped moving because the touch it moves with settled, though the phone allowed it ([ShaderClock.frameMs]): a touch starts it again. */
        private var settled = false

        /** When a touch last asked the phone whether it may move or lean ([allowed]). */
        private var askedAt = 0L

        /** A draw waits for the screen's next frame ([drawNext]). */
        private var drawPending = false

        /** When the last touch came, on the shader's clock; below 0 when there was none. */
        private var touchSeconds = -1f

        private var params = FloatArray(0)
        private var aims = FloatArray(0)
        private var gliding = false

        private val tilt = ShaderTilt()
        private var gravity: PhoneGravity? = null
        private var battery = 1f
        private var charging = false

        private val power = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (charge(intent)) show()
            }
        }

        /** Takes the battery from [intent]. True when the picture changes with it. */
        private fun charge(intent: Intent): Boolean {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val was = battery
            val wasCharging = charging
            battery = (level.toFloat() / scale).coerceIn(0f, 1f)
            charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            // The broadcast also comes for the temperature and the voltage, which nothing shows.
            if (battery == was && charging == wasCharging) return false
            val aimed = aim()
            val set = uniforms ?: return false
            return aimed || WallpaperDraws.batteryShows(set.usesBattery, set.usesCharging, battery != was, charging != wasCharging)
        }

        /** The hour and the values that follow it move on. Only while it reads the date or a parameter follows the time of day. */
        private val minute = object : Runnable {
            override fun run() {
                if (aim() || uniforms?.usesDate == true) show()
                main.postDelayed(this, ShaderDate.untilNextMinute(System.currentTimeMillis()))
            }
        }

        private fun followsTime(): Boolean {
            val set = uniforms ?: return false
            val name = state?.name ?: return false
            return WallpaperDraws.followsTime(set.usesDate, meta?.params.orEmpty().map { ShaderParams.follow(name, it) })
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            engines += this
            setTouchEventsEnabled(true)
            load()
        }

        override fun onDestroy() {
            engines -= this
            stop()
            super.onDestroy()
        }

        /** Reads what home handed over, and compiles the shader when it is another. */
        fun load() {
            val next = WallpaperState.read(this@ShaderWallpaper)
            val before = state
            val wasDark = dark
            val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            dark = next?.alwaysDark == true || night
            state = next
            if (next != null) {
                ShaderParams.restore(next.name, next.set, next.follows, next.formulas)
                ShaderValues.restore(next.given)
                if (next.code != before?.code || runtime == null) compile(next)
                prepare(next)
            }
            aim(now = next?.name != before?.name)
            if (WallpaperDraws.colorsChange(before, wasDark, next, dark)) notifyColorsChanged()
            if (seen) restart() else draw()
        }

        private fun compile(next: WallpaperState) {
            runtime = null
            meta = null
            uniforms = null
            pace = ShaderClock.Pace.DEFAULT
            frame.seconds = 0f
            if (!guard.mayTry(next.code) || ShaderMeta.problems(next.code).isNotEmpty()) return
            guard.trying(next.code)
            runtime = try {
                RuntimeShader(next.code)
            } catch (_: RuntimeException) {
                guard.proven()
                return
            }
            meta = ShaderMeta.parse(next.code).also { pace = ShaderClock.Pace.of(it) }
            main.removeCallbacks(proven)
            main.postDelayed(proven, PROVEN_MS)
        }

        private val proven = Runnable { guard.proven() }

        private fun prepare(next: WallpaperState) {
            val shader = runtime ?: return
            val colors = if (dark) next.dark else next.light
            givePage(shader, colors["bg"] ?: 0xFF000000.toInt())
            uniforms = ShaderUniforms(meta!!, shader, next.cellW, next.cellH, colors, dark, wallpaper = true)
            paint.shader = shader
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            ready = true
            frame.width = width.toFloat()
            frame.height = height.toFloat()
            frame.corner = corner()
            cutout(frame.cutout)
            draw()
        }

        /**
         * The radius of the screen's rounded corners in px, the largest of the four, for `corner`: the surface is the
         * screen. 0 when they are square or the display isn't known.
         */
        private fun corner(): Float = runCatching {
            val display = displayContext?.display ?: return@runCatching 0f
            CORNERS.maxOf { display.getRoundedCorner(it)?.radius ?: 0 }.toFloat()
        }.getOrDefault(0f)

        /**
         * The camera's cutout at the top of the screen in px, into [out], for `cutout`: left, top, right, bottom
         * ([android.view.DisplayCutout.getBoundingRectTop]). All 0 when there is none, as in landscape, where the
         * camera is at a side. Read with the surface's size, so a rotation reads it again.
         */
        private fun cutout(out: FloatArray) {
            out.fill(0f)
            val top = runCatching { displayContext?.display?.cutout?.boundingRectTop }.getOrNull() ?: return
            if (top.isEmpty) return
            out[0] = top.left.toFloat()
            out[1] = top.top.toFloat()
            out[2] = top.right.toFloat()
            out[3] = top.bottom.toFloat()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            ready = false
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (seen == visible) return
            seen = visible
            if (visible) {
                touchedAt = SystemClock.uptimeMillis()
                restart()
            } else stop()
        }

        /** Starts over, drawn once: the battery is taken as it is now, so its first broadcast changes nothing. */
        private fun restart() {
            stop()
            seen = true
            registerReceiver(power, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::charge)
            aim()
            if (followsTime()) main.postDelayed(minute, ShaderDate.untilNextMinute(System.currentTimeMillis()))
            wake()
        }

        private fun stop() {
            moving = false
            settled = false
            Choreographer.getInstance().removeFrameCallback(this)
            Choreographer.getInstance().removeFrameCallback(drawNext)
            drawPending = false
            main.removeCallbacks(minute)
            main.removeCallbacks(tiltCheck)
            runCatching { unregisterReceiver(power) }
            gravity?.stop()
            gravity = null
        }

        private fun allowed() = ShaderClock.moves(ShaderPhone.of(this@ShaderWallpaper), SystemClock.uptimeMillis() - touchedAt)

        private fun sinceTouch(): Long? = if (lastTouch < 0) null else SystemClock.uptimeMillis() - lastTouch

        private fun startTilt() {
            if (gravity != null) return
            tilt.restart()
            gravity = PhoneGravity(this@ShaderWallpaper).also { source ->
                source.start(OnGravity { x, y, z, shaky, rotation, atNs ->
                    if (tilt.gravity(x, y, z, shaky, rotation, atNs) && !moving) draw()
                })
            }
            main.removeCallbacks(tiltCheck)
            main.postDelayed(tiltCheck, CHECK_MS)
        }

        /** While only the sensor draws it, the frame loop's checks, as often: the sensor stops when the phone's state says so. */
        private val tiltCheck = object : Runnable {
            override fun run() {
                if (gravity == null) return
                if (!moving && !allowed()) {
                    gravity?.stop()
                    gravity = null
                    return
                }
                main.postDelayed(this, CHECK_MS)
            }
        }

        /** Draws once, and goes on while it moves. */
        private fun wake() {
            draw()
            val set = uniforms ?: return
            if (!seen) return
            val ok = (set.usesClock || set.usesTilt) && allowed()
            // One that moves only with the touch waits for one.
            val paced = set.usesClock && ok && ShaderClock.frameMs(pace, sinceTouch()) != null
            settled = set.usesClock && ok && !paced
            if ((paced || gliding) && !moving) loop(drawn = true)
            if (set.usesTilt && ok) startTilt()
        }

        /** Shows a change: drawn at once when it stands still, and on while a parameter glides. A frame loop that runs shows it anyway. */
        private fun show() {
            if (moving) return
            draw()
            if (gliding && seen) loop(drawn = true)
        }

        /** Starts the frame loop: its first frame is the screen's next, or a frame later when it was just [drawn]. */
        private fun loop(drawn: Boolean) {
            moving = true
            settled = false
            lastFrame = 0L
            checkedAt = SystemClock.uptimeMillis()
            frameEvery = ShaderClock.frameMs(pace, sinceTouch()) ?: FRAME_MS
            val choreographer = Choreographer.getInstance()
            choreographer.removeFrameCallback(this)
            if (drawn) choreographer.postFrameCallbackDelayed(this, WallpaperDraws.untilNextFrame(0L, frameEvery))
            else choreographer.postFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!seen || !moving) return
            val now = frameTimeNanos / 1_000_000L
            val uptime = SystemClock.uptimeMillis()
            var going = true
            if (uptime - checkedAt >= CHECK_MS) {
                checkedAt = uptime
                going = allowed()
                if (!going) {
                    gravity?.stop()
                    gravity = null
                }
            }
            // As often as the header asks; null when the touch it moves with has settled.
            val next = if (uniforms?.usesClock == true && going) ShaderClock.frameMs(pace, sinceTouch()) else null
            val every = next ?: FRAME_MS
            // A long gap (a pause) counts as one frame, so nothing jumps.
            val step = if (lastFrame == 0L) every else ShaderClock.step(now - lastFrame, every)
            lastFrame = now
            if (next != null) frame.seconds += step / 1000f
            glide(step / 1000f)
            draw()
            if (!gliding && next == null) {
                moving = false
                settled = going && uniforms?.usesClock == true
                return
            }
            frameEvery = every
            // It sleeps until just before the frame it wants, rather than waking at each refresh of the screen.
            val spent = System.nanoTime() / 1_000_000L - now
            Choreographer.getInstance().postFrameCallbackDelayed(this, WallpaperDraws.untilNextFrame(spent, every))
        }

        /** One draw at the screen's next frame, however many touches come before it. */
        private val drawNext = Choreographer.FrameCallback {
            drawPending = false
            if (!moving) draw()
        }

        override fun onTouchEvent(event: MotionEvent) {
            val uptime = SystemClock.uptimeMillis()
            touchedAt = uptime
            lastTouch = uptime
            frame.touchX = event.x
            frame.touchY = event.y
            frame.pressed = event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
            touchSeconds = frame.seconds
            val set = uniforms ?: return
            if (!seen) return
            // A shader that stopped moving, or a sensor that stopped, starts again at a touch, if the phone allows it.
            if (WallpaperDraws.touchAsks(set.usesClock, set.usesTilt, gravity != null, moving, uptime - askedAt, settled)) {
                askedAt = uptime
                settled = false
                if (allowed()) {
                    if (set.usesClock && !moving) loop(drawn = false)
                    if (set.usesTilt) startTilt()
                }
            }
            // A loop that sleeps longer than a touch wants draws its next frame at the screen's next one.
            if (WallpaperDraws.touchHurries(pace, moving, frameEvery)) {
                frameEvery = ShaderClock.frameMs(pace, 0L) ?: FRAME_MS
                val choreographer = Choreographer.getInstance()
                choreographer.removeFrameCallback(this)
                choreographer.postFrameCallback(this)
            }
            if (WallpaperDraws.touchDraws(set.usesTouch, set.usesPressed, moving) && !drawPending) {
                drawPending = true
                Choreographer.getInstance().postFrameCallback(drawNext)
            }
        }

        private fun phone() = ShaderValues.Phone(battery, charging, LocalTime.now())

        /**
         * Where the parameters are to be: at once when [now], or when people set them; else they glide there.
         * True when one is to be somewhere else, so the picture changes.
         */
        private fun aim(now: Boolean = false): Boolean {
            val m = meta ?: return false
            val name = state?.name ?: return false
            val phone = phone()
            val fresh = now || params.size != m.params.size
            if (fresh) {
                params = FloatArray(m.params.size)
                aims = FloatArray(m.params.size)
            }
            var changed = fresh
            gliding = false
            m.params.forEachIndexed { i, p ->
                val followed = ShaderParams.followed(name, p, phone)
                val target = followed ?: ShaderParams.of(name, p)
                if (target != aims[i]) changed = true
                aims[i] = target
                val far = abs(aims[i] - params[i]) > (p.max - p.min) * 0.02f
                if (fresh || followed == null || p.choices.isNotEmpty() || !far) params[i] = aims[i] else gliding = true
            }
            return changed
        }

        private fun glide(seconds: Float) {
            if (!gliding) return
            val m = meta ?: return
            val part = 1f - exp(-seconds / GLIDE_S)
            gliding = false
            m.params.forEachIndexed { i, p ->
                params[i] += (aims[i] - params[i]) * part
                if (abs(aims[i] - params[i]) <= (p.max - p.min) * 0.002f) params[i] = aims[i] else gliding = true
            }
        }

        private fun draw() {
            if (!ready) return
            val canvas = try {
                surfaceHolder.surface?.takeIf { it.isValid }?.lockHardwareCanvas()
            } catch (_: RuntimeException) {
                null
            } ?: return
            try {
                paintOn(canvas, frame.width, frame.height)
            } finally {
                runCatching { surfaceHolder.surface.unlockCanvasAndPost(canvas) }
            }
        }

        /** The shader, or the page alone when there is none to draw. */
        private fun paintOn(canvas: Canvas, w: Float, h: Float) {
            val set = uniforms
            val page = state?.let { (if (dark) it.dark else it.light)["bg"] } ?: 0xFF000000.toInt()
            if (set == null) return canvas.drawColor(page)
            frame.battery = battery
            frame.charging = if (charging) 1f else 0f
            frame.tiltX = tilt.x
            frame.tiltY = tilt.y
            frame.touchAge = when {
                touchSeconds < 0f -> ShaderFrame.NEVER
                meta?.animated == true -> (frame.seconds - touchSeconds).coerceAtLeast(0f)
                else -> 0f
            }
            val drawn = runCatching {
                set.set(frame) { params.getOrElse(it) { 0f } }
                canvas.drawRect(0f, 0f, w, h, paint)
            }
            // One that Android refuses: the page, and no more tries.
            if (drawn.isFailure) {
                uniforms = null
                canvas.drawColor(page)
            }
        }

        /** The colors that Android makes its own of, from a small picture of what is drawn. */
        override fun onComputeColors(): WallpaperColors? = runCatching { picture()?.let(WallpaperColors::fromBitmap) }.getOrNull()

        private fun picture(): Bitmap? {
            val w = frame.width
            val h = frame.height
            if (w < 2f || h < 2f) return null
            val scale = PICTURE_PX / maxOf(w, h)
            val pw = (w * scale).toInt().coerceAtLeast(1)
            val ph = (h * scale).toInt().coerceAtLeast(1)
            val usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
            ImageReader.newInstance(pw, ph, PixelFormat.RGBA_8888, 1, usage).use { reader ->
                val canvas = reader.surface.lockHardwareCanvas()
                canvas.scale(scale, scale)
                paintOn(canvas, w, h)
                reader.surface.unlockCanvasAndPost(canvas)
                for (i in 0 until PICTURE_TRIES) {
                    reader.acquireLatestImage()?.use { image ->
                        val buffer = image.hardwareBuffer ?: return null
                        return buffer.use { Bitmap.wrapHardwareBuffer(it, ColorSpace.get(ColorSpace.Named.SRGB))?.copy(Bitmap.Config.ARGB_8888, false) }
                    }
                    Thread.sleep(10)
                }
            }
            return null
        }
    }

    private companion object {
        const val FRAME_MS = WallpaperDraws.FRAME_MS
        const val CHECK_MS = WallpaperDraws.CHECK_MS

        /** A parameter is two thirds of the way to the value it follows after this long. */
        const val GLIDE_S = 0.6f

        /** A shader that ran this long is proven. */
        const val PROVEN_MS = 10_000L

        const val PICTURE_PX = 160f
        const val PICTURE_TRIES = 30

        val CORNERS = intArrayOf(
            RoundedCorner.POSITION_TOP_LEFT, RoundedCorner.POSITION_TOP_RIGHT,
            RoundedCorner.POSITION_BOTTOM_RIGHT, RoundedCorner.POSITION_BOTTOM_LEFT,
        )
    }
}

/**
 * Counts in [file] how often a shader was started and not proven, so one that crashes the live wallpaper's
 * process is left alone after [MAX_TRIES]. One for each [ShaderWallpaper], which a process has one of.
 * - A process counts a shader once, however many engines Android makes for it within a moment (the preview,
 *   the picker's pictures, the wallpaper itself): only a crash ends the process, and starts the next try.
 * - A shader that ran long enough is [proven], and its count goes.
 */
internal class WallpaperGuard(private val file: File) {
    /** The code that this process counted a try of, by its hash; null when none since the last [proven]. */
    private var counted: Int? = null

    private fun tries(code: String): Int {
        val words = runCatching { file.readText().split(' ') }.getOrNull() ?: return 0
        return if (words.size == 2 && words[0] == code.hashCode().toString()) words[1].toIntOrNull() ?: 0 else 0
    }

    /** Whether [code] may be tried: this process tries it already, or it crashed fewer than [MAX_TRIES] times in a row. */
    fun mayTry(code: String): Boolean = counted == code.hashCode() || tries(code) < MAX_TRIES

    /** [code] is about to be compiled and drawn: one more try, unless this process counted it already. */
    fun trying(code: String) {
        val hash = code.hashCode()
        if (counted == hash) return
        counted = hash
        runCatching { file.writeText("$hash ${tries(code) + 1}") }
    }

    fun proven() {
        counted = null
        file.delete()
    }

    companion object {
        const val MAX_TRIES = 3
    }
}

/**
 * Hands [shader] a page of one color, [page], to draw over: a step under opaque as on home ([ShaderMeta.PAGE_ALPHA]).
 * Nothing covers it, so a scene draws all of it. The live wallpaper and Lab's pictures ([PictureShader]) draw so.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun givePage(shader: RuntimeShader, page: Int) {
    val marked = (page and 0x00FFFFFF) or ((ShaderMeta.PAGE_ALPHA * 255f + 0.5f).toInt() shl 24)
    shader.setInputShader(ShaderMeta.INPUT, LinearGradient(0f, 0f, 0f, 1f, marked, marked, Shader.TileMode.CLAMP))
}

/** When the live wallpaper draws, and when it asks the phone: what [ShaderWallpaper] decides, without Android. */
internal object WallpaperDraws {
    /** The time between two frames of a shader that moves, at [ShaderClock.FPS]: without `@fps`, and while a touch on one that reads it settles. */
    const val FRAME_MS = 1_000L / ShaderClock.FPS

    /** How much before the frame it wants the frame loop wakes, so it doesn't wait for the screen's next one. */
    const val EARLY_MS = 4L

    /** How often the phone's state is checked while it moves or leans, and at most how often a touch asks it. */
    const val CHECK_MS = 2_000L

    /** Kolahal's own values ([ShaderValues.BUILT_IN]) that move with the time of day. */
    val TIME = setOf("day", "night")

    /**
     * How long the frame loop sleeps when it drew [spentMs] after its frame's time: until just before the next
     * frame it wants, [frameMs] after it ([ShaderClock.frameMs]).
     */
    fun untilNextFrame(spentMs: Long, frameMs: Long = FRAME_MS): Long = (frameMs - spentMs - EARLY_MS).coerceIn(0L, frameMs)

    /**
     * A touch draws it again when it reads the touch ([readsTouch]: `touch` or `touchAge`) or the finger
     * ([readsPressed]). While it is [moving], its next frame shows the touch, and the touch draws nothing.
     */
    fun touchDraws(readsTouch: Boolean, readsPressed: Boolean, moving: Boolean): Boolean = !moving && (readsTouch || readsPressed)

    /**
     * A touch asks the phone whether it may move ([ShaderClock.moves]) when a shader that moves with the clock
     * ([movesWithClock]) stands still, or one that [leans] has no sensor on ([leaning]); and not again within
     * [CHECK_MS] of the last time one asked ([sinceAskedMs]). One that stopped because the touch it moves with
     * [settled] asks at once: the touch starts it again.
     */
    fun touchAsks(
        movesWithClock: Boolean, leans: Boolean, leaning: Boolean, moving: Boolean, sinceAskedMs: Long, settled: Boolean = false,
    ): Boolean = (settled && movesWithClock && !moving) ||
        (sinceAskedMs >= CHECK_MS && ((movesWithClock && !moving) || (leans && !leaning)))

    /**
     * A touch draws the next frame at the screen's next one when the frame loop is [moving] and sleeps [frameMs]
     * between frames, longer than a touch on this shader wants ([ShaderClock.touchHurries]): a finger isn't
     * followed at `@fps`.
     */
    fun touchHurries(pace: ShaderClock.Pace, moving: Boolean, frameMs: Long): Boolean = moving && ShaderClock.touchHurries(pace, frameMs)

    /** A change of the battery's level or of the charger changes the picture when the shader reads that one. */
    fun batteryShows(readsBattery: Boolean, readsCharging: Boolean, levelChanged: Boolean, chargingChanged: Boolean): Boolean =
        (readsBattery && levelChanged) || (readsCharging && chargingChanged)

    /** It is drawn again each minute when it reads the date, or a parameter follows the time of day ([follows]: what each one follows). */
    fun followsTime(readsDate: Boolean, follows: List<String?>): Boolean = readsDate || follows.any { it in TIME }

    /**
     * Android takes its colors again when the picture's colors may be others: another shader's code, other
     * colors of the theme in use, dark changed to light, or what the user set a parameter to or made it follow.
     * Not for a value a script gave or the cell's size, which change on their own. Not the first time: Android
     * asks for the colors itself.
     */
    fun colorsChange(before: WallpaperState?, wasDark: Boolean, next: WallpaperState?, dark: Boolean): Boolean {
        if (before == null) return false
        if (next == null) return true
        return next.code != before.code || dark != wasDark ||
            (if (dark) next.dark else next.light) != (if (wasDark) before.dark else before.light) ||
            next.set != before.set || next.follows != before.follows || next.formulas != before.formulas
    }
}
