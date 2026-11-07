package com.mtwabpin.kolahal.data

import android.content.Context

/**
 * Settings the :script process needs. That process reads the settings file once, when it starts,
 * and never sees later changes. So home copies these settings into the database that both processes
 * read, whenever they change ([com.mtwabpin.kolahal.LauncherApp]).
 */
object SharedSettings {
    const val SCOPE = "shared-settings"

    fun <T> get(context: Context, key: ConfigKey<T>): T =
        KvStore(context, SCOPE).get(key.key)?.let { key.parse(it) } ?: key.default

    fun <T> set(context: Context, key: ConfigKey<T>, value: T) = KvStore(context, SCOPE).set(key.key, key.format(value))
}
