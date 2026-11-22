/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.script.ShaderCatalog
import com.mtwabpin.kolahal.script.ShaderClock
import com.mtwabpin.kolahal.script.ShaderDate
import com.mtwabpin.kolahal.script.ShaderMeta
import com.mtwabpin.kolahal.script.ShaderParams
import com.mtwabpin.kolahal.script.ShaderSafety
import com.mtwabpin.kolahal.script.ShaderTilt
import com.mtwabpin.kolahal.script.Shaders
import com.mtwabpin.kolahal.status.DeviceStatus
import com.mtwabpin.kolahal.status.Status
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** The shaders drawn now, by name. A test asks for it; a surface can't be asked what its layer does. */
internal object ActiveShaders {
    private val _names = MutableStateFlow<List<String>>(emptyList())
    val names: StateFlow<List<String>> = _names.asStateFlow()

    fun add(name: String) = _names.update { it + name }
    fun remove(name: String) = _names.update { it - name }
}

/** What a shader's drawing asks of the phone: its [phone] state for [ShaderClock], and where its [gravity] comes from. A test has its own. */
internal class ShaderSources(
    val phone: (Context) -> ShaderClock.Phone = ::phoneOf,
    val gravity: (Context) -> GravitySource = ::PhoneGravity,
)

internal val LocalShaderSources = staticCompositionLocalOf { ShaderSources() }

/**
 * `screen.shader` over a TTY surface (home on the grid, a full-screen screen): a [RenderEffect] on its
 * layer, so the shader works on what is drawn and the grid knows nothing of it. Nothing when [on] is
 * false, or when no shader can be drawn: no shader is set, it doesn't compile, it was turned off
 * (Kolahal crashed while it was on), a screen reader is on, or Android is older than 13.
 *
 * Put it first in the surface's modifier chain, before its background, so the shader covers it too.
 * [cols] and [left] are the grid's columns and where they start, in px, when the caller knows them
 * (otherwise what fits, in the middle).
 *
 * - The shader gets only the uniforms it declares ([ShaderMeta.KNOWN]).
 * - A still shader is drawn only when what it shows or a touch changes; one that is `@animated` at
 *   most [ShaderClock.FPS] times a second, while home is in front ([ShaderClock.moves]).
 * - A still shader that declares `date` is also drawn once a minute, while home is in front ([FollowDate]).
 * - One that declares `tilt` follows the phone's lean, under the same conditions as one that moves
 *   ([FollowTilt]). Nothing listens to the motion sensor for any other.
 * - One with parameters is also drawn when one is changed ([ShaderParams]).
 * - One that declares `battery` or `charging` is also drawn when they change ([FollowBattery]).
 * - [ShaderSafety] turns one off that Kolahal crashed with. A slow one is the user's to turn off.
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

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val touch = remember(shader) { ShaderTouch() }
    val clock = remember(shader) { ShaderTime() }
    ActiveMark(shader.name)
    ArmWhileShown(shader.id, safety, lifecycle)
    ProveWhenShown(shader.id, safety, lifecycle)
    if (shader.meta.animated) AnimateShader(shader, clock, touch)
    val day = remember(shader) { ShaderDay() }
    if (!shader.meta.animated && shader.meta.uses(ShaderDate.NAME)) FollowDate(shader, day)
    if (shader.meta.tilt) FollowTilt(shader, touch)
    val battery = rememberShaderBattery(shader)
    val params = ShaderParams.values.collectAsStateWithLifecycle()

    val inputs = remember(shader, grid, cols, left) {
        ShaderInputs(shader, grid, cols, left, params) { Shaders.report(context, shader.name, shader.id, it) }
    }
    return Modifier
        .shaderEffect(inputs, clock, touch, battery, day)
        .pointerInput(shader) {
            watchTouches(
                onTouch = { touch.at(it, clock.seconds) },
                onPress = { touch.pressed = it },
                onHold = { Shaders.offByHand(context) },
            )
        }
}

/** The effect of [inputs] over what this modifier is on. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Modifier.shaderEffect(
    inputs: ShaderInputs, clock: ShaderTime, touch: ShaderTouch, battery: ShaderBattery, day: ShaderDay? = null,
): Modifier =
    graphicsLayer {
        renderEffect = if (size.width > 0f && size.height > 0f) {
            inputs.effect(size.width, size.height, clock, touch, battery, day)
        } else null
        clip = true
    }

/**
 * A shader's [code] over a small sample in Settings, drawn as [rememberTtyShader] draws it. Null until
 * it is compiled, and when it can't be drawn (it doesn't compile, a screen reader is on, or Android is
 * older than 13).
 *
 * It is only looked at, so it is kept out of what judges the shader on home: no mark in
 * [ShaderSafety], no line in the log and no touches. Home isn't drawn while it is.
 * It leans with the phone, as on home, until it goes or a minute has passed. With [moves] off it stands still.
 */
