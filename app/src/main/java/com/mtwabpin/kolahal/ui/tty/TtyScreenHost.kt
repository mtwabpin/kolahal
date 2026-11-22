/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import android.animation.ValueAnimator
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import com.mtwabpin.kolahal.terminal.tty.CellStyle
import com.mtwabpin.kolahal.terminal.tty.CellWidth
import com.mtwabpin.kolahal.terminal.tty.screens.ScreenData
import com.mtwabpin.kolahal.terminal.tty.screens.ScreenEnv
import com.mtwabpin.kolahal.terminal.tty.screens.TtyKey
import com.mtwabpin.kolahal.terminal.tty.screens.TtyScreen
import com.mtwabpin.kolahal.terminal.tty.screens.TtyScreens
import com.mtwabpin.kolahal.ui.LocalTerminalShown
import com.mtwabpin.kolahal.ui.Term
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Back after this long away from home, a screen closes instead of going on. */
private const val RESUME_WITHIN_MS = 30_000L

/** The most frames a second a screen gets in battery saver. */
private const val SAVER_FPS = 15

/**
 * Runs the screen [name] (`matrix`, `clock`…) over home, on one grid that fills the page, in the
 * terminal's font, size and theme.
 * - The frame loop runs only while home is resumed, its window has focus and the log page is
 *   shown: the screen off, another app or the widgets page pause it. Back within 30 s, the same
 *   screen goes on; later, it closes.
 * - It closes on a tap, Back, or any key the screen doesn't use. [onClose] gets null for a tap,
 *   Back or the program's end; for a key, the character it typed ("" for none, and for q), which
 *   goes on into the prompt, so typing is never lost.
 * - In battery saver a screen gets at most 15 frames a second. With Android's "Remove animations"
 *   on, or when the phone is hot, a moving screen shows one still frame and a hint instead.
 * - It never keeps the screen on.
 *
 * As the idle screensaver ([saver]), any touch or key only closes it: a key is not typed into the
 * prompt ([onClose] gets null), and leaving home, turning the page or the screen going off closes
 * it at once instead of pausing it.
 *
 * [data] is what `boot` and `top` show; [clock24] is `clock.24h`; [crt] is `tty.crt` ([rememberCrt]).
 */
@Composable
fun TtyScreenHost(
    name: String,
    data: ScreenData,
    clock24: Boolean,
    onClose: (typed: String?) -> Unit,
    modifier: Modifier = Modifier,
    saver: Boolean = false,
    crt: Boolean = false,
) {
    val close by rememberUpdatedState(onClose)
    val screen = remember(name) { TtyScreens.named(name) }
    if (screen == null) {
        LaunchedEffect(name) { close(null) }
        return
    }
    val state = remember(screen) { HostState() }
    val context = LocalContext.current
    val grid = rememberTtyGrid()
    val metrics = grid.metrics
    val painter = grid.painter
    // The grid's area: the page less the system bars and the camera cutout.
    var area by remember { mutableStateOf(IntSize.Zero) }
    var areaX by remember { mutableIntStateOf(0) }
    var areaY by remember { mutableIntStateOf(0) }
    val cols = if (area.width > 0) metrics.cols(area.width) else 0
    val rows = if (area.height > 0) metrics.rows(area.height) else 0
    // Each frame that changed a row bumps this, and the draw below reads it, so only the drawing runs again.
    var frames by remember { mutableIntStateOf(0) }

    val shown = LocalTerminalShown.current
    val focused = LocalWindowInfo.current.isWindowFocused
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val still = remember(screen) {
        val power = context.getSystemService(PowerManager::class.java)
        val hot = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                (power?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_MODERATE
        screen.animated && (!ValueAnimator.areAnimatorsEnabled() || hot)
    }
    val fps = remember(screen) {
        val saver = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        (if (saver) minOf(screen.fps, SAVER_FPS) else screen.fps).coerceIn(1, 60)
    }

    LaunchedEffect(state, cols, rows, shown, focused) {
        // The screensaver doesn't wait for home to come back: it is over once home is left.
        if (saver && state.started && (!shown || !focused)) {
            close(null)
            return@LaunchedEffect
        }
        if (cols == 0 || rows == 0 || !shown || !focused) return@LaunchedEffect
        val buffer = state.buffer
        if (!state.started) {
            buffer.resize(cols, rows)
            screen.start(
                ScreenEnv(cols, rows, Random(SystemClock.uptimeMillis()), System::currentTimeMillis, clock24, data)
            )
            state.started = true
        } else if (buffer.cols != cols || buffer.rows != rows) {
            buffer.resize(cols, rows)
            screen.resize(cols, rows)
            state.stillDrawn = false
        }
        if (still) {
            if (!state.stillDrawn) {
                screen.still(buffer)
                hint(buffer, "Animations are off · tap to close")
                state.stillDrawn = true
                frames++
            }
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (state.pausedAt != 0L && SystemClock.uptimeMillis() - state.pausedAt > RESUME_WITHIN_MS) {
                close(null)
                return@repeatOnLifecycle
            }
            state.pausedAt = 0L
            val frameMs = 1_000L / fps
            var last = -1L
            try {
                while (true) {
                    val now = withFrameNanos { it } / 1_000_000L
                    // A long gap (a pause) counts as one frame, so nothing jumps.
                    val delta = if (last < 0) frameMs else (now - last).coerceIn(0L, 250L)
                    last = now
                    state.time += delta
                    val going = screen.frame(buffer, state.time, delta)
                    if (!buffer.dirty.isEmpty) frames++
                    if (!going) {
                        close(null)
                        break
                    }
                    // Sleep until about the next frame this screen wants, rather than wake at every vsync.
                    // Frame times are in System.nanoTime's time base.
                    val spent = (System.nanoTime() / 1_000_000L - now).coerceAtLeast(0L)
                    delay((frameMs - spent - 4).coerceAtLeast(0L))
                }
            } finally {
                state.pausedAt = SystemClock.uptimeMillis()
            }
        }
    }
    // The screen takes the focus from the prompt, so a hardware keyboard's keys come here (and the
    // on-screen keyboard goes down). Its focus target is attached after the first frame.
    LaunchedEffect(screen) {
        withFrameNanos { }
        runCatching { state.focus.requestFocus() }
    }
    androidx.compose.runtime.DisposableEffect(screen) {
        onDispose { screen.stop() }
    }
    // The screensaver closes as home goes: another app, the screen off, the recent apps.
    if (saver) androidx.compose.runtime.DisposableEffect(lifecycle) {
        val watch = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) close(null) }
        lifecycle.addObserver(watch)
        onDispose { lifecycle.removeObserver(watch) }
    }
    BackHandler { close(null) }
    val crtLook = rememberCrt(crt, grid.palette, metrics.cellH)

    Box(
        modifier
            .fillMaxSize()
            .background(Term.bg)
            .then(crtLook)
            .semantics {
                contentDescription = if (saver) "Screensaver: ${screen.title}" else screen.title
                onClick(label = "close") { close(null); true }
            }
            .pointerInput(screen, metrics) {
                // Any touch is the screen's: a tap that it doesn't use closes it. Nothing reaches home below.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                    val col = ((down.position.x - areaX - state.originX) / metrics.cellW).toInt()
                    val row = ((down.position.y - areaY - state.originY) / metrics.cellH).toInt()
                    if (!screen.tap(col, row)) close(null)
                }
            }
            .onKeyEvent { e -> onKey(e, screen, saver, close) }
            .focusRequester(state.focus)
            .focusable(),
    ) {
        Spacer(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                .onPlaced {
                    val at = it.positionInParent()
                    areaX = at.x.toInt()
                    areaY = at.y.toInt()
                    area = it.size
                }
                .drawBehind {
                    if (frames < 0 || !state.started) return@drawBehind
                    val buffer = state.buffer
                    // The leftover pixels go evenly to both sides, so the grid sits in the middle.
                    state.originX = ((size.width - buffer.cols * metrics.cellW) / 2f).toInt().coerceAtLeast(0)
                    state.originY = ((size.height - buffer.rows * metrics.cellH) / 2f).toInt().coerceAtLeast(0)
                    drawIntoCanvas {
                        painter.draw(it.nativeCanvas, buffer, state.originX.toFloat(), state.originY.toFloat())
                    }
                },
        )
    }
}

