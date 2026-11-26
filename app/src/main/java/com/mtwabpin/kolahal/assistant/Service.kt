package com.mtwabpin.kolahal.assistant

import org.json.JSONObject

/*
 * The Assistant's model layer: the services it asks, the keys, the conversations kept, the system
 * prompt. No Compose here: ui/assistant draws the page. Services.ALL is the one list of services.
 */

/**
 * A model a service offers: [name] as the page shows it ("Sonnet"), and [id] as the service takes it.
 * [fallback]: on a refusal, the service runs the request again on another model of its own choosing
 * (Anthropic's server-side fallback; only some models have one).
 */
class Model(val name: String, val id: String, val fallback: Boolean = false)

enum class Role { USER, ASSISTANT }

/** One turn of a conversation as it is sent: what the user said, or the model's answer. */
class Turn(val role: Role, val text: String)

/** An HTTPS request, made but not sent: [Asker] sends it, and tests read it without a network. */
class Call(val url: String, val headers: Map<String, String>, val body: String) {
    val host: String? get() = runCatching { java.net.URI(url).host }.getOrNull()
}

/** What went wrong, in the words the page shows ([AssistantException.message]). */
enum class Problem { NO_KEY, BAD_KEY, NO_CREDIT, RATE_LIMIT, BUSY, NO_MODEL, REFUSED, CUT_OFF, EMPTY, OFFLINE, OTHER }

/** A question that got no answer: [message] is its first line and hints, as the page shows them. */
class AssistantException(val problem: Problem, message: String) : java.io.IOException(message)

/**
 * One service the Assistant can ask: everything about it is here, and [Services.ALL] lists them.
 * - [id]: the value of `assistant.service`. [name]: the service as the page shows it ("Claude").
 *   [company]: whose key it is ("Anthropic").
 * - [host]: the one host its requests and its key go to. [secretName]: its key's name in the key store.
 * - [models]: the models offered, in the page's order; [default] is one of their ids. Any other id
 *   typed in Settings works too.
 * - [call] makes the request; [answer] reads the reply, or throws an [AssistantException].
 */
abstract class Service(
    val id: String,
    val name: String,
    val company: String,
    val host: String,
    val secretName: String,
    val models: List<Model>,
    val default: String,
) {
    /** The request for [turns], with [system] as the system prompt. [json]: the answer is to be JSON (rich answers). */
    abstract fun call(key: String, model: String, system: String, turns: List<Turn>, json: Boolean): Call

    /** The answer's text, from the reply's HTTP [code] and [body]. Throws an [AssistantException]. */
    abstract fun answer(code: Int, body: String, model: String): String

    /** [id]'s name as the page shows it: an offered model's name, or the id typed. */
    fun modelName(id: String): String = models.firstOrNull { it.id == id }?.name ?: id

    /** The model a question goes to when `assistant.model` is [stored]: the default for blank, or for another service's model. */
    fun modelFor(stored: String): String = when {
        stored.isBlank() -> default
        Services.ALL.any { it !== this && it.models.any { m -> m.id == stored } } -> default
        else -> stored
    }

    /** The exception for [problem], in plain words. [detail] is the service's own message, for [Problem.OTHER]. */
    fun fail(problem: Problem, model: String = "", code: Int = 0, detail: String = ""): AssistantException =
        AssistantException(problem, words(problem, model, code, detail))

    private fun words(problem: Problem, model: String, code: Int, detail: String): String = when (problem) {
        Problem.NO_KEY -> "No $company API key saved\nAdd one in Settings → Assistant"
        Problem.BAD_KEY -> "$company didn't accept the API key\nReplace it in Settings → Assistant"
        Problem.NO_CREDIT -> "The $company account has no credit or quota left\nAdd some in $company's console, then try again"
        Problem.RATE_LIMIT -> "Too many questions to $name for now\nTry again in a moment"
        Problem.BUSY -> "$name is busy\nTry again in a moment"
        Problem.NO_MODEL -> "$name has no model called $model\nChoose another in Settings → Assistant"
        Problem.REFUSED -> "$name declined to answer this"
        Problem.CUT_OFF -> "The answer was too long and was cut off\nAsk for a shorter one"
        Problem.EMPTY -> "$name gave an empty answer\nTry again"
        Problem.OFFLINE -> "Couldn't reach $name\nCheck the connection and try again"
        Problem.OTHER -> listOf("$name answered with an error (HTTP $code)", detail.take(300)).filter { it.isNotBlank() }.joinToString("\n")
    }

    override fun toString() = id

    protected companion object {
        /** The body as JSON, or null when it isn't. */
        fun json(body: String): JSONObject? = runCatching { JSONObject(body) }.getOrNull()

        /** The `error.message` of an error reply, or "". */
        fun errorMessage(json: JSONObject?): String = json?.optJSONObject("error")?.optString("message").orEmpty()
    }
}
