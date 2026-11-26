package com.mtwabpin.kolahal.assistant

/**
 * The services the Assistant can ask, in the order the page and Settings show them. Everything else
 * (the setting's choices, the page's rows, the key rows, where a key may go, the tests) reads this list.
 * - A model: one line in its service's `models`.
 * - A service: an entry here, and a [Service] object beside the others (its request and its reading
 *   of the reply).
 */
object Services {
    val ALL: List<Service> = listOf(Anthropic, Google, OpenAi)

    /** The service for `assistant.service`'s [id]: the first one for an id no service has. */
    fun byId(id: String): Service = ALL.firstOrNull { it.id == id } ?: ALL.first()
}
