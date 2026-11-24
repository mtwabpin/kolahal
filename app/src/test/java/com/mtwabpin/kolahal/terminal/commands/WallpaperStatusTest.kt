package com.mtwabpin.kolahal.terminal.commands

import com.mtwabpin.kolahal.commands.CommandCatalog
import com.mtwabpin.kolahal.commands.UsageLine
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.WallpaperChoice
import com.mtwabpin.kolahal.terminal.commands.WallpaperStatus.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What `wallpaper` says the wallpaper is ([WallpaperStatus]), and the words it takes. */
class WallpaperStatusTest {
    private fun lines(home: Kind, lock: Kind? = null, fromWallpaper: Boolean = false) =
        WallpaperStatus.lines(home, lock, shader = "dunes", theme = "material-you", themeFromWallpaper = fromWallpaper)

    /** Kolahal's live wallpaper comes before the theme's color, which comes before the user's own. */
    @Test
    fun theHomeScreensIsOneOfThree() {
        assertEquals(Kind.SHADER, WallpaperStatus.home(live = true, matched = true))
        assertEquals(Kind.THEME, WallpaperStatus.home(live = false, matched = true))
        assertEquals(Kind.YOURS, WallpaperStatus.home(live = false, matched = false))
    }

    /** The lock screen counts only when it has a wallpaper of its own; that one is never the theme's color. */
    @Test
    fun theLockScreensIsKnownWhenItHasItsOwn() {
        assertNull(WallpaperStatus.lock(own = false, live = false))
        assertNull(WallpaperStatus.lock(own = false, live = true))
        assertEquals(Kind.SHADER, WallpaperStatus.lock(own = true, live = true))
        assertEquals(Kind.YOURS, WallpaperStatus.lock(own = true, live = false))
    }

    /** One message for the home screen, and the shader by name: what it is first, more on the lines under it. */
    @Test
    fun oneLineSaysWhatHomeShows() {
        assertEquals(listOf("Home screen wallpaper: your own\nKolahal doesn't change it"), lines(Kind.YOURS))
        assertEquals(listOf("Home screen wallpaper: the theme's color\nIt follows the theme"), lines(Kind.THEME))
        val fromWallpaper = lines(Kind.THEME, fromWallpaper = true).single()
        assertTrue(fromWallpaper.startsWith("Home screen wallpaper: Kolahal's\n"))
        assertTrue(fromWallpaper.contains("doesn't follow the material-you theme"))
        assertEquals(listOf("Home screen wallpaper: shader dunes\nIt is Kolahal's live wallpaper"), lines(Kind.SHADER))
    }

    /** A second line for the lock screen, only when it shows something of another kind. */
    @Test
    fun theLockScreenHasALineWhenItDiffers() {
        assertEquals(1, lines(Kind.YOURS, lock = Kind.YOURS).size)
        assertEquals(1, lines(Kind.SHADER, lock = Kind.SHADER).size)
        assertEquals("Lock screen wallpaper: your own", lines(Kind.THEME, lock = Kind.YOURS).last())
        assertEquals("Lock screen wallpaper: your own", lines(Kind.SHADER, lock = Kind.YOURS).last())
        val lockOnly = lines(Kind.YOURS, lock = Kind.SHADER)
        assertEquals(2, lockOnly.size)
        assertEquals("Lock screen wallpaper: shader dunes\nIt is Kolahal's live wallpaper", lockOnly.last())
    }

    /**
     * `wallpaper` alone lists what can become the wallpaper, each as a line to tap: the word after
     * `wallpaper` names it. The theme's color isn't offered where the home screen has it already, and the
     * shader line puts its command in the prompt, as a name is still to come.
     */
    @Test
    fun theChoicesNameWhatBecomesTheWallpaper() {
        val all = WallpaperStatus.choices(Kind.YOURS)
        assertEquals(listOf("theme", "shader", "live"), all.map { it.word })
        assertEquals(listOf("wallpaper theme", null, "wallpaper live"), all.map { it.run })
        assertEquals(listOf(null, "wallpaper shader ", null), all.map { it.fill })
        assertTrue(all.first().note.contains("doesn't flash when you swipe home"))
        assertEquals(listOf("shader", "live"), WallpaperStatus.choices(Kind.THEME).map { it.word })
        assertEquals(listOf("theme", "shader", "live"), WallpaperStatus.choices(Kind.SHADER).map { it.word })
        // Each word is one the command takes.
        val words = UsageLine.choices(UsageLine.tokens(CommandCatalog.COMMAND_USAGES.getValue("wallpaper"))[1]).map { it.word }
        assertEquals(words, all.map { it.word })
    }

