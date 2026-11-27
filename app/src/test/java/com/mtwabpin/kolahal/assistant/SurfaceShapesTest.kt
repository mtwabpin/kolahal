package com.mtwabpin.kolahal.assistant

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * An answer whose components hold each other in a circle, or nest too deep, is refused before the
 * renderer sees it: drawing it would recurse without end on the main thread.
 */
class SurfaceShapesTest {
    private fun create(id: String) = """{"version":"v0.9","createSurface":{"surfaceId":"$id","catalogId":"c","sendDataModel":true}}"""

    private fun update(id: String, vararg components: String) =
        """{"version":"v0.9","updateComponents":{"surfaceId":"$id","components":[${components.joinToString(",")}]}}"""

    private fun refused(shapes: SurfaceShapes, vararg messages: String): String {
        try {
            shapes.take(messages.toList())
        } catch (e: IllegalArgumentException) {
            return e.message.orEmpty()
        }
        fail("taken: ${messages.toList()}")
        return ""
    }

    @Test
    fun theExampleInThePromptIsTaken() {
        val example = org.json.JSONArray(Prompts.example("c"))
        SurfaceShapes().take((0 until example.length()).map { example.get(it).toString() })
    }

    @Test
    fun childrenAreReadFromEveryPlaceAComponentNamesThem() {
        fun children(json: String) = SurfaceShapes.children(JSONObject(json)).sorted()
        assertEquals(listOf("a"), children("""{"id":"c","component":"Card","child":"a"}"""))
        assertEquals(listOf("a", "b"), children("""{"id":"c","component":"Column","children":["a","b"]}"""))
        assertEquals(listOf("row"), children("""{"id":"c","component":"List","children":{"componentId":"row","path":"/items"}}"""))
        assertEquals(listOf("t1", "t2"), children("""{"id":"c","component":"Tabs","tabs":[{"title":"One","child":"t1"},{"title":{"path":"/x"},"child":"t2"}]}"""))
        assertEquals(listOf("open", "sheet"), children("""{"id":"c","component":"Modal","trigger":"open","content":"sheet"}"""))
        // An action's context is the model's data, and a text is only text.
        assertEquals(listOf("label"), children("""{"id":"c","component":"Button","child":"label","action":{"event":{"name":"go","context":{"child":"c"}}}}"""))
        assertEquals(emptyList<String>(), children("""{"id":"c","component":"Text","text":"child"}"""))
    }

    @Test
    fun aComponentThatHoldsItselfIsRefused() {
        val message = refused(SurfaceShapes(), create("s1"), update("s1", """{"id":"root","component":"Card","child":"root"}"""))
        assertTrue(message, "root" in message)
    }

    @Test
    fun aCircleThroughOthersIsRefused() {
        refused(
            SurfaceShapes(), create("s1"),
            update(
                "s1",
                """{"id":"root","component":"Column","children":["a","title"]}""",
                """{"id":"title","component":"Text","text":"Hi"}""",
                """{"id":"a","component":"Card","child":"b"}""",
                """{"id":"b","component":"List","children":{"componentId":"root","path":"/items"}}""",
            ),
        )
    }

    /** A circle made by a later answer, changing a component drawn before: refused, and the earlier shape stays. */
    @Test
    fun aCircleMadeByALaterAnswerIsRefused() {
        val shapes = SurfaceShapes()
        shapes.take(listOf(create("s1"), update("s1", """{"id":"root","component":"Card","child":"a"}""", """{"id":"a","component":"Text","text":"x"}""")))
        refused(shapes, update("s1", """{"id":"a","component":"Card","child":"root"}"""))
        // The refused answer left nothing: an ordinary change still goes.
        shapes.take(listOf(update("s1", """{"id":"a","component":"Text","text":"y"}""")))
        // A surface made again starts empty: the same ids may be used another way.
        shapes.take(listOf(create("s1"), update("s1", """{"id":"a","component":"Card","child":"root"}""", """{"id":"root","component":"Text","text":"z"}""")))
    }

    @Test
    fun aDeepNestIsRefusedAndAnOrdinaryOneTaken() {
        fun nest(depth: Int) = update(
            "s1",
            *(1 until depth).map { """{"id":"c$it","component":"Card","child":"c${it + 1}"}""" }.toTypedArray(),
            """{"id":"c$depth","component":"Text","text":"deep"}""",
        )
        SurfaceShapes().take(listOf(create("s1"), nest(SurfaceShapes.MAX_DEPTH)))
        val message = refused(SurfaceShapes(), create("s1"), nest(SurfaceShapes.MAX_DEPTH + 1))
        assertTrue(message, "deep" in message)
        // Far deeper: the check itself doesn't recurse, so it says so rather than overflowing.
        refused(SurfaceShapes(), create("s1"), nest(20_000))
    }

    /** Surfaces are apart: the same id on two surfaces is two components. */
    @Test
    fun surfacesAreApart() {
        val shapes = SurfaceShapes()
        shapes.take(
            listOf(
                create("s1"), update("s1", """{"id":"root","component":"Card","child":"x"}"""),
                create("s2"), update("s2", """{"id":"x","component":"Card","child":"y"}"""),
            )
        )
        // On s1, x doesn't exist yet: no edge, nothing refused. On s2, root holds nothing.
        shapes.take(listOf(update("s2", """{"id":"y","component":"Card","child":"z"}""")))
    }
}
