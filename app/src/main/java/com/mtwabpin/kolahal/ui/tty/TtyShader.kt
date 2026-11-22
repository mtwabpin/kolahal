/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.script.ShaderCatalog
import com.mtwabpin.kolahal.script.ShaderClock
import com.mtwabpin.kolahal.script.ShaderMeta
import com.mtwabpin.kolahal.script.ShaderSafety
import com.mtwabpin.kolahal.script.ShaderWatchdog
import com.mtwabpin.kolahal.script.Shaders
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.ui.LocalTerminalShown
import com.mtwabpin.kolahal.ui.rememberScreenReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** The shaders drawn now, by name. A test asks for it; a surface can't be asked what its layer does. */
internal object ActiveShaders {
    private val _names = MutableStateFlow<List<String>>(emptyList())
    val names: StateFlow<List<String>> = _names.asStateFlow()

    fun add(name: String) = _names.update { it + name }
    fun remove(name: String) = _names.update { it - name }
}

/**
 * `screen.shader` over a TTY surface (home on the grid, a full-screen screen): a [RenderEffect] on its
 * layer, so the shader works on what is drawn and the grid knows nothing of it. Nothing when [on] is
 * false, or when no shader can be drawn: no shader is set, it doesn't compile, it was turned off
 * (too slow, or Kolahal crashed while it was on), a screen reader is on, or Android is older than 13.
 *
 * Put it first in the surface's modifier chain, before its background, so the shader covers it too.
 * [cols] and [left] are the grid's columns and where they start, in px, when the caller knows them
 * (otherwise what fits, in the middle).
 *
 * - The shader gets only the uniforms it declares ([ShaderMeta.KNOWN]).
 * - A still shader is drawn only when what it shows or a touch changes; one that is `@animated` at
 *   most [ShaderClock.FPS] times a second, while home is in front ([ShaderClock.moves]).
 * - [ShaderWatchdog] turns a slow one off, and [ShaderSafety] one that Kolahal crashed with.
 * - Two fingers held still for a second turn the shader off, whatever it does to the screen.
 */
