package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.Stores
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.mtwabpin.kolahal.data.KvStore
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the values for the names a script declares with `@secret` (API keys and similar), so they
 * are never in its code.
 * - Values are encrypted with AES-GCM. The key is kept in the Android Keystore and never leaves it.
 * - Values are stored in kolahal.db (scope [scope] per script name). Android's cloud backup leaves
 *   them out, and so do Kolahal's own backups ([com.mtwabpin.kolahal.data.FullBackup]).
 * - A transfer to a new phone carries the sealed values but not the Keystore key, so they can't be
 *   read there.
 * - Both the launcher and the `:script` process read them, because the Keystore key belongs to the
 *   app, not to a process.
 *
 * Each value remembers the sites its `@secret` line named when you set it ([Stored.hosts]). If the
 * code later names other sites (after an update, or a file copied over it), it doesn't get the value
 * until you set it again. That way you see where it would go.
 */
class SecretStore(private val context: Context) {

    /** A value and the sites it was set for. [hosts] is null for a value set before sites were stored (before 0.4.0). */
    class Stored(val value: String, val hosts: List<String>?) {
        /**
         * Whether this value was set for every site that [secret] names now. A value from before sites
         * were stored covers none: it is set again once, so you see where it goes. (Before, it went to
         * whatever sites the code named.)
         */
        fun covers(secret: ScriptMeta.Secret): Boolean = if (hosts == null) secret.hosts.isEmpty() else secret.hosts.all { it in hosts }
    }

    fun get(script: String, name: String): Stored? =
        KvStore(context, scope(script)).get(name)?.let { runCatching { decode(decrypt(it)) }.getOrNull() }

    /** A value stored as before 0.4.0, without its sites. For tests. */
    @androidx.annotation.VisibleForTesting internal fun setWithoutSites(script: String, name: String, value: String) {
        KvStore(context, scope(script)).set(name, encrypt(value))
    }

    /** Sets [name] for [script], for the sites [hosts] that its line names. Null or blank clears it. */
    fun set(script: String, name: String, value: String?, hosts: List<String>) {
        KvStore(context, scope(script)).set(name, value?.takeIf { it.isNotBlank() }?.let { encrypt(encode(it, hosts)) })
    }

    /** Whether [secret] has a value that may go to the sites its line names now. */
    fun isSet(script: String, secret: ScriptMeta.Secret): Boolean = get(script, secret.name)?.covers(secret) == true

    /** True when it has a value set before sites were stored (before 0.4.0), so it must be set again. */
    fun withoutSites(script: String, secret: ScriptMeta.Secret): Boolean = get(script, secret.name)?.let { it.hosts == null } == true

    /** True when it has a value, but its line names sites the value wasn't set for. */
    fun hostsChanged(script: String, secret: ScriptMeta.Secret): Boolean = get(script, secret.name)?.let { !it.covers(secret) } == true

    /** Every value of [script] that its [declared] lines may use. Used for a run. */
    fun usable(script: String, declared: List<ScriptMeta.Secret>): Map<String, String> =
        declared.mapNotNull { d -> get(script, d.name)?.takeIf { it.covers(d) }?.let { d.name to it.value } }.toMap()

    /** Forgets the values of [script]'s secrets that its code no longer declares ([declared] names). */
    fun forgetUndeclared(script: String, declared: Collection<String>) {
        val kv = KvStore(context, scope(script))
        kv.all().keys.filter { it !in declared }.forEach { kv.set(it, null) }
    }

    /** The values of [script] as stored (sealed). Undo after an update uses them, because the update forgets values its code no longer declares. */
    fun kept(script: String): Map<String, String> = KvStore(context, scope(script)).all()

    /** Puts back values that [kept] returned. A value set since then stays. */
    fun putBack(script: String, kept: Map<String, String>) {
        val kv = KvStore(context, scope(script))
        kept.forEach { (name, sealed) -> if (kv.get(name) == null) kv.set(name, sealed) }
    }

    /** Forgets every value of [script], when it is deleted. */
    fun clear(script: String) {
        val kv = KvStore(context, scope(script))
        kv.all().keys.forEach { kv.set(it, null) }
    }

    private fun encode(value: String, hosts: List<String>) = "$MARK${hosts.joinToString(",")}$MARK$value"

    private fun decode(plain: String): Stored {
        if (!plain.startsWith(MARK)) return Stored(plain, null)
        val end = plain.indexOf(MARK, 1)
        if (end < 0) return Stored(plain, null)
        return Stored(plain.substring(end + 1), plain.substring(1, end).split(',').filter { it.isNotEmpty() })
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val sealed = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_BYTES))
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES).toString(Charsets.UTF_8)
    }

    private fun key(): SecretKey = synchronized(SecretStore::class.java) {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = try {
            keyStore.getKey(ALIAS, null) as? SecretKey
        } catch (e: UnrecoverableKeyException) {
            // The Keystore can't read the key any more (it was reset, or the key is damaged), so the key can't open
            // anything again: start over. Values sealed with the old key then read as not set, and setting them again
            // works. An error that may pass, or that isn't about the key, fails only this call: deleting the key
            // then would lose every value for good.
            if (mayPass(e)) throw e
            runCatching { keyStore.deleteEntry(ALIAS) }
            null
        }
        existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    companion object {
        /**
         * Whether [e] comes from a Keystore error that may pass on its own, or that is about the Keystore rather than
         * the key. Only Android 13 and newer say so; before, every such error counts as the key's.
         */
        internal fun mayPass(e: Throwable): Boolean = Build.VERSION.SDK_INT >= 33 && generateSequence(e) { it.cause }.take(8).any {
            it is android.security.KeyStoreException && (it.isTransientFailure || it.isSystemError)
        }

        fun scope(script: String) = Stores.SECRET_PREFIX + script

        /** Shorter values can't be told apart from ordinary text, so they couldn't be kept out of the log. */
        const val MIN_LENGTH = 4

        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "kolahal-script-secrets"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val MARK = "\u0000"
    }
}
