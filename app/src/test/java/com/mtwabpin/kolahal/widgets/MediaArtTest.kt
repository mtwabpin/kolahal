package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.script.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Album art as background ([MediaArt]): when the track's cover is the Now playing card's ground, and
 * how the choice is kept per placed card, after its shape, color and hidden parts, in the saved list
 * and in backups.
 */
class MediaArtTest {
    private val looks = listOf(WidgetLook.PLAIN, WidgetLook.TONAL, WidgetLook.SPLIT, WidgetLook.SHAPE)

    private fun media(column: Int = 0, art: Boolean = false, hidden: Set<String> = emptySet()) =
        PlacedWidget(
            null, WidgetSource.builtin(WidgetSource.MEDIA), 0L, 88, 0,
            span = 6, column = column, topDp = 0, hidden = hidden, artBackground = art
        )

    /** On and with a cover: the cover is the ground in every look and size, but a small Split card, which is play or pause alone. */
    @Test
    fun theCoverIsTheGroundOnlyWhenOnAndThere() {
        for (look in looks) for (box in WidgetSizes.Box.entries) {
            val where = "${look.id} ${box.name}"
            val small = look == WidgetLook.SPLIT && box == WidgetSizes.Box.SMALL
            assertEquals(where, !small, MediaArt.ground(on = true, cover = true, look, box))
            // No cover, or the choice off: the card as it always was.
            assertFalse(where, MediaArt.ground(on = true, cover = false, look, box))
            assertFalse(where, MediaArt.ground(on = false, cover = true, look, box))
        }
    }

    /** Only the built-in Now playing card has the switch. */
    @Test
    fun onlyNowPlayingOffersIt() {
        assertTrue(MediaArt.offered(WidgetSource.MEDIA))
        listOf(WidgetSource.BATTERY, WidgetSource.AGENDA, WidgetSource.TODOS, null)
            .forEach { assertFalse("$it", MediaArt.offered(it)) }
    }

    /** It is the line's last field, after empty ones for what isn't set. Off: the line as it was. */
    @Test
    fun theSavedListKeepsIt() {
        val all = listOf(
            media(art = true),
            media(column = 6, art = true, hidden = setOf(WidgetParts.NAME)),
            media(column = 0).copy(topDp = 96),
        )
        val text = WidgetCodec.encode(all)
        assertEquals(
            listOf(
                "-|builtin:media|0|88|0|panel|6|plain|0|0||||art",
                "-|builtin:media|0|88|0|panel|6|plain|6|0|||name|art",
                "-|builtin:media|0|88|0|panel|6|plain|0|96",
            ),
            text.lines()
        )
        assertEquals(all, WidgetCodec.decode(text))
        assertFalse(WidgetCodec.start(text).changed)
    }

    /** An older line has it off; so has a word this version doesn't know there, and an app's widget. */
    @Test
    fun oldOddAndAppLinesHaveItOff() {
        assertFalse(WidgetCodec.decode("-|builtin:media|0|88|0|panel|6|plain|0|0|||name").single().artBackground)
        assertFalse(WidgetCodec.decode("-|builtin:media|0|88|0|panel|6|plain|0|0||||blur").single().artBackground)
        assertFalse(WidgetCodec.decode("5|com.a/.W|0|88|0|panel|6|plain|0|0||||art").single().artBackground)
    }

    /** A backup carries it only when on, and reads it back, also through JSON. */
    @Test
    fun backupsCarryIt() {
        val all = listOf(media(art = true), media(column = 6))
        val exported = WidgetCodec.export(all)
        assertEquals(true, exported[0][MediaArt.BACKUP])
        assertFalse(exported[1].containsKey(MediaArt.BACKUP))
        assertEquals(all, WidgetCodec.fromExport(exported))
        @Suppress("UNCHECKED_CAST")
        val read = Json.parse(Json.stringify(exported)) as List<Map<String, Any?>>
        assertEquals(all, WidgetCodec.fromExport(read))
        // Anything but true, or on an app's widget, is off.
        assertEquals(
            listOf(false, false),
            WidgetCodec.fromExport(
                listOf(
                    mapOf("provider" to "builtin:media", MediaArt.BACKUP to 3),
                    mapOf("provider" to "com.a/.W", MediaArt.BACKUP to true),
                )
            ).map { it.artBackground }
        )
    }
}
