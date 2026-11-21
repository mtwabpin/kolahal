/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.data

import android.content.Context
import com.mtwabpin.kolahal.BuildConfig

/**
 * Settings the :script process needs. That process reads the settings file once, when it starts,
 * and never sees later changes. So home copies these settings into the database that both processes
 * read, whenever they change ([mirror], called from [com.mtwabpin.kolahal.LauncherApp]).
 * - Every shared setting is in [KEYS], and only there: [mirror] copies them all in one go.
 * - [get] of a key that isn't in [KEYS] would quietly give its default, as nobody copies it. A debug
 *   build refuses it, so a new script feature can't read a setting that never arrives.
 */
object SharedSettings {
    /** The settings the :script process reads. */
    val KEYS: List<ConfigKey<*>> = listOf(Keys.LOG_KEEP_LINES, Keys.DATA_PLAN, Keys.DATA_START_DAY)

    fun <T> get(context: Context, key: ConfigKey<T>): T {
        if (BuildConfig.DEBUG) require(key in KEYS) { "${key.key} isn't shared with the :script process: add it to SharedSettings.KEYS" }
        return KvStore(context, Stores.SHARED_SETTINGS).get(key.key)?.let { key.parse(it) }
            ?: key.default
    }

    /** Sets one shared setting directly: [mirror] copies them from the settings; a test sets one here. */
    fun <T> set(context: Context, key: ConfigKey<T>, value: T) {
        if (BuildConfig.DEBUG) require(key in KEYS) { "${key.key} isn't shared with the :script process: add it to SharedSettings.KEYS" }
        KvStore(context, Stores.SHARED_SETTINGS).set(key.key, key.format(value))
    }

    /** Copies each of [KEYS] from [config], for the :script process. */
    fun mirror(context: Context, config: Config) {
        val store = KvStore(context, Stores.SHARED_SETTINGS)
        for (key in KEYS) store.set(key.key, key.valueText(config))
    }
}
