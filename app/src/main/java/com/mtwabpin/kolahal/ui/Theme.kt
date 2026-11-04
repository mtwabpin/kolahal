package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.CustomThemes

data class Palette(
    val bg: Color,
    val surface: Color,
    val fg: Color,
    val dim: Color,
    val prompt: Color,
    val error: Color,
    val info: Color,
    val link: Color,
    val script: Color,
    val personal: Color,
    val work: Color,
    val private: Color,
    /** For scripts' `fmt.color`: up, done, passing; and a warning short of an error. */
    val good: Color,
    val warn: Color,
)

object Palettes {
    val green = Palette(
        bg = Color(0xFF0B0D0B), surface = Color(0xFF151915), fg = Color(0xFFD7DED3), dim = Color(0xFF7F8A7B),
        prompt = Color(0xFF8FD18A), error = Color(0xFFF0907D), info = Color(0xFF9DB8A0), link = Color(0xFF7FB8F0),
        script = Color(0xFFB5CEA8), personal = Color(0xFF8FD18A), work = Color(0xFFF2B35B), private = Color(0xFFC792EA),
        good = Color(0xFF8FD18A), warn = Color(0xFFF2B35B),
    )
    val amber = Palette(
        bg = Color(0xFF0E0A05), surface = Color(0xFF1A140B), fg = Color(0xFFFFCC80), dim = Color(0xFFA08256),
        prompt = Color(0xFFFFB000), error = Color(0xFFFF6E5A), info = Color(0xFFD9A25F), link = Color(0xFFFFE0A3),
        script = Color(0xFFE8B96B), personal = Color(0xFFFFB000), work = Color(0xFF7FD4FF), private = Color(0xFFE08CFF),
        good = Color(0xFFB5D96B), warn = Color(0xFFFF8F40),
    )
    val mono = Palette(
        bg = Color(0xFF000000), surface = Color(0xFF141414), fg = Color(0xFFE6E6E6), dim = Color(0xFF8C8C8C),
        prompt = Color(0xFFFFFFFF), error = Color(0xFFFF8080), info = Color(0xFFB0B0B0), link = Color(0xFFCCCCCC),
        script = Color(0xFFD0D0D0), personal = Color(0xFFB8D8B8), work = Color(0xFFFFD27F), private = Color(0xFFD7A6FF),
        good = Color(0xFF9FD89F), warn = Color(0xFFFFD27F),
    )
    val dracula = Palette(
        bg = Color(0xFF282A36), surface = Color(0xFF343746), fg = Color(0xFFF8F8F2), dim = Color(0xFF8A93C2),
        prompt = Color(0xFF50FA7B), error = Color(0xFFFF5555), info = Color(0xFF8BE9FD), link = Color(0xFF8BE9FD),
        script = Color(0xFFF1FA8C), personal = Color(0xFF50FA7B), work = Color(0xFFFFB86C), private = Color(0xFFBD93F9),
        good = Color(0xFF50FA7B), warn = Color(0xFFFFB86C),
    )
    val solarized = Palette(
        bg = Color(0xFF002B36), surface = Color(0xFF073642), fg = Color(0xFF93A1A1), dim = Color(0xFF7A9097),
        prompt = Color(0xFF859900), error = Color(0xFFDC322F), info = Color(0xFF2AA198), link = Color(0xFF268BD2),
        script = Color(0xFFB58900), personal = Color(0xFF859900), work = Color(0xFFCB4B16), private = Color(0xFF6C71C4),
        good = Color(0xFF859900), warn = Color(0xFFB58900),
    )

