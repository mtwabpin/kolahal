package com.mtwabpin.kolahal.assistant

import android.content.Context
import com.mtwabpin.kolahal.script.SecretStore

/**
 * Each service's API key, sealed in [SecretStore] under an owner name no script can have, and saved
 * for its service's host alone. Like scripts' secrets, never in a backup, never logged or shown.
 * Blocking: call off the main thread.
 */
class AssistantKeys(context: Context) {
    private val secrets = SecretStore(context)

    /** The key saved for [service], with the hosts it was saved for, or null. A key from the trial page moves over first. */
    fun saved(service: Service): SecretStore.Stored? {
        secrets.get(OWNER, service.secretName)?.let { return it }
        val old = secrets.get(OLD_OWNER, service.secretName) ?: return null
        secrets.set(OWNER, service.secretName, old.value, listOf(service.host))
        secrets.set(OLD_OWNER, service.secretName, null, emptyList())
        return secrets.get(OWNER, service.secretName)
    }

    fun has(service: Service): Boolean = saved(service) != null

    /** Saves [key] for [service]'s host; null removes it. */
    fun set(service: Service, key: String?) {
        secrets.set(OWNER, service.secretName, key?.trim(), listOf(service.host))
        if (key == null) secrets.set(OLD_OWNER, service.secretName, null, emptyList())
    }

    companion object {
        private const val OWNER = "/assistant"

        /** Where the trial page kept its key (the Anthropic one, under the same name). */
        private const val OLD_OWNER = "/a2ui"

        /** The shortest key the page saves: shorter can't be told from ordinary text. */
        const val MIN_LENGTH = SecretStore.MIN_LENGTH
    }
}
