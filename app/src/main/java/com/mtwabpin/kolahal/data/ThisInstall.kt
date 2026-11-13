package com.mtwabpin.kolahal.data

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Whether kolahal.db was written by this install of Kolahal.
 *
 * Android's transfer to a new phone (over a cable or Wi-Fi) copies the database whole, with what
 * belongs to the old phone ([Stores.THIS_PHONE]):
 * - the id of the last call it logged: on the new phone, call ids start low again, so new calls
 *   wouldn't be logged until they passed it;
 * - the apps it had: the first check on the new phone would log every difference as installed,
 *   updated or removed;
 * - the copy of recent notifications kept for scripts.
 *
 * So each install keeps a random id in noBackupFilesDir, which no backup or transfer copies, and the
 * database keeps a copy. When they differ, the database came from elsewhere, and those scopes are
 * cleared, as on a first start. The copy of settings for the `:script` process stays: the settings
 * came along too, and home writes it again at once.
 */
object ThisInstall {
    private const val KEY = "id"
    private const val FILE = "install-id"

    @Volatile private var checked = false

    /** Makes the next [check] look again. For tests. */
    @androidx.annotation.VisibleForTesting internal fun forget() {
        checked = false
    }

    /** Clears the old phone's scopes when the database came from another install. Cheap after the first call. */
    fun check(context: Context) {
        if (checked) return
        synchronized(this) {
            if (checked) return
            val file = File(context.noBackupFilesDir, FILE)
            val mine = runCatching { file.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
            val store = KvStore(context, Stores.INSTALL)
            val stored = store.get(KEY)
            val id = mine ?: UUID.randomUUID().toString().also { file.writeText(it) }
            // Neither one: a new install, or the first start of a version with this check. Nothing came from elsewhere.
            if (stored != null && stored != id) clearOldPhone(context)
            if (stored != id) store.set(KEY, id)
            checked = true
        }
    }

    private fun clearOldPhone(context: Context) {
        val db = Db.get(context).writableDatabase
        for (scope in Stores.THIS_PHONE - Stores.SHARED_SETTINGS) db.delete("kv", "scope = ?", arrayOf(scope))
    }
}
