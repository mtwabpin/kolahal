/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import android.content.Context
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.os.SystemClock
import android.view.RoundedCorner
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.ShaderClock
import com.mtwabpin.kolahal.shader.ShaderDate
import com.mtwabpin.kolahal.shader.ShaderFrame
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderParams
import com.mtwabpin.kolahal.shader.ShaderPhone
import com.mtwabpin.kolahal.shader.ShaderPlay
import com.mtwabpin.kolahal.shader.ShaderSafety
import com.mtwabpin.kolahal.shader.ShaderUniforms
import com.mtwabpin.kolahal.shader.ShaderValues
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.shader.WallpaperChoice
import com.mtwabpin.kolahal.status.DeviceStatus
import com.mtwabpin.kolahal.status.Status
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.ui.home.LocalTerminalShown
import com.mtwabpin.kolahal.ui.home.rememberScreenReader
import com.mtwabpin.kolahal.widgets.StatScenes
import java.time.LocalTime
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The shaders drawn now, by name. A test asks for it; a surface can't be asked what its layer does. */
internal object ActiveShaders {
    private val _names = MutableStateFlow<List<String>>(emptyList())
    val names: StateFlow<List<String>> = _names.asStateFlow()

    fun add(name: String) = _names.update { it + name }
    fun remove(name: String) = _names.update { it - name }
}