@Composable
internal fun rememberTtyShader(
    name: String,
    grid: TtyGrid,
    cols: Int = 0,
    left: Int = 0,
    on: Boolean = true,
): Modifier {
    if (!on || name == ShaderCatalog.OFF) return Modifier
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return Modifier
    return shaderLayer(name, grid, cols, left)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun shaderLayer(name: String, grid: TtyGrid, cols: Int, left: Int): Modifier {
    // A person listening to the screen isn't looking at it: nothing is drawn over it, and nothing costs power.
    if (rememberScreenReader()) return Modifier
    val context = LocalContext.current.applicationContext
    val safety = remember { ShaderSafety.get(context) }
    // Read again when a file changes, or a shader is turned off or on again.
    val files by Shaders.version.collectAsStateWithLifecycle()
    val trips by safety.version.collectAsStateWithLifecycle()
    val loaded by produceState<LoadedShader?>(null, name, files, trips) {
        var ready: LoadedShader? = null
        try {
            // It finishes even when the shader changes meanwhile, so its mark in the store is known ([ShaderSafety.begin]).
            withContext(NonCancellable + Dispatchers.IO) { ready = LoadedShader.load(context, name) }
            value = ready
            awaitCancellation()
        } finally {
            // Never drawn: its mark goes. A surface that draws it keeps it until it stops ([ArmWhileShown]).
            ready?.let { safety.cancel(it.id) }
        }
    }
    val shader = loaded ?: return Modifier

    val view = LocalView.current
    val window = remember(view) { view.context.findActivity()?.window }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val touch = remember(shader) { ShaderTouch() }
    val clock = remember(shader) { ShaderTime() }
    ActiveMark(shader.name)
    ArmWhileShown(shader.id, safety, lifecycle)
    if (window != null) WatchFrames(shader, safety, window, view.display?.refreshRate ?: 60f, context)
    if (shader.meta.animated) AnimateShader(shader, clock, touch)

    val inputs = remember(shader, grid, cols, left) { ShaderInputs(context, shader, grid, cols, left) }
    return Modifier
        .graphicsLayer {
            renderEffect = if (size.width > 0f && size.height > 0f) {
                inputs.effect(size.width, size.height, clock, touch)
            } else null
            clip = true
        }
        .pointerInput(shader) {
            watchTouches(
                onTouch = { touch.at(it, clock.seconds) },
                onHold = { Shaders.offByHand(context) },
            )
        }
}

/** Says in [ActiveShaders] that [name] is drawn. */
@Composable
private fun ActiveMark(name: String) {
    DisposableEffect(name) {
        ActiveShaders.add(name)
        onDispose { ActiveShaders.remove(name) }
    }
}

/** While the shader [id] is on screen, it is marked in the store, until it has run well for a while ([ShaderSafety.arm]). */
@Composable
private fun ArmWhileShown(id: String, safety: ShaderSafety, lifecycle: Lifecycle) {
    DisposableEffect(id, lifecycle) {
        var armed = false
        fun arm(on: Boolean) {
            if (armed == on) return
            armed = on
            if (on) safety.arm(id) else safety.disarm(id)
        }
        // The observer hears what it missed at once, so a screen that is already up is armed here.
        val watch = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> arm(true)
                Lifecycle.Event.ON_STOP -> arm(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(watch)
        onDispose {
            lifecycle.removeObserver(watch)
            arm(false)
        }
    }
}

/** Times the window's frames while the shader is on ([ShaderWatchdog]), and turns it off when they are too slow. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun WatchFrames(shader: LoadedShader, safety: ShaderSafety, window: Window, refreshRate: Float, context: Context) {
    DisposableEffect(shader, window, refreshRate) {
        val budget = ShaderWatchdog.budgetFor(refreshRate)
        val dog = ShaderWatchdog(budget)
        fun ms(ns: Long) = (ns / 1_000_000.0).roundToInt()
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 0L) {
                val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
                // What the GPU spent on it, where the phone measures it. The whole frame's time is no
                // measure of the shader: it has the wait for the screen in it.
                val gpu = metrics.getMetric(FrameMetrics.GPU_DURATION).takeIf { it > 0 }
                when (val verdict = dog.frame(gpu, total)) {
                    ShaderWatchdog.Verdict.Fine -> Unit
                    ShaderWatchdog.Verdict.Proven -> safety.proven(shader.id)
                    is ShaderWatchdog.Verdict.Slow -> Shaders.turnOff(
                        context, shader.id,
                        "the GPU takes ${ms(verdict.frameNs)} ms or more on one frame in ten, over the limit of ${ms(budget)} ms",
                    )

                    is ShaderWatchdog.Verdict.Stalled -> Shaders.turnOff(
                        context, shader.id, "${ShaderWatchdog.STALLS} frames took longer than ${ms(ShaderWatchdog.STALL_NS)} ms",
                    )
                }
            }
        }
        window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper()))
        onDispose { runCatching { window.removeOnFrameMetricsAvailableListener(listener) } }
    }
}

/** Moves the shader's [clock] while home is in front, when the phone's state allows it ([ShaderClock.moves]). */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AnimateShader(shader: LoadedShader, clock: ShaderTime, touch: ShaderTouch) {
    val context = LocalContext.current
    val shown = LocalTerminalShown.current
    val focused = LocalWindowInfo.current.isWindowFocused
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(shader, shown, focused) {
        if (!shown || !focused) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val frameMs = 1_000L / ShaderClock.FPS
            var last = -1L
            var checkedAt = -1L
            var moving = false
            while (true) {
                val now = withFrameNanos { it } / 1_000_000L
                if (checkedAt < 0 || now - checkedAt >= CHECK_MS) {
                    moving = ShaderClock.moves(phoneOf(context), SystemClock.uptimeMillis() - touch.uptime)
                    checkedAt = now
                }
                if (moving) {
                    // A long gap (a pause) counts as one frame, so nothing jumps.
                    val delta = if (last < 0) frameMs else (now - last).coerceIn(0L, 250L)
                    clock.seconds += delta / 1000f
                    last = now
                    val spent = (System.nanoTime() / 1_000_000L - now).coerceAtLeast(0L)
                    // Wakes just before the next frame it wants.
                    delay((frameMs - spent - 4).coerceIn(0L, frameMs))
                } else {
                    // Still: it costs nothing. A touch, or the phone charging, starts it again.
                    last = -1L
                    delay(IDLE_CHECK_MS)
                    checkedAt = -1L
                }
            }
        }
    }
}

/** A phone's state for [ShaderClock]. */
private fun phoneOf(context: Context): ShaderClock.Phone {
    val power = context.getSystemService(PowerManager::class.java)
    val battery = context.getSystemService(BatteryManager::class.java)
    return ShaderClock.Phone(
        batterySaver = power?.isPowerSaveMode == true,
        animationsOff = !ValueAnimator.areAnimatorsEnabled(),
        hot = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                (power?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_MODERATE,
        charging = battery?.isCharging == true,
        batteryPercent = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 } ?: 100,
    )
}

/** The shader's own clock, in seconds: it moves only while the shader is animated and home is in front. */
private class ShaderTime {
    var seconds by mutableFloatStateOf(0f)
}

/** The last touch on the surface, in px, and when it came on the shader's clock. */
private class ShaderTouch {
    var x by mutableFloatStateOf(-1f)
        private set
    var y by mutableFloatStateOf(-1f)
        private set

    /** [ShaderTime.seconds] at the touch; below 0 when there was none. */
    var atSeconds by mutableFloatStateOf(-1f)
        private set

    /** When the last touch came, by the phone's uptime. Turning a shader on counts as a touch. */
    var uptime = SystemClock.uptimeMillis()
        private set

    fun at(position: Offset, seconds: Float) {
        x = position.x
        y = position.y
        atSeconds = seconds
        uptime = SystemClock.uptimeMillis()
    }
}

/** A shader ready to draw: compiled, with the uniforms it declares. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class LoadedShader(val id: String, val name: String, val meta: ShaderMeta, val runtime: RuntimeShader) {
    companion object {
        /** The shader [name], or null when it can't be drawn. Why goes in the log, once. Not on the main thread. */
        fun load(context: Context, name: String): LoadedShader? {
            Shaders.recover(context)
            val source = Shaders.source(context, name) ?: return null
            val id = ShaderSafety.id(name, source)
            val safety = ShaderSafety.get(context)
            if (safety.isTripped(id)) return null
            ShaderMeta.problems(source).firstOrNull()?.let {
                Shaders.report(context, name, id, it)
                return null
            }
            // Marked before it compiles: a crash in the compiler turns it off at the next start, too.
            safety.begin(id)
            val runtime = try {
                RuntimeShader(source)
            } catch (e: RuntimeException) {
                safety.cancel(id)
                Shaders.report(context, name, id, ShaderMeta.compileError(e.message.orEmpty()).toString())
                return null
            }
            return LoadedShader(id, name, ShaderMeta.parse(source), runtime)
        }
    }
}