@Composable
internal fun rememberShaderPreview(name: String, code: String, grid: TtyGrid, cols: Int = 0, left: Int = 0, moves: Boolean = true): Modifier? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return previewLayer(name, code, grid, cols, left, moves)
}

/** Home as a preview shows it with the shader [name] over it ([rememberShaderPreview]): nothing until it is read and compiled. */
@Composable
internal fun rememberPeekShader(name: String, grid: TtyGrid, cols: Int = 0, left: Int = 0): Modifier {
    val context = LocalContext.current.applicationContext
    val code by produceState<String?>(null, name) { value = withContext(Dispatchers.IO) { Shaders.source(context, name) } }
    return code?.let { rememberShaderPreview(name, it, grid, cols, left) } ?: Modifier
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun previewLayer(name: String, code: String, grid: TtyGrid, cols: Int, left: Int, moves: Boolean): Modifier? {
    if (rememberScreenReader()) return null
    val loaded by produceState<LoadedShader?>(null, name, code) {
        value = withContext(Dispatchers.IO) { LoadedShader.compile(name, code) }
    }
    val shader = loaded ?: return null
    val touch = remember(shader) { ShaderTouch() }
    val clock = remember(shader) { ShaderTime() }
    if (shader.meta.animated && moves) AnimateShader(shader, clock, touch)
    if (shader.meta.tilt && moves) FollowTilt(shader, touch)
    val battery = rememberShaderBattery(shader)
    val params = ShaderParams.values.collectAsStateWithLifecycle()
    val inputs = remember(shader, grid, cols, left) { ShaderInputs(shader, grid, cols, left, params) { } }
    return Modifier.shaderEffect(inputs, clock, touch, battery)
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

/**
 * Says that the shader [id] is proven once it has been in front for [PROVEN_MS]: a later crash isn't
 * blamed on it. How fast it is is not judged: one that is slow is for the user to turn off.
 */
@Composable
private fun ProveWhenShown(id: String, safety: ShaderSafety, lifecycle: Lifecycle) {
    LaunchedEffect(id, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(PROVEN_MS)
            safety.proven(id)
        }
    }
}

/** Moves the shader's [clock] while home is in front, when the phone's state allows it ([ShaderClock.moves]). */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AnimateShader(shader: LoadedShader, clock: ShaderTime, touch: ShaderTouch) {
    val context = LocalContext.current
    val sources = LocalShaderSources.current
    val shown = LocalTerminalShown.current
    val focused = LocalWindowInfo.current.isWindowFocused
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(shader, shown, focused) {
        if (!shown || !focused) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val frameMs = 1_000L / ShaderClock.FPS
            var last = -1L
            var checkedAt = -1L
            try {
                while (true) {
                    val now = withFrameNanos { it } / 1_000_000L
                    if (checkedAt < 0 || now - checkedAt >= CHECK_MS) {
                        clock.moving = ShaderClock.moves(sources.phone(context), SystemClock.uptimeMillis() - touch.uptime)
                        checkedAt = now
                    }
                    if (clock.moving) {
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
            } finally {
                clock.moving = false
            }
        }
    }
}

/**
 * Draws a still shader that declares `date` again at each full minute, while home is in front: a sky
 * follows the day without a frame loop. No timer runs behind another screen or app.
 */
@Composable
private fun FollowDate(shader: LoadedShader, day: ShaderDay) {
    val shown = LocalTerminalShown.current
    val focused = LocalWindowInfo.current.isWindowFocused
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(shader, shown, focused) {
        if (!shown || !focused) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                // Coming back to home counts too: the minute may have passed meanwhile.
                day.minute++
                delay(ShaderDate.untilNextMinute(System.currentTimeMillis()))
            }
        }
    }
}