    val gruvbox = Palette(
        bg = Color(0xFF282828), surface = Color(0xFF3C3836), fg = Color(0xFFEBDBB2), dim = Color(0xFFA89984),
        prompt = Color(0xFFB8BB26), error = Color(0xFFFB4934), info = Color(0xFF8EC07C), link = Color(0xFF83A598),
        script = Color(0xFFFABD2F), personal = Color(0xFFB8BB26), work = Color(0xFFFE8019), private = Color(0xFFD3869B),
        good = Color(0xFFB8BB26), warn = Color(0xFFFABD2F),
    )
    /** Green's light version: the same greens, darker, on a pale green-grey. */
    val greenLight = Palette(
        bg = Color(0xFFF3F7F1), surface = Color(0xFFE3EBE0), fg = Color(0xFF1E2A1D), dim = Color(0xFF5B6957),
        prompt = Color(0xFF2F7D32), error = Color(0xFFB3261E), info = Color(0xFF4B6B4E), link = Color(0xFF1D63A8),
        script = Color(0xFF3F6B35), personal = Color(0xFF2F7D32), work = Color(0xFF9A5B00), private = Color(0xFF7A3FB0),
        good = Color(0xFF2F7D32), warn = Color(0xFF9A5B00),
    )
    /** Amber's light version: brown and amber ink on paper. */
    val amberLight = Palette(
        bg = Color(0xFFFFF7E8), surface = Color(0xFFF6E8CC), fg = Color(0xFF3D2A0C), dim = Color(0xFF7E6136),
        prompt = Color(0xFFA05F00), error = Color(0xFFB83A26), info = Color(0xFF8A5E22), link = Color(0xFF7A4B00),
        script = Color(0xFF85571A), personal = Color(0xFFA05F00), work = Color(0xFF0A6A93), private = Color(0xFF8E3AB8),
        good = Color(0xFF4E7A12), warn = Color(0xFFB54A00),
    )
    /** Mono's light version: black on white. */
    val monoLight = Palette(
        bg = Color(0xFFFFFFFF), surface = Color(0xFFEFEFEF), fg = Color(0xFF141414), dim = Color(0xFF5E5E5E),
        prompt = Color(0xFF000000), error = Color(0xFFC0392B), info = Color(0xFF4A4A4A), link = Color(0xFF2B2B2B),
        script = Color(0xFF333333), personal = Color(0xFF3D6B3D), work = Color(0xFF8A5600), private = Color(0xFF6E44A8),
        good = Color(0xFF2E6B2E), warn = Color(0xFF8A5600),
    )
    /** Dracula's light version, Alucard. */
    val draculaLight = Palette(
        bg = Color(0xFFFFFBEB), surface = Color(0xFFF1ECD8), fg = Color(0xFF1F1F1F), dim = Color(0xFF635D97),
        prompt = Color(0xFF14710A), error = Color(0xFFCB3A2A), info = Color(0xFF036A96), link = Color(0xFF036A96),
        script = Color(0xFF846E15), personal = Color(0xFF14710A), work = Color(0xFFA34D14), private = Color(0xFF644AC9),
        good = Color(0xFF14710A), warn = Color(0xFFA34D14),
    )
    /** Solarized's light version (base3 background), its accents a shade darker to read on it. */
    val solarizedLight = Palette(
        bg = Color(0xFFFDF6E3), surface = Color(0xFFEEE8D5), fg = Color(0xFF3D525A), dim = Color(0xFF657B83),
        prompt = Color(0xFF6F8000), error = Color(0xFFC42D2A), info = Color(0xFF1A847C), link = Color(0xFF1F76B5),
        script = Color(0xFF8F6C00), personal = Color(0xFF6F8000), work = Color(0xFFB54312), private = Color(0xFF5F64B4),
        good = Color(0xFF6F8000), warn = Color(0xFF8F6C00),
    )
    /** Gruvbox's light version, with its darker ("faded") accents. */
    val gruvboxLight = Palette(
        bg = Color(0xFFFBF1C7), surface = Color(0xFFEBDBB2), fg = Color(0xFF3C3836), dim = Color(0xFF7C6F64),
        prompt = Color(0xFF79740E), error = Color(0xFF9D0006), info = Color(0xFF427B58), link = Color(0xFF076678),
        script = Color(0xFF9C6512), personal = Color(0xFF79740E), work = Color(0xFFAF3A03), private = Color(0xFF8F3F71),
        good = Color(0xFF79740E), warn = Color(0xFF9C6512),
    )
    /** Gruvbox Material, light "medium" background. */
    val gruvboxMaterialLight = Palette(
        bg = Color(0xFFFBF1C7), surface = Color(0xFFF2E5BC), fg = Color(0xFF654735), dim = Color(0xFF7C6F64),
        prompt = Color(0xFF6C782E), error = Color(0xFFC14A4A), info = Color(0xFF4C7A5D), link = Color(0xFF45707A),
        script = Color(0xFF9E6308), personal = Color(0xFF6C782E), work = Color(0xFFAB520A), private = Color(0xFF945E80),
        good = Color(0xFF6C782E), warn = Color(0xFF9E6308),
    )
    /** Gruvbox Material, dark "medium" background. */
    val gruvboxMaterial = Palette(
        bg = Color(0xFF292828), surface = Color(0xFF32302F), fg = Color(0xFFD4BE98), dim = Color(0xFFA89984),
        prompt = Color(0xFFA9B665), error = Color(0xFFEA6962), info = Color(0xFF89B482), link = Color(0xFF7DAEA3),
        script = Color(0xFFD8A657), personal = Color(0xFFA9B665), work = Color(0xFFE78A4E), private = Color(0xFFD3869B),
        good = Color(0xFFA9B665), warn = Color(0xFFD8A657),
    )

