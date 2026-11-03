package com.mtwabpin.kolahal.script

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.StyledText
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A script's daily times are alarms: one fired runs the script, told which time it was, and sets the next. */
@RunWith(AndroidJUnit4::class)
class ScriptAlarmsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scripts = ScriptRepository.get(context)
    private val logs = LogRepository.get(context)
    private val name = "at${System.nanoTime() % 1_000_000}"

    private fun shell(command: String) =
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }

    /** The script alarms waiting: dumpsys also lists ones gone, in its histories further down. */
    private fun alarms() = shell("dumpsys alarm").substringBefore("Allow while idle history").substringBefore("Removal history")
        .lines().filter { ScriptAlarms.ACTION in it }

    @After fun tearDown() {
        scripts.byName(name)?.let { ScriptScheduler.cancel(context, it.id); scripts.delete(it.id) }
        logs.recent(limit = 200).filter { it.source == name }.forEach { logs.delete(it.id) }
    }

    /** A timer waits for a network only when the script goes online: a battery log runs offline too. */
    @Test fun onlyAScriptThatGoesOnlineWaitsForANetwork() {
        val wm = androidx.work.WorkManager.getInstance(context)
        fun network(id: Long) = wm.getWorkInfosForUniqueWork("script-$id").get().single().constraints.requiredNetworkType
        val script = scripts.create(name, ScriptLang.LUA, "log(device.battery())")
        scripts.setInterval(script.id, 60)
        ScriptScheduler.sync(context, scripts.byId(script.id)!!)
        assertEquals(androidx.work.NetworkType.NOT_REQUIRED, network(script.id))

        scripts.saveCode(script.id, "log(http.get(\"https://example.com\").status)")
        ScriptScheduler.sync(context, scripts.byId(script.id)!!)
        assertEquals(androidx.work.NetworkType.CONNECTED, network(script.id))
    }

    @Test fun anAlarmRunsTheScriptAndSetsTheNextOne() {
        val script = scripts.create(name, ScriptLang.LUA, "-- @at 23:00, 07:00\nlog(\"ran at \" .. event.name .. \" \" .. event.time)")
        scripts.setAtTimes(script.id, listOf("23:00", "07:00"))
        ScriptScheduler.sync(context, scripts.byId(script.id)!!)
        // An alarm each.
        assertTrue(alarms().joinToString("\n"), alarms().any { "${script.id}/23:00" in it } && alarms().any { "${script.id}/07:00" in it })
        // As when it fires: the alarm is gone, and only the run can set the next one.
        ScriptAlarms.cancel(context, script.id, "23:00")
        assertTrue(alarms().none { "${script.id}/23:00" in it })

        context.sendBroadcast(
            Intent(context, ScriptAlarmReceiver::class.java)
                .setAction("${ScriptAlarms.ACTION}/${script.id}/23:00")
                .putExtra(ScriptAlarms.EXTRA_ID, script.id)
                .putExtra(ScriptAlarms.EXTRA_TIME, "23:00")
        )
        val until = System.currentTimeMillis() + 15_000
        var ran: String? = null
        while (ran == null && System.currentTimeMillis() < until) {
            Thread.sleep(200)
            ran = logs.recent(limit = 200).firstOrNull { it.source == name }?.text?.let(StyledText::plain)
        }
        assertEquals("ran at at 23:00", ran)
        // The next one is set again.
        val deadline = System.currentTimeMillis() + 5_000
        while (alarms().none { "${script.id}/23:00" in it } && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue(alarms().joinToString("\n"), alarms().any { "${script.id}/23:00" in it })
    }

    /** A script that goes online, at its daily time with a network up: it runs, and its request reaches the server. */
    @Test fun aScriptThatGoesOnlineRunsAtItsTime() {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        assumeTrue("no network", cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                client.use { c ->
                    val reader = c.getInputStream().bufferedReader()
                    while (reader.readLine()?.isNotEmpty() == true) Unit
                    c.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 4\r\nConnection: close\r\n\r\npong".toByteArray())
                }
            }
        }
        try {
            val script = scripts.create(name, ScriptLang.LUA, "local r = http.get('http://127.0.0.1:${server.localPort}/')\nlog('got ' .. r.body .. ' at ' .. event.time)")
            scripts.setAtTimes(script.id, listOf("21:15"))
            // As the app does when the times are set: the tear-down then knows the alarm to cancel.
            ScriptScheduler.sync(context, scripts.byId(script.id)!!)
            context.sendBroadcast(
                Intent(context, ScriptAlarmReceiver::class.java)
                    .setAction("${ScriptAlarms.ACTION}/${script.id}/21:15")
                    .putExtra(ScriptAlarms.EXTRA_ID, script.id)
                    .putExtra(ScriptAlarms.EXTRA_TIME, "21:15")
            )
            val until = System.currentTimeMillis() + 30_000
            var ran: String? = null
            while (ran == null && System.currentTimeMillis() < until) {
                Thread.sleep(200)
                ran = logs.recent(limit = 200).firstOrNull { it.source == name }?.text?.let(StyledText::plain)
            }
            assertEquals("got pong at 21:15", ran)
        } finally {
            server.close()
        }
    }

    @Test fun switchedOffItsAlarmsGo() {
        val script = scripts.create(name, ScriptLang.LUA, "log(\"x\")")
        scripts.setAtTimes(script.id, listOf("06:15"))
        ScriptScheduler.sync(context, scripts.byId(script.id)!!)
        assertTrue(alarms().any { "${script.id}/06:15" in it })
        scripts.setEnabled(script.id, false)
        ScriptScheduler.sync(context, scripts.byId(script.id)!!)
        assertTrue(alarms().none { "${script.id}/06:15" in it })
    }
}