/**
 * Keeps the phone's lean live ([LiveTilt]) for a shader that declares `tilt`, while home is in front and
 * the phone's state allows a moving shader ([ShaderClock.moves]). Otherwise it lets go: the lean goes
 * back to 0, and nothing runs until a touch, or until the battery's level or the charger changes.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun FollowTilt(shader: LoadedShader, touch: ShaderTouch) {
    val context = LocalContext.current
    val sources = LocalShaderSources.current
    val shown = LocalTerminalShown.current
    val focused = LocalWindowInfo.current.isWindowFocused
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(shader, shown, focused) {
        if (!shown || !focused) return@LaunchedEffect
        val status = DeviceStatus.get(context).status
        fun power(s: Status) = s.charging to s.batteryPct
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                while (true) {
                    val touched = touch.uptime
                    val powered = power(status.value)
                    if (ShaderClock.moves(sources.phone(context), SystemClock.uptimeMillis() - touched)) {
                        LiveTilt.hold(context, touch, sources.gravity)
                        delay(CHECK_MS)
                    } else {
                        LiveTilt.release(touch)
                        // A drag on a phone that stays in battery saver asks once a second, not at each move.
                        delay(IDLE_CHECK_MS)
                        merge(
                            snapshotFlow { touch.uptime != touched },
                            status.map { power(it) != powered },
                        ).first { it }
                    }
                }
            } finally {
                LiveTilt.release(touch)
            }
        }
    }
}

/** The battery for [shader], which follows it when it declares it ([FollowBattery]). */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun rememberShaderBattery(shader: LoadedShader): ShaderBattery {
    val context = LocalContext.current
    val battery = remember(shader) { ShaderBattery(DeviceStatus.get(context).status.value) }
    val uses = shader.meta.uses(ShaderMeta.BATTERY) || shader.meta.uses(ShaderMeta.CHARGING)
    if (uses) FollowBattery(shader, battery)
    return battery
}

/** Keeps [battery] at the status the header shows, so it has no receiver of its own. Only a value that changes draws the shader again. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun FollowBattery(shader: LoadedShader, battery: ShaderBattery) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(shader) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            DeviceStatus.get(context).status.collect { battery.set(it) }
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

    /** It moves now, so the shader is drawn again and again as it is. */
    var moving by mutableStateOf(false)
}

/** Counts the minutes a still shader with `date` was drawn for: a change draws it again. */
private class ShaderDay {
    var minute by mutableIntStateOf(0)
}

/** The battery as a shader reads it. It starts at [status]. */
private class ShaderBattery(status: Status) {
    /** How full it is, 0 to 1. Full while it isn't known. */
    var level by mutableFloatStateOf(1f)
        private set

    /** 1 while the phone is plugged in, else 0. */
    var charging by mutableFloatStateOf(0f)
        private set

    init {
        set(status)
    }

    fun set(status: Status) {
        level = (status.batteryPct ?: 100).coerceIn(0, 100) / 100f
        charging = if (status.charging) 1f else 0f
    }
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
    var uptime by mutableLongStateOf(SystemClock.uptimeMillis())
        private set

    /** A finger is down on the surface. */
    var pressed by mutableStateOf(false)

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

        /** The shader [name] with [code], for a preview: nothing is marked or logged. Null when it can't be drawn. Not on the main thread. */
        fun compile(name: String, code: String): LoadedShader? {
            if (ShaderMeta.problems(code).isNotEmpty()) return null
            val runtime = try {
                RuntimeShader(code)
            } catch (_: RuntimeException) {
                return null
            }
            return LoadedShader(ShaderSafety.id(name, code), name, ShaderMeta.parse(code), runtime)
        }
    }
}

