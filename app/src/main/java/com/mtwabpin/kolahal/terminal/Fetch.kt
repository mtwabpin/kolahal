package com.mtwabpin.kolahal.terminal

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
import com.mtwabpin.kolahal.data.StyledText.Colour
import com.mtwabpin.kolahal.data.StyledText.Style
import com.mtwabpin.kolahal.data.takeWhole
import java.util.Locale
import kotlin.math.roundToInt

/**
 * `fetch`: this phone and Kolahal at a glance, as fastfetch shows a computer: Kolahal's icon drawn
 * in text beside a row per fact, then the theme's colours. On a narrow screen the icon goes above.
 */
object Fetch {
    /** One fact: what it is (`OS`) and what it says (`Android 16 · API 36`). */
    data class Row(val label: String, val value: String)

    /**
     * The icon: two lines of the log with their coloured bars, then the prompt and its cursor (no
     * frame: each log line is its own row, and a frame's sides wouldn't meet). Each row has a mask
     * of its colours: `m` muted, `w` warning, `g` good, `a` accent, space for none.
     */
    private val LOGO = listOf(
        "▍ ━━━━━" to "w mmmmm",
        "▍ ━━━  " to "g mmm  ",
        "› ▆    " to "a a    ",
    )
    private val LOGO_WIDTH = LOGO.first().first.length
    private const val GAP = 2
    private const val LABEL_WIDTH = 8

    /** Info narrower than this goes under the icon instead of beside it. */
    private const val MIN_BESIDE = 24

    /** The theme's colours, a block each, as fastfetch ends with the terminal's. */
    private val SWATCHES = listOf(Colour.ACCENT, Colour.LINK, Colour.INFO, Colour.GOOD, Colour.WARN, Colour.BAD, Colour.MUTED, Colour.TEXT)

