package com.mtwabpin.kolahal.terminal.commands

import com.mtwabpin.kolahal.apps.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How `apps` caps a profile's grid, which apps it shows first, and the lines that count them ([AppListing]). */
class AppListingTest {
    /** Twenty apps, in the order a grid has them: by name. */
    private val names = ('a'..'t').map { "app $it" }

    private fun shown(cells: Int, pins: Map<String, Int> = emptyMap(), use: Map<String, Double> = emptyMap()) =
        AppListing.firstShown(names, cells, { pins[it] }, { use[it] ?: 0.0 })?.map { names[it] }

    /** About nine rows of chips in all, so the command's line stays on screen above them. */
    @Test
    fun moreProfilesGetFewerRowsEach() {
        assertEquals(listOf(6, 4, 3, 2, 2), (1..5).map(AppListing::rows))
    }

    /** A group that fits in its cells has no cap; one more than fits shows one cell less and "+2 more", never "+1 more". */
    @Test
    fun aGroupThatFitsHasNoCap() {
        assertNull(shown(20))
        assertNull(shown(25))
        val capped = shown(19)!!
        assertEquals(18, capped.size)
        assertEquals(2, names.size - capped.size)
    }

    /** Pinned apps first, in pin order, then the most used, then the first of the rest; shown in the grid's order. */
    @Test
    fun pinnedAndMostUsedAppsComeFirst() {
        val pins = mapOf("app t" to 0, "app q" to 1)
        val use = mapOf("app m" to 3.0, "app c" to 0.5, "app s" to 9.0, "app h" to 0.1)
        assertEquals(listOf("app c", "app m", "app q", "app s", "app t"), shown(6, pins, use))
        // A pinned app never used comes before the most used one.
        assertEquals(listOf("app t"), shown(2, pins, use))
        assertEquals(listOf("app s", "app t"), shown(3, mapOf("app t" to 0), use))
        // With fewer used apps than cells, the first others by name fill them.
        assertEquals(listOf("app a", "app b", "app c", "app d", "app k"), shown(6, use = mapOf("app k" to 1.0)))
        assertEquals(names.take(4), shown(5))
    }

    /** The divider says the profile and how many; one profile's line says how many, and what was asked for. */
    @Test
    fun theCountsSayHowMany() {
        assertEquals("── personal · 40 ──", AppListing.divider(ProfileKind.PERSONAL, 40))
        assertEquals("── work · 18 ──", AppListing.divider(ProfileKind.WORK, 18))
        assertEquals("40 apps", AppListing.count(40, null, hidden = false))
        assertEquals("1 app", AppListing.count(1, null, hidden = false))
        assertEquals("18 work apps", AppListing.count(18, ProfileKind.WORK, hidden = false))
        assertEquals("3 hidden apps", AppListing.count(3, null, hidden = true))
        assertEquals("1 hidden private app", AppListing.count(1, ProfileKind.PRIVATE, hidden = true))
    }
}
