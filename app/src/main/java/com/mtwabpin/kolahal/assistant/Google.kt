package com.mtwabpin.kolahal.assistant

import org.json.JSONArray
import org.json.JSONObject

/**
 * Gemini, through Google's `generateContent` (`POST /v1beta/models/{model}:generateContent`), the whole
 * answer at once. The key goes in the `x-goog-api-key` header, never in the address.
 * - A rich answer is asked for as JSON (`responseMimeType`).
 * - Gemini 3 and later think by default: the thinking level is set low, as someone is waiting. Older
 *   models refuse the setting, so it goes only to the newer ones.
 */
object Google : Service(
    id = "google",
    name = "Gemini",
    company = "Google",
    host = "generativelanguage.googleapis.com",
    secretName = "google",
    models = listOf(
        Model("Flash-Lite", "gemini-3.5-flash-lite"),
        Model("Flash", "gemini-3.8-flash"),
        Model("Hydrogen", "gemini-4-hydrogen"),
        Model("Carbon", "gemini-4-carbon"),
    ),
    default = "gemini-3.5-flash-lite",
) {
    private const val MAX_TOKENS = 16000

    /** Whether [model] takes a thinking level: Gemini 3 and later. */
    internal fun thinks(model: String) = !Regex("^gemini-[12][.-]").containsMatchIn(model)

    override fun call(key: String, model: String, system: String, turns: List<Turn>, json: Boolean): Call {
        fun parts(text: String) = JSONArray().put(JSONObject().put("text", text))
        val config = JSONObject().put("maxOutputTokens", MAX_TOKENS)
        if (json) config.put("responseMimeType", "application/json")
        if (thinks(model)) config.put("thinkingConfig", JSONObject().put("thinkingLevel", "LOW"))
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", parts(system)))
            .put("contents", JSONArray().apply {
                turns.forEach {
                    put(JSONObject().put("role", if (it.role == Role.USER) "user" else "model").put("parts", parts(it.text)))
                }
            })
            .put("generationConfig", config)
        // The model's name is in the address: only the characters a name has, so it can't change the path.
        require(MODEL_ID.matches(model)) { "not a model name: $model" }
        return Call("https://$host/v1beta/models/$model:generateContent", mapOf("x-goog-api-key" to key), body.toString())
    }

    override fun answer(code: Int, body: String, model: String): String {
        val json = json(body)
        if (code !in 200..299) {
            val error = json?.optJSONObject("error")
            val message = error?.optString("message").orEmpty()
            val status = error?.optString("status").orEmpty()
            val badKey = "API key" in message || "API_KEY_INVALID" in (error?.optJSONArray("details")?.toString().orEmpty())
            throw when {
                code == 401 || code == 403 || badKey -> fail(Problem.BAD_KEY)
                code == 402 -> fail(Problem.NO_CREDIT)
                (code == 429 || status == "RESOURCE_EXHAUSTED") && usedUp(error, message) -> fail(Problem.NO_CREDIT)
                code == 429 || status == "RESOURCE_EXHAUSTED" -> fail(Problem.RATE_LIMIT)
                code == 404 || status == "NOT_FOUND" -> fail(Problem.NO_MODEL, model)
                code >= 500 -> fail(Problem.BUSY)
                else -> fail(Problem.OTHER, model, code, message)
            }
        }
        json ?: throw fail(Problem.OTHER, model, code, "The answer isn't JSON")
        // No candidates: the question itself was blocked.
        val candidate = json.optJSONArray("candidates")?.optJSONObject(0) ?: throw fail(Problem.REFUSED)
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val text = (0 until parts.length()).mapNotNull { parts.optJSONObject(it) }
            .filter { !it.optBoolean("thought") }
            .joinToString("") { it.optString("text") }
        when (candidate.optString("finishReason")) {
            "", "STOP", "FINISH_REASON_UNSPECIFIED" -> Unit
            "MAX_TOKENS", "CONTINUATION" -> throw fail(Problem.CUT_OFF)
            else -> throw fail(Problem.REFUSED)
        }
        if (text.isBlank()) throw fail(Problem.EMPTY)
        return text
    }

    /**
     * Whether a quota error says the quota is used up, not just reached for the moment: a daily quota
     * (its `QuotaFailure` names one "PerDay"), or none at all for this model ("limit: 0", a quota of 0).
     * A per-minute quota is a momentary limit.
     */
    internal fun usedUp(error: JSONObject?, message: String): Boolean {
        if (Regex("""limit:\s*0\b""").containsMatchIn(message)) return true
        val details = error?.optJSONArray("details") ?: return false
        return (0 until details.length()).mapNotNull { details.optJSONObject(it) }.any { d ->
            val violations = d.optJSONArray("violations") ?: JSONArray()
            (0 until violations.length()).mapNotNull { violations.optJSONObject(it) }.any { v ->
                "PerDay" in v.optString("quotaId") || v.optString("quotaValue") == "0"
            }
        }
    }

    private val MODEL_ID = Regex("[A-Za-z0-9._-]+")
}
