package com.mtwabpin.kolahal.assistant

import org.json.JSONArray
import org.json.JSONObject

/**
 * Claude, through Anthropic's Messages API (`POST /v1/messages`), the whole answer at once. The key goes
 * in the `x-api-key` header.
 * - The system prompt is cached: it is the same on every turn, so later turns cost less.
 * - Someone is waiting for the answer, so the model is asked not to think long (effort `low`). Each
 *   model has its own default, so it is always said.
 * - Sonnet and Opus have Anthropic's server-side fallback (`fallbacks: "default"`): a request one of
 *   them declines runs again on the model Anthropic picks for it. Haiku has none.
 */
object Anthropic : Service(
    id = "anthropic",
    name = "Claude",
    company = "Anthropic",
    host = "api.anthropic.com",
    secretName = "anthropic",
    models = listOf(
        Model("Sonnet", "claude-sonnet-5-5", fallback = true),
        Model("Opus", "claude-opus-5-5", fallback = true),
        Model("Haiku", "claude-haiku-5-5"),
    ),
    default = "claude-sonnet-5-5",
) {
    const val VERSION = "2026-10-21"
    const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
    private const val MAX_TOKENS = 16000

    override fun call(key: String, model: String, system: String, turns: List<Turn>, json: Boolean): Call {
        val fallback = models.firstOrNull { it.id == model }?.fallback == true
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", MAX_TOKENS)
            .put(
                "system",
                JSONArray().put(
                    JSONObject().put("type", "text").put("text", system)
                        .put("cache_control", JSONObject().put("type", "ephemeral"))
                )
            )
            .put("messages", JSONArray().apply {
                turns.forEach {
                    put(JSONObject().put("role", if (it.role == Role.USER) "user" else "assistant").put("content", it.text))
                }
            })
            .put("output_config", JSONObject().put("effort", "low"))
        if (fallback) body.put("fallbacks", "default")
        val headers = buildMap {
            put("x-api-key", key)
            put("anthropic-version", VERSION)
            if (fallback) put("anthropic-beta", FALLBACK_BETA)
        }
        return Call("https://$host/v1/messages", headers, body.toString())
    }

    override fun answer(code: Int, body: String, model: String): String {
        val json = json(body)
        if (code !in 200..299) {
            val message = errorMessage(json)
            val type = json?.optJSONObject("error")?.optString("type").orEmpty()
            throw when {
                code == 401 || code == 403 || type == "authentication_error" || type == "permission_error" -> fail(Problem.BAD_KEY)
                code == 402 || type == "billing_error" || "credit balance" in message.lowercase() -> fail(Problem.NO_CREDIT)
                // A monthly spend cap is a 429 too, and waiting doesn't lift it.
                code == 429 && "spend" in message.lowercase() -> fail(Problem.NO_CREDIT)
                code == 429 -> fail(Problem.RATE_LIMIT)
                code == 404 || type == "not_found_error" -> fail(Problem.NO_MODEL, model)
                code == 529 || code >= 500 -> fail(Problem.BUSY)
                else -> fail(Problem.OTHER, model, code, message)
            }
        }
        json ?: throw fail(Problem.OTHER, model, code, "The answer isn't JSON")
        // A refusal is a normal reply: read its reason before its content.
        when (json.optString("stop_reason")) {
            "refusal" -> throw fail(Problem.REFUSED)
            "max_tokens" -> throw fail(Problem.CUT_OFF)
        }
        val blocks = json.optJSONArray("content") ?: JSONArray()
        val text = (0 until blocks.length()).mapNotNull { blocks.optJSONObject(it) }
            .filter { it.optString("type") == "text" }
            .joinToString("") { it.optString("text") }
        if (text.isBlank()) throw fail(Problem.EMPTY)
        return text
    }
}
