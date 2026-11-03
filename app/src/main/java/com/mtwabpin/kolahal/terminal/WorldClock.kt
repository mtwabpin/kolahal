package com.mtwabpin.kolahal.terminal

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * `date <place>`: the time somewhere else. A place is a city from the time-zone database (`tokyo`,
 * `new york`, `sao paulo`), a zone id (`Asia/Tokyo`), `utc`, or an offset like `+5:30`.
 */
object WorldClock {
    /**
     * A zone and how it is shown: `America/New_York` → "New York". A zone named by its id is loaded
     * when first used: loading every zone's rules for the list of places took a frame's worth of time
     * many times over, on the first place typed.
     */
    class Place private constructor(val name: String, private val id: String?, private val fixed: ZoneId?) {
        constructor(name: String, zone: ZoneId) : this(name, null, zone)
        constructor(name: String, id: String) : this(name, id, null)

        val zone: ZoneId by lazy { fixed ?: ZoneId.of(id) }

        override fun equals(other: Any?) = other is Place && other.name == name && other.zone == zone
        override fun hashCode() = 31 * name.hashCode() + zone.hashCode()
        override fun toString() = "Place(name=$name, zone=$zone)"
    }

    /** What one place prints: its name, and its time with how far ahead or behind it is. */
    data class Line(val place: String, val detail: String)

    private val OFFSET = Regex("^([+-])(\\d{1,2})(?::(\\d{2}))?$")

    /** Every city in the time-zone database, by name; a name that several ids share (links) is listed once. */
    private val places: List<Place> by lazy {
        ZoneId.getAvailableZoneIds().asSequence()
            .filter { '/' in it && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
            .sortedWith(compareBy({ it.length }, { it }))
            .map { Place(it.substringAfterLast('/').replace('_', ' '), id = it) }
            .distinctBy { key(it.name) }
            .sortedBy { it.name }
            .toList()
    }

    private fun key(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** The place [query] names, any case, spaces or underscores: a city, a zone id, `utc`, an offset. */
    fun find(query: String): Place? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val k = key(q)
        if (k == "utc" || k == "gmt" || k == "z") return Place("UTC", ZoneOffset.UTC)
        OFFSET.matchEntire(q)?.let { m ->
            val hours = m.groupValues[2].toInt()
            val minutes = m.groupValues[3].ifEmpty { "0" }.toInt()
            if (hours > 18 || minutes > 59) return null
            val sign = if (m.groupValues[1] == "-") -1 else 1
            return Place("UTC$q", ZoneOffset.ofHoursMinutes(sign * hours, sign * minutes))
        }
        places.firstOrNull { key(it.name) == k }?.let { return it }
        if ('/' in q) {
            val id = q.replace(' ', '_')
            ZoneId.getAvailableZoneIds().firstOrNull { it.equals(id, ignoreCase = true) }
                ?.let { return Place(it.substringAfterLast('/').replace('_', ' '), ZoneId.of(it)) }
        }
        return null
    }

    /** Places whose name starts with [prefix] (any case, spaces aside): `date to` → Tokyo, Toronto… */
    fun suggest(prefix: String, limit: Int = 12): List<Place> {
        val p = key(prefix)
        if (p.isEmpty()) return emptyList()
        return places.filter { key(it.name).startsWith(p) && key(it.name) != p }.take(limit)
    }

    /**
     * `Tokyo` / `04:26 · 3h30 ahead`: the time at [place] when it is [now] here in [here], and the
     * day too when it isn't today here.
     */
    fun line(place: Place, now: Instant, here: ZoneId, clock24: Boolean, locale: Locale = Locale.getDefault()): Line {
        val there = now.atZone(place.zone)
        val local = now.atZone(here)
        val time = there.format(DateTimeFormatter.ofPattern(if (clock24) "HH:mm" else "h:mm a", locale))
        val minutes = (there.offset.totalSeconds - local.offset.totalSeconds) / 60
        val apart = abs(minutes).let { m -> "${m / 60}h" + if (m % 60 == 0) "" else (m % 60).toString().padStart(2, '0') }
        val relative = when {
            minutes == 0 -> "same time"
            minutes > 0 -> "$apart ahead"
            else -> "$apart behind"
        }
        val day = if (there.toLocalDate() == local.toLocalDate()) "" else " " + there.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
        return Line(place.name, "$time$day · $relative")
    }

    /**
     * The places named by [words], each taking as many words as it needs: `new york london` is two.
     * Null for a word that starts no place.
     */
    fun parse(words: List<String>): List<Place>? {
        val out = ArrayList<Place>()
        var i = 0
        while (i < words.size) {
            val taken = (minOf(3, words.size - i) downTo 1).firstNotNullOfOrNull { n ->
                find(words.subList(i, i + n).joinToString(" "))?.let { it to n }
            } ?: return null
            out += taken.first
            i += taken.second
        }
        return out
    }
}