/**
 * Sets the uniforms of a [LoadedShader] and makes its effect. Uniforms it doesn't declare are skipped.
 * One that Android refuses in spite of [ShaderMeta.problems] is an error in the log, and no effect is
 * drawn: home never stops for a shader.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ShaderInputs(
    private val context: Context,
    private val shader: LoadedShader,
    grid: TtyGrid,
    private val cols: Int,
    private val left: Int,
) {
    private val runtime = shader.runtime
    private val animated = shader.meta.animated
    private val declared = shader.meta.uniforms.associateBy { it.name }
    private val cellW = grid.metrics.cellW.toFloat()
    private val cellH = grid.metrics.cellH.toFloat()
    private val palette = grid.palette
    private val dark =
        if (TtyPalette.luminance(palette.color(Ink.BG)) < TtyPalette.luminance(palette.color(Ink.FG))) 1f else 0f
    private var failed = false

    // Only a shader that declares the clock or the touch reads them, so a frame or a touch that can't
    // change a still shader doesn't draw it again.
    private val usesTouch = "touch" in declared || "touchAge" in declared
    private val usesClock = animated && ("time" in declared || "touchAge" in declared)

    /** The effect for a surface [w] × [h] px. Null when a uniform can't be set. */
    fun effect(w: Float, h: Float, clock: ShaderTime, touch: ShaderTouch): androidx.compose.ui.graphics.RenderEffect? {
        if (failed) return null
        val seconds = if (usesClock) clock.seconds else 0f
        val touched = usesTouch && touch.atSeconds >= 0f
        return try {
            float1("time", seconds)
            float2("resolution", w, h)
            float2("cell", cellW, cellH)
            val columns = if (cols > 0) cols else (w / cellW).toInt()
            float2("grid", columns.toFloat(), (h / cellH).toInt().toFloat())
            float2("origin", if (cols > 0) left.toFloat() else (w - columns * cellW) / 2f, 0f)
            float1("dark", dark)
            if (usesTouch) {
                float2("touch", if (touched) touch.x else -1f, if (touched) touch.y else -1f)
                // A still shader is drawn at the touch, and then not again: its age stays 0.
                float1("touchAge", if (!touched) NEVER else if (animated) (seconds - touch.atSeconds).coerceAtLeast(0f) else 0f)
            }
            for ((name, ink) in COLORS) color(name, palette.color(ink))
            RenderEffect.createRuntimeShaderEffect(runtime, ShaderMeta.INPUT).asComposeRenderEffect()
        } catch (e: RuntimeException) {
            failed = true
            Shaders.report(context, shader.name, shader.id, e.message ?: "it can't be drawn")
            null
        }
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

    private companion object {
        /** `touchAge` before any touch: longer than any ring or fade. */
        const val NEVER = 1000f

        val COLORS = listOf(
            "bg" to Ink.BG, "surface" to Ink.SURFACE, "fg" to Ink.FG, "dim" to Ink.DIM, "accent" to Ink.PROMPT,
            "error" to Ink.ERROR, "warn" to Ink.WARN, "good" to Ink.GOOD, "info" to Ink.INFO, "link" to Ink.LINK,
        )
    }
}

