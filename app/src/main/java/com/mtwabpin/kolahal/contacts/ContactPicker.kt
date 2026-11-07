package com.mtwabpin.kolahal.contacts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsPickerSessionContract
import androidx.activity.result.contract.ActivityResultContract

/**
 * Android's contact picker, for the Play build.
 * - The Play build has no READ_CONTACTS, because of Google Play's Contacts policy for apps that
 *   target Android 17. The app reads only the contact you pick, once, when the picker returns.
 * - On Android 17 the picker returns a session ([ContactsPickerSessionContract]).
 * - On older versions, ACTION_PICK on phone numbers lets the app read the one picked number.
 */
object ContactPicker : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        if (Build.VERSION.SDK_INT >= 37) {
            Intent(ContactsPickerSessionContract.ACTION_PICK_CONTACTS)
                .putStringArrayListExtra(ContactsPickerSessionContract.EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS, arrayListOf(Phone.CONTENT_ITEM_TYPE))
                .putExtra(ContactsPickerSessionContract.EXTRA_PICK_CONTACTS_SELECTION_LIMIT, 1)
        } else Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data.takeIf { resultCode == Activity.RESULT_OK }

    /** Returns one [Contact] for each number of the picked contact. Empty when it has no number or can't be read. */
    fun read(context: Context, uri: Uri): List<Contact> = runCatching {
        val out = ArrayList<Contact>()
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            fun text(column: String) = c.getColumnIndex(column).takeIf { it >= 0 }?.let(c::getString)
            while (c.moveToNext()) {
                // The session lists data rows of the requested kinds. Keep only phone number rows.
                if (text(ContactsContract.Data.MIMETYPE).let { it != null && it != Phone.CONTENT_ITEM_TYPE }) continue
                val number = text(Phone.NUMBER)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                val name = text(Phone.DISPLAY_NAME_PRIMARY) ?: text(Phone.DISPLAY_NAME) ?: number
                val type = text(Phone.TYPE)?.toIntOrNull()
                val key = text(Phone.LOOKUP_KEY)
                out += Contact(
                    id = text(Phone.CONTACT_ID)?.toLongOrNull() ?: 0,
                    name = name,
                    number = number,
                    label = if (type == null) "" else Phone.getTypeLabel(context.resources, type, text(Phone.LABEL)).toString().lowercase(),
                    starred = text(Phone.STARRED) == "1",
                    lookupUri = key?.let { Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, it) } ?: Uri.EMPTY,
                )
            }
        }
        out.distinctBy { it.number.filter { ch -> ch.isDigit() || ch == '+' } }
    }.getOrDefault(emptyList())
}
