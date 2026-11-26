package com.mtwabpin.kolahal.ui.a2ui

import com.mtwabpin.kolahal.script.Net
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The A2UI trial's only network call: Gemini's `generateContent` (REST, JSON), the whole answer at once.
 * The key goes in the `x-goog-api-key` header, never in the URL, and no message here carries it.
 */
internal object Gemini {
    const val HOST = "generativelanguage.googleapis.com"

    /** One turn of the conversation: [role] is "user" or "model". */
    class Turn(val role: String, val text: String)

    // An answer with a whole screen in it takes longer than the app's usual 30 seconds.
    private val client by lazy {
        Net.client.newBuilder().callTimeout(120, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    }

    /** The model's answer text for [turns], asked for as JSON. Blocking. Throws with a readable message. */
    fun generate(key: String, model: String, system: String, turns: List<Turn>): String {
        fun text(t: String) = JSONArray().put(JSONObject().put("text", t))
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", text(system)))
            .put("contents", JSONArray().apply {
                turns.forEach { put(JSONObject().put("role", it.role).put("parts", text(it.text))) }
            })
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        val request = okhttp3.Request.Builder()
            .url("https://$HOST/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .header("User-Agent", Net.USER_AGENT)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { resp ->
            val json = runCatching { JSONObject(resp.body.string()) }.getOrNull()
            if (!resp.isSuccessful) {
                val message = json?.optJSONObject("error")?.optString("message").orEmpty()
                throw java.io.IOException("Gemini answered HTTP ${resp.code}\n$message".trim())
            }
            json ?: throw java.io.IOException("Gemini's answer isn't JSON")
            val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
                ?: throw java.io.IOException(
                    "Gemini gave no answer\n" + json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                )
            val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
            val answer = (0 until parts.length()).map { parts.getJSONObject(it) }
                .filter { !it.optBoolean("thought") }
                .joinToString("") { it.optString("text") }
            if (answer.isBlank()) throw java.io.IOException("Gemini's answer is empty\nFinish reason: ${candidate.optString("finishReason")}")
            return answer
        }
    }
}
