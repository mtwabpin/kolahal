package com.mtwabpin.kolahal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The file a theme made in the theme builder is kept in, and the hex codes the builder takes. */
class CustomThemesTest {
    private val colors =
        CustomThemes.ROLES.mapIndexed { i, role -> role to (0xFF000000.toInt() or (i * 0x151515)) }
            .toMap()

    @Test
    fun aThemeRoundTripsThroughItsFile() {
        val text = CustomThemes.encode(colors)
        assertTrue(text, text.contains("\"background\": \"#000000\""))
        assertEquals(colors, CustomThemes.decode(text))
    }

    @Test
    fun aFileMissingAColorIsNotATheme() {
        val text = CustomThemes.encode(colors).lines().filterNot { it.contains("\"error\"") }
            .joinToString("\n")
        // The color really went: a change in how themes are written can't make this a plain round trip.
        assertTrue(text, "\"error\"" !in text && "\"background\"" in text)
        assertNull(CustomThemes.decode(text))
        assertNull(CustomThemes.decode("not json"))
    }

    /** Written by hand: any spacing or case, extra keys ignored. */
    @Test
    fun aHandWrittenFileIsRead() {
        val text = "{" + colors.entries.joinToString(",") { (role, c) ->
            "\"$role\":\"${
                CustomThemes.hex(c).lowercase()
            }\""
        } + ", \"note\": \"#FFFFFF\"}"
        assertEquals(colors, CustomThemes.decode(text))
    }

    @Test
    fun aLightVersionTravelsInTheSameFile() {
        val both = colors + colors.mapKeys { CustomThemes.LIGHT + it.key }
            .mapValues { it.value or 0x00F0F0F0 }
        val text = CustomThemes.encode(both)
        assertTrue(text, text.contains("\"light-background\""))
        assertEquals(both, CustomThemes.decode(text))
        assertEquals(colors.mapValues { it.value or 0x00F0F0F0 }, CustomThemes.light(both))
    }

    /** Made before themes had a light version, or with only part of one: the same in both modes. */
    @Test
    fun noOrHalfALightVersionIsNone() {
        assertNull(CustomThemes.light(colors))
        val half = colors + mapOf(CustomThemes.LIGHT + "background" to 0xFFFFFFFF.toInt())
        val halfText =
            CustomThemes.encode(colors).replace("\n}", ",\n  \"light-background\": \"#FFFFFF\"\n}")
        assertTrue(halfText, "light-background" in halfText)
        assertEquals(colors, CustomThemes.decode(halfText))
        assertNull(CustomThemes.light(half))
        assertFalse(CustomThemes.isKey("light-nope"))
        assertTrue(CustomThemes.isKey("light-background"))
    }

    /** Made before good and warn: they come from personal and work, in both versions. */
    @Test
    fun aThemeFromBeforeGoodAndWarnGetsThem() {
        val old = colors - "good" - "warn"
        val read = CustomThemes.decode(
            CustomThemes.encode(colors).lines().filterNot { "\"good\"" in it || "\"warn\"" in it }
                .joinToString("\n")
        )!!
        assertEquals(old.getValue("personal"), read.getValue("good"))
        assertEquals(old.getValue("work"), read.getValue("warn"))
        assertEquals(colors.keys, CustomThemes.complete(old)!!.keys)
    }

    /** A file to share starts with the theme's name; its colors read the same. */
    @Test
    fun aSharedFileCarriesItsName() {
        val text = CustomThemes.encode(colors, "dusk")
        assertTrue(text, text.startsWith("{\n  \"name\": \"dusk\",\n  \"background\""))
        assertEquals(colors, CustomThemes.decode(text))
    }

    /** An imported theme's name: tidied into a theme name, and never one a theme already has. */
    @Test
    fun anImportedNameIsTidiedAndFree() {
        assertEquals("my-dusk-2", CustomThemes.tidy(" My Dusk_2 "))
        assertEquals("dusk-1", CustomThemes.tidy("dusk (1)"))
        assertEquals("", CustomThemes.tidy("—"))
        assertEquals("dusk", CustomThemes.freeName("dusk", setOf("green")))
        assertEquals("dusk-3", CustomThemes.freeName("dusk", setOf("dusk", "dusk-2")))
        assertEquals("green-2", CustomThemes.freeName("green", Keys.BUILT_IN_THEMES.toSet()))
        val long = "a".repeat(24)
        assertEquals("a".repeat(22) + "-2", CustomThemes.freeName(long, setOf(long)))
    }

    @Test
    fun hexCodes() {
        assertEquals("#0B0D0B", CustomThemes.hex(0xFF0B0D0B.toInt()))
        assertEquals(0xFF8FD18A.toInt(), CustomThemes.parseHex("#8fd18a"))
        assertEquals(0xFF8FD18A.toInt(), CustomThemes.parseHex(" 8FD18A "))
        for (bad in listOf("#8FD18", "#8FD18A00", "#GGGGGG", "", "#")) assertNull(
            bad,
            CustomThemes.parseHex(bad)
        )
    }

    @Test
    fun names() {
        for (good in listOf("my-theme", "sepia2", "a")) assertTrue(
            good,
            CustomThemes.NAME.matches(good)
        )
        for (bad in listOf("My Theme", "-dash", "a".repeat(25), "dots.no", "")) assertFalse(
            bad,
            CustomThemes.NAME.matches(bad)
        )
    }

    /** A theme saved as `nord` before Kolahal had a Nord of its own gets a free name, and so does each such theme. */
    @Test
    fun aSavedThemeWithABuiltInNameGetsAFreeOne() {
        assertEquals(emptyMap<String, String>(), CustomThemes.movedNames(setOf("dusk", "nord-2")))
        assertEquals(mapOf("nord" to "nord-2"), CustomThemes.movedNames(setOf("nord", "dusk")))
        assertEquals(mapOf("nord" to "nord-3"), CustomThemes.movedNames(setOf("nord", "nord-2")))
        assertEquals(
            mapOf("material-you" to "material-you-2", "nord" to "nord-2"),
            CustomThemes.movedNames(setOf("material-you", "nord")),
        )
        assertEquals(
            mapOf("rose-pine" to "rose-pine-2"),
            CustomThemes.movedNames(setOf("rose-pine", "dusk"))
        )
    }

    /** At start: the file moves with the name, the theme keeps its colors, and the built-in name is free. */
    @Test
    fun aSavedThemeWithABuiltInNameMovesAtStart() {
        val dir = java.nio.file.Files.createTempDirectory("themes").toFile()
        try {
            val dusk = colors.mapValues { it.value or 0x00010101 }
            File(dir, "nord.json").writeText(CustomThemes.encode(colors))
            File(dir, "dusk.json").writeText(CustomThemes.encode(dusk))
            File(dir, "broken.json").writeText("{ \"background\": \"#000000\" }")

            assertEquals(mapOf("nord" to "nord-2"), CustomThemes.read(dir))
            assertEquals(listOf("dusk", "nord-2"), CustomThemes.names)
            assertEquals(colors, CustomThemes.colors("nord-2"))
            assertEquals(dusk, CustomThemes.colors("dusk"))
            assertNull(CustomThemes.colors("nord"))
            assertEquals(listOf("broken.json", "dusk.json", "nord-2.json"), dir.list()!!.sorted())
            assertTrue("nord-2" in Keys.THEME.choices)

            // The next start finds nothing to move.
            assertEquals(emptyMap<String, String>(), CustomThemes.read(dir))
            assertEquals(listOf("dusk", "nord-2"), CustomThemes.names)
        } finally {
            dir.deleteRecursively()
            // Other tests start with no saved themes.
            CustomThemes.read(dir)
        }
        assertEquals(emptyList<String>(), CustomThemes.names)
    }
}
