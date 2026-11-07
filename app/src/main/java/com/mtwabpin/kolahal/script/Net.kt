package com.mtwabpin.kolahal.script

import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/** One HTTP client for the whole app: scripts, feeds, pictures in the log, Get more scripts, `update` and currency rates. */
object Net {
    const val USER_AGENT = "Kolahal-Launcher/0.1 (+https://github.com/mtwabpin)"

    /** The longest time a whole request may take, redirects included. A script's own run may set less. */
    const val CALL_TIMEOUT_MS = 30_000L

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .addNetworkInterceptor(hopGuard)
            .build()
    }

    /** A check that every hop of a request must pass before it is sent, redirects included ([hopGuard]). */
    fun interface HopCheck {
        /** Throws an IOException to stop [hop]. */
        fun check(hop: okhttp3.Request)
    }

    private val hopGuard = Interceptor { chain ->
        chain.request().tag(HopCheck::class.java)?.check(chain.request())
        chain.proceed(chain.request())
    }

    /** [link], normalised, if it is an http(s) link. Null for any other scheme (tel:, upi:, intent:, file: …). */
    fun webUrl(link: String?): String? = link?.trim()?.toHttpUrlOrNull()?.toString()

    /** A simple blocking GET. Returns the body, or throws with the HTTP status. */
    fun get(url: String): String {
        val request = okhttp3.Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            return resp.body.string()
        }
    }
}