/** What the host keeps for one screen across recompositions and pauses. */
private class HostState {
    val buffer = CellBuffer(1, 1)
    val focus = FocusRequester()
    var started = false
    var stillDrawn = false

    /** The screen's own time, in ms: it stands still while the screen is paused. */
    var time = 0L

    /** When the loop last stopped (uptime ms), or 0 while it runs. */
    var pausedAt = 0L

    /** Where the grid's first cell is in its area, in px. */
    var originX = 0
    var originY = 0
}

/** The bottom row says why nothing moves. */
private fun hint(buffer: CellBuffer, text: String) {
    val col = ((buffer.cols - CellWidth.total(text)) / 2).coerceAtLeast(0)
    buffer.fill(0, buffer.rows - 1, buffer.cols, 1, CellBuffer.EMPTY, CellStyle.PLAIN)
    buffer.put(col, buffer.rows - 1, text, CellStyle.of(Ink.DIM))
}

/**
 * A hardware key: the screen's if it uses it; else it closes the screen. A key that types a
 * character goes on into the prompt, except q (which quits, as in less and top) and a space.
 * Back and the phone's own keys (volume, media) are left to Android.
 *
 * The screensaver ([saver]) closes on any key and types nothing. The phone's own keys close it
 * too, and still do their job (the volume changes).
 */
private fun onKey(e: KeyEvent, screen: TtyScreen, saver: Boolean, close: (String?) -> Unit): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    if (e.key == Key.Back) return false
    if (saver) {
        close(null)
        return !e.nativeKeyEvent.isSystem
    }
    if (e.nativeKeyEvent.isSystem) return false
    val code = e.utf16CodePoint
    val typed = code > 0x20 && !Character.isISOControl(code) && !e.isCtrlPressed && !e.isAltPressed && !e.isMetaPressed
    val key = when {
        typed -> TtyKey.Typed(code.toChar())
        e.key == Key.Enter || e.key == Key.NumPadEnter -> TtyKey.Named.ENTER
        e.key == Key.Escape -> TtyKey.Named.ESCAPE
        e.key == Key.DirectionUp -> TtyKey.Named.UP
        e.key == Key.DirectionDown -> TtyKey.Named.DOWN
        e.key == Key.DirectionLeft -> TtyKey.Named.LEFT
        e.key == Key.DirectionRight -> TtyKey.Named.RIGHT
        e.key == Key.Tab -> TtyKey.Named.TAB
        e.key == Key.Delete || e.key == Key.Backspace -> TtyKey.Named.DELETE
        else -> TtyKey.Named.OTHER
    }
    if (screen.key(key)) return true
    // "" says a key closed it, so the prompt takes the focus back for the keyboard.
    close(if (typed && code != 'q'.code) String(Character.toChars(code)) else "")
    return true
}
