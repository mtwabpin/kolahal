package com.mtwabpin.kolahal.control

/** Pure argument parsing for the device-control commands. It is unit tested. */
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

    /** Parses the `volume` argument: 0-100 (with or without %), up|+, down|-, mute, unmute. */
    fun volume(arg: String?): Level? = when (val a = arg?.trim()?.lowercase()) {
        null, "" -> null
        "up", "+", "louder" -> Level.Up
        "down", "-", "quieter" -> Level.Down
        "mute", "off" -> Level.Mute
        "unmute", "on" -> Level.Unmute
        else -> percent(a)?.let(Level::Percent)
    }

    /** Parses the `brightness` argument: 0-100 (with or without %), auto, up, down. */
    fun brightness(arg: String?): Level? = when (val a = arg?.trim()?.lowercase()) {
        null, "" -> null
        "auto", "adaptive" -> Level.Auto
        "up", "+" -> Level.Up
        "down", "-" -> Level.Down
        else -> percent(a)?.let(Level::Percent)
    }

    /** "40" and "40%" give 40. Returns null when it isn't a number in 0..100. */
    fun percent(s: String): Int? = s.removeSuffix("%").toIntOrNull()?.takeIf { it in 0..100 }

    /** Converts a percent to a step on a 0..[max] scale, rounded to the nearest step. */
    fun toSteps(pct: Int, max: Int): Int = ((pct.coerceIn(0, 100) * max + 50) / 100).coerceIn(0, max)

    fun toPercent(steps: Int, max: Int): Int = if (max <= 0) 0 else ((steps * 100 + max / 2) / max).coerceIn(0, 100)

    /** Converts "30s", "1m", "2min", "1h" or "90" (seconds) to milliseconds. Returns null when invalid or 0. */
    fun duration(arg: String?): Long? {
        val m = Regex("^(\\d+)\\s*(s|sec|secs|m|min|mins|h|hr|hrs)?$").find(arg?.trim()?.lowercase() ?: return null) ?: return null
        // Limit the number before multiplying. Without the limit, a huge number overflowed into a few minutes.
        val n = m.groupValues[1].toLongOrNull()?.takeIf { it in 1..MAX_DURATION_S } ?: return null
        val seconds = when (m.groupValues[2]) {
            "m", "min", "mins" -> n * 60
            "h", "hr", "hrs" -> n * 3600
            else -> n
        }
        return (seconds * 1000).takeIf { seconds <= MAX_DURATION_S }
    }

    /** The longest duration accepted, in seconds: one year. */
    private const val MAX_DURATION_S = 366L * 86_400

    /** Parses on/off words. Returns null for anything else, and the caller then toggles. */
    fun onOff(arg: String?): Boolean? = when (arg?.lowercase()) {
        "on", "1", "true", "yes" -> true
        "off", "0", "false", "no" -> false
        else -> null
    }

    /** Formats a media position: 83000 gives "1:23", 3723000 gives "1:02:03". */
    fun clock(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        val root = java.util.Locale.ROOT
        return if (h > 0) String.format(root, "%d:%02d:%02d", h, m, s) else String.format(root, "%d:%02d", m, s)
    }
}
