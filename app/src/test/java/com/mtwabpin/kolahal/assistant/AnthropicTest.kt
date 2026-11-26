package com.mtwabpin.kolahal.assistant

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Claude's Messages API: the request the app sends, and its reading of answers and errors (shapes as Anthropic documents them). */
class AnthropicTest {
    private val turns = listOf(Turn(Role.USER, "What is 2+2?"), Turn(Role.ASSISTANT, "4"), Turn(Role.USER, "And 3+3?"))

    @Test
    fun theRequest() {
        val call = Anthropic.call("k-123456", "claude-sonnet-5-5", "Be brief", turns, json = true)
        assertEquals("https://api.anthropic.com/v1/messages", call.url)
        assertEquals("k-123456", call.headers["x-api-key"])
        assertEquals(Anthropic.VERSION, call.headers["anthropic-version"])
        val body = JSONObject(call.body)
        assertEquals("claude-sonnet-5-5", body.getString("model"))
        assertTrue(body.getInt("max_tokens") > 0)
        val system = body.getJSONArray("system").getJSONObject(0)
        assertEquals("Be brief", system.getString("text"))
        assertEquals("ephemeral", system.getJSONObject("cache_control").getString("type"))
        val messages = body.getJSONArray("messages")
        assertEquals(listOf("user", "assistant", "user"), (0 until messages.length()).map { messages.getJSONObject(it).getString("role") })
        assertEquals("And 3+3?", messages.getJSONObject(2).getString("content"))
        assertEquals("low", body.getJSONObject("output_config").getString("effort"))
        // No sampling parameters or thinking settings: these models refuse them.
        for (k in listOf("temperature", "top_p", "top_k", "thinking")) assertFalse(k, body.has(k))
    }

    /** Sonnet and Opus ask for the server-side fallback, with its beta header; Haiku has none, nor a model typed by hand. */
    @Test
    fun theFallbackGoesOnlyWithModelsThatHaveOne() {
        for (model in listOf("claude-sonnet-5-5", "claude-opus-5-5")) {
            val call = Anthropic.call("k-123456", model, "s", turns, false)
            assertEquals("default", JSONObject(call.body).getString("fallbacks"))
            assertEquals("server-side-fallback-2026-07-01", call.headers["anthropic-beta"])
        }
        for (model in listOf("claude-haiku-5-5", "claude-some-other-1")) {
            val call = Anthropic.call("k-123456", model, "s", turns, false)
            assertFalse(JSONObject(call.body).has("fallbacks"))
            assertFalse(call.headers.containsKey("anthropic-beta"))
        }
    }

    @Test
    fun anAnswerIsItsTextBlocks() {
        val reply = """
            {"id":"msg_01","type":"message","role":"assistant","model":"claude-sonnet-5-5",
             "content":[{"type":"thinking","thinking":"","signature":"x"},{"type":"text","text":"6"},{"type":"text","text":" it is"}],
             "stop_reason":"end_turn","usage":{"input_tokens":10,"output_tokens":2}}
        """
        assertEquals("6 it is", Anthropic.answer(200, reply, "claude-sonnet-5-5"))
        // A fallback's switch point is a block of its own: only the text counts.
        val fallback = """{"content":[{"type":"fallback","from":{"model":"a"},"to":{"model":"b"}},{"type":"text","text":"ok"}],"stop_reason":"end_turn"}"""
        assertEquals("ok", Anthropic.answer(200, fallback, "claude-opus-5-5"))
    }

    @Test
    fun aRefusalOrACutAnswerIsSaid() {
        assertProblem(Problem.REFUSED, 200, """{"content":[],"stop_reason":"refusal","stop_details":{"category":"cyber"}}""")
        assertProblem(Problem.CUT_OFF, 200, """{"content":[{"type":"text","text":"[{\"ver"}],"stop_reason":"max_tokens"}""")
        assertProblem(Problem.EMPTY, 200, """{"content":[],"stop_reason":"end_turn"}""")
    }

    @Test
    fun errorsReadPlainly() {
        fun error(type: String, message: String) = """{"type":"error","error":{"type":"$type","message":"$message"},"request_id":"req_1"}"""
        assertProblem(Problem.BAD_KEY, 401, error("authentication_error", "invalid x-api-key"))
        assertProblem(Problem.BAD_KEY, 403, error("permission_error", "not allowed"))
        assertProblem(Problem.NO_CREDIT, 402, error("billing_error", "payment required"))
        assertProblem(Problem.NO_CREDIT, 400, error("invalid_request_error", "Your credit balance is too low to access the Anthropic API."))
        assertProblem(Problem.RATE_LIMIT, 429, error("rate_limit_error", "Number of request tokens has exceeded your per-minute rate limit"))
        assertProblem(Problem.NO_CREDIT, 429, error("rate_limit_error", "You have reached your monthly spend limit"))
        assertProblem(Problem.NO_MODEL, 404, error("not_found_error", "model: claude-x"))
        assertProblem(Problem.BUSY, 529, error("overloaded_error", "Overloaded"))
        assertProblem(Problem.BUSY, 500, error("api_error", "Internal server error"))
        val other = assertProblem(Problem.OTHER, 400, error("invalid_request_error", "messages: roles must alternate"))
        assertEquals("Claude answered with an error (HTTP 400)\nmessages: roles must alternate", other.message)
        // A reply that isn't JSON at all (a proxy's page) is still an error with words.
        assertProblem(Problem.BUSY, 502, "<html>Bad gateway</html>")
    }

    private fun assertProblem(problem: Problem, code: Int, body: String): AssistantException {
        try {
            Anthropic.answer(code, body, "claude-x")
        } catch (e: AssistantException) {
            assertEquals(body, problem, e.problem)
            return e
        }
        fail("no error for $body")
        error("unreachable")
    }
}
