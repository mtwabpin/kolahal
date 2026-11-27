package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.ui.widgets.WidgetSettingsWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A card's scene ([CardScenes]): the names offered, which looks draw it, the user's choice against
 * the script's, album art against it, and how it is kept per placed card in the saved list and in backups.
 */
class CardScenesTest {
    private fun card(source: String, column: Int = 0, scene: String? = null) =
        PlacedWidget(null, source, 0L, 88, 0, span = 6, column = column, topDp = 0, scene = scene)

    /** The names offered are the scenes a stat may name, in the order Kolahal lists its shaders. */
    @Test
    fun theNamesAreTheStatsScenes() {
        assertEquals(ShaderCatalog.BUNDLED.filter(StatScenes::drawn), CardScenes.names)
        StatScenes.WEATHER.forEach { assertTrue(it, it in CardScenes.names) }
        StatScenes.NOT_SCENES.forEach { assertFalse(it, it in CardScenes.names) }
        // None is stored as a word no shader may have.
        assertFalse(CardScenes.NONE in ShaderCatalog.BUNDLED)
        assertEquals("Grove snow", CardScenes.label("grove-snow"))
    }

    /** The card's own first: None, or the script's own and then None. */
    @Test
    fun theChoicesStartWithTheCardsOwn() {
        assertEquals(listOf(null) + CardScenes.names, CardScenes.choices(null))
        assertEquals(listOf(null, CardScenes.NONE) + CardScenes.names, CardScenes.choices("rain"))
        assertEquals("None", CardScenes.choiceLabel(null, null))
        assertEquals("As the script sets", CardScenes.choiceLabel(null, "rain"))
        assertEquals("None", CardScenes.choiceLabel(CardScenes.NONE, "rain"))
        assertEquals("Pond", CardScenes.choiceLabel("pond", "rain"))
        assertEquals("As the script sets: Rain", CardScenes.summary(null, "rain"))
        assertEquals("None", CardScenes.summary(null, null))
    }

    /** The user's choice wins over the script's, None too; with none chosen the script's shows. */
    @Test
    fun theUsersChoiceOverridesTheScripts() {
        assertEquals("rain", CardScenes.shown(null, "rain"))
        assertEquals("pond", CardScenes.shown("pond", "rain"))
        assertNull(CardScenes.shown(CardScenes.NONE, "rain"))
        assertNull(CardScenes.shown(null, null))
        assertEquals("dunes", CardScenes.shown("dunes", null))
    }

    /** The looks whose card has a fill draw it; Split, Shape and Poster keep their own, and say why. */
    @Test
    fun whichLooksDrawIt() {
        val drawn = WidgetLook.entries.filter(CardScenes::drawnIn)
        assertEquals(
            listOf(WidgetLook.PLAIN, WidgetLook.TONAL, WidgetLook.BOLD, WidgetLook.NEXT, WidgetLook.STACK, WidgetLook.NOW),
            drawn
        )
        (WidgetLook.entries - drawn.toSet()).forEach { assertTrue(it.id, CardScenes.why(it)!!.startsWith("Not in ${it.label}")) }
    }

    /** On Now playing, the cover wins while there is one; the scene shows when there isn't. */
    @Test
    fun albumArtWinsWhileThereIsACover() {
        val wide = WidgetSizes.Box.WIDE
        assertFalse(CardScenes.overArt(MediaArt.ground(on = true, cover = true, WidgetLook.PLAIN, wide)))
        assertTrue(CardScenes.overArt(MediaArt.ground(on = true, cover = false, WidgetLook.PLAIN, wide)))
        assertTrue(CardScenes.overArt(MediaArt.ground(on = false, cover = true, WidgetLook.PLAIN, wide)))
        // The page says so only with both chosen.
        val line = WidgetSettingsWords.sceneLine("pond", null, WidgetLook.PLAIN, art = true)
        assertEquals("Pond\n${CardScenes.ART}", line)
        assertEquals("None", WidgetSettingsWords.sceneLine(null, null, WidgetLook.PLAIN, art = true))
        assertEquals("Pond\nNot in Split: its tiles keep their color", WidgetSettingsWords.sceneLine("pond", null, WidgetLook.SPLIT, art = false))
    }

    /** A stat keeps the stat's scrim, strong where its figures are; every other kind has the fuller one. */
    @Test
    fun denseKindsHaveTheFullerScrim() {
        assertFalse(CardScenes.full(WidgetKind.STAT))
        (WidgetKind.entries - WidgetKind.STAT).forEach { assertTrue(it.id, CardScenes.full(it)) }
    }

    /** It is the line's field after album art: a name, or "none". Unset: the line as it was. */
    @Test
    fun theSavedListKeepsIt() {
        val all = listOf(
            card("builtin:todos", scene = "pond"),
            card("script:weather", column = 6, scene = CardScenes.NONE),
            card("builtin:battery").copy(topDp = 96),
        )
        val text = WidgetCodec.encode(all)
        assertEquals(
            listOf(
                "-|builtin:todos|0|88|0|panel|6|plain|0|0|||||pond",
                "-|script:weather|0|88|0|panel|6|plain|6|0|||||none",
                "-|builtin:battery|0|88|0|panel|6|plain|0|96",
            ),
            text.lines()
        )
        assertEquals(all, WidgetCodec.decode(text))
        assertFalse(WidgetCodec.start(text).changed)
        // A word that is no scene, and an app's widget, have the card's own.
        assertNull(WidgetCodec.decode("-|builtin:todos|0|88|0|panel|6|plain|0|0|||||crt").single().scene)
        assertNull(WidgetCodec.decode("5|com.a/.W|0|88|0|panel|6|plain|0|0|||||pond").single().scene)
    }

    /** A backup carries it only when set, and reads it back, also through JSON. */
    @Test
    fun backupsCarryIt() {
        val all = listOf(card("builtin:agenda", scene = "dunes"), card("script:weather", column = 6, scene = CardScenes.NONE), card("builtin:notes"))
        val exported = WidgetCodec.export(all)
        assertEquals(listOf("dunes", "none", null), exported.map { it[CardScenes.BACKUP] })
        assertFalse(exported[2].containsKey(CardScenes.BACKUP))
        @Suppress("UNCHECKED_CAST")
        val read = Json.parse(Json.stringify(exported)) as List<Map<String, Any?>>
        assertEquals(WidgetCodec.fromExport(exported), WidgetCodec.fromExport(read))
        assertEquals(listOf("dunes", "none", null), WidgetCodec.fromExport(read).map { it.scene })
        assertEquals(
            listOf(null, null),
            WidgetCodec.fromExport(
                listOf(
                    mapOf("provider" to "builtin:todos", CardScenes.BACKUP to "hearts"),
                    mapOf("provider" to "com.a/.W", CardScenes.BACKUP to "pond"),
                )
            ).map { it.scene }
        )
    }
}
