package com.mtwabpin.kolahal.assistant

import com.mtwabpin.kolahal.script.Net
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Asks [service]'s [model] the conversation [turns] and returns the answer's text. Blocking. Throws an
 * [AssistantException] in plain words. The page's one way out: device tests put a fake in its place.
 */
fun interface Asker {
    fun ask(service: Service, model: String, system: String, turns: List<Turn>, json: Boolean): String
}

/** Where a key may go: only over HTTPS, to its own service's host, and only a key saved for that host. */
object KeyRule {
    fun mayGo(call: Call, service: Service, savedFor: List<String>?): Boolean =
        call.url.startsWith("https://") && call.host == service.host && savedFor?.contains(service.host) == true
}

/** The [Asker] that sends: the user's key from [keys], the app's HTTP client, nothing else from the phone. */
class HttpAsker(private val keys: AssistantKeys) : Asker {
    // An answer with a whole screen in it takes longer than the app's usual 30 seconds.
    private val client by lazy {
        Net.client.newBuilder().callTimeout(180, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).build()
    }

    override fun ask(service: Service, model: String, system: String, turns: List<Turn>, json: Boolean): String {
        val saved = keys.saved(service) ?: throw service.fail(Problem.NO_KEY)
        val call = service.call(saved.value, model, system, turns, json)
        check(KeyRule.mayGo(call, service, saved.hosts)) { "a key may go only to ${service.host}" }
        val request = okhttp3.Request.Builder()
            .url(call.url)
            .apply { call.headers.forEach { (k, v) -> header(k, v) } }
            .header("User-Agent", Net.USER_AGENT)
            .post(call.body.toRequestBody("application/json".toMediaType()))
            .build()
        val (code, body) = try {
            client.newCall(request).execute().use { it.code to it.body.string() }
        } catch (e: java.io.IOException) {
            throw service.fail(Problem.OFFLINE)
        }
        return service.answer(code, body, model)
    }
}
