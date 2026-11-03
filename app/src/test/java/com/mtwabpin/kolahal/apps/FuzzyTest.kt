package com.mtwabpin.kolahal.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyTest {
    @Test fun accentsDontMatter() {
        assertEquals("cafe", Fuzzy.fold("Café"))
        assertEquals("ubersetzer", Fuzzy.fold("Übersetzer"))
    }

    @Test fun slipsFindTheApp() {
        assertTrue(Fuzzy.slip("mpas", listOf("maps")))
        assertTrue(Fuzzy.slip("gmial", listOf("gmail")))
        assertTrue(Fuzzy.slip("chrone", listOf("chrome")))
        assertTrue(Fuzzy.slip("yuotube", listOf("youtube")))
        // Of a word's start, as typing goes.
        assertTrue(Fuzzy.slip("clacu", listOf("calculator")))
        assertTrue(Fuzzy.slip("teelgram", listOf("telegram")))
        assertTrue(Fuzzy.slip("whtasapp", listOf("whatsapp")))
    }

    /** Two slips from eight letters on, whichever way they go. */
    @Test fun twoSlipsFromEightLetters() {
        assertTrue("two missing", Fuzzy.slip("calcltor", listOf("calculator")))
        assertTrue("two extra", Fuzzy.slip("caalculattor", listOf("calculator")))
        assertTrue("two wrong", Fuzzy.slip("cakculatoe", listOf("calculator")))
        assertTrue("one of each", Fuzzy.slip("calcxlatr", listOf("calculator")))
        // Of a word's start, as typing goes.
        assertTrue(Fuzzy.slip("settngs up", listOf("settings up")))
        // Below eight letters, still one.
        assertFalse(Fuzzy.slip("calcltr", listOf("calculator")))
        assertFalse(Fuzzy.slip("telgrm", listOf("telegram")))
        // Three is too many.
        assertFalse(Fuzzy.slip("clclatoor", listOf("calculator")))
    }

    @Test fun notEverythingIsASlip() {
        assertFalse(Fuzzy.slip("map", listOf("mop")))
        assertFalse(Fuzzy.slip("maps", listOf("mail")))
        assertFalse(Fuzzy.slip("notes", listOf("photos")))
    }

    @Test fun swapsCountOnce() {
        assertEquals(1, Fuzzy.distance("mpas", "maps"))
        assertEquals(1, Fuzzy.distance("maps", "map"))
        assertEquals(2, Fuzzy.distance("maps", "mail"))
    }

    /** A launch counts one, halving every week: last month's habit loses to today's. */
    @Test fun oldLaunchesFade() {
        val week = 7 * 86_400_000L
        assertEquals(25.0, AppPrefs.decayed(50.0, week), 1e-9)
        val oldFavourite = AppPrefs.decayed(50.0, 8 * week) + 1
        val daily = (0 until 30).sumOf { AppPrefs.decayed(1.0, it * 86_400_000L) }
        assertTrue("$oldFavourite vs $daily", daily > oldFavourite * 5)
    }

    /** The distance as a full table, as it was worked out before: the three-row version must agree. */
    private fun tableDistance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
            }
        }
        return d[a.length][b.length]
    }

    @Test fun distanceAgreesWithTheFullTable() {
        val r = kotlin.random.Random(7)
        repeat(5_000) {
            val a = String(CharArray(r.nextInt(0, 10)) { "abcde"[r.nextInt(5)] })
            val b = String(CharArray(r.nextInt(0, 10)) { "abcde"[r.nextInt(5)] })
            val n = r.nextInt(0, b.length + 1)
            assertEquals("$a / $b", tableDistance(a, b), Fuzzy.distance(a, b))
            assertEquals("$a / ${b.take(n)}", tableDistance(a, b.take(n)), Fuzzy.distance(a, b, n))
        }
    }

    /** One table for every length of the name finds the slips a table for each length found. */
    @Test fun slipAgreesWithATablePerLength() {
        val r = kotlin.random.Random(11)
        repeat(5_000) {
            val q = String(CharArray(r.nextInt(4, 10)) { "abcd"[r.nextInt(4)] })
            val name = String(CharArray(r.nextInt(0, 12)) { "abcd"[r.nextInt(4)] })
            val max = if (q.length >= 8) 2 else 1
            val before = (q.length - max..q.length + max).any { n -> n in 1..name.length && tableDistance(q, name.take(n)) <= max }
            assertEquals("$q / $name", before, Fuzzy.slip(q, listOf(name)))
        }
    }
}
