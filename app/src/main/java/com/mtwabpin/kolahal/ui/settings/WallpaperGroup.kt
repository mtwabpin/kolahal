/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.settings

import android.app.WallpaperManager
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.control.WallpaperMatch
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.shader.WallpaperChoice
import com.mtwabpin.kolahal.terminal.commands.WallpaperStatus
import com.mtwabpin.kolahal.ui.Action
import com.mtwabpin.kolahal.ui.ActionGroup
import com.mtwabpin.kolahal.ui.ConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Wallpaper group on Look & feel: what the phone's wallpaper can become, as `wallpaper` lists it, one
 * row each ([WallpaperStatus.rows]). The one in use says so.
 * - Theme's color: asks first, as `wallpaper theme` does, because the old wallpaper can't be brought
 *   back; the same refusals, in the same words.
 * - Shader: opens the Shaders screen ([onShaders]), where a shader's details have Set as wallpaper.
 * - Android's live wallpapers: Android's own list, as `wallpaper live` opens it.
 *
 * [theme] is the theme in use, named where it takes its colors from the wallpaper. [say] shows a line in
 * the page's snackbar.
 */
@Composable
internal fun WallpaperGroup(theme: String, say: (String) -> Unit, onShaders: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    // Counts what may have changed the wallpaper: a change made here, and each return to this page
    // (from Android's preview or its list). Each one asks Android again what the wallpaper is.
    var looks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        looks++
        onPauseOrDispose {}
    }
    val home by produceState(WallpaperStatus.Kind.YOURS, looks) {
        value = withContext(Dispatchers.IO) {
            runCatching { WallpaperStatus.home(WallpaperChoice.shown(context).home, WallpaperMatch.isMatched(context)) }
                .getOrDefault(WallpaperStatus.Kind.YOURS)
        }
    }
    val chosen by WallpaperChoice.name.collectAsStateWithLifecycle()
    // The shader the live wallpaper draws: the chosen one, or the first it falls back to.
    val shader = chosen?.takeIf(Shaders::exists) ?: WallpaperChoice.DEFAULT
    val rows = WallpaperStatus.rows(home, shader)
    ActionGroup(
        rows.map { row ->
            when (row.word) {
                WallpaperStatus.THEME -> Action(R.drawable.ic_palette, row.title, row.note) {
                    when {
                        WallpaperMatch.themeFromWallpaper -> say(WallpaperStatus.said(WallpaperMatch.Result.FROM_WALLPAPER, theme))
                        home == WallpaperStatus.Kind.THEME -> say(WallpaperStatus.said(WallpaperMatch.Result.ALREADY, theme))
                        else -> asking = true
                    }
                }

                WallpaperStatus.SHADER -> Action(R.drawable.ic_shader, row.title, row.note, chevron = true, onClick = onShaders)

                else -> Action(R.drawable.ic_wallpaper, row.title, row.note) {
                    val list = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (runCatching { context.startActivity(list) }.isFailure) say(WallpaperStatus.NO_LIST)
                }
            }
        }
    )
    if (asking) ConfirmDialog(
        title = WallpaperStatus.QUESTION,
        text = WallpaperStatus.REPLACES_SHORT,
        confirm = "Replace",
        icon = R.drawable.ic_wallpaper,
        onDismiss = { asking = false },
    ) {
        asking = false
        scope.launch {
            val result = withContext(Dispatchers.IO) { WallpaperMatch.useTheme(context) }
            looks++
            say(WallpaperStatus.said(result, theme))
        }
    }
}
