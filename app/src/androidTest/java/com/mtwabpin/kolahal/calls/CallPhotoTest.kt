package com.mtwabpin.kolahal.calls

import android.content.ContentProviderOperation
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.provider.ContactsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.shell
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A call's line shows the caller's photo whenever the contact has one, even when the phone app has
 * not copied the photo into the call log (some never do).
 */
@RunWith(AndroidJUnit4::class)
class CallPhotoTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val account = "kolahal-photo-${System.nanoTime()}"
    private val number = "5550161"

    /** A contact with a name, [number] and a photo, added from outside Kolahal (as a sync would). */
    private fun addContactWithPhoto() {
        val photo = java.io.ByteArrayOutputStream().also {
            Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }.compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        val raw = ContactsContract.RawContacts.CONTENT_URI
        val data = ContactsContract.Data.CONTENT_URI
        val ops = arrayListOf(
            ContentProviderOperation.newInsert(raw)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, account)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, account).build(),
            ContentProviderOperation.newInsert(data).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, "Photo Caller").build(),
            ContentProviderOperation.newInsert(data).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build(),
            ContentProviderOperation.newInsert(data).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photo).build(),
        )
        // Kolahal only reads contacts: write them as the shell.
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.WRITE_CONTACTS")
        try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Before fun setUp() {
        assumeTrue("the Play build has no READ_CONTACTS or calls", BuildConfig.CONTACTS)
        shell("pm grant ${context.packageName} android.permission.READ_CONTACTS")
        shell("pm grant ${context.packageName} android.permission.READ_CALL_LOG")
    }

    @After fun tearDown() {
        shell("content delete --uri content://com.android.contacts/raw_contacts?caller_is_syncadapter=true --where account_name='$account'")
        shell("content delete --uri content://call_log/calls --where number='$number'")
    }

    @Test fun aCallWithNoPhotoInTheCallLogGetsTheContactsPhoto() {
        addContactWithPhoto()
        // As the phone app writes a call first: the number only, no name or photo copied yet.
        shell("content insert --uri content://call_log/calls --bind number:s:$number --bind type:i:1 --bind date:l:${System.currentTimeMillis()} --bind duration:l:12")
        val call = CallEvents.recent(context, 10).first { it.number == number }
        assertNotNull("the contact's photo", call.photoUri)
        val photo = call.photoUri!!
        assertEquals("the line carries it", photo, CallEvents.ctx(call).values[LogContext.PHOTO])
        // And the log can draw it: the address opens as an image.
        val image = context.contentResolver.openInputStream(android.net.Uri.parse(photo))!!.use { BitmapFactory.decodeStream(it) }
        assertNotNull("the photo opens", image)
    }
}
