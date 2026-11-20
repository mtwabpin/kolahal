package com.mtwabpin.kolahal.usage

import com.mtwabpin.kolahal.usage.UsageLevels.Companion.Bucket
import com.mtwabpin.kolahal.usage.UsageLevels.Companion.DAY_MS
import com.mtwabpin.kolahal.usage.UsageLevels.Companion.Stat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Android's weeks, months and years as `usage-summary` reads them: real edges, the current one apart, no empty ones. */
class UsageLevelsTest {
    private val hour = 3_600_000L
    private val own = "com.mtwabpin.kolahal"

    /** A bucket of [days] days from day [from], with each app's hours a day. */
    private fun bucket(from: Int, days: Int, vararg perDay: Pair<String, Double>, unlocks: Int? = null, current: Boolean = false) =
        Bucket(from * DAY_MS, (from + days) * DAY_MS, perDay.associate { (pkg, h) -> pkg to (h * hour * days).toLong() }, unlocks, current)

    /** Stats group by their start; empty or short buckets are left out; the newest start is current. */
    @Test fun statsBecomeBuckets() {
        val stats = listOf(
            Stat("yt", 0, 7 * DAY_MS, 14 * hour), Stat(own, 0, 7 * DAY_MS, 5 * hour), Stat("wa", 0, 7 * DAY_MS, 0),
            // A week with nothing in it: the phone was off.
            Stat("yt", 7 * DAY_MS, 14 * DAY_MS, 0),
            Stat("yt", 14 * DAY_MS, 17 * DAY_MS, 6 * hour),
        )
        val b = UsageLevels.buckets(stats, mapOf(0L to 70))
        assertEquals(listOf(0L, 14 * DAY_MS), b.map { it.start })
        // Kolahal counts too: an app's use is its use.
        assertEquals(mapOf("yt" to 14 * hour, own to 5 * hour), b[0].apps)
        assertEquals(70, b[0].unlocks)
        assertEquals(listOf(false, true), b.map { it.current })
        assertEquals(19 * hour / 7, b[0].perDay)
        // A current bucket only hours old is left out: it would say little.
        assertTrue(UsageLevels.buckets(listOf(Stat("yt", 0, 3 * hour, hour)), emptyMap()).isEmpty())
        // One that is over may be shorter than a day: Android ends it at its last event.
        val day = UsageLevels.buckets(listOf(Stat("yt", 0, 20 * hour, hour), Stat("yt", DAY_MS, DAY_MS + 3 * hour, hour)), emptyMap())
        assertEquals(listOf(0L), day.map { it.start })
    }

    /** Averages count the buckets that are over, and the current one once it has run a week. */
    @Test fun averagesLeaveOutAYoungCurrentBucket() {
        val weeks = listOf(bucket(0, 7, "yt" to 4.0, unlocks = 700), bucket(7, 7, "yt" to 2.0, unlocks = 0), bucket(14, 2, "yt" to 10.0, current = true))
        val counted = UsageLevels.counted(weeks)
        assertEquals(2, counted.size)
        assertEquals(3 * hour, UsageLevels.perDay(counted))
        assertEquals(50, UsageLevels.unlocksPerDay(counted))
        // A month: its current bucket has run 10 days, so it counts.
        val months = listOf(bucket(0, 30, "yt" to 3.0), bucket(30, 10, "yt" to 1.0, current = true))
        assertEquals(2, UsageLevels.counted(months).size)
        assertEquals(2.5 * hour, UsageLevels.perDay(UsageLevels.counted(months))!!.toDouble(), 1.0)
        assertNull(UsageLevels.perDay(emptyList()))
        assertNull(UsageLevels.unlocksPerDay(listOf(bucket(0, 7, "yt" to 1.0))))
    }

