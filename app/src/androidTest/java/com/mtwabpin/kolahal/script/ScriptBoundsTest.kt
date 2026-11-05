package com.mtwabpin.kolahal.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.workDataOf
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What one run may write is bounded: a script can't make a row too big to read back, or flood home. */
@RunWith(AndroidJUnit4::class)
class ScriptBoundsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scripts = ScriptRepository.get(context)
    private val status = KvStore(context, HostApi.STATUS_SCOPE)

    private val logs = LogRepository.get(context)
    @get:Rule val logLines = LogLinesSince()

    @After fun tearDown() {
        scripts.byName("bounds")?.let { scripts.delete(it.id) }
        status.set("bounds", null)
        // Its notifications too, and its lines by the rule: a later run of this class counts them.
        val notifications = context.getSystemService(android.app.NotificationManager::class.java)
        notifications.activeNotifications.filter { it.tag == "bounds" }.forEach { notifications.cancel(it.tag, it.id) }
    }

    private fun refused(what: String, block: () -> Unit) {
        try {
            block()
            fail("expected a refusal: $what")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains(what))
        }
    }

    /** An error as long as a script likes is cut in the status kept with the script: its row stays readable. */
    @Test fun aLongErrorIsCutInTheStatus() {
        val s = scripts.create("bounds", ScriptLang.LUA, "error(string.rep('x', 300000), 0)")
        runBlocking { ScriptRun.run(context, workDataOf(ScriptWorker.KEY_SCRIPT_ID to s.id)) }
        val status = scripts.byId(s.id)!!.lastStatus.orEmpty()
        assertTrue(status.take(20), status.startsWith("error: xxx"))
        assertTrue("${status.length} characters", status.length <= ScriptRepository.STATUS_MAX)
        assertTrue(scripts.all().any { it.id == s.id })
    }

    /** A store counts bytes as stored, and keys too. */
    @Test fun aStoreCountsBytesAndKeys() {
        val s = scripts.create("bounds", ScriptLang.LUA, "return 1")
        val host = HostApi(context, "bounds", scriptId = s.id, notifyEnabled = false)
        refused("key too long") { host.storeSet("k".repeat(KvStore.MAX_KEY + 1), "v") }
        // 400,000 CJK characters are 1.2 MB.
        refused("value too large") { host.storeSet("k", "字".repeat(400_000)) }
        host.storeSet("k", "字".repeat(300_000))
        assertEquals(300_000, host.storeGet("k")!!.length)
    }

    /** status() writes only what changed, and a loop of it stops after a few. */
    @Test fun statusLineWritesAreCapped() {
        val s = scripts.create("bounds", ScriptLang.LUA, "status('x')")
        val host = HostApi(context, "bounds", scriptId = s.id, notifyEnabled = false)
        repeat(5) { host.status("same") }
        for (i in 1..40) host.status("n$i")
        // One write for "same", then nineteen more.
        assertEquals("n19", status.get("bounds"))
    }

    /** At most ten notifications a run; the next call says so, once. */
    @Test fun notificationsAreCapped() {
        val host = HostApi(context, "bounds", scriptId = null, notifyEnabled = true)
        repeat(12) { host.notify("t$it", "b") }
        host.flush()
        val said = logs.recent(50).count { it.id > logLines.start && "more than ${HostApi.MAX_NOTIFY_PER_RUN} notifications" in it.text }
        assertEquals(1, said)
    }

    /** A search hands its words to a site: launcher.run won't send one for a script without the internet. */
    @Test fun webCommandsNeedTheInternet() {
        val off = HostApi(
            context, "bounds", scriptId = null, notifyEnabled = false, interactive = true,
            grants = ScriptAccess.Grants(setOf("commands"), mapOf("commands" to true, ScriptAccess.INTERNET to false)),
        )
        for (cmd in listOf("search my clipboard", "g words", "yt words", "maps a place", "wiki words")) assertTrue(cmd, !off.runCommand(cmd))
        off.flush()
        assertTrue(logs.recent(50).any { it.id > logLines.start && "sends text to a website" in it.text })
    }

    /** Nor to a `@command` script that goes online: it runs with its own switches, so it could send them on. */
    @Test fun wordsDontGoOutThroughAnotherScript() {
        val online = scripts.create("bounds-online", ScriptLang.LUA, "-- @command\nhttp.get('https://example.com/' .. argline)")
        val local = scripts.create("bounds-local", ScriptLang.LUA, "-- @command\nlog(argline)")
        val off = HostApi(
            context, "bounds", scriptId = null, notifyEnabled = false, interactive = true,
            grants = ScriptAccess.Grants(setOf("commands"), mapOf("commands" to true, ScriptAccess.INTERNET to false)),
        )
        try {
            assertTrue(!off.runCommand("bounds-online my clipboard"))
            off.flush()
            assertTrue(LogRepository.get(context).recent(50).any { "refused 'bounds-online'" in it.text })
            // No words to carry, or a script that stays on the phone: nothing goes out.
            assertTrue(off.runCommand("bounds-online"))
            assertTrue(off.runCommand("bounds-local my clipboard"))
        } finally {
            scripts.delete(online.id)
            scripts.delete(local.id)
        }
    }

    /** setWork takes true or false, or "on" or "off", alike in Lua and JavaScript. */
    @Test fun setWorkReadsItsArgumentOneWay() {
        val host = HostApi(context, "bounds", scriptId = null, notifyEnabled = false)
        assertEquals(true, host.onOff(true, "x"))
        assertEquals(false, host.onOff("off", "x"))
        assertEquals(true, host.onOff("on", "x"))
        refused("x takes true or false") { host.onOff("paused", "x") }
        refused("x takes true or false") { host.onOff(1.0, "x") }
    }

    /** WorkManager stores text in modified UTF-8 (an emoji is 6 bytes): what fits its cap is counted that way. */
    @Test fun anEmojiNotificationStillQueues() {
        assertEquals(1 + 6 + 2, ScriptScheduler.storedSize("a😀é"))
        val s = scripts.create("bounds", ScriptLang.LUA, "-- @on notification\n-- @uses notifications\nreturn 1")
        // 7.8 KB as UTF-8, under the old check, but 11.7 KB as stored: building the work used to throw.
        val wm = androidx.work.WorkManager.getInstance(context)
        try {
            ScriptScheduler.runForEvent(context, s.id, mapOf("name" to "notification", "text" to "😀".repeat(1950)))
            // Queued, its text cut to fit: not dropped.
            assertEquals(1, wm.getWorkInfosForUniqueWork("script-${s.id}-events").get().size)
        } finally {
            wm.cancelUniqueWork("script-${s.id}-events")
        }
    }

    /** An event queued before its switch went off doesn't run the script. */
    @Test fun aSwitchedOffEventDoesntRun() {
        val marker = "event-ran-" + System.nanoTime()
        val s = scripts.create("bounds", ScriptLang.LUA, "-- @on unlock\nlog('$marker')")
        scripts.setAccess(s.id, "unlock", false)
        runBlocking { ScriptRun.run(context, workDataOf(ScriptWorker.KEY_SCRIPT_ID to s.id, ScriptWorker.KEY_SCHEDULED to true, ScriptWorker.KEY_EVENT to "{\"name\":\"unlock\"}")) }
        assertTrue(LogRepository.get(context).recent(50).none { it.text == marker })
        scripts.setAccess(s.id, "unlock", true)
        runBlocking { ScriptRun.run(context, workDataOf(ScriptWorker.KEY_SCRIPT_ID to s.id, ScriptWorker.KEY_SCHEDULED to true, ScriptWorker.KEY_EVENT to "{\"name\":\"unlock\"}")) }
        assertTrue(LogRepository.get(context).recent(50).any { it.text == marker })
    }

    @Test fun aHugeClipIsRefused() {
        val host = HostApi(context, "bounds", scriptId = null, notifyEnabled = false)
        refused("clipboard.set") { host.clipboardSet("x".repeat(HostApi.MAX_CLIP + 1)) }
    }
}
