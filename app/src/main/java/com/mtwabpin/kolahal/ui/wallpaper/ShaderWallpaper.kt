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
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import com.mtwabpin.kolahal.script.ShaderClock
import com.mtwabpin.kolahal.script.ShaderDate
import com.mtwabpin.kolahal.script.ShaderFrame
import com.mtwabpin.kolahal.script.ShaderMeta
import com.mtwabpin.kolahal.script.ShaderParams
import com.mtwabpin.kolahal.script.ShaderPhone
import com.mtwabpin.kolahal.script.ShaderTilt
import com.mtwabpin.kolahal.script.ShaderUniforms
import com.mtwabpin.kolahal.script.ShaderValues
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
 * - A shader that moves is drawn [ShaderClock.FPS] times a second, under the same conditions as on home
 *   ([ShaderClock.moves]); one that stands still, when something it shows changes.
 * - A shader that crashed it [MAX_TRIES] times in a row isn't tried again until its code changes: then
 *   the page shows alone.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class ShaderWallpaper : WallpaperService() {
    private val engines = LinkedHashSet<ShaderEngine>()
    private val main = Handler(Looper.getMainLooper())

    /** Home wrote another state. */
    private var watch: FileObserver? = null

    override fun onCreate() {
        super.onCreate()
        val dir = WallpaperState.dir(this).apply { mkdirs() }
        watch = object : FileObserver(dir, MOVED_TO or CLOSE_WRITE) {
            override fun onEvent(event: Int, path: String?) {
                if (path != null && path.startsWith("state")) main.post { engines.forEach { it.load() } }
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
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                battery = (level.toFloat() / scale).coerceIn(0f, 1f)
                charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                aim()
                draw()
            }
        }

        /** The hour and the values that follow it move on. */
        private val minute = object : Runnable {
            override fun run() {
                aim()
                draw()
                main.postDelayed(this, ShaderDate.untilNextMinute(System.currentTimeMillis()))
            }
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
            if (before != null) notifyColorsChanged()
            if (seen) restart() else draw()
        }

        private fun compile(next: WallpaperState) {
            runtime = null
            meta = null
            uniforms = null
            frame.seconds = 0f
            if (!Guard.mayTry(this@ShaderWallpaper, next.code) || ShaderMeta.problems(next.code).isNotEmpty()) return
            Guard.trying(this@ShaderWallpaper, next.code)
            runtime = try {
                RuntimeShader(next.code)
            } catch (_: RuntimeException) {
                Guard.proven(this@ShaderWallpaper)
                return
            }
            meta = ShaderMeta.parse(next.code)
            main.removeCallbacks(proven)
            main.postDelayed(proven, PROVEN_MS)
        }

        private val proven = Runnable { Guard.proven(this@ShaderWallpaper) }

        private fun prepare(next: WallpaperState) {
            val shader = runtime ?: return
            val colors = if (dark) next.dark else next.light
            val page = colors["bg"] ?: 0xFF000000.toInt()
            // A page of one color is what there is to draw over.
            shader.setInputShader(ShaderMeta.INPUT, LinearGradient(0f, 0f, 0f, 1f, page, page, Shader.TileMode.CLAMP))
            uniforms = ShaderUniforms(meta!!, shader, next.cellW, next.cellH, colors, dark, wallpaper = true)
            paint.shader = shader
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            ready = true
            frame.width = width.toFloat()
            frame.height = height.toFloat()
            draw()
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

        private fun restart() {
            stop()
            seen = true
            registerReceiver(power, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            aim()
            main.post(minute)
            if (uniforms?.usesTilt == true && allowed()) startTilt()
            wake()
        }

        private fun stop() {
            moving = false
            Choreographer.getInstance().removeFrameCallback(this)
            main.removeCallbacks(minute)
            runCatching { unregisterReceiver(power) }
            gravity?.stop()
            gravity = null
        }

        private fun allowed() = ShaderClock.moves(ShaderPhone.of(this@ShaderWallpaper), SystemClock.uptimeMillis() - touchedAt)

        private fun startTilt() {
            if (gravity != null) return
            tilt.restart()
            gravity = PhoneGravity(this@ShaderWallpaper).also { source ->
                source.start(OnGravity { x, y, z, shaky, rotation, atNs ->
                    if (tilt.gravity(x, y, z, shaky, rotation, atNs) && !moving) draw()
                })
            }
        }

        /** Draws once, and goes on while it moves. */
        private fun wake() {
            draw()
            if (!seen || uniforms == null) return
            val moves = uniforms?.usesClock == true && allowed()
            if ((moves || gliding) && !moving) {
                moving = true
                lastFrame = 0L
                checkedAt = SystemClock.uptimeMillis()
                Choreographer.getInstance().postFrameCallback(this)
            }
            if (uniforms?.usesTilt == true && allowed()) startTilt()
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!seen || !moving) return
            val now = frameTimeNanos / 1_000_000L
            val since = now - lastFrame
            if (lastFrame == 0L || since >= FRAME_MS - 4) {
                // A long gap (a pause) counts as one frame, so nothing jumps.
                val step = if (lastFrame == 0L) FRAME_MS else since.coerceIn(0L, 250L)
                lastFrame = now
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
                if (uniforms?.usesClock == true && going) frame.seconds += step / 1000f
                glide(step / 1000f)
                draw()
                if (!gliding && (uniforms?.usesClock != true || !going)) {
                    moving = false
                    return
                }
            }
            Choreographer.getInstance().postFrameCallback(this)
        }

        override fun onTouchEvent(event: MotionEvent) {
            touchedAt = SystemClock.uptimeMillis()
            frame.touchX = event.x
            frame.touchY = event.y
            frame.pressed = event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
            touchSeconds = frame.seconds
            if (uniforms?.usesTouch == true || uniforms?.usesPressed == true || !moving) wake()
        }

        private fun phone() = ShaderValues.Phone(battery, charging, LocalTime.now())

        /** Where the parameters are to be: at once when [now], or when people set them; else they glide there. */
        private fun aim(now: Boolean = false) {
            val m = meta ?: return
            val name = state?.name ?: return
            val phone = phone()
            val fresh = now || params.size != m.params.size
            if (fresh) {
                params = FloatArray(m.params.size)
                aims = FloatArray(m.params.size)
            }
            gliding = false
            m.params.forEachIndexed { i, p ->
                val followed = ShaderParams.followed(name, p, phone)
                aims[i] = followed ?: ShaderParams.of(name, p)
                val far = abs(aims[i] - params[i]) > (p.max - p.min) * 0.02f
                if (fresh || followed == null || p.choices.isNotEmpty() || !far) params[i] = aims[i] else gliding = true
            }
            if (gliding && seen && !moving) wake()
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

    /** Counts how often a shader was started and not proven, so one that crashes the process is left alone. */
    private object Guard {
        private fun file(context: Context) = File(WallpaperState.dir(context), "trying")

        private fun tries(context: Context, code: String): Int {
            val words = runCatching { file(context).readText().split(' ') }.getOrNull() ?: return 0
            return if (words.size == 2 && words[0] == code.hashCode().toString()) words[1].toIntOrNull() ?: 0 else 0
        }

        fun mayTry(context: Context, code: String) = tries(context, code) < MAX_TRIES

        fun trying(context: Context, code: String) {
            runCatching { file(context).writeText("${code.hashCode()} ${tries(context, code) + 1}") }
        }

        fun proven(context: Context) {
            file(context).delete()
        }
    }

    private companion object {
        const val FRAME_MS = 1_000L / ShaderClock.FPS

        /** How often the phone's state is checked while it moves. */
        const val CHECK_MS = 2_000L

        /** A parameter is two thirds of the way to the value it follows after this long. */
        const val GLIDE_S = 0.6f

        /** A shader that ran this long is proven. */
        const val PROVEN_MS = 10_000L
        const val MAX_TRIES = 3

        const val PICTURE_PX = 160f
        const val PICTURE_TRIES = 30
    }
}
