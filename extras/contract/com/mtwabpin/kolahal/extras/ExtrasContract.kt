/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.extras

import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract

/**
 * What Kolahal and Kolahal Extras say to each other. One file, compiled into both apps (each build file adds this
 * folder to its sources), so the two can't disagree on a name.
 *
 * Extras has one exported entry point, a content provider at [authority]:
 * - `call(`[HELLO]`)`: [KEY_VERSION], [KEY_SESSION], and for each feature ([LOCK], [CONTACTS], [CALLS]) whether
 *   Extras has it ([KEY_HAS]) and whether Android gives it now ([KEY_ALLOWED]).
 * - `call(`[METHOD_LOCK]`)`: locks the screen; answers [KEY_ENABLED] and [KEY_LOCKED].
 * - `query(`[PATH_CONTACTS]`)`: every phone number, with [CONTACT_COLUMNS] in that order.
 * - `query(`[PATH_CALLS]`)`: the newest calls first, with [CALL_COLUMNS] in that order; [PARAM_LIMIT], [PARAM_AFTER]
 *   (an id) and [PARAM_SINCE] (a time in ms) narrow it.
 * - Photos come as addresses of Extras' own ([PATH_PHOTO]), which only Kolahal can open.
 * - Extras calls `notifyChange` on the contacts and calls addresses when the phone's change, while it runs.
 *
 * Extras answers only Kolahal ([KOLAHAL_PACKAGES]) signed with its own key: it checks the caller on every call.
 */
object ExtrasContract {
    /** The interface's version: Extras says it in [HELLO]. A Kolahal that needs more asks for a newer Extras. */
    const val VERSION = 1

    /** Extras' package (the debug build adds `.debug`, as Kolahal's does). */
    const val PACKAGE = "com.mtwabpin.kolahal.extras"

    /** The packages Extras answers: Kolahal's release (the full and the Play build) and its debug build. */
    val KOLAHAL_PACKAGES = setOf("com.mtwabpin.kolahal", "com.mtwabpin.kolahal.debug")

    fun authority(extrasPackage: String) = "$extrasPackage.provider"

    fun uri(extrasPackage: String, path: String = ""): Uri = Uri.parse("content://${authority(extrasPackage)}/$path")

    // The features, as HELLO names them.
    const val LOCK = "lock"
    const val CONTACTS = "contacts"
    const val CALLS = "calls"
    val FEATURES = listOf(LOCK, CONTACTS, CALLS)

    // Methods.
    const val HELLO = "hello"
    const val METHOD_LOCK = "lock"

    // What HELLO answers.
    const val KEY_VERSION = "version"

    /** New each time Extras' process starts: a change means it may have missed a change of the contacts. */
    const val KEY_SESSION = "session"

    /** The features Extras has (a string array). */
    const val KEY_HAS = "has"

    /** The features Android gives Extras now (a string array). */
    const val KEY_ALLOWED = "allowed"

    // What METHOD_LOCK answers.
    const val KEY_ENABLED = "enabled"
    const val KEY_LOCKED = "locked"

    // Paths.
    const val PATH_CONTACTS = "contacts"
    const val PATH_CALLS = "calls"
    const val PATH_PHOTO = "photo"

    /** The photo's own address, under [PATH_PHOTO]. */
    const val PARAM_PHOTO = "of"
    const val PARAM_LIMIT = "limit"
    const val PARAM_AFTER = "after"
    const val PARAM_SINCE = "since"

    /** What a contacts row has, in this order: what Kolahal's full build reads from the phone. */
    val CONTACT_COLUMNS = arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.TYPE,
        ContactsContract.CommonDataKinds.Phone.LABEL,
        ContactsContract.CommonDataKinds.Phone.STARRED,
        ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
        ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
    )

    /** What a calls row has, in this order: what Kolahal's full build reads from the call log. */
    val CALL_COLUMNS = arrayOf(
        CallLog.Calls._ID,
        CallLog.Calls.NUMBER,
        CallLog.Calls.CACHED_NAME,
        CallLog.Calls.TYPE,
        CallLog.Calls.DATE,
        CallLog.Calls.DURATION,
        CallLog.Calls.NUMBER_PRESENTATION,
        CallLog.Calls.CACHED_LOOKUP_URI,
        CallLog.Calls.CACHED_PHOTO_URI,
    )
}
