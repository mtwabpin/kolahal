package com.mtwabpin.kolahal.assistant

import com.mtwabpin.kolahal.data.Keys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Services.ALL] is the one list of services: each one complete and its own, and everything that offers
 * a service or a model reads it. A key goes only to its own service's host, in a header.
 */
class ServicesTest {
    private val key = "test-key-0000"
    private val turns = listOf(Turn(Role.USER, "Hello"))

    @Test
    fun everyServiceIsCompleteAndItsOwn() {
        assertTrue(Services.ALL.isNotEmpty())
        for (s in Services.ALL) {
            assertTrue("${s.id} has no models", s.models.isNotEmpty())
            assertTrue("${s.id}: its default ${s.default} isn't one of its models", s.models.any { it.id == s.default })
            assertTrue("${s.id} has no host", s.host.isNotBlank() && '/' !in s.host)
            assertTrue("${s.id} has no secret name", s.secretName.isNotBlank())
            assertTrue("${s.id} has no name", s.name.isNotBlank() && s.company.isNotBlank())
            assertEquals("${s.id} offers a model twice", s.models.size, s.models.map { it.id }.toSet().size)
        }
        for (field in listOf<(Service) -> String>({ it.id }, { it.host }, { it.secretName }, { it.name })) {
            val values = Services.ALL.map(field)
            assertEquals("two services share $values", values.size, values.toSet().size)
        }
        // No model is offered by two services: the model says which service it is.
        val models = Services.ALL.flatMap { s -> s.models.map { it.id } }
        assertEquals(models.size, models.toSet().size)
    }

    /** The setting's choices are the list's ids, the first is its default, and its notes name every service. */
    @Test
    fun theServiceSettingReadsTheList() {
        assertEquals(Services.ALL.map { it.id }, Keys.ASSISTANT_SERVICE.choices)
        assertEquals(Services.ALL.first().id, Keys.ASSISTANT_SERVICE.default)
        assertEquals(Services.ALL.map { it.id }.toSet(), Keys.ASSISTANT_SERVICE.notes.keys)
        assertEquals(Services.ALL.first(), Services.byId("nobody"))
        for (s in Services.ALL) assertEquals(s, Services.byId(s.id))
    }

    /** Blank or another service's model gives the default; a name typed by hand is used as it is. */
    @Test
    fun theModelFollowsTheService() {
        for (s in Services.ALL) {
            assertEquals(s.default, s.modelFor(""))
            for (other in Services.ALL - s) assertEquals(s.default, s.modelFor(other.default))
            s.models.forEach { assertEquals(it.id, s.modelFor(it.id)) }
            assertEquals("some-newer-model-9", s.modelFor("some-newer-model-9"))
            assertEquals("some-newer-model-9", s.modelName("some-newer-model-9"))
        }
        // The setting takes a typed name, and nothing that could change a request's address.
        assertEquals("gemini-9.9-pro", Keys.ASSISTANT_MODEL.parse(" gemini-9.9-pro "))
        assertEquals("", Keys.ASSISTANT_MODEL.parse(""))
        assertEquals(null, Keys.ASSISTANT_MODEL.parse("a/b"))
        assertEquals(null, Keys.ASSISTANT_MODEL.parse("a?key=b"))
    }

    /** Each request goes over HTTPS to its service's host, with the key in a header and nowhere else. */
    @Test
    fun aKeyGoesOnlyToItsOwnHostInAHeader() {
        for (s in Services.ALL) for (model in s.models.map { it.id } + "typed-model-1") {
            for (json in listOf(true, false)) {
                val call = s.call(key, model, "system", turns, json)
                assertTrue(call.url, call.url.startsWith("https://${s.host}/"))
                assertEquals(s.host, call.host)
                assertFalse("${s.id}: the key is in the address", key in call.url)
                assertFalse("${s.id}: the key is in the body", key in call.body)
                assertEquals("${s.id}: the key is in one header", 1, call.headers.values.count { key in it })
                assertTrue(KeyRule.mayGo(call, s, listOf(s.host)))
                for (other in Services.ALL - s) {
                    // Saved for another host, or the call made for another service: it doesn't go.
                    assertFalse(KeyRule.mayGo(call, s, listOf(other.host)))
                    assertFalse(KeyRule.mayGo(call, other, listOf(other.host)))
                }
                assertFalse("a key saved before hosts were stored doesn't go", KeyRule.mayGo(call, s, null))
            }
        }
        val plain = Call("http://${Anthropic.host}/v1/messages", emptyMap(), "")
        assertFalse(KeyRule.mayGo(plain, Anthropic, listOf(Anthropic.host)))
    }

    /** Nothing but the conversation and the system prompt goes in a request. */
    @Test
    fun aRequestHoldsOnlyTheConversation() {
        for (s in Services.ALL) {
            val body = s.call(key, s.default, "SYSTEM-PROMPT", listOf(Turn(Role.USER, "QUESTION-1"), Turn(Role.ASSISTANT, "ANSWER-1"), Turn(Role.USER, "QUESTION-2")), false).body
            listOf("SYSTEM-PROMPT", "QUESTION-1", "ANSWER-1", "QUESTION-2").forEach { assertTrue("${s.id}: $it", it in body) }
        }
    }

    /** Every problem reads as a sentence, with the hint on a line of its own: no key is ever in it. */
    @Test
    fun problemsAreSaidPlainly() {
        for (s in Services.ALL) for (p in Problem.entries) {
            val message = s.fail(p, "m-1", 418, "detail").message.orEmpty()
            val lines = message.split('\n')
            assertTrue("$p: '$message'", lines.all { it.isNotBlank() && !it.endsWith(".") && " · " !in it })
        }
        assertEquals("Anthropic didn't accept the API key\nReplace it in Settings → Assistant", Anthropic.fail(Problem.BAD_KEY).message)
        assertEquals("Gemini has no model called x-1\nChoose another in Settings → Assistant", Google.fail(Problem.NO_MODEL, "x-1").message)
    }
}
