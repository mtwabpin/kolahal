package com.mtwabpin.kolahal.assistant

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Gemini's generateContent: the request the app sends, and its reading of answers and errors (shapes as Google documents them). */
class GoogleTest {
    private val turns = listOf(Turn(Role.USER, "Hi"), Turn(Role.ASSISTANT, "Hello"), Turn(Role.USER, "Plan a day"))

    @Test
    fun theRequest() {
        val call = Google.call("k-123456", "gemini-3.5-flash-lite", "Be brief", turns, json = true)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent", call.url)
        assertEquals("k-123456", call.headers["x-goog-api-key"])
        val body = JSONObject(call.body)
        assertEquals("Be brief", body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
        val contents = body.getJSONArray("contents")
        assertEquals(listOf("user", "model", "user"), (0 until contents.length()).map { contents.getJSONObject(it).getString("role") })
        assertEquals("Plan a day", contents.getJSONObject(2).getJSONArray("parts").getJSONObject(0).getString("text"))
        val config = body.getJSONObject("generationConfig")
        assertEquals("application/json", config.getString("responseMimeType"))
        assertEquals("LOW", config.getJSONObject("thinkingConfig").getString("thinkingLevel"))
        assertTrue(config.getInt("maxOutputTokens") > 0)
        for (k in listOf("temperature", "topP", "topK")) assertFalse(k, config.has(k))
        // Plain answers aren't asked for as JSON.
        assertFalse(JSONObject(Google.call("k-123456", "gemini-3.8-flash", "s", turns, false).body).getJSONObject("generationConfig").has("responseMimeType"))
    }

    /** Gemini 3 and later take a thinking level; an older model typed by hand doesn't get one. */
    @Test
    fun onlyNewerModelsGetAThinkingLevel() {
        Google.models.forEach { assertTrue(it.id, Google.thinks(it.id)) }
        assertFalse(Google.thinks("gemini-2.5-flash"))
        assertFalse(Google.thinks("gemini-1.5-pro"))
        assertFalse(JSONObject(Google.call("k-123456", "gemini-2.5-flash", "s", turns, false).body).getJSONObject("generationConfig").has("thinkingConfig"))
    }

    /** The model's name is in the address: one that could change the path is refused before anything is sent. */
    @Test
    fun aModelNameCantChangeTheAddress() {
        for (bad in listOf("../files", "a/b", "m?key=x", "m#x")) {
            try {
                Google.call("k-123456", bad, "s", turns, false)
                fail(bad)
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test
    fun anAnswerIsItsTextWithoutThoughts() {
        val reply = """
            {"candidates":[{"content":{"parts":[{"text":"thinking…","thought":true},{"text":"[{\"version\":"},{"text":"\"v0.9\"}]"}],"role":"model"},
              "finishReason":"STOP","index":0}],
             "usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":5,"totalTokenCount":10},"modelVersion":"gemini-3.5-flash-lite"}
        """
        assertEquals("""[{"version":"v0.9"}]""", Google.answer(200, reply, "gemini-3.5-flash-lite"))
    }

    @Test
    fun aBlockOrACutAnswerIsSaid() {
        assertProblem(Problem.REFUSED, 200, """{"promptFeedback":{"blockReason":"SAFETY"}}""")
        assertProblem(Problem.REFUSED, 200, """{"candidates":[{"content":{"parts":[]},"finishReason":"SAFETY"}]}""")
        assertProblem(Problem.REFUSED, 200, """{"candidates":[{"finishReason":"PROHIBITED_CONTENT"}]}""")
        assertProblem(Problem.CUT_OFF, 200, """{"candidates":[{"content":{"parts":[{"text":"[{"}]},"finishReason":"MAX_TOKENS"}]}""")
        assertProblem(Problem.EMPTY, 200, """{"candidates":[{"content":{"parts":[]},"finishReason":"STOP"}]}""")
    }

    @Test
    fun errorsReadPlainly() {
        fun error(code: Int, status: String, message: String, details: String = "") =
            """{"error":{"code":$code,"message":"$message","status":"$status"${if (details.isEmpty()) "" else ",\"details\":$details"}}}"""
        assertProblem(
            Problem.BAD_KEY, 400,
            error(400, "INVALID_ARGUMENT", "API key not valid. Please pass a valid API key.", """[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"API_KEY_INVALID"}]"""),
        )
        assertProblem(Problem.BAD_KEY, 403, error(403, "PERMISSION_DENIED", "Your project has been denied access."))
        assertProblem(Problem.RATE_LIMIT, 429, error(429, "RESOURCE_EXHAUSTED", "Resource has been exhausted (e.g. check quota)."))
        assertProblem(Problem.NO_MODEL, 404, error(404, "NOT_FOUND", "models/gemini-x is not found for API version v1beta"))
        assertProblem(Problem.BUSY, 503, error(503, "UNAVAILABLE", "The model is overloaded. Please try again later."))
        assertProblem(Problem.BUSY, 500, error(500, "INTERNAL", "An internal error has occurred."))
        assertProblem(Problem.OTHER, 400, error(400, "INVALID_ARGUMENT", "Invalid JSON payload received."))
    }

    private fun assertProblem(problem: Problem, code: Int, body: String) {
        try {
            Google.answer(code, body, "gemini-x")
        } catch (e: AssistantException) {
            assertEquals(body, problem, e.problem)
            return
        }
        fail("no error for $body")
    }
}
