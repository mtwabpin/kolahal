/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.shaders

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.RoundedCorner
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.shader.ShaderFrame
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderOverApps
import com.mtwabpin.kolahal.shader.ShaderParams
import com.mtwabpin.kolahal.shader.ShaderPhone
import com.mtwabpin.kolahal.shader.ShaderUniforms
import com.mtwabpin.kolahal.shader.ShaderValues
import com.mtwabpin.kolahal.ui.theme.Palettes
import com.mtwabpin.kolahal.ui.theme.darkTheme
import com.mtwabpin.kolahal.ui.wallpaper.WallpaperState
import com.mtwabpin.kolahal.ui.wallpaper.WallpaperWriter
import java.time.LocalTime

/**
 * The window that plays a one-off shader over the app in front ([ShaderOverApps.Stage]). The Play build has
 * none: nothing there calls [install].
 * - One window over other apps (`TYPE_APPLICATION_OVERLAY`), added by the application's own window manager: no
 *   activity, no service and no notification. It takes no touch and no focus ([ShaderOverApps.WINDOW_FLAGS]) and
 *   is no more opaque than Android passes touches through ([ShaderOverApps.alpha]), so the app under it works
 *   as before. It covers the whole screen, the camera's cutout and under the bars too.
 * - A plain view draws the shader each frame with the uniforms it has on home ([ShaderUniforms]): the theme's
 *   colors, the cell's size, the battery, the screen's corner and cutout, its parameters, and `progress` from 0
 *   to 1. Nothing under it can be read: `content` is clear, and what the shader returns lies over the app. It
 *   has no touches and no lean.
 * - The window goes when the shader has run; when the status bar doesn't show, which a full-screen app in
 *   front hides ([ShaderOverApps.plays]); when the screen goes off; when one of Kolahal's own screens comes to
 *   front; when anything throws; and after [ShaderOverApps.LIMIT_MS] whatever happens.
 */
internal object ShaderOverlay : ShaderOverApps.Stage, Application.ActivityLifecycleCallbacks {
    private var installed = false

    /** How many of Kolahal's own screens are started: home, the editor. Main thread. */
    private var started = 0

    /** The window that is up, if one is. Main thread. */
    private var window: OverlayWindow? = null

    override val inFront: Boolean get() = started > 0