/** What a shader's drawing asks of the phone: its [phone] state for [ShaderClock], and where its [gravity] comes from. A test has its own. */
internal class ShaderSources(
    val phone: (Context) -> ShaderClock.Phone = ShaderPhone::of,
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
 *   most [ShaderClock.FPS] times a second, or as often as its `@fps` and `@settles` say ([ShaderClock.frameMs]),
 *   while home is in front ([ShaderClock.moves]).
 * - A still shader that declares `date` is also drawn once a minute, while home is in front ([FollowDate]).
 * - One that declares `tilt` follows the phone's lean, under the same conditions as one that moves
 *   ([FollowTilt]). Nothing listens to the motion sensor for any other.
 * - One with parameters is also drawn when one changes, by hand or with the value it follows ([rememberLiveParams]).
 * - One that declares `battery` or `charging` is also drawn when they change ([FollowBattery]).
 * - One that declares `corner` gets the window's rounded corners when the surface is the whole window
 *   ([WindowCorner]); it is drawn no more often for it. One that declares `cutout` gets the camera's
 *   cutout at the top of the window the same way.
 * - [ShaderSafety] turns one off that Kolahal crashed with. A slow one is the user's to turn off.
 * - Two fingers held still for a second turn the shader off, whatever it does to the screen.
 * - A shader loaded before is kept ([ShaderCache]): composed again, the surface doesn't read and compile
 *   it again, and has it in its first frame once it has been proven in this process.
 *
 * [home]: the surface is home's own. While a full-screen screen is over it ([ShaderPlay.covered]), its
 * shader stands still and isn't drawn again with the lean or the minute: the screen draws its own. Its
 * hold on the lean stays ([FollowTilt]), so the listener that it shares with the screen goes on when
 * the screen closes. The screen's host passes false.
 *
 * [over]: home's app selector, drawn over the shader's picture while one that isn't for text is drawn
 * ([OverShader]).
 */
@Composable
internal fun rememberTtyShader(
    name: String,
    grid: TtyGrid,
    cols: Int = 0,
    left: Int = 0,
    on: Boolean = true,
    home: Boolean = true,
    over: OverShader? = null,
): Modifier {
    if (!on || name == ShaderCatalog.OFF) return Modifier
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return Modifier
    return shaderLayer(name, grid, cols, left, home, over)
}

/** The shaders loaded last, for [rememberTtyShader]. */
private val loadedShaders = ShaderCache<LoadedShader>()

/** The shader proven last in this process ([ShaderSafety.proven]): its mark needs no write. Main thread. */
private var lastProven: String? = null

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun shaderLayer(name: String, grid: TtyGrid, cols: Int, left: Int, home: Boolean, over: OverShader?): Modifier {
    // A person listening to the screen isn't looking at it: nothing is drawn over it, and nothing costs power.
    if (rememberScreenReader()) return Modifier
    val context = LocalContext.current.applicationContext
    val safety = remember { ShaderSafety.get(context) }
    // Read again when a file changes, or a shader is turned off or on again.
    val files by Shaders.version.collectAsStateWithLifecycle()
    val trips by safety.version.collectAsStateWithLifecycle()
    // One loaded before and proven since is drawn at once: [ArmWhileShown] finds nothing to write.
    val known = loadedShaders.get(name, files, trips)?.takeIf { it.id == lastProven }
    val loaded by produceState(known, name, files, trips) {
        val at = files to trips
        var ready: LoadedShader? = null
        try {
            // It finishes even when the shader changes meanwhile, so its mark in the store is known ([ShaderSafety.begin]).
            withContext(NonCancellable + Dispatchers.IO) {
                // One loaded before is marked as a load marks it, without being read and compiled again.
                ready = (known ?: loadedShaders.get(name, at.first, at.second))?.also { safety.begin(it.id) }
                    ?: LoadedShader.load(context, name)?.also { loadedShaders.put(name, at.first, at.second, it) }
            }
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
    // Under a full-screen screen home isn't seen: its shader stands still there, and the screen draws its own.
    val hidden = home && ShaderPlay.covered.collectAsStateWithLifecycle().value > 0
    ActiveMark(shader.name)
    ArmWhileShown(shader.id, safety, lifecycle)
    ProveWhenShown(shader.id, safety, lifecycle)
    if (shader.meta.animated) AnimateShader(shader, clock, touch, hidden)
    val day = remember(shader) { ShaderDay() }
    if (!shader.meta.animated && shader.meta.uses(ShaderDate.NAME)) FollowDate(shader, day, hidden)
    if (shader.meta.tilt) FollowTilt(shader, touch)
    val battery = rememberShaderBattery(shader)
    val params = rememberLiveParams(shader)
    val corner = rememberWindowCorner()

    val inputs = remember(shader, grid, cols, left) {
        ShaderInputs(shader, grid, cols, left, params, shader.meta.once) { Shaders.report(context, shader.name, shader.id, it) }
    }
    if (over != null) DrawOverShader(over, shader.meta)
    return (over?.surface ?: Modifier)
        .shaderEffect(inputs, clock, touch, battery, corner, day, hidden)
        .pointerInput(shader) {
            watchTouches(
                onTouch = { touch.at(it, clock.seconds) },
                onPress = { touch.pressed = it },
                onHold = { Shaders.offByHand(context) },
            )
        }
}

/** The effect of [inputs] over what this modifier is on. [hidden]: as [ShaderInputs.effect]. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Modifier.shaderEffect(
    inputs: ShaderInputs, clock: ShaderTime, touch: ShaderTouch, battery: ShaderBattery, corner: WindowCorner,
    day: ShaderDay? = null, hidden: Boolean = false,
): Modifier =
    graphicsLayer {
        renderEffect = if (size.width > 0f && size.height > 0f) {
            inputs.effect(size.width, size.height, clock, touch, battery, corner, day, hidden)
        } else null
        clip = true
    }

/**
 * A shader's [code] over a small sample in the Shaders tab, drawn as [rememberTtyShader] draws it. Null until
 * it is compiled, and when it can't be drawn (it doesn't compile, a screen reader is on, or Android is
 * older than 13).
 *
 * It is only looked at, so it is kept out of what judges the shader on home: no mark in
 * [ShaderSafety], no line in the log and no touches. Home isn't drawn while it is.
 * It leans with the phone, as on home, until it goes or a minute has passed. With [moves] off it stands still.
 *
 * [minute]: a count that changes each minute (a card's clock tick). A still shader that declares `date` is
 * drawn again when it changes, so a scene follows the hour as on home ([FollowDate]) without a frame of its own.
 */
@Composable
internal fun rememberShaderPreview(
    name: String, code: String, grid: TtyGrid, cols: Int = 0, left: Int = 0, moves: Boolean = true, over: OverShader? = null,
    minute: Long? = null,
): Modifier? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return previewLayer(name, code, grid, cols, left, moves, over, minute)
}

/**
 * The header of shader [name] ([ShaderMeta]), read from its file. Null for none ([ShaderCatalog.OFF]),
 * until it is read, and when its file can't be read. Read again when a shader file changes.
 */
@Composable
internal fun rememberShaderMeta(name: String?): ShaderMeta? {
    val context = LocalContext.current.applicationContext
    val files by Shaders.version.collectAsStateWithLifecycle()
    val meta by produceState<ShaderMeta?>(null, name, files) {
        value = if (name == null || name == ShaderCatalog.OFF) null
        else withContext(Dispatchers.IO) { Shaders.source(context, name)?.let(ShaderMeta::parse) }
    }
    return meta
}

/**
 * Home as a preview shows it with the shader [name] over it ([rememberShaderPreview]): nothing until it is
 * read and compiled. [over]: as on home ([rememberTtyShader]).
 */
@Composable
internal fun rememberPeekShader(name: String, grid: TtyGrid, cols: Int = 0, left: Int = 0, over: OverShader? = null): Modifier {
    val context = LocalContext.current.applicationContext
    val code by produceState<String?>(null, name) { value = withContext(Dispatchers.IO) { Shaders.source(context, name) } }
    return code?.let { rememberShaderPreview(name, it, grid, cols, left, over = over) } ?: Modifier
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun previewLayer(
    name: String, code: String, grid: TtyGrid, cols: Int, left: Int, moves: Boolean, over: OverShader?, minute: Long? = null,
): Modifier? {
    if (rememberScreenReader()) return null
    val loaded by produceState<LoadedShader?>(null, name, code) {
        value = withContext(Dispatchers.IO) { LoadedShader.compile(name, code) }
    }
    val shader = loaded ?: return null
    val touch = remember(shader) { ShaderTouch() }
    // A one-off shader shows what it is at a moment in the middle, until it moves: again and again here.
    val clock = remember(shader) { ShaderTime().apply { seconds = (shader.meta.once ?: 0f) * 0.4f } }
    if (shader.meta.animated && moves) AnimateShader(shader, clock, touch)
    if (shader.meta.tilt && moves) FollowTilt(shader, touch)
    val battery = rememberShaderBattery(shader)
    val params = rememberLiveParams(shader)
    // The sample in a shader's details is smaller than the window: its corner is 0. Settings' preview of home is the whole window.
    val corner = rememberWindowCorner()
    val inputs = remember(shader, grid, cols, left) { ShaderInputs(shader, grid, cols, left, params, shader.meta.once) { } }
    // A still that follows the day reads the minute as it draws ([ShaderInputs.effect]): a new minute draws it again.
    val day = remember(shader) { ShaderDay() }
    if (minute != null) SideEffect { day.minute = minute.toInt() }
    if (over != null) DrawOverShader(over, shader.meta)
    return (over?.surface ?: Modifier).shaderEffect(inputs, clock, touch, battery, corner, day.takeIf { minute != null })
}

/** Kolahal's own shaders compiled for cards ([rememberShaderStill]), kept apart so a card never pushes home's out of [loadedShaders]. */
private val stillShaders = ShaderCache<LoadedShader>(max = 4)

/**
 * A still of Kolahal's own shader [name], as a card's ground on home's widgets pages
 * ([com.mtwabpin.kolahal.ui.widgets.StatScene]): drawn as [rememberTtyShader] draws it, with its clock at 0.
 * Null until it is compiled, and when it can't be drawn: it isn't one of Kolahal's own ([ShaderCatalog.BUNDLED]),
 * it is no scene ([WallpaperChoice.suits]), it was turned off ([ShaderSafety.isTripped]), a screen reader is
 * on, or Android is older than 13.
 *
 * Only Kolahal's own, as they come with it (a shader of the user's with the same name isn't read): those are
 * tested with each release. A card's shader is drawn beside home's, and [ShaderSafety] keeps one mark at a time,
 * for home's, so a user's own shader on a card could be neither marked nor turned off after a crash.
 * - It is compiled once for every card: home's own is used when it is the same code, else one kept for cards
 *   ([stillShaders]). Nothing is marked or logged.
 * - No touches, and no lean or clock of its own. [minute]: a count that changes each minute (the cards' clock
 *   tick); a scene that declares `date` is drawn again when it changes, so it follows the hour as on home.
 */
@Composable
internal fun rememberShaderStill(name: String, grid: TtyGrid, minute: Long): Modifier? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return stillLayer(name, grid, minute)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun stillLayer(name: String, grid: TtyGrid, minute: Long): Modifier? {
    if (rememberScreenReader()) return null
    val context = LocalContext.current.applicationContext
    val safety = remember { ShaderSafety.get(context) }
    // Read again when a file changes, or a shader is turned off or on again.
    val files by Shaders.version.collectAsStateWithLifecycle()
    val trips by safety.version.collectAsStateWithLifecycle()
    val loaded by produceState<LoadedShader?>(null, name, files, trips) {
        value = withContext(Dispatchers.IO) { LoadedShader.still(context, name, files, trips) }
    }
    val shader = loaded ?: return null
    val touch = remember(shader) { ShaderTouch() }
    val clock = remember(shader) { ShaderTime() }
    val battery = rememberShaderBattery(shader)
    val params = rememberLiveParams(shader)
    val corner = rememberWindowCorner()
    val inputs = remember(shader, grid) { ShaderInputs(shader, grid, 0, 0, params, shader.meta.once) { } }
    // A still that follows the day reads the minute as it draws ([ShaderInputs.effect]): a new minute draws it again.
    val day = remember(shader) { ShaderDay() }
    SideEffect { day.minute = minute.toInt() }
    return Modifier.shaderEffect(inputs, clock, touch, battery, corner, day)
}

/**
 * The one-off shader that plays ([ShaderPlay]), over the surface and the shader it has: put it before
 * that one. Nothing while none plays, when [on] is false, with a screen reader on, in battery saver, with
 * animations off, when the phone is hot or nearly out of battery, and on an Android older than 13.
 *
 * It is marked in [ShaderSafety] while it plays, as the shader on home is: one that was playing when Kolahal
 * crashed or hung is turned off, and doesn't play again at the next word or request until its file changes
 * or a shader is turned on ([ShaderSafety.turnedOn]).
 */
@Composable
internal fun rememberPlayedShader(cols: Int = 0, left: Int = 0, on: Boolean = true): Modifier {
    if (!on || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return Modifier
    val playing by ShaderPlay.current.collectAsStateWithLifecycle()
    return playing?.let { playedLayer(it, rememberTtyGrid(), cols, left) } ?: Modifier
}

/** [rememberPlayedShader] for home, where a full-screen screen over it plays it instead. */
@Composable
internal fun rememberPlayedOnHome(cols: Int = 0, left: Int = 0, on: Boolean = true): Modifier {
    val covered by ShaderPlay.covered.collectAsStateWithLifecycle()
    return rememberPlayedShader(cols, left, on && covered == 0)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun playedLayer(playing: ShaderPlay.Playing, grid: TtyGrid, cols: Int, left: Int): Modifier {
    if (rememberScreenReader()) {
        LaunchedEffect(playing) { ShaderPlay.done(playing) }
        return Modifier
    }
    val context = LocalContext.current.applicationContext
    val safety = remember { ShaderSafety.get(context) }
    val loaded by produceState<LoadedShader?>(null, playing) {
        var ready: LoadedShader? = null
        try {
            // It finishes even when it is done meanwhile, so its mark in the store is known ([ShaderSafety.begin]).
            withContext(NonCancellable + Dispatchers.IO) { ready = LoadedShader.play(context, playing.name) }
            if (ready == null) ShaderPlay.done(playing) else value = ready
            awaitCancellation()
        } finally {
            // Never drawn: its mark goes. While it is drawn, [ArmWhileShown] keeps it.
            ready?.let { safety.cancel(it.id) }
        }
    }
    val shader = loaded?.takeIf { it.name == playing.name } ?: return Modifier
    val seconds = playing.seconds ?: shader.meta.once ?: return Modifier
    val touch = remember(playing) { ShaderTouch() }
    val clock = remember(playing) { ShaderTime() }
    val sources = LocalShaderSources.current
    val shown = LocalTerminalShown.current
    val focused = LocalWindowInfo.current.isWindowFocused
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    ActiveMark(shader.name)
    ArmWhileShown(shader.id, safety, lifecycle)
    LaunchedEffect(playing, shader, shown, focused) {
        if (!shown || !focused) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val late = System.currentTimeMillis() - playing.at > ShaderPlay.WAIT_MS
            if (!late && ShaderClock.moves(sources.phone(context), 0L)) {
                var last = -1L
                while (clock.seconds < seconds) {
                    val now = withFrameNanos { it } / 1_000_000L
                    // A long gap (a pause) counts as one frame, so it goes on where it was.
                    if (last >= 0) clock.seconds += (now - last).coerceIn(0L, 100L) / 1000f
                    last = now
                }
            }
            ShaderPlay.done(playing)
        }
    }
    val battery = rememberShaderBattery(shader)
    val params = rememberLiveParams(shader)
    val corner = rememberWindowCorner()
    val inputs = remember(shader, grid, cols, left, seconds) { ShaderInputs(shader, grid, cols, left, params, seconds) { ShaderPlay.done(playing) } }
    return Modifier.shaderEffect(inputs, clock, touch, battery, corner)
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
            lastProven = id
        }
    }
}

/**
 * Moves the shader's [clock] while home is in front, when the phone's state allows it ([ShaderClock.moves]).
 * Not while the surface is [hidden] under a full-screen screen. As often as its header asks ([ShaderClock.frameMs]):
 * a touch on one that reads it brings the next frame at once, and one whose only clock is the touch waits for one.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AnimateShader(shader: LoadedShader, clock: ShaderTime, touch: ShaderTouch, hidden: Boolean = false) {
    val context = LocalContext.current
    val sources = LocalShaderSources.current
    val shown = LocalTerminalShown.current
    val focused = LocalWindowInfo.current.isWindowFocused
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(shader, shown, focused, hidden) {
        if (!shown || !focused || hidden) return@LaunchedEffect
        val pace = ShaderClock.Pace.of(shader.meta)
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = -1L
            var checkedAt = -1L
            var allowed = false
            try {
                while (true) {
                    val now = withFrameNanos { it } / 1_000_000L
                    if (checkedAt < 0 || now - checkedAt >= CHECK_MS) {
                        allowed = ShaderClock.moves(sources.phone(context), SystemClock.uptimeMillis() - touch.uptime)
                        checkedAt = now
                    }
                    val frameMs = if (allowed) ShaderClock.frameMs(pace, touch.sinceMs()) else null
                    clock.moving = frameMs != null
                    if (frameMs != null) {
                        // A long gap (a pause) counts as one frame, so nothing jumps.
                        val delta = if (last < 0) frameMs else ShaderClock.step(now - last, frameMs)
                        clock.seconds += delta / 1000f
                        last = now
                        val spent = (System.nanoTime() / 1_000_000L - now).coerceAtLeast(0L)
                        // Wakes just before the next frame it wants, or at a touch that wants one sooner.
                        val wait = (frameMs - spent - 4).coerceIn(0L, frameMs)
                        if (ShaderClock.touchHurries(pace, frameMs)) untilTouch(touch, wait) else delay(wait)
                    } else if (allowed) {
                        // The touch has settled, or there was none: nothing changes until the next one.
                        last = -1L
                        untilTouch(touch, null)
                        checkedAt = -1L
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

/** Waits for the next touch on the surface, or [ms] at most (null: as long as it takes). */
private suspend fun untilTouch(touch: ShaderTouch, ms: Long?) {
    val at = touch.uptime
    val next = suspend { snapshotFlow { touch.uptime }.first { it != at } }
    if (ms == null) next() else withTimeoutOrNull(ms) { next() }
}

/**
 * Draws a still shader that declares `date` again at each full minute, while home is in front: a sky
 * follows the day without a frame loop. No timer runs behind another screen or app, or while the
 * surface is [hidden] under a full-screen screen.
 */
@Composable
private fun FollowDate(shader: LoadedShader, day: ShaderDay, hidden: Boolean) {
    val shown = LocalTerminalShown.current
    val focused = LocalWindowInfo.current.isWindowFocused
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(shader, shown, focused, hidden) {
        if (!shown || !focused || hidden) return@LaunchedEffect
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
 * Home under a full-screen screen keeps its hold, and the screen's surface holds it too: one listener.
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

/** A shader's parameters as they are drawn, in its header's order. Read as it draws, so a change draws it again. */
private class LiveParams(start: List<Float>) {
    private val states = start.map { mutableFloatStateOf(it) }

    operator fun get(i: Int): Float = states[i].floatValue

    operator fun set(i: Int, value: Float) {
        states[i].floatValue = value
    }

    fun all(): List<Float> = states.map { it.floatValue }
}

/**
 * [shader]'s parameters: what people set ([ShaderParams]), at once, as a slider is dragged; and for one that
 * follows a value ([ShaderValues]), that value, to which it glides in [GLIDE_MS] when it changes. One of
 * several words changes at once.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun rememberLiveParams(shader: LoadedShader): LiveParams {
    val context = LocalContext.current.applicationContext
    val params = shader.meta.params
    val status = remember(context) { DeviceStatus.get(context).status }
    val live = remember(shader) {
        val phone = valuesPhone(status.value)
        LiveParams(params.map { ShaderParams.now(shader.name, it, phone) })
    }
    if (params.isEmpty()) return live
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(shader, live) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val aims = live.all().toFloatArray()
            val glides = arrayOfNulls<Job>(params.size)
            // The time of day moves each minute.
            val minutes = flow {
                while (true) {
                    emit(Unit)
                    delay(ShaderDate.untilNextMinute(System.currentTimeMillis()))
                }
            }
            val settings = combine(ShaderParams.values, ShaderParams.follows, ShaderParams.formulas) { a, b, c -> Triple(a, b, c) }
            combine(settings, ShaderValues.given, status, minutes) { _, _, now, _ -> valuesPhone(now) }
                .collect { phone ->
                    params.forEachIndexed { i, param ->
                        val followed = ShaderParams.followed(shader.name, param, phone)
                        val aim = followed ?: ShaderParams.of(shader.name, param)
                        if (aim == aims[i]) return@forEachIndexed
                        aims[i] = aim
                        glides[i]?.cancel()
                        // A step too small to see, such as a minute of the day, isn't worth frames.
                        val far = abs(aim - live[i]) > (param.max - param.min) * 0.02f
                        if (followed == null || param.choices.isNotEmpty() || !far) live[i] = aim
                        else glides[i] = launch { animate(live[i], aim, animationSpec = tween(GLIDE_MS)) { x, _ -> live[i] = x } }
                    }
                }
        }
    }
    return live
}

/** How long a parameter takes to the value it follows. */
private const val GLIDE_MS = 2_000

/** The phone for [ShaderValues], at [status] and now. */
private fun valuesPhone(status: Status): ShaderValues.Phone =
    ShaderValues.Phone((status.batteryPct ?: 100) / 100f, status.charging, LocalTime.now())

/** The window's corner and camera cutout for a shader on this surface ([WindowCorner]). */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun rememberWindowCorner(): WindowCorner {
    val view = LocalView.current
    return remember(view) { WindowCorner(view) }
}

/**
 * The radius of the window's rounded corners, in px, for the `corner` uniform: the largest of the four that
 * Android reports for the window ([android.view.WindowInsets.getRoundedCorner], Android 12 and newer, so always
 * here), when the surface is as large as the window. A smaller surface, as the sample in a shader's details, gets 0.
 * Read once the window has its insets: the radius doesn't change while a window lives.
 *
 * It also has the camera's cutout at the top of the window, for the `cutout` uniform ([cutout]).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class WindowCorner(private val view: View) {
    private var radius: Float? = null

    /** The top cutout as last read, and the window's size it was read at: a rotation moves the camera to a side. */
    private var top: Rect? = null
    private var topW = -1
    private var topH = -1

    /** The radius for a surface [w] × [h] px. Not a state: reading it draws nothing again. */
    fun of(w: Float, h: Float): Float {
        val root = view.rootView
        if (root.width <= 0 || w < root.width - 1f || h < root.height - 1f) return 0f
        radius?.let { return it }
        val insets = view.rootWindowInsets ?: return 0f
        return CORNERS.maxOf { insets.getRoundedCorner(it)?.radius ?: 0 }.toFloat().also { radius = it }
    }

    /**
     * The camera's cutout at the top of the window for a surface [w] × [h] px, into [out]: left, top, right,
     * bottom, in px ([android.view.DisplayCutout.getBoundingRectTop]). All 0 when there is none, as in landscape
     * where the camera is at a side, and when the surface is smaller than the window. Not a state either.
     */
    fun cutout(w: Float, h: Float, out: FloatArray) {
        out.fill(0f)
        val root = view.rootView
        if (root.width <= 0 || w < root.width - 1f || h < root.height - 1f) return
        if (top == null || topW != root.width || topH != root.height) {
            val insets = view.rootWindowInsets ?: return
            top = insets.displayCutout?.boundingRectTop ?: Rect()
            topW = root.width
            topH = root.height
        }
        val r = top ?: return
        if (r.isEmpty) return
        out[0] = r.left.toFloat()
        out[1] = r.top.toFloat()
        out[2] = r.right.toFloat()
        out[3] = r.bottom.toFloat()
    }

    private companion object {
        val CORNERS = intArrayOf(
            RoundedCorner.POSITION_TOP_LEFT, RoundedCorner.POSITION_TOP_RIGHT,
            RoundedCorner.POSITION_BOTTOM_RIGHT, RoundedCorner.POSITION_BOTTOM_LEFT,
        )
    }
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

    /** How long since the last touch, in ms; null before the first. Turning the shader on isn't one. */
    fun sinceMs(): Long? = if (atSeconds < 0f) null else SystemClock.uptimeMillis() - uptime

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

        /**
         * The one-off shader [name] to play ([ShaderPlay]), marked as [load] marks one, and nothing logged. Null
         * when it isn't one, can't be drawn, or was turned off ([ShaderSafety.isTripped]). Not on the main thread.
         */
        fun play(context: Context, name: String): LoadedShader? {
            Shaders.recover(context)
            val source = Shaders.source(context, name) ?: return null
            val id = ShaderSafety.id(name, source)
            val safety = ShaderSafety.get(context)
            if (safety.isTripped(id) || ShaderMeta.problems(source).isNotEmpty()) return null
            val meta = ShaderMeta.parse(source)
            if (meta.once == null) return null
            // Marked before it compiles: a crash in the compiler turns it off at the next start, too.
            safety.begin(id)
            val runtime = try {
                RuntimeShader(source)
            } catch (_: RuntimeException) {
                safety.cancel(id)
                return null
            }
            return LoadedShader(id, name, meta, runtime)
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

        /**
         * Kolahal's own shader [name] for a card ([rememberShaderStill]), as it comes with Kolahal, at the
         * versions [files] and [trips]: home's when it is loaded with the same code, else one compiled once and
         * kept ([stillShaders]). Nothing is marked or logged. Null when it isn't one of Kolahal's own, is no
         * scene, can't be drawn or was turned off. Not on the main thread.
         */
        fun still(context: Context, name: String, files: Int, trips: Int): LoadedShader? {
            if (!StatScenes.drawn(name)) return null
            val code = runCatching { Shaders.bundled(context, name) }.getOrNull() ?: return null
            val id = ShaderSafety.id(name, code)
            if (ShaderSafety.get(context).isTripped(id)) return null
            val kept = loadedShaders.get(name, files, trips)?.takeIf { it.id == id }
                ?: stillShaders.get(name, files, trips)
            if (kept != null) return kept.takeIf { WallpaperChoice.suits(it.meta) }
            if (!WallpaperChoice.suits(ShaderMeta.parse(code))) return null
            return compile(name, code)?.also { stillShaders.put(name, files, trips, it) }
        }
    }
}

/**
 * Sets the uniforms of a [LoadedShader] ([ShaderUniforms]) and makes its effect. One that Android refuses in
 * spite of [ShaderMeta.problems] goes to [onError] (a line in the log), and no effect is drawn: home never
 * stops for a shader.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ShaderInputs(
    shader: LoadedShader,
    grid: TtyGrid,
    cols: Int,
    left: Int,
    private val params: LiveParams,
    /** How long a one-off shader runs, in seconds. Where it isn't played ([playedLayer]) it starts again after a second. */
    once: Float?,
    private val onError: (String) -> Unit,
) {
    private val runtime = shader.runtime
    private val animated = shader.meta.animated
    private val uniforms = ShaderUniforms(
        shader.meta, runtime, grid.metrics.cellW.toFloat(), grid.metrics.cellH.toFloat(),
        colors = COLORS.associate { (name, ink) -> name to grid.palette.color(ink) },
        dark = TtyPalette.luminance(grid.palette.color(Ink.BG)) < TtyPalette.luminance(grid.palette.color(Ink.FG)),
        once = once,
    )
    private val frame = ShaderFrame().also {
        it.cols = cols
        it.left = left
    }
    private var failed = false

    /**
     * The effect for a surface [w] × [h] px. Null when a uniform can't be set. Only a shader that declares the
     * clock or the touch reads them, so a frame or a touch that can't change a still shader doesn't draw it again.
     * A [hidden] surface (home under a full-screen screen) takes the lean as it is, and isn't drawn again when it changes.
     */
    fun effect(
        w: Float, h: Float, clock: ShaderTime, touch: ShaderTouch, battery: ShaderBattery, corner: WindowCorner,
        day: ShaderDay? = null, hidden: Boolean = false,
    ): androidx.compose.ui.graphics.RenderEffect? {
        if (failed) return null
        val seconds = if (uniforms.usesClock) clock.seconds else 0f
        val touched = uniforms.usesTouch && touch.atSeconds >= 0f
        return try {
            frame.width = w
            frame.height = h
            frame.seconds = seconds
            frame.touchX = if (touched) touch.x else -1f
            frame.touchY = if (touched) touch.y else -1f
            // A still shader is drawn at the touch, and then not again: its age stays 0.
            frame.touchAge = if (!touched) ShaderFrame.NEVER else if (animated) (seconds - touch.atSeconds).coerceAtLeast(0f) else 0f
            if (uniforms.usesPressed) frame.pressed = touch.pressed
            if (uniforms.usesBattery) frame.battery = battery.level
            if (uniforms.usesCharging) frame.charging = battery.charging
            if (uniforms.usesCorner) frame.corner = corner.of(w, h)
            if (uniforms.usesCutout) corner.cutout(w, h, frame.cutout)
            if (uniforms.usesTilt) {
                // A shader that moves is drawn anyway, and takes the lean as it is. One that stands still
                // is drawn again when the lean changes.
                if (!hidden && (!uniforms.usesClock || !clock.moving)) LiveTilt.follow()
                frame.tiltX = LiveTilt.x
                frame.tiltY = LiveTilt.y
            }
            // Read, so the minute's change draws a still shader again.
            if (uniforms.usesDate) day?.minute
            uniforms.set(frame) { params[it] }
            RenderEffect.createRuntimeShaderEffect(runtime, ShaderMeta.INPUT).asComposeRenderEffect()
        } catch (e: RuntimeException) {
            failed = true
            onError(e.message ?: "it can't be drawn")
            null
        }
    }

    private companion object {
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
