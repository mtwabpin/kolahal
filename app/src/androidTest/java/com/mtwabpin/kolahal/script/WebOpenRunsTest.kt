package com.mtwabpin.kolahal.script

import android.app.Application
import android.app.Instrumentation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.workDataOf
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.TestViewModels
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.waitUntil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * `web.open(url)` in real runs of a Lua and a JavaScript script ([HostApi.webOpen]). A run you
 * started hands the link to home, which opens it as a tapped link; a timer or an event run opens
 * nothing and says so. The browser is caught here, so none opens and nothing goes online.
 */
@RunWith(AndroidJUnit4::class)
class WebOpenRunsTest {
    @get:Rule
    val vms = TestViewModels()

    @get:Rule
    val logLines = LogLinesSince()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val scripts = ScriptRepository.get(app)
    private val logs = LogRepository.get(app)

    /** Where home notes that its first run's tour was shown: home makes none here. */
    private val launcher = app.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val toured = launcher.contains("intro_shown")

    /** The links runs handed to home ([HostApi.ACTION_OPEN_URL]). */
    private val sent = CopyOnWriteArrayList<String>()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            sent += intent.getStringExtra(HostApi.EXTRA_URL).orEmpty()
        }
    }

    /** What home asked Android to open: caught, so no browser opens. */
    private val viewed = CopyOnWriteArrayList<Intent>()
    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Intent.ACTION_VIEW) return null
            viewed += intent
            return Instrumentation.ActivityResult(0, null)
        }
    }

    private val code = mapOf(
        ScriptLang.LUA to "-- @on unlock\nlog(\"webopen \" .. tostring(web.open(\"https://example.org\")))\nweb.open(\"https://example.org\")",
        ScriptLang.JS to "// @on unlock\nlog(\"webopen \" + web.open(\"https://example.org\"));\nweb.open(\"https://example.org\");",
    )

    @Before
    fun setUp() {
        launcher.edit().putBoolean("intro_shown", true).commit()
        ContextCompat.registerReceiver(
            app, receiver, IntentFilter(HostApi.ACTION_OPEN_URL), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        instrumentation.addMonitor(monitor)
    }

    @After
    fun tearDown() {
        instrumentation.removeMonitor(monitor)
        runCatching { app.unregisterReceiver(receiver) }
        for (lang in ScriptLang.entries) scripts.byName(name(lang))?.let { scripts.delete(it.id) }
        if (!toured) launcher.edit().remove("intro_shown").commit()
    }

    private fun name(lang: ScriptLang) = "webopen-${lang.ext}"

    private fun script(lang: ScriptLang): Script =
        scripts.create(name(lang), lang, code.getValue(lang)).also { scripts.setAccess(it.id, "unlock", true) }

    private fun run(s: Script, vararg data: Pair<String, Any?>) =
        runBlocking { ScriptRun.run(app, workDataOf(ScriptWorker.KEY_SCRIPT_ID to s.id, *data)) }

    private fun added() = logs.recent(200).filter { it.id > logLines.start }

    /** A run you started hands exactly the script's address to home, and home opens it with nothing else. */
    @Test
    fun aRunYouStartedOpensTheLink() {
        vms.terminal(app)
        for (lang in ScriptLang.entries) {
            sent.clear()
            viewed.clear()
            run(script(lang), ScriptWorker.KEY_INTERACTIVE to true)
            waitUntil("$lang: home opened both links") { viewed.size == 2 }
            assertEquals(lang.name, listOf("https://example.org", "https://example.org"), sent)
            for (intent in viewed) {
                // Home reads it as a tapped link does, which writes the address out in full.
                assertEquals("https://example.org/", intent.dataString)
                assertTrue(intent.extras?.isEmpty ?: true)
            }
            assertTrue(lang.name, added().any { it.text == "webopen true" })
            assertTrue(lang.name, added().none { "web.open" in it.text })
        }
    }

    /** A timer or an event run opens nothing: one line says why, and the call returns false. */
    @Test
    fun aTimerOrAnEventRunOpensNothing() {
        vms.terminal(app)
        for (lang in ScriptLang.entries) {
            val s = script(lang)
            for (data in listOf(
                arrayOf<Pair<String, Any?>>(ScriptWorker.KEY_SCHEDULED to true),
                arrayOf<Pair<String, Any?>>(
                    ScriptWorker.KEY_SCHEDULED to true,
                    ScriptWorker.KEY_EVENT to "{\"name\":\"unlock\"}"
                ),
            )) {
                val before = logs.maxId()
                run(s, *data)
                val lines = logs.recent(50).filter { it.id > before }
                assertEquals(lang.name, 1, lines.count { it.text == "webopen false" })
                assertEquals(
                    lang.name,
                    listOf("web.open didn't open the link\nIt works only when you start the script"),
                    lines.filter { it.kind == LogKind.ERROR }.map { it.text })
            }
        }
        // Long enough for a broadcast to have arrived, had there been one.
        Thread.sleep(1_000)
        assertEquals(emptyList<String>(), sent)
        assertEquals(emptyList<Intent>(), viewed)
    }
}
