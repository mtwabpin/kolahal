/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.theme

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import android.view.WindowInsets
import androidx.annotation.RequiresApi
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInWindow

/**
 * Corners to use instead of the window's own ([windowTopCorners]). A device test supplies them on
 * an emulator whose screen has square corners. Null, the default, means the window's.
 */
val LocalTopCorners = staticCompositionLocalOf<TopCorners?> { null }

/**
 * Corners to use instead of the window's own ([windowBottomCorners]), as [LocalTopCorners] for
 * the top. Null, the default, means the window's.
 */
val LocalBottomCorners = staticCompositionLocalOf<BottomCorners?> { null }

/**
 * Home's log page in the pager beside the widgets page ([HomePage]), or null where there is none: home
 * without a widgets page, Settings' preview, a test.
 */
val LocalHomePage = staticCompositionLocalOf<HomePage?> { null }

/**
 * Where home's log page is. The pager slides it sideways while home turns to or from the widgets
 * page, and it rests at the window's left side. [coordinates] are the page's, set when it is placed.
 */
class HomePage {
    var coordinates: LayoutCoordinates? = null
}

/**
 * The x of [line] to measure the screen's corners from: its x on home's [page], which stays the same
 * while the page slides, as the corners belong to the screen and not to the page. At rest that is its
 * x in the window. Without a page, or for a line outside it, its x in the window.
 */
fun cornerX(line: LayoutCoordinates, page: HomePage?): Float {
    val on = page?.coordinates?.takeIf { it.isAttached && it.findRootCoordinates() === line.findRootCoordinates() }
        ?: return line.positionInWindow().x
    return on.localPositionOf(line, Offset.Zero).x
}

/**
 * The window's rounded top corners as Android reports them, in the window's pixels. Android 12 and
 * newer only: older Android doesn't say, and then there are none. None either while [view] is not
 * attached to a window.
 */
fun windowTopCorners(view: View): TopCorners {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return TopCorners.NONE
    val insets = view.rootWindowInsets ?: return TopCorners.NONE
    return TopCorners(
        corner(insets, RoundedCorner.POSITION_TOP_LEFT),
        corner(insets, RoundedCorner.POSITION_TOP_RIGHT),
    )
}

/**
 * The window's rounded bottom corners as Android reports them, in the window's pixels, as
 * [windowTopCorners] reads the top ones: Android 12 and newer, and only while [view] is attached.
 */
fun windowBottomCorners(view: View): BottomCorners {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return BottomCorners.NONE
    val insets = view.rootWindowInsets ?: return BottomCorners.NONE
    return BottomCorners(
        corner(insets, RoundedCorner.POSITION_BOTTOM_LEFT),
        corner(insets, RoundedCorner.POSITION_BOTTOM_RIGHT),
    )
}

/**
 * The corner at [position], or null when it is square or not reported. A window that doesn't reach
 * the corner (split screen, a window of its own) gets it with its center at 0, 0: none either.
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun corner(insets: WindowInsets, position: Int): ScreenCorner? {
    val corner = insets.getRoundedCorner(position) ?: return null
    if (corner.radius <= 0 || corner.center.x <= 0 || corner.center.y <= 0) return null
    return ScreenCorner(corner.radius, corner.center.x, corner.center.y)
}
