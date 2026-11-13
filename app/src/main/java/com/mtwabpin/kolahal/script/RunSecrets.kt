package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.sha256Hex

/**
 * One run's secrets ([HostApi] keeps one for the run): the marks that stand for the values, where a
 * mark may become its value, and the masking that keeps a value out of what the run shows or stores.
 * - A script never gets a value from Kolahal, only a mark ([mark]).
 * - A request gets the value where the script put the mark, only for the sites the `@secret` line
 *   names, and only over https ([fill], [check]).
 * - Everything else the run shows or keeps has values and marks masked ([redact], [masked], [urlKey]).
 *
 * [secrets] is null for code typed at the prompt, which has none.
 */
internal class RunSecrets(
    private val secrets: HostApi.Secrets?,
    /** Android's `Uri.encode`: one of the forms a value takes in a URL ([forms]). A JVM test passes its own. */
    private val uriEncode: (String) -> String = android.net.Uri::encode,
) {
    /** This run's own number. A mark holds it, so the mark means nothing outside the run. */
    private val nonce = java.util.UUID.randomUUID().toString().replace("-", "").take(12)

    /** This run's marks ([mark]). They use ASCII letters, digits and underscores, so URLs, JSON and headers carry them unchanged. */
    private val marks = Regex("__kolahal_secret_([A-Za-z][A-Za-z0-9_]{0,39})_${nonce}__")

    /**
     * `secrets.get(name)`: returns a mark that stands for the value you set for a declared `@secret`
     * name (nil until you set one). It never returns the value itself.
     * - The script puts the mark where a request needs the value.
     * - [HostApi.httpRequest] replaces the mark with the value ([fill]), but only for the sites the
     *   `@secret` line names, and only over https.
     * - The script never gets the value back from Kolahal. A mark can't be part of a site's name,
     *   because the address lookup would carry the value in plain text, and its error would quote
     *   it. Errors from a failed request are masked ([masked]). Only a site the line names could
     *   send the value back.
     * - The mark holds this run's [nonce], so it means nothing outside the run.
     */
    fun mark(name: String): String? {
        val s = secrets ?: throw IllegalStateException("secrets.get only works in saved scripts")
        if (s.declared.none { it.name == name }) {
            throw IllegalStateException("secrets.get(\"$name\") needs `@secret $name <site> <description>` at the top of the script")
        }
        return if (s.values[name] != null) "__kolahal_secret_${name}_${nonce}__" else null
    }

    /**
     * [text] with this run's marks replaced by their values, for a request to [url]'s host.
     * This happens only for a site that the secret's `@secret` line names, only over https, and never
     * with a mark in the site's name or its user name. Anything else stops the run.
     */
    fun fill(text: String, url: okhttp3.HttpUrl): String = marks.replace(text) { m ->
        val name = m.groupValues[1]
        val secret = secrets?.declared?.firstOrNull { it.name == name } ?: return@replace m.value
        val value = secrets.values[name] ?: return@replace m.value
        // `https://<mark>.api.example.com` ends in an allowed site, but the value would go to the
        // address lookup in plain text, and the lookup's error would give it to the script.
        if (listOf(url.host, url.username, url.password).any(marks::containsMatchIn)) {
            throw java.io.IOException("$name can't be part of a site's name, only of its path, query, headers or body")
        }
        if (!secret.allows(url.host)) throw java.io.IOException("$name may only be sent to ${secret.hosts.joinToString()}, not ${url.host}")
        if (!url.isHttps) throw java.io.IOException("$name may only be sent over https, not to http://${url.host}")
        value
    }

    /**
     * This script's secret values in every form a request or a line may carry them (as typed,
     * URL-encoded, JSON-escaped), longest first. They are needed because a site could send one back.
     * Values shorter than [SecretStore.MIN_LENGTH] can't be set.
     */
    private val secretForms: List<String> =
        secrets?.values?.values.orEmpty().filter { it.length >= SecretStore.MIN_LENGTH }.flatMap(::forms).distinct().sortedByDescending { it.length }

    /** [text] with this script's secrets masked, both values and marks. So nothing a run shows ever shows a key. */
    fun redact(text: String): String = secretForms.fold(marks.replace(text, "••••")) { t, v -> t.replace(v, "••••") }

    /**
     * [e] as the script may see it: with this script's secrets masked in its message, or [e] itself
     * when the message holds none. A request's error can quote an address or a header that contains
     * a value (OkHttp names the header and value of a bad character), and the script reads the
     * message. The cause is left out, because it would carry the value on.
     */
    fun masked(e: Throwable): Throwable {
        if (e is kotlinx.coroutines.CancellationException) return e
        val message = e.message ?: return e
        val shown = redact(message)
        if (shown == message) return e
        return when (e) {
            is java.io.IOException -> java.io.IOException(shown)
            is IllegalArgumentException -> IllegalArgumentException(shown)
            else -> IllegalStateException(shown)
        }
    }

    /**
     * A secret goes only to the sites its `@secret` line names, and only over [https]. If a request
     * carries it ([carried]: the URL, body and headers) anywhere else, or over plain http, the run
     * stops. A script that holds the value (because its site sent it back) could still disguise it.
     * So this protects against mistakes more than against a script written to leak.
     */
    fun check(host: String, carried: List<String>, https: Boolean) {
        val s = secrets ?: return
        for (secret in s.declared) {
            val value = s.values[secret.name]?.takeIf { it.length >= SecretStore.MIN_LENGTH } ?: continue
            val forms = forms(value)
            if (carried.none { text -> forms.any { it in text } }) continue
            if (!secret.allows(host)) throw java.io.IOException("${secret.name} may only be sent to ${secret.hosts.joinToString()}, not $host")
            // A script must assume a hostile network, where plain http shows the value to anyone on the way.
            if (!https) throw java.io.IOException("${secret.name} may only be sent over https, not to http://$host")
        }
    }

    /** The forms a value can take in a URL, a body or a line: as typed, URL-encoded, and JSON-escaped (Lua's json.encode writes `\/`). */
    private fun forms(value: String): Set<String> =
        setOf(value, java.net.URLEncoder.encode(value, "UTF-8"), uriEncode(value), value.replace("/", "\\/"))

    /**
     * A store key for [url] ([prefix] + the URL).
     * - A URL that carries a secret is hashed, so the key never sits in the script's store, or in a backup.
     * - A mark counts by its name, so the key is the same in every run.
     */
    fun urlKey(prefix: String, url: String): String {
        if (redact(url) == url) return prefix + url
        val stable = marks.replace(url) { "\u0000" + it.groupValues[1] }
        return prefix + "#" + sha256Hex(stable.toByteArray())
    }
}
