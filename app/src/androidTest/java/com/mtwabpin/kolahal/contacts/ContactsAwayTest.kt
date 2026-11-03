package com.mtwabpin.kolahal.contacts

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.BuildConfig
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Contacts changed while home is away aren't read again at each change: they're read when home is
 * back, or when a command needs them first.
 */
@RunWith(AndroidJUnit4::class)
class ContactsAwayTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val contacts = ContactsRepository.get(context)
    private val account = "kolahal-away-${System.nanoTime()}"

    /** Run without a shell: arguments are split at spaces, and quotes are kept as they are. */
    private fun shell(command: String) =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes().decodeToString() }

    /** The names [add] gave, so [tearDown] waits for them to leave the list. */
    private val added = mutableListOf<String>()

    /** A contact with [name] (one word) and a number, added from outside Kolahal (as a sync would). */
    private fun add(name: String, number: String) {
        added += name
        shell("content insert --uri content://com.android.contacts/raw_contacts --bind account_type:s:$account --bind account_name:s:$account")
        val id = Regex("_id=(\\d+)").findAll(
            shell("content query --uri content://com.android.contacts/raw_contacts --projection _id --where account_name='$account'"),
        ).last().groupValues[1]
        shell("content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$id --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:$name")
        shell("content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$id --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:$number --bind data2:i:2")
    }

    private fun known(name: String) = contacts.contacts.value.any { it.name == name }

    private fun waitUntil(what: String, check: () -> Boolean) {
        val until = System.currentTimeMillis() + 8_000
        while (!check() && System.currentTimeMillis() < until) Thread.sleep(100)
        assertTrue(what, check())
    }

    @Before fun setUp() {
        assumeTrue("the Play build has no READ_CONTACTS", BuildConfig.CONTACTS)
        shell("pm grant ${context.packageName} android.permission.READ_CONTACTS")
        contacts.refresh()
    }

    @After fun tearDown() {
        shell("content delete --uri content://com.android.contacts/raw_contacts?caller_is_syncadapter=true --where account_name='$account'")
        // As a process with no home started has it.
        contacts.setActive(false)
        // Away, the delete only marks the list stale: read it again so later tests don't find these.
        contacts.refresh()
        waitUntil("the test's contacts gone from the list") { added.none(::known) }
    }

    @Test fun awayAChangeWaitsForACommandToNeedIt() {
        contacts.setActive(false)
        val name = "AwayTest${System.nanoTime() % 100_000}"
        add(name, "5550198")
        // Past the wait a change while home is shown has before it reloads.
        Thread.sleep(2_500)
        assertTrue("read again while away", !known(name))
        // A command asking for contacts (off the main thread) reads them first.
        assertTrue("not read when needed", contacts.current().any { it.name == name })
    }

    @Test fun backTheChangeIsRead() {
        contacts.setActive(false)
        val name = "BackTest${System.nanoTime() % 100_000}"
        add(name, "5550197")
        Thread.sleep(1_500)
        assertTrue("read again while away", !known(name))
        contacts.setActive(true)
        waitUntil("read on return") { known(name) }
    }

    /** Shown, a change is read as it comes. */
    @Test fun shownAChangeIsRead() {
        contacts.setActive(true)
        val name = "ShownTest${System.nanoTime() % 100_000}"
        add(name, "5550196")
        waitUntil("read as it came") { known(name) }
    }
}