    /** Each built-in theme: its dark version, then its light one. */
    private val BUILT_IN: Map<String, Pair<Palette, Palette>> by lazy {
        mapOf(
            "green" to (green to greenLight),
            "amber" to (amber to amberLight),
            "mono" to (mono to monoLight),
            "dracula" to (dracula to draculaLight),
            "solarized" to (solarized to solarizedLight),
            "gruvbox" to (gruvbox to gruvboxLight),
            "gruvbox-material" to (gruvboxMaterial to gruvboxMaterialLight),
        )
    }

    /**
     * A theme's [dark] or light version: built-in, or one people made ([CustomThemes]; without a
     * light version, the same in both). Green for a name it doesn't know.
     */
    fun named(name: String, dark: Boolean = true): Palette {
        BUILT_IN[name]?.let { (d, l) -> return if (dark) d else l }
        val colors = CustomThemes.colors(name) ?: return if (dark) green else greenLight
        return fromColors(if (dark) colors else CustomThemes.light(colors) ?: colors)
    }

    /** Both versions of a theme by role ([CustomThemes]' form): where the theme builder starts. */
    fun themeColors(name: String): Map<String, Int> {
        val dark = colorsOf(named(name, dark = true))
        val light = colorsOf(named(name, dark = false))
        return dark + light.mapKeys { CustomThemes.LIGHT + it.key }
    }

    /** A custom theme's colours, by role ([CustomThemes.ROLES]), as a palette. */
    fun fromColors(c: Map<String, Int>): Palette {
        fun role(name: String) = Color(c.getValue(name))
        return Palette(
            bg = role("background"), surface = role("surface"), fg = role("text"), dim = role("muted"),
            prompt = role("accent"), error = role("error"), info = role("info"), link = role("link"),
            script = role("script"), personal = role("personal"), work = role("work"), private = role("private"),
            good = role("good"), warn = role("warn"),
        )
    }

    /** A palette's colours by role: where the theme builder starts. */
    fun colorsOf(p: Palette): Map<String, Int> = mapOf(
        "background" to p.bg, "surface" to p.surface, "text" to p.fg, "muted" to p.dim, "accent" to p.prompt,
        "link" to p.link, "info" to p.info, "script" to p.script, "error" to p.error,
        "personal" to p.personal, "work" to p.work, "private" to p.private, "good" to p.good, "warn" to p.warn,
    ).mapValues { it.value.toArgb() }
}

/**
 * Current terminal colours and type. Backed by Compose state, so changing the theme or font
 * size recomposes everything that reads from it.
 */
object Term {
    var palette by mutableStateOf(Palettes.green)
    var fontSize by mutableIntStateOf(14)

    /** Terminal typeface (`font`); [FontFamily.Monospace] is the system monospace. */
    var fontFamily by mutableStateOf<FontFamily>(FontFamily.Monospace)

    /** Minimum height (sp: it follows the phone's text size) of a plain log line (`log.line-height`); tappable lines get +10, two-column rows +14 ([tallerTaps]). */
    var lineHeight by mutableIntStateOf(22)

    /** `log.taller-taps`: tappable lines and two-column rows taller than [lineHeight]; off, every line is that tall. */
    var tallerTaps by mutableStateOf(false)

    /** Minimum height (dp) of suggestion and app-grid chips (`chips.height`). */
    var chipHeight by mutableIntStateOf(48)

    val bg get() = palette.bg

    val surface get() = palette.surface
    val fg get() = palette.fg
    val dim get() = palette.dim
    val prompt get() = palette.prompt
    val error get() = palette.error
    val info get() = palette.info
    val link get() = palette.link
    val script get() = palette.script

