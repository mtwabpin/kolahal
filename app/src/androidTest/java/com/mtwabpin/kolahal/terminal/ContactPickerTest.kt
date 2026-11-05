package com.mtwabpin.kolahal.terminal

import android.app.Application
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.CommandRunner
import com.mtwabpin.kolahal.TerminalUiStub
import com.mtwabpin.kolahal.contacts.Contact
import com.mtwabpin.kolahal.contacts.ContactsRepository
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Play build's contacts, without READ_CONTACTS: `call`, `sms` and `contacts` open Android's
 * contact picker, and a name picked before works again by itself. The debug build has the
 * permission, so the repository is switched to the picker here.
 */
@RunWith(AndroidJUnit4::class)
class ContactPickerTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val logs = LogRepository.get(app)
    private val contacts = ContactsRepository.get(app)

    /** What the last pick was for; the test plays Android's picker by calling it. */
    private var picking: ((Contact) -> Unit)? = null
    private var picks = 0

    private val commands = CommandRunner(app, object : TerminalUiStub() {
        override fun pickContact(then: (Contact) -> Unit) { picking = then; picks++ }
    })

    private val mum = contact("Kolahal Test Mum", "+15550100")
    private val sam = contact("Kolahal Test Sam", "+15550111")

    private fun contact(name: String, number: String) =
        Contact(0, name, number, "mobile", false, Uri.parse("content://com.android.contacts/contacts/lookup/kt-$number"))

    /** Dialer and messaging intents, caught before they leave the test. */
    private val dial = instrumentation.addMonitor(IntentFilter(Intent.ACTION_DIAL).apply { addDataScheme("tel") }, null, true)
    private val sms = instrumentation.addMonitor(IntentFilter(Intent.ACTION_SENDTO).apply { addDataScheme("smsto") }, null, true)

    @Before fun setUp() {
        contacts.picker = true
        listOf(mum, sam).forEach { contacts.forget(it.name) }
    }

    @After fun tearDown() {
        listOf(mum, sam).forEach { contacts.forget(it.name) }
        contacts.picker = !com.mtwabpin.kolahal.BuildConfig.CONTACTS
        listOf(dial, sms).forEach(instrumentation::removeMonitor)
    }

    private fun run(line: String) = commands.lines(line)

    private fun caught(monitor: Instrumentation.ActivityMonitor, hits: Int) {
        val until = System.currentTimeMillis() + 5_000
        while (monitor.hits < hits && System.currentTimeMillis() < until) Thread.sleep(20)
        assertEquals(hits, monitor.hits)
    }

    /** `call` with no name, or one not picked before, opens the picker; the contact picked is dialled. */
    @Test fun callPicksThenDials() {
        run("call")
        assertEquals(1, picks)
        picking!!(mum)
        caught(dial, 1)
        assertTrue(logs.recent(5).any { it.text == "Dialing +15550100" })

        run("call kolahal test nobody")
        assertEquals("a name it doesn't know opens the picker too", 2, picks)
    }

    /** A contact picked before is found by name, without the picker. */
    @Test fun aNamePickedBeforeDialsStraightAway() {
        contacts.remember(listOf(mum))
        val out = run("call kolahal test mum")
        assertEquals("no picker for a name it knows", 0, picks)
        caught(dial, 1)
        assertTrue(out.map { it.text }.toString(), out.any { it.text == "Dialing +15550100" })
    }

    /** `sms` keeps the message for after the pick. */
    @Test fun smsPicksAndKeepsTheMessage() {
        run("sms kolahal test nobody running late")
        assertEquals(1, picks)
        picking!!(sam)
        caught(sms, 1)
        assertTrue(logs.recent(5).any { it.text == "Message to +15550111" })
    }

    /** Two picked contacts match a name: both are listed, to tap. */
    @Test fun severalMatchesAreListed() {
        contacts.remember(listOf(mum))
        contacts.remember(listOf(sam))
        val out = run("call kolahal test")
        assertEquals(0, picks)
        assertEquals(0, dial.hits)
        val lines = out.filter { it.ctx.values[LogContext.PHONE] != null }.map { it.text }
        assertEquals(listOf(mum.name, sam.name), lines)
    }

    /** `contacts` picks one and logs it; `contacts <name>` finds it later; `--forget` drops it. */
    @Test fun contactsPicksFindsAndForgets() {
        run("contacts")
        assertEquals(1, picks)
        picking!!(mum)
        assertTrue(logs.recent(5).any { it.text == mum.name && it.ctx.values[LogContext.PHONE] == mum.number })

        contacts.remember(listOf(mum))
        assertEquals(listOf(mum.name), run("contacts kolahal test mum").map { it.text })
        assertTrue(run("contacts --forget kolahal test mum").any { it.text == "Forgot ${mum.name}" })
        assertTrue(contacts.search("kolahal test mum").isEmpty())
        run("contacts kolahal test mum")
        assertEquals("forgotten, the name opens the picker again", 2, picks)
    }

    /** A picked contact's numbers replace what was kept for it; a forgotten one stays forgotten. */
    @Test fun rememberReplacesTheContactsNumbers() {
        contacts.remember(listOf(mum))
        contacts.remember(listOf(contact(mum.name, "+15550199"), contact(mum.name, "+15550198")))
        assertEquals(listOf("+15550199", "+15550198"), contacts.contacts.value.filter { it.name == mum.name }.map { it.number })
        contacts.forget(mum.name)
        assertNull(contacts.contacts.value.firstOrNull { it.name == mum.name })
    }

    /** After `call `, the contacts picked before, and a line to pick another; no "Allow contacts access". */
    @Test fun suggestionsOfferPickedContactsAndThePicker() {
        contacts.remember(listOf(mum))
        val completer = Completer(app, object : Completer.Data {
            override val history = emptyList<String>()
            override val scripts = emptyList<Script>()
            override val commandNames = emptyList<String>()
            override val scriptUsages = emptyMap<String, String>()
            override val statusNames = emptySet<String>()
            override val feeds = emptyList<com.mtwabpin.kolahal.feeds.FeedInfo>()
            override val todos = emptyList<com.mtwabpin.kolahal.productivity.Todo>()
            override val notes = emptyList<com.mtwabpin.kolahal.productivity.Note>()
            override val reminders = emptyList<com.mtwabpin.kolahal.productivity.Reminder>()
        })
        val call = completer.suggestions("call kolahal").filterIsInstance<Suggestion.Completion>()
        assertEquals(listOf("call ${mum.name}", "call"), call.map { it.replacement })
        assertNotNull(call.lastOrNull { it.label == "Pick a contact" })
        assertTrue(completer.suggestions("call ").none { it.label == "Allow contacts access" })
        val forget = completer.suggestions("contacts --forget kol").filterIsInstance<Suggestion.Completion>()
        assertEquals(listOf("contacts --forget ${mum.name}"), forget.map { it.replacement })
    }

    /** A contact picked from `call`'s suggestions opens the dialer at once, where you still press Call. */
    @Test fun aContactPickedForCallOpensTheDialer() {
        contacts.remember(listOf(mum))
        val config = Config.get(app)
        try {
            val vm = vms.terminal(app)
            // The rows the prompt shows for what's typed, as a tap or Tab takes them.
            fun callRows(typed: String): List<Suggestion.Completion> {
                instrumentation.runOnMainSync { vm.onInputChange(androidx.compose.ui.text.input.TextFieldValue(typed)) }
                // A contact's rows, not earlier lines from the history.
                return vm.suggestions(typed).filterIsInstance<Suggestion.Completion>()
                    .filter { it.replacement.startsWith("call ") && it.icon == com.mtwabpin.kolahal.R.drawable.ic_person }
            }
            fun tap(row: Suggestion.Completion) = instrumentation.runOnMainSync { vm.onSuggestion(row) }
            fun waitForDials(n: Int) {
                val deadline = System.currentTimeMillis() + 5_000
                while (dial.hits < n && System.currentTimeMillis() < deadline) Thread.sleep(100)
            }
            // With launch.instant off it waits for Enter, as before.
            config.set(Keys.LAUNCH_INSTANT, "off")
            val row = callRows("call Kolahal Test Mu").first()
            assertEquals("call ${mum.name}", row.replacement)
            tap(row)
            Thread.sleep(1_000)
            assertEquals(0, dial.hits)
            assertEquals("call ${mum.name}", vm.input.text)
            // With launch.instant all a tap opens the dialer.
            config.set(Keys.LAUNCH_INSTANT, "all")
            tap(callRows("call Kolahal Test Mu").first())
            waitForDials(1)
            assertEquals(1, dial.hits)
            assertEquals("", vm.input.text)
            // Tab takes the first row as typing does: the line waits for Enter.
            callRows("call Kolahal Test Mu")
            instrumentation.runOnMainSync { vm.complete() }
            Thread.sleep(1_000)
            assertEquals(1, dial.hits)
            assertEquals("call ${mum.name}", vm.input.text)
            // A name with two numbers: each row stands for a number, and dials that one.
            val work = contact(mum.name, "+15550122")
            contacts.remember(listOf(mum, work))
            val rows = callRows("call Kolahal Test Mu")
            assertEquals(setOf("call ${mum.number}", "call ${work.number}"), rows.map { it.replacement }.toSet())
            val before = logs.maxId()
            tap(rows.first { it.replacement == "call ${work.number}" })
            waitForDials(2)
            assertEquals(2, dial.hits)
            assertTrue(logs.recent(20).any { it.id > before && it.text == "Dialing ${work.number}" })
        } finally {
            config.reset(Keys.LAUNCH_INSTANT)
        }
    }
}