/**
 * Sets the uniforms of a [LoadedShader] and makes its effect. Uniforms it doesn't declare are skipped.
 * One that Android refuses in spite of [ShaderMeta.problems] goes to [onError] (a line in the log), and
 * no effect is drawn: home never stops for a shader.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ShaderInputs(
    private val shader: LoadedShader,
    grid: TtyGrid,
    private val cols: Int,
    private val left: Int,
    /** What every shader's parameters are set to: read as it draws, so a change draws it again. */
    private val params: State<Map<String, Map<String, String>>>,
    private val onError: (String) -> Unit,
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
    private val usesDate = declared[ShaderDate.NAME]?.floats == ShaderDate.FLOATS
    private val usesTilt = declared[ShaderTilt.NAME]?.floats == ShaderTilt.FLOATS
    private val usesPressed = ShaderMeta.PRESSED in declared
    private val usesBattery = ShaderMeta.BATTERY in declared
    private val usesCharging = ShaderMeta.CHARGING in declared

    /** The effect for a surface [w] × [h] px. Null when a uniform can't be set. */
    fun effect(
        w: Float, h: Float, clock: ShaderTime, touch: ShaderTouch, battery: ShaderBattery, day: ShaderDay? = null,
    ): androidx.compose.ui.graphics.RenderEffect? {
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
            if (usesPressed) float1(ShaderMeta.PRESSED, if (touch.pressed) 1f else 0f)
            if (usesBattery) float1(ShaderMeta.BATTERY, battery.level)
            if (usesCharging) float1(ShaderMeta.CHARGING, battery.charging)
            if (usesTilt) {
                // A shader that moves is drawn anyway, and takes the lean as it is. One that stands still
                // is drawn again when the lean changes.
                if (!usesClock || !clock.moving) LiveTilt.follow()
                runtime.setFloatUniform(ShaderTilt.NAME, LiveTilt.x, LiveTilt.y)
            }
            if (usesDate) {
                // Read, so the minute's change draws a still shader again.
                day?.minute
                val now = ShaderDate.of(LocalDateTime.now())
                runtime.setFloatUniform(ShaderDate.NAME, now[0], now[1], now[2], now[3])
            }
            if (shader.meta.params.isNotEmpty()) {
                val set = params.value[shader.name].orEmpty()
                for (p in shader.meta.params) float1(p.name, ShaderParams.of(set, p))
            }
            for ((name, ink) in COLORS) color(name, palette.color(ink))
            RenderEffect.createRuntimeShaderEffect(runtime, ShaderMeta.INPUT).asComposeRenderEffect()
        } catch (e: RuntimeException) {
            failed = true
            onError(e.message ?: "it can't be drawn")
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

/** How long a shader must be in front, without a crash, to be proven. */
private const val PROVEN_MS = 10_000L

/** How long two fingers are held for the shader to go off. */
private const val HOLD_MS = 1_000L

/** How often the phone's state is checked while an animated shader moves. */
private const val CHECK_MS = 2_000L

/** How often it is checked while it stands still. */
private const val IDLE_CHECK_MS = 1_000L

/**
 * Follows touches without taking any: the last position ([onTouch]), whether a finger is down
 * ([onPress], after its position), and two fingers held still for [HOLD_MS] ([onHold]).
 *
 * A gesture that Android cancels comes as an event with no finger down, so it ends the press as well.
 */
internal suspend fun PointerInputScope.watchTouches(
    onTouch: (Offset) -> Unit,
    onPress: (Boolean) -> Unit,
    onHold: () -> Unit,
) = try {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.filter { it.pressed }
            down.firstOrNull()?.let { onTouch(it.position) }
            onPress(down.isNotEmpty())
            if (event.type == PointerEventType.Press && down.size >= 2) {
                if (held(down.associate { it.id to it.position })) onHold()
                // Waiting for them took events, perhaps the one that lifted the last finger.
                onPress(currentEvent.changes.any { it.pressed })
            }
        }
    }
} finally {
    // Nobody follows the finger any more.
    onPress(false)
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
