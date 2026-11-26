/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.gesture

import android.content.Context
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.commands.BuildFeature
import com.mtwabpin.kolahal.extras.ExtrasLink
import kotlin.concurrent.thread

/**
 * Locking the screen, for the double-tap, the quick action and `lock`: with the full build's own service
 * ([LockService]), or, in a build without it (the Play build), through Kolahal Extras ([ExtrasLink]). Each use of
 * [LockService] is behind the build switch, so R8 drops its code from the Play build.
 */
object ScreenLock {
    /** Whether Kolahal offers locking: the build's own service, or Kolahal Extras that can lock now. */
    fun offered(context: Context): Boolean =
        BuildConfig.LOCK_SERVICE || ExtrasLink.get(context).ready(BuildFeature.LOCK_SERVICE)

    /** Locks the screen on the caller's thread, which mustn't be the main one in a build without the service. True when it locked. */
    fun lockNow(context: Context): Boolean =
        if (BuildConfig.LOCK_SERVICE) LockService.lock() else ExtrasLink.get(context).lock()

    /**
     * Locks the screen from the main thread, and runs [failed] when it couldn't: at once with the build's own service,
     * or after asking Kolahal Extras off the main thread.
     */
    fun lock(context: Context, failed: () -> Unit) {
        if (BuildConfig.LOCK_SERVICE) {
            if (!LockService.lock()) failed()
        } else thread(name = "lock") { if (!ExtrasLink.get(context).lock()) failed() }
    }

    /** Whether locking works now: the service is on, in the build or in Kolahal Extras (as last read). */
    fun isEnabled(context: Context): Boolean =
        if (BuildConfig.LOCK_SERVICE) LockService.isEnabled(context) else offered(context)
}
