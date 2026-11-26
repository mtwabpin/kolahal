package com.mtwabpin.kolahal.assistant

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The system prompt's parts, and taking the A2UI messages out of an answer. */
class PromptsTest {
    private val catalog = "https://example.org/catalog.json"
    private val schema = "{\"SCHEMA\":true}"

    @Test
    fun theRichPromptHasTheFormatTheDesignTheExampleAndTheSchema() {
        val p = Prompts.system(rich = true, catalogId = catalog, schema = schema, custom = "")
        assertTrue(p.startsWith("You draw your answers on a phone with the A2UI protocol"))
        for (part in listOf("createSurface", "Design:", "Hierarchy", "Card", "Never more than three columns", "caption", "Icons only from the catalog", "Dividers sparingly", "Nothing interactive", "\"s1\"")) {
            assertTrue(part, part in p)
        }
        assertTrue(p.trimEnd().endsWith(schema))
        assertTrue(catalog in p)
        // The example is real A2UI: messages with the catalog, a root, no picture and nothing to type in.
        val example = JSONArray(Prompts.example(catalog))
        val messages = (0 until example.length()).map { example.getJSONObject(it) }
        assertEquals(catalog, messages[0].getJSONObject("createSurface").getString("catalogId"))
        val components = messages[1].getJSONObject("updateComponents").getJSONArray("components")
        val types = (0 until components.length()).map { components.getJSONObject(it).getString("component") }
        assertTrue("root" in (0 until components.length()).map { components.getJSONObject(it).getString("id") })
        assertTrue(types.containsAll(listOf("Card", "Row", "Column", "Text", "Icon", "Divider")))
        assertTrue(types.none { it in listOf("Image", "Video", "AudioPlayer", "TextField", "Button") })
    }

    @Test
    fun thePlainPromptAsksForText() {
        val p = Prompts.system(rich = false, catalogId = catalog, schema = schema, custom = "")
        assertFalse("A2UI" in p)
        assertFalse(schema in p)
        assertTrue("plain text" in p)
    }

    /** The user's own instructions come last, after the part that stays the same, so that part is cached. */
    @Test
    fun customInstructionsComeLast() {
        for (rich in listOf(true, false)) {
            val base = Prompts.system(rich, catalog, schema, "  ")
            val own = Prompts.system(rich, catalog, schema, " Answer in Spanish ")
            assertTrue(own.startsWith(base))
            assertTrue(own.endsWith("Answer in Spanish"))
        }
    }

    /** Nothing from the phone is in the prompt: no date, no names, no apps. */
    @Test
    fun thePromptIsTheSameEveryTime() {
        assertEquals(Prompts.system(true, catalog, schema, "x"), Prompts.system(true, catalog, schema, "x"))
        assertEquals(Prompts.system(false, catalog, schema, ""), Prompts.system(false, catalog, schema, ""))
    }

    @Test
    fun theMessagesComeOutOfAFencedAnswer() {
        val list = """[{"version":"v0.9","createSurface":{"surfaceId":"s1"}},{"version":"v0.9","deleteSurface":{"surfaceId":"s0"}}]"""
        for (answer in listOf(list, "```json\n$list\n```", "Here you go:\n```\n$list\n```\nDone.", "  $list  ")) {
            assertEquals(answer, 2, Prompts.messages(answer).size)
        }
        // An object holding them, and a single message.
        assertEquals(2, Prompts.messages("{\"messages\":$list}").size)
        assertEquals(1, Prompts.messages("""{"version":"v0.9","deleteSurface":{"surfaceId":"s1"}}""").size)
        for (bad in listOf("No JSON here.", "[not json", "\"just a string\"")) {
            try {
                Prompts.messages(bad)
                fail(bad)
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test
    fun anAnswerSaysWhichSurfacesItMakesAndChanges() {
        val messages = Prompts.messages(
            """[{"createSurface":{"surfaceId":"s2"}},{"updateComponents":{"surfaceId":"s2"}},{"updateDataModel":{"surfaceId":"s1"}},{"updateComponents":{"surfaceId":"s1"}}]"""
        )
        assertEquals(listOf("s2") to listOf("s1"), Prompts.surfaces(messages))
    }
}
