/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.extras

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process

/** Who may ask Extras for anything, and what Android gives Extras now. */
object Kolahal {
    /**
     * Whether the app with [uid] is Kolahal (a package of [ExtrasContract.KOLAHAL_PACKAGES]) signed with this app's
     * key. Checked on every call: it holds whichever app was installed first.
     */
    fun trusted(context: Context, uid: Int): Boolean {
        val pm = context.packageManager
        val names = runCatching { pm.getPackagesForUid(uid) }.getOrNull() ?: return false
        if (names.none { it in ExtrasContract.KOLAHAL_PACKAGES }) return false
        return pm.checkSignatures(Process.myUid(), uid) == PackageManager.SIGNATURE_MATCH
    }

    /** What the screen says about Kolahal: not installed, installed with another key, or installed with this one. */
    enum class Found { MISSING, OTHER_KEY, SAME_KEY }

    /** Kolahal as this build of Extras pairs with it: the debug build with Kolahal's debug build. */
    private val packageName = if (BuildConfig.DEBUG) "com.mtwabpin.kolahal.debug" else "com.mtwabpin.kolahal"

    fun find(context: Context): Found {
        val pm = context.packageManager
        val uid = runCatching { pm.getPackageUid(packageName, 0) }.getOrNull() ?: return Found.MISSING
        return if (trusted(context, uid)) Found.SAME_KEY else Found.OTHER_KEY
    }

    fun canReadContacts(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun canReadCalls(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    /** The features ([ExtrasContract.FEATURES]) Android gives Extras now. */
    fun allowed(context: Context): List<String> = buildList {
        if (LockService.isEnabled(context)) add(ExtrasContract.LOCK)
        if (canReadContacts(context)) add(ExtrasContract.CONTACTS)
        if (canReadCalls(context)) add(ExtrasContract.CALLS)
    }
}
