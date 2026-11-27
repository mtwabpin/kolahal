package com.mtwabpin.kolahal.assistant

import com.mtwabpin.kolahal.script.Net
import com.mtwabpin.kolahal.script.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/**
 * Asks [service]'s [model] the conversation [turns] and returns the answer's text. Blocking. Throws an
 * [AssistantException] in plain words. The page's one way out: device tests put a fake in its place.
 */
fun interface Asker {
    fun ask(service: Service, model: String, system: String, turns: List<Turn>, json: Boolean): String

    /**
     * As [ask], from a coroutine, off the main thread. Cancelling the coroutine stops the request where
     * the asker can: [HttpAsker] cancels its call. By default, [ask] on the IO dispatcher.
     */
    suspend fun send(service: Service, model: String, system: String, turns: List<Turn>, json: Boolean): String =
        withContext(Dispatchers.IO) { ask(service, model, system, turns, json) }
}

/** Where a key may go: only over HTTPS, to its own service's host, and only a key saved for that host. */
object KeyRule {
    fun mayGo(call: Call, service: Service, savedFor: List<String>?): Boolean =
        call.url.startsWith("https://") && call.host == service.host && savedFor?.contains(service.host) == true

    /**
     * Whether [key] can be a key: printable ASCII, no spaces, as every service's keys are. A key copied
     * from a web page can carry a character that can't be seen (a zero-width space): no header can hold
     * it, so it is refused where it is entered.
     */
    fun fits(key: String): Boolean = key.isNotEmpty() && key.all { it in '!'..'~' }

    /** What the key fields say under a key that doesn't [fit][fits]. */
    const val UNFIT = "The key has a space or a character that isn't allowed"
}

/**
 * The [Asker] that sends: the user's key from [saved], the app's HTTP client, nothing else from the
 * phone. A redirect isn't followed: the key would go with it, maybe to another host.
 */
class HttpAsker internal constructor(
    private val saved: (Service) -> SecretStore.Stored?,
    base: () -> OkHttpClient,
) : Asker {
    constructor(keys: AssistantKeys) : this(keys::saved, { Net.client })

    private val client by lazy { assistantClient(base()) }

    override fun ask(service: Service, model: String, system: String, turns: List<Turn>, json: Boolean): String {
        val call = newCall(service, model, system, turns, json)
        val (code, body) = try {
            call.execute().use { it.code to it.body.string() }
        } catch (e: IOException) {
            throw service.fail(Problem.OFFLINE)
        }
        return read(service, model, code, body)
    }

    override suspend fun send(service: Service, model: String, system: String, turns: List<Turn>, json: Boolean): String {
        // The key store is read off the main thread; the call itself runs on the client's own threads.
        val call = withContext(Dispatchers.IO) { newCall(service, model, system, turns, json) }
        val (code, body) = try {
            call.await()
        } catch (e: IOException) {
            throw service.fail(Problem.OFFLINE)
        }
        return read(service, model, code, body)
    }

    private fun newCall(service: Service, model: String, system: String, turns: List<Turn>, json: Boolean): okhttp3.Call {
        val saved = saved(service) ?: throw service.fail(Problem.NO_KEY)
        if (!KeyRule.fits(saved.value)) throw service.unfitKey()
        val call = service.call(saved.value, model, system, turns, json)
        check(KeyRule.mayGo(call, service, saved.hosts)) { "a key may go only to ${service.host}" }
        return client.newCall(request(call, service))
    }

    private fun read(service: Service, model: String, code: Int, body: String): String {
        if (code in 300..399) throw service.fail(Problem.OTHER, model, code, "It sent the question to another address")
        return service.answer(code, body, model)
    }

    companion object {
        /** The Assistant's client: an answer with a whole screen in it takes longer than the app's usual 30 seconds, and no redirect is followed. */
        internal fun assistantClient(base: OkHttpClient): OkHttpClient = base.newBuilder()
            .callTimeout(180, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        /**
         * [call] as OkHttp's request. OkHttp refuses a header value it can't send with the whole value in
         * its message, and the value is the key: that message goes no further, a fixed one takes its place.
         */
        internal fun request(call: Call, service: Service): okhttp3.Request = try {
            okhttp3.Request.Builder()
                .url(call.url)
                .apply { call.headers.forEach { (k, v) -> header(k, v) } }
                .header("User-Agent", Net.USER_AGENT)
                .post(call.body.toRequestBody("application/json".toMediaType()))
                .build()
        } catch (e: IllegalArgumentException) {
            throw service.unfitKey()
        }

        /** The call's code and body; cancelling the coroutine cancels the call. */
        private suspend fun okhttp3.Call.await(): Pair<Int, String> = suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { this@await.cancel() }
            enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    // If the continuation was cancelled already, it ignores this.
                    cont.resumeWith(runCatching { response.use { it.code to it.body.string() } })
                }
            })
        }
    }
}