    fun profileColor(kind: ProfileKind) = when (kind) {
        ProfileKind.PERSONAL -> palette.personal
        ProfileKind.WORK -> palette.work
        ProfileKind.PRIVATE -> palette.private
        ProfileKind.CLONE -> palette.info
    }

    /** `font.sans`: the phone's own (sans-serif) font for nothing, the prompt, or the whole terminal. */
    var sans by mutableStateOf(Sans.OFF)

    enum class Sans {
        OFF, PROMPT, ALL;

        companion object {
            fun of(value: String) = when (value) {
                "prompt" -> PROMPT
                "all" -> ALL
                else -> OFF
            }
        }
    }

    /** The terminal's text: [fontFamily], or the phone's font with `font.sans all`. */
    val text: TextStyle get() = style(if (sans == Sans.ALL) FontFamily.Default else fontFamily)

    /** Always [fontFamily]: the script editor, and lists that line columns up with spaces. */
    val code: TextStyle get() = style(fontFamily)

    /** The prompt line: the phone's font from `font.sans prompt` on. */
    val promptText: TextStyle get() = style(if (sans == Sans.OFF) fontFamily else FontFamily.Default)

    /** Always the phone's font, whatever `font.sans` says: the app selector with `selector.system-font`. */
    val phoneText: TextStyle get() = style(FontFamily.Default)

    private var cachedPalette: Palette? = null
    private var cachedSize = 0
    private val styles = HashMap<FontFamily, TextStyle>()

    /** Base text style in [family], rebuilt only when the palette or font size changes. */
    private fun style(family: FontFamily): TextStyle {
        val p = palette
        val size = fontSize
        if (cachedPalette !== p || cachedSize != size) {
            styles.clear()
            cachedPalette = p
            cachedSize = size
        }
        return styles.getOrPut(family) {
            TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = (size * 1.36f).sp, color = p.fg)
        }
    }
}

@Composable
fun KolahalTheme(content: @Composable () -> Unit) {
    val p = Term.palette
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = p.bg,
            surface = p.surface,
            surfaceContainer = p.surface,
            onSurface = p.fg,
            onBackground = p.fg,
            primary = p.prompt,
            error = p.error,
        ),
        content = content,
    )
}

/**
 * Top / side padding that clears both the status bar and any camera cutout. With the status bar
 * hidden (`screen.bars hide`) its inset is 0, but the cutout's is not, so nothing ends up behind
 * the camera hole.
 */
@Composable
fun androidx.compose.ui.Modifier.cutoutSafePadding(): androidx.compose.ui.Modifier = windowInsetsPadding(
    WindowInsets.statusBars
        .union(WindowInsets.displayCutout)
        .only(androidx.compose.foundation.layout.WindowInsetsSides.Top + androidx.compose.foundation.layout.WindowInsetsSides.Horizontal),
)

/**
 * The page's colour behind three-button navigation, over a list that scrolls under it: the list's
 * text would show between the buttons. Gesture navigation's handle takes no taps (its inset here is
 * 0), nor does a hidden bar (`screen.bars immersive`), so the list still runs to the edge there. Put
 * it before the scroll, on a list that reaches the bottom of the screen; [alpha] fades it with the page.
 */
@Composable
fun androidx.compose.ui.Modifier.navigationBarScrim(alpha: () -> Float = { 1f }): androidx.compose.ui.Modifier {
    val color = MaterialTheme.colorScheme.surface
    // The keyboard covers the bar while it shows.
    val bar = WindowInsets.tappableElement.exclude(WindowInsets.ime)
    return drawWithContent {
        drawContent()
        val height = bar.getBottom(this).toFloat()
        if (height > 0f) drawRect(color.copy(alpha = alpha()), topLeft = Offset(0f, size.height - height), size = Size(size.width, height))
    }
}

/**
 * Home's background: the theme's colour. With [wallpaper] above 0 (`log.wallpaper`,
 * `widgets.wallpaper`) the phone's wallpaper, which the window lets through then, shows that many
 * percent: the background is drawn that much fainter.
 */
fun androidx.compose.ui.Modifier.page(wallpaper: Int = 0): androidx.compose.ui.Modifier =
    background(Term.bg.copy(alpha = Term.bg.alpha * (1f - wallpaper.coerceIn(0, 100) / 100f)))
