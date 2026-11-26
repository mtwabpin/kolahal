package com.mtwabpin.kolahal.assistant

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** OpenAI's Responses API: the request the app sends, and its reading of answers and errors (shapes as OpenAI documents them). */
class OpenAiTest {
    private val turns = listOf(Turn(Role.USER, "Hi"), Turn(Role.ASSISTANT, "Hello"), Turn(Role.USER, "List three fruits"))

    @Test
    fun theRequest() {
        val call = OpenAi.call("k-123456", "gpt-6-luna", "Be brief", turns, json = true)
        assertEquals("https://api.openai.com/v1/responses", call.url)
        assertEquals("Bearer k-123456", call.headers["Authorization"])
        val body = JSONObject(call.body)
        assertEquals("gpt-6-luna", body.getString("model"))
        assertFalse("nothing is kept at OpenAI", body.getBoolean("store"))
        val input = body.getJSONArray("input")
        // The system prompt first, so it is cached, then the conversation.
        assertEquals(listOf("developer", "user", "assistant", "user"), (0 until input.length()).map { input.getJSONObject(it).getString("role") })
        assertEquals("Be brief", input.getJSONObject(0).getString("content"))
        assertEquals("List three fruits", input.getJSONObject(3).getString("content"))
        assertEquals("low", body.getJSONObject("reasoning").getString("effort"))
        assertTrue(body.getInt("max_output_tokens") >= 16000)
        for (k in listOf("temperature", "top_p", "instructions", "previous_response_id")) assertFalse(k, body.has(k))
    }

    /** Reasoning models get an effort; an older model typed by hand doesn't. */
    @Test
    fun onlyReasoningModelsGetAnEffort() {
        OpenAi.models.forEach { assertTrue(it.id, OpenAi.reasons(it.id)) }
        assertTrue(OpenAi.reasons("gpt-5.6"))
        assertTrue(OpenAi.reasons("o4-mini"))
        assertFalse(OpenAi.reasons("gpt-4o"))
        assertFalse(JSONObject(OpenAi.call("k-123456", "gpt-4o", "s", turns, false).body).has("reasoning"))
    }

    @Test
    fun anAnswerIsItsMessagesText() {
        val reply = """
            {"id":"resp_1","object":"response","status":"completed","model":"gpt-6-luna",
             "output":[{"id":"rs_1","type":"reasoning","summary":[]},
                       {"id":"msg_1","type":"message","role":"assistant","status":"completed",
                        "content":[{"type":"output_text","text":"Apple, ","annotations":[]},{"type":"output_text","text":"pear, plum","annotations":[]}]}],
             "usage":{"input_tokens":20,"output_tokens":6,"total_tokens":26}}
        """
        assertEquals("Apple, pear, plum", OpenAi.answer(200, reply, "gpt-6-luna"))
    }

    @Test
    fun aRefusalOrACutAnswerIsSaid() {
        assertProblem(Problem.REFUSED, 200, """{"status":"completed","output":[{"type":"message","content":[{"type":"refusal","refusal":"I can't help with that."}]}]}""")
        assertProblem(Problem.CUT_OFF, 200, """{"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},"output":[]}""")
        assertProblem(Problem.REFUSED, 200, """{"status":"incomplete","incomplete_details":{"reason":"content_filter"},"output":[]}""")
        assertProblem(Problem.EMPTY, 200, """{"status":"completed","output":[{"type":"reasoning","summary":[]}]}""")
    }

    @Test
    fun errorsReadPlainly() {
        fun error(message: String, type: String, code: String?) =
            """{"error":{"message":"$message","type":"$type","param":null,"code":${code?.let { "\"$it\"" } ?: "null"}}}"""
        assertProblem(Problem.BAD_KEY, 401, error("Incorrect API key provided: k-12***56.", "invalid_request_error", "invalid_api_key"))
        assertProblem(Problem.NO_CREDIT, 429, error("You exceeded your current quota.", "insufficient_quota", "insufficient_quota"))
        assertProblem(Problem.NO_CREDIT, 429, error("Credit balance exhausted.", "insufficient_quota", "credit_balance_exhausted"))
        assertProblem(Problem.NO_CREDIT, 429, error("Spend limit reached.", "insufficient_quota", "project_spend_limit_exceeded"))
        assertProblem(Problem.RATE_LIMIT, 429, error("Rate limit reached for requests", "requests", "rate_limit_exceeded"))
        assertProblem(Problem.RATE_LIMIT, 429, error("Slow down", "rate_limit_error", "slow_down"))
        assertProblem(Problem.NO_MODEL, 404, error("The model `gpt-x` does not exist", "invalid_request_error", "model_not_found"))
        assertProblem(Problem.BUSY, 503, error("Model temporarily overloaded", "service_unavailable_error", "server_is_overloaded"))
        assertProblem(Problem.BUSY, 500, error("The server had an error while processing your request.", "server_error", null))
        assertProblem(Problem.OTHER, 403, error("Country, region, or territory not supported", "request_forbidden", "unsupported_country_region_territory"))
    }

    /** A wrong key's message holds part of the key: the page never shows it. */
    @Test
    fun aWrongKeysMessageIsNotShown() {
        try {
            OpenAi.answer(401, """{"error":{"message":"Incorrect API key provided: k-12***56.","type":"invalid_request_error","code":"invalid_api_key"}}""", "gpt-6-luna")
            fail()
        } catch (e: AssistantException) {
            assertFalse(e.message.orEmpty(), "k-12" in e.message.orEmpty())
        }
    }

    private fun assertProblem(problem: Problem, code: Int, body: String) {
        try {
            OpenAi.answer(code, body, "gpt-x")
        } catch (e: AssistantException) {
            assertEquals(body, problem, e.problem)
            return
        }
        fail("no error for $body")
    }
}
