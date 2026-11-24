/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.mtwabpin.kolahal.data.HomeStyles
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.terminal.ScreenSaver
import com.mtwabpin.kolahal.terminal.ScriptScreenRequests
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.terminal.tty.screens.LogDecodeScreen
import com.mtwabpin.kolahal.terminal.tty.screens.TtyScreens
import com.mtwabpin.kolahal.ui.tty.TtyScreenHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long the screensaver's minute is, and how it reads the phone's state ([ScreenSaver.Phone]).
 * A device test makes the minute short, and the phone always willing: tests run with animations off.
 */
internal class ScreenSaverTiming(
    val minuteMs: Long = 60_000L,
    val phone: (Context) -> ScreenSaver.Phone = ::phoneState,
)

internal val LocalScreenSaverTiming = staticCompositionLocalOf { ScreenSaverTiming() }

/** The phone's state for the screensaver: the screen on, battery saver, "Remove animations". */
internal fun phoneState(context: Context): ScreenSaver.Phone {
    val power = context.getSystemService(PowerManager::class.java)
    return ScreenSaver.Phone(
        screenOn = power?.isInteractive != false,
        batterySaver = power?.isPowerSaveMode == true,
        animationsOff = !ValueAnimator.areAnimatorsEnabled(),
    )
}

/**
 * Opens the idle screensaver (`screen.saver`) once home has been left alone for
 * `screen.saver-after` minutes ([ScreenSaver.next]). It is part of home, so it only waits while home
 * is shown; each change to home ([sheetOpen], [typing], the page, the focus) starts the wait again.
 * Touches and keys reach it through [TerminalViewModel.lastInputAt].
 */
@Composable
internal fun ScreenSaverWatch(vm: TerminalViewModel, sheetOpen: Boolean, typing: Boolean) {
    val configVersion by vm.config.version.collectAsStateWithLifecycle()
    // In the gui style a screen that draws the character grid is off: the stored value waits for the tty style.
    val saver = remember(configVersion) { ScreenSaver.inEffect(vm.config[Keys.SCREEN_SAVER], HomeStyles.ttyNow(vm.config)) }
    if (saver !in ScreenSaver.SCREENS) return
    val after = remember(configVersion) { vm.config[Keys.SCREEN_SAVER_AFTER] }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val focused = LocalWindowInfo.current.isWindowFocused
    val screen by vm.ttyScreen.collectAsStateWithLifecycle()
    val home = ScreenSaver.Home(
        inFront = lifecycle.isAtLeast(Lifecycle.State.RESUMED) && focused,
        logPage = LocalTerminalShown.current,
        sheetOpen = sheetOpen,
        typing = typing,
        screenOpen = screen != null,
    )
    val context = LocalContext.current
    val timing = LocalScreenSaverTiming.current
    LaunchedEffect(saver, after, home, timing) {
        // Home is as it is from now: the quiet starts here at the earliest.
        val readySince = SystemClock.uptimeMillis()
        while (true) {
            val now = SystemClock.uptimeMillis()
            val quietSince = maxOf(readySince, vm.lastInputAt)
            when (val next = ScreenSaver.next(saver, after, quietSince, now, home, timing.phone(context), timing.minuteMs)) {
                ScreenSaver.Next.Never -> break
                is ScreenSaver.Next.Wait -> delay(next.ms)
                is ScreenSaver.Next.Open -> {
                    vm.showScreenSaver(next.screen)
                    break
                }
            }
        }
    }
}

/**
 * The full-screen screen over home's log page, in both home styles: one you opened (`top`,
 * `screen matrix`…) or the idle screensaver. MainActivity puts it over the terminal page. The screensaver
 * closes if battery saver or "Remove animations" comes on while it runs.
 */
@Composable
fun HomeScreenOverlay(vm: TerminalViewModel) {
    val name by vm.ttyScreen.collectAsStateWithLifecycle()
    val saver by vm.screenSaverOn.collectAsStateWithLifecycle()
    val configVersion by vm.config.version.collectAsStateWithLifecycle()
    val shown = name ?: return
    // In the gui style decode is home's own log, which scrambles where it is, with the shader home has over it.
    val inPlace = shown == "decode" && remember(configVersion) { HomeStyle.of(vm.config[Keys.HOME_STYLE]) } == HomeStyle.GUI
    // decode takes over home's lines where they are, so it waits a moment for the keyboard to be down.
    if (shown == "decode" && !keyboardDown()) return
    if (saver) {
        val context = LocalContext.current
        val timing = LocalScreenSaverTiming.current
        LaunchedEffect(shown, timing) {
            while (true) {
                delay(SAVER_RECHECK_MS)
                if (!timing.phone(context).allows) {
                    vm.closeScreenSaver()
                    break
                }
            }
        }
    }
    // A touch or a key only closes the screensaver; from a screen you opened, a typed key goes on into the prompt.
    val close: (String?) -> Unit = if (saver) { _ -> vm.closeScreenSaver() } else vm::closeTtyScreen
    val app = LocalContext.current.applicationContext
    TtyScreenHost(
        name = shown,
        data = remember(shown) { vm.ttyScreenData() },
        clock24 = remember(shown) { vm.config[Keys.CLOCK_24H] },
        onClose = close,
        saver = saver,
        shader = if (inPlace) ShaderCatalog.OFF else remember(configVersion) { vm.config[Keys.SCREEN_SHADER] },
        seeThrough = inPlace,
        // A script's own screen (tty.screen), or one of Kolahal's.
        make = { if (inPlace) LogDecodeScreen(vm.logDecode) else ScriptScreenRequests.screen(app, it) ?: TtyScreens.named(it) },
    )
}

/** True once the keyboard is down, or after [KEYBOARD_WAIT_MS] at most. */
@Composable
private fun keyboardDown(): Boolean {
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    var down by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withTimeoutOrNull(KEYBOARD_WAIT_MS) { snapshotFlow { ime.getBottom(density) }.first { it == 0 } }
        down = true
    }
    return down || ime.getBottom(density) == 0
}

private const val KEYBOARD_WAIT_MS = 600L

/** While the screensaver runs, how often it checks that battery saver and animations still allow it. */
private const val SAVER_RECHECK_MS = 10_000L