    /** The last bucket that is over against those before it; the current one plays no part. */
    @Test fun changeIsOfTheLastOneOver() {
        val months = listOf(bucket(0, 30, "yt" to 4.0), bucket(30, 30, "yt" to 4.0), bucket(60, 30, "yt" to 3.0), bucket(90, 10, "yt" to 9.0, current = true))
        assertEquals(-25L, UsageLevels.change(months))
        assertNull(UsageLevels.change(months.takeLast(2)))
    }

    /**
     * A year can't hold less than the months inside it: Android deleted a reinstalled app's history
     * from the year and not from the months. Each app gets the larger; a month outside is no part.
     */
    @Test fun theYearIsFilledFromItsMonths() {
        val year = Bucket(0, 200 * DAY_MS, mapOf("ff" to 50 * hour, "wa" to 20 * hour), null, current = true)
        val months = listOf(
            Bucket(-30 * DAY_MS, 10 * DAY_MS, mapOf("ff" to 500 * hour), null, current = false),
            Bucket(100 * DAY_MS, 130 * DAY_MS, mapOf("ff" to 60 * hour, "wa" to 5 * hour, "rd" to 3 * hour), null, current = false),
            Bucket(130 * DAY_MS, 200 * DAY_MS - hour / 2, mapOf("ff" to 40 * hour), null, current = true),
        )
        val filled = UsageLevels.fill(listOf(year), months).single()
        assertEquals(mapOf("ff" to 100 * hour, "wa" to 20 * hour, "rd" to 3 * hour), filled.apps)
        assertEquals(year.start to year.end, filled.start to filled.end)
        // With no month inside, the year stays as Android gives it.
        assertEquals(year, UsageLevels.fill(listOf(year), months.take(1)).single())
    }

    /** The top apps over all buckets; a year's apps left behind and new ones. */
    @Test fun topAppsAndShifts() {
        val last = bucket(0, 365, "insta" to 1.2, "yt" to 1.0, "maps" to 0.1)
        val now = bucket(365, 200, "insta" to 0.15, "yt" to 1.1, "duo" to 0.25, current = true)
        assertEquals(listOf("yt", "insta"), UsageLevels.top(listOf(last, now), 2).map { it.first })
        assertEquals(listOf("insta"), UsageLevels.leftBehind(last, now).map { it.pkg })
        // Maps had under 30 minutes a day: not "left behind", however little it has now.
        assertEquals(listOf("duo"), UsageLevels.newApps(last, now).map { it.pkg })
        assertEquals(15 * 60_000L, UsageLevels.newApps(last, now).single().now)
    }

    /** One app over the buckets: its time a day, its change, and when it was last used, a year knowing what its months know. */
    @Test fun oneAppOverTheBuckets() {
        val months = listOf(
            Bucket(0, 30 * DAY_MS, mapOf("yt" to 30 * hour, "wa" to 30 * hour), null, current = false, lastUsed = mapOf("yt" to 10L)),
            Bucket(30 * DAY_MS, 60 * DAY_MS, mapOf("yt" to 15 * hour), null, current = false, lastUsed = mapOf("yt" to 50L)),
            Bucket(60 * DAY_MS, 70 * DAY_MS, emptyMap(), null, current = true),
        )
        assertEquals(hour, UsageLevels.perDay(months.take(1), "yt"))
        // Half the time a day of the month before it.
        assertEquals(-50L, UsageLevels.change(months, "yt"))
        // An app with no time before has no change to say.
        assertNull(UsageLevels.change(months, "duo"))
        assertEquals(0L, months[2].total("yt"))
        assertEquals(50L, UsageLevels.lastUsed(months, "yt"))
        assertNull(UsageLevels.lastUsed(months, "duo"))
        val year = Bucket(0, 70 * DAY_MS, mapOf("yt" to 10 * hour), null, current = true, lastUsed = mapOf("yt" to 5L))
        val filled = UsageLevels.fill(listOf(year), months).single()
        assertEquals(45 * hour, filled.total("yt"))
        assertEquals(50L, filled.lastUsed["yt"])
    }
}
