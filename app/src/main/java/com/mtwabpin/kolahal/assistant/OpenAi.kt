package com.mtwabpin.kolahal.assistant

import org.json.JSONArray
import org.json.JSONObject

/**
 * ChatGPT's models, through OpenAI's Responses API (`POST /v1/responses`), the whole answer at once. The
 * key goes in the `Authorization: Bearer` header.
 * - The system prompt is the first message, from the developer: it is the same on every turn, so it is
 *   cached. OpenAI keeps nothing of the conversation (`store: false`): the app sends it whole each time.
 * - Reasoning models (GPT-5 and later, the o-series) are asked not to think long (effort `low`); older
 *   models refuse the setting, so it goes only to these.
 */
object OpenAi : Service(
    id = "openai",
    name = "ChatGPT",
    company = "OpenAI",
    host = "api.openai.com",
    secretName = "openai",
    models = listOf(
        Model("Luna", "gpt-6-luna"),
        Model("Terra", "gpt-6.1-terra"),
        Model("Sol", "gpt-6.2-sol"),
        Model("Astra", "gpt-6.2-astra"),
    ),
    default = "gpt-6-luna",
) {
    /** Reasoning counts toward it, so it is generous. */
    private const val MAX_TOKENS = 25000

    /** Whether [model] takes a reasoning effort. */
    internal fun reasons(model: String) = Regex("^(gpt-[5-9]|gpt-\\d\\d|o\\d)").containsMatchIn(model)

    override fun call(key: String, model: String, system: String, turns: List<Turn>, json: Boolean): Call {
        val input = JSONArray().put(JSONObject().put("role", "developer").put("content", system))
        turns.forEach { input.put(JSONObject().put("role", if (it.role == Role.USER) "user" else "assistant").put("content", it.text)) }
        val body = JSONObject()
            .put("model", model)
            .put("input", input)
            .put("max_output_tokens", MAX_TOKENS)
            .put("store", false)
        if (reasons(model)) body.put("reasoning", JSONObject().put("effort", "low"))
        return Call("https://$host/v1/responses", mapOf("Authorization" to "Bearer $key"), body.toString())
    }

    /** The 429 codes that waiting doesn't help: no credit, or a spending or usage limit reached. */
    private fun outOfCredit(code: String, type: String) =
        code == "insufficient_quota" || type == "insufficient_quota" || code == "credit_balance_exhausted" ||
            code.endsWith("_spend_limit_exceeded") || code.endsWith("_usage_limit_exceeded")

    override fun answer(code: Int, body: String, model: String): String {
        val json = json(body)
        if (code !in 200..299) {
            val error = json?.optJSONObject("error")
            val type = error?.optString("type").orEmpty()
            val errorCode = error?.optString("code").orEmpty()
            throw when {
                code == 401 || errorCode == "invalid_api_key" -> fail(Problem.BAD_KEY)
                code == 429 && outOfCredit(errorCode, type) -> fail(Problem.NO_CREDIT)
                code == 429 -> fail(Problem.RATE_LIMIT)
                code == 404 || errorCode == "model_not_found" -> fail(Problem.NO_MODEL, model)
                code >= 500 -> fail(Problem.BUSY)
                else -> fail(Problem.OTHER, model, code, error?.optString("message").orEmpty())
            }
        }
        json ?: throw fail(Problem.OTHER, model, code, "The answer isn't JSON")
        if (json.optString("status") == "incomplete") {
            throw when (json.optJSONObject("incomplete_details")?.optString("reason")) {
                "content_filter" -> fail(Problem.REFUSED)
                else -> fail(Problem.CUT_OFF)
            }
        }
        json.optJSONObject("error")?.let { throw fail(Problem.OTHER, model, code, it.optString("message")) }
        // The output holds reasoning items too: only messages' text counts.
        val output = json.optJSONArray("output") ?: JSONArray()
        val parts = (0 until output.length()).mapNotNull { output.optJSONObject(it) }
            .filter { it.optString("type") == "message" }
            .flatMap { m -> m.optJSONArray("content")?.let { c -> (0 until c.length()).mapNotNull { c.optJSONObject(it) } }.orEmpty() }
        val text = parts.filter { it.optString("type") == "output_text" }.joinToString("") { it.optString("text") }
        if (text.isBlank() && parts.any { it.optString("type") == "refusal" }) throw fail(Problem.REFUSED)
        if (text.isBlank()) throw fail(Problem.EMPTY)
        return text
    }
}
