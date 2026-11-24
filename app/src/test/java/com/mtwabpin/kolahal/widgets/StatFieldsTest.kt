package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** A stat's `points` (its sparkline) and `scene` (a shader's still as its ground), and the Poster look. */
class StatFieldsTest {
    private fun parse(raw: Map<String, Any?>) =
        WidgetParser.parse(mapOf("value" to "6,240") + raw, WidgetKind.STAT, { it }, { it }) as WidgetSpec.Stat

    private fun refused(raw: Map<String, Any?>): String? =
        assertThrows(IllegalArgumentException::class.java) { parse(raw) }.message

    @Test
    fun pointsAreNumbersAndTheLast60AreKept() {
        assertEquals(listOf(5900f, 6100f, 6240f), parse(mapOf("points" to listOf(5900, 6100.0, 6240))).points)
        assertEquals((41..100).map { it.toFloat() }, parse(mapOf("points" to (1..100).toList())).points)
        assertEquals(emptyList<Float>(), parse(emptyMap()).points)
        assertEquals(emptyList<Float>(), parse(mapOf("points" to null)).points)
        assertEquals(
            "widget: points must be a list of numbers; one of them is 'many'",
            refused(mapOf("points" to listOf(1, "many")))
        )
        assertEquals(
            "widget: points must be a list of numbers, not 'many'",
            refused(mapOf("points" to "many"))
        )
    }

    @Test
    fun aSceneIsTheNameOfAShaderThatIsAScene() {
        assertEquals("pond", parse(mapOf("scene" to "pond")).scene)
        assertEquals("rain", parse(mapOf("scene" to " Rain ")).scene)
        // A user's own shader is checked on the phone, when the card draws it.
        assertEquals("my-sky", parse(mapOf("scene" to "my-sky")).scene)
        assertNull(parse(emptyMap()).scene)
        assertEquals(
            "widget: scene crt is no scene: it works on the text or plays once; use a scene such as pond, rain, snowfall, dunes or mountains",
            refused(mapOf("scene" to "crt"))
        )
        assertEquals(
            "widget: scene hearts is no scene: it works on the text or plays once; use a scene such as pond, rain, snowfall, dunes or mountains",
            refused(mapOf("scene" to "hearts"))
        )
        assertEquals(
            "widget: scene must be a shader's name, such as pond, not 'clear night'",
            refused(mapOf("scene" to "clear night"))
        )
    }

    /** What `widget.show` checked comes back from the store as it was; a stored scene that is no scene reads as nothing. */
    @Test
    fun pointsAndSceneAreStored() {
        val spec = parse(mapOf("points" to listOf(1.0824, 1.0791, 1.0866), "scene" to "dunes"))
        assertEquals(spec, WidgetJson.decode(WidgetJson.encode(spec)))
        assertNull(WidgetJson.decode("""{"v":1,"kind":"stat","value":"7","scene":"crt"}"""))
        assertNull(WidgetJson.decode("""{"v":1,"kind":"stat","value":"7","points":[1,"x"]}"""))
        // A stat without them is stored as before: neither key is written.
        val plain = WidgetJson.encode(WidgetSpec.Stat(WidgetBase(), value = "7"))
        assertTrue(plain, "points" !in plain && "scene" !in plain)
    }

    /**
     * The bundled shaders `widget.show` refuses as a scene are those that can't be a wallpaper either:
     * they work on the text, or they play once. Read from their headers.
     */
    @Test
    fun theShadersThatAreNoSceneAreTheOnesOnTextOrPlayedOnce() {
        val notScenes = ShaderCatalog.BUNDLED.filter { name ->
            val meta = ShaderMeta.parse(sourceFile("src/main/assets/shaders/$name.agsl").readText())
            meta.text || meta.once != null
        }
        assertEquals(notScenes.sorted(), StatScenes.NOT_SCENES.sorted())
        StatScenes.WEATHER.forEach { assertTrue(it, it in ShaderCatalog.BUNDLED && StatScenes.problem(it) == null) }
    }

    /** Poster is offered for the kinds with one value to make large, and for a widget whose kind isn't known yet. */
    @Test
    fun posterIsForAStatACountdownAndAClock() {
        listOf(WidgetKind.STAT, WidgetKind.COUNTDOWN, WidgetKind.CLOCK, null).forEach {
            assertTrue("$it", WidgetLook.POSTER in WidgetLook.offeredFor(it))
        }
        WidgetKind.entries.filter { it != WidgetKind.STAT && it != WidgetKind.COUNTDOWN && it != WidgetKind.CLOCK }
            .forEach { assertTrue("$it", WidgetLook.POSTER !in WidgetLook.offeredFor(it)) }
        assertEquals(WidgetLook.PLAIN, WidgetLook.POSTER.drawnFor(WidgetKind.LIST))
        assertEquals(WidgetLook.POSTER, WidgetLook.POSTER.drawnFor(WidgetKind.CLOCK))
        assertEquals(WidgetLook.POSTER, WidgetLook.named("Poster"))
    }
}
