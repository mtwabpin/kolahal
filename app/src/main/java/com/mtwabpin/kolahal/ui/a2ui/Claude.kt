package com.mtwabpin.kolahal.ui.a2ui

import com.mtwabpin.kolahal.script.Net
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The A2UI trial's only network call: the Claude API's Messages endpoint (REST, JSON), the whole
 * answer at once. The key goes in the `x-api-key` header, and no message here carries it.
 */
internal object Claude {
    const val HOST = "api.anthropic.com"

    /** The models the page offers, the first as `a2ui.model`'s default: what it shows, and its id. */
    val MODELS = listOf(
        "Sonnet" to "claude-sonnet-5-5",
        "Opus" to "claude-opus-5-5",
        "Haiku" to "claude-haiku-5-5",
    )

    /** One turn of the conversation: [role] is "user" or "assistant". */
    class Turn(val role: String, val text: String)

    // Someone is waiting for a screen, so the model is asked not to think long. Each model has its own
    // default, so it is always said.
    private const val EFFORT = "low"
    private const val MAX_TOKENS = 16000

    // An answer with a whole screen in it takes longer than the app's usual 30 seconds.
    private val client by lazy {
        Net.client.newBuilder().callTimeout(180, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).build()
    }

    /** The model's answer text for [turns]. Blocking. Throws with a readable message. */
    fun generate(key: String, model: String, system: String, turns: List<Turn>): String {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", MAX_TOKENS)
            // The system prompt is large and the same on every turn: cached, so later turns cost less.
            .put(
                "system",
                JSONArray().put(
                    JSONObject().put("type", "text").put("text", system)
                        .put("cache_control", JSONObject().put("type", "ephemeral"))
                )
            )
            .put("messages", JSONArray().apply {
                turns.forEach { put(JSONObject().put("role", it.role).put("content", it.text)) }
            })
            .put("output_config", JSONObject().put("effort", EFFORT))
        val request = okhttp3.Request.Builder()
            .url("https://$HOST/v1/messages")
            .header("x-api-key", key)
            .header("anthropic-version", "2026-10-21")
            .header("User-Agent", Net.USER_AGENT)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { resp ->
            val json = runCatching { JSONObject(resp.body.string()) }.getOrNull()
            if (!resp.isSuccessful) {
                val message = json?.optJSONObject("error")?.optString("message").orEmpty()
                val what = when (resp.code) {
                    401 -> "The API key isn't valid"
                    403 -> "The API key isn't allowed to do this"
                    429 -> "Too many requests, or the account has no credit: try again in a moment"
                    529, 503 -> "Claude is overloaded: try again in a moment"
                    else -> "Claude answered an error"
                }
                throw java.io.IOException("$what (HTTP ${resp.code})\n$message".trim())
            }
            json ?: throw java.io.IOException("Claude's answer isn't JSON")
            when (json.optString("stop_reason")) {
                "refusal" -> throw java.io.IOException("Claude declined to answer this")
                "max_tokens" -> throw java.io.IOException("The answer was too long and was cut off")
            }
            val blocks = json.optJSONArray("content") ?: JSONArray()
            val answer = (0 until blocks.length()).map { blocks.getJSONObject(it) }
                .filter { it.optString("type") == "text" }
                .joinToString("") { it.optString("text") }
            if (answer.isBlank()) throw java.io.IOException("Claude's answer is empty\nStop reason: ${json.optString("stop_reason")}")
            return unwrapped(answer)
        }
    }

    /** [answer] without a code fence or words around its JSON: from the first `[` to the last `]`. */
    internal fun unwrapped(answer: String): String {
        val from = answer.indexOf('[')
        val to = answer.lastIndexOf(']')
        return if (from in 0 until to) answer.substring(from, to + 1) else answer.trim()
    }
}
