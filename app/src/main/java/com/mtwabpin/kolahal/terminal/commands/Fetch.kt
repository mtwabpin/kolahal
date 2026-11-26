/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.StyledText.ColorRole
import com.mtwabpin.kolahal.data.StyledText.Style
import com.mtwabpin.kolahal.data.takeWhole
import java.util.Locale
import kotlin.math.roundToInt

/**
 * `fetch`: a summary of this phone and Kolahal, like fastfetch shows for a computer.
 * Kolahal's icon is drawn in text, with one row per fact beside it, then the theme's colors.
 * On a narrow screen the icon goes above the facts.
 */
object Fetch {
    /** One fact: its label (`OS`) and its value (`Android 16 · API 36`), in [color] when it has one. */
    data class Row(val label: String, val value: String, val color: ColorRole? = null)

    /**
     * The icon: two answers with their colored bars (a big number and a smaller one, then a range
     * bar), then the prompt and its cursor.
     * It has no frame, because each log line is a separate row and the sides of a frame would not meet.
     * Each row has a color mask:
     * - `m` muted
     * - `w` warning
     * - `g` good
     * - `a` accent
     * - space: no color
     */
    private val LOGO = listOf(
        "▍ ███ ▄▄" to "w mmm mm",
        "▍ ─━━━━─" to "g mggggm",
        "› ▆     " to "a a     ",
    )
    private val LOGO_WIDTH = LOGO.first().first.length
    private const val GAP = 2
    private const val LABEL_WIDTH = 8

    /** If the space for the facts is narrower than this, they go under the icon instead of beside it. */
    private const val MIN_BESIDE = 24

    /** The theme's colors, one block each. fastfetch also ends with the terminal's colors. */
    private val SWATCHES = listOf(
        ColorRole.ACCENT,
        ColorRole.LINK,
        ColorRole.INFO,
        ColorRole.GOOD,
        ColorRole.WARN,
        ColorRole.BAD,
        ColorRole.MUTED,
        ColorRole.TEXT
    )

    private fun colorOf(mask: Char) = when (mask) {
        'm' -> ColorRole.MUTED
        'w' -> ColorRole.WARN
        'g' -> ColorRole.GOOD
        'a' -> ColorRole.ACCENT
        else -> null
    }

    private fun logoLine(i: Int): String {
        val (text, mask) = LOGO[i]
        return buildString {
            var start = 0
            while (start < text.length) {
                var end = start + 1
                while (end < text.length && mask[end] == mask[start]) end++
                val run = text.substring(start, end)
                append(colorOf(mask[start])?.let { StyledText.color(it, run) } ?: run)
                start = end
            }
        }
    }

    private fun ellipsize(s: String, max: Int) =
        if (s.length <= max) s else s.takeWhole((max - 1).coerceAtLeast(0)) + "…"

    /**
     * Returns the styled lines ([StyledText]) that `fetch` prints, for a log [cols] characters wide:
     * - a [user]`@`[device] title;
     * - a rule;
     * - one row per fact;
     * - the colors.
     * They go beside the icon when they fit.
     */
    fun layout(
        device: String,
        rows: List<Row>,
        cols: Int,
        user: String = Keys.USER_NAME.default
    ): List<String> {
        val beside = cols - LOGO_WIDTH - GAP >= MIN_BESIDE
        val width = if (beside) cols - LOGO_WIDTH - GAP else cols
        val title = ellipsize("$user@$device", width)
        val info = buildList {
            // If the title is cut before its @ (few columns, at a huge text size), color it as one piece.
            val accent =
                { s: String -> StyledText.wrap(Style.BOLD, StyledText.color(ColorRole.ACCENT, s)) }
            add(
                if ('@' in title) accent(title.substringBefore('@')) + "@" + accent(
                    title.substringAfter(
                        '@'
                    )
                ) else accent(title)
            )
            add(StyledText.color(ColorRole.MUTED, "─".repeat(title.length)))
            for ((label, value, color) in rows) {
                val shown = ellipsize(value, width - LABEL_WIDTH)
                add(
                    StyledText.wrap(
                        Style.BOLD,
                        StyledText.color(ColorRole.ACCENT, label.padEnd(LABEL_WIDTH))
                    ) + (color?.let { StyledText.color(it, shown) } ?: shown)
                )
            }
            add("")
            val block = if (width >= SWATCHES.size * 3) "███" else "██"
            add(SWATCHES.joinToString("") { StyledText.color(it, block) })
        }
        if (!beside) return LOGO.indices.map(::logoLine) + "" + info
        val pad = " ".repeat(LOGO_WIDTH + GAP)
        return info.mapIndexed { i, line -> (if (i < LOGO.size) logoLine(i) + " ".repeat(GAP) else pad) + line }
    }