/** How long two fingers are held for the shader to go off. */
private const val HOLD_MS = 1_000L

/** How often the phone's state is checked while an animated shader moves. */
private const val CHECK_MS = 2_000L

/** How often it is checked while it stands still. */
private const val IDLE_CHECK_MS = 1_000L

/**
 * Follows touches without taking any: the last position ([onTouch]), and two fingers held still for
 * [HOLD_MS] ([onHold]).
 */
private suspend fun PointerInputScope.watchTouches(onTouch: (Offset) -> Unit, onHold: () -> Unit) = awaitPointerEventScope {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val down = event.changes.filter { it.pressed }
        down.firstOrNull()?.let { onTouch(it.position) }
        if (event.type == PointerEventType.Press && down.size >= 2 && held(down.associate { it.id to it.position })) onHold()
    }
}

/** Waits until the two fingers ([from], where they went down) have been still for [HOLD_MS]: false when one lifts or moves. */
private suspend fun AwaitPointerEventScope.held(from: Map<PointerId, Offset>): Boolean {
    val slop = viewConfiguration.touchSlop * 2
    val moved = withTimeoutOrNull(HOLD_MS) {
        var still = true
        while (still) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.filter { it.pressed }
            still = down.size >= 2 && down.all { c ->
                from[c.id]?.let { (c.position - it).getDistance() <= slop } ?: true
            }
        }
        false
    }
    return moved == null
}

/** The activity this view is in: its window's frames are timed. */
private fun Context.findActivity(): Activity? {
    var at: Context? = this
    while (at is ContextWrapper) {
        if (at is Activity) return at
        at = at.baseContext
    }
    return null
}
