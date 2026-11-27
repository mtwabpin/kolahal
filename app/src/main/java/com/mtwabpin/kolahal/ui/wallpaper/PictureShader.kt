/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RuntimeShader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import androidx.annotation.RequiresApi
import com.mtwabpin.kolahal.shader.ShaderFrame
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderUniforms

/**
 * A scene compiled for Lab's pictures: drawn as the live wallpaper draws it ([ShaderWallpaper]), over a page of the
 * theme's color a step under opaque ([ShaderMeta.PAGE_ALPHA]) and with `wallpaper` at 1, at the moment, hour and
 * arrangement a [PictureLook] says. The composer's preview draws it on screen; [PictureRender] draws it at full size.
 * One instance is drawn from one thread at a time.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class PictureShader private constructor(val meta: ShaderMeta, private val runtime: RuntimeShader) {
    private val paint = Paint().apply { shader = runtime }
    private val frame = ShaderFrame()

    /**
     * Draws it [width] × [height] pixels on [canvas], as [look] says. Throws a RuntimeException when Android refuses
     * a uniform.
     */
    fun draw(canvas: Canvas, width: Int, height: Int, look: PictureLook) {
        givePage(runtime, look.colors["bg"] ?: 0xFF000000.toInt())
        val (cellW, cellH) = PictureMath.cell(look.cellW, look.cellH, look.screenW, width)
        val uniforms = ShaderUniforms(meta, runtime, cellW, cellH, look.colors, look.dark, wallpaper = true)
        uniforms.set(PictureMath.frame(look, width, height, frame)) { look.params.getOrElse(it) { 0f } }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    companion object {
        /** [code] compiled, or the compiler's error ([ShaderMeta.compileError]). Not on the main thread. */
        fun compile(code: String): Result<PictureShader> {
            val meta = ShaderMeta.parse(code)
            ShaderMeta.problems(code).firstOrNull()?.let { return Result.failure(IllegalArgumentException(it)) }
            return try {
                Result.success(PictureShader(meta, RuntimeShader(code)))
            } catch (e: RuntimeException) {
                Result.failure(IllegalArgumentException(ShaderMeta.compileError(e.message.orEmpty()).toString()))
            }
        }
    }
}

/** What drawing a picture at full size came to ([PictureRender.draw]). */
internal sealed class PictureResult {
    /** The picture, in sRGB. The one who gets it recycles it. */
    class Drawn(val bitmap: Bitmap) : PictureResult()

    /** This phone can't draw a picture so large. */
    data object TooLarge : PictureResult()

    /** There wasn't the memory for it. */
    data object OutOfMemory : PictureResult()

    /** The shader can't be drawn: [why], as the compiler or Android said it. */
    class Failed(val why: String) : PictureResult()
}

/**
 * Draws a picture at full size off the screen, as the live wallpaper draws its small picture for Android's colors:
 * into an [ImageReader]'s surface with [android.view.Surface.lockHardwareCanvas], its buffer then wrapped as a
 * [Bitmap] and copied once. So the most it holds is the GPU's buffer and that one copy.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal object PictureRender {
    /** How many times, 10 ms apart, it looks for the drawn frame before it gives up: 5 seconds. */
    private const val TRIES = 500

    @Volatile
    private var largest: Int? = null

    @Volatile
    private var read = false

    /**
     * The GPU's largest texture, in pixels a side (GL_MAX_TEXTURE_SIZE): a picture with a longer side can't be drawn.
     * Read once, from a small EGL context of its own. Null when it can't be read. Not on the main thread.
     */
    fun maxSide(): Int? {
        if (read) return largest
        largest = runCatching(::readMaxSide).getOrNull()?.takeIf { it > 0 }
        read = true
        return largest
    }

    private fun readMaxSide(): Int? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val wanted = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        if (!EGL14.eglChooseConfig(display, wanted, 0, configs, 0, 1, count, 0) || count[0] < 1) return null
        val config = configs[0] ?: return null
        val context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        if (context == EGL14.EGL_NO_CONTEXT) return null
        val surface = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        try {
            if (surface == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(display, surface, surface, context)) return null
            val max = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, max, 0)
            return max[0]
        } finally {
            // The display stays initialized: Android's own drawing uses it too.
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
        }
    }

    /**
     * Draws [code] [width] × [height] pixels as [look] says. Not on the main thread. [cancelled] is asked while it
     * waits for the frame: a picture no one waits for any more is let go.
     */
    fun draw(code: String, look: PictureLook, width: Int, height: Int, cancelled: () -> Boolean = { false }): PictureResult {
        maxSide()?.let { if (maxOf(width, height) > it) return PictureResult.TooLarge }
        val shader = PictureShader.compile(code).getOrElse { return PictureResult.Failed(it.message.orEmpty()) }
        val usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        return try {
            ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1, usage).use { reader ->
                val canvas = reader.surface.lockHardwareCanvas()
                val drawn = runCatching { shader.draw(canvas, width, height, look) }
                reader.surface.unlockCanvasAndPost(canvas)
                drawn.exceptionOrNull()?.let { return PictureResult.Failed(it.message ?: "it can't be drawn") }
                repeat(TRIES) {
                    if (cancelled()) return PictureResult.Failed("cancelled")
                    reader.acquireLatestImage()?.use { image ->
                        val buffer = image.hardwareBuffer ?: return PictureResult.TooLarge
                        return buffer.use { hardware ->
                            val wrapped = Bitmap.wrapHardwareBuffer(hardware, ColorSpace.get(ColorSpace.Named.SRGB))
                                ?: return PictureResult.TooLarge
                            try {
                                wrapped.copy(Bitmap.Config.ARGB_8888, false)?.let { PictureResult.Drawn(it) } ?: PictureResult.TooLarge
                            } finally {
                                wrapped.recycle()
                            }
                        }
                    }
                    Thread.sleep(10)
                }
                PictureResult.TooLarge
            }
        } catch (_: OutOfMemoryError) {
            PictureResult.OutOfMemory
        } catch (_: IllegalArgumentException) {
            // A size the surface can't have.
            PictureResult.TooLarge
        } catch (_: IllegalStateException) {
            PictureResult.TooLarge
        }
    }
}