    /**
     * Home is being made in this process ([com.mtwabpin.kolahal.MainActivity]), before it starts: from now on
     * a word can play its shader over an app, and Kolahal's own screens are counted. Main thread.
     */
    fun install(app: Application) {
        if (installed) return
        installed = true
        app.registerActivityLifecycleCallbacks(this)
        ShaderOverApps.install(app, this)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun show(context: Context, ready: ShaderOverApps.Ready, now: ShaderOverApps.Now, ended: (played: Boolean) -> Unit): Boolean {
        if (window != null) return false
        val next = OverlayWindow(context.applicationContext, ready, now) { played ->
            window = null
            ended(played)
        }
        return try {
            next.add()
            window = next
            true
        } catch (_: RuntimeException) {
            // Android refused the window, or the shader: nothing stays, and it waits for home.
            next.drop()
            false
        }
    }

    override fun onActivityStarted(activity: Activity) {
        started++
        // Kolahal itself is in front: nothing plays over it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) window?.remove()
    }

    override fun onActivityStopped(activity: Activity) {
        // One that started before the count began stops without having been counted.
        started = (started - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

/**
 * One window over the app in front, for one shader ([ready]). [now]: what [ShaderOverApps.plays] said yes
 * to; the view asks it again each frame, with the status bar as it is. [onGone] comes once, when the window
 * is gone ([remove]): with true when a frame of the shader was drawn. Main thread.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class OverlayWindow(
    private val context: Context,
    private val ready: ShaderOverApps.Ready,
    private val now: ShaderOverApps.Now,
    private val onGone: (played: Boolean) -> Unit,
) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val view = ShaderView(context)
    private var added = false
    private var listening = false
    private var gone = false

    /** A frame of the shader was drawn: it has played, and doesn't play again on home. */
    private var drawn = false

    private val limit = Runnable { remove() }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = remove()
    }

    /** Puts the window up. Throws when Android refuses it, or when no touch would pass through one that can be seen. */
    fun add() {
        val alpha = ShaderOverApps.alpha(context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch)
        check(alpha > 0f) { "touches pass through no window that can be seen" }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            ShaderOverApps.WINDOW_FLAGS,
            PixelFormat.TRANSLUCENT,
        )
        params.alpha = alpha
        // The whole screen: beside the camera and under the bars.
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        params.fitInsetsTypes = 0
        params.title = TITLE
        manager.addView(view, params)
        added = true
        ContextCompat.registerReceiver(context, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        listening = true
        main.postDelayed(limit, ShaderOverApps.LIMIT_MS)
    }

    /** Takes the window away, once, and says so ([onGone]). Also from the view while it is drawn. */
    fun remove() {
        if (gone) return
        drop()
        onGone(drawn)
    }

    /** Takes away what [add] put up, without a word: for one that [add] couldn't finish. */
    fun drop() {
        gone = true
        main.removeCallbacks(limit)
        if (listening) runCatching { context.unregisterReceiver(screenOff) }
        listening = false
        if (added) runCatching { manager.removeViewImmediate(view) }
        added = false
    }

    /** Draws the shader over all of the window, again at each frame of the screen until it has run. */
    private inner class ShaderView(context: Context) : View(context) {
        private val paint = Paint()
        private val frame = ShaderFrame()
        private var uniforms: ShaderUniforms? = null
        private var params = FloatArray(0)

        /** When the frame before was drawn, by the phone's uptime; below 0 before the first. */
        private var last = -1L

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            // A rotation: another size, and the corner and the cutout are read again.
            measured = false
        }

        override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
            measured = false
            // The status bar may have gone: the next frame asks.
            invalidate()
            return super.onApplyWindowInsets(insets)
        }

        /** The corner and the cutout are read for the size and the insets it has now. */
        private var measured = false

        override fun onDraw(canvas: Canvas) {
            if (gone) return
            try {
                drawFrame(canvas)
            } catch (_: RuntimeException) {
                // A uniform that Android refuses, or a canvas that can't draw a shader: nothing stays.
                remove()
            }
        }

        private fun drawFrame(canvas: Canvas) {
            val insets = rootWindowInsets ?: return invalidate()
            // A full-screen app is in front when the status bar doesn't show: nothing plays over it.
            if (!ShaderOverApps.plays(now.copy(statusBar = insets.isVisible(WindowInsets.Type.statusBars())))) return remove()
            val set = uniforms ?: prepare()
            val at = SystemClock.uptimeMillis()
            // A long gap (a pause) counts as one frame, as on home.
            if (last >= 0) frame.seconds += (at - last).coerceIn(0L, 100L) / 1000f
            last = at
            if (frame.seconds >= ready.seconds) return remove()
            frame.width = width.toFloat()
            frame.height = height.toFloat()
            if (!measured) readScreen(insets)
            set.set(frame) { params[it] }
            canvas.drawRect(0f, 0f, frame.width, frame.height, paint)
            drawn = true
            postInvalidateOnAnimation()
        }

        /**
         * The uniforms as home sets them for a one-off ([ShaderUniforms]), from the theme and the phone as they
         * are now: the colors by role, dark or light, the cell's size, the battery, and the parameters as they
         * are set or as the values they follow make them.
         */
        private fun prepare(): ShaderUniforms {
            val config = Config.get(context)
            val dark = darkTheme(config, context.resources.configuration)
            val colors = WallpaperWriter.colors(Palettes.named(config[Keys.THEME], dark))
            val metrics = context.resources.displayMetrics
            fun px(size: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size.toFloat(), metrics)
            val (cellW, cellH) = WallpaperState.cell(px(config[Keys.FONT_SIZE]), px(config[Keys.LOG_LINE_HEIGHT]))
            // Nothing under the window can be read: the shader's input is clear.
            ready.runtime.setInputShader(
                ShaderMeta.INPUT,
                LinearGradient(0f, 0f, 0f, 1f, Color.TRANSPARENT, Color.TRANSPARENT, Shader.TileMode.CLAMP),
            )
            paint.shader = ready.runtime
            val phone = ShaderPhone.of(context)
            frame.battery = phone.batteryPercent / 100f
            frame.charging = if (phone.charging) 1f else 0f
            val values = ShaderValues.Phone(frame.battery, phone.charging, LocalTime.now())
            params = FloatArray(ready.meta.params.size) { ShaderParams.now(ready.name, ready.meta.params[it], values) }
            return ShaderUniforms(ready.meta, ready.runtime, cellW, cellH, colors, dark, once = ready.seconds).also { uniforms = it }
        }

        /** The screen's rounded corner (the largest) and the camera's cutout at the top, in px, as on home: the window is the screen. */
        private fun readScreen(insets: WindowInsets) {
            measured = true
            frame.corner = CORNERS.maxOf { insets.getRoundedCorner(it)?.radius ?: 0 }.toFloat()
            frame.cutout.fill(0f)
            val top = insets.displayCutout?.boundingRectTop ?: return
            if (top.isEmpty) return
            frame.cutout[0] = top.left.toFloat()
            frame.cutout[1] = top.top.toFloat()
            frame.cutout[2] = top.right.toFloat()
            frame.cutout[3] = top.bottom.toFloat()
        }
    }

    private companion object {
        /** The window's name, as Android's tools show it. */
        const val TITLE = "Kolahal one-off shader"

        val CORNERS = intArrayOf(
            RoundedCorner.POSITION_TOP_LEFT, RoundedCorner.POSITION_TOP_RIGHT,
            RoundedCorner.POSITION_BOTTOM_RIGHT, RoundedCorner.POSITION_BOTTOM_LEFT,
        )
    }
}
