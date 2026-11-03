package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.StyledText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchTest {
    private val rows = listOf(
        Fetch.Row("OS", "Android 16 · API 36"),
        Fetch.Row("Kernel", "6.6.66-android15-8-gd0c0ffee-ab12345678"),
        Fetch.Row("Kolahal", "0.4.3"),
    )

    private fun plain(lines: List<String>) = lines.map(StyledText::plain)

    @Test fun theIconSitsBesideTheFactsWhenTheyFit() {
        val lines = plain(Fetch.layout("Pixel 9", rows, cols = 40))
        // Title, rule, a row per fact, a gap, the colours.
        assertEquals(2 + rows.size + 2, lines.size)
        assertTrue(lines[0], lines[0].startsWith("▍ ━━━━━  kolahal@Pixel 9"))
        assertTrue(lines[2], lines[2].startsWith("› ▆      OS      Android 16"))
        // Past the icon, the facts keep their column.
        assertTrue(lines[3], lines[3].startsWith("         Kernel  6.6.66"))
        assertTrue(lines.all { it.length <= 40 })
        // Too long for its room: cut, and it says so.
        assertEquals(40, lines[3].length)
        assertTrue(lines[3].endsWith("…"))
    }

    @Test fun onANarrowScreenTheIconGoesAbove() {
        val lines = plain(Fetch.layout("Pixel 9", rows, cols = 30))
        assertEquals(listOf("▍ ━━━━━", "▍ ━━━  ", "› ▆    ", ""), lines.take(4))
        assertEquals("kolahal@Pixel 9", lines[4])
        assertTrue(lines.all { it.length <= 30 })
    }

    /** Your name (user.name) instead of kolahal, its rule as long as the title. */
    @Test fun theTitleHasYourName() {
        val lines = plain(Fetch.layout("Pixel 9", rows, cols = 30, user = "alex"))
        assertEquals("alex@Pixel 9", lines[4])
        assertEquals("─".repeat("alex@Pixel 9".length), lines[5])
    }

    /** A few columns (a huge text size on a narrow screen): the title is cut short to fit, never an error. */
    @Test fun aTinyWidthStillLaysOut() {
        val title = "kolahal@Pixel 9"
        for (cols in listOf(0, 1, 3, 5, 9, 15, 16)) {
            val lines = plain(Fetch.layout("Pixel 9", rows, cols))
            val expected = if (title.length <= cols) title else title.take((cols - 1).coerceAtLeast(0)) + "…"
            assertEquals("$cols columns", expected, lines[4])
        }
        assertEquals("kola…", plain(Fetch.layout("Pixel 9", rows, cols = 5))[4])
    }

    @Test fun theColoursAreTheThemesByRole() {
        val last = Fetch.layout("x", rows, cols = 40).last()
        assertEquals(8, StyledText.lines(last).single().spans.mapNotNull { it.colour }.distinct().size)
    }

    @Test fun uptimeInDaysHoursAndMinutes() {
        assertEquals("12m", Fetch.uptime(12 * 60_000L))
        assertEquals("4h 2m", Fetch.uptime((4 * 60 + 2) * 60_000L))
        assertEquals("3d 0h 5m", Fetch.uptime((3 * 24 * 60 + 5) * 60_000L))
    }
}
