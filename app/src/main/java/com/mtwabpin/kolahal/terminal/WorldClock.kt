package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.TimeFormats
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs

/**
 * `date <place>`: shows the time in another place. A place is one of:
 * - a city from the time-zone database (`tokyo`, `new york`, `sao paulo`);
 * - a zone id (`Asia/Tokyo`);
 * - `utc`;
 * - an offset like `+5:30`.
 */
object WorldClock {
    /**
     * A zone and its display name: `America/New_York` → "New York".
     * A zone given by its id is loaded only when first used. Loading the rules of every zone for
     * the list of places was too slow: it took many frames when the user typed the first place.
     */
    class Place private constructor(val name: String, private val id: String?, private val fixed: ZoneId?) {
        constructor(name: String, zone: ZoneId) : this(name, null, zone)
        constructor(name: String, id: String) : this(name, id, null)

        val zone: ZoneId by lazy { fixed ?: ZoneId.of(id) }

        /** The name in the form that typed text is matched against ([key]). It is computed once, not for every place on every key press. */
        internal val folded: String by lazy(LazyThreadSafetyMode.PUBLICATION) { key(name) }

        override fun equals(other: Any?) = other is Place && other.name == name && other.zone == zone
        override fun hashCode() = 31 * name.hashCode() + zone.hashCode()
        override fun toString() = "Place(name=$name, zone=$zone)"
    }

    /**
     * The output for one place: its name, and its time with how far ahead or behind it is.
     * [detail] is [time], [day] (when it isn't today here) and [relative] together: `04:26 · 3h30 ahead`.
     */
    data class Line(val place: String, val detail: String, val time: String = "", val day: String = "", val relative: String = "")

    private val OFFSET = Regex("^([+-])(\\d{1,2})(?::(\\d{2}))?$")

    /** Every city in the time-zone database, sorted by name. If several ids share a name (links), it is listed once. */
    private val places: List<Place> by lazy {
        ZoneId.getAvailableZoneIds().asSequence()
            .filter { '/' in it && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
            .sortedWith(compareBy({ it.length }, { it }))
            .map { Place(it.substringAfterLast('/').replace('_', ' '), id = it) }
            .distinctBy { it.folded }
            .sortedBy { it.name }
            .toList()
    }

    private fun key(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Returns the place that [query] names: a city, a zone id, `utc` or an offset. Case, spaces and underscores are ignored. */
    fun find(query: String): Place? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val k = key(q)
        if (k == "utc" || k == "gmt" || k == "z") return Place("UTC", ZoneOffset.UTC)
        OFFSET.matchEntire(q)?.let { m ->
            val hours = m.groupValues[2].toInt()
            val minutes = m.groupValues[3].ifEmpty { "0" }.toInt()
            // Java supports offsets up to ±18:00 only.
            if (hours > 18 || minutes > 59 || (hours == 18 && minutes > 0)) return null
            val sign = if (m.groupValues[1] == "-") -1 else 1
            return Place("UTC$q", ZoneOffset.ofHoursMinutes(sign * hours, sign * minutes))
        }
        places.firstOrNull { it.folded == k }?.let { return it }
        if ('/' in q) {
            val id = q.replace(' ', '_')
            ZoneId.getAvailableZoneIds().firstOrNull { it.equals(id, ignoreCase = true) }
                ?.let { return Place(it.substringAfterLast('/').replace('_', ' '), ZoneId.of(it)) }
        }
        return null
    }

    /** Returns places whose name starts with [prefix], ignoring case and spaces: `date to` → Tokyo, Toronto… */
    fun suggest(prefix: String, limit: Int = 12): List<Place> {
        val p = key(prefix)
        if (p.isEmpty()) return emptyList()
        return places.filter { it.folded.startsWith(p) && it.folded != p }.take(limit)
    }

    /**
     * Returns `Tokyo` / `04:26 · 3h30 ahead`: the time at [place] at the moment [now], compared with [here].
     * It also shows the day when that day is not today here.
     */
    fun line(place: Place, now: Instant, here: ZoneId, clock24: Boolean, locale: Locale = Locale.getDefault()): Line {
        val there = now.atZone(place.zone)
        val local = now.atZone(here)
        val time = there.format(TimeFormats.clockFormat(clock24, locale))
        val minutes = (there.offset.totalSeconds - local.offset.totalSeconds) / 60
        val apart = abs(minutes).let { m -> "${m / 60}h" + if (m % 60 == 0) "" else (m % 60).toString().padStart(2, '0') }
        val relative = when {
            minutes == 0 -> "same time"
            minutes > 0 -> "$apart ahead"
            else -> "$apart behind"
        }
        val day = if (there.toLocalDate() == local.toLocalDate()) "" else there.format(TimeFormats.dayFormat(locale))
        return Line(place.name, time + (if (day.isEmpty()) "" else " $day") + " · $relative", time, day, relative)
    }

    /**
     * Returns the places named by [words]. Each place uses as many words as it needs (up to 3),
     * so `new york london` is two places. Returns null if a word does not start any place.
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
