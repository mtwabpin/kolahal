/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.extras

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.ContentObserver
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import java.util.UUID

/**
 * Extras' one way in ([ExtrasContract]). Every call checks that the caller is Kolahal signed with this app's key
 * ([Kolahal.trusted]); anyone else gets a SecurityException and nothing else.
 */
class ExtrasProvider : ContentProvider() {
    private val ctx: Context get() = context!!

    override fun onCreate(): Boolean {
        Signals.start(ctx)
        return true
    }

    private fun checkCaller() {
        if (!Kolahal.trusted(ctx, Binder.getCallingUid())) throw SecurityException("Kolahal Extras answers only Kolahal")
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        checkCaller()
        // Android's own checks below run as Extras, not as the caller.
        val id = Binder.clearCallingIdentity()
        try {
            return when (method) {
                ExtrasContract.HELLO -> {
                    // A permission given since the process started: start watching now.
                    Signals.start(ctx)
                    Bundle().apply {
                        putInt(ExtrasContract.KEY_VERSION, ExtrasContract.VERSION)
                        putString(ExtrasContract.KEY_SESSION, SESSION)
                        putStringArray(ExtrasContract.KEY_HAS, ExtrasContract.FEATURES.toTypedArray())
                        putStringArray(ExtrasContract.KEY_ALLOWED, Kolahal.allowed(ctx).toTypedArray())
                    }
                }

                ExtrasContract.METHOD_LOCK -> Bundle().apply {
                    putBoolean(ExtrasContract.KEY_ENABLED, LockService.isEnabled(ctx))
                    putBoolean(ExtrasContract.KEY_LOCKED, LockService.lock())
                }

                else -> null
            }
        } finally {
            Binder.restoreCallingIdentity(id)
        }
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor? = query(uri, projection, selection, selectionArgs, sortOrder, null)

    /** The caller's projection, selection and order are ignored: each path answers its fixed columns ([ExtrasContract]). */
    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?,
        cancellationSignal: CancellationSignal?,
    ): Cursor? {
        checkCaller()
        val id = Binder.clearCallingIdentity()
        try {
            return when (uri.pathSegments.firstOrNull()) {
                ExtrasContract.PATH_CONTACTS -> if (Kolahal.canReadContacts(ctx)) contacts() else null
                ExtrasContract.PATH_CALLS -> if (Kolahal.canReadCalls(ctx)) calls(uri) else null
                else -> null
            }
        } finally {
            Binder.restoreCallingIdentity(id)
        }
    }

    /** Every phone number, as Kolahal's full build reads them. */
    private fun contacts(): Cursor {
        val out = MatrixCursor(ExtrasContract.CONTACT_COLUMNS)
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, ExtrasContract.CONTACT_COLUMNS, null, null,
            "${ContactsContract.CommonDataKinds.Phone.STARRED} DESC, ${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY}",
        )?.use { c ->
            while (c.moveToNext()) out.addRow(
                arrayOf<Any?>(
                    c.getLong(0), c.getString(1), c.getString(2), c.getInt(3), c.getString(4), c.getInt(5),
                    c.getString(6), photo(c.getString(7)),
                ),
            )
        }
        return out
    }

    /** The newest calls first, at most [ExtrasContract.PARAM_LIMIT], after an id or since a time when asked. */
    private fun calls(uri: Uri): Cursor {
        val limit = uri.getQueryParameter(ExtrasContract.PARAM_LIMIT)?.toIntOrNull()?.coerceIn(1, 500) ?: 50
        val after = uri.getQueryParameter(ExtrasContract.PARAM_AFTER)?.toLongOrNull()
        val since = uri.getQueryParameter(ExtrasContract.PARAM_SINCE)?.toLongOrNull()
        val where = listOfNotNull(after?.let { "${CallLog.Calls._ID} > ?" }, since?.let { "${CallLog.Calls.DATE} >= ?" })
        val args = listOfNotNull(after?.toString(), since?.toString())
        val phone = CallLog.Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, limit.toString()).build()
        val out = MatrixCursor(ExtrasContract.CALL_COLUMNS)
        ctx.contentResolver.query(
            phone, ExtrasContract.CALL_COLUMNS, where.joinToString(" AND ").ifEmpty { null },
            args.toTypedArray().ifEmpty { null }, "${CallLog.Calls._ID} DESC",
        )?.use { c ->
            while (c.moveToNext() && out.count < limit) {
                val number = c.getString(1).orEmpty()
                val presentation = c.getInt(6)
                // The phone app fills the call log's copy of the photo late, or never: then the contact's own.
                val photo = c.getString(8)?.takeIf { it.isNotBlank() }
                    ?: if (presentation == CallLog.Calls.PRESENTATION_ALLOWED) contactPhoto(number) else null
                out.addRow(
                    arrayOf<Any?>(
                        c.getLong(0), number, c.getString(2), c.getInt(3), c.getLong(4), c.getLong(5), presentation,
                        c.getString(7), photo(photo),
                    ),
                )
            }
        }
        return out
    }

    /** The photo of the contact with this [number], from the contacts; null without one, or without contacts access. */
    private fun contactPhoto(number: String): String? {
        if (number.isBlank() || !Kolahal.canReadContacts(ctx)) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI, ContactsContract.PhoneLookup.PHOTO_URI)
        return runCatching {
            ctx.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) (c.getString(0) ?: c.getString(1))?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()
    }

    /** A contact photo's address as Kolahal can open it: through Extras ([openAssetFile]), as only Extras may read it. */
    private fun photo(address: String?): String? = address?.takeIf { it.isNotBlank() }?.let {
        ExtrasContract.uri(ctx.packageName, ExtrasContract.PATH_PHOTO).buildUpon()
            .appendQueryParameter(ExtrasContract.PARAM_PHOTO, it).build().toString()
    }

    /** Opens a contact photo for Kolahal: only the contacts' own photos, read only. */
    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        checkCaller()
        if (mode != "r" || uri.pathSegments.firstOrNull() != ExtrasContract.PATH_PHOTO) return null
        val photo = uri.getQueryParameter(ExtrasContract.PARAM_PHOTO)?.let(Uri::parse) ?: return null
        if (photo.scheme != "content" || photo.authority != ContactsContract.AUTHORITY || !Kolahal.canReadContacts(ctx)) return null
        val id = Binder.clearCallingIdentity()
        try {
            return ctx.contentResolver.openAssetFileDescriptor(photo, "r")
        } finally {
            Binder.restoreCallingIdentity(id)
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    /**
     * Tells Kolahal when the contacts or the call log change, while Extras' process runs: `notifyChange` on the
     * addresses it reads them from. Kolahal asks again when home comes to the front, so a change missed while Extras
     * wasn't running is caught up ([ExtrasContract.KEY_SESSION]).
     */
    private object Signals {
        private var contacts = false
        private var calls = false

        @Synchronized
        fun start(context: Context) {
            val resolver = context.contentResolver
            val main = Handler(Looper.getMainLooper())
            fun relay(path: String) = object : ContentObserver(main) {
                override fun onChange(selfChange: Boolean) =
                    resolver.notifyChange(ExtrasContract.uri(context.packageName, path), null)
            }
            if (!contacts && Kolahal.canReadContacts(context)) contacts = runCatching {
                resolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, relay(ExtrasContract.PATH_CONTACTS))
            }.isSuccess
            if (!calls && Kolahal.canReadCalls(context)) calls = runCatching {
                resolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, relay(ExtrasContract.PATH_CALLS))
            }.isSuccess
        }
    }

    companion object {
        private val SESSION = UUID.randomUUID().toString()
    }
}