    private fun colourOf(mask: Char) = when (mask) {
        'm' -> Colour.MUTED
        'w' -> Colour.WARN
        'g' -> Colour.GOOD
        'a' -> Colour.ACCENT
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
                append(colourOf(mask[start])?.let { StyledText.colour(it, run) } ?: run)
                start = end
            }
        }
    }

    private fun ellipsize(s: String, max: Int) = if (s.length <= max) s else s.takeWhole((max - 1).coerceAtLeast(0)) + "…"

    /**
     * The lines `fetch` prints, styled ([StyledText]), for a log [cols] characters wide: a
     * [user]`@`[device] title, a rule, a row per fact and the colours, beside the icon when they fit.
     */
    fun layout(device: String, rows: List<Row>, cols: Int, user: String = Keys.USER_NAME.default): List<String> {
        val beside = cols - LOGO_WIDTH - GAP >= MIN_BESIDE
        val width = if (beside) cols - LOGO_WIDTH - GAP else cols
        val title = ellipsize("$user@$device", width)
        val info = buildList {
            // Cut short of its @ (a few columns, at a huge text size): the name alone, in one piece.
            val accent = { s: String -> StyledText.wrap(Style.BOLD, StyledText.colour(Colour.ACCENT, s)) }
            add(if ('@' in title) accent(title.substringBefore('@')) + "@" + accent(title.substringAfter('@')) else accent(title))
            add(StyledText.colour(Colour.MUTED, "─".repeat(title.length)))
            for ((label, value) in rows) {
                add(StyledText.wrap(Style.BOLD, StyledText.colour(Colour.ACCENT, label.padEnd(LABEL_WIDTH))) + ellipsize(value, width - LABEL_WIDTH))
            }
            add("")
            val block = if (width >= SWATCHES.size * 3) "███" else "██"
            add(SWATCHES.joinToString("") { StyledText.colour(it, block) })
        }
        if (!beside) return LOGO.indices.map(::logoLine) + "" + info
        val pad = " ".repeat(LOGO_WIDTH + GAP)
        return info.mapIndexed { i, line -> (if (i < LOGO.size) logoLine(i) + " ".repeat(GAP) else pad) + line }
    }

    /**
     * How many characters of the terminal font fit across a log line: in the log [widthPx] wide
     * (the whole display when home hasn't shown yet, or in a test).
     */
    fun columns(context: Context, widthPx: Int? = null): Int {
        val config = Config.get(context)
        val metrics = context.resources.displayMetrics
        val scale = context.resources.configuration.fontScale
        val size = config[Keys.FONT_SIZE]
        // The bundled mono fonts are 0.6 em wide. The log's side padding and its gutter.
        val gutter = if (config[Keys.LOG_GUTTER]) 10 else 0
        val widthDp = (widthPx ?: metrics.widthPixels) / metrics.density - 24 - gutter
        return (widthDp / (size * scale * 0.6f)).toInt()
    }

    /** The phone's name, and its facts. */
    fun facts(context: Context): Pair<String, List<Row>> {
        val config = Config.get(context)
        val device = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL
        val rows = buildList {
            add(Row("OS", "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}"))
            val maker = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.getDefault()) }
            add(Row("Device", if (Build.MODEL.startsWith(maker, ignoreCase = true)) Build.MODEL else "$maker ${Build.MODEL}"))
            System.getProperty("os.version")?.let { add(Row("Kernel", it)) }
            add(Row("Uptime", uptime(SystemClock.elapsedRealtime())))
            add(Row("Kolahal", BuildConfig.VERSION_NAME + if (BuildConfig.LOCK_SERVICE) "" else " · Play build"))
            val apps = AppRepository.get(context).apps.value
            if (apps.isNotEmpty()) {
                val others = apps.groupingBy { it.profile.kind }.eachCount().filterKeys { it != ProfileKind.PERSONAL }
                add(Row("Apps", (listOf("${apps.size}") + others.map { (kind, n) -> "$n ${kind.label}" }).joinToString(" · ")))
            }
            val scripts = ScriptRepository.get(context).all()
            add(Row("Scripts", "${scripts.size} · ${scripts.count { it.enabled }} on"))
            add(Row("Feeds", com.mtwabpin.kolahal.feeds.FeedRepository.get(context).all().size.toString()))
            val dark = config[Keys.THEME_ALWAYS_DARK] ||
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            add(Row("Theme", "${config[Keys.THEME]} · ${if (dark) "dark" else "light"}"))
            add(Row("Font", "${config[Keys.FONT]} · ${config[Keys.FONT_SIZE]}sp"))
            context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.let { d ->
                val mode = d.mode
                add(Row("Display", "${mode.physicalWidth}×${mode.physicalHeight} · ${d.refreshRate.roundToInt()} Hz"))
            }
            val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } else null
            add(Row("CPU", "${soc ?: Build.HARDWARE} · ${Runtime.getRuntime().availableProcessors()} cores"))
            context.getSystemService(ActivityManager::class.java)?.let { am ->
                val m = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
                add(Row("Memory", "${gb(m.totalMem - m.availMem)} / ${gb(m.totalMem)} GB"))
            }
            runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()?.let { fs ->
                add(Row("Storage", "${gb(fs.totalBytes - fs.availableBytes)} / ${gb(fs.totalBytes)} GB"))
            }
            val status = com.mtwabpin.kolahal.status.DeviceStatus.get(context).status.value
            status.batteryPct?.let { add(Row("Battery", "$it%" + if (status.charging) " · charging" else "")) }
            add(Row("Network", status.network))
            add(Row("Unlocks", "${com.mtwabpin.kolahal.unlock.UnlockCounter.get(context).today.value} today"))
        }
        return device to rows
    }

    /** 3d 4h 12m; 4h 12m; 12m. */
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

    /** Bytes as gigabytes, as Android's storage settings count them: 1 decimal. */
    private fun gb(bytes: Long) = String.format(Locale.US, "%.1f", bytes / 1e9)
}
