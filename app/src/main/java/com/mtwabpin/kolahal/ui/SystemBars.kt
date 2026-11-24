/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Which of Android's bars a window hides, as `screen.hide-bars` says. */
enum class HiddenBars(val status: Boolean, val navigation: Boolean) {
    NONE(false, false),
    STATUS(true, false),
    BOTH(true, true);

    companion object {
        /** The setting's value: none, status or both. Any other value hides none. */
        fun of(choice: String): HiddenBars = when (choice) {
            "status" -> STATUS
            "both" -> BOTH
            else -> NONE
        }
    }
}

/**
 * Hides the bars that [bars] hides on this window, and shows the others. A swipe from an edge shows a
 * hidden bar for a moment, then it hides again.
 */
fun Window.hideBars(bars: HiddenBars) {
    val controller = WindowCompat.getInsetsController(this, decorView)
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    val status = WindowInsetsCompat.Type.statusBars()
    val nav = WindowInsetsCompat.Type.navigationBars()
    if (bars.status) controller.hide(status) else controller.show(status)
    if (bars.navigation) controller.hide(nav) else controller.show(nav)
}

/** An activity whose sheets and dialogs hide the bars it hides ([DialogBars]). */
interface HidesBars {
    /** What the activity's window hides now. */
    val hiddenBars: HiddenBars
}

/**
 * What [DialogBars] hides, in place of the activity's [HidesBars]. Null (the default) asks the
 * activity. Tests provide it.
 */
val LocalHiddenBars = staticCompositionLocalOf<(() -> HiddenBars)?> { null }

/**
 * Call in the content of a sheet or a dialog: its window hides the bars that its activity hides
 * ([HidesBars], `screen.hide-bars`).
 * - A sheet or a dialog is a window of its own, and Android shows the bars that the window in front
 *   asks for. Without this, the status bar shows while a sheet is open over home.
 * - With no bar hidden, or in an activity that hides none (the text editor), it leaves the window as
 *   Material made it.
 * - Outside a dialog's window it does nothing.
 */
@Composable
fun DialogBars() {
    val provided = LocalHiddenBars.current
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val bars = provided?.invoke() ?: (activity as? HidesBars)?.hiddenBars ?: HiddenBars.NONE
        if (window != null && bars != HiddenBars.NONE) window.hideBars(bars)
        onDispose { }
    }
}
