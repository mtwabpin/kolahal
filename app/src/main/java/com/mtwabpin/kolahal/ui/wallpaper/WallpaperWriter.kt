/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.wallpaper

import android.content.Context
import android.util.TypedValue
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.Quietly
import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.ShaderParams
import com.mtwabpin.kolahal.shader.ShaderValues
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.shader.WallpaperChoice
import com.mtwabpin.kolahal.ui.theme.Palette
import com.mtwabpin.kolahal.ui.theme.Palettes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.toArgb

/** Writes [WallpaperState] while a shader is the live wallpaper's, whenever something it has changes. Main process. */
object WallpaperWriter {
    /** Changes that come together, as a slider's do, are written once. */
    private const val TOGETHER_MS = 120L

    private var last: WallpaperState? = null

    @OptIn(FlowPreview::class)
    suspend fun run(context: Context) {
        val config = Config.get(context)
        combine(
            WallpaperChoice.name,
            merge(ShaderParams.values, ShaderParams.follows, ShaderParams.formulas, ShaderValues.given),
            Shaders.version, config.version,
        ) { name, _, _, _ -> name }
            .debounce(TOGETHER_MS)
            .collect { name ->
                withContext(Dispatchers.IO) { Quietly.run(context, "Writing the live wallpaper") { write(context, config, name) } }
            }
    }

    /** Also when none was chosen: the wallpaper can be picked in Android's own list. */
    private fun write(context: Context, config: Config, chosen: String?) {
        val name = chosen?.takeIf(Shaders::exists) ?: WallpaperChoice.DEFAULT
        val code = Shaders.source(context, name) ?: return
        val theme = config[Keys.THEME]
        val follows = ShaderParams.follows.value[name].orEmpty()
        val metrics = context.resources.displayMetrics
        // Both are sizes of text, as home's grid takes them.
        fun px(size: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size.toFloat(), metrics)
        val (cellW, cellH) = WallpaperState.cell(px(config[Keys.FONT_SIZE]), px(config[Keys.LOG_LINE_HEIGHT]))
        val state = WallpaperState(
            name = name,
            code = code,
            set = ShaderParams.values.value[name].orEmpty(),
            follows = follows,
            formulas = ShaderParams.formulas.value[name].orEmpty(),
            given = ShaderValues.given.value,
            dark = colors(Palettes.named(theme, dark = true)),
            light = colors(Palettes.named(theme, dark = false)),
            alwaysDark = config[Keys.THEME_ALWAYS_DARK],
            cellW = cellW,
            cellH = cellH,
        )
        if (state == last) return
        // In a new process: the file may hold it already, and then it isn't written and synced again.
        if (last == null && WallpaperState.read(context) == state) {
            last = state
            return
        }
        WallpaperState.write(context, state)
        last = state
    }

    private fun colors(p: Palette): Map<String, Int> = mapOf(
        "bg" to p.bg, "surface" to p.surface, "fg" to p.fg, "dim" to p.dim, "accent" to p.prompt,
        "error" to p.error, "warn" to p.warn, "good" to p.good, "info" to p.info, "link" to p.link,
    ).mapValues { it.value.toArgb() }
}