    /**
     * Returns how many characters of the terminal font fit on one log line, for a log [widthPx] wide.
     * Without [widthPx] (home not shown yet, or in a test), it uses the whole display width.
     */
    fun columns(context: Context, widthPx: Int? = null): Int {
        val config = Config.get(context)
        val metrics = context.resources.displayMetrics
        val scale = context.resources.configuration.fontScale
        val size = config[Keys.FONT_SIZE]
        // The bundled mono fonts are 0.6 em wide. We subtract the log's side padding (24) and its gutter.
        val gutter = if (config[Keys.LOG_GUTTER]) 10 else 0
        val widthDp = (widthPx ?: metrics.widthPixels) / metrics.density - 24 - gutter
        return (widthDp / (size * scale * 0.6f)).toInt()
    }

    /** Returns the phone's name and its facts. */
    fun facts(context: Context): Pair<String, List<Row>> {
        val config = Config.get(context)
        val device = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL
        val rows = buildList {
            add(Row("OS", "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}"))
            val maker = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.getDefault()) }
            add(
                Row(
                    "Device",
                    if (Build.MODEL.startsWith(
                            maker,
                            ignoreCase = true
                        )
                    ) Build.MODEL else "$maker ${Build.MODEL}"
                )
            )
            System.getProperty("os.version")?.let { add(Row("Kernel", it)) }
            add(Row("Uptime", uptime(SystemClock.elapsedRealtime())))
            add(
                Row(
                    "Kolahal",
                    BuildConfig.VERSION_NAME + if (BuildConfig.SELF_UPDATE) "" else " · Play build"
                )
            )
            val apps = AppRepository.get(context).apps.value
            if (apps.isNotEmpty()) {
                val others = apps.groupingBy { it.profile.kind }.eachCount()
                    .filterKeys { it != ProfileKind.PERSONAL }
                add(
                    Row(
                        "Apps",
                        (listOf("${apps.size}") + others.map { (kind, n) -> "$n ${kind.label}" }).joinToString(
                            " · "
                        )
                    )
                )
            }
            val scripts = ScriptRepository.get(context).all()
            add(Row("Scripts", "${scripts.size} · ${scripts.count { it.enabled }} on"))
            add(
                Row(
                    "Feeds",
                    com.mtwabpin.kolahal.feeds.FeedRepository.get(context).all().size.toString()
                )
            )
            val dark = config[Keys.THEME_ALWAYS_DARK] ||
                    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            add(Row("Theme", "${config[Keys.THEME]} · ${if (dark) "dark" else "light"}"))
            add(Row("Font", "${config[Keys.FONT]} · ${config[Keys.FONT_SIZE]}sp"))
            context.getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY)?.let { d ->
                    val mode = d.mode
                    add(
                        Row(
                            "Display",
                            "${mode.physicalWidth}×${mode.physicalHeight} · ${d.refreshRate.roundToInt()} Hz"
                        )
                    )
                }
            val soc =
                if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } else null
            add(
                Row(
                    "CPU",
                    "${soc ?: Build.HARDWARE} · ${Runtime.getRuntime().availableProcessors()} cores"
                )
            )
            context.getSystemService(ActivityManager::class.java)?.let { am ->
                val m = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
                add(Row("Memory", "${gb(m.totalMem - m.availMem)} / ${gb(m.totalMem)} GB"))
            }
            runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()?.let { fs ->
                add(
                    Row(
                        "Storage",
                        "${gb(fs.totalBytes - fs.availableBytes)} / ${gb(fs.totalBytes)} GB"
                    )
                )
            }
            val status = com.mtwabpin.kolahal.status.DeviceStatus.get(context).status.value
            status.batteryPct?.let {
                add(
                    Row(
                        "Battery",
                        "$it%" + if (status.charging) " · charging" else ""
                    )
                )
            }
            add(Row("Network", status.network))
            // From the daily limit, in the warning or error color, with the limit after it.
            val unlocks = com.mtwabpin.kolahal.unlock.UnlockCounter.get(context).today.value
            val limit = config[Keys.USAGE_UNLOCK_LIMIT]
            val level = com.mtwabpin.kolahal.usage.UsageLimits.unlocks(unlocks, limit)
            add(
                Row(
                    "Unlocks",
                    "$unlocks today" + if (level == com.mtwabpin.kolahal.usage.UsageLimits.Level.NONE) "" else ", limit $limit",
                    com.mtwabpin.kolahal.usage.UsageLimits.role(level),
                )
            )
        }
        return device to rows
    }

    /** Formats an uptime as `3d 4h 12m`, `4h 12m` or `12m`. */
    fun uptime(millis: Long): String {
        val minutes = millis / 60_000
        val d = minutes / (24 * 60)
        val h = minutes / 60 % 24
        val m = minutes % 60
        return when {
            d > 0 -> "${d}d ${h}h ${m}m"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    /** Formats bytes as gigabytes (10^9, as Android's storage settings count them) with 1 decimal. */
    private fun gb(bytes: Long) = String.format(Locale.US, "%.1f", bytes / 1e9)
}