    /**
     * Settings' Wallpaper group has the same three, in the same order, by the same words. The one the home
     * screen has says "In use", the shader by its name; the theme's color otherwise says why one would want it.
     */
    @Test
    fun settingsRowsAreTheCommandsChoices() {
        val rows = WallpaperStatus.rows(Kind.YOURS, "pond")
        assertEquals(WallpaperStatus.choices(Kind.YOURS).map { it.word }, rows.map { it.word })
        assertEquals(listOf("Theme's color", "Shader", "Android's live wallpapers"), rows.map { it.title })
        assertEquals("So your wallpaper doesn't flash when you swipe home", rows[0].note)
        assertTrue(rows.none { it.note.startsWith("In use") })
        assertEquals("In use · it follows the theme", WallpaperStatus.rows(Kind.THEME, "pond")[0].note)
        assertEquals("In use: dunes · tap to pick another", WallpaperStatus.rows(Kind.SHADER, "dunes")[1].note)
        assertEquals(rows[2].note, WallpaperStatus.rows(Kind.SHADER, "dunes")[2].note)
    }

    /** Each way `wallpaper theme` can end has its words, the theme named where it takes its colors from the wallpaper; only two aren't failures. */
    @Test
    fun eachEndOfTheThemesColorHasItsWords() {
        val results = com.mtwabpin.kolahal.control.WallpaperMatch.Result.entries
        assertEquals(results.size, results.map { WallpaperStatus.said(it, "material-you") }.toSet().size)
        assertTrue(WallpaperStatus.said(com.mtwabpin.kolahal.control.WallpaperMatch.Result.FROM_WALLPAPER, "material-you").startsWith("The wallpaper can't follow material-you\n"))
        assertEquals(
            listOf(com.mtwabpin.kolahal.control.WallpaperMatch.Result.SET, com.mtwabpin.kolahal.control.WallpaperMatch.Result.ALREADY),
            results.filterNot(WallpaperStatus::failed),
        )
        assertTrue(WallpaperStatus.REPLACES.contains("can't restore the old one"))
        assertTrue(WallpaperStatus.REPLACES_SHORT.contains("can't bring the old one back"))
    }

    /** What `wallpaper` and `wallpaper theme` say fits a phone's line first, with more on the lines under it. */
    @Test
    fun theFirstLineFits() {
        val all = Kind.entries.flatMap { home ->
            listOf(null, Kind.YOURS, Kind.SHADER).flatMap { lock -> lines(home, lock) + lines(home, lock, fromWallpaper = true) }
        } + com.mtwabpin.kolahal.control.WallpaperMatch.Result.entries.map { WallpaperStatus.said(it, "material-you") } +
            WallpaperStatus.REPLACES
        for (text in all) assertTrue(text, text.lines().first().length <= 43)
    }

    /** `wallpaper` offers theme, shader and live, and `shader` takes a name. `match`, its old word, is gone. */
    @Test
    fun theUsageLineHasTheThreeWords() {
        val usage = CommandCatalog.COMMAND_USAGES.getValue("wallpaper")
        val choices = UsageLine.choices(UsageLine.tokens(usage)[1])
        assertEquals(listOf("theme", "shader", "live"), choices.map { it.word })
        assertFalse("match" in CommandCatalog.COMMANDS.first { it.name == "wallpaper" }.let { it.usage + it.help + it.args })
        assertEquals(listOf(false, true, false), choices.map { it.takesValue })
        val uses = CommandCatalog.COMMANDS.first { it.name == "wallpaper" }.uses.map { it.first }
        assertEquals(listOf("wallpaper", "wallpaper theme", "wallpaper shader <name>", "wallpaper live"), uses)
    }

    /** A one-off or a shader that works on text is never offered or set as a wallpaper; a scene is. */
    @Test
    fun aWallpaperIsNoOneOffAndNotOnText() {
        val content = "uniform shader content;\nhalf4 main(float2 p) { return content.eval(p); }"
        assertTrue(WallpaperChoice.suits(ShaderMeta.parse(content)))
        assertFalse(WallpaperChoice.suits(ShaderMeta.parse("// @once 6\n$content")))
        assertFalse(WallpaperChoice.suits(ShaderMeta.parse("// @text\n$content")))
    }
}
