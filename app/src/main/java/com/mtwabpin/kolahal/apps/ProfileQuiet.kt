package com.mtwabpin.kolahal.apps

import android.content.Context
import android.os.Build
import android.os.UserHandle
import android.os.UserManager

/**
 * Pausing and resuming a profile (the work profile, the private space), the same from the
 * terminal and from scripts (`device.setWork`).
 */
object ProfileQuiet {
    /** What [set] did. */
    enum class Result {
        DONE,

        /** Resuming needs the profile's PIN: Android shows its screen, and the profile comes on after it. */
        NEEDS_PIN,

        /** Android refused: Kolahal isn't the default launcher, or it is older than Android 10. */
        FAILED,
    }

    /** Pauses ([quiet]) or resumes the profile of [user]. */
    fun set(context: Context, user: UserHandle, quiet: Boolean): Result {
        if (Build.VERSION.SDK_INT < 29) return Result.FAILED
        val um = context.getSystemService(UserManager::class.java)
        // Android answers false when it needs the PIN first, and then asks for it itself.
        return runCatching { um.requestQuietModeEnabled(quiet, user) }
            .map { if (it) Result.DONE else Result.NEEDS_PIN }
            .getOrDefault(Result.FAILED)
    }
}
