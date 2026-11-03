package com.mtwabpin.kolahal.control

/** Pure argument parsing for the device-control commands (unit tested). */
object ControlArgs {

    sealed interface Level {
        /** Absolute level in percent, 0..100. */
        data class Percent(val pct: Int) : Level
        data object Up : Level
        data object Down : Level
        data object Mute : Level
        data object Unmute : Level
        data object Auto : Level
    }

    /** `volume` argument: 0-100 (optionally with %), up|+, down|-, mute, unmute. */
    fun volume(arg: String?): Level? = when (val a = arg?.trim()?.lowercase()) {
        null, "" -> null
        "up", "+", "louder" -> Level.Up
        "down", "-", "quieter" -> Level.Down
        "mute", "off" -> Level.Mute
        "unmute", "on" -> Level.Unmute
        else -> percent(a)?.let(Level::Percent)
    }

    /** `brightness` argument: 0-100 (optionally with %), auto, up, down. */
    fun brightness(arg: String?): Level? = when (val a = arg?.trim()?.lowercase()) {
        null, "" -> null
        "auto", "adaptive" -> Level.Auto
        "up", "+" -> Level.Up
        "down", "-" -> Level.Down
        else -> percent(a)?.let(Level::Percent)
    }

    /** "40", "40%" -> 40; null when not a number in 0..100. */
    fun percent(s: String): Int? = s.removeSuffix("%").toIntOrNull()?.takeIf { it in 0..100 }

    /** Percent of a 0..[max] scale, rounded to the nearest step. */
    fun toSteps(pct: Int, max: Int): Int = ((pct.coerceIn(0, 100) * max + 50) / 100).coerceIn(0, max)

    fun toPercent(steps: Int, max: Int): Int = if (max <= 0) 0 else ((steps * 100 + max / 2) / max).coerceIn(0, 100)

    /** "30s", "1m", "2min", "1h", "90" (seconds) -> milliseconds; null when invalid or 0. */
    fun duration(arg: String?): Long? {
        val m = Regex("^(\\d+)\\s*(s|sec|secs|m|min|mins|h|hr|hrs)?$").find(arg?.trim()?.lowercase() ?: return null) ?: return null
        // Small enough to multiply: a huge number wrapped round to a few minutes.
        val n = m.groupValues[1].toLongOrNull()?.takeIf { it in 1..MAX_DURATION_S } ?: return null
        val seconds = when (m.groupValues[2]) {
            "m", "min", "mins" -> n * 60
            "h", "hr", "hrs" -> n * 3600
            else -> n
        }
        return (seconds * 1000).takeIf { seconds <= MAX_DURATION_S }
    }

    /** The longest duration taken, in seconds: a year. */
    private const val MAX_DURATION_S = 366L * 86_400

    /** on/off/toggle words; null for anything else (the caller toggles on null input). */
    fun onOff(arg: String?): Boolean? = when (arg?.lowercase()) {
        "on", "1", "true", "yes" -> true
        "off", "0", "false", "no" -> false
        else -> null
    }

    /** Media position: 83000 -> "1:23", 3723000 -> "1:02:03". */
    fun clock(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        val root = java.util.Locale.ROOT
        return if (h > 0) String.format(root, "%d:%02d:%02d", h, m, s) else String.format(root, "%d:%02d", m, s)
    }

    /** A 10-cell bar for a percentage: "████░░░░░░". */
    fun bar(pct: Int): String {
        val filled = ((pct.coerceIn(0, 100) * 10 + 50) / 100)
        return "█".repeat(filled) + "░".repeat(10 - filled)
    }
}
